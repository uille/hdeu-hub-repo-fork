/*
 * Copyright (C) 2024 European Union
 *  __
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the “Licence”);
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 * *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an “AS IS” basis, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */
package io.piveau.hub.shell

import io.piveau.dcatap.*
import io.piveau.hub.Constants
import io.piveau.json.ConfigHelper
import io.piveau.json.asJsonObject
import io.piveau.pipe.PipeLauncher
import io.piveau.pipe.PiveauCluster
import io.piveau.rdf.extractDatasetAsModel
import io.piveau.rdf.presentAs
import io.piveau.vocabularies.isA
import io.vertx.core.Vertx
import io.vertx.core.cli.Argument
import io.vertx.core.cli.CLI
import io.vertx.core.cli.Option
import io.vertx.core.json.JsonObject
import io.vertx.ext.shell.command.Command
import io.vertx.ext.shell.command.CommandBuilder
import io.vertx.ext.shell.command.CommandProcess
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.vocabulary.DCAT
import org.apache.jena.vocabulary.DCTerms
import org.apache.jena.vocabulary.RDF
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class LaunchCatalogueCommand private constructor(vertx: Vertx) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val command: Command
    private val tripleStore: TripleStore

    private val catalogueManager
        get() = tripleStore.catalogueManager

    private val metricsManager
        get() = tripleStore.datasetManager

    private lateinit var pipeLauncher: PipeLauncher

    init {
        val config = vertx.orCreateContext.config()
        val clusterConfig = ConfigHelper.forConfig(config).forceJsonObject(Constants.ENV_PIVEAU_CLUSTER_CONFIG)
        tripleStore = TripleStore(
            vertx,
            config.asJsonObject(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG),
            WebClient.create(vertx),
            "launch-pipe"
        )

        PiveauCluster.create(vertx, clusterConfig)
            .compose { cluster -> cluster.pipeLauncher(vertx) }
            .onSuccess { launcher -> pipeLauncher = launcher }
            .onFailure { cause -> log.error("Create launcher", cause) }

        command = CommandBuilder.command(
            CLI.create("launch")
                .addArgument(
                    Argument()
                        .setArgName("catalogueId")
                        .setRequired(true)
                        .setDescription("The id of the catalogues to launch pipe with.")
                )
                .addArgument(
                    Argument()
                        .setArgName("pipeName")
                        .setRequired(true)
                        .setDescription("The name of the pipe to launch.")
                )
                .addOption(
                    Option()
                        .setArgName("pulse")
                        .setShortName("p")
                        .setLongName("pulse")
                        .setDefaultValue("0")
                        .setDescription("Pulse of pipe feeding in milliseconds")
                )
                .addOption(Option().setFlag(true).setArgName("update").setShortName("u").setLongName("update"))
                .addOption(
                    Option().setHelp(true).setFlag(true).setArgName("help").setShortName("h").setLongName("help")
                )
                .addOption(Option().setFlag(true).setArgName("verbose").setShortName("v").setLongName("verbose"))
        ).scopedProcessHandler(::launch).build(vertx)
    }

    private suspend fun launch(process: CommandProcess) {
        val commandLine = process.commandLine()

        val catalogueId: String = commandLine.getArgumentValue(0)
        if (!catalogueManager.exist(catalogueId).coAwait()) {
            process.write("Catalogue $catalogueId does not exist.\n")
            return
        }

        val pulse = commandLine.getOptionValue<String>("pulse").toLongOrNull() ?: 0L

        val verbose = commandLine.isFlagEnabled("verbose")
        val update = commandLine.isFlagEnabled("update")
        if (update) {
            process.write("[WARNING] Updating existing metrics")
        }

        val pipeName: String = commandLine.getArgumentValue(1)
        if (!pipeLauncher.isPipeAvailable(pipeName)) {
            process.write("Pipe $pipeName not available.\n")
            return
        }

        val subCatalogues = catalogueManager.subCatalogues(catalogueId).coAwait()
        val allCatalogues = subCatalogues.ifEmpty {
            listOf(DCATAPUriSchema.createForCatalogue(catalogueId))
        }

        allCatalogues.forEach skip@{ currentCatalogue ->
            process.write("Start pipe $pipeName for catalogue ${currentCatalogue.id}\n")

            if (!catalogueManager.exist(currentCatalogue.id).coAwait()) {
                process.write("Catalogue ${currentCatalogue.id} does not exist.\n")
                return@skip
            }

            val catalogueModel = catalogueManager.getStripped(currentCatalogue.id).coAwait()
            if (verbose) {
                process.write("Catalogue metadata fetched successfully\n")
            }
            val catalogue = catalogueModel.getResource(currentCatalogue.catalogueUriRef)
            val source = catalogue.getProperty(DCTerms.type)?.literal?.lexicalForm ?: "dcat-ap"
            val dataInfo = JsonObject()
                .put("catalogue", currentCatalogue.id)
                .put("source", source)

            val datasetsList = catalogueManager.allDatasets(currentCatalogue.id).coAwait()
            dataInfo.put("total", datasetsList.size)
            dataInfo.put("skipped", 0)
            if (verbose) {
                process.write("Using data info ${dataInfo.encode()}\n")
            }

            val counter = AtomicInteger(0)

            catalogueManager.datasetsAsFlow(currentCatalogue.id)
                .onEach { delay(pulse) }
                .filter { (uriRef, model) ->
                    // TODO find and solve issue (introduce an argument for limit)
                    val distributionsCount = model.listSubjectsWithProperty(RDF.type, DCAT.Distribution).toList().size
                    if (distributionsCount < 500) {
                        true
                    } else {
                        process.write("[WARNING] ${uriRef.id} has $distributionsCount distributions, skipped.\n")
                        dataInfo.put("skipped", dataInfo.getLong("skipped") + 1)
                        false
                    }
                }
                .transform { (uriRef, datasetModel) ->
                    if (verbose) {
                        process.write("Processing ${uriRef.id}")
                    }
                    val dataset = uriRef.inModel(datasetModel)
                    val extractedModel = dataset.extractDatasetAsModel()
                    val jenaDataset = DatasetFactory.create(extractedModel)

                    if (verbose) {
                        process.write("Dataset ${dataset.uri} fetched\n")
                    }
                    if (update) {
                        val metricsModel = metricsManager.getGraph(uriRef.metricsGraphName).coAwait()
                        jenaDataset.addNamedModel(uriRef.metricsGraphName, metricsModel)
                    }
                    emit(dataset to jenaDataset)
                }
                .transform { (dataset, jenaDataset) ->
                    try {
                        val dataInfoCopy = dataInfo.copy()
                            .put("identifier", DCATAPUriSchema.parseUriRef(dataset.uri).id)
                            .put("counter", counter.incrementAndGet())

                        pipeLauncher.runPipeWithData(
                            pipeName,
                            jenaDataset.presentAs(Lang.TRIG),
                            Lang.TRIG.headerString,
                            dataInfoCopy
                        ).coAwait()
                        emit(dataInfoCopy)
                    } catch (e: Exception) {
                        process.write("[ERROR] Running pipe (${e.javaClass.simpleName}): ${e.message}\n")
                    }
                }
                .collect {
                    if (verbose) {
                        process.write("Pipe launched:\n${it.encode()}\n")
                    } else {
                        process.write("\r${it.getLong("counter")}")
                    }
                }

            process.write("Catalogue ${currentCatalogue.id} finished\n")
        }
    }

    companion object {
        fun create(vertx: Vertx) = LaunchCatalogueCommand(vertx).command
    }
}
