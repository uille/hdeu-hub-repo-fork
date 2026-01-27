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
import io.piveau.hub.Constants;
import io.piveau.hub.services.vocabularies.VocabulariesService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

class VocabularyHandlerTest {

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

  // listVocabularies

  @Test
  void listVocabularies_metadata_keepsJsonLd_success() {
    VocabulariesService svc = mock(VocabulariesService.class);
    when(svc.listVocabularies(anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(Future.succeededFuture("[]"));

    try (MockedStatic<VocabulariesService> svcStatic = mockStatic(VocabulariesService.class)) {
      svcStatic.when(() -> VocabulariesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter offset = mock(RequestParameter.class); when(offset.getInteger()).thenReturn(0);
      RequestParameter limit = mock(RequestParameter.class); when(limit.getInteger()).thenReturn(10);
      RequestParameter valueType = mock(RequestParameter.class); when(valueType.getString()).thenReturn("metadata");

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter(eq(Constants.OFFSET))).thenReturn(offset);
      when(params.queryParameter(eq(Constants.LIMIT))).thenReturn(limit);
      when(params.queryParameter(eq("valueType"))).thenReturn(valueType);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      stubRequestForNegotiation(ctx);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      new VocabularyHandler(vertx).listVocabularies(ctx);

      verify(svc).listVocabularies(
          eq(ContentType.JSON_LD.getMimeType()), // stays JSON-LD for metadata
          eq("metadata"),
          eq(10), eq(0));
      verify(resp).end(anyString());
    }
  }

  @Test
  void listVocabularies_nonMetadata_forcesJson_success() {
    VocabulariesService svc = mock(VocabulariesService.class);
    when(svc.listVocabularies(anyString(), anyString(), anyInt(), anyInt()))
        .thenReturn(Future.succeededFuture("[]"));

    try (MockedStatic<VocabulariesService> svcStatic = mockStatic(VocabulariesService.class)) {
      svcStatic.when(() -> VocabulariesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter offset = mock(RequestParameter.class); when(offset.getInteger()).thenReturn(3);
      RequestParameter limit = mock(RequestParameter.class); when(limit.getInteger()).thenReturn(25);
      RequestParameter valueType = mock(RequestParameter.class); when(valueType.getString()).thenReturn("data");

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter(eq(Constants.OFFSET))).thenReturn(offset);
      when(params.queryParameter(eq(Constants.LIMIT))).thenReturn(limit);
      when(params.queryParameter(eq("valueType"))).thenReturn(valueType);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      stubRequestForNegotiation(ctx);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      new VocabularyHandler(vertx).listVocabularies(ctx);

      verify(svc).listVocabularies(
          eq(ContentType.JSON.getMimeType()), // forced to JSON
          eq("data"),
          eq(25), eq(3));
      verify(resp).end(anyString());
    }
  }

  // getVocabulary

  @Test
  void getVocabulary_success_usesIdAndAccept() {
    VocabulariesService svc = mock(VocabulariesService.class);
    when(svc.getVocabulary(anyString(), anyString())).thenReturn(Future.succeededFuture("{}"));

    try (MockedStatic<VocabulariesService> svcStatic = mockStatic(VocabulariesService.class)) {
      svcStatic.when(() -> VocabulariesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      stubRequestForNegotiation(ctx);
      when(ctx.pathParam("vocabularyId")).thenReturn("voc-1");
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new VocabularyHandler(vertx).getVocabulary(ctx);

      verify(svc).getVocabulary(eq(""), eq(ContentType.DEFAULT.getMimeType()));
      verify(resp).end(anyString());
    }
  }

  // createOrUpdateVocabulary

  @Test
  void createOrUpdateVocabulary_singleChunk_created201_setsLocation() {
    VocabulariesService svc = mock(VocabulariesService.class);

    try (MockedStatic<VocabulariesService> svcStatic = mockStatic(VocabulariesService.class)) {
      svcStatic.when(() -> VocabulariesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      // request params
      when(ctx.request().getParam("vocabularyId")).thenReturn("voc-7");
      when(ctx.request().getParam("uri")).thenReturn("http://example.com/v");
      when(ctx.request().getParam("hash")).thenReturn(null);
      when(ctx.request().getParam("chunkId")).thenReturn(null);
      when(ctx.request().getParam("numberOfChunks")).thenReturn(null);

      // headers & body
      stubParsedHeaders(ctx, "application/ld+json");
      when(ctx.body().asString()).thenReturn("{}");

      when(svc.putVocabulary(eq("voc-7"), eq("http://example.com/v"),
          eq("application/ld+json"), eq("{}")))
          .thenReturn(Future.succeededFuture("created"));

      new VocabularyHandler(vertx).createOrUpdateVocabulary(ctx);

      verify(resp).putHeader(eq(HttpHeaders.LOCATION), eq("/vocabularies/voc-7"));
      verify(resp).setStatusCode(201);
      verify(resp).end();
    }
  }

  @Test
  void createOrUpdateVocabulary_multiChunk_accepted202() {
    VocabulariesService svc = mock(VocabulariesService.class);

    try (MockedStatic<VocabulariesService> svcStatic = mockStatic(VocabulariesService.class)) {
      svcStatic.when(() -> VocabulariesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      when(ctx.request().getParam("vocabularyId")).thenReturn("voc-9");
      when(ctx.request().getParam("uri")).thenReturn("http://ex/v");
      when(ctx.request().getParam("hash")).thenReturn("h123");
      when(ctx.request().getParam("chunkId")).thenReturn("2");
      when(ctx.request().getParam("numberOfChunks")).thenReturn("4");

      stubParsedHeaders(ctx, "text/turtle");
      when(ctx.body().asString()).thenReturn("ttl");

      when(svc.putVocabularyChunk(eq("voc-9"), eq("http://ex/v"),
          eq("text/turtle"), eq("ttl"), eq("h123"), eq(2), eq(4)))
          .thenReturn(Future.succeededFuture("accepted"));

      new VocabularyHandler(vertx).createOrUpdateVocabulary(ctx);

      verify(resp).setStatusCode(202);
      verify(resp).end();
    }
  }

  @Test
  void createOrUpdateVocabulary_multiChunk_updated204() {
    VocabulariesService svc = mock(VocabulariesService.class);

    try (MockedStatic<VocabulariesService> svcStatic = mockStatic(VocabulariesService.class)) {
      svcStatic.when(() -> VocabulariesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      when(ctx.request().getParam("vocabularyId")).thenReturn("voc-10");
      when(ctx.request().getParam("uri")).thenReturn("http://ex/v");
      when(ctx.request().getParam("hash")).thenReturn("h999");
      when(ctx.request().getParam("chunkId")).thenReturn("0");
      when(ctx.request().getParam("numberOfChunks")).thenReturn("2");

      stubParsedHeaders(ctx, "application/n-triples");
      when(ctx.body().asString()).thenReturn("nt");

      when(svc.putVocabularyChunk(eq("voc-10"), eq("http://ex/v"),
          eq("application/n-triples"), eq("nt"), eq("h999"), eq(0), eq(2)))
          .thenReturn(Future.succeededFuture("updated"));

      new VocabularyHandler(vertx).createOrUpdateVocabulary(ctx);

      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  // putVocabulary

  @Test
  void putVocabulary_bodyMissing_callsContextFail400() {
    VocabulariesService svc = mock(VocabulariesService.class);

    try (MockedStatic<VocabulariesService> svcStatic = mockStatic(VocabulariesService.class)) {
      svcStatic.when(() -> VocabulariesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(true);

      RequestParameters params = mock(RequestParameters.class);
      when(params.body()).thenReturn(body);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      stubRequestForNegotiation(ctx); // ContentNegotiation in ctor
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      new VocabularyHandler(vertx).putVocabulary(ctx);

      verify(ctx).fail(eq(400), argThat(ex -> ex instanceof ServiceException se && se.failureCode() == 400));
      verifyNoInteractions(svc);
    }
  }

  @Test
  void putVocabulary_created201_setsLocation() {
    VocabulariesService svc = mock(VocabulariesService.class);
    when(svc.putVocabulary(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("created"));

    try (MockedStatic<VocabulariesService> svcStatic = mockStatic(VocabulariesService.class)) {
      svcStatic.when(() -> VocabulariesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.body()).thenReturn(body);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      stubRequestForNegotiation(ctx);
      when(ctx.pathParam("vocabularyId")).thenReturn("voc-x"); // used by ContentNegotiation
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      stubParsedHeaders(ctx, "application/ld+json");

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new VocabularyHandler(vertx).putVocabulary(ctx);

      verify(svc).putVocabulary(eq(""), eq(""), eq("application/ld+json"), eq("{}"));
      verify(resp).setStatusCode(201);
      verify(resp).putHeader(eq(HttpHeaders.LOCATION), eq("/vocabularies/"));
      verify(resp).end();
    }
  }

  @Test
  void putVocabulary_updated204() {
    VocabulariesService svc = mock(VocabulariesService.class);
    when(svc.putVocabulary(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("updated"));

    try (MockedStatic<VocabulariesService> svcStatic = mockStatic(VocabulariesService.class)) {
      svcStatic.when(() -> VocabulariesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("payload");

      RequestParameters params = mock(RequestParameters.class);
      when(params.body()).thenReturn(body);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      stubRequestForNegotiation(ctx);
      when(ctx.pathParam("vocabularyId")).thenReturn("voc-y");
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      stubParsedHeaders(ctx, "text/turtle");

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new VocabularyHandler(vertx).putVocabulary(ctx);

      verify(svc).putVocabulary(eq(""), eq(""), eq("text/turtle"), eq("payload"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  // deleteVocabulary

  @Test
  void deleteVocabulary_success_204() {
    VocabulariesService svc = mock(VocabulariesService.class);
    when(svc.deleteVocabulary(anyString())).thenReturn(Future.succeededFuture());

    try (MockedStatic<VocabulariesService> svcStatic = mockStatic(VocabulariesService.class)) {
      svcStatic.when(() -> VocabulariesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter idParam = mock(RequestParameter.class);
      when(idParam.getString()).thenReturn("voc-del");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter(eq("vocabularyId"))).thenReturn(idParam);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      when(ctx.response()).thenReturn(resp);

      new VocabularyHandler(vertx).deleteVocabulary(ctx);

      verify(svc).deleteVocabulary(eq("voc-del"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  // error path smoke (one example)
  @Test
  void getVocabulary_failure_callsHubRepo() {
    VocabulariesService svc = mock(VocabulariesService.class);
    when(svc.getVocabulary(anyString(), anyString()))
        .thenReturn(Future.failedFuture(new ServiceException(500, "boom")));

    try (MockedStatic<VocabulariesService> svcStatic = mockStatic(VocabulariesService.class);
        MockedStatic<HubRepo> hub = mockStatic(HubRepo.class)) {
      svcStatic.when(() -> VocabulariesService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
      stubRequestForNegotiation(ctx);
      when(ctx.pathParam("vocabularyId")).thenReturn("voc-err");

      new VocabularyHandler(vertx).getVocabulary(ctx);

      hub.verify(() -> HubRepo.failureResponse(eq(ctx), any()), times(1));
    }
  }
}
