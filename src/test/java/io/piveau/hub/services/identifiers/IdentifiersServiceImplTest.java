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
package io.piveau.hub.services.identifiers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.dcatap.DatasetManager;
import io.piveau.dcatap.TripleStore;
import io.piveau.hub.Constants;
import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.hub.indexing.Indexing;
import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.rdf.RDFMimeTypes;
import io.piveau.vocabularies.vocabulary.ADMS;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.serviceproxy.ServiceException;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@ExtendWith(VertxExtension.class)
class IdentifiersServiceImplTest {

  private AutoCloseable mocks;
  private Model model;
  private Resource datasetRes;

  @Mock
  private WebClient web;

  @Mock
  private TripleStore tripleStore;

  @Mock
  private DatasetManager datasetManager;

  @Mock
  private DatasetsService datasetsService;

  @Mock
  private DatasetHelper helper;

  private static final String DATASET_ID = "ds-1";
  private static final String CATALOGUE_ID = "cat-1";
  private static final String DATASET_URI = "http://data.example/dataset/" + DATASET_ID;
  private static final String RECORD_URI = "http://data.example/record/" + DATASET_ID;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);

    // real model to assert mutations
    model = ModelFactory.createDefaultModel();
    datasetRes = model.createResource(DATASET_URI);
    Resource recordRes = model.createResource(RECORD_URI);

    when(helper.model()).thenReturn(model);
    when(helper.resource()).thenReturn(datasetRes);
    when(helper.recordResource()).thenReturn(recordRes);
    when(helper.uriRef()).thenReturn(DATASET_URI);

    when(tripleStore.getDatasetManager()).thenReturn(datasetManager);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  private static JsonObject cfgWithRegistries(JsonArray registries) {
    return new JsonObject().put(Constants.ENV_PIVEAU_IDENTIFIERS_REGISTRATION, registries);
  }

  private static JsonArray registriesMockOnly() {
    return new JsonArray().add(new JsonObject()
        .put("type", "mock")
        .put("enabled", true));
  }

  private static JsonArray registriesMockAndDoi() {
    return new JsonArray()
        .add(new JsonObject()
            .put("type", "eu-ra-doi")
            .put("enabled", true)
            .put("api", "https://example-ra")
            .put("prefix", "10.1111")
            .put("user", "u").put("password", "p"))
        .add(new JsonObject()
            .put("type", "mock")
            .put("enabled", true));
  }

  // -------------------------- createIdentifier --------------------------
  @Test
  void createIdentifier_unknownType_returns500(Vertx vertx) {
    try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class);
        MockedStatic<DatasetHelper> dhStatic = mockStatic(DatasetHelper.class)) {

      dsStatic.when(() -> DatasetsService.createProxy(any(), anyString()))
          .thenReturn(datasetsService);

      when(datasetManager.get(anyString(), anyString()))
          .thenReturn(Future.succeededFuture(model));
      dhStatic.when(() -> DatasetHelper.create(any(Model.class)))
          .thenReturn(helper);

      IdentifiersService svc = IdentifiersService.create(
          vertx, web, cfgWithRegistries(registriesMockOnly()), tripleStore, ar -> {
          });

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<JsonObject>[] out = new AsyncResult[1];

      svc.createIdentifier(DATASET_ID, CATALOGUE_ID, "unknown", ar -> {
        out[0] = ar;
        latch.countDown();
      });
      awaitOk(latch);

      assertTrue(out[0].failed());
      assertInstanceOf(ServiceException.class, out[0].cause());
      assertEquals(500, ((ServiceException) out[0].cause()).failureCode());
      assertTrue(out[0].cause().getMessage().contains("not configured"));
    }
  }

  @Test
  void createIdentifier_mock_success_modelUpdated_andDatasetStored(Vertx vertx) {
    try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class);
        MockedStatic<DatasetHelper> dhStatic = mockStatic(DatasetHelper.class)) {

      dsStatic.when(() -> DatasetsService.createProxy(any(), anyString()))
          .thenReturn(datasetsService);
      when(datasetsService.putDatasetOrigin(
          eq(DATASET_ID), anyString(), eq(RDFMimeTypes.NQUADS), eq(CATALOGUE_ID), anyBoolean()))
          .thenReturn(Future.succeededFuture());

      when(datasetManager.get(DATASET_ID, CATALOGUE_ID))
          .thenReturn(Future.succeededFuture(model));
      dhStatic.when(() -> DatasetHelper.create(any(Model.class))).thenReturn(helper);

      IdentifiersService svc = IdentifiersService.create(
          vertx, web, cfgWithRegistries(registriesMockOnly()), tripleStore, ar -> {
          });

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<JsonObject>[] out = new AsyncResult[1];

      svc.createIdentifier(DATASET_ID, CATALOGUE_ID, "mock", ar -> {
        out[0] = ar;
        latch.countDown();
      });
      awaitOk(latch);

      assertTrue(out[0].succeeded());
      assertEquals("success", out[0].result().getString("status"));
      String identifierUri = out[0].result().getString("identifier");
      assertNotNull(identifierUri);

      // Verify storage called
      verify(datasetsService).putDatasetOrigin(eq(DATASET_ID), anyString(), eq(RDFMimeTypes.NQUADS),
          eq(CATALOGUE_ID), eq(false));

      // Assert model was updated with ADMS.identifier
      Statement s = datasetRes.getProperty(ADMS.identifier);
      assertNotNull(s, "ADMS.identifier missing on dataset");
      Resource idRes = s.getResource();
      assertNotNull(idRes);
      assertTrue(idRes.hasProperty(RDF.type, ADMS.Identifier));
      assertTrue(idRes.hasProperty(SKOS.notation));
      assertTrue(idRes.hasProperty(DCTerms.creator));
      assertTrue(idRes.hasProperty(DCTerms.issued));
      // issued typed as xsd:dateTime
      assertEquals(XSDDatatype.XSDdateTime.getURI(),
          idRes.getProperty(DCTerms.issued).getLiteral().getDatatypeURI());
    }
  }

  @Test
  void createIdentifier_datasetNotFound_returns404(Vertx vertx) {
    try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class);
        MockedStatic<DatasetHelper> ignored = mockStatic(DatasetHelper.class)) {

      dsStatic.when(() -> DatasetsService.createProxy(any(), anyString()))
          .thenReturn(datasetsService);

      when(datasetManager.get(anyString(), anyString()))
          .thenReturn(Future.failedFuture("nope"));

      IdentifiersService svc = IdentifiersService.create(
          vertx, web, cfgWithRegistries(registriesMockOnly()), tripleStore, ar -> {
          });

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<JsonObject>[] out = new AsyncResult[1];
      svc.createIdentifier(DATASET_ID, CATALOGUE_ID, "mock", ar -> {
        out[0] = ar;
        latch.countDown();
      });
      awaitOk(latch);

      assertTrue(out[0].failed());
      ServiceException se = (ServiceException) out[0].cause();
      assertEquals(404, se.failureCode());
      assertTrue(se.getMessage().contains("dataset was not found"));
    }
  }

  @Test
  void createIdentifier_registryFailure_bubbles500(Vertx vertx) {
    try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class);
        MockedStatic<DatasetHelper> dhStatic = mockStatic(DatasetHelper.class)) {

      dsStatic.when(() -> DatasetsService.createProxy(any(), anyString()))
          .thenReturn(datasetsService);
      when(datasetManager.get(DATASET_ID, CATALOGUE_ID)).thenReturn(Future.succeededFuture(model));
      dhStatic.when(() -> DatasetHelper.create(any(Model.class))).thenReturn(helper);

      // Build service then replace registries map with a failing registry
      IdentifiersServiceImpl svcImpl = (IdentifiersServiceImpl) IdentifiersService.create(
          vertx, web, cfgWithRegistries(registriesMockOnly()), tripleStore, ar -> {
          });
      IdentifierRegistry failing = mock(IdentifierRegistry.class);
      when(failing.getIdentifier(anyString(), any(), any()))
          .thenReturn(Future.failedFuture("boom"));

      Field f = IdentifiersServiceImpl.class.getDeclaredField("registries");
      f.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, IdentifierRegistry> regs = (Map<String, IdentifierRegistry>) f.get(svcImpl);
      regs.clear();
      regs.put("mock", failing);

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<JsonObject>[] out = new AsyncResult[1];
      svcImpl.createIdentifier(DATASET_ID, CATALOGUE_ID, "mock", ar -> {
        out[0] = ar;
        latch.countDown();
      });
      awaitOk(latch);

      assertTrue(out[0].failed());
      ServiceException se = (ServiceException) out[0].cause();
      assertEquals(500, se.failureCode());
      assertTrue(se.getMessage().contains("boom"));
    } catch (NoSuchFieldException | IllegalAccessException e) {
      fail(e);
    }
  }

  @Test
  void createIdentifier_storeFailure_bubblesFailure(Vertx vertx) {
    try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class);
        MockedStatic<DatasetHelper> dhStatic = mockStatic(DatasetHelper.class)) {

      dsStatic.when(() -> DatasetsService.createProxy(any(), anyString()))
          .thenReturn(datasetsService);
      when(datasetsService.putDatasetOrigin(anyString(), anyString(), anyString(), anyString(),
          anyBoolean()))
          .thenReturn(Future.failedFuture("store failed"));

      when(datasetManager.get(DATASET_ID, CATALOGUE_ID)).thenReturn(Future.succeededFuture(model));
      dhStatic.when(() -> DatasetHelper.create(any(Model.class))).thenReturn(helper);

      IdentifiersService svc = IdentifiersService.create(
          vertx, web, cfgWithRegistries(registriesMockOnly()), tripleStore, ar -> {
          });

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<JsonObject>[] out = new AsyncResult[1];
      svc.createIdentifier(DATASET_ID, CATALOGUE_ID, "mock", ar -> {
        out[0] = ar;
        latch.countDown();
      });
      awaitOk(latch);

      assertTrue(out[0].failed());
      assertEquals("store failed", out[0].cause().getMessage());
    }
  }

  @Test
  void getExistingIdentifiers_isUsed_andExtractsNotationAndSchema(Vertx vertx) throws Exception {
    // Build an existing ADMS identifier on the dataset
    String existingDoiUri = "https://doi.org/10.1111/999999999999999";
    String existingNotation = "10.1111/999999999999999";

    Resource idRes = model.createResource(existingDoiUri);
    // make the notation a TYPED literal so schema can be picked up
    idRes.addProperty(SKOS.notation,
        model.createTypedLiteral(existingNotation, XSDDatatype.XSDstring));
    datasetRes.addProperty(ADMS.identifier, idRes);

    try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class);
        MockedStatic<DatasetHelper> dhStatic = mockStatic(DatasetHelper.class)) {

      dsStatic.when(() -> DatasetsService.createProxy(any(), anyString()))
          .thenReturn(datasetsService);
      when(datasetsService.putDatasetOrigin(
          eq(DATASET_ID), anyString(), eq(RDFMimeTypes.NQUADS), eq(CATALOGUE_ID), anyBoolean()))
          .thenReturn(Future.succeededFuture());

      // triple store returns our real model; helper wraps it
      when(datasetManager.get(DATASET_ID, CATALOGUE_ID)).thenReturn(Future.succeededFuture(model));
      dhStatic.when(() -> DatasetHelper.create(any(Model.class))).thenReturn(helper);

      // Build the service (with real MockRegistry), then replace registry with a MOCK to capture args
      IdentifiersServiceImpl svcImpl = (IdentifiersServiceImpl) IdentifiersService.create(
          vertx, web, cfgWithRegistries(registriesMockOnly()), tripleStore, ar -> {
          });

      // Swap in a mock registry
      Field f = IdentifiersServiceImpl.class.getDeclaredField("registries");
      f.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, IdentifierRegistry> regs = (Map<String, IdentifierRegistry>) f.get(svcImpl);
      regs.clear();
      IdentifierRegistry regMock = mock(IdentifierRegistry.class);
      regs.put("mock", regMock);

      // Return a minimal valid identifier so setIdentifier(...) doesn't NPE
      Identifier newId = new Identifier();
      newId.identifierURI = "https://doi.org/10.1111/new123";
      newId.identifier = "10.1111/new123";
      newId.schema = XSDDatatype.XSDstring.getURI();
      newId.creatorURI = "http://creator";
      newId.issued = OffsetDateTime.now().toZonedDateTime();

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Identifier>> existingCap = ArgumentCaptor.forClass(List.class);
      when(regMock.getIdentifier(eq(DATASET_URI), eq(helper), existingCap.capture()))
          .thenReturn(Future.succeededFuture(newId));

      // Act
      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<JsonObject>[] out = new AsyncResult[1];
      svcImpl.createIdentifier(DATASET_ID, CATALOGUE_ID, "mock", ar -> {
        out[0] = ar;
        latch.countDown();
      });
      assertTrue(latch.await(1, TimeUnit.SECONDS), "callback not called");

      // Assert handler success
      assertTrue(out[0].succeeded());

      // Assert getExistingIdentifiers extracted our existing identifier correctly
      List<Identifier> passed = existingCap.getValue();
      assertNotNull(passed);
      assertEquals(1, passed.size());
      Identifier extracted = passed.get(0);
      assertEquals(existingDoiUri, extracted.identifierURI);
      assertEquals(existingNotation, extracted.identifier);
      assertEquals(XSDDatatype.XSDstring.getURI(), extracted.schema);
    }
  }


  // -------------------------- checkIdentifierRequirement --------------------------
  private static JsonObject sampleIndexedMetadata() {
    return new JsonObject()
        .put("creator", new JsonObject().put("name", "Creator Org"))
        .put("publisher", new JsonObject().put("name", "Publisher Org"))
        .put("issued", "2024-01-01")
        .put("title", new JsonObject().put("en", "T"));
  }

  @Test
  void checkIdentifierRequirement_allTypes_returnsBoth(Vertx vertx) {
    try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class);
        MockedStatic<DatasetHelper> dhStatic = mockStatic(DatasetHelper.class);
        MockedStatic<Indexing> idxStatic = mockStatic(Indexing.class)) {

      dsStatic.when(() -> DatasetsService.createProxy(any(), anyString()))
          .thenReturn(datasetsService);

      when(datasetManager.get(DATASET_ID, CATALOGUE_ID)).thenReturn(Future.succeededFuture(model));
      dhStatic.when(() -> DatasetHelper.create(any(Model.class))).thenReturn(helper);

      idxStatic.when(
              () -> Indexing.indexingDataset(any(Resource.class), any(Resource.class), isNull(),
                  eq("en")))
          .thenReturn(sampleIndexedMetadata());

      IdentifiersService svc = IdentifiersService.create(
          vertx, web, cfgWithRegistries(registriesMockAndDoi()), tripleStore, ar -> {
          });

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<JsonObject>[] out = new AsyncResult[1];
      svc.checkIdentifierRequirement(DATASET_ID, CATALOGUE_ID, null, ar -> {
        out[0] = ar;
        latch.countDown();
      });
      awaitOk(latch);

      assertTrue(out[0].succeeded());
      JsonArray arr = out[0].result().getJsonArray("status");
      assertNotNull(arr);
      assertEquals(2, arr.size());

      // Names present regardless of order
      Set<String> names = new HashSet<>();
      for (int i = 0; i < arr.size(); i++) {
        names.add(arr.getJsonObject(i).getString("identifierName"));
        assertTrue(arr.getJsonObject(i).getBoolean("isEligible"));
      }
      assertTrue(names.contains("Mock Identity Registry"));
      assertTrue(names.contains("EU RA DOI Registry"));
    }
  }

  @Test
  void checkIdentifierRequirement_specificType_onlyOne(Vertx vertx) {
    try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class);
        MockedStatic<DatasetHelper> dhStatic = mockStatic(DatasetHelper.class);
        MockedStatic<Indexing> idxStatic = mockStatic(Indexing.class)) {

      dsStatic.when(() -> DatasetsService.createProxy(any(), anyString()))
          .thenReturn(datasetsService);

      when(datasetManager.get(DATASET_ID, CATALOGUE_ID)).thenReturn(Future.succeededFuture(model));
      dhStatic.when(() -> DatasetHelper.create(any(Model.class))).thenReturn(helper);

      idxStatic.when(
              () -> Indexing.indexingDataset(any(Resource.class), any(Resource.class), isNull(),
                  eq("en")))
          .thenReturn(sampleIndexedMetadata());

      IdentifiersService svc = IdentifiersService.create(
          vertx, web, cfgWithRegistries(registriesMockAndDoi()), tripleStore, ar -> {
          });

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<JsonObject>[] out = new AsyncResult[1];
      svc.checkIdentifierRequirement(DATASET_ID, CATALOGUE_ID, "mock", ar -> {
        out[0] = ar;
        latch.countDown();
      });
      awaitOk(latch);

      assertTrue(out[0].succeeded());
      JsonArray arr = out[0].result().getJsonArray("status");
      assertEquals(1, arr.size());
      assertEquals("Mock Identity Registry", arr.getJsonObject(0).getString("identifierName"));
    }
  }

  @Test
  void checkIdentifierRequirement_datasetNotFound_returns404(Vertx vertx) {
    try (MockedStatic<DatasetsService> dsStatic = mockStatic(DatasetsService.class);
        MockedStatic<DatasetHelper> ignored = mockStatic(DatasetHelper.class)) {

      dsStatic.when(() -> DatasetsService.createProxy(any(), anyString()))
          .thenReturn(datasetsService);

      when(datasetManager.get(anyString(), anyString()))
          .thenReturn(Future.failedFuture("missing"));

      IdentifiersService svc = IdentifiersService.create(
          vertx, web, cfgWithRegistries(registriesMockOnly()), tripleStore, ar -> {
          });

      CountDownLatch latch = new CountDownLatch(1);
      final AsyncResult<JsonObject>[] out = new AsyncResult[1];
      svc.checkIdentifierRequirement(DATASET_ID, CATALOGUE_ID, null, ar -> {
        out[0] = ar;
        latch.countDown();
      });
      awaitOk(latch);

      assertTrue(out[0].failed());
      ServiceException se = (ServiceException) out[0].cause();
      assertEquals(404, se.failureCode());
      assertTrue(se.getMessage().contains("dataset was not found"));
    }
  }

  private static void awaitOk(CountDownLatch latch) {
    try {
      assertTrue(latch.await(1, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      fail(e);
    }
  }
}
