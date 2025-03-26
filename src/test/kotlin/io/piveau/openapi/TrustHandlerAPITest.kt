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
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.riot.Lang
import org.apache.jena.vocabulary.DCAT
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

@DisplayName("Testing TrustHandler API")
@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TrustHandlerAPITest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private lateinit var client: WebClient
    private val serviceAddress = "http://localhost:8080";
    private val serviceAddressTrust = serviceAddress + "/trust";

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        client = WebClient.create(vertx)

        // Path to your configuration file
        val configPath = Paths.get("conf/config.json")
        if (Files.exists(configPath)) {
            try {
                val content = Files.readString(configPath)
                val config = JsonObject(content)
                log.debug("Config Loaded: $config")

                // Deploy the MainVerticle with the loaded configuration
                val deploymentOptions = DeploymentOptions().setConfig(config)
                vertx.deployVerticle(MainVerticle(), deploymentOptions) { deployResult ->
                    if (deployResult.succeeded()) {
                        log.info("MainVerticle deployed successfully.")
                        testContext.completeNow()
                    } else {
                        log.error("Failed to deploy MainVerticle: ${deployResult.cause()}")
                        testContext.failNow(deployResult.cause())
                    }
                }
            } catch (e: IOException) {
                log.error("IO Exception while reading config: ${e.message}")
                testContext.failNow(e)
            }
        } else {
            val errorMsg = "Configuration file not found at path: $configPath"
            log.error(errorMsg)
            testContext.failNow(IOException(errorMsg))
        }
    }

    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close { closeResult ->
            if (closeResult.succeeded()) {
                log.info("Vert.x instance closed successfully.")
                testContext.completeNow()
            } else {
                log.error("Failed to close Vert.x instance: ${closeResult.cause()}")
                testContext.failNow(closeResult.cause())
            }
        }
    }

    /**
     * Extension function to sanitize a Verifiable Credential (VC) JsonObject by removing dynamic fields.
     *
     * This function removes the "issuanceDate" field and the "jws" field within the "proof" object.
     *
     * @receiver JsonObject The original VC JsonObject to sanitize.
     * @return JsonObject The sanitized VC JsonObject.
     */
    fun JsonObject.sanitizeVC(): JsonObject {
        // Remove "issuanceDate"
        this.remove("issuanceDate")

        // Remove "jws" from "proof" if "proof" exists and is a JsonObject
        val proof = this.getJsonObject("proof")
        proof?.remove("jws")

        return this
    }

