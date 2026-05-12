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
import io.piveau.utils.dcatap.dsl.distribution
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
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
import org.apache.jena.riot.RDFFormat
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

@DisplayName("Testing Distributions API")
@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DistributionsAPITest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val apikey = "test-api-key"
    private lateinit var client: WebClient
    private var deploymentId: String? = null
    private var baseUrl: String = "http://localhost:8080"
    private val serviceAddress get() = "$baseUrl/distributions"

    private var identifiers: List<String> = listOf()

    private val testCatalogue = catalogue("test-catalogue") {
        title { "Test catalogue" lang "en" }
        description { "Some description" lang "en" }
        publisher {
            name { "FOKUS" lang "" }
        }
    }

    private val testDistribution = distribution("test-distribution") {
        title { "Test distribution" lang "en" }
        addProperty(DCAT.accessURL, ResourceFactory.createResource("https://example.com"))
    }

    private val testDataset = dataset("test-dataset") {
        title { "Test Dataset" lang "en" }
        description { "Some description" lang "en" }
    }.also { it += testDistribution }

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
            .compose { resp ->
                if (resp.statusCode() == 201 || resp.statusCode() == 204) {
                    // Use legacy PUT /datasets/{id}?catalogue= (putDatasetOrigin - upsert) so that
                    // testDataset.id is registered as the piveau ID for subsequent test calls
                    client.putAbs("$baseUrl/datasets/${testDataset.id}")
                        .addQueryParam("catalogue", testCatalogue.id)
                        .putHeader("X-API-Key", apikey)
                        .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
                        .sendBuffer(Buffer.buffer(testDataset.model.asString(Lang.NTRIPLES)))
                } else {
                    Future.failedFuture("Failed to create test catalogue: ${resp.statusCode()} ${resp.bodyAsString()}")
                }
            }
            .compose { resp ->
                when {
                    resp.statusCode() == 201 || resp.statusCode() == 204 ->
                        client.getAbs("$baseUrl/datasets/${testDataset.id}/distributions")
                            .addQueryParam("valueType", "identifiers")
                            .send()
                    else -> Future.failedFuture("Failed to create test dataset: ${resp.statusCode()} ${resp.bodyAsString()}")
                }
            }
            .onSuccess {
                when (it.statusCode()) {
                    200 -> {
                        identifiers = it.body().toJsonArray().map(Any::toString)
                        testContext.completeNow()
                    }

                    else -> testContext.failNow(it.statusMessage())
                }
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(RDFMimeTypesEnum::class)
    fun `Test get distribution`(accept: RDFMimeTypesEnum?, vertx: Vertx, testContext: VertxTestContext) {
        val expected = accept ?: RDFMimeTypesEnum.JSONLD
        val expectedContentType = "${expected.value}; charset=${expected.charset}"

        val request = client.getAbs("$serviceAddress/${identifiers.first()}")

        accept?.let { request.putHeader(HttpHeaders.ACCEPT.toString(), it.value) }

        request
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(200, it.statusCode())
                    assertEquals(expectedContentType, it.getHeader(HttpHeaders.CONTENT_TYPE.toString()))
                    assertNotNull(it.body().toString())
                    assertEquals(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt(), it.body().bytes.size)
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    fun `Test get distribution 404 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.getAbs("$serviceAddress/8af8dd9d-6017-47e1-aa85-9feec2de9128")
            .putHeader(HttpHeaders.ACCEPT.toString(), RDFMimeTypes.JSONLD)
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(404, it.statusCode())
                    assertEquals("Distribution not found", it.body().toString())
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(RDFMimeTypesEnum::class)
    fun `Test head get distribution`(accept: RDFMimeTypesEnum?, vertx: Vertx, testContext: VertxTestContext) {
        val expected = accept ?: RDFMimeTypesEnum.JSONLD
        val expectedContentType = "${expected.value}; charset=${expected.charset}"

        val request = client.headAbs("$serviceAddress/${identifiers.first()}")

        accept?.let { request.putHeader(HttpHeaders.ACCEPT.toString(), it.value) }

        request
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(200, it.statusCode())
                    assertEquals(expectedContentType, it.getHeader(HttpHeaders.CONTENT_TYPE.toString()))
                    assertNull(it.body())
                    assertTrue(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt() > 0)
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
    fun `Test put distribution`(apiKey: String, statusCode: String, vertx: Vertx, testContext: VertxTestContext) {
        client.putAbs("$serviceAddress/${identifiers.first()}")
            .putHeader("X-API-Key", apiKey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .sendBuffer(Buffer.buffer(testDistribution.model.asString(RDFFormat.NTRIPLES)))
            .onSuccess {
                testContext.verify {
                    assertEquals(statusCode.toInt(), it.statusCode())
                    when (statusCode.toInt()) {
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
    fun `Test put distribution 404 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.putAbs("$serviceAddress/8af8dd9d-6017-47e1-aa85-9feec2de9128")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .sendBuffer(Buffer.buffer(testDistribution.model.asString(RDFFormat.NTRIPLES)))
            .onSuccess {
                if (it.statusCode() == 400 && it.body() != null) {
                    log.debug(it.body().toString())
                }
                testContext.verify {
                    assertEquals(404, it.statusCode())
                    assertEquals("Distribution not found", it.body().toString())
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
    fun `Test delete distribution`(apiKey: String, statusCode: String, vertx: Vertx, testContext: VertxTestContext) {
        client.deleteAbs("$serviceAddress/${identifiers.first()}")
            .putHeader("X-API-Key", apiKey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(statusCode.toInt(), it.statusCode())
                    when (statusCode.toInt()) {
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
    fun `Test delete distribution 404 response`(vertx: Vertx, testContext: VertxTestContext) {
        client.deleteAbs("$serviceAddress/8af8dd9d-6017-47e1-aa85-9feec2de9128")
            .putHeader("X-API-Key", apikey)
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), RDFMimeTypes.NTRIPLES)
            .send()
            .onSuccess {
                testContext.verify {
                    assertEquals(404, it.statusCode())
                    assertEquals("Distribution not found", it.body().toString())
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