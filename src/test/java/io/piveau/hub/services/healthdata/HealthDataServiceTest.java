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
package io.piveau.hub.services.healthdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@ExtendWith(VertxExtension.class)
class HealthDataServiceTest {

  private AutoCloseable mocks;

  @Mock
  private WebClient client;

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

  @Test
  void fetchWikidataInfo_labelString_success_Qid(Vertx vertx) throws Exception {
    try (MockedStatic<WebClient> webStatic = mockStatic(WebClient.class)) {
      webStatic.when(() -> WebClient.create(any(Vertx.class))).thenReturn(client);

      when(client.getAbs("https://www.wikidata.org/w/api.php")).thenReturn(request);
      stubFluent(request);

      when(response.statusCode()).thenReturn(200);
      JsonObject body = new JsonObject()
          .put("entities", new JsonObject()
              .put("Q123", new JsonObject()
                  .put("labels", new JsonObject()
                      .put("en", new JsonObject().put("value", "Foo Label")))));
      when(response.bodyAsJsonObject()).thenReturn(body);
      stubSendSuccess(request, response);

      HealthDataService svc = new HealthDataService(vertx);
      CompletableFuture<String> fut = svc.fetchWikidataInfo("https://www.wikidata.org/wiki/Q123",
          false);

      String out = fut.get(1, TimeUnit.SECONDS);
      assertEquals("Foo Label", out);

      // Verify params
      verify(request).addQueryParam("action", "wbgetentities");
      verify(request).addQueryParam("ids", "Q123");
      verify(request).addQueryParam("format", "json");
      verify(request).timeout(anyLong()); // default 10s if env not set
    }
  }

  @Test
  void fetchWikidataInfo_json_success_PropertyId(Vertx vertx) throws Exception {
    try (MockedStatic<WebClient> webStatic = mockStatic(WebClient.class)) {
      webStatic.when(() -> WebClient.create(any(Vertx.class))).thenReturn(client);

      when(client.getAbs("https://www.wikidata.org/w/api.php")).thenReturn(request);
      stubFluent(request);

      when(response.statusCode()).thenReturn(200);
      JsonObject body = new JsonObject()
          .put("entities", new JsonObject()
              .put("P1690", new JsonObject()
                  .put("labels", new JsonObject()
                      .put("en", new JsonObject().put("value", "Sports ID")))
                  .put("descriptions", new JsonObject()
                      .put("en", new JsonObject().put("value", "Sports identifier")))
                  .put("aliases", new JsonObject()
                      .put("en", new JsonArray().add(new JsonObject().put("value", "SID"))))));
      when(response.bodyAsJsonObject()).thenReturn(body);
      stubSendSuccess(request, response);

      HealthDataService svc = new HealthDataService(vertx);
      CompletableFuture<String> fut = svc.fetchWikidataInfo(
          "https://www.wikidata.org/wiki/Property:P1690", true);

      String json = fut.get(1, TimeUnit.SECONDS);
      JsonObject out = new JsonObject(json);
      assertEquals("Sports ID", out.getString("label"));
      assertEquals("Sports identifier", out.getString("description"));
      assertEquals("SID", out.getString("alias"));

      verify(request).addQueryParam("ids", "P1690");
    }
  }

  @Test
  void fetchWikidataInfo_json_missingFields_returnsEmptyStrings(Vertx vertx) throws Exception {
    try (MockedStatic<WebClient> webStatic = mockStatic(WebClient.class)) {
      webStatic.when(() -> WebClient.create(any(Vertx.class))).thenReturn(client);

      when(client.getAbs(anyString())).thenReturn(request);
      stubFluent(request);

      when(response.statusCode()).thenReturn(200);
      // entities exists but no en label/description/alias
      JsonObject body = new JsonObject()
          .put("entities", new JsonObject()
              .put("Q42", new JsonObject())); // empty entity
      when(response.bodyAsJsonObject()).thenReturn(body);
      stubSendSuccess(request, response);

      HealthDataService svc = new HealthDataService(vertx);
      String json = svc.fetchWikidataInfo("https://www.wikidata.org/wiki/Q42", true)
          .get(1, TimeUnit.SECONDS);

      JsonObject out = new JsonObject(json);
      assertEquals("", out.getString("label"));
      assertEquals("", out.getString("description"));
      assertEquals("", out.getString("alias"));
    }
  }

