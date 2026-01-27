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
package io.piveau.hub.ext.trust;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.RSAKey;
import io.piveau.hub.Constants;
import io.piveau.hub.ext.trust.services.DIDService;
import io.piveau.hub.ext.trust.services.SetupService;
import io.piveau.hub.ext.trust.services.TrustService;
import io.piveau.rdf.RDFMimeTypes;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class TrustHandlerTest {

  // ---- Helpers to inject private fields ----
  private static void setField(Object target, String name, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static <T> T getField(Object target, String name, Class<T> type) throws Exception {
    Field f = target.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }

  private static RoutingContext stubCommonRoutingContext() {
    RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
    HttpServerRequest req = mock(HttpServerRequest.class, Answers.RETURNS_DEEP_STUBS);
    when(req.method()).thenReturn(HttpMethod.GET);
    when(req.getHeader(anyString())).thenReturn(null);
    when(ctx.request()).thenReturn(req);

    HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
    when(ctx.response()).thenReturn(resp);
    return ctx;
  }

  @Test
  void handleCreateDIDDocument_success_jsonld() throws Exception {
    Vertx vertx = mock(Vertx.class, Answers.RETURNS_DEEP_STUBS);
    JsonObject cfg = new JsonObject().put(Constants.ENV_PIVEAU_TRUST_CONFIG, new JsonObject());
    TrustHandler handler = new TrustHandler(vertx, cfg);

    // Inject mocks
    DIDService didService = mock(DIDService.class);
    RSAKey rsaPublicKey = mock(RSAKey.class);
    setField(handler, "didService", didService);
    handler.rsaPublicKey = rsaPublicKey; // public field
    handler.did = "did:example:issuer";

    // Input parameters
    RequestParameter didParam = mock(RequestParameter.class);
    when(didParam.getString()).thenReturn("did:example:123");
    RequestParameters params = mock(RequestParameters.class);
    when(params.queryParameter("did")).thenReturn(didParam);

    RoutingContext ctx = stubCommonRoutingContext();
    when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

    // Service behavior
    JsonObject didDoc = new JsonObject().put("id", "did:example:123");
    when(didService.createDID(eq("did:example:123"), eq("JWK2020-RSA"), same(rsaPublicKey)))
        .thenReturn(Future.succeededFuture(didDoc));

    // Call
    handler.handleCreateDIDDocument(ctx);

    // Verify
    HttpServerResponse resp = ctx.response();
    verify(resp).putHeader(eq(io.vertx.core.http.HttpHeaders.CONTENT_TYPE), eq(RDFMimeTypes.JSONLD));
    verify(resp).end(eq(didDoc.toString()));
  }

  @Test
  void handleGetDID_success_jsonld() throws Exception {
    Vertx vertx = mock(Vertx.class, Answers.RETURNS_DEEP_STUBS);
    JsonObject cfg = new JsonObject().put(Constants.ENV_PIVEAU_TRUST_CONFIG, new JsonObject());
    TrustHandler handler = new TrustHandler(vertx, cfg);

    DIDService didService = mock(DIDService.class);
    setField(handler, "didService", didService);

    RoutingContext ctx = stubCommonRoutingContext();
    when(ctx.request().getParam("did")).thenReturn("did:example:abc");

    JsonObject didDoc = new JsonObject().put("id", "did:example:abc");
    when(didService.getDID(eq("did:example:abc"))).thenReturn(Future.succeededFuture(didDoc));

    handler.handleGetDID(ctx);

    HttpServerResponse resp = ctx.response();
    verify(resp).putHeader(eq(io.vertx.core.http.HttpHeaders.CONTENT_TYPE), eq(RDFMimeTypes.JSONLD));
    verify(resp).end(eq(didDoc.toString()));
  }

  @Test
  void handleGetSetup_success_jsonld() throws Exception {
    Vertx vertx = mock(Vertx.class, Answers.RETURNS_DEEP_STUBS);
    JsonObject cfg = new JsonObject().put(Constants.ENV_PIVEAU_TRUST_CONFIG, new JsonObject());
    TrustHandler handler = new TrustHandler(vertx, cfg);

    SetupService setupService = mock(SetupService.class);
    setField(handler, "setupService", setupService);

    RoutingContext ctx = stubCommonRoutingContext();

    JsonObject complianceVC = new JsonObject().put("type", "ComplianceCredential");
    when(setupService.createSetup()).thenReturn(Future.succeededFuture(complianceVC));

    handler.handleGetSetup(ctx);

    HttpServerResponse resp = ctx.response();
    verify(resp).putHeader(eq(io.vertx.core.http.HttpHeaders.CONTENT_TYPE), eq(RDFMimeTypes.JSONLD));
    verify(resp).end(eq(complianceVC.encodePrettily()));
  }

  @Test
  void handleGetSetupVC_success_jsonld() throws Exception {
    Vertx vertx = mock(Vertx.class, Answers.RETURNS_DEEP_STUBS);
    JsonObject cfg = new JsonObject().put(Constants.ENV_PIVEAU_TRUST_CONFIG, new JsonObject());
    TrustHandler handler = new TrustHandler(vertx, cfg);

    SetupService setupService = mock(SetupService.class);
    setField(handler, "setupService", setupService);

    RoutingContext ctx = stubCommonRoutingContext();
    when(ctx.pathParam("fileName")).thenReturn("core-vc.json");

    JsonObject vc = new JsonObject().put("id", "urn:vc:core");
    when(setupService.getSetupVC(eq("core-vc.json"))).thenReturn(Future.succeededFuture(vc));

    handler.handleGetSetupVC(ctx);

    HttpServerResponse resp = ctx.response();
    verify(resp).putHeader(eq(io.vertx.core.http.HttpHeaders.CONTENT_TYPE), eq(RDFMimeTypes.JSONLD));
    verify(resp).end(eq(vc.encodePrettily()));
  }

  @Test
  void handleGetTrustByResource_success_jsonld() throws Exception {
    Vertx vertx = mock(Vertx.class, Answers.RETURNS_DEEP_STUBS);
    // minimal config so TrustHandler ctor doesn’t do heavy init
    JsonObject cfg = new JsonObject().put(Constants.ENV_PIVEAU_TRUST_CONFIG, new JsonObject());
    TrustHandler handler = new TrustHandler(vertx, cfg);

    // inject a mocked TrustService
    TrustService trustService = mock(TrustService.class);
    Field f = TrustHandler.class.getDeclaredField("trustService");
    f.setAccessible(true);
    f.set(handler, trustService);

    // routing context + request basics for ContentNegotiation
    RoutingContext ctx = mock(RoutingContext.class, Answers.RETURNS_DEEP_STUBS);
    when(ctx.request().method()).thenReturn(HttpMethod.GET);
    when(ctx.request().getHeader(anyString())).thenReturn(null);
    when(ctx.pathParam("id")).thenReturn("resource-1");

    // validation params (cached, showCompliance, type)
    RequestParameter cached = mock(RequestParameter.class);
    when(cached.getBoolean()).thenReturn(true);
    RequestParameter showCompliance = mock(RequestParameter.class);
    when(showCompliance.getBoolean()).thenReturn(false);
    RequestParameter typeParam = mock(RequestParameter.class);
    when(typeParam.getString()).thenReturn("dataset");

    RequestParameters params = mock(RequestParameters.class);
    when(params.queryParameter("cached")).thenReturn(cached);
    when(params.queryParameter("showCompliance")).thenReturn(showCompliance);
    when(params.pathParameter("type")).thenReturn(typeParam);
    when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);

    HttpServerResponse resp = mock(HttpServerResponse.class, Answers.RETURNS_SELF);
    when(ctx.response()).thenReturn(resp);

    // STUB: relax matchers so it always matches and returns a Future
    JsonObject result = new JsonObject().put("trust", "ok");
    when(trustService.getTrustByResource(anyBoolean(), anyBoolean(), anyString(), anyString()))
        .thenReturn(io.vertx.core.Future.succeededFuture(result));

    // act
    handler.handleGetTrustByResource(ctx);

    // assert
    verify(trustService).getTrustByResource(eq(true), eq(false), eq("dataset"), eq(""));
    verify(resp).putHeader(eq(HttpHeaders.CONTENT_TYPE), eq(RDFMimeTypes.JSONLD));
    verify(resp).end(eq(result.encodePrettily()));
  }
}
