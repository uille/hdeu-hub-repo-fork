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

import io.piveau.dcatap.DCATAPUriRef
import io.piveau.dcatap.DCATAPUriSchema
import io.piveau.dcatap.TripleStore
import io.piveau.hub.Constants
import io.piveau.json.asJsonObject
import io.piveau.utils.candidatesCompletion
import io.vertx.core.Vertx
import io.vertx.core.cli.Argument
import io.vertx.core.cli.CLI
import io.vertx.core.cli.Option
import io.vertx.ext.shell.command.Command
import io.vertx.ext.shell.command.CommandBuilder
import io.vertx.ext.shell.command.CommandProcess
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.flow.*
import org.apache.jena.vocabulary.DCAT

class RepairCatalogueCommand private constructor(vertx: Vertx) {
    private val command: Command
    private val tripleStore: TripleStore
    private val catalogueManager
        get() = tripleStore.catalogueManager

    init {
        val config = vertx.orCreateContext.config()
        tripleStore = TripleStore(
            vertx,
            config.asJsonObject(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG),
            WebClient.create(vertx)
        )

        command = CommandBuilder.command(
            CLI.create("repair")
                .addArgument(
                    Argument()
                        .setArgName("catalogueId")
                        .setRequired(false)
                        .setDescription("The id of the catalogue")
                )
                .addOption(
                    Option()
                        .setArgName("offset")
                        .setLongName("offset")
                        .setShortName("o")
                        .setDefaultValue("0")
                        .setDescription("Catalogue list offset")
                        .setSingleValued(true)
                )
                .addOption(
                    Option()
                        .setArgName("limit")
                        .setLongName("limit")
                        .setShortName("l")
                        .setDefaultValue("500")
                        .setDescription("Catalogue list limit")
                        .setSingleValued(true)
                )
                .addOption(
                    Option().setHelp(true).setFlag(true).setArgName("help").setShortName("h").setLongName("help")
                        .setDescription("This help")
                )
        ).completionHandler { completion ->
            catalogueManager.listUris().onSuccess { list ->
                val ids = list.map { it.id }
                completion.candidatesCompletion(ids)
            }.onFailure { completion.complete("", true) }
        }.scopedProcessHandler(this::handleCommand).build(vertx)
    }

    private suspend fun handleCommand(process: CommandProcess) {
        val catalogues = extractCatalogueList(catalogueManager.listUris().coAwait().map { it.id }.sorted(), process)
        catalogues.forEach { repairCatalogue(it, process, catalogues.size == 1) }
    }

    private suspend fun repairCatalogue(catalogueId: String, process: CommandProcess, travers: Boolean) {
        val catalogues = mutableListOf(DCATAPUriSchema.createForCatalogue(catalogueId))

        if (travers) {
            val subCatalogues = catalogueManager.subCatalogues(catalogueId).coAwait()
            if (subCatalogues.isNotEmpty()) {
                process.write("Found ${subCatalogues.size} sub-catalogues\n")
            }
            catalogues.addAll(subCatalogues)
        }

        catalogues
            .forEach { catalogue ->
                val validRecords = checkDatasets(catalogue, process)
                removeInvalidRecordEntries(catalogue, validRecords, process)
            }
    }

    private suspend fun checkDatasets(catalogueUriRef: DCATAPUriRef, process: CommandProcess): Set<String> {
        val datasets = catalogueManager.allDatasets(catalogueUriRef.id).coAwait().toSet()
        process.write("Found ${datasets.size} dataset entries in catalogue ${catalogueUriRef.id}\n")
        return datasets
            .asFlow()
            .transform {
                try {
                    if (tripleStore.datasetManager.exist(it).coAwait()) {
                        emit(it)
                    } else {
                        process.write("Dataset ${it.id} does not really exist. Removing entry from catalogue\n")
                        tripleStore.datasetManager.deleteGraph(it.uriRef)
                        catalogueManager.removeDatasetEntry(catalogueUriRef.uriRef, it.uriRef)
                    }
                } catch (e: Exception) {
                    process.write("Dataset ${it.id} exists check failure: ${e.message}\n")
                }
            }
            .transform { datasetUriRef ->
                try {
                    if (!tripleStore.ask(
                            ASK_TEMPLATE.format(
                                catalogueUriRef.graphName,
                                catalogueUriRef.uriRef,
                                datasetUriRef.uriRef,
                                datasetUriRef.recordUriRef
                            )
                        ).coAwait()
                    ) {
                        catalogueManager
                            .addDatasetEntry(
                                catalogueUriRef.graphName,
                                catalogueUriRef.uriRef,
                                datasetUriRef.uriRef,
                                datasetUriRef.recordUriRef
                            ).coAwait()
                    }

                    emit(datasetUriRef.recordUriRef)
                } catch (e: Exception) {
                    process.write("Dataset ${datasetUriRef.id} refresh failure: ${e.message}\n")
                }
            }.toSet()
    }

    private suspend fun removeInvalidRecordEntries(catalogueUriRef: DCATAPUriRef, validRecords: Set<String>, process: CommandProcess) {
        val allRecords = catalogueManager.allRecords(catalogueUriRef.id).coAwait()
            .map { it.recordUriRef }
            .toSet()
        process.write("Found ${allRecords.size} record entries in catalogue ${catalogueUriRef.id}\n")

        (allRecords - validRecords)
            .forEach {
                try {
                    tripleStore.update(
                        "DELETE DATA { GRAPH <${catalogueUriRef.graphNameRef}> { <${catalogueUriRef.uriRef}> <${DCAT.record}> <$it> } }"
                    ).coAwait()
                } catch (e: Exception) {
                    process.write("ERROR removing record entry for $it: ${e.message}\n")
                }
            }
    }

    companion object {
        private const val ASK_TEMPLATE = "ASK { GRAPH <%s> { <%s> <http://www.w3.ord/ns/dcat#dataset> <%s> ; <http://www.w3.ord/ns/dcat#record> <%s> } }"
        fun create(vertx: Vertx): Command {
            return RepairCatalogueCommand(vertx).command
        }
    }

}
