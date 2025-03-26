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

import io.piveau.dcatap.CatalogueManager
import io.piveau.dcatap.DCATAPUriSchema
import io.piveau.dcatap.DatasetManager
import io.piveau.dcatap.TripleStore
import io.piveau.hub.Constants
import io.piveau.hub.Defaults
import io.piveau.hub.dataobjects.DatasetHelper
import io.piveau.hub.services.translation.TranslationService
import io.piveau.hub.services.translation.TranslationServiceUtils
import io.piveau.json.asJsonObject
import io.piveau.vocabularies.Concept
import io.piveau.vocabularies.Languages
import io.vertx.core.Vertx
import io.vertx.core.cli.Argument
import io.vertx.core.cli.CLI
import io.vertx.core.cli.Option
import io.vertx.core.json.JsonObject
import io.vertx.ext.shell.command.Command
import io.vertx.ext.shell.command.CommandBuilder
import io.vertx.ext.shell.command.CommandProcess
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import org.apache.jena.vocabulary.DCTerms

class TranslateCommand private constructor(vertx: Vertx) {
    private val command: Command

    private val catalogueManager: CatalogueManager
    private val datasetManager: DatasetManager

    private val translationService: TranslationService?

    init {
        val config = vertx.orCreateContext.config()
        val tripleStore = TripleStore(
            vertx,
            config.asJsonObject(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG),
            WebClient.create(vertx)
        )

        catalogueManager = tripleStore.catalogueManager
        datasetManager = tripleStore.datasetManager

        translationService =
            if (config.asJsonObject(Constants.ENV_PIVEAU_TRANSLATION_SERVICE).getBoolean("enable", false)) {
                TranslationService.createProxy(vertx, TranslationService.SERVICE_ADDRESS)
            } else {
                null
            }

        command = CommandBuilder.command(
            CLI.create("translate")
                .addOption(
                    Option().setArgName("help")
                        .setHelp(true)
                        .setDescription("This help")
                        .setFlag(true)
                        .setShortName("h")
                        .setLongName("help")
                )
                .addOption(
                    Option().setArgName("force")
                        .setDescription("Forces translation also for already completed status.")
                        .setFlag(true)
                        .setShortName("f")
                        .setLongName("force")
                )
                .addArgument(
                    Argument()
                        .setArgName("objectType")
                        .setDescription("Object type of id. Possible values are 'dataset and 'catalogue'.")
                        .setRequired(true)
                )
                .addArgument(
                    Argument()
                        .setArgName("objectId")
                        .setDescription("The id of the object to translate")
                        .setMultiValued(true)
                        .setRequired(true)
                )
        ).scopedProcessHandler(this::translate).build(vertx)
    }

    private suspend fun translate(process: CommandProcess) {
        if (translationService != null) {
            val objectId = process.commandLine().getArgumentValue<String>("objectId")

            when (process.commandLine().getArgumentValue<String>("objectType")) {
                "dataset" -> translateDataset(objectId, process)
                "catalogue" -> translateCatalogue(objectId, process)
                else -> process.write("Unknown object type. Please, use 'dataset' or 'catalogue'.\n").end()
            }
        } else {
            process.write("[WARNING] Translation is disabled\n").end()
        }
    }

    private suspend fun translateDataset(datasetId: String, process: CommandProcess) {
        val datasetUriRef = DCATAPUriSchema.createForDataset(datasetId)
        val catalogueResource = datasetManager.catalogue(datasetUriRef.uriRef).await()
        val catalogueSchema = DCATAPUriSchema.parseUriRef(catalogueResource.uri)

        val datasetModel = datasetManager.getGraph(datasetUriRef.graphNameRef).await()

        val datasetHelper = DatasetHelper.create(datasetModel)

        val force = process.commandLine().isFlagEnabled("force")

        if (!force && TranslationServiceUtils.isTranslationCompleted(datasetHelper)) {
            process.write("[INFO] Translation already completed\n").end()
        } else {
            datasetHelper.catalogueId(catalogueSchema.id)
            datasetHelper.catalogueInfo = JsonObject().put("langCode", catalogueLanguage(catalogueSchema.id))

            translationService?.initializeTranslationProcess(datasetHelper, null, force) {
                if (it.succeeded()) {
                    process.write("[INFO] Translation initialized\n").end()
                } else {
                    process.write("[ERROR] Translation initialization: ${it.cause().message}\n").end()
                }
            }
        }
    }

    private suspend fun translateCatalogue(catalogueId: String, process: CommandProcess) {
        val defaultLanguage = catalogueLanguage(catalogueId)
        val force = process.commandLine().isFlagEnabled("force")

        catalogueManager
            .datasetsAsFlow(catalogueId)
            .map { (_, model) -> DatasetHelper.create(model) }
            .filterNot { helper ->
                (!force && TranslationServiceUtils.isTranslationCompleted(helper)).also {
                    if (it) process.write("[INFO] Dataset ${helper.piveauId()} already translated\n")
                }
            }
            .onCompletion { cause ->
                if (cause == null) process.write("[INFO] Catalogue translation finished\n")
                else process.write("[ERROR] Catalogue translation failure: ${cause.message}\n")
                process.end()
            }
            .collect { helper ->
                helper.catalogueId(catalogueId)
                helper.catalogueInfo = JsonObject().put("langCode", defaultLanguage)
                translationService?.initializeTranslationProcess(helper, null, force) {
                    if (it.failed()) {
                        process.write("[ERROR] Translation initialization failure for ${helper.piveauId()}: ${it.cause().message}\n")
                    }
                }
            }
    }

    private suspend fun catalogueLanguage(catalogueId: String): String? {
        val model = catalogueManager.getStripped(catalogueId).await()
        val resource = model.getResource(DCATAPUriSchema.createFor(catalogueId).catalogueUriRef)
        val language = resource.getPropertyResourceValue(DCTerms.language)
        val concept = if (Languages.getConcept(language) != null) {
            Languages.getConcept(language)
        } else {
            Languages.getConcept(Defaults.DEFAULT_LANG)
        }
        return Languages.iso6391Code(concept as Concept)
    }

    companion object {
        fun create(vertx: Vertx): Command {
            return TranslateCommand(vertx).command
        }
    }

}