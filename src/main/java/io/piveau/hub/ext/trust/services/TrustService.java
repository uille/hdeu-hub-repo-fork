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

import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.hub.services.resources.ResourcesService;
import io.piveau.rdf.Piveau;
import io.piveau.rdf.RDFMimeTypes;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TrustService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Model mod = ModelFactory.createDefaultModel();
    private ResourcesService resourcesService;
    private DatasetsService datasetsService;
    Vertx vertx;
    String did;
    String baseUri;
    String baseUrl;
    ComplianceService complianceService;
    StorageService storageService;
    VCService vcService;

    public TrustService(
            Vertx vertx,
            String did,
            String baseUri,
            String baseUrl,
            StorageService storageService,
            ComplianceService complianceService,
            VCService vcService,
            ResourcesService resourcesService,
            DatasetsService datasetsService
    ) {
        this.vertx = vertx;
        this.did = did;
        this.baseUri = baseUri;
        this.baseUrl = baseUrl;
        this.storageService = storageService;
        this.complianceService = complianceService;
        this.vcService = vcService;
        this.resourcesService = resourcesService;
        this.datasetsService = datasetsService;
    }

    public Future<JsonObject> getTrustByResource(
            boolean cached,
            boolean compliance,
            String type,
            String id
    ) {
        Promise<JsonObject> promise = Promise.promise();

        if (cached) {
            String path = getStoragePathforServiceOffering(type, id);
            if (compliance) {
                path = getStoragePathforServiceOfferingCompliance(type, id);
            }

            Future<String> readFile = storageService.readFile(path);
            readFile.onComplete(resource -> {
                if (resource.succeeded()) {
                    JsonObject res = new JsonObject(resource.result());
                    promise.complete(res);
                } else {
                    logger.error("Failed to read Setup VC: " + resource.cause().getMessage());
                    promise.fail(resource.cause());
                }
            });
        } else {
            getResource(id, type).onSuccess(resourceModel -> {
                createServiceOffering(resourceModel, type, id).onComplete(ar -> {
                    if (ar.succeeded()) {
                        JsonObject resourceVC = ar.result();
                        promise.complete(resourceVC);
                    } else {
                        logger.error("Setup failed", ar.cause());
                        promise.fail(ar.cause());
                    }
                });
            }).onFailure(error -> {
                logger.error("Getting resource failed: ", error);
                promise.fail(error);
            });
        }

        return promise.future();
    }

    private Future<Model> getResource(String id, String type) {
        return resourcesService.getResource(id, type, RDFMimeTypes.NTRIPLES)
            .map(resource -> Piveau.toModel(resource.getBytes(), RDFMimeTypes.NTRIPLES));
    }

    private JsonObject buildComplianceServiceOffering(Model sOModel, String type, String resourceId) {
        String graphName = baseUri + type + "/" + resourceId;
        Resource sOResource = sOModel.getResource(graphName);

        // Variables for ServiceOffering Document
        String id = baseUrl + "/trust/service-offering/" + resourceId;
        String csId = id + "#cs";
        String legalParticipantCsId = baseUrl + "/trust/setup" + "/legal-participant.json#cs";

        // Create the @context array
        JsonArray contextArray = new JsonArray();
        contextArray.add("https://www.w3.org/2018/credentials/v1");
        contextArray.add("https://w3id.org/security/suites/jws-2020/v1");
        contextArray.add("https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#");
        contextArray.add(new JsonObject().put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"));
        contextArray.add(new JsonObject().put("xsd", "http://www.w3.org/2001/XMLSchema#"));
        contextArray.add(new JsonObject().put("schema", "https://schema.org/"));
        contextArray.add(new JsonObject().put("vcard", "http://www.w3.org/2006/vcard/ns#"));
        contextArray.add(new JsonObject().put("pv", "https://piveau.eu/ns/voc#"));

        // Create the credentialSubject object
        JsonObject credentialSubject = new JsonObject();
        credentialSubject.put("@id", id);
        credentialSubject.put("@type", "gx:ServiceOffering");
        credentialSubject.put("gx:providedBy", new JsonObject().put("id", legalParticipantCsId));
        // DEBUGGING: This SOResource.getProperty() is returning null
        credentialSubject.put("schema:description", sOResource.getProperty(getCustomProperty("https://schema.org/description")).getString());
        credentialSubject.put("schema:name", sOResource.getProperty(getCustomProperty("https://schema.org/name")).getString());

        // Create gx:dataAccountExport object
        JsonObject dataAccountExport = new JsonObject();
        Resource dataAccountExportRes = sOResource.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#dataAccountExport")).getResource();
        dataAccountExport.put("gx:accessType", dataAccountExportRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#accessType")).getString());
        dataAccountExport.put("gx:formatType", "application/" + dataAccountExportRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#formatType")).getString());
        dataAccountExport.put("gx:requestType", dataAccountExportRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#requestType")).getString());
        credentialSubject.put("gx:dataAccountExport", dataAccountExport);

        credentialSubject.put("gx:dataProtectionRegime", sOResource.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#dataProtectionRegime")).getString());

        // Create gx:endpoint object
        JsonObject endpointURL = new JsonObject();
        Resource endpointRes = sOResource.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#endpoint")).getResource();

        endpointURL.put("@type", "xsd:anyURI");
        endpointURL.put("@value", endpointRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#endpointURL")).getString());

        JsonObject standardReference = new JsonObject();
        standardReference.put("@type", "xsd:anyURI");
        standardReference.put("@value", "https://standard.com");

        JsonObject standardConformity = new JsonObject();
        Resource standardConformityRes = endpointRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#standardConformity")).getResource();

        standardConformity.put("gx:publisher", standardConformityRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#publisher")).getString());
        standardConformity.put("gx:standardReference", standardConformityRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#standardReference")).getString());
        standardConformity.put("gx:title", standardConformityRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#title")).getString());

        JsonObject endpoint = new JsonObject();
        endpoint.put("gx:endpointURL", endpointURL);
        endpoint.put("gx:formalDescription", endpointRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#formalDescription")).getString());
        endpoint.put("gx:standardConformity", standardConformity);
        credentialSubject.put("gx:endpoint", endpoint);

        // Create gx:hostedOn object
        JsonObject hostedOn = new JsonObject();
        Resource hostenOnRes = sOResource.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#hostedOn")).getResource();

        hostedOn.put("schema:description", hostenOnRes.getProperty(getCustomProperty("https://schema.org/description")).getString());
        hostedOn.put("schema:name", hostenOnRes.getProperty(getCustomProperty("https://schema.org/name")).getString());
        hostedOn.put("gx:copyrightOwnedBy", hostenOnRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#copyrightOwnedBy")).getString());
        hostedOn.put("gx:license", hostenOnRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#license")).getString());
        hostedOn.put("gx:resourcePolicy", hostenOnRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#resourcePolicy")).getString());
        credentialSubject.put("gx:hostedOn", hostedOn);

        // Create keyword array
        JsonArray keywords = new JsonArray();
        sOResource.listProperties(getCustomProperty("https://w3id.org/gaia-x/development#keyword")).forEachRemaining(prop -> keywords.add(prop.getString()));
        credentialSubject.put("gx:keyword", keywords);

        credentialSubject.put("gx:provisionType", sOResource.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#provisionType")).getString());
        credentialSubject.put("gx:policy", sOResource.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#servicePolicy")).getString());

        // Create gx:termsAndConditions object
        JsonObject termsAndConditionsURL = new JsonObject();
        Resource termsAndConditionsRes = sOResource.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#serviceOfferingTermsAndConditions")).getResource();
        termsAndConditionsURL.put("@value", termsAndConditionsRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#url")).getString());

        JsonObject termsAndConditions = new JsonObject();
        termsAndConditions.put("gx:hash", termsAndConditionsRes.getProperty(getCustomProperty("https://w3id.org/gaia-x/development#hash")).getString());
        termsAndConditions.put("gx:URL", termsAndConditionsURL);
        credentialSubject.put("gx:termsAndConditions", termsAndConditions);

        // Create the final document
        JsonObject document = new JsonObject();
        document.put("@context", contextArray);
        document.put("type", "VerifiableCredential");
        document.put("id", id);
        document.put("issuer", did);
        document.put("issuanceDate", "2024-06-14T12:08:56.739Z");
        document.put("credentialSubject", credentialSubject);

        return document;
    }

    private Property getCustomProperty(String uri) {
        return mod.createProperty(uri);
    }

    /**
     * Creates a Service Offering Verifiable Credential (VC) and submits it for compliance verification.
     *
     * This method performs the following steps:
     * 1. Creates and stores a Service Offering VC using the provided policy, terms and conditions, and data account export.
     * 2. Submits the Legal Participant, Terms and Conditions, Legal Registration Number, and Service Offering VCs
     *    to the Gaia-X clearing house for compliance verification.
     * 3. Stores the resulting compliance VC.
     *
     * @return Future<JsonObject> representing the compliance VC or an error if any step fails.
     */
    public Future<JsonObject> createServiceOffering(Model resource, String type, String resourceId) {
        Promise<JsonObject> promise = Promise.promise();

        JsonObject rawServiceOffering = buildComplianceServiceOffering(resource, type, resourceId);
        // Create ServiceOffering VC
        getVCServiceOffering(rawServiceOffering)
                .onSuccess(VCServiceOffering -> {
                    // Store VC
                    String directoryPath = "trust/service-offering/";
                    Future<Void> writeStorageFile = storageService.writeFile(getStoragePathforServiceOffering(type, resourceId), VCServiceOffering.toString());

                    writeStorageFile.onComplete(writeRes -> {
                        if (writeRes.succeeded()) {
                            // Read Legal Participant, Terms and Conditions, Legal Registration Number VCs from filesystem
                            String setupDirectoryPath = "trust/setup/";
                            Future<String> futureVCLegalParticipant = storageService.readFile(setupDirectoryPath + "legal-participant.json");
                            Future<String> futureVCTermsAndConditions = storageService.readFile(setupDirectoryPath + "terms-and-conditions.json");
                            Future<String> futureVCLegalRegistrationNumber = storageService.readFile(setupDirectoryPath + "legal-registration-number.json");

                            // Wait for all Futures to complete
                            CompositeFuture.all(futureVCLegalParticipant, futureVCTermsAndConditions, futureVCLegalRegistrationNumber)
                                    .onComplete(compositeFuture -> {
                                        if (compositeFuture.succeeded()) {
                                            try {
                                                // Parse all the JSON objects from the file results
                                                JsonObject VCLegalParticipant = new JsonObject(futureVCLegalParticipant.result());
                                                JsonObject VCTermsAndConditions = new JsonObject(futureVCTermsAndConditions.result());
                                                JsonObject VCLegalRegistrationNumber = new JsonObject(futureVCLegalRegistrationNumber.result());

                                                // Submit to compliance
                                                List<JsonObject> VCs = new ArrayList<>();
                                                VCs.add(VCLegalParticipant);
                                                VCs.add(VCTermsAndConditions);
                                                VCs.add(VCLegalRegistrationNumber);
                                                VCs.add(VCServiceOffering);

                                                String vcId = baseUrl + "/trust/" + type + "/" + resourceId + "?showCompliance=true";
                                                Future<JsonObject> futureComplianceVC = complianceService.getComplianceVC(VCs, vcId);

                                                futureComplianceVC.onSuccess(complianceVC -> {
                                                    // Store the Compliance VC
                                                    storageService.writeFile(getStoragePathforServiceOfferingCompliance(type, resourceId), complianceVC.toString())
                                                            .onComplete(writeComplianceRes -> {
                                                                if (writeComplianceRes.succeeded()) {
                                                                    promise.complete(VCServiceOffering);  // Complete with the Service Offering VC
                                                                } else {
                                                                    logger.debug("Failed to write Compliance VC: " + writeComplianceRes.cause().getMessage());
                                                                    promise.fail(writeComplianceRes.cause());
                                                                }
                                                            });
                                                }).onFailure(err -> {
                                                    logger.debug("Failed to get Compliance VC: " + err.getMessage());
                                                    promise.fail(err);
                                                });
                                            } catch (Exception e) {
                                                logger.error("Error parsing VCs from files", e);
                                                promise.fail(e);
                                            }
                                        } else {
                                            logger.debug("Failed to read one or more VC files: " + compositeFuture.cause().getMessage());
                                            promise.fail(compositeFuture.cause());
                                        }
                                    });
                        } else {
                            logger.debug("Failed to write VC: " + writeRes.cause().getMessage());
                            promise.fail(writeRes.cause());
                        }
                    });
                })
                .onFailure(err -> {
                    logger.debug("Failed to create ServiceOffering VC: " + err.getMessage());
                    promise.fail(err);
                });

        return promise.future();
    }

    private String getStoragePathforServiceOffering(String type, String id) {
        return "trust/service-offering/" + type + "_" + id + ".json";
    }

    private String getStoragePathforServiceOfferingCompliance(String type, String id) {
        return "trust/service-offering/" + type + "_compliance_" + id + ".json";
    }

    /**
     * Creates and returns the Gaia-X ServiceOffering Verifiable Credential (VC).
     *
     * @return JsonObject representing the Gaia-X ServiceOffering VC.
     */
    public Future<JsonObject> getVCServiceOffering(JsonObject rawServiceOffering) {
        Promise<JsonObject> promise = Promise.promise();
        // Sign VC document
        vcService.signVCDocument(rawServiceOffering)
                .onSuccess(vc -> {
                    promise.complete(vc);
                })
                .onFailure(err -> {
                    logger.debug("Sign VC document failure");
                    JsonObject error = new JsonObject().put("error", "Failed to sign VC document").put("details", err.getMessage());
                    promise.fail(error.encodePrettily());
                });

        // Return the future which will be completed or failed
        return promise.future();
    }
}
