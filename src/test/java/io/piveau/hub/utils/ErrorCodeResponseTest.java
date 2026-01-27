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
package io.piveau.hub.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ErrorCodeResponseTest {

  private Vertx vertx;
  private WebClient client;
  private HttpServer server;
  private int port;

  @BeforeAll
  void beforeAll() {
    vertx = Vertx.vertx();
    client = WebClient.create(vertx);
  }

  @AfterAll
  void afterAll(VertxTestContext ctx) {
    client.close();
    vertx.close().onComplete(ar -> ctx.completeNow());
  }

  @BeforeEach
  void setup(VertxTestContext ctx) {
    Router router = Router.router(vertx);

    router.get("/forbidden").handler(ErrorCodeResponse::forbidden);
    router.get("/forbiddenCause").handler(rc -> ErrorCodeResponse.forbidden(rc, "not-allowed"));
    router.get("/internal").handler(ErrorCodeResponse::internalServerError);
    router.get("/internalCause").handler(rc -> ErrorCodeResponse.internalServerError(rc, "broken"));
    router.get("/bad").handler(ErrorCodeResponse::badRequest);
    router.get("/badCause").handler(rc -> ErrorCodeResponse.badRequest(rc, "invalid-input"));
    router.get("/unauth").handler(ErrorCodeResponse::unauthorized);
    router.get("/unauthCause").handler(rc -> ErrorCodeResponse.unauthorized(rc, "token-missing"));

    server = vertx.createHttpServer().requestHandler(router);
    server.listen(0).onComplete(ar -> {
      if (ar.succeeded()) {
        port = ar.result().actualPort();
        ctx.completeNow();
      } else {
        ctx.failNow(ar.cause());
      }
    });
  }

  @AfterEach
  void tearDown(VertxTestContext ctx) {
    server.close().onComplete(ar -> ctx.completeNow());
  }

  // ---------- Helpers

  private void assertErrorResponse(io.vertx.ext.web.client.HttpResponse<?> resp,
      int expectedStatus,
      String expectedCauseOrNull) {
    assertEquals(expectedStatus, resp.statusCode());
    assertEquals("application/json", resp.getHeader("Content-Type"));

    // Body can be null (no body) or JSON. Support both.
    String raw = resp.bodyAsString();

    if (expectedCauseOrNull == null) {
      // No cause expected: allow either no body OR minimal JSON.
      if (raw == null || raw.isBlank()) {
        // ok: no body variant
        return;
      }
      // If there is a body, verify it is JSON with status:"error" and no "cause"
      JsonObject body = resp.bodyAsJsonObject();
      assertNotNull(body, "Expected JSON when body is present");
      assertEquals("error", body.getString("status"));
      assertFalse(body.containsKey("cause"), "No cause should be present");
    } else {
      // Cause expected: there must be a JSON body containing it
      assertNotNull(raw, "Expected JSON body");
      JsonObject body = resp.bodyAsJsonObject();
      assertNotNull(body, "Expected JSON body");
      assertEquals("error", body.getString("status"));
      assertEquals(expectedCauseOrNull, body.getString("cause"));
    }
  }

  // ---------- Tests

  @Test
  void forbidden_noCause(VertxTestContext ctx) {
    client.get(port, "localhost", "/forbidden").send(ar -> {
      assertTrue(ar.succeeded());
      assertErrorResponse(ar.result(), 403, null);
      ctx.completeNow();
    });
  }

  @Test
  void forbidden_withCause(VertxTestContext ctx) {
    client.get(port, "localhost", "/forbiddenCause").send(ar -> {
      assertTrue(ar.succeeded());
      assertErrorResponse(ar.result(), 403, "not-allowed");
      ctx.completeNow();
    });
  }

  @Test
  void internal_noCause(VertxTestContext ctx) {
    client.get(port, "localhost", "/internal").send(ar -> {
      assertTrue(ar.succeeded());
      assertErrorResponse(ar.result(), 500, null);
      ctx.completeNow();
    });
  }

  @Test
  void internal_withCause(VertxTestContext ctx) {
    client.get(port, "localhost", "/internalCause").send(ar -> {
      assertTrue(ar.succeeded());
      assertErrorResponse(ar.result(), 500, "broken");
      ctx.completeNow();
    });
  }

  @Test
  void bad_noCause(VertxTestContext ctx) {
    client.get(port, "localhost", "/bad").send(ar -> {
      assertTrue(ar.succeeded());
      assertErrorResponse(ar.result(), 400, null);
      ctx.completeNow();
    });
  }

  @Test
  void bad_withCause(VertxTestContext ctx) {
    client.get(port, "localhost", "/badCause").send(ar -> {
      assertTrue(ar.succeeded());
      assertErrorResponse(ar.result(), 400, "invalid-input");
      ctx.completeNow();
    });
  }

  @Test
  void unauth_noCause(VertxTestContext ctx) {
    client.get(port, "localhost", "/unauth").send(ar -> {
      assertTrue(ar.succeeded());
      assertErrorResponse(ar.result(), 401, null);
      ctx.completeNow();
    });
  }

  @Test
  void unauth_withCause(VertxTestContext ctx) {
    client.get(port, "localhost", "/unauthCause").send(ar -> {
      assertTrue(ar.succeeded());
      assertErrorResponse(ar.result(), 401, "token-missing");
      ctx.completeNow();
    });
  }
}
