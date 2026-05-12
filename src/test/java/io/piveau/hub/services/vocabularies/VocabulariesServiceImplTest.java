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
package io.piveau.hub.services.vocabularies;

import static io.piveau.rdf.RDFMimeTypes.NTRIPLES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.dcatap.CatalogueManager;
import io.piveau.dcatap.DCATAPUriRef;
import io.piveau.dcatap.DatasetManager;
import io.piveau.dcatap.Prefixes;
import io.piveau.dcatap.TripleStore;
import io.piveau.hub.Constants;
import io.piveau.hub.indexing.IndexingVocabulariesKt;
import io.piveau.hub.services.catalogues.CataloguesService;
import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.hub.services.index.IndexService;
import io.piveau.hub.vocabularies.Vocabularies;
import io.piveau.rdf.Piveau;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.DCAT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@ExtendWith(VertxExtension.class)
class VocabulariesServiceImplTest {

  private AutoCloseable mocks;

  @Mock
  private CatalogueManager catalogueManager;

  @Mock
  private CataloguesService cataloguesService;

  @Mock
  private DatasetManager datasetManager;

  @Mock
  private DatasetsService datasetsService;

  @Mock
  private IndexService indexService;

  @Mock
  private TripleStore tripleStore;

  private VocabulariesService vocabulariesService;

  @BeforeEach
  void setUp(Vertx vertx) {
    mocks = MockitoAnnotations.openMocks(this);
    when(tripleStore.getCatalogueManager()).thenReturn(catalogueManager);
    when(tripleStore.getDatasetManager()).thenReturn(datasetManager);

    vocabulariesService = newService(vertx, true);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  private VocabulariesService newService(Vertx vertx, boolean indexingEnabled) {
    final VocabulariesService[] out = new VocabulariesService[1];
    JsonObject config = new JsonObject().put(
        Constants.ENV_PIVEAU_HUB_SEARCH_SERVICE,
        new JsonObject().put("enabled", indexingEnabled)
    );
    try (
        MockedStatic<IndexService> idxStatic = mockStatic(IndexService.class);
        MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class);
        MockedStatic<CataloguesService> csStatic = mockStatic(CataloguesService.class)
    ) {
      if (indexingEnabled) {
        idxStatic
            .when(() ->
                IndexService.createProxy(
                    eq(vertx),
                    eq(IndexService.SERVICE_ADDRESS),
                    eq(IndexService.DEFAULT_TIMEOUT)
                )
            )
            .thenReturn(indexService);
      }
      dsStatic
          .when(() -> DatasetsService.createProxy(eq(vertx), eq(DatasetsService.SERVICE_ADDRESS)))
          .thenReturn(datasetsService);
      csStatic
          .when(
              () -> CataloguesService.createProxy(eq(vertx), eq(CataloguesService.SERVICE_ADDRESS)))
          .thenReturn(cataloguesService);

      out[0] = VocabulariesService.create(vertx, tripleStore, config, ar -> {
      });
    }
    return out[0];
  }

  // ---------------- listVocabularies ----------------
  @Test
  void listVocabularies_originalIds_returnsOriginalIdsJson() {
    when(catalogueManager.allDatasetIdentifiers("vocabularies")).thenReturn(
        Future.succeededFuture(List.of("o1", "o2"))
    );

    String encoded = vocabulariesService
        .listVocabularies("application/json", "originalIds", 10, 0)
        .result();
    JsonArray arr = new JsonArray(encoded);

    assertEquals(2, arr.size());
    assertEquals("o1", arr.getString(0));
    assertEquals("o2", arr.getString(1));

    verify(catalogueManager).allDatasetIdentifiers("vocabularies");
  }

  @Test
  void listVocabularies_identifiers_returnsIdsJson() {
    // mock DCATAPUriRef list
    DCATAPUriRef r1 = mock(DCATAPUriRef.class);
    DCATAPUriRef r2 = mock(DCATAPUriRef.class);
    when(r1.getId()).thenReturn("v1");
    when(r2.getId()).thenReturn("v2");
    when(catalogueManager.datasets(eq("vocabularies"), anyInt(), anyInt())).thenReturn(
        Future.succeededFuture(List.of(r1, r2))
    );

    String encoded = vocabulariesService
        .listVocabularies("application/json", "identifiers", 10, 0)
        .result();
    JsonArray arr = new JsonArray(encoded);
    assertEquals(2, arr.size());
    assertEquals("v1", arr.getString(0));
    assertEquals("v2", arr.getString(1));
  }

