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
package io.piveau.hub.services.identifiers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.hub.indexing.Indexing;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@ExtendWith(VertxExtension.class)
class EURADOIRegistryTest {

  private AutoCloseable mocks;

  @Mock
  private WebClient client;

  @Mock
  private DatasetHelper dataset;

  @Mock
  private HttpRequest<Buffer> request;

  @Mock
  private HttpResponse<Buffer> response;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  private static JsonObject cfg() {
    return new JsonObject()
        .put("user", "u")
        .put("password", "p")
        .put("api", "https://ra.example/api")
        .put("prefix", "10.1234");
  }

  @Test
  void getName_returnsExpected(Vertx vertx) {
    EURADOIRegistry reg = new EURADOIRegistry(vertx, client, cfg());
    assertEquals("EU RA DOI Registry", reg.getName());
  }

  @Test
  void getIdentifier_existingDoi_skipsAvailability_andUsesRegisterResult(Vertx vertx) {
    EURADOIRegistry real = new EURADOIRegistry(vertx, client, cfg());
    EURADOIRegistry reg = spy(real);

    Identifier existing = new Identifier();
    existing.identifierURI = "https://doi.org/10.1234/oldId";
    existing.identifier = "10.1234/oldId";

    try (MockedStatic<Indexing> idx = mockStatic(Indexing.class)) {
      idx.when(() -> Indexing.indexingDataset(any(), any(), isNull(), eq("en")))
          .thenReturn(minimalIndexedMetadata());

      doAnswer(inv -> {
        String xml = inv.getArgument(0, String.class);
        // basic smoke checks
        assertTrue(xml.contains("<subjects"));
        assertTrue(xml.contains("EuroVoc"));                     // from subjectScheme attr
        assertTrue(xml.contains("http://eurovoc.europa.eu/"));   // from schemeURI attr
        assertTrue(xml.contains("xml:lang=\"en\""));             // keyword/description xml:lang
        assertTrue(xml.contains(">An English abstract.<"));      // description text
        // return the mocked register response
        return Future.succeededFuture(new JsonObject().put("first_doi", existing.identifier));
      }).when(reg).registerDOI(anyString());

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<Identifier>[] out = new AsyncResult[1];
      reg.getIdentifier("https://data.example/ds/1", dataset, List.of(existing))
          .onComplete(ar -> {
            out[0] = ar;
            latch.countDown();
          });

      awaitOk(latch);

      assertNotNull(out[0]);
      assertTrue(out[0].succeeded());
      Identifier id = out[0].result();
      assertEquals("https://doi.org/10.1234/oldId", id.identifierURI);
      assertEquals("10.1234/oldId", id.identifier);
      assertEquals("http://publications.europa.eu/resource/authority/notation-type/DOI", id.schema);
      assertEquals("http://publications.europa.eu/resource/authority/corporate-body/PUBL",
          id.creatorURI);
      assertNotNull(id.issued);
      assertEquals(ZoneOffset.UTC, id.issued.getOffset());
    }
  }

  @Test
  void getIdentifier_noExisting_usesAvailability_andRegisterResult(Vertx vertx) {
    EURADOIRegistry real = new EURADOIRegistry(vertx, client, cfg());
    EURADOIRegistry reg = spy(real);

    // HEAD availability success
    when(client.headAbs(anyString())).thenReturn(request);
    when(request.expect(any())).thenReturn(request);
    doAnswer(inv -> {
      Handler<AsyncResult<HttpResponse<Buffer>>> h = inv.getArgument(0);
      h.handle(Future.succeededFuture(response));
      return null;
    }).when(request).send(any());

    try (MockedStatic<Indexing> idx = mockStatic(Indexing.class)) {
      idx.when(() -> Indexing.indexingDataset(any(), any(), isNull(), eq("en")))
          .thenReturn(minimalIndexedMetadata());

      doAnswer(inv -> {
        String xml = inv.getArgument(0, String.class);
        // basic smoke checks
        assertTrue(xml.contains("<subjects"));
        assertTrue(xml.contains("EuroVoc"));                     // from subjectScheme attr
        assertTrue(xml.contains("http://eurovoc.europa.eu/"));   // from schemeURI attr
        assertTrue(xml.contains("xml:lang=\"en\""));             // keyword/description xml:lang
        assertTrue(xml.contains(">An English abstract.<"));      // description text
        // return the mocked register response
        return Future.succeededFuture(new JsonObject().put("first_doi", "10.1234/abc9999"));
      }).when(reg).registerDOI(anyString());

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<Identifier>[] out = new AsyncResult[1];
      reg.getIdentifier("https://data.example/ds/2", dataset, new ArrayList<>())
          .onComplete(ar -> {
            out[0] = ar;
            latch.countDown();
          });

      awaitOk(latch);
      assertTrue(out[0].succeeded());
      assertEquals("https://doi.org/10.1234/abc9999", out[0].result().identifierURI);
      assertEquals("10.1234/abc9999", out[0].result().identifier);
    }
  }

