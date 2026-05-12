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
package io.piveau.duplication

import io.piveau.hub.Constants
import io.piveau.hub.MainVerticle
import io.piveau.rdf.RDFMimeTypes
import io.piveau.rdf.presentAs
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
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.riot.Lang
import org.apache.jena.vocabulary.DCAT
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@DisplayName("Testing identifier duplication")
@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DuplicatesTests {

    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val apikey = "test-api-key"
    private lateinit var client: WebClient
    private var deploymentId: String? = null
    private var baseUrl: String = "http://localhost:8080"
    private val serviceAddress get() = "$baseUrl/catalogues"

    private val testCatalogue1 = catalogue("test-catalogue-1") {
        title { "Test catalogue 1" lang "en" }
        description { "Some description" lang "en" }
        publisher {
            name { "FOKUS" lang "" }
        }
    }

    private val testCatalogue2 = catalogue("test-catalogue-2") {
        title { "Test catalogue 2" lang "en" }
        description { "Some description" lang "en" }
        publisher {
            name { "FOKUS" lang "" }
        }
    }

    private val testCatalogue3 = catalogue("test-catalogue-3") {
        title { "Test catalogue 3" lang "en" }
        description { "Some description" lang "en" }
        publisher {
            name { "FOKUS" lang "" }
        }
    }

    private val testDataset = dataset("test-dataset") {
        title { "Test Dataset" lang "en" }
        description { "Some description" lang "en" }
        distribution("test-distribution") {
            title {
                "Test distribution" lang "en"
            }
            addProperty(DCAT.accessURL, ResourceFactory.createResource("http://example.com"))
        }
    }

    @BeforeAll
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        client = WebClient.create(vertx)

        val port = java.net.ServerSocket(0).use { it.localPort }
        baseUrl = "http://localhost:$port"

        val config = JsonObject()
            .put(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG, MockTripleStore.getDefaultConfig())
            .put(
                Constants.ENV_PIVEAU_HUB_API_KEYS, JsonObject()
                .put(apikey, JsonArray().add("*"))
                .put("noPermissionsApiKey", JsonArray().add("foreign-catalogue")))
            .put(Constants.ENV_PIVEAU_HUB_SERVICE_PORT, port)
            .put(Constants.ENV_PIVEAU_HUB_TEST_MODE, true)

        MockTripleStore()
            .deploy(vertx)
            .compose {
                vertx.deployVerticle(MainVerticle::class.java, DeploymentOptions().setConfig(config))
            }
            .compose { id ->
                deploymentId = id
                client.putAbs("$baseUrl/catalogues/${testCatalogue1.id}")
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
                    .putHeader("X-API-Key", apikey)
                    .sendBuffer(Buffer.buffer(testCatalogue1.model.presentAs(Lang.NTRIPLES)))
            }
            .compose { resp ->
                if (resp.statusCode() == 201 || resp.statusCode() == 204) {
                    client.putAbs("$baseUrl/catalogues/${testCatalogue2.id}")
                        .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
                        .putHeader("X-API-Key", apikey)
                        .sendBuffer(Buffer.buffer(testCatalogue2.model.presentAs(Lang.NTRIPLES)))
                } else {
                    io.vertx.core.Future.failedFuture("Failed to create catalogue 1: ${resp.statusCode()} ${resp.bodyAsString()}")
                }
            }
            .compose { resp ->
                if (resp.statusCode() == 201 || resp.statusCode() == 204) {
                    client.putAbs("$baseUrl/catalogues/${testCatalogue3.id}")
                        .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
                        .putHeader("X-API-Key", apikey)
                        .sendBuffer(Buffer.buffer(testCatalogue3.model.presentAs(Lang.NTRIPLES)))
                } else {
                    io.vertx.core.Future.failedFuture("Failed to create catalogue 2: ${resp.statusCode()} ${resp.bodyAsString()}")
                }
            }
            // Pre-create testDataset in all 3 catalogues via legacy PUT (putDatasetOrigin - upsert)
            // so that subsequent PUT /datasets/origin calls (update-only, isDatasetUpdate=true) succeed
            .compose { resp ->
                if (resp.statusCode() == 201 || resp.statusCode() == 204) {
                    client.putAbs("$baseUrl/datasets/${testDataset.id}")
                        .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
                        .putHeader("X-API-Key", apikey)
                        .addQueryParam("catalogue", testCatalogue1.id)
                        .sendBuffer(Buffer.buffer(testDataset.model.presentAs(Lang.NTRIPLES)))
                } else {
                    io.vertx.core.Future.failedFuture("Failed to create catalogue 3: ${resp.statusCode()} ${resp.bodyAsString()}")
                }
            }
            .compose { resp ->
                if (resp.statusCode() == 201 || resp.statusCode() == 204) {
                    client.putAbs("$baseUrl/datasets/${testDataset.id}")
                        .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
                        .putHeader("X-API-Key", apikey)
                        .addQueryParam("catalogue", testCatalogue2.id)
                        .sendBuffer(Buffer.buffer(testDataset.model.presentAs(Lang.NTRIPLES)))
                } else {
                    io.vertx.core.Future.failedFuture("Failed to pre-create dataset in catalogue 1: ${resp.statusCode()} ${resp.bodyAsString()}")
                }
            }
            .compose { resp ->
                if (resp.statusCode() == 201 || resp.statusCode() == 204) {
                    client.putAbs("$baseUrl/datasets/${testDataset.id}")
                        .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
                        .putHeader("X-API-Key", apikey)
                        .addQueryParam("catalogue", testCatalogue3.id)
                        .sendBuffer(Buffer.buffer(testDataset.model.presentAs(Lang.NTRIPLES)))
                } else {
                    io.vertx.core.Future.failedFuture("Failed to pre-create dataset in catalogue 2: ${resp.statusCode()} ${resp.bodyAsString()}")
                }
            }
            .onSuccess {
                if (it.statusCode() == 201 || it.statusCode() == 204) {
                    testContext.completeNow()
                } else {
                    testContext.failNow("Failed to pre-create dataset in catalogue 3: ${it.statusCode()} ${it.bodyAsString()}")
                }
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(1)
    fun `Create first test dataset in catalogue 1`(vertx: Vertx, testContext: VertxTestContext) {
        client.putAbs("${serviceAddress}/${testCatalogue1.id}/datasets/origin")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .addQueryParam("originalId", testDataset.id)
            .sendBuffer(Buffer.buffer(testDataset.model.presentAs(Lang.NTRIPLES)))
            .onSuccess {
                testContext.verify {
                    // Dataset pre-created in setUp; PUT /datasets/origin is update-only → 204 or 304
                    Assertions.assertTrue(it.statusCode() == 204 || it.statusCode() == 304)
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(2)
    fun `Create second test dataset in catalogue 2`(vertx: Vertx, testContext: VertxTestContext) {
        client.putAbs("${serviceAddress}/${testCatalogue2.id}/datasets/origin")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .addQueryParam("originalId", testDataset.id)
            .sendBuffer(Buffer.buffer(testDataset.model.presentAs(Lang.NTRIPLES)))
            .onSuccess {
                testContext.verify {
                    Assertions.assertTrue(it.statusCode() == 204 || it.statusCode() == 304)
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(3)
    fun `Create third test dataset in catalogue 3`(vertx: Vertx, testContext: VertxTestContext) {
        client.putAbs("${serviceAddress}/${testCatalogue3.id}/datasets/origin")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .addQueryParam("originalId", testDataset.id)
            .sendBuffer(Buffer.buffer(testDataset.model.presentAs(Lang.NTRIPLES)))
            .onSuccess {
                testContext.verify {
                    Assertions.assertTrue(it.statusCode() == 204 || it.statusCode() == 304)
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(4)
    fun `Translation first test dataset`(vertx: Vertx, testContext: VertxTestContext) {
        val translation = JsonObject()
            .put("id", "https://piveau.io/set/data/test-dataset~~2")
            .put("data", JsonObject()
                .put("catalogueId", "test-catalogue-3")
                .put("defaultLanguage", "en")
                .put("dict", JsonObject()))

        client.postAbs("$baseUrl/translation")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
            .sendBuffer(Buffer.buffer(translation.encode()))
            .onSuccess {
                testContext.verify {
                    Assertions.assertEquals(200, it.statusCode())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(5)
    fun `Get list of test datasets`(vertx: Vertx, testContext: VertxTestContext) {
        client.getAbs("$baseUrl/datasets")
//            .putHeader(HttpHeaders.ACCEPT.toString(), RDFMimeTypes.TURTLE)
            .send()
            .onSuccess {
                testContext.verify {
                    Assertions.assertEquals(200, it.statusCode())
                    log.debug(it.body().toString())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(6)
    fun `Get translation information`(vertx: Vertx, testContext: VertxTestContext) {
        client.getAbs("$baseUrl/datasets/test-dataset~~2")
            .putHeader(HttpHeaders.ACCEPT.toString(), RDFMimeTypes.TURTLE)
            .send()
            .onSuccess {
                testContext.verify {
                    Assertions.assertEquals(200, it.statusCode())
                    log.debug(it.body().toString())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @AfterAll
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        val id = deploymentId
        if (id != null) {
            vertx.undeploy(id)
                .onSuccess { testContext.completeNow() }
                .onFailure(testContext::failNow)
        } else {
            testContext.completeNow()
        }
    }


}