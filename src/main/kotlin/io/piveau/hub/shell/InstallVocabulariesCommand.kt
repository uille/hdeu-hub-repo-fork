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

import io.piveau.hub.vocabularies.installVocabularies
import io.vertx.core.Vertx
import io.vertx.core.cli.CLI
import io.vertx.core.cli.Option
import io.vertx.ext.shell.command.Command
import io.vertx.ext.shell.command.CommandBuilder
import io.vertx.ext.shell.command.CommandProcess

class InstallVocabulariesCommand private constructor(vertx: Vertx) {

    private val command: Command

    private val config = vertx.orCreateContext.config()

    init {
        command = CommandBuilder.command(
            CLI.create("installVocabularies")
                .setSummary("Install a selected list of vocabularies.")
                .addOption(
                    Option()
                        .setArgName("override")
                        .setShortName("o")
                        .setLongName("override")
                        .setDescription("Override any existing graphs and indexes.")
                        .setFlag(true)
                )
                .addOption(
                    Option()
                        .setArgName("remote")
                        .setShortName("r")
                        .setLongName("remote")
                        .setDescription("If possible, fetch vocabulary from remote, otherwise load from local file.")
                        .setFlag(true)
                )
                .addOption(
                    Option()
                        .setArgName("list")
                        .setShortName("l")
                        .setLongName("list")
                        .setDescription("List installable vocabularies and their configuration.")
                        .setFlag(true)
                )
                .addOption(
                    Option()
                        .setHelp(true)
                        .setFlag(true)
                        .setArgName("help")
                        .setShortName("h")
                        .setLongName("help")
                )
                .addOption(
                    Option()
                        .setArgName("tags")
                        .setShortName("t")
                        .setLongName("tags")
                        .setDescription("Install only tagged vocabularies. If you don't specify a tag, all 'core' tagged vocabularies will be installed.")
                        .setMultiValued(true)
                        .setDefaultValue("core")
                )
        ).scopedProcessHandler(::install).build(vertx)
    }

    private suspend fun install(process: CommandProcess) {
        installVocabularies(process, config)
        process.write("Vocabularies installed\n")
    }

    companion object {
        fun create(vertx: Vertx) = InstallVocabulariesCommand(vertx).command
    }
}
