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
import io.piveau.utils.dcatap.dsl.dataset
import io.piveau.utils.dcatap.dsl.distribution
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
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@DisplayName("Testing Dataset Distributions API")
@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DatasetDistributionsAPITest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val apikey = "test-api-key"

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
        distribution("test-distribution") {
            title { "Test distribution" lang "en" }
            addProperty(DCAT.accessURL, ResourceFactory.createResource("http://example.com"))
        }
    }

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
                    .sendBuffer(Buffer.buffer(testDataset.model.asString(Lang.NTRIPLES)))
            }
            .onSuccess {
                if (it.statusCode() == 201) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(it.statusMessage())
                }
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(RDFMimeTypesEnum::class)
    fun `Test list distributions of datasets with value type 'metadata'`(
        accept: RDFMimeTypesEnum?,
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        val expected = accept ?: RDFMimeTypesEnum.JSONLD
        val expectedContentType = "${expected.value}; charset=${expected.charset}"

        val request = client.getAbs("$serviceAddress/${testDataset.id}/distributions")
            .addQueryParam("valueType", "metadata")

        accept?.let { request.putHeader(HttpHeaders.ACCEPT.toString(), it.value) }

        request.send()
            .onSuccess {
                testContext.verify {
                    assertEquals(200, it.statusCode())
                    assertEquals(expectedContentType, it.getHeader(HttpHeaders.CONTENT_TYPE.toString()))
                    assertNotNull(it.body())
                    assertEquals(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt(), it.body().bytes.size)
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(RDFMimeTypesEnum::class)
    fun `Test head list distributions of dataset with value type 'metadata'`(
        accept: RDFMimeTypesEnum?,
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        val expected = accept ?: RDFMimeTypesEnum.JSONLD
        val expectedContentType = "${expected.value}; charset=${expected.charset}"

        val request = client.headAbs("$serviceAddress/${testDataset.id}/distributions")
            .addQueryParam("valueType", "metadata")

        accept?.let { request.putHeader(HttpHeaders.ACCEPT.toString(), it.value) }

        request.send()
            .onSuccess {
                testContext.verify {
                    assertEquals(200, it.statusCode())
                    assertEquals(expectedContentType, it.getHeader(HttpHeaders.CONTENT_TYPE.toString()))
                    assertTrue(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt() > 0)
                    assertNull(it.body())
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
    fun `Test list distributions of dataset with value types`(valueType: String, vertx: Vertx, testContext: VertxTestContext) {
        client.getAbs("$serviceAddress/${testDataset.id}/distributions")
            .addQueryParam("valueType", valueType)
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(200, it.statusCode())
                    assertEquals("application/json; charset=UTF-8", it.getHeader(HttpHeaders.CONTENT_TYPE.toString()))
                    assertNotNull(it.body())
                    assertEquals(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt(), it.body().bytes.size)
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
    fun `Test head list distributions of dataset with value types`(valueType: String, vertx: Vertx, testContext: VertxTestContext) {
        client.headAbs("$serviceAddress/${testDataset.id}/distributions")
            .addQueryParam("valueType", valueType)
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(200, it.statusCode())
                    assertEquals("application/json; charset=UTF-8", it.getHeader(HttpHeaders.CONTENT_TYPE.toString()))
                    assertTrue(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt() > 0)
                    assertNull(it.body())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    fun `Test list distributions of dataset 400 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.getAbs("$serviceAddress/${testDataset.id}/distributions")
            .addQueryParam("offset", "first")
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(400, it.statusCode())
                    assertEquals(it.getHeader(HttpHeaders.CONTENT_TYPE.toString()), "application/json; charset=UTF-8")
                    assertEquals(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt(), it.body().bytes.size)
                    val debug = it.body().toJsonObject()
                    assertEquals("offset", debug.getString("parameterName"))
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    fun `Test list distributions of dataset 404 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.getAbs("$serviceAddress/failure-dataset/distributions")
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(404, it.statusCode())
                    assertEquals("Dataset not found", it.body().toString())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @CsvSource(
        "invalidApiKey,401",
        "noPermissionsApiKey,403",
        "test-api-key,201"
    )
    fun `Test add distribution to dataset`(
        apiKey: String,
        statusCode: String,
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        val testDistribution = distribution ("test-distribution") {
            title { "Test distribution" lang "en" }
            addProperty(DCAT.accessURL, ResourceFactory.createResource("https://example.com"))
        }

        client.postAbs("$serviceAddress/${testDataset.id}/distributions")
            .putHeader("X-API-Key", apiKey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .sendBuffer(Buffer.buffer(testDistribution.model.presentAs(Lang.NTRIPLES)))
            .onSuccess {
                testContext.verify {
                    assertEquals(statusCode.toInt(), it.statusCode())
                    when (it.statusCode()) {
                        201 -> {
                            assertNull(it.body())
                            assertTrue(it.getHeader(HttpHeaders.LOCATION.toString()).isNotBlank())
                        }
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
    fun `Test add distribution to dataset 400 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.postAbs("$serviceAddress/${testDataset.id}/distributions")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
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
    fun `Test add distribution to dataset 404 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.postAbs("$serviceAddress/failure-dataset/distributions")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .sendBuffer(Buffer.buffer(testDataset.model.asString(Lang.NTRIPLES)))
            .onSuccess {
                testContext.verify {
                    assertEquals(404, it.statusCode())
                    assertEquals("Dataset not found", it.body().toString())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    companion object {
        const val serviceAddress = "http://localhost:8080/datasets"
    }

}