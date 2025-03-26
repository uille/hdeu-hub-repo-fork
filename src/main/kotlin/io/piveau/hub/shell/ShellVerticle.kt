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

import io.piveau.hub.Constants
import io.piveau.json.asJsonObject
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.shell.ShellService
import io.vertx.ext.shell.ShellServiceOptions
import io.vertx.ext.shell.command.CommandBuilder
import io.vertx.ext.shell.command.CommandProcess
import io.vertx.ext.shell.command.CommandRegistry
import io.vertx.ext.shell.term.HttpTermOptions
import io.vertx.ext.shell.term.TelnetTermOptions
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
class ShellVerticle : CoroutineVerticle() {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun start() {
        val shellServiceOptions = ShellServiceOptions()
            .setWelcomeMessage("\n  Welcome to the piveau-hub-repo CLI!\n\n")
            .setSessionTimeout(21600000).apply {
                config.asJsonObject(Constants.ENV_PIVEAU_HUB_SHELL_CONFIG).forEach {
                    val options = it.value as JsonObject
                    when (it.key) {
                        "telnet" -> telnetOptions = TelnetTermOptions()
                            .setHost(options.getString("host", "0.0.0.0"))
                            .setPort(options.getInteger("port", 5000))

                        "http" -> httpOptions = HttpTermOptions()
                            .setHost(options.getString("host", "0.0.0.0"))
                            .setPort(options.getInteger("port", 8085))
                    }
                }
            }

        ShellService.create(vertx, shellServiceOptions).start()

        val registry = CommandRegistry.getShared(vertx)
        val futures = listOf(
            registry.registerCommand(InstallVocabulariesCommand.create(vertx)),
            registry.registerCommand(LegacyIndexCommand.create(vertx)),
            registry.registerCommand(IndexCommand.create(vertx)),
            registry.registerCommand(KeycloakCommand.create(vertx)),
            registry.registerCommand(ClearCatalogueCommand.create(vertx)),
            registry.registerCommand(RepairCatalogueCommand.create(vertx)),
            registry.registerCommand(LaunchCatalogueCommand.create(vertx)),
            registry.registerCommand(SyncScoreCommand.create(vertx)),
            registry.registerCommand(VocabularyCommand.create(vertx)),
            registry.registerCommand(RemoveDuplicatesCommand.create(vertx)),
            registry.registerCommand(TransferHistoryCommand.create(vertx)),
            registry.registerCommand(TranslateCommand.create(vertx)),
            registry.registerCommand(ZombieDatasetsCommand.create(vertx)),
            registry.registerCommand(FixPublisherCommand.create(vertx)),
            registry.registerCommand(FixChecksumCommand.create(vertx)),
            registry.registerCommand(DeleteHistoryCommand.create(vertx))
        )
        config.asJsonObject(Constants.ENV_PIVEAU_HUB_SHELL_CONFIG).forEach {
            val options = it.value as JsonObject
            when (it.key) {
                "telnet" -> log.info("Telnet shell started on port  ${options.getInteger("port", 5000)}")
                "http" -> log.info("HTTP shell started on port  ${options.getInteger("port", 8085)}")
            }
        }

        Future.join(futures).coAwait()
    }

}

@OptIn(ExperimentalTime::class)
fun CommandBuilder.scopedProcessHandler(handler: suspend (process: CommandProcess) -> Unit): CommandBuilder =
    processHandler { process ->
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val duration = measureTime {
                handler(process)
            }
            process.write("Command finished in $duration\n").end()
        }
        process.endHandler { scope.cancel() }
        process.interruptHandler { process.write("Process interrupted\n").end() }
    }
