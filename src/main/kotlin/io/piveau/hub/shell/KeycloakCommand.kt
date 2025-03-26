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

import io.piveau.dcatap.TripleStore
import io.piveau.hub.security.KeyCloakService
import io.piveau.hub.Constants
import io.piveau.json.asJsonObject
import io.piveau.utils.candidatesCompletion
import io.vertx.core.Vertx
import io.vertx.core.cli.Argument
import io.vertx.core.cli.CLI
import io.vertx.core.cli.Option
import io.vertx.ext.shell.command.Command
import io.vertx.ext.shell.command.CommandBuilder
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.await
import kotlin.time.ExperimentalTime

@ExperimentalTime
class KeycloakCommand private constructor(vertx: Vertx) {
    private val command: Command
    private val tripleStore: TripleStore
    private val keycloakService: KeyCloakService = KeyCloakService.createProxy(vertx, KeyCloakService.SERVICE_ADDRESS)

    init {
        val config = vertx.orCreateContext.config()
        tripleStore = TripleStore(
            vertx,
            config.asJsonObject(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG),
            WebClient.create(vertx)
        )
        command = CommandBuilder.command(
            CLI.create("syncKeycloakGroups")
                .addArgument(
                    Argument()
                        .setArgName("catalogueIds")
                        .setRequired(false)
                        .setMultiValued(true)
                        .setDescription("The ids of the catalogues to index")
                )
                .addOption(
                    Option().setArgName("help")
                        .setHelp(true)
                        .setFlag(true)
                        .setShortName("h")
                        .setLongName("help")
                        .setDescription("This help")
                )
        ).completionHandler { completion ->
            tripleStore.catalogueManager.listUris().onSuccess { list ->
                val ids = list.map { it.id }
                completion.candidatesCompletion(ids)
            }.onFailure { completion.complete("", false) }
        }.scopedProcessHandler { process ->
            var catalogueIds = process.commandLine().allArguments()
            if (catalogueIds.isEmpty()) {
                process.write("Syncing all catalogues\n")
                val uris = tripleStore.catalogueManager.listUris().await()
                catalogueIds = uris.map { it.id }
            }
            catalogueIds.forEach {
                process.write("Syncing catalogue $it\n")
                keycloakService.createResource(it)
            }
            if (catalogueIds.size > 1) {
                process.write("Syncing of catalogues finished.\n")
            }
        }.build(vertx)
    }

    companion object {
        fun create(vertx: Vertx): Command {
            return KeycloakCommand(vertx).command
        }
    }

}
