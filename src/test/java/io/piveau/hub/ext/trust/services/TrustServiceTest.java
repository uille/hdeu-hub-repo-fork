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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.hub.services.resources.ResourcesService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

class TrustServiceTest {

  AutoCloseable mocks;
  Vertx vertx;
  StorageService storageService;
  ComplianceService complianceService;
  VCService vcService;
  ResourcesService resourcesService;
  DatasetsService datasetsService;

  TrustService svc;

  final String did = "did:example:abc";
  final String baseUri = "https://example.org/";
  final String baseUrl = "https://example.com";
  final String type = "dataset";
  final String resourceId = "r1";

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    vertx = mock(Vertx.class); // not used directly by TrustService code paths here
    storageService = mock(StorageService.class);
    complianceService = mock(ComplianceService.class);
    vcService = mock(VCService.class);
    resourcesService = mock(ResourcesService.class);
    datasetsService = mock(DatasetsService.class);

    svc = new TrustService(
        vertx, did, baseUri, baseUrl,
        storageService, complianceService, vcService,
        resourcesService, datasetsService
    );
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mocks != null) mocks.close();
  }

  // ---------- helpers ----------

  private static <T> T awaitOk(Future<T> fut) {
    try {
      return fut.toCompletionStage().toCompletableFuture().get(2, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException("awaitOk failed", e);
    }
  }

  private static String awaitFailMsg(Future<?> fut) {
    try {
      fut.toCompletionStage().toCompletableFuture().get(2, TimeUnit.SECONDS);
      fail("Expected failure");
      return null;
    } catch (ExecutionException ee) {
      return ee.getCause() == null ? null : ee.getCause().getMessage();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Property p(Model m, String uri) { return m.createProperty(uri); }

  /** Build a Jena model containing all triples expected by buildComplianceServiceOffering(...) */
  private Model buildResourceModel() {
    Model m = ModelFactory.createDefaultModel();

    String soUri = baseUri + type + "/" + resourceId;
    Resource so = m.createResource(soUri);

    // schema.org basics
    so.addProperty(p(m,"https://schema.org/description"), "Nice service offering");
    so.addProperty(p(m,"https://schema.org/name"), "SO name");

    // dataAccountExport node
    Resource dae = m.createResource();
    dae.addProperty(p(m,"https://w3id.org/gaia-x/development#accessType"), "API");
    dae.addProperty(p(m,"https://w3id.org/gaia-x/development#formatType"), "json");
    dae.addProperty(p(m,"https://w3id.org/gaia-x/development#requestType"), "online");
    so.addProperty(p(m,"https://w3id.org/gaia-x/development#dataAccountExport"), dae);

    // simple literals
    so.addProperty(p(m,"https://w3id.org/gaia-x/development#dataProtectionRegime"), "GDPR");
    so.addProperty(p(m,"https://w3id.org/gaia-x/development#provisionType"), "pay-per-use");
    so.addProperty(p(m,"https://w3id.org/gaia-x/development#servicePolicy"), "policy-123");

    // endpoint
    Resource endpoint = m.createResource();
    endpoint.addProperty(p(m,"https://w3id.org/gaia-x/development#endpointURL"), "https://api.example.com/v1");
    endpoint.addProperty(p(m,"https://w3id.org/gaia-x/development#formalDescription"), "OpenAPI v3");

    Resource stdConf = m.createResource();
    stdConf.addProperty(p(m,"https://w3id.org/gaia-x/development#publisher"), "ISO");
    stdConf.addProperty(p(m,"https://w3id.org/gaia-x/development#standardReference"), "ISO/IEC 27001");
    stdConf.addProperty(p(m,"https://w3id.org/gaia-x/development#title"), "Security management");
    endpoint.addProperty(p(m,"https://w3id.org/gaia-x/development#standardConformity"), stdConf);
    so.addProperty(p(m,"https://w3id.org/gaia-x/development#endpoint"), endpoint);

    // hostedOn
    Resource hosted = m.createResource();
    hosted.addProperty(p(m,"https://schema.org/description"), "hosted env desc");
    hosted.addProperty(p(m,"https://schema.org/name"), "host platform");
    hosted.addProperty(p(m,"https://w3id.org/gaia-x/development#copyrightOwnedBy"), "ACME");
    hosted.addProperty(p(m,"https://w3id.org/gaia-x/development#license"), "MIT");
    hosted.addProperty(p(m,"https://w3id.org/gaia-x/development#resourcePolicy"), "r-pol");
    so.addProperty(p(m,"https://w3id.org/gaia-x/development#hostedOn"), hosted);

    // keywords
    so.addProperty(p(m,"https://w3id.org/gaia-x/development#keyword"), "k1");
    so.addProperty(p(m,"https://w3id.org/gaia-x/development#keyword"), "k2");

    // terms & conditions node
    Resource tnc = m.createResource();
    tnc.addProperty(p(m,"https://w3id.org/gaia-x/development#hash"), "deadbeef");
    tnc.addProperty(p(m,"https://w3id.org/gaia-x/development#url"), "https://example.com/terms.pdf");
    so.addProperty(p(m,"https://w3id.org/gaia-x/development#serviceOfferingTermsAndConditions"), tnc);

    return m;
  }

  private Buffer modelAsNTriples(Model m) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    RDFDataMgr.write(out, m, RDFFormat.NTRIPLES_UTF8);
    return Buffer.buffer(out.toByteArray());
  }

  private JsonObject minimalSigned(JsonObject raw) {
    // Simulate "signing" by just wrapping
    return new JsonObject()
        .put("signed", true)
        .put("payload", raw);
  }

  // ---------- tests ----------

  @Test
  void createServiceOffering_success_flow() {
    // Arrange: sign passes
    when(vcService.signVCDocument(any(JsonObject.class)))
        .thenAnswer(inv -> Future.succeededFuture(minimalSigned(inv.getArgument(0))));
    // Write VC file OK
    when(storageService.writeFile(eq("trust/service-offering/" + type + "_" + resourceId + ".json"), anyString()))
        .thenReturn(Future.succeededFuture());

    // Read 3 setup VCs OK
    when(storageService.readFile(eq("trust/setup/legal-participant.json")))
        .thenReturn(Future.succeededFuture(new JsonObject().put("lp", 1).encode()));
    when(storageService.readFile(eq("trust/setup/terms-and-conditions.json")))
        .thenReturn(Future.succeededFuture(new JsonObject().put("tc", 1).encode()));
    when(storageService.readFile(eq("trust/setup/legal-registration-number.json")))
        .thenReturn(Future.succeededFuture(new JsonObject().put("lrn", 1).encode()));

    // Compliance OK
    when(complianceService.getComplianceVC(anyList(), anyString()))
        .thenReturn(Future.succeededFuture(new JsonObject().put("compliance", true)));

    // Store compliance OK
    when(storageService.writeFile(eq("trust/service-offering/" + type + "_compliance_" + resourceId + ".json"), anyString()))
        .thenReturn(Future.succeededFuture());

    // Act
    Model model = buildResourceModel();
    JsonObject result = awaitOk(svc.createServiceOffering(model, type, resourceId));

    // Assert
    assertTrue(result.getBoolean("signed"), "Expected signed VC");
    verify(vcService, atLeastOnce()).signVCDocument(any(JsonObject.class));
    verify(complianceService, times(1)).getComplianceVC(anyList(), contains("/trust/" + type + "/" + resourceId));
  }

  @Test
  void createServiceOffering_writeVC_fails_propagates() {
    when(vcService.signVCDocument(any(JsonObject.class)))
        .thenAnswer(inv -> Future.succeededFuture(minimalSigned(inv.getArgument(0))));

    when(storageService.writeFile(eq("trust/service-offering/" + type + "_" + resourceId + ".json"), anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("write vc fail")));

    String msg = awaitFailMsg(svc.createServiceOffering(buildResourceModel(), type, resourceId));
    assertEquals("write vc fail", msg);
  }

  @Test
  void createServiceOffering_readSetup_fails_propagates() {
    when(vcService.signVCDocument(any(JsonObject.class)))
        .thenAnswer(inv -> Future.succeededFuture(minimalSigned(inv.getArgument(0))));
    when(storageService.writeFile(eq("trust/service-offering/" + type + "_" + resourceId + ".json"), anyString()))
        .thenReturn(Future.succeededFuture());

    when(storageService.readFile(eq("trust/setup/legal-participant.json")))
        .thenReturn(Future.failedFuture(new RuntimeException("lp read fail")));
    when(storageService.readFile(eq("trust/setup/terms-and-conditions.json")))
        .thenReturn(Future.succeededFuture("{}"));
    when(storageService.readFile(eq("trust/setup/legal-registration-number.json")))
        .thenReturn(Future.succeededFuture("{}"));

    String msg = awaitFailMsg(svc.createServiceOffering(buildResourceModel(), type, resourceId));
    assertEquals("lp read fail", msg);
  }

  @Test
  void createServiceOffering_compliance_fails_propagates() {
    when(vcService.signVCDocument(any(JsonObject.class)))
        .thenAnswer(inv -> Future.succeededFuture(minimalSigned(inv.getArgument(0))));
    when(storageService.writeFile(eq("trust/service-offering/" + type + "_" + resourceId + ".json"), anyString()))
        .thenReturn(Future.succeededFuture());

    when(storageService.readFile(anyString())).thenReturn(Future.succeededFuture("{}"));

    when(complianceService.getComplianceVC(anyList(), anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("compliance down")));

    String msg = awaitFailMsg(svc.createServiceOffering(buildResourceModel(), type, resourceId));
    assertEquals("compliance down", msg);
  }

  @Test
  void createServiceOffering_writeCompliance_fails_propagates() {
    when(vcService.signVCDocument(any(JsonObject.class)))
        .thenAnswer(inv -> Future.succeededFuture(minimalSigned(inv.getArgument(0))));
    when(storageService.writeFile(eq("trust/service-offering/" + type + "_" + resourceId + ".json"), anyString()))
        .thenReturn(Future.succeededFuture());

    when(storageService.readFile(anyString())).thenReturn(Future.succeededFuture("{}"));

    when(complianceService.getComplianceVC(anyList(), anyString()))
        .thenReturn(Future.succeededFuture(new JsonObject().put("ok", true)));

    when(storageService.writeFile(eq("trust/service-offering/" + type + "_compliance_" + resourceId + ".json"), anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("write compliance fail")));

    String msg = awaitFailMsg(svc.createServiceOffering(buildResourceModel(), type, resourceId));
    assertEquals("write compliance fail", msg);
  }

  @Test
  void getTrustByResource_cached_success_readsNonCompliance() {
    JsonObject cached = new JsonObject().put("cached", true);
    when(storageService.readFile(eq("trust/service-offering/" + type + "_" + resourceId + ".json")))
        .thenReturn(Future.succeededFuture(cached.encode()));

    JsonObject out = awaitOk(svc.getTrustByResource(true, false, type, resourceId));
    assertTrue(out.getBoolean("cached"));
  }

  @Test
  void getTrustByResource_cached_success_readsCompliance() {
    JsonObject cached = new JsonObject().put("compliance", true);
    when(storageService.readFile(eq("trust/service-offering/" + type + "_compliance_" + resourceId + ".json")))
        .thenReturn(Future.succeededFuture(cached.encode()));

    JsonObject out = awaitOk(svc.getTrustByResource(true, true, type, resourceId));
    assertTrue(out.getBoolean("compliance"));
  }

  @Test
  void getTrustByResource_cached_readFails_propagates() {
    when(storageService.readFile(anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("no cache")));

    String msg = awaitFailMsg(svc.getTrustByResource(true, false, type, resourceId));
    assertEquals("no cache", msg);
  }
}