  @Test
  void listVocabularies_uriRefs_returnsUrisJson() {
    DCATAPUriRef r1 = mock(DCATAPUriRef.class);
    DCATAPUriRef r2 = mock(DCATAPUriRef.class);
    when(r1.getUri()).thenReturn("http://example/vocab/1");
    when(r2.getUri()).thenReturn("http://example/vocab/2");

    when(catalogueManager.datasets(eq("vocabularies"), anyInt(), anyInt())).thenReturn(
        Future.succeededFuture(List.of(r1, r2))
    );

    String encoded = vocabulariesService
        .listVocabularies("application/json", "uriRefs", 10, 0)
        .result();
    JsonArray arr = new JsonArray(encoded);

    assertEquals(2, arr.size());
    assertEquals("http://example/vocab/1", arr.getString(0));
    assertEquals("http://example/vocab/2", arr.getString(1));

    verify(catalogueManager).datasets("vocabularies", 0, 10);
  }

  @Test
  void listVocabularies_metadata_returnsPresentedPayload() {
    // the service calls catalogueManager.listDatasets(...) then Piveau.presentAs(list, acceptType, DCAT.Dataset)
    List<Model> models = List.of(
        ModelFactory.createDefaultModel(),
        ModelFactory.createDefaultModel()
    );
    String presented = "PRESENTED";

    when(catalogueManager.listDatasets(eq("vocabularies"), anyInt(), anyInt())).thenReturn(
        Future.succeededFuture(models)
    );

    try (MockedStatic<Piveau> pivStatic = mockStatic(Piveau.class)) {
      pivStatic
          .when(() -> Piveau.presentAs(eq(models), eq("application/ld+json"), eq(DCAT.Dataset)))
          .thenReturn(presented);

      String out = vocabulariesService
          .listVocabularies("application/ld+json", "metadata", 5, 2)
          .result();
      assertEquals(presented, out);

      verify(catalogueManager).listDatasets("vocabularies", 2, 5);
      pivStatic.verify(() ->
          Piveau.presentAs(eq(models), eq("application/ld+json"), eq(DCAT.Dataset))
      );
    }
  }

  @Test
  void listVocabularies_unknownType_400() {
    Future<String> fut = vocabulariesService.listVocabularies("application/json", "nope", 10, 0);
    assertTrue(fut.failed());
    assertInstanceOf(ServiceException.class, fut.cause());
    assertEquals(400, ((ServiceException) fut.cause()).failureCode());
  }

  // ---------------- getVocabulary ----------------
  @Test
  void getVocabulary_success_returnsSerialized() {
    String vocabId = "ev";
    String uri = "http://example/vocab";
    Model model = ModelFactory.createDefaultModel();
    String serialized = "SER";

    try (
        MockedStatic<Vocabularies> vStatic = mockStatic(Vocabularies.class);
        MockedStatic<Prefixes> prefStatic = mockStatic(Prefixes.class);
        MockedStatic<Piveau> pivStatic = mockStatic(Piveau.class)
    ) {
      vStatic
          .when(() -> Vocabularies.findVocabularyUriRef(eq(vocabId), eq(datasetManager)))
          .thenReturn(Future.succeededFuture(uri));
      when(tripleStore.getGraph(uri)).thenReturn(Future.succeededFuture(model));
      prefStatic.when(() -> Prefixes.setNsPrefixesFiltered(eq(model))).thenAnswer(inv -> null);
      pivStatic
          .when(() -> Piveau.presentAs(eq(model), eq("application/ld+json")))
          .thenReturn(serialized);

      String out = vocabulariesService.getVocabulary(vocabId, "application/ld+json").result();
      assertEquals(serialized, out);
    }
  }

