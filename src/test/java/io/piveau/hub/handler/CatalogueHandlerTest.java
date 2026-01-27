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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.piveau.HubRepo;
import io.piveau.hub.services.catalogues.CataloguesService;
import io.piveau.hub.util.ContentType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.serviceproxy.ServiceException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

class CatalogueHandlerTest {

  private static Vertx vertx;

  @BeforeAll
  static void init() {
    vertx = Vertx.vertx();
  }

  @AfterAll
  static void teardown() {
    if (vertx != null) {
      vertx.close();
    }
  }

  @Test
  void put_updated_returns204() {
    // service mock
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.putCatalogue(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("updated"));

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class)) {
      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      // params
      RequestParameter pathId = mock(RequestParameter.class);
      when(pathId.getString()).thenReturn("id-2");

      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(pathId);
      when(params.body()).thenReturn(body);

      // response
      HttpServerResponse resp = mock(HttpServerResponse.class);
      when(resp.setStatusCode(anyInt())).thenReturn(resp);

      // ctx (deep stubs for header chain)
      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);
      when(ctx.parsedHeaders().contentType().value()).thenReturn("application/json");

      // act
      new CatalogueHandler(vertx).handlePutCatalogue(ctx);

      // assert
      verify(svc).putCatalogue(eq("id-2"), eq("{}"), eq(ContentType.JSON.getMimeType()));
      verify(resp).setStatusCode(204);
      verify(resp).end();
      verifyNoMoreInteractions(resp);
    }
  }

  @Test
  void put_wrongContentType_callsFailure_415() {
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.putCatalogue(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("created")); // won’t be reached

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {

      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter pathId = mock(RequestParameter.class);
      when(pathId.getString()).thenReturn("id-5");

      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(pathId);
      when(params.body()).thenReturn(body);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(mock(HttpServerResponse.class));
      // simulate invalid/missing content type
      when(ctx.parsedHeaders().contentType()).thenReturn(null);

      new CatalogueHandler(vertx).handlePutCatalogue(ctx);

      hubStatic.verify(() -> HubRepo.failureResponse(eq(ctx),
          argThat(t -> t instanceof ServiceException se && se.failureCode() == 415)));
    }
  }

  @Test
  void put_bodyMissing_callsFailure_400() {
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.putCatalogue(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("created")); // won’t be reached

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {

      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter pathId = mock(RequestParameter.class);
      when(pathId.getString()).thenReturn("id-4");

      RequestParameter emptyBody = mock(RequestParameter.class);
      when(emptyBody.isEmpty()).thenReturn(true);

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(pathId);
      when(params.body()).thenReturn(emptyBody);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(mock(HttpServerResponse.class));

      new CatalogueHandler(vertx).handlePutCatalogue(ctx);

      hubStatic.verify(() -> HubRepo.failureResponse(eq(ctx),
          argThat(t -> t instanceof ServiceException se
              && se.failureCode() == 400
              && "Body required".equals(se.getMessage()))));
    }
  }

  @Test
  void listCatalogues_nonMetadata_forcesJson() {
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.listCatalogues(anyString(), anyString(), any(), any()))
        .thenReturn(Future.succeededFuture("OK"));

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class)) {
      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter limit = mock(RequestParameter.class);
      when(limit.getInteger()).thenReturn(25);

      RequestParameter offset = mock(RequestParameter.class);
      when(offset.getInteger()).thenReturn(5);

      RequestParameter valueType = mock(RequestParameter.class);
      when(valueType.getString()).thenReturn("data"); // not "metadata" => expect JSON

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("limit")).thenReturn(limit);
      when(params.queryParameter("offset")).thenReturn(offset);
      when(params.queryParameter("valueType")).thenReturn(valueType);

      HttpServerResponse resp = mock(HttpServerResponse.class);
      when(resp.setStatusCode(anyInt())).thenReturn(resp);

      // IMPORTANT: putHeader must return the response for chaining
      when(resp.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(resp);
      when(resp.putHeader(anyString(), anyString())).thenReturn(resp);

      // (optional) if your code calls these:
      when(resp.putHeader(any(CharSequence.class), anyString())).thenReturn(resp);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);

      new CatalogueHandler(vertx).handleListCatalogues(ctx);

      verify(svc).listCatalogues(eq(ContentType.JSON.getMimeType()), eq("data"), eq(5), eq(25));
    }
  }


  @Test
  void list_metadata_success_usesJsonLd_andWritesBody() {
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.listCatalogues(anyString(), anyString(), any(), any()))
        .thenReturn(Future.succeededFuture("{\"ok\":true}"));

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class)) {
      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter limit = mock(RequestParameter.class);
      when(limit.getInteger()).thenReturn(10);
      RequestParameter offset = mock(RequestParameter.class);
      when(offset.getInteger()).thenReturn(0);
      RequestParameter valueType = mock(RequestParameter.class);
      when(valueType.getString()).thenReturn("metadata");

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("limit")).thenReturn(limit);
      when(params.queryParameter("offset")).thenReturn(offset);
      when(params.queryParameter("valueType")).thenReturn(valueType);

      HttpServerResponse resp = mock(HttpServerResponse.class);
      when(resp.setStatusCode(anyInt())).then(Answers.RETURNS_SELF);
      when(resp.putHeader(any(CharSequence.class), any(CharSequence.class)))
          .then(Answers.RETURNS_SELF);
      when(resp.putHeader(anyString(), anyString()))
          .then(Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);

      new CatalogueHandler(vertx).handleListCatalogues(ctx);

      verify(svc).listCatalogues(eq(ContentType.JSON_LD.getMimeType()), eq("metadata"), eq(0),
          eq(10));
      verify(resp).putHeader(any(CharSequence.class), any(CharSequence.class));
      verify(resp).end(anyString());
    }
  }

  @Test
  void list_nonMetadata_success_usesJson_andWritesBody() {
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.listCatalogues(anyString(), anyString(), any(), any()))
        .thenReturn(Future.succeededFuture("[]"));

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class)) {
      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter limit = mock(RequestParameter.class);
      when(limit.getInteger()).thenReturn(25);
      RequestParameter offset = mock(RequestParameter.class);
      when(offset.getInteger()).thenReturn(5);
      RequestParameter valueType = mock(RequestParameter.class);
      when(valueType.getString()).thenReturn("data");

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("limit")).thenReturn(limit);
      when(params.queryParameter("offset")).thenReturn(offset);
      when(params.queryParameter("valueType")).thenReturn(valueType);

      HttpServerResponse resp = mock(HttpServerResponse.class);
      when(resp.setStatusCode(anyInt())).then(Answers.RETURNS_SELF);
      when(resp.putHeader(any(CharSequence.class), any(CharSequence.class)))
          .then(Answers.RETURNS_SELF);
      when(resp.putHeader(anyString(), anyString()))
          .then(Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);

      new CatalogueHandler(vertx).handleListCatalogues(ctx);

      verify(svc).listCatalogues(eq(ContentType.JSON.getMimeType()), eq("data"), eq(5), eq(25));
      verify(resp).end(anyString());
    }
  }

  @Test
  void list_failure_callsHubRepoFailure() {
    RuntimeException boom = new RuntimeException("list failed");
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.listCatalogues(anyString(), anyString(), any(), any()))
        .thenReturn(Future.failedFuture(boom));

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {
      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter limit = mock(RequestParameter.class);
      when(limit.getInteger()).thenReturn(1);
      RequestParameter offset = mock(RequestParameter.class);
      when(offset.getInteger()).thenReturn(2);
      RequestParameter valueType = mock(RequestParameter.class);
      when(valueType.getString()).thenReturn("metadata");

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("limit")).thenReturn(limit);
      when(params.queryParameter("offset")).thenReturn(offset);
      when(params.queryParameter("valueType")).thenReturn(valueType);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(mock(HttpServerResponse.class));

      new CatalogueHandler(vertx).handleListCatalogues(ctx);

      hubStatic.verify(() -> HubRepo.failureResponse(eq(ctx), eq(boom)));
    }
  }

  @Test
  void get_failure_callsHubRepoFailure() {
    RuntimeException boom = new RuntimeException("get failed");
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.getCatalogue(anyString(), anyString()))
        .thenReturn(Future.failedFuture(boom));

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {

      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.pathParam("catalogueId")).thenReturn("xyz");
      when(ctx.response()).thenReturn(mock(HttpServerResponse.class));

      new CatalogueHandler(vertx).handleGetCatalogue(ctx);

      hubStatic.verify(() -> HubRepo.failureResponse(eq(ctx), eq(boom)));
    }
  }

  // ---------- putCatalogue ----------

  @Test
  void put_created_returns201() {
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.putCatalogue(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("created"));

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class)) {
      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class);
      when(id.getString()).thenReturn("id-1");
      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{\"a\":1}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(id);
      when(params.body()).thenReturn(body);

      HttpServerResponse resp = mock(HttpServerResponse.class);
      when(resp.setStatusCode(anyInt())).then(Answers.RETURNS_SELF);
      when(resp.end()).thenAnswer(inv -> null);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);
      when(ctx.parsedHeaders().contentType().value()).thenReturn("application/json");

      new CatalogueHandler(vertx).handlePutCatalogue(ctx);

      verify(svc).putCatalogue(eq("id-1"), eq("{\"a\":1}"), eq(ContentType.JSON.getMimeType()));
      verify(resp).setStatusCode(201);
      verify(resp).end();
    }
  }

  @Test
  void put_defaultBranch_callsCtxFail400_andNoEnd() {
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.putCatalogue(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("weird-status"));

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class)) {
      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class);
      when(id.getString()).thenReturn("id-3");
      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(id);
      when(params.body()).thenReturn(body);

      HttpServerResponse resp = mock(HttpServerResponse.class);
      when(resp.setStatusCode(anyInt())).then(Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);
      when(ctx.parsedHeaders().contentType().value()).thenReturn("application/json");

      new CatalogueHandler(vertx).handlePutCatalogue(ctx);

      verify(ctx).fail(400);
      verify(resp, never()).end();
    }
  }

  @Test
  void put_bodyMissing_callsFailure400() {
    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {

      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(mock(CataloguesService.class));

      RequestParameter id = mock(RequestParameter.class);
      when(id.getString()).thenReturn("id-4");
      RequestParameter emptyBody = mock(RequestParameter.class);
      when(emptyBody.isEmpty()).thenReturn(true);

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(id);
      when(params.body()).thenReturn(emptyBody);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(mock(HttpServerResponse.class));

      new CatalogueHandler(vertx).handlePutCatalogue(ctx);

      hubStatic.verify(() -> HubRepo.failureResponse(eq(ctx),
          argThat(t -> t instanceof ServiceException se
              && se.failureCode() == 400
              && "Body required".equals(se.getMessage()))));
    }
  }

  @Test
  void put_wrongContentType_callsFailure415() {
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.putCatalogue(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("created")); // won’t be reached

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {

      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class);
      when(id.getString()).thenReturn("id-5");
      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(id);
      when(params.body()).thenReturn(body);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(mock(HttpServerResponse.class));
      // invalid/missing content-type
      when(ctx.parsedHeaders().contentType()).thenReturn(null);

      new CatalogueHandler(vertx).handlePutCatalogue(ctx);

      hubStatic.verify(() -> HubRepo.failureResponse(eq(ctx),
          argThat(t -> t instanceof ServiceException se && se.failureCode() == 415)));
    }
  }

  @Test
  void put_failure_callsHubRepoFailure() {
    RuntimeException boom = new RuntimeException("put failed");
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.putCatalogue(anyString(), anyString(), anyString()))
        .thenReturn(Future.failedFuture(boom));

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {

      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class);
      when(id.getString()).thenReturn("id-6");
      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(id);
      when(params.body()).thenReturn(body);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(mock(HttpServerResponse.class));
      when(ctx.parsedHeaders().contentType().value()).thenReturn("application/json");

      new CatalogueHandler(vertx).handlePutCatalogue(ctx);

      hubStatic.verify(() -> HubRepo.failureResponse(eq(ctx), eq(boom)));
    }
  }

  // ---------- deleteCatalogue ----------

  @Test
  void delete_success_returns204() {
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.deleteCatalogue(anyString())).thenReturn(Future.succeededFuture());

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class)) {
      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class);
      when(id.getString()).thenReturn("del-1");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(id);

      HttpServerResponse resp = mock(HttpServerResponse.class);
      when(resp.setStatusCode(anyInt())).then(Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);

      new CatalogueHandler(vertx).handleDeleteCatalogue(ctx);

      verify(svc).deleteCatalogue(eq("del-1"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  @Test
  void delete_failure_callsHubRepoFailure() {
    RuntimeException boom = new RuntimeException("delete failed");
    CataloguesService svc = mock(CataloguesService.class);
    when(svc.deleteCatalogue(anyString())).thenReturn(Future.failedFuture(boom));

    try (MockedStatic<CataloguesService> svcStatic = mockStatic(CataloguesService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {

      svcStatic.when(() -> CataloguesService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class);
      when(id.getString()).thenReturn("del-2");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(id);

      RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(mock(HttpServerResponse.class));

      new CatalogueHandler(vertx).handleDeleteCatalogue(ctx);

      hubStatic.verify(() -> HubRepo.failureResponse(eq(ctx), eq(boom)));
    }
  }
}
