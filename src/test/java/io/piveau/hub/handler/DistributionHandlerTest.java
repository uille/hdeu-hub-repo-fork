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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.piveau.HubRepo;
import io.piveau.hub.services.distributions.DistributionsService;
import io.piveau.hub.util.ContentType;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

class DistributionHandlerTest {

  private final io.vertx.core.Vertx vertx = mock(io.vertx.core.Vertx.class);

  // ---------- helpers ----------

  private HttpServerRequest stubRequestCommon(RoutingContext ctx) {
    HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_DEEP_STUBS);
    when(req.method()).thenReturn(HttpMethod.GET);
    when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
    when(req.getHeader(anyString())).thenReturn(null);
    when(req.absoluteURI()).thenReturn("http://example.test/datasets/ds-1/distributions");
    when(ctx.request()).thenReturn(req);
    return req;
  }

  private void stubParsedHeaders(RoutingContext ctx, String contentType) {
    ParsedHeaderValues parsed = mock(ParsedHeaderValues.class, Answers.RETURNS_DEEP_STUBS);
    when(parsed.contentType().value()).thenReturn(contentType);
    when(ctx.parsedHeaders()).thenReturn(parsed);
  }

  // ---------- tests ----------

  @Test
  void handleListDatasetDistributions_nonMetadata_acceptTypeRemainsAsCaptured_callsService_andEnds() {
    DistributionsService svc = mock(DistributionsService.class);
    when(svc.listDatasetDistributions(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("[]"));

    try (MockedStatic<DistributionsService> svcStatic = mockStatic(DistributionsService.class)) {
      svcStatic.when(() -> DistributionsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      // ContentNegotiation needs request method/headers
      HttpServerRequest req = stubRequestCommon(ctx);
      when(ctx.pathParam("datasetId")).thenReturn("ds-1");

      // query params: valueType != metadata
      RequestParameter valueType = mock(RequestParameter.class);
      when(valueType.getString()).thenReturn("identifiers");
      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("valueType")).thenReturn(valueType);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      new DistributionHandler(vertx).handleListDatasetDistributions(ctx);

      // NOTE: current code captures acceptType before setAcceptType(...) → remains JSON-LD
      verify(svc).listDatasetDistributions(eq(""), eq("identifiers"),
          eq(ContentType.JSON_LD.getMimeType()));
      verify(resp).end(anyString());
    }
  }

  @Test
  void handleGetDistribution_success_endsWithPayload() {
    DistributionsService svc = mock(DistributionsService.class);
    when(svc.getDistribution(anyString(), anyString()))
        .thenReturn(Future.succeededFuture("{}"));

    try (MockedStatic<DistributionsService> svcStatic = mockStatic(DistributionsService.class)) {
      svcStatic.when(() -> DistributionsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      // For ContentNegotiation
      stubRequestCommon(ctx);
      when(ctx.pathParam("distributionId")).thenReturn("dist-9");

      new DistributionHandler(vertx).handleGetDistribution(ctx);

      verify(svc).getDistribution(eq(""), anyString());
      verify(resp).end(anyString());
    }
  }

  @Test
  void handlePostDatasetDistribution_missingBody_fails400() {
    DistributionsService svc = mock(DistributionsService.class);

    try (MockedStatic<DistributionsService> svcStatic = mockStatic(DistributionsService.class);
        MockedStatic<HubRepo> hub = mockStatic(HubRepo.class)) {
      svcStatic.when(() -> DistributionsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);

      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(true);
      RequestParameter datasetIdParam = mock(RequestParameter.class);
      when(datasetIdParam.getString()).thenReturn("ds-1");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(datasetIdParam);
      when(params.body()).thenReturn(body);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      // parsed headers still accessed before the return, be safe:
      stubParsedHeaders(ctx, "application/ld+json");

      new DistributionHandler(vertx).handlePostDatasetDistribution(ctx);

      // handler calls context.fail(400, ServiceException)
      verify(ctx).fail(eq(400), any());
      verifyNoInteractions(svc);
    }
  }

  @Test
  void handlePostDatasetDistribution_success_201_locationHeader() {
    DistributionsService svc = mock(DistributionsService.class);
    when(svc.postDistribution(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("new-dist"));

    try (MockedStatic<DistributionsService> svcStatic = mockStatic(DistributionsService.class)) {
      svcStatic.when(() -> DistributionsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      HttpServerRequest req = stubRequestCommon(ctx);
      when(req.absoluteURI()).thenReturn("http://example.test/datasets/ds-1/distributions");

      RequestParameter datasetIdParam = mock(RequestParameter.class);
      when(datasetIdParam.getString()).thenReturn("ds-1");
      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{payload}");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(datasetIdParam);
      when(params.body()).thenReturn(body);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      stubParsedHeaders(ctx, "application/ld+json");

      new DistributionHandler(vertx).handlePostDatasetDistribution(ctx);

      verify(svc).postDistribution(eq("ds-1"), eq("{payload}"), eq("application/ld+json"));
      verify(resp).putHeader(eq("Location"), eq("http://example.test/datasets/ds-1/distributions/new-dist"));
      verify(resp).setStatusCode(201);
      verify(resp).end();
    }
  }

  @Test
  void handlePutDistribution_success_updated_204() {
    DistributionsService svc = mock(DistributionsService.class);
    when(svc.putDistribution(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("updated"));

    try (MockedStatic<DistributionsService> svcStatic = mockStatic(DistributionsService.class)) {
      svcStatic.when(() -> DistributionsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      RequestParameter distIdParam = mock(RequestParameter.class);
      when(distIdParam.getString()).thenReturn("dist-7");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("distributionId")).thenReturn(distIdParam);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      when(ctx.get("datasetId")).thenReturn("ds-9");
      when(ctx.body().asString()).thenReturn("{put}");
      stubParsedHeaders(ctx, "application/n-triples");

      new DistributionHandler(vertx).handlePutDistribution(ctx);

      verify(svc).putDistribution(eq("ds-9"), eq("dist-7"), eq("{put}"), eq("application/n-triples"));
      verify(resp).setStatusCode(HttpStatus.SC_NO_CONTENT);
      verify(resp).end();
    }
  }

  @Test
  void handlePutDistribution_unexpectedStatus_500_failureResponse() {
    DistributionsService svc = mock(DistributionsService.class);
    // Return something other than "updated"
    when(svc.putDistribution(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("weird"));

    try (MockedStatic<DistributionsService> svcStatic = mockStatic(DistributionsService.class);
        MockedStatic<HubRepo> hub = mockStatic(HubRepo.class)) {
      svcStatic.when(() -> DistributionsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);

      RequestParameter distIdParam = mock(RequestParameter.class);
      when(distIdParam.getString()).thenReturn("dist-7");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("distributionId")).thenReturn(distIdParam);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      when(ctx.get("datasetId")).thenReturn("ds-9");
      when(ctx.body().asString()).thenReturn("{put}");
      stubParsedHeaders(ctx, "application/n-triples");

      new DistributionHandler(vertx).handlePutDistribution(ctx);

      hub.verify(() -> HubRepo.failureResponse(eq(ctx), any()), times(1));
    }
  }

  @Test
  void handleDeleteDistribution_success_204() {
    DistributionsService svc = mock(DistributionsService.class);
    when(svc.deleteDistribution(anyString(), anyString()))
        .thenReturn(Future.succeededFuture());

    try (MockedStatic<DistributionsService> svcStatic = mockStatic(DistributionsService.class)) {
      svcStatic.when(() -> DistributionsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      RequestParameter distIdParam = mock(RequestParameter.class);
      when(distIdParam.getString()).thenReturn("dist-3");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("distributionId")).thenReturn(distIdParam);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      when(ctx.get("datasetId")).thenReturn("ds-11");

      new DistributionHandler(vertx).handleDeleteDistribution(ctx);

      verify(svc).deleteDistribution(eq("ds-11"), eq("dist-3"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  @Test
  void handleDeleteDistribution_failure_callsHubRepoFailureResponse() {
    DistributionsService svc = mock(DistributionsService.class);
    when(svc.deleteDistribution(anyString(), anyString()))
        .thenReturn(Future.failedFuture("boom"));

    try (MockedStatic<DistributionsService> svcStatic = mockStatic(DistributionsService.class);
        MockedStatic<HubRepo> hub = mockStatic(HubRepo.class)) {
      svcStatic.when(() -> DistributionsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);

      RequestParameter distIdParam = mock(RequestParameter.class);
      when(distIdParam.getString()).thenReturn("dist-3");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("distributionId")).thenReturn(distIdParam);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      when(ctx.get("datasetId")).thenReturn("ds-11");

      new DistributionHandler(vertx).handleDeleteDistribution(ctx);

      hub.verify(() -> HubRepo.failureResponse(eq(ctx), any()), times(1));
    }
  }
}
