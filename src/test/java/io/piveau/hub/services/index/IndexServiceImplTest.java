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
package io.piveau.hub.services.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class IndexServiceImplTest {

  @Mock
  private WebClient web;

  @Mock
  private HttpRequest<Buffer> req;

  @Mock
  private HttpResponse<Buffer> resp;

  private IndexService service;

  private static final JsonObject CONFIG = new JsonObject()
      .put("url", "localhost")
      .put("port", 8082)
      .put("api_key", "a92b814f-63f8-4a5d-abc7-8710de0d2e86");

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    // We can ignore the ready handler return since the constructor always succeeds with "this"
    service = IndexService.create(web, CONFIG, ar -> {
    });

    // Make all fluent calls return the same request mock by default
    when(req.putHeader(anyString(), anyString())).thenReturn(req);
    when(req.timeout(anyLong())).thenReturn(req);
    when(req.addQueryParam(anyString(), anyString())).thenReturn(req);
    when(req.setQueryParam(anyString(), anyString())).thenReturn(req);
    when(req.expect(any())).thenReturn(req);
  }

  // ------------------------- addDataset -------------------------

  @Test
  void addDataset_exists_true_put200_succeeds() throws Exception {
    String id = "abc";
    JsonObject dataset = new JsonObject().put("id", id);

    // entityExists -> GET /datasets/abc returns 200
    HttpRequest<Buffer> getReq = mock(HttpRequest.class);
    HttpResponse<Buffer> getResp = mock(HttpResponse.class);
    when(getResp.statusCode()).thenReturn(200);
    when(web.get(anyInt(), anyString(), eq("/datasets/" + id))).thenReturn(getReq);
    when(getReq.putHeader(anyString(), anyString())).thenReturn(getReq);
    when(getReq.putHeader(anyString(), anyString())).thenReturn(getReq);
    when(getReq.timeout(anyLong())).thenReturn(getReq);
    stubCallbackSend(getReq, getResp);

    // PUT /datasets/{id} returns 200
    HttpRequest<Buffer> putReq = mock(HttpRequest.class);
    HttpResponse<Buffer> putResp = mock(HttpResponse.class);
    when(putResp.statusCode()).thenReturn(200);
    when(web.put(anyInt(), anyString(), eq("/datasets/" + id))).thenReturn(putReq);
    when(putReq.putHeader(anyString(), anyString())).thenReturn(putReq);
    when(putReq.timeout(anyLong())).thenReturn(putReq);
    stubCallbackSendJson(putReq, putResp);

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);

    service.addDataset(dataset, capturing(latch, out));
    assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    assertTrue(out.value.succeeded());
  }

  @Test
  void addDataset_notExists_post201_succeeds() throws Exception {
    String id = "abc";
    JsonObject dataset = new JsonObject().put("id", id);

    // entityExists -> GET /datasets/abc returns 404
    HttpRequest<Buffer> getReq = mock(HttpRequest.class);
    HttpResponse<Buffer> getResp = mock(HttpResponse.class);
    when(getResp.statusCode()).thenReturn(404);
    when(web.get(anyInt(), anyString(), eq("/datasets/" + id))).thenReturn(getReq);
    when(getReq.putHeader(anyString(), anyString())).thenReturn(getReq);
    when(getReq.timeout(anyLong())).thenReturn(getReq);
    stubCallbackSend(getReq, getResp);

    // POST /datasets 201
    HttpRequest<Buffer> postReq = mock(HttpRequest.class);
    HttpResponse<Buffer> postResp = mock(HttpResponse.class);
    when(postResp.statusCode()).thenReturn(201);
    when(web.post(anyInt(), anyString(), eq("/datasets"))).thenReturn(postReq);
    when(postReq.putHeader(anyString(), anyString())).thenReturn(postReq);
    when(postReq.timeout(anyLong())).thenReturn(postReq);
    stubCallbackSendJson(postReq, postResp);

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);

    service.addDataset(dataset, capturing(latch, out));
    assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    assertTrue(out.value.succeeded());
  }

  @Test
  void addDataset_entityExistsFails_propagatesFailure() throws Exception {
    String id = "abc";
    JsonObject dataset = new JsonObject().put("id", id);

    HttpRequest<Buffer> getReq = mock(HttpRequest.class);
    when(web.get(anyInt(), anyString(), eq("/datasets/" + id))).thenReturn(getReq);
    when(getReq.putHeader(anyString(), anyString())).thenReturn(getReq);
    when(getReq.timeout(anyLong())).thenReturn(getReq);
    stubCallbackSendFailure(getReq, new RuntimeException("boom"));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);

    service.addDataset(dataset, capturing(latch, out));
    assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    assertTrue(out.value.failed());
    assertEquals("boom", out.value.cause().getMessage());
  }

  // ------------------------- addDatasetPut -------------------------

  @Test
  void addDatasetPut_emptyDataset_fails() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.addDatasetPut(new JsonObject(), capturing(latch, out));
    assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
    assertTrue(out.value.failed());
    assertEquals("Empty index object", out.value.cause().getMessage());
  }

  @Test
  void addDatasetPut_200_returnsDataset() {
    String id = "abc";
    JsonObject dataset = new JsonObject().put("id", id);

    HttpRequest<Buffer> putReq = mock(HttpRequest.class);
    HttpResponse<Buffer> putResp = mock(HttpResponse.class);
    when(putResp.statusCode()).thenReturn(200);

    when(web.put(anyInt(), anyString(), eq("/datasets/" + id))).thenReturn(putReq);
    when(putReq.putHeader(anyString(), anyString())).thenReturn(putReq);
    when(putReq.timeout(anyLong())).thenReturn(putReq);
    when(putReq.addQueryParam("synchronous", "true")).thenReturn(putReq);
    when(putReq.sendJsonObject(any(JsonObject.class))).thenReturn(Future.succeededFuture(putResp));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.addDatasetPut(dataset, capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
    assertEquals(dataset, out.value.result());
  }

  @Test
  void addDatasetPut_400_returnsServiceException() {
    String id = "abc";
    JsonObject dataset = new JsonObject().put("id", id);

    HttpRequest<Buffer> putReq = mock(HttpRequest.class);
    HttpResponse<Buffer> putResp = mock(HttpResponse.class);
    when(putResp.statusCode()).thenReturn(400);
    when(putResp.bodyAsString()).thenReturn("bad");

    when(web.put(anyInt(), anyString(), eq("/datasets/" + id))).thenReturn(putReq);
    when(putReq.putHeader(anyString(), anyString())).thenReturn(putReq);
    when(putReq.timeout(anyLong())).thenReturn(putReq);
    when(putReq.addQueryParam("synchronous", "true")).thenReturn(putReq);
    when(putReq.sendJsonObject(any(JsonObject.class))).thenReturn(Future.succeededFuture(putResp));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.addDatasetPut(dataset, capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.failed());
    // status/message handled by ServiceException; we just assert failure here
  }

  // ------------------------- modifyDataset -------------------------

  @Test
  void modifyDataset_emptyPayload_fails() {
    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.modifyDataset("id", new JsonObject(), capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.failed());
    assertEquals("Empty index object", out.value.cause().getMessage());
  }

  @Test
  void modifyDataset_emptyId_fails() {
    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.modifyDataset(" ", new JsonObject().put("a", 1), capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.failed());
    assertEquals("Dataset id is empty", out.value.cause().getMessage());
  }

  @Test
  void modifyDataset_success_returnsEmptyJson() {
    HttpRequest<Buffer> patchReq = mock(HttpRequest.class);
    when(web.patch(anyInt(), anyString(), eq("/datasets/id"))).thenReturn(patchReq);
    when(patchReq.putHeader(anyString(), anyString())).thenReturn(patchReq);
    when(patchReq.timeout(anyLong())).thenReturn(patchReq);
    when(patchReq.expect(any())).thenReturn(patchReq);
    when(patchReq.sendJsonObject(any(JsonObject.class))).thenReturn(Future.succeededFuture(resp));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.modifyDataset("id", new JsonObject().put("x", 1), capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
    assertEquals(new JsonObject(), out.value.result());
  }

  // ------------------------- putVocabulary -------------------------

  @Test
  void putVocabulary_201_returnsArray() {
    HttpRequest<Buffer> putReq = mock(HttpRequest.class);
    HttpResponse<Buffer> putResp = mock(HttpResponse.class);
    when(putResp.statusCode()).thenReturn(201);
    when(putResp.bodyAsJsonArray()).thenReturn(new JsonArray().add(1));

    when(web.put(anyInt(), anyString(), eq("/vocabularies/v1"))).thenReturn(putReq);
    when(putReq.putHeader(anyString(), anyString())).thenReturn(putReq);
    when(putReq.timeout(anyLong())).thenReturn(putReq);
    when(putReq.sendJsonObject(any(JsonObject.class))).thenReturn(Future.succeededFuture(putResp));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonArray>> out = new Holder<>(null);
    service.putVocabulary("v1", new JsonObject().put("a", 1), capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
    assertEquals(1, out.value.result().size());
  }

  @Test
  void putVocabulary_400_failsWithServiceException() {
    HttpRequest<Buffer> putReq = mock(HttpRequest.class);
    HttpResponse<Buffer> putResp = mock(HttpResponse.class);
    when(putResp.statusCode()).thenReturn(400);
    when(putResp.statusMessage()).thenReturn("Bad");
    when(putResp.bodyAsJsonObject()).thenReturn(new JsonObject().put("err", "x"));

    when(web.put(anyInt(), anyString(), eq("/vocabularies/v1"))).thenReturn(putReq);
    when(putReq.putHeader(anyString(), anyString())).thenReturn(putReq);
    when(putReq.timeout(anyLong())).thenReturn(putReq);
    when(putReq.sendJsonObject(any(JsonObject.class))).thenReturn(Future.succeededFuture(putResp));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonArray>> out = new Holder<>(null);
    service.putVocabulary("v1", new JsonObject().put("a", 1), capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.failed());
  }

  // ------------------------- deleteVocabulary -------------------------

  @Test
  void deleteVocabulary_success_returnsEmpty() {
    HttpRequest<Buffer> delReq = mock(HttpRequest.class);
    when(web.delete(anyInt(), anyString(), eq("/vocabularies/v1"))).thenReturn(delReq);
    when(delReq.putHeader(anyString(), anyString())).thenReturn(delReq);
    when(delReq.timeout(anyLong())).thenReturn(delReq);
    when(delReq.expect(any())).thenReturn(delReq);
    when(delReq.send()).thenReturn(Future.succeededFuture(resp));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.deleteVocabulary("v1", capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
    assertEquals(new JsonObject(), out.value.result());
  }

  // ------------------------- deleteDataset -------------------------

  @Test
  void deleteDataset_success_returnsEmpty() {
    HttpRequest<Buffer> delReq = mock(HttpRequest.class);
    when(web.delete(anyInt(), anyString(), eq("/datasets/abc"))).thenReturn(delReq);
    when(delReq.putHeader(anyString(), anyString())).thenReturn(delReq);
    when(delReq.timeout(anyLong())).thenReturn(delReq);
    when(delReq.expect(any())).thenReturn(delReq);
    when(delReq.send()).thenReturn(Future.succeededFuture(resp));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.deleteDataset("abc", capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
    assertEquals(new JsonObject(), out.value.result());
  }

  // ------------------------- listDatasets -------------------------

  @Test
  void listDatasets_success_returnsArray() {
    HttpRequest<Buffer> getReq = mock(HttpRequest.class);
    HttpResponse<Buffer> getResp = mock(HttpResponse.class);
    when(getResp.bodyAsJsonArray()).thenReturn(new JsonArray().add("x"));
    when(web.get(anyInt(), anyString(), eq("/datasets"))).thenReturn(getReq);
    when(getReq.setQueryParam("catalogue", "cat")).thenReturn(getReq);
    when(getReq.setQueryParam("alias", "al")).thenReturn(getReq);
    when(getReq.timeout(anyLong())).thenReturn(getReq);
    when(getReq.expect(any())).thenReturn(getReq);
    stubCallbackSend(getReq, getResp);

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonArray>> out = new Holder<>(null);
    service.listDatasets("cat", "al", capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
    assertEquals(1, out.value.result().size());
  }

  // ------------------------- deleteCatalog -------------------------

  @Test
  void deleteCatalog_success_returnsEmpty() {
    HttpRequest<Buffer> delReq = mock(HttpRequest.class);
    when(web.delete(anyInt(), anyString(), eq("/catalogues/c1"))).thenReturn(delReq);
    when(delReq.putHeader(anyString(), anyString())).thenReturn(delReq);
    when(delReq.timeout(anyLong())).thenReturn(delReq);
    when(delReq.expect(any())).thenReturn(delReq);

    stubCallbackSend(delReq, resp); // callback style in impl

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.deleteCatalog("c1", capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
  }

  // ------------------------- addCatalog -------------------------

  @Test
  void addCatalog_success_returnsEmpty() {
    HttpRequest<Buffer> putReq = mock(HttpRequest.class);
    when(web.put(anyInt(), anyString(), eq("/catalogues/c1"))).thenReturn(putReq);
    when(putReq.putHeader(anyString(), anyString())).thenReturn(putReq);
    when(putReq.timeout(anyLong())).thenReturn(putReq);
    when(putReq.expect(any())).thenReturn(putReq);

    // callback style sendJsonObject
    doAnswer(inv -> {
      Handler<AsyncResult<HttpResponse<Buffer>>> h = inv.getArgument(1);
      h.handle(Future.succeededFuture(resp));
      return null;
    }).when(putReq).sendJsonObject(any(JsonObject.class), any());

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.addCatalog(new JsonObject().put("id", "c1"), capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
  }

  // ------------------------- getDataset -------------------------

  @Test
  void getDataset_200_returnsNestedResult() {
    HttpRequest<Buffer> getReq = mock(HttpRequest.class);
    HttpResponse<Buffer> getResp = mock(HttpResponse.class);
    JsonObject body = new JsonObject().put("result", new JsonObject().put("k", "v"));
    when(getResp.statusCode()).thenReturn(200);
    when(getResp.bodyAsJsonObject()).thenReturn(body);

    when(web.get(anyInt(), anyString(), eq("/datasets/abc"))).thenReturn(getReq);
    when(getReq.putHeader(anyString(), anyString())).thenReturn(getReq);
    when(getReq.timeout(anyLong())).thenReturn(getReq);
    when(getReq.expect(any())).thenReturn(getReq);
    when(getReq.send()).thenReturn(Future.succeededFuture(getResp));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.getDataset("abc", capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
    assertEquals(new JsonObject().put("k", "v"), out.value.result());
  }

  // ------------------------- putDatasetBulk -------------------------

  @Test
  void putDatasetBulk_empty_fails() {
    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonArray>> out = new Holder<>(null);
    service.putDatasetBulk(new JsonArray(), capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.failed());
    assertEquals("Empty index object", out.value.cause().getMessage());
  }

  @Test
  void putDatasetBulk_201_returnsArray() {
    HttpRequest<Buffer> putReq = mock(HttpRequest.class);
    HttpResponse<Buffer> putResp = mock(HttpResponse.class);
    when(putResp.statusCode()).thenReturn(201);
    when(putResp.bodyAsJsonArray()).thenReturn(new JsonArray().add(1));

    when(web.put(anyInt(), anyString(), eq("/bulk/datasets"))).thenReturn(putReq);
    when(putReq.putHeader(anyString(), anyString())).thenReturn(putReq);
    when(putReq.timeout(anyLong())).thenReturn(putReq);
    when(putReq.addQueryParam("synchronous", "true")).thenReturn(putReq);
    when(putReq.sendJsonObject(any(JsonObject.class))).thenReturn(Future.succeededFuture(putResp));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonArray>> out = new Holder<>(null);
    service.putDatasetBulk(new JsonArray().add(new JsonObject().put("id", "a")),
        capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
    assertEquals(1, out.value.result().size());
  }

  // ------------------------- putResource / deleteResource -------------------------

  @Test
  void putResource_201_succeeds() {
    HttpRequest<Buffer> putReq = mock(HttpRequest.class);
    HttpResponse<Buffer> putResp = mock(HttpResponse.class);
    when(putResp.statusCode()).thenReturn(201);

    when(web.put(anyInt(), anyString(), eq("/resources/type/id"))).thenReturn(putReq);
    when(putReq.putHeader(anyString(), anyString())).thenReturn(putReq);
    when(putReq.timeout(anyLong())).thenReturn(putReq);
    when(putReq.sendJsonObject(any(JsonObject.class))).thenReturn(Future.succeededFuture(putResp));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<Void>> out = new Holder<>(null);
    service.putResource("id", "type", new JsonObject().put("a", 1), capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
  }

  @Test
  void deleteResource_204_succeeds() {
    HttpRequest<Buffer> delReq = mock(HttpRequest.class);
    HttpResponse<Buffer> delResp = mock(HttpResponse.class);
    when(delResp.statusCode()).thenReturn(204);

    when(web.delete(anyInt(), anyString(), eq("/resources/type/id"))).thenReturn(delReq);
    when(delReq.putHeader(anyString(), anyString())).thenReturn(delReq);
    when(delReq.timeout(anyLong())).thenReturn(delReq);
    when(delReq.send()).thenReturn(Future.succeededFuture(delResp));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<Void>> out = new Holder<>(null);
    service.deleteResource("id", "type", capturing(latch, out));
    awaitOk(latch);
    assertTrue(out.value.succeeded());
  }

  // ------------------------- addDatasetWithoutCB -------------------------

  @Test
  void addDatasetWithoutCB_oneExisting_sendsPutOnly() throws Exception {
    // entityExists -> GET /datasets/ex1 returns 200
    HttpRequest<Buffer> getReq = mock(HttpRequest.class);
    HttpResponse<Buffer> getResp = mock(HttpResponse.class);
    when(getResp.statusCode()).thenReturn(200);
    when(web.get(anyInt(), anyString(), eq("/datasets/ex1"))).thenReturn(getReq);
    when(getReq.putHeader(anyString(), anyString())).thenReturn(getReq);
    when(getReq.timeout(anyLong())).thenReturn(getReq);
    stubCallbackSend(getReq, getResp);

    // PUT /datasets (bulk)
    HttpRequest<Buffer> putReq = mock(HttpRequest.class);
    HttpResponse<Buffer> putResp = mock(HttpResponse.class);
    when(web.put(anyInt(), anyString(), eq("/datasets"))).thenReturn(putReq);
    when(putReq.putHeader(anyString(), anyString())).thenReturn(putReq);
    when(putReq.timeout(anyLong())).thenReturn(putReq);
    stubCallbackSendJson(putReq, putResp);

    JsonObject ds = new JsonObject().put("id", "ex1");
    JsonObject payload = new JsonObject().put("datasets", new JsonArray().add(ds));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.addDatasetWithoutCB(payload, capturing(latch, out));
    assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    assertTrue(out.value.succeeded());
    // Verify PUT was used, POST not used
    verify(web, never()).post(anyInt(), anyString(), eq("/datasets"));
  }

  @Test
  void addDatasetWithoutCB_notExisting_sendsPostOnly() throws Exception {
    // entityExists -> GET /datasets/ex2 returns 404
    HttpRequest<Buffer> getReq = mock(HttpRequest.class);
    HttpResponse<Buffer> getResp = mock(HttpResponse.class);
    when(getResp.statusCode()).thenReturn(404);
    when(web.get(anyInt(), anyString(), eq("/datasets/ex2"))).thenReturn(getReq);
    when(getReq.putHeader(anyString(), anyString())).thenReturn(getReq);
    when(getReq.timeout(anyLong())).thenReturn(getReq);
    stubCallbackSend(getReq, getResp);

    // POST /datasets (bulk)
    HttpRequest<Buffer> postReq = mock(HttpRequest.class);
    HttpResponse<Buffer> postResp = mock(HttpResponse.class);
    when(web.post(anyInt(), anyString(), eq("/datasets"))).thenReturn(postReq);
    when(postReq.putHeader(anyString(), anyString())).thenReturn(postReq);
    when(postReq.timeout(anyLong())).thenReturn(postReq);
    stubCallbackSendJson(postReq, postResp);

    JsonObject ds = new JsonObject().put("id", "ex2");
    JsonObject payload = new JsonObject().put("datasets", new JsonArray().add(ds));

    CountDownLatch latch = new CountDownLatch(1);
    Holder<AsyncResult<JsonObject>> out = new Holder<>(null);
    service.addDatasetWithoutCB(payload, capturing(latch, out));
    assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    assertTrue(out.value.succeeded());
    // Verify POST was used, PUT not used
    verify(web, never()).put(anyInt(), anyString(), eq("/datasets"));
  }

  // ------------------------- Helpers -------------------------
  private static <T> Handler<AsyncResult<T>> capturing(CountDownLatch latch,
      Holder<AsyncResult<T>> out) {
    return ar -> {
      out.value = ar;
      latch.countDown();
    };
  }

  private static class Holder<T> {

    T value;

    Holder(T value) {
      this.value = value;
    }
  }

  private void stubCallbackSendJson(HttpRequest<Buffer> r, HttpResponse<Buffer> response) {
    var ans = doAnswer(inv -> {
      Handler<AsyncResult<HttpResponse<Buffer>>> h = inv.getArgument(1);
      h.handle(Future.succeededFuture(response));
      return null;
    });
    ans.when(r).sendJsonObject(any(JsonObject.class), any());
  }

  private void stubCallbackSend(HttpRequest<Buffer> r, HttpResponse<Buffer> response) {
    doAnswer(inv -> {
      Handler<AsyncResult<HttpResponse<Buffer>>> h = inv.getArgument(0);
      h.handle(Future.succeededFuture(response));
      return null;
    }).when(r).send(any());
  }

  private void stubCallbackSendFailure(HttpRequest<Buffer> r, Throwable t) {
    doAnswer(inv -> {
      Handler<AsyncResult<HttpResponse<Buffer>>> h = inv.getArgument(0);
      h.handle(Future.failedFuture(t));
      return null;
    }).when(r).send(any());
  }

  private static void awaitOk(CountDownLatch latch) {
    try {
      assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    } catch (InterruptedException e) {
      fail(e);
    }
  }
}
