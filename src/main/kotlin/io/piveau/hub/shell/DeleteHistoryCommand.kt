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
import io.piveau.hub.Constants
import io.piveau.json.asJsonObject
import io.piveau.sparql.SparqlQueryPool
import io.vertx.core.Vertx
import io.vertx.core.cli.CLI
import io.vertx.core.cli.Option
import io.vertx.ext.shell.command.Command
import io.vertx.ext.shell.command.CommandBuilder
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.await
import java.nio.file.Path

class DeleteHistoryCommand private constructor(vertx: Vertx) {

    private val command: Command

    private val tripleStore: TripleStore

    private val client: WebClient = WebClient.create(vertx)

    init {
        val config = vertx.orCreateContext.config()
        tripleStore = TripleStore(
            vertx,
            config.asJsonObject(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG),
            client
        )
        command = CommandBuilder.command(
            CLI.create("deleteMetricsHistories")
                .setDescription("Delete old metrics history graphs from main system")
                .addOption(
                    Option().setArgName("help")
                        .setHelp(true)
                        .setFlag(true)
                        .setShortName("h")
                        .setLongName("help")
                        .setDescription("This help")
                )
        ).scopedProcessHandler { process ->

            val query = SparqlQueryPool.create(vertx, Path.of("queries")).format("metricsHistories") ?: ""

            do {
                val resultSet = tripleStore.select(query).await()
                var cont = false
                if (resultSet.hasNext()) {
                    val histories = resultSet.asSequence().map { qr -> qr.getResource("g") }.toList()

                    histories.forEach {
                        tripleStore.deleteGraph(it.uri).await()
                        process.write("Deleted ${it.uri}\n")
                    }
                    cont = true
                }
            } while (cont)

            process.write("Flow finished\n")
        }.build(vertx)
    }

    companion object {
        fun create(vertx: Vertx): Command {
            return DeleteHistoryCommand(vertx).command
        }
    }

}
