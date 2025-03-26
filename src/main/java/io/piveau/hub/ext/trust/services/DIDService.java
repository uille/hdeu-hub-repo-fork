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

import com.nimbusds.jose.jwk.RSAKey;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DIDService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    StorageService storageService;
    String storageLocation;

    public DIDService(String storageLocation, StorageService storageService) {
        this.storageLocation = storageLocation;
        this.storageService = storageService;
    }

    // Creates and stores DID Document
    public Future<JsonObject> createDID(String did, String verificationMethodType, RSAKey rsaPublicKey) throws IllegalArgumentException {
        Promise<JsonObject> promise = Promise.promise();

        // Check if verificationMethodType currently supported
        JsonArray supportedVerificationMethods = new JsonArray()
            .add("JWK2020-RSA");
        if (!supportedVerificationMethods.contains(verificationMethodType)) {
            throw new IllegalArgumentException("Verification method type '" + verificationMethodType + "' is not currently supported.");
        }

        // Create DID Document with context and id
        JsonObject didDocument = new JsonObject()
            .put("@context", new JsonArray()
                .add("https://www.w3.org/ns/did/v1") 
                .add("https://w3id.org/security/suites/jws-2020/v1"))
            .put("id", did);

        // Verification Method
        if ("JWK2020-RSA".equals(verificationMethodType)) {
            JsonObject publicKeyJwkJSON = new JsonObject(rsaPublicKey.toJSONString());

            publicKeyJwkJSON
                .put("alg", "RS256")
                .put("x5u", "https://ids.fokus.fraunhofer.de/.well-known/fullchain.pem");

            JsonObject verificationMethodObject = new JsonObject()
                .put("id", did + "#JWK2020-RSA")
                .put("type", "JsonWebKey2020")
                .put("controller", did)
                .put("publicKeyJwk", publicKeyJwkJSON);

            // Add the verificationMethod and assertionMethod to the DID document
            didDocument
                .put("verificationMethod", new JsonArray().add(verificationMethodObject))
                .put("assertionMethod", new JsonArray().add(did + "#" + verificationMethodType));
        }

        // Store DID Document
        String fileName = storageLocation + "/" + did.replace(":", "-") + ".json";
        String file = didDocument.toString();
        Future<Void> writeDid = storageService.writeFile(fileName, file);
        writeDid.onComplete(res -> {
            if (res.succeeded()) {
                promise.complete(didDocument);
            } else {
                logger.debug("Failed to write DID: " + res.cause().getMessage());
                promise.fail(res.cause());
            }
        });

        return promise.future();
    }

    // Get DID Document
    public Future<JsonObject> getDID(String did) {
        Promise<JsonObject> promise = Promise.promise();

        String fileName = storageLocation + "/" + did.replace(":", "-") + ".json";
        storageService.readFile(fileName).onComplete(readResult -> {
            if (readResult.succeeded()) {
                String fileContent = readResult.result();
                JsonObject didDocument = new JsonObject(fileContent);
                promise.complete(didDocument);
            } else {
                logger.error("Failed to read DID file", readResult.cause());
                promise.fail(readResult.cause());
            }
        });

        return promise.future();
    }
}
