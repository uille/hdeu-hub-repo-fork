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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

@ExtendWith(VertxExtension.class)
class DIDServiceTest {

  private StorageService storageService;
  private DIDService didService;

  private static final String STORAGE_LOCATION = "trust/did";
  private static final String DID = "did:web:example.org";

  @BeforeEach
  void setUp() {
    storageService = mock(StorageService.class);
    didService = new DIDService(STORAGE_LOCATION, storageService);
  }

  @Test
  void createDID_success_writesFile_andReturnsDocument(VertxTestContext tc) throws Exception {
    // Arrange
    RSAKey rsaPublicKey = new RSAKeyGenerator(2048).keyID("kid-1").generate().toPublicJWK();
    when(storageService.writeFile(anyString(), anyString()))
        .thenReturn(Future.succeededFuture());

    // Act
    didService.createDID(DID, "JWK2020-RSA", rsaPublicKey).onComplete(ar -> {
      try {
        // Assert
        assertTrue(ar.succeeded(), "Future should succeed");
        JsonObject doc = ar.result();

        // Basic structure checks
        assertEquals(DID, doc.getString("id"));
        JsonArray ctx = doc.getJsonArray("@context");
        assertNotNull(ctx);
        assertTrue(ctx.contains("https://www.w3.org/ns/did/v1"));
        assertTrue(ctx.contains("https://w3id.org/security/suites/jws-2020/v1"));

        // verificationMethod present
        JsonArray vms = doc.getJsonArray("verificationMethod");
        assertNotNull(vms);
        assertEquals(1, vms.size());
        JsonObject vm0 = vms.getJsonObject(0);
        assertEquals("JsonWebKey2020", vm0.getString("type"));
        assertEquals(DID, vm0.getString("controller"));
        assertTrue(vm0.getString("id").startsWith(DID + "#"));

        JsonObject jwk = vm0.getJsonObject("publicKeyJwk");
        assertNotNull(jwk);
        assertEquals("RS256", jwk.getString("alg"));
        assertEquals("https://ids.fokus.fraunhofer.de/.well-known/fullchain.pem", jwk.getString("x5u"));

        // File was written in expected path with json containing DID
        ArgumentCaptor<String> pathCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(storageService).writeFile(pathCap.capture(), bodyCap.capture());
        assertEquals("trust/did/did-web-example.org.json", pathCap.getValue());
        assertTrue(bodyCap.getValue().contains("\"id\":\"" + DID + "\""));

        tc.completeNow();
      } catch (Throwable t) {
        tc.failNow(t);
      }
    });
  }

  @Test
  void createDID_unsupportedVerificationMethod_throwsImmediately() throws Exception {
    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> didService.createDID("did:web:example.org", "FOO-UNSUPPORTED", /* rsaPublicKey = */ null)
    );
    assertTrue(ex.getMessage().contains("not currently supported"));
    verifyNoInteractions(storageService);
  }

  @Test
  void createDID_writeFileFails_propagatesFailure(VertxTestContext tc) throws Exception {
    RSAKey rsaPublicKey = new RSAKeyGenerator(2048).generate().toPublicJWK(); // <- 2048
    when(storageService.writeFile(anyString(), anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("disk full")));

    didService.createDID(DID, "JWK2020-RSA", rsaPublicKey).onComplete(ar -> {
      if (ar.succeeded()) {
        tc.failNow("Expected failure");
        return;
      }
      assertNotNull(ar.cause());
      assertTrue(ar.cause().getMessage().contains("disk full"));
      tc.completeNow();
    });
  }

  @Test
  void getDID_success_readsFile_andReturnsParsedJson(VertxTestContext tc) {
    JsonObject stored = new JsonObject()
        .put("@context", new JsonArray().add("https://www.w3.org/ns/did/v1"))
        .put("id", DID);
    when(storageService.readFile(anyString()))
        .thenReturn(Future.succeededFuture(stored.encode()));

    didService.getDID(DID).onComplete(ar -> {
      try {
        assertTrue(ar.succeeded());
        JsonObject doc = ar.result();
        assertEquals(DID, doc.getString("id"));

        ArgumentCaptor<String> pathCap = ArgumentCaptor.forClass(String.class);
        verify(storageService).readFile(pathCap.capture());
        assertEquals("trust/did/did-web-example.org.json", pathCap.getValue());
        tc.completeNow();
      } catch (Throwable t) {
        tc.failNow(t);
      }
    });
  }

  @Test
  void getDID_readFails_propagatesFailure(VertxTestContext tc) {
    when(storageService.readFile(anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("not found")));

    didService.getDID(DID).onComplete(ar -> {
      if (ar.succeeded()) {
        tc.failNow("Expected failure");
        return;
      }
      assertNotNull(ar.cause());
      assertTrue(ar.cause().getMessage().contains("not found"));
      tc.completeNow();
    });
  }
}
