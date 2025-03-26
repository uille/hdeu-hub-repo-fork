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

import io.vertx.ext.shell.command.CommandProcess

fun extractCatalogueList(catalogueList: List<String>, process: CommandProcess): List<String> {
    val catalogueId = process.commandLine().getArgumentValue<String>(0)
    return when {
        catalogueList.isEmpty() && catalogueId == null -> listOf()
        catalogueId == null -> {

            var offset = process.commandLine().getOptionValue<String>("offset").toInt()
            var limit = process.commandLine().getOptionValue<String>("limit").toInt()

            offset = when {
                offset > catalogueList.size - 1 -> catalogueList.size - 1
                else -> offset
            }
            limit = when {
                offset + limit > catalogueList.size -> catalogueList.size - offset
                else -> limit
            }
            catalogueList.subList(offset, offset + limit)
        }
        else -> listOf(catalogueId)
    }
}
