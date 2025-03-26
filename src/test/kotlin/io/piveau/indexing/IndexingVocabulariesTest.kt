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
package io.piveau.indexing

import io.piveau.hub.indexing.indexConceptScheme
import io.piveau.rdf.toModel
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.predicate.ResponsePredicate
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@DisplayName("Indexing geonames test")
@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexingVocabulariesTest {

    val log: Logger = LoggerFactory.getLogger(this.javaClass)

    @BeforeAll
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        testContext.completeNow()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `Test local vocabulary indexing`(vertx: Vertx, testContext: VertxTestContext) {
        vertx.fileSystem().readFile("de-contributors-skos.rdf")
            .compose { buffer ->
                vertx.executeBlocking { p ->
                    val model = buffer.bytes.toModel(Lang.RDFXML)
                    p.complete(model)
                }
            }.onSuccess { model ->
                var index: JsonObject;
                measureTime {
                    index = indexConceptScheme(
                        "contributors",
                        "http://dcat-ap.de/def/contributors",
                        model
                    )
                }.let { duration -> log.info("${duration.inWholeMilliseconds}") }
                testContext.completeNow()
            }
            .onFailure {
                testContext.failNow(it)
            }
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.MINUTES)
    @Disabled
    fun `Test remote vocabulary indexing`(vertx: Vertx, testContext: VertxTestContext) {
        WebClient.create(vertx).getAbs("http://publications.europa.eu/resource/distribution/eurovoc/rdf/skos_ap_act/eurovoc-skos-ap-act.rdf")
            .expect(ResponsePredicate.SC_SUCCESS)
            .send()
            .map { it.body() }
            .compose { buffer ->
                vertx.executeBlocking<Model> { p ->
                    val model = buffer.bytes.toModel(Lang.RDFXML)
                    p.complete(model)
                }
            }.onSuccess { model ->
                log.info("Number of triples: ${model.size()} / Chunks: ${model.size() / 2500}")
                var index = indexConceptScheme(
                    "eurovoc",
                    "http://publications.europa.eu/resource/authority/eurovoc",
                    model
                )
                testContext.completeNow()
            }
            .onFailure {
                testContext.failNow(it)
            }
    }

}