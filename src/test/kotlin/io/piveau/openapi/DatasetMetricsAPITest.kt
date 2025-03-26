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
package io.piveau.openapi

import io.piveau.hub.Constants
import io.piveau.hub.Defaults
import io.piveau.hub.MainVerticle
import io.piveau.rdf.RDFMimeTypes
import io.piveau.rdf.presentAs
import io.piveau.rdf.readTrigResource
import io.piveau.test.MockTripleStore
import io.piveau.utils.dcatap.dsl.catalogue
import io.piveau.utils.dcatap.dsl.dataset
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.apache.jena.query.DatasetFactory
import org.apache.jena.riot.Lang
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// TODO
// 1. Head metrics
// 2. Put metrics
// 3. Delete metrics

@DisplayName("Testing Dataset Metrics API")
@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DatasetMetricsAPITest {
    private val apikey = "test-api-key"
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private lateinit var client: WebClient

    private val testCatalogue = catalogue("test-catalogue") {
        title { "Test catalogue" lang "en" }
        description { "Some description" lang "en" }
        publisher {
            name { "FOKUS" lang "" }
        }
    }

    private val testDataset = dataset("test-dataset") {
        title { "Test Dataset" lang "en" }
        description { "Some description" lang "en" }
    }

    private val testMetrics = DatasetFactory.create().readTrigResource("example_metric_1.ttl")

    @BeforeAll
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        client = WebClient.create(vertx)

        val config = JsonObject()
            .put(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG, MockTripleStore.getDefaultConfig())
            .put(Constants.ENV_PIVEAU_HUB_API_KEYS, JsonObject()
                .put(apikey, JsonArray().add("*"))
                .put("noPermissionsApiKey", JsonArray().add("foreign-catalogue")))

        MockTripleStore()
            .loadGraph(testCatalogue.uri, testCatalogue.model)
            .deploy(vertx)
            .compose {
                vertx.deployVerticle(MainVerticle::class.java, DeploymentOptions().setConfig(config))
            }
            .compose {
                client.putAbs("http://localhost:8080/catalogues/${testCatalogue.id}/datasets/origin")
                    .addQueryParam("originalId", testDataset.id)
                    .putHeader("X-API-Key", apikey)
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
                    .sendBuffer(Buffer.buffer(testDataset.model.presentAs(Lang.NTRIPLES)))
            }
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @ValueSource(ints = [201,204])
    fun `Test put dataset metrics`(statusCode: Int, vertx: Vertx, testContext: VertxTestContext) {
        client.putAbs("$serviceAddress/${testDataset.id}/metrics")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.TRIG)
            .sendBuffer(Buffer.buffer(testMetrics.presentAs(Lang.TRIG)))
            .onSuccess {
                testContext.verify {
                    assertEquals(statusCode, it.statusCode())
                    assertNull(it.body())
                    if (it.statusCode() == 201) {
                        assertNotNull(it.getHeader(HttpHeaders.LOCATION.toString()))
                    }
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    fun `Test put dataset metrics 404 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.putAbs("$serviceAddress/failure-dataset/metrics")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.TRIG)
            .sendBuffer(Buffer.buffer(testMetrics.presentAs(Lang.TRIG)))
            .onSuccess {
                testContext.verify {
                    assertEquals(404, it.statusCode())
                    assertEquals("Dataset not found", it.body().toString())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    fun `Test put dataset metrics 400 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.putAbs("$serviceAddress/${testDataset.id}/metrics")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.TRIG)
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(400, it.statusCode())
                    assertEquals("Body required", it.body().toString())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    fun `Test get dataset metrics`(vertx: Vertx, testContext: VertxTestContext) {
        client.putAbs("$serviceAddress/${testDataset.id}/metrics")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.TRIG)
            .sendBuffer(Buffer.buffer(testMetrics.presentAs(Lang.TRIG)))
            .onSuccess {
                client.getAbs("$serviceAddress/${testDataset.id}/metrics")
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.TRIG)
                    .send()
                    .onSuccess {
                        testContext.verify {
                            assertEquals(200, it.statusCode())
                        }
                        testContext.completeNow()
                    }
                    .onFailure(testContext::failNow)
            }
            .onFailure(testContext::failNow)
    }

    @Test
    fun `Test head get dataset metrics`(vertx: Vertx, testContext: VertxTestContext) {
        testContext.completeNow()
    }

    @Test
    fun `Test delete dataset metrics`(vertx: Vertx, testContext: VertxTestContext) {
        testContext.completeNow()
    }

    companion object {
        const val serviceAddress = "http://localhost:8080/datasets"
    }

}