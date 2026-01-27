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
package io.piveau.hub.handler;

import io.piveau.HubRepo;
import io.piveau.hub.services.resources.ResourcesService;
import io.piveau.hub.util.ContentType;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.ParsableMIMEValue;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResourceHandlerTest {

  private final Vertx vertx = mock(Vertx.class);

  private HttpServerRequest stubRequestCommon(RoutingContext ctx) {
    HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_DEEP_STUBS);
    when(req.method()).thenReturn(HttpMethod.GET);
    when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
    when(req.getHeader(anyString())).thenReturn(null);
    when(ctx.request()).thenReturn(req);
    return req;
  }

  private void stubParsedHeaders(RoutingContext ctx, String ct) {
    ParsedHeaderValues ph = mock(ParsedHeaderValues.class, Answers.RETURNS_DEEP_STUBS);
    when(ph.contentType()).thenReturn(new ParsableMIMEValue(ct));
    when(ctx.parsedHeaders()).thenReturn(ph);
  }

  // --- listResourceTypes ---

  @Test
  void listResourceTypes_success_200_json() {
    ResourcesService svc = mock(ResourcesService.class);
    // ✅ stub the method under test
    when(svc.listResourceTypes()).thenReturn(Future.succeededFuture(new JsonArray()));

    try (MockedStatic<ResourcesService> stat = mockStatic(ResourcesService.class)) {
      stat.when(() -> ResourcesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new ResourceHandler(vertx).listResourceTypes(ctx);

      verify(resp).putHeader("Access-Control-Allow-Origin", "*");
      verify(resp).putHeader("Content-Type", "application/json");
      verify(resp).setStatusCode(200);
      verify(resp).end("[]");
    }
  }

  @Test
  void listResourceTypes_failure_400_plain() {
    ResourcesService svc = mock(ResourcesService.class);
    when(svc.listResourceTypes()).thenReturn(Future.failedFuture(new RuntimeException("oops")));

    try (MockedStatic<ResourcesService> stat = mockStatic(ResourcesService.class)) {
      stat.when(() -> ResourcesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new ResourceHandler(vertx).listResourceTypes(ctx);

      verify(resp).putHeader("Access-Control-Allow-Origin", "*");
      verify(resp).putHeader("Content-Type", "text/plain");
      verify(resp).setStatusCode(400);
      verify(resp).end(contains("oops"));
    }
  }

  // --- listResources ---

  @Test
  void listResources_success_200_json() {
    ResourcesService svc = mock(ResourcesService.class);
    // ✅ stub the method under test
    when(svc.listResources(eq("image/png")))
        .thenReturn(Future.succeededFuture(new JsonArray()));

    try (MockedStatic<ResourcesService> stat = mockStatic(ResourcesService.class)) {
      stat.when(() -> ResourcesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);

      // If you have a helper like stubRequestCommon(ctx), you can keep it.
      // Otherwise, minimally stub request + response like this:
      HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_DEEP_STUBS);
      when(req.getParam("type")).thenReturn("image/png");
      when(ctx.request()).thenReturn(req);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new ResourceHandler(vertx).listResources(ctx);

      verify(svc).listResources(eq("image/png"));
      verify(resp).putHeader("Access-Control-Allow-Origin", "*");
      verify(resp).putHeader("Content-Type", "application/json");
      verify(resp).setStatusCode(200);
      verify(resp).end("[]");
    }
  }

  // --- getResource ---

  @Test
  void getResource_success_usesContentNegotiation() {
    // Service mock
    ResourcesService svc = mock(ResourcesService.class);
    when(svc.getResource(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("payload"));

    try (MockedStatic<ResourcesService> stat = mockStatic(ResourcesService.class)) {
      stat.when(() -> ResourcesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      // Context + request/response
      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);

      HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_DEEP_STUBS);
      when(req.method()).thenReturn(HttpMethod.GET);
      when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
      when(req.getHeader("Accept")).thenReturn(null); // no Accept header => default by ContentNegotiation
      when(req.getParam("type")).thenReturn("text");
      when(ctx.request()).thenReturn(req);

      when(ctx.pathParam("id")).thenReturn("res-1");

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      // Execute
      new ResourceHandler(vertx).getResource(ctx);

      // Verify accept fallback + header + body
      verify(svc).getResource(eq(""), eq("text"), eq(ContentType.JSON_LD.getMimeType()));
      verify(resp).putHeader("Access-Control-Allow-Origin", "*");
      verify(resp).putHeader(eq("Content-Type"), eq(ContentType.JSON_LD.getMimeType()));
      verify(resp).end(eq("payload"));
    }
  }

  // --- deleteResource ---

  @Test
  void deleteResource_success_204() {
    ResourcesService svc = mock(ResourcesService.class);
    when(svc.deleteResource(anyString(), anyString())).thenReturn(Future.succeededFuture());

    try (MockedStatic<ResourcesService> stat = mockStatic(ResourcesService.class)) {
      stat.when(() -> ResourcesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerRequest req = stubRequestCommon(ctx);
      when(req.getParam("id")).thenReturn("res-9");
      when(req.getParam("type")).thenReturn("doc");
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new ResourceHandler(vertx).deleteResource(ctx);

      verify(svc).deleteResource(eq("res-9"), eq("doc"));
      verify(resp).putHeader("Access-Control-Allow-Origin", "*");
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  // --- putResource ---

  @Test
  void putResource_created_201_location_and_body() {
    ResourcesService svc = mock(ResourcesService.class);
    when(svc.putResource(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("created"));

    try (MockedStatic<ResourcesService> stat = mockStatic(ResourcesService.class)) {
      stat.when(() -> ResourcesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);

      HttpServerRequest req = stubRequestCommon(ctx);
      when(req.getParam("id")).thenReturn("abc-123");
      when(req.getParam("type")).thenReturn("img");

      stubParsedHeaders(ctx, "text/plain");
      when(ctx.body().asString()).thenReturn("file-content");

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new ResourceHandler(vertx).putResource(ctx);

      verify(svc).putResource(eq("abc-123"), eq("img"), eq("file-content"), eq("text/plain"));
      verify(resp).putHeader("Access-Control-Allow-Origin", "*");
      verify(resp).setStatusCode(201);
      verify(resp).putHeader(eq(HttpHeaders.LOCATION), eq("created"));
      verify(resp).putHeader(eq(HttpHeaders.CONTENT_TYPE), eq("application/json"));
      verify(resp).end(contains("\"id\":\"abc-123\""));
    }
  }

  @Test
  void putResource_updated_200_location_and_body() {
    ResourcesService svc = mock(ResourcesService.class);
    when(svc.putResource(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("updated"));

    try (MockedStatic<ResourcesService> stat = mockStatic(ResourcesService.class)) {
      stat.when(() -> ResourcesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);

      HttpServerRequest req = stubRequestCommon(ctx);
      when(req.getParam("id")).thenReturn("abc-124");
      when(req.getParam("type")).thenReturn("img");

      stubParsedHeaders(ctx, "text/plain");
      when(ctx.body().asString()).thenReturn("file");

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new ResourceHandler(vertx).putResource(ctx);

      verify(resp).setStatusCode(200);
      verify(resp).putHeader(eq(HttpHeaders.LOCATION), eq("updated"));
      verify(resp).putHeader(eq(HttpHeaders.CONTENT_TYPE), eq("application/json"));
      verify(resp).end(contains("\"id\":\"abc-124\""));
    }
  }

  @Test
  void putResource_unexpectedStatus_400_withBody() {
    ResourcesService svc = mock(ResourcesService.class);
    when(svc.putResource(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("weird-status"));

    try (MockedStatic<ResourcesService> stat = mockStatic(ResourcesService.class)) {
      stat.when(() -> ResourcesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);

      HttpServerRequest req = stubRequestCommon(ctx);
      when(req.getParam("id")).thenReturn("abc-125");
      when(req.getParam("type")).thenReturn("img");

      stubParsedHeaders(ctx, "text/plain");
      when(ctx.body().asString()).thenReturn("file");

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new ResourceHandler(vertx).putResource(ctx);

      verify(resp).setStatusCode(400);
      verify(resp).end(eq("weird-status"));
    }
  }

  // --- postResource (delegates to putResource) ---

  @Test
  void postResource_generatesId_and_creates() {
    ResourcesService svc = mock(ResourcesService.class);
    when(svc.putResource(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("created"));

    try (MockedStatic<ResourcesService> stat = mockStatic(ResourcesService.class)) {
      stat.when(() -> ResourcesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);

      // real params to capture generated id
      MultiMap params = MultiMap.caseInsensitiveMultiMap();
      HttpServerRequest req = stubRequestCommon(ctx);
      when(req.params()).thenReturn(params);
      // make getParam read from the map
      when(req.getParam(anyString())).thenAnswer(inv -> params.get((String) inv.getArgument(0)));
      // preset type
      params.add("type", "doc");

      stubParsedHeaders(ctx, "text/plain");
      when(ctx.body().asString()).thenReturn("content");
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new ResourceHandler(vertx).postResource(ctx);

      // an id must have been added and used in putResource
      String generatedId = params.get("id");
      verify(svc).putResource(eq(generatedId), eq("doc"), eq("content"), eq("text/plain"));
      verify(resp).setStatusCode(201);
      verify(resp).putHeader(eq(HttpHeaders.LOCATION), eq("created"));
      verify(resp).putHeader(eq(HttpHeaders.CONTENT_TYPE), eq("application/json"));
      verify(resp).end(contains("\"id\":\"" + generatedId + "\""));
    }
  }

  // Small error-path smoke test
  @Test
  void putResource_failure_callsHubRepo() {
    ResourcesService svc = mock(ResourcesService.class);
    when(svc.putResource(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("boom")));

    try (MockedStatic<ResourcesService> stat = mockStatic(ResourcesService.class);
        MockedStatic<HubRepo> hub = mockStatic(HubRepo.class)) {
      stat.when(() -> ResourcesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerRequest req = stubRequestCommon(ctx);
      when(req.getParam("id")).thenReturn("r-1");
      when(req.getParam("type")).thenReturn("doc");
      stubParsedHeaders(ctx, "text/plain");
      when(ctx.body().asString()).thenReturn("x");
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new ResourceHandler(vertx).putResource(ctx);

      hub.verify(() -> HubRepo.failureResponse(eq(ctx), any()), times(1));
    }
  }
}