  @Test
  void getIdentifier_missingCreator_fails_withMessage(Vertx vertx) {
    EURADOIRegistry reg = spy(new EURADOIRegistry(vertx, client, cfg()));

    // Metadata lacks "creator" -> buildRequestXML throws IdentifierException
    JsonObject bad = minimalIndexedMetadata();
    bad.remove("creator");

    try (MockedStatic<Indexing> idx = mockStatic(Indexing.class)) {
      idx.when(() -> Indexing.indexingDataset(any(), any(), isNull(), eq("en")))
          .thenReturn(bad);

      // Ensure DOI retrieval returns something quickly (existing DOI)
      Identifier existing = new Identifier();
      existing.identifierURI = "https://doi.org/10.1234/x";
      existing.identifier = "10.1234/x";

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<Identifier>[] out = new AsyncResult[1];
      reg.getIdentifier("https://data.example/ds/3", dataset, List.of(existing))
          .onComplete(ar -> {
            out[0] = ar;
            latch.countDown();
          });

      awaitOk(latch);
      assertTrue(out[0].failed());
      assertTrue(out[0].cause().getMessage().contains("The dataset has no creator"));
    }
  }

  @Test
  void registerDOI_success_returnsBody(Vertx vertx) {
    when(client.postAbs("https://ra.example/api")).thenReturn(request);
    when(request.expect(any())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.basicAuthentication("u", "p")).thenReturn(request);

    JsonObject body = new JsonObject().put("first_doi", "10.1234/abc");
    when(response.bodyAsJsonObject()).thenReturn(body);

    doAnswer(inv -> {
      Handler<AsyncResult<HttpResponse<Buffer>>> h = inv.getArgument(1);
      h.handle(Future.succeededFuture(response));
      return null;
    }).when(request).sendBuffer(any(Buffer.class), any());

    EURADOIRegistry reg = new EURADOIRegistry(vertx, client, cfg());

    CountDownLatch latch = new CountDownLatch(1);
    final AsyncResult<JsonObject>[] out = new AsyncResult[1];
    reg.registerDOI("<xml/>").onComplete(ar -> {
      out[0] = ar;
      latch.countDown();
    });

    awaitOk(latch);
    assertTrue(out[0].succeeded());
    assertEquals("10.1234/abc", out[0].result().getString("first_doi"));
  }

  @Test
  void registerDOI_failure_propagatesMessage(Vertx vertx) {
    when(client.postAbs("https://ra.example/api")).thenReturn(request);
    when(request.expect(any())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    when(request.basicAuthentication("u", "p")).thenReturn(request);

    doAnswer(inv -> {
      Handler<AsyncResult<HttpResponse<Buffer>>> h = inv.getArgument(1);
      h.handle(Future.failedFuture("nope"));
      return null;
    }).when(request).sendBuffer(any(Buffer.class), any());

    EURADOIRegistry reg = new EURADOIRegistry(vertx, client, cfg());

    CountDownLatch latch = new CountDownLatch(1);
    final AsyncResult<JsonObject>[] out = new AsyncResult[1];
    reg.registerDOI("<xml/>").onComplete(ar -> {
      out[0] = ar;
      latch.countDown();
    });

    awaitOk(latch);
    assertTrue(out[0].failed());
    assertEquals("nope", out[0].cause().getMessage());
  }

  @Test
  void getIdentifierEligibility_flagsAndEligibility(Vertx vertx) {
    EURADOIRegistry reg = new EURADOIRegistry(vertx, client, cfg());

    JsonObject ok = new JsonObject()
        .put("creator", new JsonObject().put("name", "c"))
        .put("publisher", new JsonObject().put("name", "p"))
        .put("issued", "2020");

    JsonObject r1 = reg.getIdentifierEligibility(ok);
    assertEquals("EU RA DOI Registry", r1.getString("identifierName"));
    assertTrue(r1.getBoolean("isEligible"));
    JsonObject e1 = r1.getJsonObject("element");
    assertTrue(e1.getBoolean("creator"));
    assertTrue(e1.getBoolean("publisher"));
    assertTrue(e1.getBoolean("issued"));

    JsonObject bad = new JsonObject()
        .put("creator", new JsonObject()) // missing name
        .put("publisher", new JsonObject().put("name", "p"));

    JsonObject r2 = reg.getIdentifierEligibility(bad);
    assertFalse(r2.getBoolean("isEligible"));
    JsonObject e2 = r2.getJsonObject("element");
    assertFalse(e2.getBoolean("creator"));
    assertTrue(e2.getBoolean("publisher"));
    assertFalse(e2.getBoolean("issued"));
  }

  // ----------------------------- helpers -----------------------------
  private static void awaitOk(CountDownLatch latch) {
    try {
      assertTrue(latch.await(2, TimeUnit.SECONDS), "async did not complete in time");
    } catch (InterruptedException e) {
      fail(e);
    }
  }

  private static JsonObject minimalIndexedMetadata() {
    return new JsonObject()
        .put("creator", new JsonObject().put("name", "Creator Org"))
        .put("publisher", new JsonObject().put("name", "Publisher Org"))
        .put("title", new JsonObject().put("en", "Dataset Title"))
        .put("issued", "2024-05-10")
        // subjects -> EuroVoc entries (label only)
        .put("subject", new JsonArray()
            .add(new JsonObject().put("label", "Agriculture"))
            .add(new JsonObject().put("label", "Climate policy")))
        // keywords -> label + language (uses xml:lang)
        .put("keywords", new JsonArray()
            .add(new JsonObject().put("label", "climate").put("language", "en"))
            .add(new JsonObject().put("label", "κλίμα").put("language", "el")))
        // multilingual descriptions (lang -> text)
        .put("description", new JsonObject()
            .put("en", "An English abstract.")
            .put("el", "Μια ελληνική περίληψη."));
  }

}
