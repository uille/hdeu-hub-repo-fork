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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.piveau.hub.services.identifiers.IdentifiersService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentifiersHandlerTest {

  /** Utility: create handler and swap in a mock IdentifiersService via reflection. */
  private IdentifiersHandler newHandlerWithMock(IdentifiersService svc) throws Exception {
    IdentifiersHandler handler = new IdentifiersHandler(Vertx.vertx());
    Field f = IdentifiersHandler.class.getDeclaredField("identifiersService");
    f.setAccessible(true);
    f.set(handler, svc);
    return handler;
  }

  // ---------- createDatasetIdentifier ----------

  @Test
  void createDatasetIdentifier_success_200_json() throws Exception {
    IdentifiersService svc = mock(IdentifiersService.class);
    IdentifiersHandler handler = newHandlerWithMock(svc);

    RoutingContext ctx = mock(RoutingContext.class);
    HttpServerResponse resp = mock(HttpServerResponse.class);

    // path + query
    when(ctx.pathParam("datasetId")).thenReturn("ds-1");
    when(ctx.queryParam("catalogue")).thenReturn(java.util.List.of("cat-1"));
    when(ctx.queryParam("type")).thenReturn(java.util.List.of("doi"));

    when(ctx.response()).thenReturn(resp);

    // IMPORTANT: stub the CharSequence overload so the call returns resp (not null)
    when(resp.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(resp);
    when(resp.setStatusCode(anyInt())).thenReturn(resp);
    // (end() returns void; no stubbing needed)

    // Service callback success
    doAnswer(inv -> {
      @SuppressWarnings("unchecked")
      io.vertx.core.Handler<io.vertx.core.AsyncResult<Object>> h = inv.getArgument(3);
      h.handle(io.vertx.core.Future.succeededFuture(new io.vertx.core.json.JsonObject().put("ok", true)));
      return null;
    }).when(svc).createIdentifier(eq("ds-1"), eq("cat-1"), eq("doi"), any());

    // Act
    handler.createDatasetIdentifier(ctx);

    // Assert
    verify(resp).putHeader(eq(HttpHeaders.CONTENT_TYPE), eq("application/json")); // CharSequence, String
    verify(resp).setStatusCode(200);
    verify(resp).end(anyString());
  }


  @Test
  void checkDatasetIdentifierEligibility_success_withType() throws Exception {
    IdentifiersService svc = mock(IdentifiersService.class);
    IdentifiersHandler handler = newHandlerWithMock(svc);

    RoutingContext ctx = mock(RoutingContext.class);
    HttpServerResponse resp = mock(HttpServerResponse.class);
    RequestParameters params = mock(RequestParameters.class);
    RequestParameter typeParam = mock(RequestParameter.class);

    // path + query
    when(ctx.pathParam("datasetId")).thenReturn("ds-2");
    when(ctx.queryParam("catalogue")).thenReturn(List.of("cat-2"));

    // validation params
    when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
    when(params.queryParameter("type")).thenReturn(typeParam);
    when(typeParam.getString()).thenReturn("handle");

    // response (make fluent for BOTH overloads)
    when(ctx.response()).thenReturn(resp);
    when(resp.putHeader(anyString(), anyString())).thenReturn(resp);
    when(resp.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(resp);
    when(resp.setStatusCode(anyInt())).thenReturn(resp);

    // service callback success
    doAnswer(inv -> {
      Handler<AsyncResult<Object>> h = inv.getArgument(3);
      h.handle(Future.succeededFuture(new JsonObject().put("eligible", true)));
      return null;
    }).when(svc).checkIdentifierRequirement(eq("ds-2"), eq("cat-2"), eq("handle"), any());

    // act
    handler.checkDatasetIdentifierEligibility(ctx);

    // assert
    verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    verify(resp).setStatusCode(200);
    verify(resp).end(anyString());
  }

  @Test
  void checkDatasetIdentifierEligibility_success_withoutType_null() throws Exception {
    IdentifiersService svc = mock(IdentifiersService.class);
    IdentifiersHandler handler = newHandlerWithMock(svc);

    RoutingContext ctx = mock(RoutingContext.class);
    HttpServerResponse resp = mock(HttpServerResponse.class);
    RequestParameters params = mock(RequestParameters.class);

    // path + query
    when(ctx.pathParam("datasetId")).thenReturn("ds-3");
    when(ctx.queryParam("catalogue")).thenReturn(List.of("cat-3"));

    // validation params (no "type" -> null)
    when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
    when(params.queryParameter("type")).thenReturn(null);

    // response must be fluent for BOTH putHeader overloads
    when(ctx.response()).thenReturn(resp);
    when(resp.putHeader(anyString(), anyString())).thenReturn(resp);
    when(resp.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(resp);
    when(resp.setStatusCode(anyInt())).thenReturn(resp);

    // service callback success; 3rd arg (identifierType) should be null
    doAnswer(inv -> {
      // you could assert here if you want:
      // assertNull(inv.getArgument(2));
      @SuppressWarnings("unchecked")
      Handler<AsyncResult<Object>> h = inv.getArgument(3);
      h.handle(Future.succeededFuture(new io.vertx.core.json.JsonObject().put("eligible", true)));
      return null;
    }).when(svc).checkIdentifierRequirement(eq("ds-3"), eq("cat-3"), isNull(), any());

    // act
    handler.checkDatasetIdentifierEligibility(ctx);

    // assert
    verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    verify(resp).setStatusCode(200);
    verify(resp).end(anyString());
  }

  // ---------- handleExtractDatasetIdentifier ----------

  @Test
  void handleExtractDatasetIdentifier_emptyBody_400() throws Exception {
    IdentifiersService svc = mock(IdentifiersService.class);
    IdentifiersHandler handler = newHandlerWithMock(svc);

    RoutingContext ctx = mock(RoutingContext.class);
    RequestParameters params = mock(RequestParameters.class);
    RequestParameter body = mock(RequestParameter.class);

    when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
    when(params.body()).thenReturn(body);
    when(body.isEmpty()).thenReturn(true);

    handler.handleExtractDatasetIdentifier(ctx);

    verify(ctx).fail(eq(400), any(Throwable.class));
    verifyNoInteractions(svc);
  }

  @Test
  void handleExtractDatasetIdentifier_notFound_404() throws Exception {
    IdentifiersService svc = mock(IdentifiersService.class);
    IdentifiersHandler handler = newHandlerWithMock(svc);

    RoutingContext ctx = mock(RoutingContext.class);
    HttpServerResponse resp = mock(HttpServerResponse.class);
    stubFluent(resp);                        // <<< add this

    RequestParameters params = mock(RequestParameters.class);
    RequestParameter body = mock(RequestParameter.class);
    ParsedHeaderValues parsed = mock(ParsedHeaderValues.class);
    MIMEHeader ct = mock(MIMEHeader.class);

    when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
    when(params.body()).thenReturn(body);
    when(body.isEmpty()).thenReturn(false);

    String ttlNoIdentifier = String.join("\n",
        "@prefix dcat: <http://www.w3.org/ns/dcat#> .",
        "[] a dcat:Dataset ."
    );
    when(body.toString()).thenReturn(ttlNoIdentifier);

    when(ctx.parsedHeaders()).thenReturn(parsed);
    when(parsed.contentType()).thenReturn(ct);
    when(ct.value()).thenReturn("text/turtle");

    when(ctx.response()).thenReturn(resp);

    handler.handleExtractDatasetIdentifier(ctx);

    verify(resp).setStatusCode(404);
    verify(resp).putHeader("Content-Type", "application/json");
    verify(resp).end(contains("No dataset identifier found"));
  }

  private static void stubFluent(HttpServerResponse resp) {
    // setStatusCode returns the same response (fluent)
    when(resp.setStatusCode(anyInt())).thenReturn(resp);

    // Cover BOTH putHeader overloads:
    // (String, String)
    doReturn(resp).when(resp).putHeader(anyString(), anyString());
    // (CharSequence, CharSequence)
    when(resp.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(resp);
  }

  @Test
  void handleExtractDatasetIdentifier_found_200() throws Exception {
    IdentifiersService svc = mock(IdentifiersService.class);
    IdentifiersHandler handler = newHandlerWithMock(svc);

    RoutingContext ctx = mock(RoutingContext.class);
    HttpServerResponse resp = mock(HttpServerResponse.class);
    RequestParameters params = mock(RequestParameters.class);
    RequestParameter body = mock(RequestParameter.class);
    ParsedHeaderValues parsed = mock(ParsedHeaderValues.class);
    MIMEHeader ct = mock(MIMEHeader.class);

    when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
    when(params.body()).thenReturn(body);
    when(body.isEmpty()).thenReturn(false);

    // Minimal, valid Turtle: dataset typed as dcat:Dataset and identifier is xsd:anyURI
    String turtle = """
    @prefix dct:  <http://purl.org/dc/terms/> .
    @prefix dcat: <http://www.w3.org/ns/dcat#> .
    @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .

    <http://example.org/dataset/6ad1de14-b670-458c-b847-2d77243bd343>
      a dcat:Dataset ;
      dct:identifier "http://ehelse.healthdataportal.eu/dataset/6ad1de14-b670-458c-b847-2d77243bd343"^^xsd:anyURI .
  """;
    when(body.toString()).thenReturn(turtle);

    when(ctx.parsedHeaders()).thenReturn(parsed);
    when(parsed.contentType()).thenReturn(ct);
    when(ct.value()).thenReturn("text/turtle");

    when(ctx.response()).thenReturn(resp);
    // Make HttpServerResponse fluent for both putHeader overloads
    when(resp.putHeader(anyString(), anyString())).thenReturn(resp);
    when(resp.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(resp);
    when(resp.setStatusCode(anyInt())).thenReturn(resp);

    // act
    handler.handleExtractDatasetIdentifier(ctx);

    // assert
    verify(resp).setStatusCode(200);
    verify(resp).putHeader(eq("Content-Type"), eq("application/json"));
    verify(resp).end(contains("\"dataset_id\""));
    verify(resp).end(contains("6ad1de14-b670-458c-b847-2d77243bd343"));
  }
}