  // ---------------- indexVocabulary ----------------
  @Test
  void indexVocabulary_success_callsIndex_andReturnsArray() {
    String vocabId = "ev";
    String uri = "http://example/vocab";
    Model model = ModelFactory.createDefaultModel();
    JsonObject index = new JsonObject().put("ok", true);

    try (
        MockedStatic<Vocabularies> vStatic = mockStatic(Vocabularies.class);
        MockedStatic<IndexingVocabulariesKt> idxStatic = mockStatic(IndexingVocabulariesKt.class)
    ) {
      vStatic
          .when(() -> Vocabularies.findVocabularyUriRef(eq(vocabId), eq(datasetManager)))
          .thenReturn(Future.succeededFuture(uri));
      when(tripleStore.getGraph(uri)).thenReturn(Future.succeededFuture(model));
      idxStatic
          .when(() -> IndexingVocabulariesKt.indexConceptScheme(eq(vocabId), eq(uri), eq(model)))
          .thenReturn(index);

      // complete handler with success
      doAnswer(inv -> {
        Handler<AsyncResult<JsonArray>> handler = inv.getArgument(2);
        handler.handle(Future.succeededFuture(new JsonArray().add(1)));
        return null;
      })
          .when(indexService)
          .putVocabulary(eq(vocabId), eq(index), any());

      JsonArray arr = vocabulariesService.indexVocabulary(vocabId).result();
      assertEquals(1, arr.size());
      verify(indexService).putVocabulary(eq(vocabId), eq(index), any());
    }
  }

  // ---------------- putVocabularyFile ----------------
  @Test
  void putVocabularyFile_success_setsGraph_andReturnsStatus(Vertx vertx) throws Exception {
    String vocabId = "v1";
    String vocabUri = "http://example/vocab";

    // Real TTL file (no static mocks)
    var tmp = Files.createTempFile("vocab", ".ttl");
    Files.writeString(
        tmp,
        """
                @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
                <http://example/vocab> a skos:ConceptScheme .
            """
    );

    // Disable indexing to avoid IndexService interactions on worker thread
    VocabulariesService vocabulariesService = newService(vertx, false);

    // Make sure setGraph is stubbed with any(Model) to avoid instance mismatch
    when(tripleStore.setGraph(eq(vocabUri), any(Model.class), eq(false))).thenReturn(
        Future.succeededFuture("OK")
    );

    String status = vocabulariesService
        .putVocabularyFile(vocabId, vocabUri, "text/turtle", tmp.toString())
        .toCompletionStage()
        .toCompletableFuture()
        .get(2, TimeUnit.SECONDS);

    assertEquals("OK", status);
    verify(tripleStore).setGraph(eq(vocabUri), any(Model.class), eq(false));
  }

  // ---------------- putVocabulary ----------------
  @Test
  void putVocabulary_success_withExplicitUri_indexesAndReturnsStatus() throws Exception {
    String vocabId = "v1";
    String vocabUri = "http://example/vocab";
    String contentType = "text/turtle";
    String content = """
        @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
        <http://example/vocab> a skos:ConceptScheme .
        """;

    // ---- updateVocabularyDataset() dependencies ----
    when(datasetsService.getDatasetOrigin(eq(vocabId), eq("vocabularies"), anyString())).thenReturn(
        Future.failedFuture(new ServiceException(404, "Dataset not found"))
    );

    when(
        datasetsService.putDatasetOrigin(
            eq(vocabId),
            anyString(),
            anyString(),
            eq("vocabularies"),
            eq(false)
        )
    ).thenReturn(Future.succeededFuture(new JsonObject().put("status", "created")));

    // ---- triplestore storage ----
    when(tripleStore.setGraph(eq(vocabUri), any(Model.class), eq(false))).thenReturn(
        Future.succeededFuture("OK")
    );

    // ---- indexing ----
    doAnswer(inv -> {
      Handler<AsyncResult<JsonArray>> handler = inv.getArgument(2);
      handler.handle(Future.succeededFuture(new JsonArray()));
      return null;
    })
        .when(indexService)
        .putVocabulary(eq(vocabId), any(JsonObject.class), any());

    // Run
    String status = vocabulariesService
        .putVocabulary(vocabId, vocabUri, contentType, content)
        .toCompletionStage()
        .toCompletableFuture()
        .get(2, TimeUnit.SECONDS);

    assertEquals("OK", status);

    // Verify graph stored under the explicit URI
    verify(tripleStore).setGraph(eq(vocabUri), any(Model.class), eq(false));

    // Verify dataset helper path was invoked (loosely on accept/content types)
    verify(datasetsService).getDatasetOrigin(eq(vocabId), eq("vocabularies"), anyString());
    verify(datasetsService).putDatasetOrigin(
        eq(vocabId),
        anyString(),
        anyString(),
        eq("vocabularies"),
        eq(false)
    );

    // Verify indexing was called
    verify(indexService).putVocabulary(eq(vocabId), any(JsonObject.class), any());
  }

