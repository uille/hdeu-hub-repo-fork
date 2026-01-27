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
package io.piveau.hub.services.drafts;

import static io.piveau.hub.Constants.ENV_PIVEAU_HUB_XML_DECLARATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.dcatap.DCATAPUriRef;
import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.dcatap.TripleStore;
import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.rdf.Piveau;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

class DatasetDraftsServiceImplTest {

  @Mock
  private TripleStore triple;
  private AutoCloseable mocks;
  private DatasetDraftsService svc;

  private static final JsonObject CFG = new JsonObject().put(ENV_PIVEAU_HUB_XML_DECLARATION, true);

  @BeforeEach
  void setup() {
    mocks = MockitoAnnotations.openMocks(this);
    // Vertx is unused by the impl, pass null
    svc = DatasetDraftsService.create(triple, CFG, null, ar -> {
    });
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  // ------------------------------------------------------------
  // listDatasetDrafts
  // ------------------------------------------------------------
  @Test
  void listDatasetDrafts_withProvider_mergesTitlesAndDescriptions() throws Exception {
    ResultSet rs = mock(ResultSet.class);
    QuerySolution q1 = qs("ds1", "http://example/cat/A",
        lit("Title EN", "en"), null);
    QuerySolution q2 = qs("ds1", "http://example/cat/A",
        lit("Titre FR", "fr"), lit("Desc FR", "fr"));

    when(rs.hasNext()).thenReturn(true, true, false);
    when(rs.next()).thenReturn(q1, q2);
    when(triple.select(anyString())).thenReturn(Future.succeededFuture(rs));

    CountDownLatch latch = new CountDownLatch(1);
    final AsyncResult<JsonArray>[] out = new AsyncResult[1];
    svc.listDatasetDrafts(null, "provider-1", ar -> {
      out[0] = ar;
      latch.countDown();
    });

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertTrue(out[0].succeeded());
    JsonArray arr = out[0].result();
    assertEquals(1, arr.size());
    JsonObject item = arr.getJsonObject(0);
    assertEquals("ds1", item.getString("id"));
    assertEquals("A", item.getString("catalog"));
    assertEquals("Title EN", item.getJsonObject("title").getString("en"));
    assertEquals("Titre FR", item.getJsonObject("title").getString("fr"));
    assertEquals("Desc FR", item.getJsonObject("description").getString("fr"));
  }

  @Test
  void listDatasetDrafts_withCatalogueFilter_buildsResult() throws Exception {
    ResultSet rs = mock(ResultSet.class);
    QuerySolution q1 = qs("ds2", "http://example/cat/B", null, null);
    when(rs.hasNext()).thenReturn(true, false);
    when(rs.next()).thenReturn(q1);
    when(triple.select(anyString())).thenReturn(Future.succeededFuture(rs));

    CountDownLatch latch = new CountDownLatch(1);
    final AsyncResult<JsonArray>[] out = new AsyncResult[1];
    svc.listDatasetDrafts(List.of("B"), null, ar -> {
      out[0] = ar;
      latch.countDown();
    });

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertTrue(out[0].succeeded());
    assertEquals(1, out[0].result().size());
    assertEquals("ds2", out[0].result().getJsonObject(0).getString("id"));
    assertEquals("B", out[0].result().getJsonObject(0).getString("catalog"));
  }

  @Test
  void listDatasetDrafts_selectFails_nonServiceException_maps500() throws Exception {
    when(triple.select(anyString())).thenReturn(Future.failedFuture(new RuntimeException("boom")));

    CountDownLatch latch = new CountDownLatch(1);
    final AsyncResult<JsonArray>[] out = new AsyncResult[1];
    svc.listDatasetDrafts(null, null, ar -> {
      out[0] = ar;
      latch.countDown();
    });

    assertTrue(latch.await(1, TimeUnit.SECONDS));
    assertTrue(out[0].failed());
    assertInstanceOf(ServiceException.class, out[0].cause());
    assertEquals(500, ((ServiceException) out[0].cause()).failureCode());
  }

  // ------------------------------------------------------------
  // readDatasetDraft
  // ------------------------------------------------------------
  @Test
  void readDatasetDraft_success_returnsPresentedContent() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class);
        MockedStatic<Piveau> pRes = mockStatic(Piveau.class);
        MockedStatic<io.piveau.dcatap.Prefixes> ignored = mockStatic(
            io.piveau.dcatap.Prefixes.class)) {

      String catalogueId = "CAT";
      DCATAPUriRef catRef = ref(null, "http://example/catalogues/CAT");
      schema.when(() -> DCATAPUriSchema.createFor(eq(catalogueId))).thenReturn(catRef);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(true, false);
      QuerySolution q = mock(QuerySolution.class);
      Resource dsRes = mock(Resource.class);
      when(dsRes.getURI()).thenReturn("http://graph/ds1");
      when(q.getResource("dataset")).thenReturn(dsRes);
      when(rs.next()).thenReturn(q);
      when(triple.select(anyString())).thenReturn(Future.succeededFuture(rs));

      Model model = mock(Model.class);
      when(model.isEmpty()).thenReturn(false);
      when(triple.construct(contains("http://graph/ds1"))).thenReturn(
          Future.succeededFuture(model));

      pRes.when(() -> Piveau.presentAs(eq(model), eq("application/rdf+xml"))).thenReturn("<rdf/>");
      // prefixes call is void; default is fine

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] out = new AsyncResult[1];
      svc.readDatasetDraft("ds1", "CAT", "application/rdf+xml", ar -> {
        out[0] = ar;
        latch.countDown();
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(out[0].succeeded());
      assertEquals("<rdf/>", out[0].result());
    }
  }

  @Test
  void readDatasetDraft_constructEmpty_404() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "http://cat");
      schema.when(() -> DCATAPUriSchema.createFor(anyString())).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(true, false);
      QuerySolution q = mock(QuerySolution.class);
      Resource dsRes = mock(Resource.class);
      when(dsRes.getURI()).thenReturn("uri");
      when(q.getResource("dataset")).thenReturn(dsRes);
      when(rs.next()).thenReturn(q);
      when(triple.select(anyString())).thenReturn(Future.succeededFuture(rs));

      Model m = mock(Model.class);
      when(m.isEmpty()).thenReturn(true);
      when(triple.construct(anyString())).thenReturn(Future.succeededFuture(m));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] out = new AsyncResult[1];
      svc.readDatasetDraft("id", "CAT", "t", ar -> {
        out[0] = ar;
        latch.countDown();
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(out[0].failed());
      assertInstanceOf(ServiceException.class, out[0].cause());
      assertEquals(404, ((ServiceException) out[0].cause()).failureCode());
    }
  }

  @Test
  void readDatasetDraft_constructFails_notFound() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "http://cat");
      schema.when(() -> DCATAPUriSchema.createFor(anyString())).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(true, false);
      QuerySolution q = mock(QuerySolution.class);
      Resource dsRes = mock(Resource.class);
      when(dsRes.getURI()).thenReturn("uri");
      when(q.getResource("dataset")).thenReturn(dsRes);
      when(rs.next()).thenReturn(q);
      when(triple.select(anyString())).thenReturn(Future.succeededFuture(rs));

      // First: Not found -> 404
      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] results = new AsyncResult[1];
      when(triple.construct(anyString()))
          .thenReturn(Future.failedFuture(new RuntimeException("Not found: graph")));

      svc.readDatasetDraft("id", "CAT", "t", ar -> {
        results[0] = ar;
        latch.countDown();
      });
      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(results[0].failed());
      assertEquals(404, ((ServiceException) results[0].cause()).failureCode());
    }
  }

  @Test
  void readDatasetDraft_constructFails_500() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "http://cat");
      schema.when(() -> DCATAPUriSchema.createFor(anyString())).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(true, false);
      QuerySolution q = mock(QuerySolution.class);
      Resource dsRes = mock(Resource.class);
      when(dsRes.getURI()).thenReturn("uri");
      when(q.getResource("dataset")).thenReturn(dsRes);
      when(rs.next()).thenReturn(q);
      when(triple.select(anyString())).thenReturn(Future.succeededFuture(rs));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] results = new AsyncResult[1];
      when(triple.construct(anyString()))
          .thenReturn(Future.failedFuture(new RuntimeException("oops")));
      svc.readDatasetDraft("id", "CAT", "t", ar -> {
        results[0] = ar;
        latch.countDown();
      });
      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(results[0].failed());
      assertEquals(500, ((ServiceException) results[0].cause()).failureCode());
    }
  }

  @Test
  void readDatasetDraft_selectNoRows_404() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "http://cat");
      schema.when(() -> DCATAPUriSchema.createFor(anyString())).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(false);
      when(triple.select(anyString())).thenReturn(Future.succeededFuture(rs));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] out = new AsyncResult[1];
      svc.readDatasetDraft("id", "CAT", "t", ar -> {
        out[0] = ar;
        latch.countDown();
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(out[0].failed());
      assertEquals(404, ((ServiceException) out[0].cause()).failureCode());
    }
  }

  @Test
  void readDatasetDraft_selectFails_ServiceException() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "http://cat");
      schema.when(() -> DCATAPUriSchema.createFor(anyString())).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(true, false);
      QuerySolution q = mock(QuerySolution.class);
      Resource dsRes = mock(Resource.class);
      when(dsRes.getURI()).thenReturn("uri");
      when(q.getResource("dataset")).thenReturn(dsRes);
      when(rs.next()).thenReturn(q);
      when(triple.select(anyString()))
          .thenReturn(Future.failedFuture(new ServiceException(404, "Not found")));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] out = new AsyncResult[1];
      svc.readDatasetDraft("id", "CAT", "t", ar -> {
        out[0] = ar;
        latch.countDown();
      });
      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(out[0].failed());
      assertInstanceOf(ServiceException.class, out[0].cause());
    }
  }

  @Test
  void readDatasetDraft_selectFails_OtherException() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "http://cat");
      schema.when(() -> DCATAPUriSchema.createFor(anyString())).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(true, false);
      QuerySolution q = mock(QuerySolution.class);
      Resource dsRes = mock(Resource.class);
      when(dsRes.getURI()).thenReturn("uri");
      when(q.getResource("dataset")).thenReturn(dsRes);
      when(rs.next()).thenReturn(q);
      when(triple.select(anyString()))
          .thenReturn(Future.failedFuture(new RuntimeException("oops")));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] out = new AsyncResult[1];
      svc.readDatasetDraft("id", "CAT", "t", ar -> {
        out[0] = ar;
        latch.countDown();
      });
      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(out[0].failed());
      assertInstanceOf(ServiceException.class, out[0].cause());
      assertEquals(500, ((ServiceException) out[0].cause()).failureCode());
    }
  }

  @Test
  void readDatasetDraft_throws() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      schema.when(() -> DCATAPUriSchema.createFor(any()))
          .thenThrow(new IllegalArgumentException("bad"));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] out = new AsyncResult[1];
      svc.readDatasetDraft("id", "CAT", "t", ar -> {
        out[0] = ar;
        latch.countDown();
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(out[0].failed());
      assertInstanceOf(ServiceException.class, out[0].cause());
      assertEquals(500, ((ServiceException) out[0].cause()).failureCode());
    }
  }

  // ------------------------------------------------------------
  // createOrUpdateDatasetDraft
  // ------------------------------------------------------------
  @Test
  void createOrUpdateDatasetDraft_success_withRecursionAndProvider() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class);
        MockedStatic<DatasetHelper> dh = mockStatic(DatasetHelper.class)) {

      // DatasetHelper
      DatasetHelper helper = mock(DatasetHelper.class);
      dh.when(() -> DatasetHelper.create(eq("ds"), eq("payload"), eq("ct"), eq("CAT")))
          .thenReturn(helper);

      when(helper.piveauId()).thenReturn("pid");
      when(helper.recordUriRef()).thenReturn("http://rec");
      when(helper.graphName()).thenReturn("graph");
      Model model = mock(Model.class);
      when(helper.model()).thenReturn(model);

      // Model ops
      Resource recRes = mock(Resource.class);
      Resource catRes = mock(Resource.class);
      Statement st1 = mock(Statement.class);
      Statement st2 = mock(Statement.class);
      when(model.getResource("http://rec")).thenReturn(recRes);
      when(model.createResource("http://example/catalogues/CAT")).thenReturn(catRes);
      when(model.createStatement(any(), any(), any(Resource.class))).thenReturn(st1);
      when(model.createStatement(any(), any(), anyString())).thenReturn(st2); // provider
      when(model.add(any(Statement.class))).thenReturn(model);

      var createRef = ref(null, "http://example/catalogues/CAT");
      var applyRef = ref("http://example/dataset/pid", null);

      // Schema
      schema.when(() -> DCATAPUriSchema.createForCatalogue("CAT")).thenReturn(createRef);
      schema.when(() -> DCATAPUriSchema.applyFor(anyString())).thenAnswer(inv -> applyRef);

      // Ask recursion sequence: exist1(true), exist2(false) -> recurse; exist1(false) -> return pid~~1
      when(triple.ask(anyString()))
          .thenReturn(Future.succeededFuture(true))   // q1(pid)
          .thenReturn(Future.succeededFuture(false))  // q2(pid)
          .thenReturn(Future.succeededFuture(false)); // q1(pid~~1)

      // setGraph success
      when(triple.setGraph("graph", model, true)).thenReturn(Future.succeededFuture("OK"));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] out = new AsyncResult[1];
      svc.createOrUpdateDatasetDraft("ds", "CAT", "payload", "ct", "prov-123", ar -> {
        out[0] = ar;
        latch.countDown();
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(out[0].succeeded());
      assertEquals("OK", out[0].result());
      // Ensure helper was initialized with the resolved id (pid~~1)
      verify(helper).init("pid~~1");
      // Added both catalog and provider statements
      verify(model, atLeast(2)).add(any(Statement.class));
    }
  }

  @Test
  void createOrUpdateDatasetDraft_setGraphFailure_maps500() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class);
        MockedStatic<DatasetHelper> dh = mockStatic(DatasetHelper.class)) {

      DatasetHelper helper = mock(DatasetHelper.class);
      dh.when(() -> DatasetHelper.create(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(helper);
      when(helper.piveauId()).thenReturn("pid");
      when(helper.recordUriRef()).thenReturn("rec");
      when(helper.graphName()).thenReturn("g");
      Model model = mock(Model.class);
      when(helper.model()).thenReturn(model);

      var createRef = ref(null, "http://cat");
      var applyRef = ref("http://ds", "http://cat");

      schema.when(() -> DCATAPUriSchema.createForCatalogue(anyString())).thenReturn(createRef);
      schema.when(() -> DCATAPUriSchema.applyFor(anyString())).thenReturn(applyRef);

      when(triple.ask(anyString())).thenReturn(Future.succeededFuture(true),
          Future.succeededFuture(true));
      when(triple.setGraph(anyString(), any(Model.class), eq(true)))
          .thenReturn(Future.failedFuture(new RuntimeException("bad")));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] out = new AsyncResult[1];
      svc.createOrUpdateDatasetDraft("ds", "CAT", "p", "ct", "prov", ar -> {
        out[0] = ar;
        latch.countDown();
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(out[0].failed());
      assertInstanceOf(ServiceException.class, out[0].cause());
      assertEquals(500, ((ServiceException) out[0].cause()).failureCode());
    }
  }

  @Test
  void createOrUpdateDatasetDraft_findPiveauId_askFailure_maps500() throws Exception {
    try (MockedStatic<DatasetHelper> dh = mockStatic(DatasetHelper.class)) {
      DatasetHelper helper = mock(DatasetHelper.class);
      dh.when(() -> DatasetHelper.create(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(helper);
      when(helper.piveauId()).thenReturn("pid");

      when(triple.ask(anyString())).thenReturn(Future.failedFuture(new RuntimeException("oops")));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] out = new AsyncResult[1];
      svc.createOrUpdateDatasetDraft("ds", "CAT", "p", "ct", "", ar -> {
        out[0] = ar;
        latch.countDown();
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(out[0].failed());
      assertInstanceOf(ServiceException.class, out[0].cause());
      assertEquals(500, ((ServiceException) out[0].cause()).failureCode());
    }
  }

  // ------------------------------------------------------------
  // deleteDatasetDraft
  // ------------------------------------------------------------
  @Test
  void deleteDatasetDraft_success() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "http://example/catalogues/CAT");
      schema.when(() -> DCATAPUriSchema.applyFor(eq("CAT"))).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(true, false);
      QuerySolution q = mock(QuerySolution.class);
      Resource dsRes = mock(Resource.class);
      when(dsRes.getURI()).thenReturn("http://graph/ds");
      when(q.getResource("dataset")).thenReturn(dsRes);
      when(rs.next()).thenReturn(q);
      when(triple.select(anyString())).thenReturn(Future.succeededFuture(rs));

      when(triple.deleteGraph("http://graph/ds")).thenReturn(Future.succeededFuture());

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<Void>[] out = new AsyncResult[1];
      svc.deleteDatasetDraft("ds", "CAT", ar -> {
        out[0] = ar;
        latch.countDown();
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(out[0].succeeded());
    }
  }

  @Test
  void deleteDatasetDraft_deleteGraphFailure_notFound() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "cat");
      schema.when(() -> DCATAPUriSchema.applyFor(anyString())).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(true, false);
      QuerySolution q = mock(QuerySolution.class);
      Resource dsRes = mock(Resource.class);
      when(dsRes.getURI()).thenReturn("uri");
      when(q.getResource("dataset")).thenReturn(dsRes);
      when(rs.next()).thenReturn(q);
      when(triple.select(anyString())).thenReturn(Future.succeededFuture(rs));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<Void>[] results = new AsyncResult[1];
      when(triple.deleteGraph(anyString())).thenReturn(
          Future.failedFuture(new RuntimeException("Not found: x")));
      svc.deleteDatasetDraft("d", "C", ar -> {
        results[0] = ar;
        latch.countDown();
      });
      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(results[0].failed());
      assertEquals(404, ((ServiceException) results[0].cause()).failureCode());
    }
  }

  @Test
  void deleteDatasetDraft_selectFails_ServiceException() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "cat");
      schema.when(() -> DCATAPUriSchema.applyFor(anyString())).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(true, false);
      QuerySolution q = mock(QuerySolution.class);
      Resource dsRes = mock(Resource.class);
      when(dsRes.getURI()).thenReturn("uri");
      when(q.getResource("dataset")).thenReturn(dsRes);
      when(rs.next()).thenReturn(q);
      when(triple.select(anyString()))
          .thenReturn(Future.failedFuture(new ServiceException(404, "Not found")));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<Void>[] results = new AsyncResult[1];
      svc.deleteDatasetDraft("d", "C", ar -> {
        results[0] = ar;
        latch.countDown();
      });
      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(results[0].failed());
      assertInstanceOf(ServiceException.class, results[0].cause());
    }
  }

  @Test
  void deleteDatasetDraft_selectFails_OtherException() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "cat");
      schema.when(() -> DCATAPUriSchema.applyFor(anyString())).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(true, false);
      QuerySolution q = mock(QuerySolution.class);
      Resource dsRes = mock(Resource.class);
      when(dsRes.getURI()).thenReturn("uri");
      when(q.getResource("dataset")).thenReturn(dsRes);
      when(rs.next()).thenReturn(q);
      when(triple.select(anyString()))
          .thenReturn(Future.failedFuture(new RuntimeException("oops")));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<Void>[] results = new AsyncResult[1];
      svc.deleteDatasetDraft("d", "C", ar -> {
        results[0] = ar;
        latch.countDown();
      });
      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(results[0].failed());
      assertInstanceOf(ServiceException.class, results[0].cause());
      assertEquals(500, ((ServiceException) results[0].cause()).failureCode());
    }
  }

  @Test
  void deleteDatasetDraft_throws() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      schema.when(() -> DCATAPUriSchema.applyFor(any()))
          .thenThrow(new IllegalArgumentException("bad"));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<Void>[] results = new AsyncResult[1];
      svc.deleteDatasetDraft("d", "C", ar -> {
        results[0] = ar;
        latch.countDown();
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(results[0].failed());
      assertInstanceOf(ServiceException.class, results[0].cause());
      assertEquals(500, ((ServiceException) results[0].cause()).failureCode());
    }
  }

  @Test
  void deleteDatasetDraft_selectNoRows_404() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "cat");
      schema.when(() -> DCATAPUriSchema.applyFor(anyString())).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(false);
      when(triple.select(anyString())).thenReturn(Future.succeededFuture(rs));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<Void>[] results = new AsyncResult[1];
      svc.deleteDatasetDraft("d", "C", ar -> {
        results[0] = ar;
        latch.countDown();
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(results[0].failed());
      assertEquals(404, ((ServiceException) results[0].cause()).failureCode());
    }
  }

  @Test
  void deleteDatasetDraft_deleteGraphFailure_500() throws Exception {
    try (MockedStatic<DCATAPUriSchema> schema = mockStatic(DCATAPUriSchema.class)) {
      var ref = ref(null, "cat");
      schema.when(() -> DCATAPUriSchema.applyFor(anyString())).thenReturn(ref);

      ResultSet rs = mock(ResultSet.class);
      when(rs.hasNext()).thenReturn(true, false);
      QuerySolution q = mock(QuerySolution.class);
      Resource dsRes = mock(Resource.class);
      when(dsRes.getURI()).thenReturn("uri");
      when(q.getResource("dataset")).thenReturn(dsRes);
      when(rs.next()).thenReturn(q);
      when(triple.select(anyString())).thenReturn(Future.succeededFuture(rs));

      // other -> 500
      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<Void>[] results = new AsyncResult[1];
      when(triple.deleteGraph(anyString())).thenReturn(
          Future.failedFuture(new RuntimeException("bad")));
      svc.deleteDatasetDraft("d", "C", ar -> {
        results[0] = ar;
        latch.countDown();
      });
      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(results[0].failed());
      assertEquals(500, ((ServiceException) results[0].cause()).failureCode());
    }
  }

  // ------------------------------------------------------------
  // createDatasetDraft (delegates to createOrUpdate)
  // ------------------------------------------------------------
  @Test
  void createDatasetDraft_generatesId_andDelegates() throws Exception {
    try (MockedStatic<DatasetHelper> dh = mockStatic(DatasetHelper.class)) {
      // Make recursion trivial
      DatasetHelper helper = mock(DatasetHelper.class);
      dh.when(() -> DatasetHelper.create(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(helper);
      when(helper.piveauId()).thenReturn("pid");
      when(helper.recordUriRef()).thenReturn("rec");
      when(helper.graphName()).thenReturn("g");
      when(helper.model()).thenReturn(mock(Model.class));
      when(triple.ask(anyString())).thenReturn(Future.succeededFuture(false)); // straight through
      when(triple.setGraph(anyString(), any(Model.class), eq(true)))
          .thenReturn(Future.succeededFuture("OK"));

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<String>[] out = new AsyncResult[1];
      svc.createDatasetDraft("CAT", "payload", "ct", "prov", ar -> {
        out[0] = ar;
        latch.countDown();
      });

      assertTrue(latch.await(1, TimeUnit.SECONDS));
      assertTrue(out[0].succeeded());
      assertEquals("OK", out[0].result());
    }
  }

  // ------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------
  private static DCATAPUriRef ref(String datasetUriRef, String catalogueUriRef) {
    DCATAPUriRef r = mock(DCATAPUriRef.class);
    when(r.getDatasetUriRef()).thenReturn(datasetUriRef);
    when(r.getCatalogueUriRef()).thenReturn(catalogueUriRef);
    return r;
  }

  private static Literal lit(String text, String lang) {
    Literal l = mock(Literal.class);
    when(l.getLexicalForm()).thenReturn(text);
    when(l.getLanguage()).thenReturn(lang);
    return l;
  }

  private static QuerySolution qs(String id, String catUri, Literal title, Literal desc) {
    QuerySolution q = mock(QuerySolution.class);
    Literal idLit = lit(id, "");
    when(q.getLiteral("id")).thenReturn(idLit);

    Resource catRes = mock(Resource.class);
    when(catRes.getURI()).thenReturn(catUri);
    when(q.getResource("catalog")).thenReturn(catRes);

    when(q.getLiteral("title")).thenReturn(title);
    when(q.getLiteral("description")).thenReturn(desc);
    return q;
  }
}
