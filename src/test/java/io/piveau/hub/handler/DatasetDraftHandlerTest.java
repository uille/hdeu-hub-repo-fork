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
import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.hub.services.drafts.DatasetDraftsService;
import io.piveau.hub.util.ContentNegotiation;
import io.piveau.hub.util.ContentType;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.ParsableMIMEValue;
import io.vertx.serviceproxy.ServiceException;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatasetDraftHandlerTest {

  private final io.vertx.core.Vertx vertx = mock(io.vertx.core.Vertx.class);

  // Helpers for ContentNegotiation in methods that construct it
  private void stubNegotiationBasics(RoutingContext ctx) {
    HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_DEEP_STUBS);
    when(req.method()).thenReturn(HttpMethod.GET);
    when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
    when(req.getHeader(anyString())).thenReturn(null);
    when(ctx.request()).thenReturn(req);
  }

  private void stubParsedHeaders(RoutingContext ctx, String contentType) {
    ParsedHeaderValues parsed = mock(ParsedHeaderValues.class, Answers.RETURNS_DEEP_STUBS);
    when(parsed.contentType()).thenReturn(new ParsableMIMEValue(contentType));
    when(ctx.parsedHeaders()).thenReturn(parsed);
  }

  // ---------------- listDatasetDrafts ----------------

  @Test
  void listDatasetDrafts_filterByProviderTrue_success_json() {
    DatasetDraftsService drafts = mock(DatasetDraftsService.class);

    try (MockedStatic<DatasetDraftsService> draftStatic = mockStatic(DatasetDraftsService.class)) {
      draftStatic.when(() -> DatasetDraftsService.createProxy(eq(vertx), anyString()))
          .thenReturn(drafts);

      // Also needed in constructor
      DatasetsService datasets = mock(DatasetsService.class);
      try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class)) {
        dsStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(datasets);

        RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
        HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
        when(ctx.response()).thenReturn(resp);

        when(ctx.queryParam("filterByProvider")).thenReturn(List.of("true"));
        when(ctx.queryParam("authorizedResources")).thenReturn(List.of("res-1", "res-2"));
        when(ctx.queryParam("provider")).thenReturn(List.of("prov-7"));

        // capture handler and complete successfully
        doAnswer(inv -> {
          var handler = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<Object>>>getArgument(2);
          handler.handle(Future.succeededFuture(new io.vertx.core.json.JsonArray()));
          return null;
        }).when(drafts).listDatasetDrafts(anyList(), anyString(), any());

        new DatasetDraftHandler(vertx).listDatasetDrafts(ctx);

        verify(drafts).listDatasetDrafts(eq(List.of("res-1", "res-2")), eq("prov-7"), any());
        verify(resp).putHeader(eq("Content-Type"), eq("application/json"));
        verify(resp).end(eq("[]"));
      }
    }
  }

  // ---------------- createDatasetDraft ----------------

  @Test
  void createDatasetDraft_created_201() {
    DatasetDraftsService drafts = mock(DatasetDraftsService.class);

    try (MockedStatic<DatasetDraftsService> draftStatic = mockStatic(DatasetDraftsService.class)) {
      draftStatic.when(() -> DatasetDraftsService.createProxy(eq(vertx), anyString()))
          .thenReturn(drafts);

      DatasetsService datasets = mock(DatasetsService.class);
      try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class)) {
        dsStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(datasets);

        RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
        HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
        when(ctx.response()).thenReturn(resp);

        when(ctx.queryParam("catalogue")).thenReturn(List.of("cat-1"));
        when(ctx.queryParam("provider")).thenReturn(List.of("prov-1"));
        stubParsedHeaders(ctx, "application/ld+json");
        when(ctx.body().asString()).thenReturn("{}");

        doAnswer(inv -> {
          var handler = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<String>>>getArgument(4);
          handler.handle(Future.succeededFuture("created"));
          return null;
        }).when(drafts).createDatasetDraft(anyString(), anyString(), anyString(), anyString(), any());

        new DatasetDraftHandler(vertx).createDatasetDraft(ctx);

        verify(drafts).createDatasetDraft(eq("cat-1"), eq("{}"), eq("application/ld+json"), eq("prov-1"), any());
        verify(resp).setStatusCode(201);
        verify(resp).end();
      }
    }
  }

  @Test
  void createDatasetDraft_wrongStatus_400() {
    DatasetDraftsService drafts = mock(DatasetDraftsService.class);

    try (MockedStatic<DatasetDraftsService> draftStatic = mockStatic(DatasetDraftsService.class)) {
      draftStatic.when(() -> DatasetDraftsService.createProxy(eq(vertx), anyString()))
          .thenReturn(drafts);

      DatasetsService datasets = mock(DatasetsService.class);
      try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class)) {
        dsStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(datasets);

        RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
        HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
        when(ctx.response()).thenReturn(resp);

        when(ctx.queryParam("catalogue")).thenReturn(List.of("cat-1"));
        when(ctx.queryParam("provider")).thenReturn(List.of("prov-1"));
        stubParsedHeaders(ctx, "application/ld+json");
        when(ctx.body().asString()).thenReturn("{}");

        doAnswer(inv -> {
          var handler = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<String>>>getArgument(4);
          handler.handle(Future.succeededFuture("weird"));
          return null;
        }).when(drafts).createDatasetDraft(anyString(), anyString(), anyString(), anyString(), any());

        new DatasetDraftHandler(vertx).createDatasetDraft(ctx);

        verify(resp).setStatusCode(400);
        verify(resp).end();
      }
    }
  }

  // ---------------- readDatasetDraft ----------------

  @Test
  void readDatasetDraft_success_setsContentType_andBody() {
    DatasetDraftsService drafts = mock(DatasetDraftsService.class);

    try (MockedStatic<DatasetDraftsService> draftStatic = mockStatic(DatasetDraftsService.class)) {
      draftStatic.when(() -> DatasetDraftsService.createProxy(eq(vertx), anyString()))
          .thenReturn(drafts);

      DatasetsService datasets = mock(DatasetsService.class);
      try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class)) {
        dsStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(datasets);

        RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
        stubNegotiationBasics(ctx);
        when(ctx.pathParam("id")).thenReturn("ds-1");
        when(ctx.queryParam("catalogue")).thenReturn(List.of("cat-1"));

        HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
        when(ctx.response()).thenReturn(resp);

        // ContentNegotiation default with no Accept header -> application/ld+json
        doAnswer(inv -> {
          var handler = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<String>>>getArgument(3);
          handler.handle(Future.succeededFuture("{}"));
          return null;
        }).when(drafts).readDatasetDraft(anyString(), anyString(), anyString(), any());

        new DatasetDraftHandler(vertx).readDatasetDraft(ctx);

        verify(drafts).readDatasetDraft(eq(""), eq("cat-1"),
            eq(ContentType.JSON_LD.getMimeType()), any());
        verify(resp).putHeader(eq("Content-Type"), eq(ContentType.JSON_LD.getMimeType()));
        verify(resp).end(eq("{}"));
      }
    }
  }

  // ---------------- createOrUpdateDatasetDraft ----------------

  @Test
  void createOrUpdateDatasetDraft_created_201() {
    DatasetDraftsService drafts = mock(DatasetDraftsService.class);

    try (MockedStatic<DatasetDraftsService> draftStatic = mockStatic(DatasetDraftsService.class)) {
      draftStatic.when(() -> DatasetDraftsService.createProxy(eq(vertx), anyString()))
          .thenReturn(drafts);

      DatasetsService datasets = mock(DatasetsService.class);
      try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class)) {
        dsStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(datasets);

        RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
        when(ctx.pathParam("id")).thenReturn("ds-7");
        when(ctx.queryParam("catalogue")).thenReturn(List.of("cat-1"));
        when(ctx.queryParam("provider")).thenReturn(List.of("prov-1"));
        stubParsedHeaders(ctx, "application/ld+json");
        when(ctx.body().asString()).thenReturn("{}");

        HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
        when(ctx.response()).thenReturn(resp);

        doAnswer(inv -> {
          var handler = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<String>>>getArgument(5);
          handler.handle(Future.succeededFuture("created"));
          return null;
        }).when(drafts).createOrUpdateDatasetDraft(anyString(), anyString(), anyString(), anyString(), anyString(), any());

        new DatasetDraftHandler(vertx).createOrUpdateDatasetDraft(ctx);

        verify(resp).setStatusCode(201);
        verify(resp).end();
      }
    }
  }

  @Test
  void createOrUpdateDatasetDraft_updated_204() {
    DatasetDraftsService drafts = mock(DatasetDraftsService.class);

    try (MockedStatic<DatasetDraftsService> draftStatic = mockStatic(DatasetDraftsService.class)) {
      draftStatic.when(() -> DatasetDraftsService.createProxy(eq(vertx), anyString()))
          .thenReturn(drafts);

      DatasetsService datasets = mock(DatasetsService.class);
      try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class)) {
        dsStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(datasets);

        RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
        when(ctx.pathParam("id")).thenReturn("ds-7");
        when(ctx.queryParam("catalogue")).thenReturn(List.of("cat-1"));
        when(ctx.queryParam("provider")).thenReturn(List.of()); // empty -> handler uses ""
        stubParsedHeaders(ctx, "application/ld+json");
        when(ctx.body().asString()).thenReturn("{}");

        HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
        when(ctx.response()).thenReturn(resp);

        doAnswer(inv -> {
          var handler = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<String>>>getArgument(5);
          handler.handle(Future.succeededFuture("updated"));
          return null;
        }).when(drafts).createOrUpdateDatasetDraft(anyString(), anyString(), anyString(), anyString(), anyString(), any());

        new DatasetDraftHandler(vertx).createOrUpdateDatasetDraft(ctx);

        verify(resp).setStatusCode(204);
        verify(resp).end();
      }
    }
  }

  // ---------------- deleteDatasetDraft ----------------

  @Test
  void deleteDatasetDraft_success_204() {
    DatasetDraftsService drafts = mock(DatasetDraftsService.class);

    try (MockedStatic<DatasetDraftsService> draftStatic = mockStatic(DatasetDraftsService.class)) {
      draftStatic.when(() -> DatasetDraftsService.createProxy(eq(vertx), anyString()))
          .thenReturn(drafts);

      DatasetsService datasets = mock(DatasetsService.class);
      try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class)) {
        dsStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(datasets);

        RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
        when(ctx.pathParam("id")).thenReturn("ds-5");
        when(ctx.queryParam("catalogue")).thenReturn(List.of("cat-3"));

        HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
        when(ctx.response()).thenReturn(resp);

        doAnswer(inv -> {
          var handler = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<Void>>>getArgument(2);
          handler.handle(Future.succeededFuture());
          return null;
        }).when(drafts).deleteDatasetDraft(anyString(), anyString(), any());

        new DatasetDraftHandler(vertx).deleteDatasetDraft(ctx);

        verify(drafts).deleteDatasetDraft(eq("ds-5"), eq("cat-3"), any());
        verify(resp).setStatusCode(204);
        verify(resp).end();
      }
    }
  }

  // ---------------- publishDatasetDraft ----------------

  @Test
  void publishDatasetDraft_success_204() {
    DatasetDraftsService drafts = mock(DatasetDraftsService.class);
    DatasetsService datasets = mock(DatasetsService.class);

    try (MockedStatic<DatasetDraftsService> draftStatic = mockStatic(DatasetDraftsService.class);
        MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class)) {

      draftStatic.when(() -> DatasetDraftsService.createProxy(eq(vertx), anyString())).thenReturn(drafts);
      dsStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(datasets);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);
      when(ctx.pathParam("id")).thenReturn("ds-9");
      when(ctx.queryParam("catalogue")).thenReturn(List.of("cat-9"));

      // Spy to bypass the actual Jena removal. Just ensure cleaned payload is used.
      DatasetDraftHandler handler = spy(new DatasetDraftHandler(vertx));
      doReturn("cleaned-ntriples").when(handler).removeCatalogRecord(anyString(), anyString());

      // read draft returns some payload
      doAnswer(inv -> {
        var resultHandler = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<String>>>getArgument(3);
        resultHandler.handle(Future.succeededFuture("original-ntriples"));
        return null;
      }).when(drafts).readDatasetDraft(eq("ds-9"), eq("cat-9"), eq("application/n-triples"), any());

      // putDatasetOrigin succeeds
      when(datasets.putDatasetOrigin(eq("ds-9"), eq("cleaned-ntriples"), eq("application/n-triples"), eq("cat-9"), eq(false)))
          .thenReturn(Future.succeededFuture(new io.vertx.core.json.JsonObject().put("status", "updated")));

      // delete draft succeeds
      doAnswer(inv -> {
        var h = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<Void>>>getArgument(2);
        h.handle(Future.succeededFuture());
        return null;
      }).when(drafts).deleteDatasetDraft(eq("ds-9"), eq("cat-9"), any());

      handler.publishDatasetDraft(ctx);

      verify(drafts).readDatasetDraft(eq("ds-9"), eq("cat-9"), eq("application/n-triples"), any());
      verify(datasets).putDatasetOrigin(eq("ds-9"), eq("cleaned-ntriples"), eq("application/n-triples"), eq("cat-9"), eq(false));
      verify(drafts).deleteDatasetDraft(eq("ds-9"), eq("cat-9"), any());
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  // ---------------- hideDataset ----------------

  @Test
  void hideDataset_success_204() {
    DatasetDraftsService drafts = mock(DatasetDraftsService.class);
    DatasetsService datasets = mock(DatasetsService.class);

    try (MockedStatic<DatasetDraftsService> draftStatic = mockStatic(DatasetDraftsService.class);
        MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class)) {

      draftStatic.when(() -> DatasetDraftsService.createProxy(eq(vertx), anyString())).thenReturn(drafts);
      dsStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(datasets);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);
      when(ctx.pathParam("id")).thenReturn("ds-11");
      when(ctx.queryParam("catalogue")).thenReturn(List.of("cat-11"));
      when(ctx.queryParam("provider")).thenReturn(List.of("prov-abc"));

      // getDatasetOrigin returns NT
      when(datasets.getDatasetOrigin(eq("ds-11"), eq("cat-11"), eq("application/n-triples")))
          .thenReturn(Future.succeededFuture("nt-content"));

      // createOrUpdateDraft succeeds
      doAnswer(inv -> {
        var h = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<String>>>getArgument(5);
        h.handle(Future.succeededFuture("created"));
        return null;
      }).when(drafts).createOrUpdateDatasetDraft(eq("ds-11"), eq("cat-11"), eq("nt-content"),
          eq("application/n-triples"), eq("prov-abc"), any());

      // delete origin succeeds
      when(datasets.deleteDatasetOrigin(eq("ds-11"), eq("cat-11")))
          .thenReturn(Future.succeededFuture());

      new DatasetDraftHandler(vertx).hideDataset(ctx);

      verify(datasets).getDatasetOrigin(eq("ds-11"), eq("cat-11"), eq("application/n-triples"));
      verify(drafts).createOrUpdateDatasetDraft(eq("ds-11"), eq("cat-11"), eq("nt-content"),
          eq("application/n-triples"), eq("prov-abc"), any());
      verify(datasets).deleteDatasetOrigin(eq("ds-11"), eq("cat-11"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  @Test
  void hideDataset_success_emptyProvider_204() {
    DatasetDraftsService drafts = mock(DatasetDraftsService.class);
    DatasetsService datasets = mock(DatasetsService.class);

    try (MockedStatic<DatasetDraftsService> draftStatic = mockStatic(DatasetDraftsService.class);
        MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class)) {

      draftStatic.when(() -> DatasetDraftsService.createProxy(eq(vertx), anyString())).thenReturn(drafts);
      dsStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(datasets);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);
      when(ctx.pathParam("id")).thenReturn("ds-12");
      when(ctx.queryParam("catalogue")).thenReturn(List.of("cat-12"));
      when(ctx.queryParam("provider")).thenReturn(List.of()); // empty list => handler uses ""

      when(datasets.getDatasetOrigin(eq("ds-12"), eq("cat-12"), eq("application/n-triples")))
          .thenReturn(Future.succeededFuture("nt-content"));

      doAnswer(inv -> {
        var h = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<String>>>getArgument(5);
        h.handle(Future.succeededFuture("updated"));
        return null;
      }).when(drafts).createOrUpdateDatasetDraft(eq("ds-12"), eq("cat-12"), eq("nt-content"),
          eq("application/n-triples"), eq(""), any());

      when(datasets.deleteDatasetOrigin(eq("ds-12"), eq("cat-12"))).thenReturn(Future.succeededFuture());

      new DatasetDraftHandler(vertx).hideDataset(ctx);

      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  // ---------------- error path smoke test ----------------

  @Test
  void listDatasetDrafts_failure_callsHubRepo() {
    DatasetDraftsService drafts = mock(DatasetDraftsService.class);

    try (MockedStatic<DatasetDraftsService> draftStatic = mockStatic(DatasetDraftsService.class);
        MockedStatic<HubRepo> hub = mockStatic(HubRepo.class)) {

      draftStatic.when(() -> DatasetDraftsService.createProxy(eq(vertx), anyString()))
          .thenReturn(drafts);

      // Also needed in constructor
      try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class)) {
        dsStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString()))
            .thenReturn(mock(DatasetsService.class));

        RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
        when(ctx.queryParam("filterByProvider")).thenReturn(List.of("false"));
        when(ctx.queryParam("authorizedResources")).thenReturn(List.of());
        when(ctx.queryParam("provider")).thenReturn(List.of()); // ignored

        doAnswer(inv -> {
          var h = inv.<io.vertx.core.Handler<io.vertx.core.AsyncResult<Object>>>getArgument(2);
          h.handle(Future.failedFuture(new ServiceException(500, "boom")));
          return null;
        }).when(drafts).listDatasetDrafts(anyList(), anyString(), any());

        new DatasetDraftHandler(vertx).listDatasetDrafts(ctx);

        hub.verify(() -> HubRepo.failureResponse(eq(ctx), any()), times(1));
      }
    }
  }
}
