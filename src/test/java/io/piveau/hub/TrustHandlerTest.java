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
package io.piveau.hub;

import io.vertx.core.json.JsonObject;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.piveau.hub.ext.trust.TrustHandler;

@DisplayName("Testing TrustHandler")
@ExtendWith(VertxExtension.class)
class TrustHandlerTest {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private JsonObject config;

    @BeforeEach
    void setUp() {
        // Get config from conf/config.json
        Path configPath = Paths.get("conf/config.json");

        if (Files.exists(configPath)) {
            try {
                String content = Files.readString(configPath);
                config = new JsonObject(content);
            } catch (IOException e) {
                logger.debug("IO Exception: " + e.getMessage());
            }
        } else {
            logger.debug("Could not find conf/config.json");
        }
    }

//    @Test
//    @DisplayName("testTrustHandler - createDID")
//    void testTrustHandlerDID(Vertx vertx, VertxTestContext testContext) {
//        logger.debug("*** testTrustHandler DID");
//
//        RoutingContext context = Mockito.mock(RoutingContext.class);
//        TrustHandler trustHandler = new TrustHandler(vertx, config);
//
//        JsonObject expected = new JsonObject("{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:web:ids.fokus.fraunhofer.de\",\"verificationMethod\":[{\"id\":\"did:web:ids.fokus.fraunhofer.de#JWK2020-RSA\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:web:ids.fokus.fraunhofer.de\",\"publicKeyJwk\":{\"kty\":\"RSA\",\"e\":\"AQAB\",\"n\":\"rNY1MRmHkytaKNGFU4ds2su5KcflhSSLpWLpwg5d2NpxIvReoYo1wZwtPp6yN4OMGNe6S-rTtC_LUbRxgU0E0Sgb2GxPtYYK6L3Lzb1aK96xkSfXR4ozf8c8UBWcvqh56NRUeNOLkV_4mgkY2b9J5cMKnpG0nTVKGeC2zfS25wxd06_u09WIfbPBWweVDhL1PO6oVOvseerM8c8V53UbZURNygYYXd360rTgN33clWfVOMBrLD90OOBO_u_0gdKUHqYF4_YcJyR0LOkaDzpCbvQp-LWzDmomFI5uXQsf_nHmij7H5SlDmHRHJB777jCLqFgFHF_14G0QJPdwHN_Qlw\",\"alg\":\"RS256\",\"x5u\":\"https://ids.fokus.fraunhofer.de/.well-known/fullchain.pem\"}}],\"assertionMethod\":[\"did:web:ids.fokus.fraunhofer.de#JWK2020-RSA\"]}");
//        JsonObject actual = trustHandler.handleGetDID(context);
//        assertEquals(expected, actual);
//
//        logger.debug("DID: " + actual);
//
//        testContext.completeNow();
//    }
//
//    @Test
//    @DisplayName("testTrustHandler - Terms and Conditions VC")
//    void testTrustHandlerTermsAndConditions(Vertx vertx, VertxTestContext testContext) {
//        logger.debug("*** testTrustHandler - Terms and Conditions VC");
//
//        TrustHandler trustHandler = new TrustHandler(vertx, config);
//
//        trustHandler.getVCTermsAndConditions()
//                .onSuccess(res -> {
//                    logger.debug("Terms and Conditions VC: " + res);
//                    testContext.completeNow();
//                })
//                .onFailure(error -> {
//                    logger.error("Failed to get VC: " + error.getMessage(), error);
//                    testContext.failNow(error);
//                });
//    }
//
//    @Test
//    @DisplayName("testTrustHandler - Legal Registration Number VC")
//    void testTrustHandlerLegalRegistrationNumber(Vertx vertx, VertxTestContext testContext) {
//        logger.debug("*** testTrustHandler - Legal Registration Number VC");
//
//        TrustHandler trustHandler = new TrustHandler(vertx, config);
//
//        trustHandler.getVCLegalRegistrationNumber()
//                .onSuccess(res -> {
//                    logger.debug("Legal Registration Number VC: " + res);
//                    testContext.completeNow();
//                })
//                .onFailure(error -> {
//                    logger.error("Failed to get VC: " + error.getMessage(), error);
//                    testContext.failNow(error);
//                });
//    }
//
//    @Test
//    @DisplayName("testTrustHandler - Legal Participant")
//    void testTrustHandlerLegalParticipant(Vertx vertx, VertxTestContext testContext) {
//        logger.debug("*** testTrustHandler - Legal Participant");
//
//        TrustHandler trustHandler = new TrustHandler(vertx, config);
//
//        trustHandler.getVCLegalParticipant()
//                .onSuccess(res -> {
//                    logger.debug("Legal Participant VC: " + res);
//                    testContext.completeNow();
//                })
//                .onFailure(error -> {
//                    logger.error("Failed to get VC: " + error.getMessage(), error);
//                    testContext.failNow(error);
//                });
//    }
//
//    @Test
//    @DisplayName("testTrustHandler - Service Offering")
//    void testTrustHandlerServiceOffering(Vertx vertx, VertxTestContext testContext) {
//        logger.debug("*** testTrustHandler - Service Offering");
//
//        TrustHandler trustHandler = new TrustHandler(vertx, config);
//
//        trustHandler.getVCTermsAndConditions()
//                .onSuccess(res -> {
//                    logger.debug("Terms and Conditions VC: " + res);
//                    testContext.completeNow();
//                })
//                .onFailure(error -> {
//                    logger.error("Failed to get VC: " + error.getMessage(), error);
//                    testContext.failNow(error);
//                });
//    }
//
//
//
//    @Test
//    @DisplayName("testTrustHandler - Compliance VC")
//    void testTrustHandlerCompliance(Vertx vertx, VertxTestContext testContext) {
//        logger.debug("*** testTrustHandler - Compliance VC");
//
//        TrustHandler trustHandler = new TrustHandler(vertx, config);
//
//        // Get LRN VC
//        List<JsonObject> vcsList = new ArrayList<JsonObject>();
//        JsonObject legalRegistrationNumberVC = new JsonObject("{\"@context\":[\"https://www.w3.org/2018/credentials/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"type\":[\"VerifiableCredential\"],\"id\":\"https://ids.fokus.fraunhofer.de/app/legal-registration-number.json\",\"issuer\":\"did:web:registration.lab.gaia-x.eu:v1\",\"issuanceDate\":\"2024-08-06T11:58:10.898Z\",\"credentialSubject\":{\"@context\":\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#\",\"type\":\"gx:legalRegistrationNumber\",\"id\":\"https://ids.fokus.fraunhofer.de/app/legal-registration-number.json#cs\",\"gx:vatID\":\"FR09883637795\",\"gx:vatID-countryCode\":\"FR\"},\"evidence\":[{\"gx:evidenceURL\":\"http://ec.europa.eu/taxation_customs/vies/services/checkVatService\",\"gx:executionDate\":\"2024-08-06T11:58:10.898Z\",\"gx:evidenceOf\":\"gx:vatID\"}],\"proof\":{\"type\":\"JsonWebSignature2020\",\"created\":\"2024-08-06T11:58:10.913Z\",\"proofPurpose\":\"assertionMethod\",\"verificationMethod\":\"did:web:registration.lab.gaia-x.eu:v1#X509-JWK2020\",\"jws\":\"eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..IX2SehlW93ygfR7fWyxwOXSI7aD2sUTk9H0HqA1NBSyQxJ9YgtFJP8cnqWFbk60HF5FpX8x8JPdJpmoCdmiyma5rmNTIhy-8HHOJswPo_8Jg7pDHz5UPll86a8rRVxCB5Dt7o5nKzYrwyHVRxUlSgJMdkMWWawSqSrMeqJA5Rdhxvx0IVWQLWgT8DntbECg7EZjMRrlmFf0t7MoqguoiJVa3btYk1JXMcRkGSvEnJcw_rZkDnv3SXxjCdD6pT-1vD0sxHXfipcZO8nmUAKmPjOOdtzAYrYkmurbAG1yMsTw58O5bQYORfobMvWuHuuyJzxnxTiIwVr-JtBuTrkXKwQ\"}}");
//        vcsList.add(legalRegistrationNumberVC);
//        JsonObject termsAndConditionsVC = new JsonObject("{\"@context\":[\"https://www.w3.org/2018/credentials/v1\",\"https://w3id.org/security/suites/jws-2020/v1\",\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#\"],\"type\":\"VerifiableCredential\",\"issuanceDate\":\"2024-08-06T13:57:02.976809\",\"credentialSubject\":{\"type\":\"gx:GaiaXTermsAndConditions\",\"gx:termsAndConditions\":\"The PARTICIPANT signing the Self-Description agrees as follows:\\n- to update its descriptions about any changes, be it technical, organizational, or legal - especially but not limited to contractual in regards to the indicated attributes present in the descriptions.\\n\\nThe keypair used to sign Verifiable Credentials will be revoked where Gaia-X Association becomes aware of any inaccurate statements in regards to the claims which result in a non-compliance with the Trust Framework and policy rules defined in the Policy Rules and Labelling Document (PRLD).\",\"id\":\"https://ids.fokus.fraunhofer.de/app/terms-and-conditions.json#cs\"},\"issuer\":\"did:web:ids.fokus.fraunhofer.de\",\"id\":\"https://ids.fokus.fraunhofer.de/app/terms-and-conditions.json\",\"proof\":{\"type\":\"JsonWebSignature2020\",\"proofPurpose\":\"assertionMethod\",\"verificationMethod\":\"did:web:ids.fokus.fraunhofer.de#JWK2020-RSA\",\"jws\":\"eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..R1cbfnS9d-lm3Ttxo5ByRT0d-rU_LE6hA2SKJQOnumRgEJeZcILh5rY8llqzpxzdWcL2-jFo2EbzoKO3Kah42Yx0rIqdef4a-Hd-ifgp4R28Q4kw7qe647QIFwrRN3uBSdcA9xIxKYEFp7g9PNECj5OyifaHa3l1IA_qSK1_DfaQ3AxnIk0ZU-Y0WO0wjwfS8uNFO2sBHrFQM7Pbr4M2chlo5kThC9QgaPZOTTl04jz36nTsAPBocjmjV2Qm33BwxBgAVBf6TaH2YPZGS9mKmp2WnYiGRToZTHEzyztmfIqmOENeaTlesESUyjWm-yt9GeuKOlFTBlSNHX5V-QkrYA\"}}");
//        vcsList.add(termsAndConditionsVC);
//        JsonObject legalParticipantVC = new JsonObject("{\"@context\":[\"https://www.w3.org/2018/credentials/v1\",\"https://w3id.org/security/suites/jws-2020/v1\",\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#\"],\"type\":[\"VerifiableCredential\"],\"id\":\"https://ids.fokus.fraunhofer.de/app/legal-participant.json\",\"issuer\":\"did:web:ids.fokus.fraunhofer.de\",\"issuanceDate\":\"2024-08-06T13:58:28.721421\",\"credentialSubject\":{\"id\":\"https://ids.fokus.fraunhofer.de/app/legal-participant.json#cs\",\"type\":\"gx:LegalParticipant\",\"gx:legalName\":\"Fraunhofer FOKUS\",\"gx:legalRegistrationNumber\":{\"id\":\"https://ids.fokus.fraunhofer.de/app/legal-registration-number.json#cs\"},\"gx:headquarterAddress\":{\"gx:countrySubdivisionCode\":\"FR-HDF\"},\"gx:legalAddress\":{\"gx:countrySubdivisionCode\":\"FR-HDF\"},\"gx-terms-and-conditions:gaiaxTermsAndConditions\":\"70c1d713215f95191a11d38fe2341faed27d19e083917bc8732ca4fea4976700\"},\"proof\":{\"type\":\"JsonWebSignature2020\",\"proofPurpose\":\"assertionMethod\",\"verificationMethod\":\"did:web:ids.fokus.fraunhofer.de#JWK2020-RSA\",\"jws\":\"eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..j5J8r85ugwTKOxibWxDmsmpZWV_DmgeubQZ2JCqk1WuaTRkfyWjYbTNhL7T0b3Aa-RY8cmv1bCXNIK8MTD_Iz8HpDbAmJmHOzy-VMucAv6TcNH0XS96a9DpcaDNyV3EpzqJHdJDEAstEOqAJ51ibqKnhlg1xmEiP8KL3jk9YktTNvcNrOYH3Uqvvr6q0E5e9GzjY7j20XymY_Vjf8iXhWPugP1yUzyK3jksyP3KydGiK0XHcCnL_MBb9QUT9TIqNU8NqmE8lxwH4cs-TUuJ_hIkSVdtqD2nxtZ5bFB4QWn83C-VbKPiniH_mNXsEqAl7KZSNCODxK5bH1FB2iIZl2A\"}}");
//        vcsList.add(legalParticipantVC);
//        JsonObject serviceOfferingVC = new JsonObject("{\"@context\":[\"https://www.w3.org/2018/credentials/v1\",\"https://w3id.org/security/suites/jws-2020/v1\",\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#\"],\"type\":\"VerifiableCredential\",\"id\":\"https://ids.fokus.fraunhofer.de/app/service-offering.json\",\"issuer\":\"did:web:ids.fokus.fraunhofer.de\",\"issuanceDate\":\"2024-06-14T12:08:56.739Z\",\"credentialSubject\":{\"type\":\"gx:ServiceOffering\",\"id\":\"https://ids.fokus.fraunhofer.de/app/service-offering.json\",\"gx:providedBy\":{\"id\":\"https://ids.fokus.fraunhofer.de/app/service-offering.json\"},\"gx:policy\":\"\",\"gx:termsAndConditions\":{\"gx:URL\":\"http://termsandconds.com\",\"gx:hash\":\"d8402a23de560f5ab34b22d1a142feb9e13b3143\"},\"gx:dataAccountExport\":{\"gx:requestType\":\"API\",\"gx:accessType\":\"digital\",\"gx:formatType\":\"application/json\"}},\"proof\":{\"type\":\"JsonWebSignature2020\",\"proofPurpose\":\"assertionMethod\",\"verificationMethod\":\"did:web:ids.fokus.fraunhofer.de#JWK2020-RSA\",\"jws\":\"eyJhbGciOiJQUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..N82eBsqaxecpQ32Vz8CfCouj0xwoYhsXK4qsiz38FejH43c83s6g8wtbYLB5nHb_1lwHRW9JHgpD8yMe__SgRXjGNTvDcuL7oFTg99dnLBXUq4RyXrLH59S9alsOb3ozbEIDbOrF0I9v1J08Uaf6GTsOL9N4Fzx11vrPiGNwcZIvobSyE05XT2hiR_ee8r-EUYfuf9GI9oM-ho6lvGWThnyrxxziuWzy-sDwiNCKz7B_o_5QyxUEPpRM0FCscgRJiZsu9cb8owcqsBXTntKzmKuanpjurLhPU0NS6x4MvQFAzZoJsrMvgzUGKdOh7ieOW4PV_nFGw1vjKdOTHu0Fkw\"}}");
//        vcsList.add(serviceOfferingVC);
//
//        String vcId = "vcId";
//        trustHandler.getComplianceVC(
//                vcsList, vcId
//        )
//                .onSuccess(res -> {
//                        logger.debug("Success. Result: " + res);
//                        testContext.completeNow();
//                    }
//                )
//                .onFailure(res -> {
//                        logger.debug("Failure. Result: " + res);
//                        testContext.completeNow();
//                    }
//                );
//    }

}