  @Test
  void fetchWikidataInfo_label_missing_returnsFallbackMessage(Vertx vertx) throws Exception {
    try (MockedStatic<WebClient> webStatic = mockStatic(WebClient.class)) {
      webStatic.when(() -> WebClient.create(any(Vertx.class))).thenReturn(client);

      when(client.getAbs(anyString())).thenReturn(request);
      stubFluent(request);

      when(response.statusCode()).thenReturn(200);
      // entities present but no en label
      JsonObject body = new JsonObject()
          .put("entities", new JsonObject()
              .put("Q999", new JsonObject()
                  .put("labels", new JsonObject()
                      .put("fr", new JsonObject().put("value", "Quelque")))));
      when(response.bodyAsJsonObject()).thenReturn(body);
      stubSendSuccess(request, response);

      HealthDataService svc = new HealthDataService(vertx);
      String out = svc.fetchWikidataInfo("https://www.wikidata.org/wiki/Q999", false)
          .get(1, TimeUnit.SECONDS);

      assertEquals("No label found", out);
    }
  }

  @Test
  void fetchWikidataInfo_non200_returnsNull(Vertx vertx) throws Exception {
    try (MockedStatic<WebClient> webStatic = mockStatic(WebClient.class)) {
      webStatic.when(() -> WebClient.create(any(Vertx.class))).thenReturn(client);

      when(client.getAbs(anyString())).thenReturn(request);
      stubFluent(request);

      when(response.statusCode()).thenReturn(500);
      when(response.statusMessage()).thenReturn("Internal Error");
      stubSendSuccess(request, response);

      HealthDataService svc = new HealthDataService(vertx);
      String out = svc.fetchWikidataInfo("https://www.wikidata.org/wiki/Q1", false)
          .get(1, TimeUnit.SECONDS);

      assertNull(out);
    }
  }

  @Test
  void fetchWikidataInfo_failure_returnsNull(Vertx vertx) throws Exception {
    try (MockedStatic<WebClient> webStatic = mockStatic(WebClient.class)) {
      webStatic.when(() -> WebClient.create(any(Vertx.class))).thenReturn(client);

      when(client.getAbs(anyString())).thenReturn(request);
      stubFluent(request);
      stubSendFailure(request, new RuntimeException("net down"));

      HealthDataService svc = new HealthDataService(vertx);
      String out = svc.fetchWikidataInfo("https://www.wikidata.org/wiki/Q2", false)
          .get(1, TimeUnit.SECONDS);

      assertNull(out);
    }
  }

  @Test
  void fetchWikidataInfo_invalidUrl_completesExceptionally(Vertx vertx) {
    try (MockedStatic<WebClient> webStatic = mockStatic(WebClient.class)) {
      // Even though client won't be used (we fail early), we still need the static stub
      webStatic.when(() -> WebClient.create(any(Vertx.class))).thenReturn(client);

      HealthDataService svc = new HealthDataService(vertx);
      CompletableFuture<String> fut = svc.fetchWikidataInfo("https://www.wikidata.org/wiki/",
          false);

      assertTrue(fut.isCompletedExceptionally());
      fut.handle((r, ex) -> {
        assertNotNull(ex);
        assertInstanceOf(IllegalArgumentException.class, ex);
        assertTrue(ex.getMessage().contains("Invalid property URL"));
        return null;
      }).join();
    }
  }

  // -------------------- helpers --------------------
  private static void stubFluent(HttpRequest<Buffer> req) {
    when(req.addQueryParam(anyString(), anyString())).thenReturn(req);
    when(req.timeout(anyLong())).thenReturn(req);
  }

  private static void stubSendSuccess(HttpRequest<Buffer> req, HttpResponse<Buffer> resp) {
    doAnswer(inv -> {
      @SuppressWarnings("unchecked")
      var handler = (Handler<AsyncResult<HttpResponse<Buffer>>>) inv.getArgument(0);
      handler.handle(Future.succeededFuture(resp));
      return null;
    }).when(req).send(any());
  }

  private static void stubSendFailure(HttpRequest<Buffer> req, Throwable throwable) {
    doAnswer(inv -> {
      @SuppressWarnings("unchecked")
      var handler = (Handler<AsyncResult<HttpResponse<Buffer>>>) inv.getArgument(0);
      handler.handle(Future.failedFuture(throwable));
      return null;
    }).when(req).send(any());
  }
}
