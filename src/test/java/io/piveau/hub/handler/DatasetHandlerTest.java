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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.HubRepo;
import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.hub.util.ContentType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.serviceproxy.ServiceException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class DatasetHandlerTest {

  private static final String PATH_PARAM_CATALOGUE = "catalogueId";
  private static final String QUERY_PARAM_DATASET = "originalId";
  private static Vertx vertx;

  @BeforeAll
  static void init() { vertx = Vertx.vertx(); }

  @AfterAll
  static void teardown() { if (vertx != null) vertx.close(); }

  // -------------------- handleListDatasets --------------------

  @Test
  void listDatasets_noCatalogue_metadata_jsonLd_success() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.listDatasets(anyString(), anyString(), any(), any(), anyBoolean(), anyString(), any()))
        .thenReturn(Future.succeededFuture("[]"));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      // query params
      RequestParameter limit = mock(RequestParameter.class);
      when(limit.getInteger()).thenReturn(10);
      RequestParameter offset = mock(RequestParameter.class);
      when(offset.getInteger()).thenReturn(0);
      RequestParameter hydra = mock(RequestParameter.class);
      when(hydra.getBoolean()).thenReturn(null); // default false path
      RequestParameter usePaged = mock(RequestParameter.class);
      when(usePaged.getBoolean()).thenReturn(null);
      RequestParameter valueType = mock(RequestParameter.class);
      when(valueType.getString()).thenReturn("metadata");
      // IMPORTANT: return a RequestParameter, not null
      RequestParameter sourceIds = mock(RequestParameter.class);
      when(sourceIds.getBoolean()).thenReturn(null);

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("limit")).thenReturn(limit);
      when(params.queryParameter("offset")).thenReturn(offset);
      when(params.queryParameter("hydra")).thenReturn(hydra);
      when(params.queryParameter("usePagedCollection")).thenReturn(usePaged);
      when(params.queryParameter("catalogue")).thenReturn(null); // no catalogue
      when(params.queryParameter("valueType")).thenReturn(valueType);
      when(params.queryParameter("sourceIds")).thenReturn(sourceIds); // <- fixed

      // request/response
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_SELF);
      when(req.method()).thenReturn(io.vertx.core.http.HttpMethod.GET); // ContentNegotiation needs this
      when(req.absoluteURI()).thenReturn("http://example.test/api/datasets");
      when(req.headers()).thenReturn(io.vertx.core.MultiMap.caseInsensitiveMultiMap());

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.request()).thenReturn(req);
      when(ctx.response()).thenReturn(resp);

      new DatasetHandler(vertx).handleListDatasets(ctx);

      verify(svc).listDatasets(
          eq(ContentType.JSON_LD.getMimeType()), // accept
          eq("metadata"),
          eq(10), // limit
          eq(0),  // offset
          eq(false), // hydra default
          eq("http://example.test/api/datasets"),
          isNull() // usePagedCollection
      );
      verify(resp).end(anyString());
    }
  }

  @Test
  void listDatasets_withCatalogue_nonMetadata_forcesJson_success() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.listCatalogueDatasets(anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Future.succeededFuture("payload"));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      // ---- RequestParameters
      RequestParameter limit = mock(RequestParameter.class);
      when(limit.getInteger()).thenReturn(25);

      RequestParameter offset = mock(RequestParameter.class);
      when(offset.getInteger()).thenReturn(5);

      RequestParameter valueType = mock(RequestParameter.class);
      when(valueType.getString()).thenReturn("data"); // not "metadata"

      RequestParameter catalogue = mock(RequestParameter.class);
      when(catalogue.getString()).thenReturn("cat-1");

      // IMPORTANT: return a RequestParameter mock, not null
      RequestParameter hydra = mock(RequestParameter.class);
      when(hydra.getBoolean()).thenReturn(null); // will be treated as default false

      RequestParameter usePaged = mock(RequestParameter.class);
      when(usePaged.getBoolean()).thenReturn(null);

      RequestParameter sourceIds = mock(RequestParameter.class);
      when(sourceIds.getBoolean()).thenReturn(null);

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("limit")).thenReturn(limit);
      when(params.queryParameter("offset")).thenReturn(offset);
      when(params.queryParameter("valueType")).thenReturn(valueType);
      when(params.queryParameter("catalogue")).thenReturn(catalogue);
      when(params.queryParameter("hydra")).thenReturn(hydra);                  // <-- fixed
      when(params.queryParameter("usePagedCollection")).thenReturn(usePaged);  // <-- fixed
      when(params.queryParameter("sourceIds")).thenReturn(sourceIds);          // <-- fixed

      // ---- Request / Response
      HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_SELF);
      when(req.absoluteURI()).thenReturn("http://example.test/api/datasets");
      when(req.headers()).thenReturn(io.vertx.core.MultiMap.caseInsensitiveMultiMap());

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.request()).thenReturn(req);
      when(ctx.response()).thenReturn(resp);

      new DatasetHandler(vertx).handleListDatasets(ctx);

      verify(svc).listCatalogueDatasets(
          eq(ContentType.JSON.getMimeType()), // forced JSON for non "metadata"
          eq("data"),
          eq("cat-1"),
          eq(25),
          eq(5)
      );
      verify(resp).end(anyString());
    }
  }


  @Test
  void listDatasets_failure_callsHubRepoFailure() {
    RuntimeException boom = new RuntimeException("list failed");
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.listDatasets(anyString(), anyString(), any(), any(), anyBoolean(), anyString(), any()))
        .thenReturn(Future.failedFuture(boom));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {

      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      // ---- RequestParameters (return RequestParameter mocks, not nulls)
      RequestParameter limit = mock(RequestParameter.class);
      when(limit.getInteger()).thenReturn(10);

      RequestParameter offset = mock(RequestParameter.class);
      when(offset.getInteger()).thenReturn(0);

      RequestParameter hydra = mock(RequestParameter.class);
      when(hydra.getBoolean()).thenReturn(null); // -> handler defaults to false

      RequestParameter usePaged = mock(RequestParameter.class);
      when(usePaged.getBoolean()).thenReturn(null);

      RequestParameter sourceIds = mock(RequestParameter.class);
      when(sourceIds.getBoolean()).thenReturn(null);

      RequestParameter valueType = mock(RequestParameter.class);
      when(valueType.getString()).thenReturn("metadata"); // keep JSON-LD path

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("limit")).thenReturn(limit);
      when(params.queryParameter("offset")).thenReturn(offset);
      when(params.queryParameter("hydra")).thenReturn(hydra);
      when(params.queryParameter("usePagedCollection")).thenReturn(usePaged);
      when(params.queryParameter("sourceIds")).thenReturn(sourceIds);
      when(params.queryParameter("valueType")).thenReturn(valueType);
      when(params.queryParameter("catalogue")).thenReturn(null); // allowed (handler checks null)

      // ---- Request / Response
      HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_SELF);
      when(req.absoluteURI()).thenReturn("http://x");
      when(req.headers()).thenReturn(io.vertx.core.MultiMap.caseInsensitiveMultiMap());

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.request()).thenReturn(req);
      when(ctx.response()).thenReturn(mock(HttpServerResponse.class, Answers.RETURNS_SELF));

      // ---- Invoke
      new DatasetHandler(vertx).handleListDatasets(ctx);

      // ---- Verify failure path
      hubStatic.verify(() -> HubRepo.failureResponse(eq(ctx), eq(boom)));
    }
  }

  // -------------------- handleGetDataset --------------------

  @Test
  void getDataset_noCatalogue_usesJsonLd_success() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.getDataset(anyString(), anyString())).thenReturn(Future.succeededFuture("{}"));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      // --- request + headers used by ContentNegotiation
      HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_SELF);
      when(req.method()).thenReturn(io.vertx.core.http.HttpMethod.GET);
      io.vertx.core.MultiMap headers = io.vertx.core.MultiMap.caseInsensitiveMultiMap();
      headers.add(io.vertx.core.http.HttpHeaders.ACCEPT, ContentType.JSON_LD.getMimeType());
      when(req.headers()).thenReturn(headers);

      // Parsed headers (non-null to avoid NPEs in other paths)
      io.vertx.ext.web.ParsedHeaderValues parsed = mock(io.vertx.ext.web.ParsedHeaderValues.class);

      // --- routing context
      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.request()).thenReturn(req);
      when(ctx.parsedHeaders()).thenReturn(parsed);
      when(ctx.pathParam(anyString())).thenReturn("ds-1");
      when(ctx.response()).thenReturn(resp);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(mock(RequestParameters.class));

      new DatasetHandler(vertx).handleGetDataset(ctx);

      verify(svc).getDataset(eq(""), eq(ContentType.JSON_LD.getMimeType()));
      verify(resp).end(anyString());
    }
  }

  @Test
  void getDataset_withCatalogue_callsGetDatasetOrigin_success() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.getDatasetOrigin(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("{}"));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      // query param ?catalogue=cat-9
      RequestParameter cat = mock(RequestParameter.class);
      when(cat.getString()).thenReturn("cat-9");
      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("catalogue")).thenReturn(cat);

      // response
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      // request (needed by ContentNegotiation)
      HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_SELF);
      when(req.method()).thenReturn(HttpMethod.GET);
      when(req.getHeader(HttpHeaders.ACCEPT.toString()))
          .thenReturn(ContentType.JSON_LD.getMimeType());

      // routing context
      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.pathParam("datasetId")).thenReturn("ds-1");
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);
      when(ctx.request()).thenReturn(req);

      new DatasetHandler(vertx).handleGetDataset(ctx);

      verify(svc).getDatasetOrigin(eq(""), eq("cat-9"), eq(ContentType.JSON_LD.getMimeType()));
      verify(resp).end(anyString());
    }
  }

  // -------------------- handleGetDatasetOrigin --------------------

  @Test
  void getDatasetOrigin_success() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.getDatasetOrigin(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture("{}"));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      // query param: originalId
      RequestParameter original = mock(RequestParameter.class);
      when(original.getString()).thenReturn("origin-7");
      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("originalId")).thenReturn(original);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      // request used by ContentNegotiation
      HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_SELF);
      when(req.method()).thenReturn(HttpMethod.GET);
      when(req.headers()).thenReturn(io.vertx.core.MultiMap.caseInsensitiveMultiMap());
      when(req.getParam("catalogueId")).thenReturn("cat-1"); // <-- important

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.pathParam("catalogueId")).thenReturn("cat-1"); // harmless, but ContentNegotiation uses getParam
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.request()).thenReturn(req);
      when(ctx.response()).thenReturn(resp);

      new DatasetHandler(vertx).handleGetDatasetOrigin(ctx);

      verify(svc).getDatasetOrigin(
          eq("origin-7"),
          eq(""),
          eq(ContentType.JSON_LD.getMimeType())
      );
      verify(resp).end(anyString());
    }
  }


  // -------------------- handleGetRecord --------------------

  @Test
  void getRecord_success() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.getRecord(anyString(), anyString()))
        .thenReturn(Future.succeededFuture("{}"));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      // ---- Request + headers used by ContentNegotiation
      io.vertx.core.MultiMap headers = io.vertx.core.MultiMap.caseInsensitiveMultiMap();
      headers.add("Accept", ContentType.JSON_LD.getMimeType());

      HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_SELF);
      when(req.method()).thenReturn(io.vertx.core.http.HttpMethod.GET);
      when(req.headers()).thenReturn(headers);
      when(req.absoluteURI()).thenReturn("http://example.test/api/datasets/ds-2");

      // (Optional but safe) params path binding for the id
      RequestParameter idParam = mock(RequestParameter.class);
      when(idParam.getString()).thenReturn("ds-2");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(idParam);

      // ---- Response + context
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.request()).thenReturn(req);
      when(ctx.response()).thenReturn(resp);
      when(ctx.pathParam("datasetId")).thenReturn("ds-2");
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      // act
      new DatasetHandler(vertx).handleGetRecord(ctx);

      // assert
      verify(svc).getRecord(eq("ds-2"), eq(ContentType.JSON_LD.getMimeType()));
      verify(resp).end(anyString());
    }
  }

  // -------------------- handlePutDataset --------------------

  @Test
  void putDataset_emptyBody_ctxFail400() {
    DatasetsService svc = mock(DatasetsService.class);
    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class); when(id.getString()).thenReturn("ds-x");
      RequestParameter body = mock(RequestParameter.class); when(body.isEmpty()).thenReturn(true);
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(id);
      when(params.body()).thenReturn(body);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

      new DatasetHandler(vertx).handlePutDataset(ctx);

      verify(ctx).fail(eq(400), any(ServiceException.class));
    }
  }

  @Test
  void putDataset_wrongContentType_callsFailure415() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.putDataset(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture(new JsonObject()));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {

      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class);
      when(id.getString()).thenReturn("ds-1");

      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(id);
      when(params.body()).thenReturn(body);

      // -- parsedHeaders() must return a non-null object:
      ParsedHeaderValues parsed = mock(ParsedHeaderValues.class);
      // Simulate invalid/absent content-type:
      when(parsed.contentType()).thenReturn(null);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);
      when(ctx.parsedHeaders()).thenReturn(parsed);

      new DatasetHandler(vertx).handlePutDataset(ctx);

      hubStatic.verify(() -> HubRepo.failureResponse(eq(ctx),
          argThat(t -> t instanceof ServiceException se && se.failureCode() == 415)));
    }
  }

  @Test
  void putDataset_updated_204_withLocation() {
    JsonObject status = new JsonObject()
        .put("status", "updated")
        .put(HttpHeaders.LOCATION.toString(), "/ds/123");

    DatasetsService svc = mock(DatasetsService.class);
    when(svc.putDataset(anyString(), anyString(), anyString()))
        .thenReturn(Future.succeededFuture(status));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString()))
          .thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class);
      when(id.getString()).thenReturn("ds-1");
      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(id);
      when(params.body()).thenReturn(body);

      // --- parsedHeaders() → contentType().value()
      ParsedHeaderValues parsed = mock(ParsedHeaderValues.class);
      MIMEHeader mime = mock(MIMEHeader.class);
      when(mime.value()).thenReturn("application/json");
      when(parsed.contentType()).thenReturn(mime);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.parsedHeaders()).thenReturn(parsed);  // <<< important
      when(ctx.response()).thenReturn(resp);

      new DatasetHandler(vertx).handlePutDataset(ctx);

      verify(resp).setStatusCode(204);
      verify(resp).putHeader(eq(HttpHeaders.LOCATION), eq("/ds/123"));
      verify(resp).end();
    }
  }

  @Test
  void putDataset_withCatalogue_created_201() {
    JsonObject status = new JsonObject()
        .put("status", "created")
        .put(HttpHeaders.LOCATION.toString(), "/orig/1");

    DatasetsService svc = mock(DatasetsService.class);
    when(svc.putDatasetOrigin(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Future.succeededFuture(status));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      // RequestParameters
      RequestParameter id = mock(RequestParameter.class);
      when(id.getString()).thenReturn("orig-id");

      RequestParameter body = mock(RequestParameter.class);
      when(body.isEmpty()).thenReturn(false);
      when(body.toString()).thenReturn("{}");

      RequestParameter catalogue = mock(RequestParameter.class);
      when(catalogue.getString()).thenReturn("cat-1");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(id);
      when(params.body()).thenReturn(body);
      when(params.queryParameter("catalogue")).thenReturn(catalogue);

      // Response
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      // Parsed headers chain
      ParsedHeaderValues parsed = mock(ParsedHeaderValues.class);
      MIMEHeader mime = mock(MIMEHeader.class);
      when(mime.value()).thenReturn("application/ld+json");
      when(parsed.contentType()).thenReturn(mime);

      // RoutingContext
      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.parsedHeaders()).thenReturn(parsed);
      when(ctx.response()).thenReturn(resp);

      // (Optional) bypass heavy validator to keep this unit test focused
      DatasetHandler handler = spy(new DatasetHandler(vertx));
      doReturn(false).when(handler).datasetIsInvalid(any(), anyString(), anyString());

      // act
      handler.handlePutDataset(ctx);

      // assert
      verify(resp).setStatusCode(201);
      verify(resp).putHeader(eq(HttpHeaders.LOCATION), eq("/orig/1"));
      verify(resp).end();
    }
  }


  // -------------------- handlePutDatasetOrigin --------------------

  @Test
  void putDatasetOrigin_created_201_whenValid() {
    JsonObject status = new JsonObject().put("status", "created")
        .put(HttpHeaders.LOCATION.toString(), "/orig/2");
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.putDatasetOriginHealthData(anyString(), anyString(), anyString(), anyString(),
        anyBoolean(), anyBoolean())).thenReturn(Future.succeededFuture(status));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      // Spy handler to skip validator logic
      DatasetHandler handler = spy(new DatasetHandler(vertx));
      doReturn(false).when(handler).datasetIsInvalid(any(), anyString(), anyString());

      // --- RequestParameters
      RequestParameter cat = mock(RequestParameter.class); when(cat.getString()).thenReturn("cat-2");
      RequestParameter qid = mock(RequestParameter.class); when(qid.getString()).thenReturn("orig-xyz");
      RequestParameter bodyParam = mock(RequestParameter.class);
      when(bodyParam.isEmpty()).thenReturn(false);
      when(bodyParam.toString()).thenReturn("{}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(cat);
      when(params.queryParameter("originalId")).thenReturn(qid);
      when(params.body()).thenReturn(bodyParam);

      // --- parsedHeaders()
      ParsedHeaderValues parsed = mock(ParsedHeaderValues.class);
      MIMEHeader mime = mock(MIMEHeader.class);
      when(mime.value()).thenReturn("application/json");
      when(parsed.contentType()).thenReturn(mime);

      // --- context.body()
      RequestBody reqBody = mock(RequestBody.class);
      when(reqBody.asString()).thenReturn("{}");

      // --- RoutingContext & response
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.parsedHeaders()).thenReturn(parsed);
      when(ctx.body()).thenReturn(reqBody);   // <<< important
      when(ctx.response()).thenReturn(resp);

      // act
      handler.handlePutDatasetOrigin(ctx);

      // assert
      verify(resp).setStatusCode(201);
      verify(resp).putHeader(eq(HttpHeaders.LOCATION), eq("/orig/2"));
      verify(resp).end();
    }
  }

  @Test
  void putDatasetOrigin_shortCircuits_whenInvalid() {
    DatasetsService svc = mock(DatasetsService.class);
    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      DatasetHandler handler = spy(new DatasetHandler(vertx));
      doReturn(true).when(handler).datasetIsInvalid(any(), anyString(), anyString()); // force early return

      // ---- RequestParameters: MUST stub both path and query params
      RequestParameter catParam = mock(RequestParameter.class);
      when(catParam.getString()).thenReturn("cat-2");

      RequestParameter originParam = mock(RequestParameter.class);
      when(originParam.getString()).thenReturn("orig-xyz");

      RequestParameter bodyParam = mock(RequestParameter.class);
      when(bodyParam.isEmpty()).thenReturn(false);
      when(bodyParam.toString()).thenReturn("{}");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter(PATH_PARAM_CATALOGUE)).thenReturn(catParam);            // <-- required
      when(params.queryParameter(QUERY_PARAM_DATASET)).thenReturn(originParam);         // <-- required
      when(params.body()).thenReturn(bodyParam);

      // ---- parsedHeaders() chain
      ParsedHeaderValues parsed = mock(ParsedHeaderValues.class);
      MIMEHeader mime = mock(MIMEHeader.class);
      when(mime.value()).thenReturn("application/json");
      when(parsed.contentType()).thenReturn(mime);

      // ---- body() chain
      RequestBody reqBody = mock(RequestBody.class);
      when(reqBody.asString()).thenReturn("{}");

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.parsedHeaders()).thenReturn(parsed);
      when(ctx.body()).thenReturn(reqBody);
      when(ctx.response()).thenReturn(resp);

      // act
      handler.handlePutDatasetOrigin(ctx);

      // assert: service must NOT be called if invalid
      verify(svc, never()).putDatasetOriginHealthData(
          anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean());
    }
  }

  // -------------------- handlePostCatalogueDataset --------------------

  @Test
  void postCatalogueDataset_wrongContentType_callsFailure415() {
    DatasetsService svc = mock(DatasetsService.class);

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {

      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      // path param
      RequestParameter cat = mock(RequestParameter.class);
      when(cat.getString()).thenReturn("cat-1");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(cat);

      // response
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      // parsedHeaders() -> contentType() == null (invalid)
      ParsedHeaderValues parsed = mock(ParsedHeaderValues.class);
      when(parsed.contentType()).thenReturn(null);

      // body() – mock defensively in case handler touches it before failing
      RequestBody rb = mock(RequestBody.class);
      when(rb.asString()).thenReturn("{}");

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);
      when(ctx.parsedHeaders()).thenReturn(parsed);
      when(ctx.body()).thenReturn(rb);

      new DatasetHandler(vertx).handlePostCatalogueDataset(ctx);

      hubStatic.verify(() -> HubRepo.failureResponse(
          eq(ctx),
          argThat(t -> t instanceof ServiceException se && se.failureCode() == 415)
      ));
    }
  }


  @Test
  void postCatalogueDataset_missingBody_callsFailure400() {
    // service is never called in this path, but needs to exist for the proxy
    DatasetsService svc = mock(DatasetsService.class);

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class);
        MockedStatic<HubRepo> hubStatic = mockStatic(HubRepo.class)) {

      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      // --- RequestParameters (catalogue present, body missing)
      RequestParameter catParam = mock(RequestParameter.class);
      when(catParam.getString()).thenReturn("cat-1");

      RequestParameter bodyParam = mock(RequestParameter.class);
      when(bodyParam.isEmpty()).thenReturn(true); // <- missing body

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(catParam);
      when(params.body()).thenReturn(bodyParam);

      // --- parsedHeaders() chain
      ParsedHeaderValues parsed = mock(ParsedHeaderValues.class);
      MIMEHeader mime = mock(MIMEHeader.class);
      when(mime.value()).thenReturn("application/ld+json");
      when(parsed.contentType()).thenReturn(mime);

      // --- RoutingContext & response
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.parsedHeaders()).thenReturn(parsed);
      when(ctx.response()).thenReturn(resp);

      // Optional: if your handler ever touches context.body(), make it safe
      RequestBody rb = mock(RequestBody.class);
      when(rb.asString()).thenReturn(null);
      when(ctx.body()).thenReturn(rb);

      // act
      new DatasetHandler(vertx).handlePostCatalogueDataset(ctx);

      // assert: service was NOT called; 400 bubbled via HubRepo.failureResponse
      verify(svc, never()).postDataset(anyString(), anyString(), anyString(), anyBoolean());
      hubStatic.verify(() -> HubRepo.failureResponse(eq(ctx),
          argThat(t -> t instanceof ServiceException se && se.failureCode() == 400)));
    }
  }


  @Test
  void postCatalogueDataset_created_201_jsonBody() {
    JsonObject status = new JsonObject()
        .put("status", "created")
        .put("id", "ds-99")
        .put(HttpHeaders.LOCATION.toString(), "/ds/99");

    DatasetsService svc = mock(DatasetsService.class);
    when(svc.postDataset(anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Future.succeededFuture(status));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      // Spy to bypass validator
      DatasetHandler handler = spy(new DatasetHandler(vertx));
      doReturn(false).when(handler).datasetIsInvalid(any(), anyString(), anyString());

      // ---- RequestParameters
      RequestParameter cat = mock(RequestParameter.class);
      when(cat.getString()).thenReturn("cat-1");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(cat);

      // ---- parsedHeaders() chain
      ParsedHeaderValues parsed = mock(ParsedHeaderValues.class);
      MIMEHeader mime = mock(MIMEHeader.class);
      when(mime.value()).thenReturn("application/ld+json");
      when(parsed.contentType()).thenReturn(mime);

      // ---- body() chain
      RequestBody reqBody = mock(RequestBody.class);
      when(reqBody.asString()).thenReturn("{}");

      // ---- response + ctx
      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.parsedHeaders()).thenReturn(parsed);   // <-- important
      when(ctx.body()).thenReturn(reqBody);          // <-- important
      when(ctx.response()).thenReturn(resp);

      // act
      handler.handlePostCatalogueDataset(ctx);

      // assert
      verify(resp).setStatusCode(201);
      verify(resp).putHeader(eq(HttpHeaders.LOCATION), eq("/ds/99"));
      verify(resp).putHeader(eq(HttpHeaders.CONTENT_TYPE), eq("application/json"));
      verify(resp).end(contains("\"id\":\"ds-99\""));
    }
  }

  // -------------------- handleDeleteDataset / Origin / Legacy --------------------

  @Test
  void deleteDataset_noQuery_callsDeleteDataset_204() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.deleteDataset(anyString())).thenReturn(Future.succeededFuture());

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class); when(id.getString()).thenReturn("ds-7");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(id);
      when(params.queryParameter("catalogue")).thenReturn(null);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);

      new DatasetHandler(vertx).handleDeleteDataset(ctx);

      verify(svc).deleteDataset(eq("ds-7"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  @Test
  void deleteDataset_withQuery_callsDeleteDatasetOrigin_204() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.deleteDatasetOrigin(anyString(), anyString())).thenReturn(Future.succeededFuture());

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class); when(id.getString()).thenReturn("ds-7");
      RequestParameter cat = mock(RequestParameter.class); when(cat.getString()).thenReturn("cat-x");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(id);
      when(params.queryParameter("catalogue")).thenReturn(cat);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);

      new DatasetHandler(vertx).handleDeleteDataset(ctx);

      verify(svc).deleteDatasetOrigin(eq("ds-7"), eq("cat-x"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  @Test
  void deleteDatasetOrigin_path_success_204() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.deleteDatasetOrigin(anyString(), anyString())).thenReturn(Future.succeededFuture());

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter cat = mock(RequestParameter.class); when(cat.getString()).thenReturn("cat-9");
      RequestParameter orig = mock(RequestParameter.class); when(orig.getString()).thenReturn("orig-1");

      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("catalogueId")).thenReturn(cat);
      when(params.queryParameter("originalId")).thenReturn(orig);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);

      new DatasetHandler(vertx).handleDeleteDatasetOrigin(ctx);

      verify(svc).deleteDatasetOrigin(eq("orig-1"), eq("cat-9"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  @Test
  void deleteDatasetLegacy_success_204() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.deleteDatasetOrigin(anyString(), anyString())).thenReturn(Future.succeededFuture());

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class); when(id.getString()).thenReturn("orig-5");
      RequestParameter cat = mock(RequestParameter.class); when(cat.getString()).thenReturn("cat-5");

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("id")).thenReturn(id);
      when(params.queryParameter("catalogue")).thenReturn(cat);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);

      new DatasetHandler(vertx).handleDeleteDatasetLegacy(ctx);

      verify(svc).deleteDatasetOrigin(eq("orig-5"), eq("cat-5"));
      verify(resp).setStatusCode(204);
      verify(resp).end();
    }
  }

  // -------------------- handleListCatalogueDatasets --------------------

  @Test
  void listCatalogueDatasets_nonMetadata_forcesJson() {
    DatasetsService svc = mock(DatasetsService.class);
    when(svc.listCatalogueDatasets(anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(Future.succeededFuture("[]"));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter limit = mock(RequestParameter.class); when(limit.getInteger()).thenReturn(20);
      RequestParameter offset = mock(RequestParameter.class); when(offset.getInteger()).thenReturn(2);
      RequestParameter valueType = mock(RequestParameter.class); when(valueType.getString()).thenReturn("data");

      RequestParameters params = mock(RequestParameters.class);
      when(params.queryParameter("limit")).thenReturn(limit);
      when(params.queryParameter("offset")).thenReturn(offset);
      when(params.queryParameter("valueType")).thenReturn(valueType);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      // NEW: request needed by ContentNegotiation
      HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_SELF);
      when(req.method()).thenReturn(HttpMethod.GET);
      // Accept can be anything; handler will force JSON for non-metadata
      when(req.getHeader(HttpHeaders.ACCEPT.toString())).thenReturn("*/*");

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.pathParam("catalogueId")).thenReturn("cat-77");
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.response()).thenReturn(resp);
      when(ctx.request()).thenReturn(req); // <-- important

      new DatasetHandler(vertx).handleListCatalogueDatasets(ctx);

      verify(svc).listCatalogueDatasets(
          eq(ContentType.JSON.getMimeType()),
          eq("data"),
          eq(""),
          eq(20),
          eq(2)
      );
      verify(resp).end(anyString());
    }
  }


  // -------------------- handleIndexDataset --------------------

  @Test
  void indexDataset_success_200_json() {
    DatasetsService svc = mock(DatasetsService.class);
    JsonObject result = new JsonObject().put("ok", true);
    when(svc.indexDataset(anyString(), any())).thenReturn(Future.succeededFuture(result));

    try (MockedStatic<DatasetsService> svcStatic = mockStatic(DatasetsService.class)) {
      svcStatic.when(() -> DatasetsService.createProxy(eq(vertx), anyString())).thenReturn(svc);

      RequestParameter id = mock(RequestParameter.class);
      when(id.getString()).thenReturn("ds-idx");
      RequestParameters params = mock(RequestParameters.class);
      when(params.pathParameter("datasetId")).thenReturn(id);

      HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);

      RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_SELF);
      when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
      when(ctx.get("catalogueId")).thenReturn("cat-idx");
      when(ctx.response()).thenReturn(resp);

      new DatasetHandler(vertx).handleIndexDataset(ctx);

      verify(resp).putHeader(eq(HttpHeaders.CONTENT_TYPE), eq("application/json"));
      verify(resp).setStatusCode(200);

      ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
      verify(resp).end(body.capture());

      // parse and assert structurally
      assertTrue(new JsonObject(body.getValue()).getBoolean("ok"));
    }
  }
}