  @Test
  void putVocabulary_blankUri_extractsConceptSchemeUri_andIndexes() throws Exception {
    String vocabId = "v1";
    String extractedUri = "http://example/extracted";
    String contentType = "text/turtle";
    String content = """
        @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
        <http://example/extracted> a skos:ConceptScheme .
        """;

    // ---- updateVocabularyDataset() dependencies ----
    // Make getDatasetOrigin fail so updateVocabularyDataset creates a new dataset model
    when(datasetsService.getDatasetOrigin(vocabId, "vocabularies", NTRIPLES)).thenReturn(
        Future.failedFuture(new ServiceException(404, "Dataset not found"))
    );

    // It will then call putDatasetOrigin(..., "vocabularies", false)
    when(
        datasetsService.putDatasetOrigin(
            eq(vocabId),
            anyString(),
            eq(NTRIPLES),
            eq("vocabularies"),
            eq(false)
        )
    ).thenReturn(Future.succeededFuture(new JsonObject().put("status", "created")));

    // ---- triplestore storage ----
    when(tripleStore.setGraph(eq(extractedUri), any(Model.class), eq(false))).thenReturn(
        Future.succeededFuture("OK")
    );

    // ---- indexing ----
    doAnswer(inv -> {
      Handler<AsyncResult<JsonArray>> handler = inv.getArgument(2);
      handler.handle(Future.succeededFuture(new JsonArray()));
      return null;
    })
        .when(indexService)
        .putVocabulary(eq(vocabId), any(JsonObject.class), any());

    String status = vocabulariesService
        .putVocabulary(vocabId, "", contentType, content)
        .toCompletionStage()
        .toCompletableFuture()
        .get(2, TimeUnit.SECONDS);

    assertEquals("OK", status);

    // verify extracted URI was used for graph write + indexing
    verify(tripleStore).setGraph(eq(extractedUri), any(Model.class), eq(false));
    verify(indexService).putVocabulary(eq(vocabId), any(JsonObject.class), any());

    // and verify updateVocabularyDataset path was invoked through the service
    verify(datasetsService).getDatasetOrigin(vocabId, "vocabularies", NTRIPLES);
    verify(datasetsService).putDatasetOrigin(
        eq(vocabId),
        anyString(),
        eq(NTRIPLES),
        eq("vocabularies"),
        eq(false)
    );
  }

  @Test
  void putVocabulary_parseError_failsWith500(VertxTestContext testContext) {
    Future<String> fut = vocabulariesService.putVocabulary(
        "v1",
        "http://example/vocab",
        "text/turtle",
        "BAD"
    );

    fut.onComplete(ar -> {
      testContext.verify(() -> {
        assertTrue(ar.failed(), "Expected future to fail");
        assertInstanceOf(ServiceException.class, ar.cause());
        var se = (ServiceException) ar.cause();
        assertEquals(500, se.failureCode());
        assertTrue(se.getMessage().contains("Out of place"));
        assertTrue(se.getMessage().contains("KEYWORD:BAD"));
      });
      testContext.completeNow();
    });
  }

  // ---------------- putVocabularyChunk ----------------
  @Test
  void putVocabularyChunk_firstChunk_returnsAccepted() {
    Future<String> fut = vocabulariesService.putVocabularyChunk(
        "v1",
        "http://example/vocab",
        "text/turtle",
        "CHUNK-0",
        "hash-1",
        0,
        2
    );
    assertTrue(fut.succeeded());
    assertEquals("accepted", fut.result());
  }

