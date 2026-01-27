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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContentNegotiationTest {

  private Vertx vertx;
  private WebClient client;
  private HttpServer server;
  private int port;

  @BeforeAll
  void setUp(VertxTestContext ctx) {
    vertx = Vertx.vertx();
    client = WebClient.create(vertx);

    Router router = Router.router(vertx);

    // Route 1: id with extension (.ttl) => should set Turtle MIME
    router.get("/dataset/:idWithExt").handler(rc -> {
      // Inject RequestParameters into rc (as if a ValidationHandler had run)
      RequestParameters params = mock(RequestParameters.class);
      RequestParameter idParam = mock(RequestParameter.class);
      when(idParam.getString()).thenReturn(rc.pathParam("idWithExt")); // e.g., "my-ds.ttl"
      when(params.pathParameter("id")).thenReturn(idParam);
      rc.put(ValidationHandler.REQUEST_CONTEXT_KEY, params);

      ContentNegotiation negotiation = new ContentNegotiation(rc, "id", ContentType.DEFAULT);
      assertEquals("my-ds", negotiation.getId());
      assertEquals(ContentType.TURTLE.getMimeType(), negotiation.getAcceptType());

      negotiation.headOrGetResponse("hello turtle");
    });

    // Route 2: id without extension; Accept is supported -> use Accept
    router.get("/dataset-accept/:id").handler(rc -> {
      // Inject params where "id" has no extension
      RequestParameters params = mock(RequestParameters.class);
      RequestParameter idParam = mock(RequestParameter.class);
      when(idParam.getString()).thenReturn(rc.pathParam("id")); // e.g., "plain-id"
      when(params.pathParameter("resId")).thenReturn(idParam);   // we will ask for "resId" below
      rc.put(ValidationHandler.REQUEST_CONTEXT_KEY, params);

      // Simulate Accept header via real request; class reads parsedHeaders internally
      ContentNegotiation negotiation = new ContentNegotiation(rc, "resId", ContentType.DEFAULT);
      assertEquals("plain-id", negotiation.getId());
      // Client will send Accept: application/ld+json; expect JSON-LD
      assertEquals(ContentType.JSON_LD.getMimeType(), negotiation.getAcceptType());

      negotiation.headOrGetResponse("{\"ok\":true}");
    });

    // Route 3: id without extension; Accept unsupported -> fallback to DEFAULT
    router.get("/dataset-default/:id").handler(rc -> {
      RequestParameters params = mock(RequestParameters.class);
      RequestParameter idParam = mock(RequestParameter.class);
      when(idParam.getString()).thenReturn(rc.pathParam("id")); // e.g., "plain-id"
      when(params.pathParameter("id")).thenReturn(idParam);
      rc.put(ValidationHandler.REQUEST_CONTEXT_KEY, params);

      ContentNegotiation negotiation = new ContentNegotiation(rc, "id", ContentType.DEFAULT);
      assertEquals(ContentType.DEFAULT.getMimeType(), negotiation.getAcceptType());
      negotiation.headOrGetResponse("default-body");
    });

    // Route 4: No RequestParameters in context -> id == "" and fallback to DEFAULT
    router.get("/no-params").handler(rc -> {
      // Do NOT put ValidationHandler.REQUEST_CONTEXT_KEY
      ContentNegotiation negotiation = new ContentNegotiation(rc);
      assertEquals("", negotiation.getId());
      assertEquals(ContentType.DEFAULT.getMimeType(), negotiation.getAcceptType());
      negotiation.headOrGetResponse("no-params-body");
    });

    // Route 5: HEAD request should set Content-Length and no body
    router.head("/head/:id").handler(rc -> {
      RequestParameters params = mock(RequestParameters.class);
      RequestParameter idParam = mock(RequestParameter.class);
      when(idParam.getString()).thenReturn(rc.pathParam("id") + ".jsonld"); // include ext to avoid Accept usage
      when(params.pathParameter("id")).thenReturn(idParam);
      rc.put(ValidationHandler.REQUEST_CONTEXT_KEY, params);

      ContentNegotiation negotiation = new ContentNegotiation(rc, "id", ContentType.DEFAULT);
      String content = "{\"k\":\"v\"}";
      negotiation.headOrGetResponse(content);
    });

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

  @AfterAll
  void tearDown(VertxTestContext ctx) {
    server.close().onComplete(ar -> vertx.close().onComplete(v -> ctx.completeNow()));
  }

  @Test
  void idWithExtension_setsMimeToTurtle_andStripsId(VertxTestContext ctx) {
    client.get(port, "localhost", "/dataset/my-ds.ttl")
        .putHeader("Accept", "application/json") // should be ignored because extension wins
        .send(ar -> {
          assertTrue(ar.succeeded());
          var resp = ar.result();
          assertEquals(200, resp.statusCode());
          // Content-Type must be Turtle + charset
          assertEquals(ContentType.TURTLE.getMimeType() + "; charset=UTF-8", resp.getHeader("Content-Type"));
          assertEquals("hello turtle", resp.bodyAsString());
          ctx.completeNow();
        });
  }

  @Test
  void idWithoutExtension_supportedAccept_usesAccept(VertxTestContext ctx) {
    client.get(port, "localhost", "/dataset-accept/plain-id")
        .putHeader("Accept", ContentType.JSON_LD.getMimeType())
        .send(ar -> {
          assertTrue(ar.succeeded());
          var resp = ar.result();
          assertEquals(200, resp.statusCode());
          assertEquals(ContentType.JSON_LD.getMimeType() + "; charset=UTF-8", resp.getHeader("Content-Type"));
          assertEquals("{\"ok\":true}", resp.bodyAsString());
          ctx.completeNow();
        });
  }

  @Test
  void idWithoutExtension_unsupportedAccept_fallsBackToDefault(VertxTestContext ctx) {
    client.get(port, "localhost", "/dataset-default/plain-id")
        .putHeader("Accept", "application/x-does-not-exist")
        .send(ar -> {
          assertTrue(ar.succeeded());
          var resp = ar.result();
          assertEquals(200, resp.statusCode());
          assertEquals(ContentType.DEFAULT.getMimeType() + "; charset=UTF-8", resp.getHeader("Content-Type"));
          assertEquals("default-body", resp.bodyAsString());
          ctx.completeNow();
        });
  }

  @Test
  void noRequestParameters_usesDefaultAndEmptyId(VertxTestContext ctx) {
    client.get(port, "localhost", "/no-params")
        .send(ar -> {
          assertTrue(ar.succeeded());
          var resp = ar.result();
          assertEquals(200, resp.statusCode());
          assertEquals(ContentType.DEFAULT.getMimeType() + "; charset=UTF-8", resp.getHeader("Content-Type"));
          assertEquals("no-params-body", resp.bodyAsString());
          ctx.completeNow();
        });
  }

  @Test
  void head_setsContentLength_andNoBody(VertxTestContext ctx) {
    String content = "{\"k\":\"v\"}";
    int expectedLength = content.getBytes(StandardCharsets.UTF_8).length;

    client.request(HttpMethod.HEAD, port, "localhost", "/head/anything")
        .putHeader("Accept", "application/x-ignored") // extension in handler forces JSON-LD
        .send(ar -> {
          assertTrue(ar.succeeded());
          var resp = ar.result();
          assertEquals(200, resp.statusCode());
          // In the handler we used ".jsonld", so expect JSON-LD mime
          assertEquals(ContentType.JSON_LD.getMimeType() + "; charset=UTF-8", resp.getHeader("Content-Type"));
          assertEquals(String.valueOf(expectedLength), resp.getHeader("Content-Length"));
          // HEAD responses should not have a body
          assertNull(resp.bodyAsString());
          ctx.completeNow();
        });
  }
}
