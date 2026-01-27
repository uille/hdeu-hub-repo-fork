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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import org.junit.jupiter.api.Test;

class VCServiceTest {

  @Test
  void signVCDocument_success_returnsSignedVC() {
    // Arrange: mock Vert.x WebClient chain
    WebClient webClient = mock(WebClient.class);
    @SuppressWarnings("unchecked")
    HttpRequest<Buffer> httpRequest = (HttpRequest<Buffer>) mock(HttpRequest.class);
    @SuppressWarnings("unchecked")
    HttpResponse<Buffer> httpResponse = (HttpResponse<Buffer>) mock(HttpResponse.class);

    when(webClient.postAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);

    // When sendJsonObject is called, invoke the handler with a succeeded AsyncResult
    doAnswer(inv -> {
      Handler<AsyncResult<HttpResponse<Buffer>>> handler = inv.getArgument(1);
      // Build the response body the service expects: {"data": {...signed VC...}}
      JsonObject body = new JsonObject().put("data", new JsonObject().put("signed", true));
      when(httpResponse.bodyAsJsonObject()).thenReturn(body);

      handler.handle(new AsyncResult<>() {
        @Override public HttpResponse<Buffer> result() { return httpResponse; }
        @Override public Throwable cause() { return null; }
        @Override public boolean succeeded() { return true; }
        @Override public boolean failed() { return false; }
      });
      return null;
    }).when(httpRequest).sendJsonObject(any(JsonObject.class), any());

    // Minimal crypto + service setup (keys won’t be used by signVCDocument path)
    RSAKey rsaPrivate = genRsa(2048);
    RSAKey rsaPublic  = rsaPrivate.toPublicJWK();
    VCService svc = new VCService("did:web:example", rsaPrivate, rsaPublic, webClient, "https://signer.example");

    // Act
    JsonObject input = new JsonObject().put("hello", "world");
    JsonObject out = svc.signVCDocument(input).result();

    // Assert
    assertNotNull(out);
    assertTrue(out.getBoolean("signed"));
    verify(webClient).postAbs(eq("https://signer.example/sign"));
    verify(httpRequest).putHeader(eq("Content-Type"), eq("application/json"));
  }

  @Test
  void signVCDocument_failure_propagatesError() {
    WebClient webClient = mock(WebClient.class);
    @SuppressWarnings("unchecked")
    HttpRequest<Buffer> httpRequest = (HttpRequest<Buffer>) mock(HttpRequest.class);

    when(webClient.postAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);

    doAnswer(inv -> {
      Handler<AsyncResult<HttpResponse<Buffer>>> handler = inv.getArgument(1);
      RuntimeException boom = new RuntimeException("sign fail");
      handler.handle(new AsyncResult<>() {
        @Override public HttpResponse<Buffer> result() { return null; }
        @Override public Throwable cause() { return boom; }
        @Override public boolean succeeded() { return false; }
        @Override public boolean failed() { return true; }
      });
      return null;
    }).when(httpRequest).sendJsonObject(any(JsonObject.class), any());

    RSAKey rsaPrivate = genRsa(2048);
    RSAKey rsaPublic  = rsaPrivate.toPublicJWK();
    VCService svc = new VCService("did:web:example", rsaPrivate, rsaPublic, webClient, "https://signer.example");

    Future<JsonObject> fut = svc.signVCDocument(new JsonObject().put("k", "v"));
    assertTrue(fut.failed());
    assertEquals("sign fail", fut.cause().getMessage());
  }

  @Test
  void createAndVerifyJWS_roundTrip_ok() throws JOSEException {
    RSAKey rsaPrivate = genRsa(2048);
    RSAKey rsaPublic  = rsaPrivate.toPublicJWK();
    VCService svc = new VCService("did:web:example", rsaPrivate, rsaPublic, mock(WebClient.class), "http://unused");

    String payload = "test-payload";
    String detachedJws = svc.createJWSSignature(payload, rsaPrivate);

    // Detached serialization should contain two dots (no payload part)
    assertTrue(detachedJws.contains(".."));

    Boolean verified = svc.verifyJWSSignature(payload, detachedJws, rsaPublic);
    assertTrue(verified);
  }

  @Test
  void computeSha256Hash_knownVector_ok() throws NoSuchAlgorithmException {
    String out = VCService.computeSha256Hash("abc");
    // SHA-256("abc")
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", out);
  }

  @Test
  void convertToPEM_containsMarkers() throws JOSEException {
    RSAKey rsaPrivate = genRsa(2048);
    RSAPublicKey pub = rsaPrivate.toRSAPublicKey();
    VCService svc = new VCService("did:web:example", rsaPrivate, rsaPrivate.toPublicJWK(), mock(WebClient.class), "http://unused");

    String pem = svc.convertToPEM(pub);
    assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"));
    assertTrue(pem.contains("\n"));
    assertTrue(pem.endsWith("-----END PUBLIC KEY-----"));
  }

  private static RSAKey genRsa(int bits) {
    try {
      return new RSAKeyGenerator(bits).keyID("kid").generate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