  @Test
  void putVocabularyChunk_completeAll_callsTriplestore_andReturnsFinal() {
    String vocabId = "v1";
    String vocabUri = "http://example/vocab";
    String hash = "hash-1";

    try (
        MockedStatic<Vocabularies> vStatic = mockStatic(Vocabularies.class);
        MockedStatic<Piveau> pivStatic = mockStatic(Piveau.class);
        MockedStatic<IndexingVocabulariesKt> idxStatic = mockStatic(IndexingVocabulariesKt.class)
    ) {
      // First chunk
      assertEquals(
          "accepted",
          vocabulariesService
              .putVocabularyChunk(vocabId, vocabUri, "text/turtle", "C0", hash, 0, 2)
              .result()
      );

      // Stubs for completion path on 2nd chunk
      vStatic
          .when(() -> Vocabularies.updateVocabularyDataset(any(), any(), eq(vocabUri), eq(vocabId)))
          .thenReturn(Future.succeededFuture());

      when(tripleStore.update("CLEAR GRAPH <" + vocabUri + ">;")).thenReturn(
          Future.succeededFuture()
      );
      pivStatic
          .when(() -> Piveau.toModel(any(byte[].class), eq("text/turtle")))
          .thenReturn(ModelFactory.createDefaultModel());
      when(tripleStore.putGraph(eq(vocabUri), any(Model.class))).thenReturn(
          Future.succeededFuture("PUT_OK")
      );
      when(tripleStore.postGraph(eq(vocabUri), any(Model.class))).thenReturn(
          Future.succeededFuture("POST_OK")
      );

      // indexVocabulary (triggered onSuccess) stubs
      vStatic
          .when(() -> Vocabularies.findVocabularyUriRef(eq(vocabId), eq(datasetManager)))
          .thenReturn(Future.succeededFuture(vocabUri));
      when(tripleStore.getGraph(vocabUri)).thenReturn(
          Future.succeededFuture(ModelFactory.createDefaultModel())
      );
      idxStatic
          .when(() ->
              IndexingVocabulariesKt.indexConceptScheme(eq(vocabId), eq(vocabUri), any(Model.class))
          )
          .thenReturn(new JsonObject().put("ok", true));
      doAnswer(inv -> {
        Handler<AsyncResult<JsonArray>> handler = inv.getArgument(2);
        handler.handle(Future.succeededFuture(new JsonArray()));
        return null;
      })
          .when(indexService)
          .putVocabulary(eq(vocabId), any(JsonObject.class), any());

      // Second (final) chunk -> should return the last post result
      String result = vocabulariesService
          .putVocabularyChunk(vocabId, vocabUri, "text/turtle", "C1", hash, 1, 2)
          .result();
      assertEquals("POST_OK", result);
    }
  }

  @Test
  void putVocabularyChunk_badInputs_400() {
    // missing hash
    Future<String> f1 = vocabulariesService.putVocabularyChunk("v", "u", "t", "c", "", 0, 1);
    assertTrue(f1.failed());
    assertInstanceOf(ServiceException.class, f1.cause());
    assertEquals(400, ((ServiceException) f1.cause()).failureCode());

    // invalid indices
    Future<String> f2 = vocabulariesService.putVocabularyChunk("v", "u", "t", "c", "hash", 2, 1);
    assertTrue(f2.failed());
    assertEquals(400, ((ServiceException) f2.cause()).failureCode());
  }

  // ---------------- deleteVocabulary ----------------
  @Test
  void deleteVocabulary_success_deletesGraph_dataset_andIndexes() {
    String vocabId = "v1";
    String uri = "http://example/vocab";

    try (MockedStatic<Vocabularies> vStatic = mockStatic(Vocabularies.class)) {
      vStatic
          .when(() -> Vocabularies.findVocabularyUriRef(eq(vocabId), eq(datasetManager)))
          .thenReturn(Future.succeededFuture(uri));
      when(tripleStore.deleteGraph(uri)).thenReturn(Future.succeededFuture());
      when(datasetsService.deleteDatasetOrigin(vocabId, "vocabularies")).thenReturn(
          Future.succeededFuture()
      );

      // index service callback success
      doAnswer(inv -> {
        Handler<AsyncResult<JsonObject>> handler = inv.getArgument(1);
        handler.handle(Future.succeededFuture(new JsonObject()));
        return null;
      })
          .when(indexService)
          .deleteVocabulary(eq(vocabId), any());

      Future<Void> fut = vocabulariesService.deleteVocabulary(vocabId);
      assertTrue(fut.succeeded());
      verify(tripleStore).deleteGraph(uri);
      verify(datasetsService).deleteDatasetOrigin(vocabId, "vocabularies");
      verify(indexService).deleteVocabulary(eq(vocabId), any());
    }
  }
}
