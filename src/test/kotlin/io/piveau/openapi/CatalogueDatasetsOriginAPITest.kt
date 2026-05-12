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

import io.piveau.RDFMimeTypesEnum
import io.piveau.hub.Constants
import io.piveau.hub.MainVerticle
import io.piveau.rdf.RDFMimeTypes
import io.piveau.rdf.asString
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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// TODO
// 1. Create dataset
// 2. Update dateset
// 3. Get dataset
// 4. Delete dataset
// 5. Failure cases

@DisplayName("Testing Catalogue Datasets Origin API")
@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CatalogueDatasetsOriginAPITest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val apikey = "test-api-key"

    private lateinit var client: WebClient
    private var deploymentId: String? = null
    private var baseUrl: String = "http://localhost:8080"
    private val serviceAddress get() = "$baseUrl/catalogues"

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
                    .put("noPermissionsApiKey", JsonArray().add("foreign-catalogue"))
            )
            .put(Constants.ENV_PIVEAU_HUB_SERVICE_PORT, port)
            .put(Constants.ENV_PIVEAU_HUB_TEST_MODE, true)

        MockTripleStore()
            .deploy(vertx)
            .compose {
                vertx.deployVerticle(MainVerticle::class.java, DeploymentOptions().setConfig(config))
            }
            .compose { id ->
                deploymentId = id
                client.putAbs("$baseUrl/catalogues/${testCatalogue.id}")
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
                    .putHeader("X-API-Key", apikey)
                    .sendBuffer(Buffer.buffer(testCatalogue.model.presentAs(Lang.NTRIPLES)))
            }
            .compose {
                // Pre-create the dataset via the legacy PUT /datasets endpoint (putDatasetOrigin,
                // which is always upsert) so that subsequent PUT /datasets/origin calls (update-only)
                // can find it by originalId
                client.putAbs("$baseUrl/datasets/${testDataset.id}")
                    .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
                    .putHeader("X-API-Key", apikey)
                    .addQueryParam("catalogue", testCatalogue.id)
                    .sendBuffer(Buffer.buffer(testDataset.model.presentAs(Lang.NTRIPLES)))
            }
            .onSuccess {
                if (it.statusCode() == 201 || it.statusCode() == 204) {
                    testContext.completeNow()
                } else {
                    testContext.failNow("Failed to pre-create test dataset: ${it.statusCode()} ${it.statusMessage()} ${it.bodyAsString()}")
                }
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(1)
    fun `Test create dataset from origin`(vertx: Vertx, testContext: VertxTestContext) {
        val buffer = Buffer.buffer(testDataset.model.presentAs(Lang.NTRIPLES))
        println("buffer created")
        client.putAbs("$serviceAddress/${testCatalogue.id}/datasets/origin")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .addQueryParam("originalId", testDataset.id)
            .sendBuffer(buffer)
            .onSuccess {
                testContext.verify {
                    // Dataset pre-created in setUp via legacy PUT (upsert).
                    // PUT /datasets/origin is update-only: 204 if data changed, 304 if unchanged.
                    assertTrue(it.statusCode() == 204 || it.statusCode() == 304)
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(2)
    fun `Test update dataset from origin`(vertx: Vertx, testContext: VertxTestContext) {
        testDataset.addLiteral(DCAT.keyword, "keyword")

        client.putAbs("$serviceAddress/${testCatalogue.id}/datasets/origin")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .addQueryParam("originalId", testDataset.id)
            .sendBuffer(Buffer.buffer(testDataset.model.presentAs(Lang.NTRIPLES)))
            .onSuccess {
                testContext.verify {
                    assertEquals(204, it.statusCode())
                    assertNull(it.body())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(3)
    fun `Test no update dataset from origin`(vertx: Vertx, testContext: VertxTestContext) {
        client.putAbs("$serviceAddress/${testCatalogue.id}/datasets/origin")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .addQueryParam("originalId", testDataset.id)
            .sendBuffer(Buffer.buffer(testDataset.model.asString(Lang.NTRIPLES)))
            .onSuccess {
                testContext.verify {
                    assertEquals(304, it.statusCode())
                    assertNull(it.body())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(RDFMimeTypesEnum::class)
    @Order(4)
    fun `Test get dataset from origin`(accept: RDFMimeTypesEnum?, vertx: Vertx, testContext: VertxTestContext) {
        val expected = accept ?: RDFMimeTypesEnum.JSONLD
        val expectedContentType = "${expected.value}; charset=${expected.charset}"

        val request = client.getAbs("$serviceAddress/${testCatalogue.id}/datasets/origin")

        accept?.let { request.putHeader(HttpHeaders.ACCEPT.toString(), it.value) }

        request
            .addQueryParam("originalId", testDataset.id)
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(200, it.statusCode())
                    assertEquals(expectedContentType, it.getHeader(HttpHeaders.CONTENT_TYPE.toString()))
                    assertNotNull(it.body())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(RDFMimeTypesEnum::class)
    @Order(5)
    fun `Test head get dataset from origin`(accept: RDFMimeTypesEnum?, vertx: Vertx, testContext: VertxTestContext) {
        val expected = accept ?: RDFMimeTypesEnum.JSONLD
        val expectedContentType = "${expected.value}; charset=${expected.charset}"

        val request = client.headAbs("$serviceAddress/${testCatalogue.id}/datasets/origin")

        accept?.let { request.putHeader(HttpHeaders.ACCEPT.toString(), it.value) }
        request
            .addQueryParam("originalId", testDataset.id)
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(200, it.statusCode())
                    assertEquals(expectedContentType, it.getHeader(HttpHeaders.CONTENT_TYPE.toString()))
                    assertNull(it.body())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @CsvSource(
        "invalidApiKey,401",
        "noPermissionsApiKey,403",
        "test-api-key,204"
    )
    @Order(6)
    fun `Test delete dataset from origin`(apiKey: String, statusCode: String, vertx: Vertx, testContext: VertxTestContext) {
        client.deleteAbs("$serviceAddress/${testCatalogue.id}/datasets/origin")
            .putHeader("X-API-Key", apiKey)
            .addQueryParam("originalId", testDataset.id)
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(statusCode.toInt(), it.statusCode())
                    when (it.statusCode()) {
                        204 -> assertNull(it.body())
                        401 -> {
                            assertEquals("Unauthorized", it.statusMessage())
                            assertEquals("Bearer", it.getHeader("WWW-Authenticate"))
                        }
                        403 -> assertEquals("Forbidden", it.statusMessage())
                    }
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(7)
    fun `Test delete dataset from origin 404 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.deleteAbs("$serviceAddress/failure-catalogue/datasets/origin")
            .putHeader("X-API-Key", apikey)
            .addQueryParam("originalId", testDataset.id)
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(404, it.statusCode())
                    assertEquals("Catalogue not found", it.body().toString())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(8)
    fun `Test delete dataset from origin 404 response 2`(vertx: Vertx, testContext: VertxTestContext) {
        client.deleteAbs("$serviceAddress/${testCatalogue.id}/datasets/origin")
            .putHeader("X-API-Key", apikey)
            .addQueryParam("originalId", "failure-dataset")
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(404, it.statusCode())
                    assertEquals("Not Found", it.statusMessage())
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