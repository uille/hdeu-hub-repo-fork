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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SetupServiceTest {

  WebClient webClient;
  VCService vcService;
  ComplianceService complianceService;
  StorageService storageService;

  HttpRequest<Buffer> httpRequest;
  HttpResponse<Buffer> httpResponse;

  SetupService service;

  final String did = "did:web:example.org";
  final String baseUrl = "https://example.org";
  final String storageLocation = "trust/setup";

  @BeforeEach
  void setUp() {
    webClient = mock(WebClient.class);
    vcService = mock(VCService.class);
    complianceService = mock(ComplianceService.class);
    storageService = mock(StorageService.class);
    httpRequest = mock(HttpRequest.class);
    httpResponse = mock(HttpResponse.class);

    service = new SetupService(
        webClient,
        did,
        baseUrl,
        vcService,
        complianceService,
        storageService,
        storageLocation
    );
  }

  // ---------- Helpers for WebClient stubbing ----------

  private void stubPostJsonSuccess(JsonObject bodyToReturn) {
    when(webClient.postAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);

    doAnswer(inv -> {
      var handler = inv.getArgument(1, io.vertx.core.Handler.class);
      when(httpResponse.bodyAsJsonObject()).thenReturn(bodyToReturn);
      handler.handle(io.vertx.core.Future.succeededFuture(httpResponse));
      return null;
    }).when(httpRequest).sendJsonObject(any(), any());
  }

  private void stubPostJsonFailure(Throwable t) {
    when(webClient.postAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);

    doAnswer(inv -> {
      var handler = inv.getArgument(1, io.vertx.core.Handler.class);
      handler.handle(io.vertx.core.Future.failedFuture(t));
      return null;
    }).when(httpRequest).sendJsonObject(any(), any());
  }

  // ---------------- createSetup() ----------------

  @Test
  void createSetup_success_writesVCs_callsCompliance_andStoresCompliance() {
    when(vcService.signVCDocument(any(JsonObject.class)))
        .thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));

    JsonObject lrn = new JsonObject().put("lrn", "ok");
    stubPostJsonSuccess(lrn);

    when(storageService.writeFile(endsWith("/legal-participant.json"), anyString()))
        .thenReturn(Future.succeededFuture());
    when(storageService.writeFile(endsWith("/terms-and-conditions.json"), anyString()))
        .thenReturn(Future.succeededFuture());
    when(storageService.writeFile(endsWith("/legal-registration-number.json"), anyString()))
        .thenReturn(Future.succeededFuture());

    JsonObject compliance = new JsonObject().put("compliance", "vc");
    when(complianceService.getComplianceVC(anyList(), anyString()))
        .thenReturn(Future.succeededFuture(compliance));

    when(storageService.writeFile(endsWith("/compliance.json"), anyString()))
        .thenReturn(Future.succeededFuture());

    JsonObject result = service.createSetup().result();
    assertNotNull(result);
    assertEquals("vc", result.getString("compliance"));

    ArgumentCaptor<String> vcIdCap = ArgumentCaptor.forClass(String.class);
    verify(complianceService).getComplianceVC(anyList(), vcIdCap.capture());
    assertTrue(vcIdCap.getValue().endsWith("/trust/setup/compliance.json"));
  }

  @Test
  void createSetup_writeOfAnyVC_fails_propagates() {
    when(vcService.signVCDocument(any())).thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));
    stubPostJsonSuccess(new JsonObject().put("lrn", "ok"));

    when(storageService.writeFile(endsWith("/legal-participant.json"), anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("disk full")));
    when(storageService.writeFile(endsWith("/terms-and-conditions.json"), anyString()))
        .thenReturn(Future.succeededFuture());
    when(storageService.writeFile(endsWith("/legal-registration-number.json"), anyString()))
        .thenReturn(Future.succeededFuture());

    var fut = service.createSetup();
    assertTrue(fut.failed());
    assertEquals("disk full", fut.cause().getMessage());
  }

  @Test
  void createSetup_complianceCallFails_propagates() {
    when(vcService.signVCDocument(any())).thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));
    stubPostJsonSuccess(new JsonObject().put("lrn", "ok"));
    when(storageService.writeFile(anyString(), anyString())).thenReturn(Future.succeededFuture());
    when(complianceService.getComplianceVC(anyList(), anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("clearing house down")));

    var fut = service.createSetup();
    assertTrue(fut.failed());
    assertEquals("clearing house down", fut.cause().getMessage());
  }

  @Test
  void createSetup_complianceWriteFails_propagates() {
    when(vcService.signVCDocument(any())).thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));
    stubPostJsonSuccess(new JsonObject().put("lrn", "ok"));
    when(storageService.writeFile(endsWith(".json"), anyString()))
        .thenReturn(Future.succeededFuture());
    when(complianceService.getComplianceVC(anyList(), anyString()))
        .thenReturn(Future.succeededFuture(new JsonObject().put("ok", true)));
    when(storageService.writeFile(endsWith("/compliance.json"), anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("cannot save compliance")));

    var fut = service.createSetup();
    assertTrue(fut.failed());
    assertEquals("cannot save compliance", fut.cause().getMessage());
  }

  // -------- getVCTermsAndConditions() --------

  @Test
  void getVCTermsAndConditions_signOk_returnsSigned() {
    when(vcService.signVCDocument(any(JsonObject.class)))
        .thenAnswer(inv -> Future.succeededFuture(new JsonObject(((JsonObject) inv.getArgument(0)).getMap())
            .put("signed", true)));

    JsonObject vc = service.getVCTermsAndConditions().result();
    assertTrue(vc.getBoolean("signed"));
    assertEquals("VerifiableCredential", vc.getString("type"));
    assertNotNull(vc.getString("issuanceDate"));
    assertEquals(did, vc.getString("issuer"));
  }

  @Test
  void getVCTermsAndConditions_signFails_propagates() {
    when(vcService.signVCDocument(any(JsonObject.class)))
        .thenReturn(Future.failedFuture(new RuntimeException("sign boom")));

    var fut = service.getVCTermsAndConditions();
    assertTrue(fut.failed());
    assertTrue(fut.cause().getMessage().contains("Failed to sign VC document"));
  }

  // -------- getVCLegalParticipant() --------

  @Test
  void getVCLegalParticipant_signOk_returnsSigned() {
    when(vcService.signVCDocument(any(JsonObject.class)))
        .thenAnswer(inv -> Future.succeededFuture(new JsonObject(((JsonObject) inv.getArgument(0)).getMap())
            .put("signed", true)));

    JsonObject vc = service.getVCLegalParticipant().result();
    assertTrue(vc.getBoolean("signed"));
    assertEquals("VerifiableCredential", vc.getString("type"));
    assertEquals(did, vc.getString("issuer"));
    JsonObject cs = vc.getJsonObject("credentialSubject");
    assertNotNull(cs);
    assertEquals("gx:LegalParticipant", cs.getString("type"));
  }

  @Test
  void getVCLegalParticipant_signFails_propagates() {
    when(vcService.signVCDocument(any(JsonObject.class)))
        .thenReturn(Future.failedFuture(new RuntimeException("sign fail")));

    var fut = service.getVCLegalParticipant();
    assertTrue(fut.failed());
    assertTrue(fut.cause().getMessage().contains("Failed to sign VC document"));
  }

  // -------- getVCLegalRegistrationNumber() --------

  @Test
  void getVCLegalRegistrationNumber_success_returnsFromNotary() {
    JsonObject fromNotary = new JsonObject().put("vc", "lrn");
    stubPostJsonSuccess(fromNotary);

    JsonObject res = service.getVCLegalRegistrationNumber().result();
    assertEquals("lrn", res.getString("vc"));

    ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
    verify(webClient).postAbs(urlCap.capture());
    assertTrue(urlCap.getValue().contains("registrationNumberVC"));
  }

  @Test
  void getVCLegalRegistrationNumber_httpFails_propagates() {
    stubPostJsonFailure(new RuntimeException("network"));
    var fut = service.getVCLegalRegistrationNumber();
    assertTrue(fut.failed());
    assertEquals("network", fut.cause().getMessage());
  }

  // -------- getSetupVC() --------

  @Test
  void getSetupVC_readOk_returnsJson() {
    String fileName = "legal-participant.json";
    JsonObject stored = new JsonObject().put("hello", "world");
    when(storageService.readFile("trust/setup/" + fileName))
        .thenReturn(Future.succeededFuture(stored.encode()));

    JsonObject res = service.getSetupVC(fileName).result();
    assertEquals("world", res.getString("hello"));
  }

  @Test
  void getSetupVC_readFails_propagates() {
    when(storageService.readFile("trust/setup/missing.json"))
        .thenReturn(Future.failedFuture(new RuntimeException("not found")));

    var fut = service.getSetupVC("missing.json");
    assertTrue(fut.failed());
    assertEquals("not found", fut.cause().getMessage());
  }

  // -------- postSetupVC() --------

  @Test
  void postSetupVC_writeOk_returnsJson() {
    String body = new JsonObject().put("k", "v").encode();
    when(storageService.writeFile(eq("trust/setup/" + "/" + "x.json"), eq(body)))
        .thenReturn(Future.succeededFuture());

    JsonObject res = service.postSetupVC("x.json", body).result();
    assertEquals("v", res.getString("k"));
  }

  @Test
  void postSetupVC_writeFails_propagates() {
    String body = new JsonObject().put("k", "v").encode();
    when(storageService.writeFile(anyString(), anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("io error")));

    var fut = service.postSetupVC("x.json", body);
    assertTrue(fut.failed());
    assertEquals("io error", fut.cause().getMessage());
  }
}
