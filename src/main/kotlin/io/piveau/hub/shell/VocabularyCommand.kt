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

import io.piveau.hub.services.vocabularies.VocabulariesService
import io.piveau.hub.util.ContentType
import io.vertx.core.Vertx
import io.vertx.core.cli.Argument
import io.vertx.core.cli.CLI
import io.vertx.core.cli.Option
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.shell.command.Command
import io.vertx.ext.shell.command.CommandBuilder
import io.vertx.ext.shell.command.CommandProcess
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.flow.*

class VocabularyCommand private constructor(vertx: Vertx) {

    private val vocabularyService: VocabulariesService =
        VocabulariesService.createProxy(vertx, VocabulariesService.SERVICE_ADDRESS)

    private val command: Command

    init {
        command = CommandBuilder.command(
            CLI.create("vocabs")
                .addOption(
                    Option().setHelp(true).setFlag(true).setArgName("help").setShortName("h").setLongName("help")
                )
                .addArgument(
                    Argument().setRequired(true).setIndex(1).setArgName("action")
                        .setDescription("Possible action is 'list' or 'indexing'.")
                )
                .addArgument(
                    Argument().setRequired(false).setArgName("vocabs").setMultiValued(true)
                        .setDescription("Specify one or more vocabularies for the 'show' or 'indexing' action.")
                )
        ).scopedProcessHandler(::action).build(vertx)
    }

    private suspend fun action(process: CommandProcess) {
        val commandLine = process.commandLine()
        when (process.commandLine().getArgumentValue<String>(1)) {
            "list" -> {
                vocabularyService.listVocabularies(ContentType.JSON.mimeType, "originalIds", 500, 0)
                    .onSuccess { process.write("${it}\n") }
                    .onFailure { process.write("List vocabularies failed: ${it.message}\n") }
            }

            "indexing" -> {
                val vocabs = commandLine.getArgumentValues<String>(2)

                val processingVocabularies = when {
                    vocabs.isEmpty() -> try {
                        vocabularyService.listVocabularies(ContentType.JSON.mimeType, "originalIds", 500, 0)
                            .map { JsonArray(it) }
                            .await()
                            .map { it as String }.toSet()
                    } catch (t: Throwable) {
                        process.write("Listing vocabularies failed: ${t.message}\n")
                        return
                    }
                    else -> vocabs.toSet()
                }

                processingVocabularies.asFlow()
                    .transform {vocab ->
                        try {
                            process.write("Indexing vocabulary $vocab...")
                            val array = vocabularyService.indexVocabulary(vocab).await()
                            process.write("done\n")
                            emit(vocab to array)
                        } catch (t: Throwable) {
                            process.write("\nIndexing vocabulary $vocab failed: ${t.message}\n")
                        }
                    }
                    .collect { (vocab, array) ->
                        val errors = JsonArray(array.map { it as JsonObject }.filter { it.getInteger("status") != 200 && it.getInteger("status") != 200 })
                        if (!errors.isEmpty) {
                            process.write("Vocab $vocab errors: ${errors.encodePrettily()}\n")
                        }
                    }
            }
            else -> {
                process.write("Unknown action.\n").end()
            }
        }
    }

    companion object {
        fun create(vertx: Vertx): Command { return VocabularyCommand(vertx).command }
    }
}
