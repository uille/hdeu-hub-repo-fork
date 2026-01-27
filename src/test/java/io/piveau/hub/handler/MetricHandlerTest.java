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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.piveau.HubRepo;
import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.hub.services.metrics.MetricsService;
import io.piveau.hub.util.ContentType;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.ParsableMIMEValue;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.serviceproxy.ServiceException;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

class MetricHandlerTest {

  private final Vertx vertx = mock(Vertx.class);

  private void stubRequestForNegotiation(RoutingContext ctx) {
    HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_DEEP_STUBS);
    when(req.method()).thenReturn(HttpMethod.GET);
    when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
    when(req.getHeader(anyString())).thenReturn(null);
    when(ctx.request()).thenReturn(req);
  }

  private void stubParsedHeaders(RoutingContext ctx, String contentType) {
    ParsedHeaderValues ph = mock(ParsedHeaderValues.class, Answers.RETURNS_DEEP_STUBS);
    when(ph.contentType()).thenReturn(new ParsableMIMEValue(contentType));
    when(ctx.parsedHeaders()).thenReturn(ph);
  }

  // --- handleGetMetric ---

  @Test
  void handleGetMetric_default_noHistoric_usesJsonLd_success() {
    MetricsService svc = mock(MetricsService.class);
    when(svc.getMetrics(anyString(), anyBoolean(), anyString()))
        .thenReturn(Future.succeededFuture("{}"));

    try (MockedStatic<MetricsService> svcStatic = mockStatic(MetricsService.class)) {
      svcStatic.when(() -> MetricsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("historic")).thenReturn(null); // not provided

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      stubRequestForNegotiation(ctx);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.pathParam("datasetId")).thenReturn("ds-1"); // used by ContentNegotiation
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new MetricHandler(vertx).handleGetMetric(ctx);

      verify(svc).getMetrics(eq(""), eq(false), eq(ContentType.JSON_LD.getMimeType()));
      verify(resp).end(anyString());
    }
  }

  @Test
  void handleGetMetric_historicTrue_success() {
    MetricsService svc = mock(MetricsService.class);
    when(svc.getMetrics(anyString(), anyBoolean(), anyString()))
        .thenReturn(Future.succeededFuture("{}"));

    try (MockedStatic<MetricsService> svcStatic = mockStatic(MetricsService.class)) {
      svcStatic.when(() -> MetricsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter historic = mock(RequestParameter.class);
      when(historic.getBoolean()).thenReturn(true);
      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("historic")).thenReturn(historic);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      stubRequestForNegotiation(ctx);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.pathParam("datasetId")).thenReturn("ds-2");
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new MetricHandler(vertx).handleGetMetric(ctx);

      verify(svc).getMetrics(eq(""), eq(true), eq(ContentType.JSON_LD.getMimeType()));
      verify(resp).end(anyString());
    }
  }

  // --- handlePutMetric ---

  @Test
  void handlePutMetric_missingBody_callsFail400() {
    MetricsService svc = mock(MetricsService.class);

    try (MockedStatic<MetricsService> svcStatic = mockStatic(MetricsService.class)) {
      svcStatic.when(() -> MetricsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(true);

      RequestParameter idParam = mock(RequestParameter.class);
      when(idParam.getString()).thenReturn("ds-x");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(idParam);
      when(params.body()).thenReturn(body);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      new MetricHandler(vertx).handlePutMetric(ctx);

      verify(ctx).fail(eq(400), argThat(ex -> ex instanceof ServiceException se && se.failureCode() == 400));
      verifyNoInteractions(svc);
    }
  }

  @Test
  void handlePutMetric_created201_setsLocation() {
    MetricsService svc = mock(MetricsService.class);
    when(svc.putMetrics(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("created"));

    try (MockedStatic<MetricsService> svcStatic = mockStatic(MetricsService.class)) {
      svcStatic.when(() -> MetricsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter idParam = mock(RequestParameter.class);
      when(idParam.getString()).thenReturn("ds-created");

      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(idParam);
      when(params.body()).thenReturn(body);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      stubParsedHeaders(ctx, "application/ld+json");

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new MetricHandler(vertx).handlePutMetric(ctx);

      // compute expected Location via the same helper the handler uses
      String expectedLoc = DCATAPUriSchema
          .createFor("ds-created", DCATAPUriSchema.getMetricsContext())
          .getMetricsUriRef();

      verify(svc).putMetrics(eq("ds-created"), eq("{}"), eq("application/ld+json"));
      verify(resp).setStatusCode(201);
      verify(resp).putHeader(eq(HttpHeaders.LOCATION), eq(expectedLoc));
      verify(resp).end();
    }
  }

  @Test
  void handlePutMetric_updated204() {
    MetricsService svc = mock(MetricsService.class);
    when(svc.putMetrics(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("updated"));

    try (MockedStatic<MetricsService> svcStatic = mockStatic(MetricsService.class)) {
      svcStatic.when(() -> MetricsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter idParam = mock(RequestParameter.class);
      when(idParam.getString()).thenReturn("ds-upd");

      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("payload");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(idParam);
      when(params.body()).thenReturn(body);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      stubParsedHeaders(ctx, "application/n-triples");

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new MetricHandler(vertx).handlePutMetric(ctx);

      verify(svc).putMetrics(eq("ds-upd"), eq("payload"), eq("application/n-triples"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  // --- handleDeleteMetric ---

  @Test
  void handleDeleteMetric_withPathParam_204() {
    MetricsService svc = mock(MetricsService.class);
    when(svc.deleteMetrics(anyString(), anyString())).thenReturn(Future.succeededFuture());

    try (MockedStatic<MetricsService> svcStatic = mockStatic(MetricsService.class)) {
      svcStatic.when(() -> MetricsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter pathId = mock(RequestParameter.class);
      when(pathId.getString()).thenReturn("ds-77");
      RequestParameter catalogue = mock(RequestParameter.class);
      when(catalogue.getString()).thenReturn("cat-1");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(pathId);
      when(params.queryParameter("catalogue")).thenReturn(catalogue);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new MetricHandler(vertx).handleDeleteMetric(ctx);

      verify(svc).deleteMetrics(eq("ds-77"), eq("cat-1"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  @Test
  void handleDeleteMetric_withoutPathParam_usesQueryId_204() {
    MetricsService svc = mock(MetricsService.class);
    when(svc.deleteMetrics(anyString(), anyString())).thenReturn(Future.succeededFuture());

    try (MockedStatic<MetricsService> svcStatic = mockStatic(MetricsService.class)) {
      svcStatic.when(() -> MetricsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      // pathParameter is null -> handler should use queryParameter("id")
      RequestParameter queryId = mock(RequestParameter.class);
      when(queryId.getString()).thenReturn("ds-q");
      RequestParameter catalogue = mock(RequestParameter.class);
      when(catalogue.getString()).thenReturn("cat-2");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(null);
      when(params.queryParameter("id")).thenReturn(queryId);
      when(params.queryParameter("catalogue")).thenReturn(catalogue);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new MetricHandler(vertx).handleDeleteMetric(ctx);

      verify(svc).deleteMetrics(eq("ds-q"), eq("cat-2"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  // One failure smoke test to ensure HubRepo is called on service error.
  @Test
  void handleGetMetric_failure_callsHubRepo() {
    MetricsService svc = mock(MetricsService.class);
    when(svc.getMetrics(anyString(), anyBoolean(), anyString()))
        .thenReturn(Future.failedFuture(new ServiceException(500, "boom")));

    try (MockedStatic<MetricsService> svcStatic = mockStatic(MetricsService.class);
        MockedStatic<HubRepo> hub = mockStatic(HubRepo.class)) {
      svcStatic.when(() -> MetricsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("historic")).thenReturn(null);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      stubRequestForNegotiation(ctx);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.pathParam("datasetId")).thenReturn("ds-err");

      new MetricHandler(vertx).handleGetMetric(ctx);

      hub.verify(() -> HubRepo.failureResponse(eq(ctx), any()), times(1));
    }
  }
}