//    @ParameterizedTest
//    @NullSource
//    @EnumSource(RDFMimeTypesEnum::class)
//    @Order(4)
//    fun `Test TrustHandler Get DID`(
//        accept: RDFMimeTypesEnum?,
//        vertx: Vertx,
//        testContext: VertxTestContext
//    ) {
//
//        // Expected FOKUS IDS Server DID
//        val expected = JsonObject("{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:web:ids.fokus.fraunhofer.de\",\"verificationMethod\":[{\"id\":\"did:web:ids.fokus.fraunhofer.de#JWK2020-RSA\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:web:ids.fokus.fraunhofer.de\",\"publicKeyJwk\":{\"kty\":\"RSA\",\"e\":\"AQAB\",\"n\":\"rNY1MRmHkytaKNGFU4ds2su5KcflhSSLpWLpwg5d2NpxIvReoYo1wZwtPp6yN4OMGNe6S-rTtC_LUbRxgU0E0Sgb2GxPtYYK6L3Lzb1aK96xkSfXR4ozf8c8UBWcvqh56NRUeNOLkV_4mgkY2b9J5cMKnpG0nTVKGeC2zfS25wxd06_u09WIfbPBWweVDhL1PO6oVOvseerM8c8V53UbZURNygYYXd360rTgN33clWfVOMBrLD90OOBO_u_0gdKUHqYF4_YcJyR0LOkaDzpCbvQp-LWzDmomFI5uXQsf_nHmij7H5SlDmHRHJB777jCLqFgFHF_14G0QJPdwHN_Qlw\",\"alg\":\"RS256\",\"x5u\":\"https://ids.fokus.fraunhofer.de/.well-known/fullchain.pem\"}}],\"assertionMethod\":[\"did:web:ids.fokus.fraunhofer.de#JWK2020-RSA\"]}");
//        // HTTP Get Request to the /trust/did endpoint
//        log.debug("Service address trust: ${serviceAddressTrust}")
//
//        val did = "did:web:ids.fokus.fraunhofer.de";
//        val request = client.getAbs("${serviceAddressTrust}/did")
//            .addQueryParam("did", did)
//
//        // If 'accept' is not null, set the 'Accept' header in the request
//        accept?.let { request.putHeader(HttpHeaders.ACCEPT.toString(), it.value) }
//
//        // Send request
//        request.send()
//            .onSuccess {
//                // If successful, test response
//                testContext.verify {
//                    // Check if the status code is 200 (OK)
//                    assertEquals(200, it.statusCode())
//                    // Ensure the response body is not null
//                    assertNotNull(it.body())
//                    // Verify the content length header matches the size of the response body
//                    assertEquals(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt(), it.body().bytes.size)
//                    // Check if the response body matches the expected DID
//                    assertEquals(expected, it.body().toJsonObject())
//                }
//
//                // Check if did stored at trust/did/did-web-ids.fokus.fraunhofer.de.json
//                val storedDidPath = "trust/did/did-web-ids.fokus.fraunhofer.de.json"
//                vertx.fileSystem().readFile(storedDidPath) { fileResult ->
//                    if (fileResult.succeeded()) {
//                        val fileContent = fileResult.result().toString()
//                        log.debug("Stored DID file content: $fileContent")
//                        val storedDidJson = JsonObject(fileContent)
//
//                        testContext.verify {
//                            // Compare the stored DID JSON with the expected
//                            assertEquals(expected, storedDidJson, "Stored DID JSON does not match the expected DID JSON")
//                        }
//
//                        // Signal that the test has completed successfully
//                        testContext.completeNow()
//                    } else {
//                        // Log the error and fail the test
//                        log.error("Failed to read stored DID file at $storedDidPath: ${fileResult.cause().message}")
//                        testContext.failNow(fileResult.cause())
//                    }
//                }
//                testContext.completeNow()
//            }
//            .onFailure(testContext::failNow)
//    }
//
//    @ParameterizedTest
//    @NullSource
//    @EnumSource(RDFMimeTypesEnum::class)
//    @Order(4)
//    fun `Test TrustHandler Create Setup`(
//        accept: RDFMimeTypesEnum?,
//        vertx: Vertx,
//        testContext: VertxTestContext
//    ) {
//
//        val request = client.postAbs("${serviceAddressTrust}/setup")
//
//        // If 'accept' is not null, set the 'Accept' header in the request
//        accept?.let { request.putHeader(HttpHeaders.ACCEPT.toString(), it.value) }
//
//        // Send request
//        request.send()
//            .onSuccess {
//                // If successful, test response
//                testContext.verify {
//                    // Check if the status code is 200 (OK)
//                    assertEquals(200, it.statusCode())
//                    // Ensure the response body is not null
//                    assertNotNull(it.body())
//                    // Verify the content length header matches the size of the response body
//                    assertEquals(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt(), it.body().bytes.size)
//
//                    // Check if the response body matches the expected setup VC
//                    val setupVC = it.bodyAsJsonObject()
//                    log.debug("SETUP VC: $setupVC")
//                    // assertEquals(expected, it.body().toJsonObject())
//                }
//
//                // Check that correct files stored at trust/setup
//                val storedVCPathLegalParticipant = "trust/setup/legal-participant.json"
//                vertx.fileSystem().readFile(storedVCPathLegalParticipant) { fileResult ->
//                    if (fileResult.succeeded()) {
//                        val fileContent = fileResult.result().toString()
//                        log.debug("Stored legal-participant.json file content: $fileContent")
//                        val VCLegalParticipant = JsonObject(fileContent)
//                        val expected = JsonObject("{\"@context\":[\"https://www.w3.org/2018/credentials/v1\",\"https://w3id.org/security/suites/jws-2020/v1\",\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#\"],\"credentialSubject\":{\"gx-terms-and-conditions:gaiaxTermsAndConditions\":\"70c1d713215f95191a11d38fe2341faed27d19e083917bc8732ca4fea4976700\",\"gx:headquarterAddress\":{\"gx:countrySubdivisionCode\":\"FR-HDF\"},\"gx:legalAddress\":{\"gx:countrySubdivisionCode\":\"FR-HDF\"},\"gx:legalName\":\"Fraunhofer FOKUS\",\"gx:legalRegistrationNumber\":{\"id\":\"http://localhost:8080/trust/setup/legal-registration-number.json#cs\"},\"id\":\"http://localhost:8080/trust/setup/legal-participant.json#cs\",\"type\":\"gx:LegalParticipant\"},\"id\":\"http://localhost:8080/trust/setup/legal-participant.json\",\"issuanceDate\":\"2024-09-19T12:56:41.757811Z\",\"issuer\":\"did:web:example.org\",\"proof\":{\"jws\":\"eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..Ij912IyGardHhQ9sy3xkOQctVrQwrNP59De9MZ8kK77t82cwFxaL59rkLBotpFV4Pk5bYXmh2z98O2Tb84LxrOc0pyGQgRWyGHLNJAMfZQfz8DFBFRaCQ4V4SII5iepiWUVEyuyBheTz7Vp72X-O3L3A5JDKSptetB-_q1WGJbF03Ls07ulq1b4_6wkp9bVRt9_XE2p7qGStudL_uhL8Uo_ViHyXAWYn5lBulyZ55vpswFc1sEFUmeA3eGWTQvoYHa3Rdbf2MraFDbHqt5ix6wd6aGHY9iI9EcnlRejybkkgGKDn5BzOf4uyiAq-s8OH21Ff4f8SFtnTJ0porSYNYg\",\"proofPurpose\":\"assertionMethod\",\"type\":\"JsonWebSignature2020\",\"verificationMethod\":\"did:web:example.org#JWK2020-RSA\"},\"type\":\"VerifiableCredential\"}")
//
//                        testContext.verify {
//                            // TODO: Test Signature Verification
//
//                            // Compare the stored legal-participant.json with expected
//                            expected.sanitizeVC()
//                            VCLegalParticipant.sanitizeVC()
//                            assertEquals(expected, VCLegalParticipant, "Stored legal-participant.json does not match expected")
//                        }
//
//                        // Signal that the test has completed successfully
//                        testContext.completeNow()
//                    } else {
//                        // Log the error and fail the test
//                        log.error("Failed to read stored legal-participant.json file at $storedVCPathLegalParticipant: ${fileResult.cause().message}")
//                        testContext.failNow(fileResult.cause())
//                    }
//                }
//
//                // Check that correct files stored at trust/setup
//                val storedVCPathTermsAndConditions = "trust/setup/terms-and-conditions.json"
//                vertx.fileSystem().readFile(storedVCPathTermsAndConditions) { fileResult ->
//                    if (fileResult.succeeded()) {
//                        val fileContent = fileResult.result().toString()
//                        log.debug("Stored terms-and-conditions.json file content: $fileContent")
//                        val VCTermsAndConditions = JsonObject(fileContent)
//                        val expected = JsonObject("""{"@context":["https://www.w3.org/2018/credentials/v1","https://w3id.org/security/suites/jws-2020/v1","https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#"],"credentialSubject":{"gx:termsAndConditions":"The PARTICIPANT signing the Self-Description agrees as follows:\n- to update its descriptions about any changes, be it technical, organizational, or legal - especially but not limited to contractual in regards to the indicated attributes present in the descriptions.\n\nThe keypair used to sign Verifiable Credentials will be revoked where Gaia-X Association becomes aware of any inaccurate statements in regards to the claims which result in a non-compliance with the Trust Framework and policy rules defined in the Policy Rules and Labelling Document (PRLD).","id":"http://localhost:8080/trust/setup/terms-and-conditions.json#cs","type":"gx:GaiaXTermsAndConditions"},"id":"http://localhost:8080/trust/setup/terms-and-conditions.json","issuanceDate":"2024-09-19T12:56:41.758368Z","issuer":"did:web:example.org","proof":{"jws":"eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..pl0S-pzzEnr7ZKZ0vc5R5L4vDJ5M77o9xa2i5Xg5_sOBTlQIu88ucGCm3tASVjJQuZ9Z1OyNVzdSIHHqTg-KMQmNVchDPrw46Qrp3OGMYn7lhDhTbXEtWhZP93109R-8XhzdABdPIcUCrWAturxh6ERLiw5PhkgzgjKU2Ub3u2LY7oKt2G0pttH3RmAoAurCIsQe7ogEzKg-ky14T4fkddg1qZKK__xhRrd2qCYFHU88Oy-UR-QR-F3zTEoJwSc-sVi4Nv-kRGBimpK2OgSvOBs4zuCLVycSOZ0Y-TbJqbfJA-OLlLOqOqqqVBk6ceUEuES0UEwxe3UDWRyEUHjP_Q","proofPurpose":"assertionMethod","type":"JsonWebSignature2020","verificationMethod":"did:web:example.org#JWK2020-RSA"},"type":"VerifiableCredential"}""")
//
//                        testContext.verify {
//                            // TODO: Test Signature Verification
//
//                            // Compare the stored terms-and-conditions.json with expected
//                            expected.sanitizeVC()
//                            VCTermsAndConditions.sanitizeVC()
//                            assertEquals(expected, VCTermsAndConditions, "Stored terms-and-conditions.json does not match expected")
//                        }
//
//                        // Signal that the test has completed successfully
//                        testContext.completeNow()
//                    } else {
//                        // Log the error and fail the test
//                        log.error("Failed to read stored legal-participant.json file at $storedVCPathTermsAndConditions: ${fileResult.cause().message}")
//                        testContext.failNow(fileResult.cause())
//                    }
//                }
//
//                // Check that correct files stored at trust/setup
//                val storedVCPathLegalRegistrationNumber = "trust/setup/legal-registration-number.json"
//                vertx.fileSystem().readFile(storedVCPathLegalRegistrationNumber) { fileResult ->
//                    if (fileResult.succeeded()) {
//                        val fileContent = fileResult.result().toString()
//                        log.debug("Stored legal-registration-number file content: $fileContent")
//                        val VCLegalRegistrationNumber = JsonObject(fileContent)
//                        val expected = JsonObject("""{"@context":["https://www.w3.org/2018/credentials/v1","https://w3id.org/security/suites/jws-2020/v1"],"type":["VerifiableCredential"],"id":"http://localhost:8080/trust/setup/legal-registration-number.json","issuer":"did:web:registration.lab.gaia-x.eu:v1","issuanceDate":"2024-09-19T12:56:42.039Z","credentialSubject":{"@context":"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#","type":"gx:legalRegistrationNumber","id":"http://localhost:8080/trust/setup/legal-registration-number.json#cs","gx:vatID":"FR09883637795","gx:vatID-countryCode":"FR"},"evidence":[{"gx:evidenceURL":"http://ec.europa.eu/taxation_customs/vies/services/checkVatService","gx:executionDate":"2024-09-19T12:56:42.039Z","gx:evidenceOf":"gx:vatID"}],"proof":{"type":"JsonWebSignature2020","created":"2024-09-19T12:56:42.057Z","proofPurpose":"assertionMethod","verificationMethod":"did:web:registration.lab.gaia-x.eu:v1#X509-JWK2020","jws":"eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..qQSiuaNmgx1Uw1vVH5oL6pM8XZPx54vkOxyMRhfFEmub3pPE89iBZWLApW0_aOKZJ8jFmBfn7QTq0EG_a4AQrjEpAWay9x0uGnpEB7dd80aeFRsjpLaFyly4AZal5Q4Wkx2tsUyXxRHAduqC_PHcKEnHNnY0rmgHdW_F61Tq5Teg_O195IquDEJJPBykjlml5WcBa_gC6O_FZ-w3nUCZWVAA5GVYkywX2-abxjQ6YVp0P3qS0f5ptx9emfOmeuK9gJ-zeYtZpIJrwxm6TzxBKGwFcZKeBEDw-V3redbpyM6eo_jy69FZbVWew1wg0_FQ1VnaXEggCS-rv-vQKNlG9g"}}""")
//
//                        testContext.verify {
//                            // TODO: Test Signature Verification
//
//                            // Compare the stored terms-and-conditions.json with expected
//                            expected.sanitizeVC()
//                            VCLegalRegistrationNumber.sanitizeVC()
//                            assertEquals(expected, VCLegalRegistrationNumber, "Stored legal-registration-number.json does not match expected")
//                        }
//
//                        // Signal that the test has completed successfully
//                        testContext.completeNow()
//                    } else {
//                        // Log the error and fail the test
//                        log.error("Failed to read stored legal-participant.json file at $storedVCPathLegalRegistrationNumber: ${fileResult.cause().message}")
//                        testContext.failNow(fileResult.cause())
//                    }
//                }
//
//                testContext.completeNow()
//            }
//            .onFailure(testContext::failNow)
//    }
//
//    @ParameterizedTest
//    @NullSource
//    @EnumSource(RDFMimeTypesEnum::class)
//    @Order(4)
//    fun `Test TrustHandler Get Setup VCs`(
//        accept: RDFMimeTypesEnum?,
//        vertx: Vertx,
//        testContext: VertxTestContext
//    ) {
//
//        val request = client.postAbs("${serviceAddressTrust}/setup")
//
//        // If 'accept' is not null, set the 'Accept' header in the request
//        accept?.let { request.putHeader(HttpHeaders.ACCEPT.toString(), it.value) }
//
//        // Send request
//        request.send()
//            .onSuccess {
//                // If successful, retrieve each of the three Setup VCs
//
//                // Legal Participant
//                val requestLegalParticipantVC = client.getAbs("${serviceAddressTrust}/setup/legal-participant.json")
//                requestLegalParticipantVC.send()
//                    .onSuccess {
//                        val expected = JsonObject("{\"@context\":[\"https://www.w3.org/2018/credentials/v1\",\"https://w3id.org/security/suites/jws-2020/v1\",\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#\"],\"credentialSubject\":{\"gx-terms-and-conditions:gaiaxTermsAndConditions\":\"70c1d713215f95191a11d38fe2341faed27d19e083917bc8732ca4fea4976700\",\"gx:headquarterAddress\":{\"gx:countrySubdivisionCode\":\"FR-HDF\"},\"gx:legalAddress\":{\"gx:countrySubdivisionCode\":\"FR-HDF\"},\"gx:legalName\":\"Fraunhofer FOKUS\",\"gx:legalRegistrationNumber\":{\"id\":\"http://localhost:8080/trust/setup/legal-registration-number.json#cs\"},\"id\":\"http://localhost:8080/trust/setup/legal-participant.json#cs\",\"type\":\"gx:LegalParticipant\"},\"id\":\"http://localhost:8080/trust/setup/legal-participant.json\",\"issuanceDate\":\"2024-09-19T12:56:41.757811Z\",\"issuer\":\"did:web:example.org\",\"proof\":{\"jws\":\"eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..Ij912IyGardHhQ9sy3xkOQctVrQwrNP59De9MZ8kK77t82cwFxaL59rkLBotpFV4Pk5bYXmh2z98O2Tb84LxrOc0pyGQgRWyGHLNJAMfZQfz8DFBFRaCQ4V4SII5iepiWUVEyuyBheTz7Vp72X-O3L3A5JDKSptetB-_q1WGJbF03Ls07ulq1b4_6wkp9bVRt9_XE2p7qGStudL_uhL8Uo_ViHyXAWYn5lBulyZ55vpswFc1sEFUmeA3eGWTQvoYHa3Rdbf2MraFDbHqt5ix6wd6aGHY9iI9EcnlRejybkkgGKDn5BzOf4uyiAq-s8OH21Ff4f8SFtnTJ0porSYNYg\",\"proofPurpose\":\"assertionMethod\",\"type\":\"JsonWebSignature2020\",\"verificationMethod\":\"did:web:example.org#JWK2020-RSA\"},\"type\":\"VerifiableCredential\"}")
//
//                        // If successful, test response
//                        testContext.verify {
//                            // Check if the status code is 200 (OK)
//                            assertEquals(200, it.statusCode())
//                            // Ensure the response body is not null
//                            assertNotNull(it.body())
//                            // Verify the content length header matches the size of the response body
//                            assertEquals(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt(), it.body().bytes.size)
//                            // Check if the response body matches the expected DID
//                            assertEquals(expected, it.body().toJsonObject())
//                        }
//
//                    }
//                    .onFailure(testContext::failNow)
//
//                val requestTermsAndConditionsVC = client.getAbs("${serviceAddressTrust}/setup/terms-and-conditions.json")
//                requestTermsAndConditionsVC.send()
//                    .onSuccess {
//                        val expected = JsonObject("""{"@context":["https://www.w3.org/2018/credentials/v1","https://w3id.org/security/suites/jws-2020/v1","https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#"],"credentialSubject":{"gx:termsAndConditions":"The PARTICIPANT signing the Self-Description agrees as follows:\n- to update its descriptions about any changes, be it technical, organizational, or legal - especially but not limited to contractual in regards to the indicated attributes present in the descriptions.\n\nThe keypair used to sign Verifiable Credentials will be revoked where Gaia-X Association becomes aware of any inaccurate statements in regards to the claims which result in a non-compliance with the Trust Framework and policy rules defined in the Policy Rules and Labelling Document (PRLD).","id":"http://localhost:8080/trust/setup/terms-and-conditions.json#cs","type":"gx:GaiaXTermsAndConditions"},"id":"http://localhost:8080/trust/setup/terms-and-conditions.json","issuanceDate":"2024-09-19T12:56:41.758368Z","issuer":"did:web:example.org","proof":{"jws":"eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..pl0S-pzzEnr7ZKZ0vc5R5L4vDJ5M77o9xa2i5Xg5_sOBTlQIu88ucGCm3tASVjJQuZ9Z1OyNVzdSIHHqTg-KMQmNVchDPrw46Qrp3OGMYn7lhDhTbXEtWhZP93109R-8XhzdABdPIcUCrWAturxh6ERLiw5PhkgzgjKU2Ub3u2LY7oKt2G0pttH3RmAoAurCIsQe7ogEzKg-ky14T4fkddg1qZKK__xhRrd2qCYFHU88Oy-UR-QR-F3zTEoJwSc-sVi4Nv-kRGBimpK2OgSvOBs4zuCLVycSOZ0Y-TbJqbfJA-OLlLOqOqqqVBk6ceUEuES0UEwxe3UDWRyEUHjP_Q","proofPurpose":"assertionMethod","type":"JsonWebSignature2020","verificationMethod":"did:web:example.org#JWK2020-RSA"},"type":"VerifiableCredential"}""")
//
//                        // If successful, test response
//                        testContext.verify {
//                            // Check if the status code is 200 (OK)
//                            assertEquals(200, it.statusCode())
//                            // Ensure the response body is not null
//                            assertNotNull(it.body())
//                            // Verify the content length header matches the size of the response body
//                            assertEquals(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt(), it.body().bytes.size)
//                            // Check if the response body matches the expected DID
//                            assertEquals(expected, it.body().toJsonObject())
//                        }
//
//                    }
//                    .onFailure(testContext::failNow)
//
//                val requestLegalRegistrationNumberVC = client.getAbs("${serviceAddressTrust}/setup/legal-registration-number.json")
//                requestLegalRegistrationNumberVC.send()
//                    .onSuccess {
//                        val expected = JsonObject("""{"@context":["https://www.w3.org/2018/credentials/v1","https://w3id.org/security/suites/jws-2020/v1"],"type":["VerifiableCredential"],"id":"http://localhost:8080/trust/setup/legal-registration-number.json","issuer":"did:web:registration.lab.gaia-x.eu:v1","issuanceDate":"2024-09-19T12:56:42.039Z","credentialSubject":{"@context":"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#","type":"gx:legalRegistrationNumber","id":"http://localhost:8080/trust/setup/legal-registration-number.json#cs","gx:vatID":"FR09883637795","gx:vatID-countryCode":"FR"},"evidence":[{"gx:evidenceURL":"http://ec.europa.eu/taxation_customs/vies/services/checkVatService","gx:executionDate":"2024-09-19T12:56:42.039Z","gx:evidenceOf":"gx:vatID"}],"proof":{"type":"JsonWebSignature2020","created":"2024-09-19T12:56:42.057Z","proofPurpose":"assertionMethod","verificationMethod":"did:web:registration.lab.gaia-x.eu:v1#X509-JWK2020","jws":"eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..qQSiuaNmgx1Uw1vVH5oL6pM8XZPx54vkOxyMRhfFEmub3pPE89iBZWLApW0_aOKZJ8jFmBfn7QTq0EG_a4AQrjEpAWay9x0uGnpEB7dd80aeFRsjpLaFyly4AZal5Q4Wkx2tsUyXxRHAduqC_PHcKEnHNnY0rmgHdW_F61Tq5Teg_O195IquDEJJPBykjlml5WcBa_gC6O_FZ-w3nUCZWVAA5GVYkywX2-abxjQ6YVp0P3qS0f5ptx9emfOmeuK9gJ-zeYtZpIJrwxm6TzxBKGwFcZKeBEDw-V3redbpyM6eo_jy69FZbVWew1wg0_FQ1VnaXEggCS-rv-vQKNlG9g"}}""")
//
//                        // If successful, test response
//                        testContext.verify {
//                            // Check if the status code is 200 (OK)
//                            assertEquals(200, it.statusCode())
//                            // Ensure the response body is not null
//                            assertNotNull(it.body())
//                            // Verify the content length header matches the size of the response body
//                            assertEquals(it.getHeader(HttpHeaders.CONTENT_LENGTH.toString()).toInt(), it.body().bytes.size)
//                            // Check if the response body matches the expected DID
//                            assertEquals(expected, it.body().toJsonObject())
//                        }
//
//                    }
//                    .onFailure(testContext::failNow)
//
//                testContext.completeNow()
//            }
//            .onFailure(testContext::failNow)
//    }
//
//    @ParameterizedTest
//    @NullSource
//    @EnumSource(RDFMimeTypesEnum::class)
//    @Order(4)
//    fun `Test TrustHandler Get Trust - Service Offering`(
//        accept: RDFMimeTypesEnum?,
//        vertx: Vertx,
//        testContext: VertxTestContext
//    ) {
//
//        val type = "service-offering"
//        val id = "data-product-designer"
//        val absoluteUrl = "${serviceAddress}/trust/${type}/${id}"
//        val request = client.getAbs(absoluteUrl)
//            .addQueryParam("cached", "false")
//            .addQueryParam("showCompliance", "true")
//
//        // If 'accept' is not null, set the 'Accept' header in the request
//        accept?.let {
//            request.putHeader(HttpHeaders.ACCEPT.toString(), it.value)
//            log.debug("Set Accept header to: ${it.value}")
//        }
//
//        // Send request
//        request.send()
//            .onSuccess { response ->
//                try {
//                    log.debug("Received response with status code: ${response.statusCode()}")
//                    when (response.statusCode()) {
//                        200 -> {
//                            val serviceOfferingVC = response.bodyAsJsonObject()
//                            log.debug("ServiceOfferingVC: $serviceOfferingVC")
//
//                            val storedVCPathServiceOffering = "trust/service-offering/${type}_$id.json"
//                            log.debug("Attempting to read stored VC from: $storedVCPathServiceOffering")
//                            vertx.fileSystem().readFile(storedVCPathServiceOffering) { fileResult ->
//                                if (fileResult.succeeded()) {
//                                    val fileContent = fileResult.result().toString()
//                                    log.debug("Stored service-offering file content: $fileContent")
//                                    val storedVC = JsonObject(fileContent)
//
//                                    // Define the expected JSON content (ensure this is valid JSON without comments)
//                                    val expectedJson = """
//                                        {
//                                            "@context": [
//                                                "https://www.w3.org/2018/credentials/v1",
//                                                "https://w3id.org/security/suites/jws-2020/v1"
//                                            ],
//                                            "type": ["VerifiableCredential"],
//                                            "id": "http://localhost:8080/trust/setup/legal-registration-number.json",
//                                            "issuer": "did:web:registration.lab.gaia-x.eu:v1",
//                                            "issuanceDate": "2024-09-19T12:56:42.039Z",
//                                            "credentialSubject": {
//                                                "@context": "https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#",
//                                                "type": "gx:legalRegistrationNumber",
//                                                "id": "http://localhost:8080/trust/setup/legal-registration-number.json#cs",
//                                                "gx:vatID": "FR09883637795",
//                                                "gx:vatID-countryCode": "FR"
//                                            },
//                                            "evidence": [{
//                                                "gx:evidenceURL": "http://ec.europa.eu/taxation_customs/vies/services/checkVatService",
//                                                "gx:executionDate": "2024-09-19T12:56:42.039Z",
//                                                "gx:evidenceOf": "gx:vatID"
//                                            }],
//                                            "proof": {
//                                                "type": "JsonWebSignature2020",
//                                                "created": "2024-09-19T12:56:42.057Z",
//                                                "proofPurpose": "assertionMethod",
//                                                "verificationMethod": "did:web:registration.lab.gaia-x.eu:v1#X509-JWK2020",
//                                                "jws": "eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..qQSiuaNmgx1Uw1vVH5oL6pM8XZPx54vkOxyMRhfFEmub3pPE89iBZWLApW0_aOKZJ8jFmBfn7QTq0EG_a4AQrjEpAWay9x0uGnpEB7dd80aeFRsjpLaFyly4AZal5Q4Wkx2tsUyXxRHAduqC_PHcKEnHNnY0rmgHdW_F61Tq5Teg_O195IquDEJJPBykjlml5WcBa_gC6O_FZ-w3nUCZWVAA5GVYkywX2-abxjQ6YVp0P3qS0f5ptx9emfOmeuK9gJ-zeYtZpIJrwxm6TzxBKGwFcZKeBEDw-V3redbpyM6eo_jy69FZbVWew1wg0_FQ1VnaXEggCS-rv-vQKNlG9g"
//                                            }
//                                        }
//                                    """.trimIndent()
//
//                                    val expected = JsonObject(expectedJson)
//                                    log.debug("Expected VC: $expected")
//
//                                    testContext.verify {
//                                        // TODO: Test Signature Verification
//
//                                        expected.sanitizeVC()
//                                        storedVC.sanitizeVC()
//                                        assertEquals(
//                                            expected,
//                                            storedVC,
//                                            "Stored legal-registration-number.json does not match expected"
//                                        )
//                                    }
//
//                                    // Signal that the test has completed successfully
//                                    testContext.completeNow()
//                                } else {
//                                    log.error(
//                                        "Failed to read stored ServiceOffering JSON file at $storedVCPathServiceOffering: ${fileResult.cause().message}"
//                                    )
//                                    testContext.failNow(fileResult.cause())
//                                }
//                            }
//                        }
//                        400 -> {
//                            log.error("Invalid request. Status Code: 400")
//                            testContext.failNow(Exception("Invalid request. Status Code: 400"))
//                        }
//                        404 -> {
//                            log.error("Resource type or ID not found. Status Code: 404")
//                            testContext.failNow(Exception("Resource type or ID not found. Status Code: 404"))
//                        }
//                        else -> {
//                            log.error("Unexpected status code: ${response.statusCode()}")
//                            testContext.failNow(Exception("Unexpected status code: ${response.statusCode()}"))
//                        }
//                    }
//                } catch (e: Exception) {
//                    log.error("Exception during test execution: ${e.message}", e)
//                    testContext.failNow(e)
//                }
//            }
//            .onFailure { throwable ->
//                // Handle request failure
//                log.error("Request failed: ${throwable.message}", throwable)
//                testContext.failNow(throwable)
//            }
//    }


}