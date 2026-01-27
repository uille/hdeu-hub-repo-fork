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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.hub.services.translation.TranslationService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TranslationServiceHandlerTest {

  Vertx vertx;
  TranslationServiceHandler handler;
  TranslationService translationService; // mock

  @BeforeEach
  void setUp() throws Exception {
    vertx = Vertx.vertx();
    handler = new TranslationServiceHandler(vertx);

    // Inject mock TranslationService
    translationService = mock(TranslationService.class);
    Field f = TranslationServiceHandler.class.getDeclaredField("translationService");
    f.setAccessible(true);
    f.set(handler, translationService);
  }

  @Test
  void handlePostTranslation_success_returns200() {
    RoutingContext ctx = mock(RoutingContext.class);
    RequestParameters params = mock(RequestParameters.class);
    RequestParameter bodyParam = mock(RequestParameter.class);
    HttpServerResponse resp = mock(HttpServerResponse.class);

    when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
    when(params.body()).thenReturn(bodyParam);
    when(bodyParam.getJsonObject()).thenReturn(new JsonObject().put("x", 1));

    when(ctx.response()).thenReturn(resp);
    when(resp.setStatusCode(anyInt())).thenReturn(resp);

    // Service: success
    doAnswer(inv -> {
      var cb = inv.getArgument(1, io.vertx.core.Handler.class);
      cb.handle(Future.succeededFuture(new JsonObject().put("status", "success")));
      return null;
    }).when(translationService).receiveTranslation(any(JsonObject.class), any());

    handler.handlePostTranslation(ctx);

    verify(resp).setStatusCode(200);
    verify(resp).end();
    verify(ctx, never()).fail(anyInt());
  }

  @Test
  void handlePostTranslation_notFound_callsFail404() {
    RoutingContext ctx = mock(RoutingContext.class);
    RequestParameters params = mock(RequestParameters.class);
    RequestParameter bodyParam = mock(RequestParameter.class);
    HttpServerResponse resp = mock(HttpServerResponse.class); // <-- add

    when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
    when(params.body()).thenReturn(bodyParam);
    when(bodyParam.getJsonObject()).thenReturn(new JsonObject().put("id", "abc"));
    when(ctx.response()).thenReturn(resp); // <-- add

    // Service: Not found
    doAnswer(inv -> {
      var cb = inv.getArgument(1, io.vertx.core.Handler.class);
      cb.handle(Future.succeededFuture(new JsonObject().put("status", "Not found")));
      return null;
    }).when(translationService).receiveTranslation(any(JsonObject.class), any());

    handler.handlePostTranslation(ctx);

    verify(ctx).fail(404);
    // verify on 'resp' (the mock), not on ctx.response() which might be null
    verify(resp, never()).setStatusCode(200);
    verify(resp, never()).end();
  }

  @Test
  void handlePostTranslation_serviceFailure_doesNotThrow_and_noSuccessWrites() {
    RoutingContext ctx = mock(RoutingContext.class);
    RequestParameters params = mock(RequestParameters.class);
    RequestParameter bodyParam = mock(RequestParameter.class);
    HttpServerResponse resp = mock(HttpServerResponse.class);

    when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
    when(params.body()).thenReturn(bodyParam);
    when(bodyParam.getJsonObject()).thenReturn(new JsonObject().put("id", "xyz"));

    when(ctx.response()).thenReturn(resp);

    RuntimeException boom = new RuntimeException("boom");
    doAnswer(inv -> {
      var cb = inv.getArgument(1, io.vertx.core.Handler.class);
      cb.handle(Future.failedFuture(boom));
      return null;
    }).when(translationService).receiveTranslation(any(JsonObject.class), any());

    assertDoesNotThrow(() -> handler.handlePostTranslation(ctx));

    // We don't assert HubRepo static; just ensure no 200 and no 404 fail path
    verify(resp, never()).setStatusCode(200);
    verify(ctx, never()).fail(anyInt());
  }

  @Test
  void handlePostTranslation_exceptionCaught_doesNotThrow() {
    RoutingContext ctx = mock(RoutingContext.class);

    // Force exception inside try {} block
    when(ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenThrow(new IllegalStateException("bad"));

    assertDoesNotThrow(() -> handler.handlePostTranslation(ctx));
    // No specific verifications here since HubRepo is static; we just ensure the catch block handled it.
  }
}
