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

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.rdf.RdfDataset;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Collections;

public class VCService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private String did;
    private RSAKey rsaPrivateKey;
    private RSAKey rsaPublicKey;
    private WebClient client;
    private String addressVCSignService;

    public VCService(
            String did,
            RSAKey rsaPrivateKey, RSAKey rsaPublicKey,
            WebClient client, String addressVCSignService
    ) {
        this.did = did;
        this.rsaPrivateKey = rsaPrivateKey;
        this.rsaPublicKey = rsaPublicKey;
        // TODO: Client/VCSigningServiceAddress used to send requests to signing service. Ultimately, this should be removed
        this.client = client;
        this.addressVCSignService = addressVCSignService;
    }

    /**
     * Signs a Verifiable Credential (VC) document by sending it to an external signing service.
     *
     * @param document the Verifiable Credential (VC) JSON object to be signed
     * @return a `Future` representing the result of the signing process, which contains the signed VC document
     *         if successful, or an error if the signing fails
     */
    public Future<JsonObject> signVCDocument(JsonObject document) {
        Promise<JsonObject> promise = Promise.promise();

        // Sign Document
        JsonObject payload = new JsonObject()
                .put("document", document)
                .put("verification_method", did + "#JWK2020-RSA");
        client.postAbs(addressVCSignService + "/sign")
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(payload, ar -> {
                    if (ar.succeeded()) {
                        // Return signed VC Document
                        JsonObject vc = ar.result().bodyAsJsonObject().getJsonObject("data");
                        promise.complete(vc);
                    } else {
                        // Return error message
                        logger.debug("*** Unable to sign VC Document: " + ar.cause());
                        promise.fail(ar.cause());
                    }
                });

        return promise.future();
    }

    //    public JsonObject signVCDocument(JsonObject document) {
//        logger.debug("*** Inside signVCDocument");
//        try {
//            // URDNA normalization
//            logger.debug("document: " + document);
//            RdfDataset documentRdf = vertxJsonToRdfDataset(document);
//            logger.debug("documentRdf: " + documentRdf);
//            RdfDataset normalized = RdfNormalize.normalize(documentRdf);
//            logger.debug("normalized: " + normalized);
//
//            // SHA256 hash
//            String normalizedHash = computeSha256Hash(normalized.toString());
//            logger.debug("normalizedHash: " + normalizedHash);
//
//            // Sign using JWS
//            String signature = createJWSSignature(normalizedHash, this.rsaPrivateKey);
//            logger.debug("signature: " + signature);
//
//            // Get proof
//            JsonObject proof = new JsonObject();
//            proof.put("type", "JsonWebSignature2020");
//            proof.put("proofPurpose", "assertionMethod");
//            proof.put("verificationMethod", "did:web:ids.fokus.fraunhofer.de#JWK2020-RSA");
//            proof.put("jws", signature);
//            document.put("proof", proof);
//
//            return document;
//        } catch (Exception e) {
//            logger.debug("An error occurred: " + e.getMessage());
//            return document;
//        }
//    }

    /**
     * Converts a Vert.x JSON object into an RDF dataset.
     *
     * @param vertxJsonObject the Vert.x JSON object to be converted
     * @return an RDF dataset representing the JSON object, or `null` if an error occurs during the conversion
     */
    private RdfDataset vertxJsonToRdfDataset(JsonObject vertxJsonObject) {
        try {
            String jsonString = vertxJsonObject.encode();
            StringReader stringReader = new StringReader(jsonString);
            JsonDocument jsonDocument = JsonDocument.of(stringReader);
            RdfDataset rdfDataset = JsonLd.toRdf(jsonDocument).get();

            return rdfDataset;
        } catch (Exception e) {
            logger.debug("Exception: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a JSON Web Signature (JWS) with an unencoded payload using the specified RSA private key.
     *
     * @param payload the original payload to be signed (typically the normalized hash or content of the VC)
     * @param rsaPrivateKey the RSA private key used to sign the payload
     * @return the JWS as a serialized string in detached mode (the payload is not included in the final JWS string)
     * @throws JOSEException if an error occurs during the signing process
     */
    public String createJWSSignature(String payload, RSAKey rsaPrivateKey) throws JOSEException {
        JWSSigner signer = new RSASSASigner(rsaPrivateKey);

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.PS256)
                .base64URLEncodePayload(false)
                .criticalParams(Collections.singleton("b64"))
                .build();

        JWSObject jwsObject = new JWSObject(header, new Payload(payload));
        jwsObject.sign(signer);

        boolean isDetached = true;
        return jwsObject.serialize(isDetached);
    }

    /**
     * Verifies the signature of a JSON Web Signature (JWS) using the given payload and RSA public key.
     *
     * @param payload the original payload that was signed and now needs to be verified
     * @param jws the serialized JWS (JSON Web Signature) containing the signature to verify
     * @param rsaPublicKey the RSA public key used to verify the signature
     * @return true if the JWS signature is valid, false otherwise
     */
    public Boolean verifyJWSSignature(String payload, String jws, RSAKey rsaPublicKey) {
        JWSObject parsedJWSObject = null;
        try {
            parsedJWSObject = JWSObject.parse(jws, new Payload(payload));
            JWSVerifier verifier = new RSASSAVerifier(rsaPublicKey);
            return parsedJWSObject.verify(verifier);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Computes the SHA-256 hash of a given input string.
     * SHA-256 is a cryptographic hash function that generates a 256-bit (32-byte) hash.
     *
     * @param input the input string to be hashed
     * @return a hexadecimal representation of the SHA-256 hash
     * @throws NoSuchAlgorithmException if the SHA-256 algorithm is not available
     */
    public static String computeSha256Hash(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Converts an RSAPublicKey into a PEM-formatted String.
     * PEM format is a base64-encoded version of the key wrapped with
     * "-----BEGIN PUBLIC KEY-----" and "-----END PUBLIC KEY-----".
     *
     * @param publicKey the RSAPublicKey that needs to be converted to PEM format
     * @return a String representation of the public key in PEM format
     */
    public String convertToPEM(RSAPublicKey publicKey) {
        StringBuilder pemBuilder = new StringBuilder();
        pemBuilder.append("-----BEGIN PUBLIC KEY-----\n");
        pemBuilder.append(Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(publicKey.getEncoded()));
        pemBuilder.append("\n-----END PUBLIC KEY-----");
        return pemBuilder.toString();
    }
}
