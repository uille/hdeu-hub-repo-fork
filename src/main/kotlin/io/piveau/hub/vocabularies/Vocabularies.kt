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
@file:JvmName("Vocabularies")
/*
 * Copyright (c) 2023. European Commission
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package io.piveau.hub.vocabularies

import io.piveau.dcatap.DCATAPUriSchema
import io.piveau.dcatap.DatasetManager
import io.piveau.dcatap.TripleStore
import io.piveau.dcatap.dcatapResource
import io.piveau.hub.Constants
import io.piveau.hub.services.catalogues.CataloguesService
import io.piveau.hub.services.datasets.DatasetsService
import io.piveau.hub.services.vocabularies.VocabulariesService
import io.piveau.json.asJsonObject
import io.piveau.rdf.RDFMimeTypes
import io.piveau.rdf.presentAs
import io.piveau.rdf.toModel
import io.piveau.utils.dcatap.dsl.catalogue
import io.piveau.utils.dcatap.dsl.dataset
import io.piveau.vocabularies.vocabulary.EDP
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.file.OpenOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.shell.command.CommandProcess
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.predicate.ResponsePredicate
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.kotlin.coroutines.await
import io.vertx.serviceproxy.ServiceException
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.riot.Lang
import org.apache.jena.vocabulary.DCAT
import org.apache.jena.vocabulary.DCTerms
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.cast

private const val VOCABULARIES_CATALOGUE_ID = "vocabularies"

val vocabulariesCatalogue = catalogue(VOCABULARIES_CATALOGUE_ID) {
    title { "Vocabularies" lang "en" }
    description { "DCAT-AP related Vocabularies" lang "en" }
    language { "ENG" }
    publisher {
        name { "Data Europa EU" lang "en" }
        addProperty(DCTerms.type, model.createResource("http://purl.org/adms/publishertype/NationalAuthority"))
    }
    addProperty(DCTerms.type, "dcat-ap")
    addProperty(EDP.visibility, EDP.hidden)
}

suspend fun installVocabularies(process: CommandProcess, config: JsonObject) {

    process.write("Installing vocabularies\n")

    val vertx = process.vertx()

    val list = process.commandLine().isFlagEnabled("list")
    if (list) {
        val buffer = vertx.fileSystem().readFile("vocabularies.json").await()
        process.write("${buffer.toJsonArray().encodePrettily()}\n")
        return
    }

    val tripleStore = TripleStore(
        vertx,
        config.asJsonObject(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG),
        name = "install-vocabularies"
    )

    val override = process.commandLine().isFlagEnabled("override")
    val remote = process.commandLine().isFlagEnabled("remote")

    val client = WebClient.create(vertx)

    try {
        val vocabulariesService = VocabulariesService.createProxy(vertx, VocabulariesService.SERVICE_ADDRESS)
        val datasetsService = DatasetsService.createProxy(vertx, DatasetsService.SERVICE_ADDRESS)
        val cataloguesService = CataloguesService.createProxy(vertx, CataloguesService.SERVICE_ADDRESS)

        val tags = process.commandLine().getOptionValues<String>("tags").toMutableList()
        if (tags.isEmpty()) {
            tags.add("core")
        }

        val vocabularies = vertx.fileSystem().readFile("vocabularies.json").await().toJsonArray()

        vocabularies
            .map { it as JsonObject }
            .filter {
                val vocabularyTags = it.getJsonArray("tags", JsonArray()).map(String::class::cast).toSet()
                tags.any { tag -> vocabularyTags.contains(tag) }
            }
            .forEach { info ->
                val id = info.getString("id")
                val uriRef = info.getString("uriRef")

                process.write("Installing $id ($uriRef)...\n")

                if (override || !tripleStore.datasetManager.existGraph(uriRef).await()) {
                    try {
                        val status = when {
                            remote && info.containsKey("remote") -> {
                                val filename = fetchFromRemote(vertx, client, info.getString("remote"), info.getJsonObject("headers", JsonObject())).await()
                                process.write("Vocabulary $id fetched\n")
                                mergeExtensions(info)
                                vocabulariesService.putVocabularyFile(
                                        id,
                                        uriRef,
                                        RDFMimeTypes.RDFXML,
                                        filename
                                    ).await()
                            }

                            info.containsKey("file") -> {
                                val content = loadFromLocal(vertx, info.getString("file")).await()
                                process.write("Vocabulary $id loaded\n")
                                mergeExtensions(info)
                                vocabulariesService.putVocabulary(
                                        id,
                                        uriRef,
                                        RDFMimeTypes.RDFXML,
                                        content
                                ).await()
                            }

                            else -> throw Throwable("Vocabulary $id is not properly configured")
                        }
                        process.write("Vocabulary $id $status\n")

                        val status2 = updateVocabularyDataset(datasetsService, cataloguesService, uriRef, id).await()
                        process.write("Vocabulary $id related dataset ${status2.getString("status")}\n")
                    } catch (t: Throwable) {
                        process.write("Vocabulary $id error: ${t.message}\n")
                    }
                } else {
                    process.write("Vocabulary $id already stored\n")
                }
            }
    } catch (t: Throwable) {
        process.write("Something went wrong: ${t.stackTraceToString()}\n")
    }
}

fun fetchFromRemote(vertx: Vertx, client: WebClient, url: String, headers: JsonObject): Future<String> {
    return Future.future { promise ->
        val file = AtomicReference<String>()
        vertx.fileSystem().createTempFile("piveau", ".tmp")
            .compose { filename ->
                file.set(filename)
                vertx.fileSystem().open(filename, OpenOptions().setWrite(true))
            }.compose { stream ->
                val request = client.getAbs(url)

                // Iterate through the headers map and add each to the request
                headers.forEach { entry ->
                    request.putHeader(entry.key, entry.value.toString())
                }

                // Make the request with BodyCodec and the stream
                request.`as`(BodyCodec.pipe(stream, true))
                    .expect(ResponsePredicate.SC_SUCCESS)
                    .send()
            }
            .onSuccess {
                promise.complete(file.get())
            }
            .onFailure(promise::fail)
    }
}

fun loadFromLocal(vertx: Vertx, file: String): Future<String> = vertx.fileSystem().readFile(file).map { it.toString() }

fun updateVocabularyDataset(
    datasetsService: DatasetsService,
    cataloguesService: CataloguesService,
    vocabularyUriRef: String,
    vocabularyId: String
): Future<JsonObject> = datasetsService.getDatasetOrigin(vocabularyId, VOCABULARIES_CATALOGUE_ID, RDFMimeTypes.NTRIPLES)
    .transform { ar ->
        val model = if (ar.succeeded()) {
            ar.result().encodeToByteArray().toModel(Lang.NTRIPLES)
        } else {
            dataset(vocabularyId) {
                title { vocabularyId lang "en" }
                description { "DCAT-AP related vocabulary" lang "en" }
                // add issued and modified
                distribution {
                    addProperty(DCAT.accessURL, ResourceFactory.createResource(vocabularyUriRef))
                }
                addProperty(DCTerms.issued, Instant.now().toString(), XSDDatatype.XSDdateTime)
            }.model
        }

        val resource = model.dcatapResource(DCATAPUriSchema.createForDataset(vocabularyId))
        resource?.removeAll(DCTerms.modified)
        resource?.addProperty(DCTerms.modified, Instant.now().toString(), XSDDatatype.XSDdateTime)

        val catalogueFuture = if (ar.failed() && ar.cause() is ServiceException && ar.cause().message == "Catalogue not found") {
            cataloguesService.putCatalogue(
                VOCABULARIES_CATALOGUE_ID,
                vocabulariesCatalogue.model.presentAs(Lang.NTRIPLES),
                RDFMimeTypes.NTRIPLES)
        } else {
            Future.succeededFuture("exists")
        }

        catalogueFuture
            .compose {
                datasetsService.putDatasetOrigin(
                    vocabularyId,
                    model.presentAs(Lang.NTRIPLES),
                    RDFMimeTypes.NTRIPLES,
                    VOCABULARIES_CATALOGUE_ID,
                    false
                )
            }
    }

suspend fun mergeExtensions(info: JsonObject) {
    if (info.containsKey("extensions")) {
//                            val model = vertx.executeBlocking { it.complete(content.toByteArray().toModel(Lang.RDFXML)) }.await()
//                            info.getJsonArray("extensions", JsonArray())
//                                .map { it as String }
//                                .forEach {
//                                    val extension = loadFromLocal(vertx, it).await()
//                                    val extensionModel = vertx.executeBlocking { p ->
//                                        p.complete(extension.toByteArray().toModel(Lang.RDFXML))
//                                    }.await()
//                                    model.add(extensionModel)
//                                }
//                            content = model.presentAs(Lang.RDFXML)
    }
}

fun findVocabularyUriRef(vocabularyId: String, datasetManager: DatasetManager): Future<String> =
    datasetManager.get(vocabularyId, VOCABULARIES_CATALOGUE_ID)
        .map { model ->
            when {
                model.isEmpty -> throw Exception("Vocabulary $vocabularyId not found")
                else -> {
                    model.listObjectsOfProperty(DCAT.accessURL)
                        .toList()
                        .stream()
                        .filter(RDFNode::isURIResource)
                        .findFirst()
                        .orElseThrow().asResource().uri
                }
            }
        }
