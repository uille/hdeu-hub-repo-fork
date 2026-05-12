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
import io.piveau.hub.Defaults
import io.piveau.hub.MainVerticle
import io.piveau.rdf.RDFMimeTypes
import io.piveau.rdf.asString
import io.piveau.rdf.presentAs
import io.piveau.test.MockTripleStore
import io.piveau.utils.dcatap.dsl.catalogue
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.apache.jena.riot.Lang
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@DisplayName("Testing Catalogues API")
@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CataloguesAPITest {

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

    @BeforeAll
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        client = WebClient.create(vertx)

        val port = java.net.ServerSocket(0).use { it.localPort }
        baseUrl = "http://localhost:$port"

        val config = JsonObject()
            .put(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG, MockTripleStore.getDefaultConfig())
            .put(Constants.ENV_PIVEAU_HUB_API_KEYS, JsonObject()
                .put(apikey, JsonArray().add("*"))
                .put("noPermissionsApiKey", JsonArray().add("foreign-catalogue")))
            .put(Constants.ENV_PIVEAU_HUB_SERVICE_PORT, port)
            .put(Constants.ENV_PIVEAU_HUB_TEST_MODE, true)

        MockTripleStore()
            .deploy(vertx)
            .compose {
                vertx.deployVerticle(MainVerticle::class.java, DeploymentOptions().setConfig(config))
            }
            .onSuccess { id ->
                deploymentId = id
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(1)
    fun `Test create catalogue`(vertx: Vertx, testContext: VertxTestContext) {
        val catalogue = catalogue("test-catalogue") {
            title { "Test catalogue" lang "en" }
            description { "Some description" lang "en" }
            publisher {
                name { "FOKUS" lang ""}
            }
        }
        val content = catalogue.model.presentAs(Lang.NTRIPLES)
        client.putAbs("$serviceAddress/test-catalogue")
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .putHeader("X-API-Key", apikey)
            .sendBuffer(Buffer.buffer(content))
            .onSuccess {
                testContext.verify {
                    assertEquals(201, it.statusCode())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(2)
    fun `Test update catalogue`(vertx: Vertx, testContext: VertxTestContext) {
        val catalogueUpdate = catalogue("test-catalogue") {
            title { "Test catalogue" lang "en" }
            description { "Updated description" lang "en" }
            publisher {
                name { "FOKUS DPS" lang ""}
            }
        }
        val content = catalogueUpdate.model.presentAs(Lang.NTRIPLES)
        client.putAbs("$serviceAddress/test-catalogue")
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .putHeader("X-API-Key", apikey)
            .sendBuffer(Buffer.buffer(content))
            .onSuccess {
                testContext.verify {
                    assertEquals(204, it.statusCode())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(RDFMimeTypesEnum::class)
    @Order(3)
    fun `Test list catalogues with accept types for value type metadata`(accept: RDFMimeTypesEnum?, vertx: Vertx, testContext: VertxTestContext) {
        val expected = accept ?: RDFMimeTypesEnum.JSONLD
        val expectedContentType = "${expected.value}; charset=${expected.charset}"
        val request = client.getAbs(serviceAddress).addQueryParam("valueType", "metadata")

        accept?.let { request.putHeader(HttpHeaders.ACCEPT.toString(), it.value) }

        request.send()
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

    @Test
    @Order(4)
    fun `Test list catalogues with offset and limit`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        client.getAbs(serviceAddress)
            .addQueryParam("offset", "5")
            .addQueryParam("limit", "10")
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(200, it.statusCode())
                    assertEquals("application/json; charset=UTF-8", it.getHeader(HttpHeaders.CONTENT_TYPE.toString()))
                    assertTrue(it.body().toJsonArray().isEmpty)
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    @Order(5)
    fun `Test list catalogues 400 response`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        client.getAbs(serviceAddress)
            .addQueryParam("offset", "previous")
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(400, it.statusCode())
                    assertEquals("application/json; charset=UTF-8", it.getHeader(HttpHeaders.CONTENT_TYPE.toString()))
                    val content = it.body().toJsonObject()
                    assertEquals("offset", content.getString("parameterName"))
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "uriRefs",
            "identifiers",
            "originalIds"
        ]
    )
    @Order(6)
    fun `Test list catalogues with value types`(valueType: String, vertx: Vertx, testContext: VertxTestContext) {
        client.getAbs(serviceAddress)
            .addQueryParam("valueType", valueType)
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(200, it.statusCode())
                    assertEquals("application/json; charset=UTF-8", it.getHeader(HttpHeaders.CONTENT_TYPE.toString()))
                    val content = it.body().toJsonArray()
                    assertEquals(1, content.size())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(RDFMimeTypesEnum::class)
    @Order(7)
    fun `Test get catalogue`(accept: RDFMimeTypesEnum?, vertx: Vertx, testContext: VertxTestContext) {
        val expected = accept ?: RDFMimeTypesEnum.JSONLD
        val expectedContentType = "${expected.value}; charset=${expected.charset}"
        val request = client.getAbs("$serviceAddress/test-catalogue")

        accept?.let { request.putHeader(HttpHeaders.ACCEPT.toString(), it.value) }

        request.send()
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
    @CsvSource(
        "invalidApiKey,401",
        "noPermissionsApiKey,403",
        "test-api-key,204"
    )
    @Order(8)
    fun `Test delete catalogue`(apiKey: String, statusCode: String, vertx: Vertx, testContext: VertxTestContext) {
        client.deleteAbs("$serviceAddress/test-catalogue")
            .putHeader("X-API-Key", apiKey)
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
    @Order(9)
    fun `Test get catalogue 404 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.getAbs("$serviceAddress/test-catalogue")
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
    @Order(10)
    fun `Test delete catalogue 404 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.deleteAbs("$serviceAddress/test-catalogue")
            .putHeader("X-API-Key", apikey)
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