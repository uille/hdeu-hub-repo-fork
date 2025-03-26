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
package io.piveau.hub.ext.trust;

import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import io.piveau.hub.Constants;
import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.hub.ext.trust.services.*;
import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.hub.services.resources.*;
import io.piveau.hub.util.*;
import io.piveau.rdf.*;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.validation.*;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrustHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Vertx vertx;
    private DatasetsService datasetsService;
    private ResourcesService resourcesService;
    private JsonObject config;
    private WebClient client;

    private String baseUri;
    private Model mod = ModelFactory.createDefaultModel();

    public String did;

    public RSAKey rsaPublicKey;
    public StorageService storageService;
    private VCService vcService;
    private ComplianceService complianceService;
    private DIDService didService;
    private SetupService setupService;
    private TrustService trustService;

    public TrustHandler(
            Vertx vertx,
            JsonObject config
    ) {
        this.vertx = vertx;
        this.client = WebClient.create(vertx);
        this.config = config.getJsonObject(Constants.ENV_PIVEAU_TRUST_CONFIG);

        JsonObject schemaConfig = config.getJsonObject(Constants.ENV_PIVEAU_DCATAP_SCHEMA_CONFIG, new JsonObject());
        this.baseUri = schemaConfig.getString("baseUri", "https://piveau.io/") + schemaConfig.getString("resource", "set/resource/");

        resourcesService = ResourcesService.createProxy(vertx, ResourcesService.SERVICE_ADDRESS);
        datasetsService = DatasetsService.createProxy(vertx, DatasetsService.SERVICE_ADDRESS);
        if (this.config.containsKey("key")) {
            try {
                // Get RSA Private and Public Keys
                JWK jwk = JWK.parseFromPEMEncodedObjects(this.config.getString("key"));
                RSAKey rsaPrivateKey = jwk.toRSAKey();
                RSAKey rsaPublicKey = rsaPrivateKey.toPublicJWK();
                this.rsaPublicKey = rsaPublicKey;
                this.did = this.config.getString("did");
                String addressVCSignService = this.config.getString("addressSignService");

                // Setup Storage Service
                FileSystem fileSystem = vertx.fileSystem();
                this.storageService = new StorageService(fileSystem);

                // Setup DID Service
                String pathDid = this.config.getString("pathDid");
                this.didService = new DIDService(
                    pathDid,
                    storageService
                );

                // Setup VC Service (Used for signing/verifying VC documents)
                this.vcService = new VCService(
                    did,
                    rsaPrivateKey,
                    rsaPublicKey,
                    client,
                    addressVCSignService
                );

                // Setup compliance service for submitting/storing compliance VCs to Gaia-X
                this.complianceService = new ComplianceService(
                    client
                );

                // Setup Service to create Setup VCs
                String baseUrl = this.config.getString("address");
                String storageLocation = "trust/setup";
                this.setupService = new SetupService(
                    client,
                    did,
                    baseUrl,
                    vcService,
                    complianceService,
                    storageService,
                    storageLocation
                );

                // Trust Service for ServiceOffering VCs
                this.trustService = new TrustService(
                    vertx,
                    did,
                    this.baseUri,
                    baseUrl,
                    storageService,
                    complianceService,
                    vcService,
                    resourcesService,
                    datasetsService
                );

            } catch (JOSEException e) {
                logger.error("Skipping JWK initialization as 'key' is not configured: " + e.getMessage());
            }
        } else {
            logger.debug("Config does not contain key");
        }

    }

    /*
    ###############################################################################################
    ############################################ DID ##############################################
    ###############################################################################################
    */

    /**
     * Handles HTTP requests to retrieve the Decentralized Identifier (DID) document.
     *
     * This method is an HTTP request handler that responds with the DID document in JSON-LD format.
     *
     * @param context the routing context which provides access to the HTTP request and response
     */
    public void handleGetDID(RoutingContext context) {
        // Get params
        String did = context.request().getParam("did");

        // Create DID
        didService.getDID(did).onComplete(result -> {
            if (result.succeeded()) {
                // If the DID retrieved, respond with the DID document
                JsonObject didDocument = result.result();
                context.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, RDFMimeTypes.JSONLD)
                        .end(didDocument.toString());
            } else {
                // If DID not retrieved, respond with an error
                logger.error("Failed to create DID: " + result.cause());
                context.response().setStatusCode(500).end("Failed to create DID");
            }
        });
    }

    /**
     * Handles HTTP requests to create a Decentralized Identifier (DID) document.
     *
     * @param context the routing context which provides access to the HTTP request and response objects
     */
    public void handleCreateDIDDocument(RoutingContext context) {
        RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
        String did = parameters.queryParameter("did").getString();

        // Create DID
        didService.createDID(did, "JWK2020-RSA", rsaPublicKey).onComplete(result -> {
            if (result.succeeded()) {
                // If the DID creation succeeded, respond with the DID document
                JsonObject didDocument = result.result();
                context.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, RDFMimeTypes.JSONLD)
                        .end(didDocument.toString());
            } else {
                // If the DID creation failed, respond with an error
                logger.error("Failed to create DID: " + result.cause());
                context.response().setStatusCode(500).end("Failed to create DID");
            }
        });
    }

    /*
    ###############################################################################################
    ######################################## SETUP VCs ###########################################
    ###############################################################################################
    */

    /**
     * Handles HTTP requests to initiate the setup process and retrieve the resulting compliance VC.
     *
     * This method is an HTTP request handler that triggers the setup process, which includes generating
     * various Verifiable Credentials (VCs), storing them, and submitting them for compliance verification.
     * It then responds with the compliance VC in JSON-LD format.
     *
     * @param context the routing context which provides access to the HTTP request and response
     */
    public void handleGetSetup(RoutingContext context) {
        setupService.createSetup().onComplete(ar -> {
            if (ar.succeeded()) {
                // Future succeeded, send the response with the resulting JsonObject
                JsonObject complianceVC = ar.result();
                context.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, RDFMimeTypes.JSONLD)
                        .end(complianceVC.encodePrettily());
            } else {
                // Future failed, send an error response
                logger.error("Setup failed", ar.cause());
                context.response()
                        .setStatusCode(500)
                        .end("Failed to complete setup: " + ar.cause().getMessage());
            }
        });
    }

    /**
     * Handles HTTP requests to create and retrieve a Service Offering Verifiable Credential (VC).
     *
     * @param context the routing context which provides access to the HTTP request and response
     */
    public void handleGetSetupVC(RoutingContext context) {
        String fileName = context.pathParam("fileName");
        setupService.getSetupVC(fileName).onComplete(ar -> {
            if (ar.succeeded()) {
                // Future succeeded, send the response with the resulting JsonObject
                JsonObject setupVC = ar.result();
                context.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, RDFMimeTypes.JSONLD)
                        .end(setupVC.encodePrettily());
            } else {
                // Future failed, send an error response
                logger.error("Getting Setup VC Failed", ar.cause());
                context.response()
                        .setStatusCode(500)
                        .end("Failed to complete getting core vc: " + ar.cause().getMessage());
            }
        });
    }

    /*
    ###############################################################################################
    ##################################### SERVICE OFFERING ########################################
    ###############################################################################################
    */

    public void handleGetTrustByResource(RoutingContext context) {
        ContentNegotiation contentNegotiation = new ContentNegotiation(context);
        RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

        boolean cached = parameters.queryParameter("cached").getBoolean();
        boolean compliance = parameters.queryParameter("showCompliance").getBoolean();
        String type = parameters.pathParameter("type").getString();
        String id = contentNegotiation.getId();

        trustService.getTrustByResource(cached, compliance, type, id).onComplete(ar -> {
            if (ar.succeeded()) {
                // Future succeeded, send the response with the resulting JsonObject
                JsonObject setupVC = ar.result();
                context.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, RDFMimeTypes.JSONLD)
                        .end(setupVC.encodePrettily());
            } else {
                // Future failed, send an error response
                logger.error("Getting Trust by Resource Failed", ar.cause());
                context.response()
                        .setStatusCode(500)
                        .end("Failed to complete getting setup vc: " + ar.cause().getMessage());
            }
        });
    }

}
