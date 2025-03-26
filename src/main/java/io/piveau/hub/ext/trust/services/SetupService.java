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
package io.piveau.hub.ext.trust.services;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SetupService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private WebClient client;
    String did;
    String baseUrl;
    VCService vcService;
    ComplianceService complianceService;
    StorageService storageService;
    String storageLocation;

    public SetupService(
            WebClient client,
            String did,
            String baseUrl,
            VCService vcService,
            ComplianceService complianceService,
            StorageService storageService,
            String storageLocation
    ) {
        this.client = client;
        this.did = did;
        this.baseUrl = baseUrl;
        this.vcService = vcService;
        this.complianceService = complianceService;
        this.storageService = storageService;
        this.storageLocation = storageLocation;
    }

    /**
     * Initializes TrustHandler by generating and storing various Verifiable Credentials (VCs).
     *
     * This method performs the following steps:
     * 1. Generates and stores three VCs: Legal Participant, Terms and Conditions, and Legal Registration Number.
     * 2. Submits the VCs to the Gaia-X clearing house for compliance checks.
     * 3. Stores the resulting compliance VC
     *
     * @return Future<JsonObject> representing the compliance VC or an error if any step fails.
     */
    public Future<JsonObject> createSetup() {
        // Create 3 VCs (Legal Participant, Terms and Conditions, Legal Registration Number)
        Future<JsonObject> futureVCLegalParticipant = getVCLegalParticipant();
        Future<JsonObject> futureVCTermsAndConditions = getVCTermsAndConditions();
        Future<JsonObject> futureVCLegalRegistrationNumber = getVCLegalRegistrationNumber();

        // Proceed if Futures completed
        Promise<JsonObject> promise = Promise.promise();
        List<Future> vcFutures = new ArrayList<>();
        vcFutures.add(futureVCLegalParticipant);
        vcFutures.add(futureVCTermsAndConditions);
        vcFutures.add(futureVCLegalRegistrationNumber);
        CompositeFuture.all(vcFutures).onComplete(compositeFuture -> {
            if (compositeFuture.succeeded()) {
                JsonObject VCLegalParticipant = futureVCLegalParticipant.result();
                JsonObject VCTermsAndConditions = futureVCTermsAndConditions.result();
                JsonObject VCLegalRegistrationNumber = futureVCLegalRegistrationNumber.result();

                // Write VCs to StorageService
                Future<Void> writeVCLegalParticipant = storageService.writeFile(storageLocation + "/legal-participant.json", VCLegalParticipant.toString());
                Future<Void> writeVCTermsAndConditions = storageService.writeFile(storageLocation + "/terms-and-conditions.json", VCTermsAndConditions.toString());
                Future<Void> writeVCLegalRegistrationNumber = storageService.writeFile(storageLocation + "/legal-registration-number.json", VCLegalRegistrationNumber.toString());

                // Handle file writes completion
                CompositeFuture.all(writeVCLegalParticipant, writeVCTermsAndConditions, writeVCLegalRegistrationNumber).onComplete(writeComposite -> {
                    if (writeComposite.succeeded()) {
                        // Submit to Compliance
                        List<JsonObject> VCs = new ArrayList<>();
                        VCs.add(VCLegalParticipant);
                        VCs.add(VCTermsAndConditions);
                        VCs.add(VCLegalRegistrationNumber);
                        String vcId = baseUrl + "/trust/setup/compliance.json";
                        complianceService.getComplianceVC(VCs, vcId).onComplete(complianceVCResult -> {
                            if (complianceVCResult.succeeded()) {
                                // Receive and store compliance VC
                                JsonObject complianceVC = complianceVCResult.result();
                                Future<Void> writeComplianceVC = storageService.writeFile(storageLocation + "/compliance.json", complianceVC.toString());

                                // Handle compliance VC write completion
                                writeComplianceVC.onComplete(writeResult -> {
                                    if (writeResult.succeeded()) {
                                        promise.complete(complianceVC);  // Complete the main promise here
                                    } else {
                                        logger.debug("Failed to write Compliance VC: " + writeResult.cause().getMessage());
                                        promise.fail(writeResult.cause());  // Fail the promise if writing fails
                                    }
                                });
                            } else {
                                logger.debug("Failed to get compliance VC: " + complianceVCResult.cause().getMessage());
                                promise.fail(complianceVCResult.cause());  // Fail if compliance VC generation fails
                            }
                        });
                    } else {
                        logger.debug("Failed to write VCs to storage: " + writeComposite.cause().getMessage());
                        promise.fail(writeComposite.cause());  // Fail the promise if writing VCs fails
                    }
                });
            } else {
                logger.debug("CompositeFuture failed: " + compositeFuture.cause().getMessage());
                promise.fail(compositeFuture.cause());  // Fail if initial CompositeFuture fails
            }
        });

        return promise.future();
    }

    /**
     * Creates and returns the Gaia-X Terms and Conditions Verifiable Credential (VC).
     *
     * @return JsonObject representing the Gaia-X Terms and Conditions VC.
     */
    public Future<JsonObject> getVCTermsAndConditions() {
        Promise<JsonObject> promise = Promise.promise();

        // Create VC Document
        JsonObject document = new JsonObject();
        JsonArray context = new JsonArray();
        context.add("https://www.w3.org/2018/credentials/v1");
        context.add("https://w3id.org/security/suites/jws-2020/v1");
        context.add("https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#");
        document.put("@context", context);

        String type = "VerifiableCredential";
        document.put("type", type);

        String id = baseUrl + "/trust/setup/terms-and-conditions.json";
        document.put("id", id);

        String issuanceDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        document.put("issuanceDate", issuanceDate);

        String issuer = did;
        document.put("issuer", issuer);

        JsonObject credentialSubject = new JsonObject();
        credentialSubject.put("type", "gx:GaiaXTermsAndConditions");
        credentialSubject.put("gx:termsAndConditions", "The PARTICIPANT signing the Self-Description agrees as follows:\n- to update its descriptions about any changes, be it technical, organizational, or legal - especially but not limited to contractual in regards to the indicated attributes present in the descriptions.\n\nThe keypair used to sign Verifiable Credentials will be revoked where Gaia-X Association becomes aware of any inaccurate statements in regards to the claims which result in a non-compliance with the Trust Framework and policy rules defined in the Policy Rules and Labelling Document (PRLD).");
        credentialSubject.put("id", id + "#cs");
        document.put("credentialSubject", credentialSubject);

        // Sign VC document
        vcService.signVCDocument(document)
                .onSuccess(vc -> {
                    promise.complete(vc);
                })
                .onFailure(err -> {
                    logger.error("Failed to sign VC document: " + err.getMessage());
                    JsonObject error = new JsonObject().put("error", "Failed to sign VC document").put("details", err.getMessage());
                    promise.fail(error.encodePrettily());
                });

        // Return the future which will be completed or failed
        return promise.future();
    }

    /**
     * Creates and returns the Gaia-X Legal Registration Number Verifiable Credential (VC).
     *
     * This method creates a Legal Registration Number request, sends the request to the Gaia-X Notary, and stores
     * the resulting Legal Registration Number VC.
     *
     * @return JsonObject representing the Gaia-X Legal Registration Number VC.
     */
    public Future<JsonObject> getVCLegalRegistrationNumber() {
        // Render request template
        JsonObject template = new JsonObject();

        JsonArray context = new JsonArray();
        context.add("https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/participant");
        template.put("@context", context);

        template.put("type", "gx:legalRegistrationNumber");
        String csId = baseUrl + "/trust/setup/legal-registration-number.json#cs";
        template.put("id", csId);
        template.put("gx:vatID", "FR09883637795");

        // Request LRN VC
        Promise<JsonObject> promise = Promise.promise();
        String notaryUrl = "https://registrationnumber.notary.lab.gaia-x.eu/v1/registrationNumberVC";
        String lrnVcId = baseUrl + "/trust/setup/legal-registration-number.json";
        client.postAbs(notaryUrl + "?vcid=" + lrnVcId)
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(template, ar -> {
                    if (ar.succeeded()) {
                        JsonObject lrnVC = ar.result().bodyAsJsonObject();
                        promise.complete(lrnVC);
                    } else {
                        logger.debug("*** Unable to retrieve LegalRegistrationNumber: " + ar.cause());
                        promise.fail(ar.cause());
                    }
                });

        return promise.future();
    }

    /**
     * Creates and returns the Gaia-X Legal Participant Verifiable Credential (VC).
     *
     * @return JsonObject representing the Gaia-X Legal Participant VC.
     */
    public Future<JsonObject> getVCLegalParticipant() {
        Promise<JsonObject> promise = Promise.promise();

        // Create VC Document
        JsonObject document = new JsonObject();
        JsonArray context = new JsonArray();
        context.add("https://www.w3.org/2018/credentials/v1");
        context.add("https://w3id.org/security/suites/jws-2020/v1");
        context.add("https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#");
        document.put("@context", context);

        String type = "VerifiableCredential";
        document.put("type", type);

        String id = baseUrl + "/trust/setup/legal-participant.json";
        document.put("id", id);

        String issuanceDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        document.put("issuanceDate", issuanceDate);

        // Issuer
        document.put("issuer", did);

        // Credential Subject
        JsonObject credentialSubject = new JsonObject();
        String credentialSubjectId = baseUrl + "/trust/setup/legal-participant.json#cs";
        credentialSubject.put("id", credentialSubjectId);
        credentialSubject.put("type", "gx:LegalParticipant");
        credentialSubject.put("gx:legalName", "Fraunhofer FOKUS");

        // gx:legalRegistrationNumber
        JsonObject legalRegistrationNumber = new JsonObject();
        String legalRegistrationNumberId = baseUrl + "/trust/setup/legal-registration-number.json#cs";
        legalRegistrationNumber.put("id", legalRegistrationNumberId);
        credentialSubject.put("gx:legalRegistrationNumber", legalRegistrationNumber);

        // gx:headquarterAddress
        // TODO: Update country code
        JsonObject headquarterAddress = new JsonObject();
        headquarterAddress.put("gx:countrySubdivisionCode", "FR-HDF");
        credentialSubject.put("gx:headquarterAddress", headquarterAddress);

        // gx:legalAddress
        // TODO: Update country code
        JsonObject legalAddress = new JsonObject();
        legalAddress.put("gx:countrySubdivisionCode", "FR-HDF");
        credentialSubject.put("gx:legalAddress", legalAddress);

        // gx-terms-and-conditions:gaiaxTermsAndConditions
        credentialSubject.put("gx-terms-and-conditions:gaiaxTermsAndConditions", "70c1d713215f95191a11d38fe2341faed27d19e083917bc8732ca4fea4976700");

        document.put("credentialSubject", credentialSubject);

        // Sign VC document
        vcService.signVCDocument(document)
                .onSuccess(vc -> {
                    promise.complete(vc);
                })
                .onFailure(err -> {
                    JsonObject error = new JsonObject().put("error", "Failed to sign VC document").put("details", err.getMessage());
                    promise.fail(error.encodePrettily());
                });

        // Return the future which will be completed or failed
        return promise.future();
    }

    public Future<JsonObject> getSetupVC(String fileName) {
        Promise<JsonObject> promise = Promise.promise();

        String directoryPath = "trust/setup/";
        Future<String> readSetupVC = storageService.readFile(directoryPath + fileName);
        readSetupVC.onComplete(readResult -> {
            if (readResult.succeeded()) {
                logger.debug("Reading Setup VC succeeded");
                promise.complete(new JsonObject(readResult.result()));
            } else {
                logger.debug("Failed to read Setup VC: " + readResult.cause().getMessage());
                promise.fail(readResult.cause());
            }
        });

        return promise.future();
    }

    /**
     * Stores a Verifiable Credential (VC) as a file in the Trust Core directory.
     *
     * @param fileName the name of the file to be created
     * @param fileContent the content of the file in string format
     * @return Future representing the result of the file write operation
     */
    public Future<JsonObject> postSetupVC(String fileName, String fileContent) {
        Promise<JsonObject> promise = Promise.promise();

        String directoryPath = "trust/setup/";
        Future<Void> writeSetupVC = storageService.writeFile(directoryPath + "/" + fileName, fileContent);
        writeSetupVC.onComplete(writeResult -> {
            if (writeResult.succeeded()) {
                logger.debug("Writing DID succeeded");
                promise.complete(new JsonObject(fileContent));
            } else {
                logger.debug("Failed to write DID: " + writeResult.cause().getMessage());
                promise.fail(writeResult.cause());
            }
        });

        return promise.future();
    }

}
