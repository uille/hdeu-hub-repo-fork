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
package io.piveau.hub.services.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.dcatap.Prefixes;
import io.piveau.dcatap.TripleStore;
import io.piveau.hub.Constants;
import io.piveau.hub.services.index.IndexService;
import io.piveau.profile.ExtraResource;
import io.piveau.profile.Indexer;
import io.piveau.profile.PiveauProfile;
import io.piveau.rdf.Piveau;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.serviceproxy.ServiceException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@ExtendWith(VertxExtension.class)
class ResourcesServiceImplTest {

  private AutoCloseable mocks;
  private ResourcesService service;

  @Mock
  private TripleStore tripleStore;

  @Mock
  private PiveauProfile profile;

  @Mock
  private IndexService indexService;

  // no "resource" key -> use default "set/resource/"
  private static final JsonObject SCHEMA_CFG = new JsonObject()
      .put("baseUri", "http://healthdata-local.com/");

  private static final JsonObject SEARCH_CFG = new JsonObject()
      .put("enabled", true)
      .put("url", "localhost")
      .put("port", 8082)
      .put("api_key", "k");

  private static JsonObject baseConfig() {
    return new JsonObject()
        .put(Constants.ENV_PIVEAU_DCATAP_SCHEMA_CONFIG, SCHEMA_CFG)
        .put(Constants.ENV_PIVEAU_HUB_SEARCH_SERVICE, SEARCH_CFG);
  }

  @BeforeEach
  void setUp(Vertx vertx) {
    mocks = MockitoAnnotations.openMocks(this);

    try (MockedStatic<IndexService> idxStatic = mockStatic(IndexService.class)) {
      idxStatic.when(
              () -> IndexService.createProxy(eq(vertx), eq(IndexService.SERVICE_ADDRESS), anyLong()))
          .thenReturn(indexService);
      service = ResourcesService.create(vertx, tripleStore, baseConfig(), profile, ar -> {
      });
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  // -------- listResourceTypes --------
  @Test
  void listResourceTypes_returnsKeysFromProfile() throws Exception {
    // prepare profile.extraResources as a map with two keys
    Map<String, ExtraResource> map = new HashMap<>();
    map.put("TypeA", mock(ExtraResource.class));
    map.put("TypeB", mock(ExtraResource.class));
    Field field = PiveauProfile.class.getDeclaredField("extraResources");
    field.setAccessible(true);
    field.set(profile, map);

    JsonArray arr = service.listResourceTypes().result();
    assertEquals(2, arr.size());
    assertTrue(arr.contains("TypeA"));
    assertTrue(arr.contains("TypeB"));
  }

  // -------- listResources --------
  @Test
  void listResources_knownType_returnsUris() {
    String type = "TypeA";
    ExtraResource er = mock(ExtraResource.class);
    when(profile.extraResourceIsSet(type)).thenReturn(true);
    when(profile.getExtraResource(type)).thenReturn(er);
    when(er.getTypeURI()).thenReturn("http://example.org/types/TypeA");

    ResultSet rs = mock(ResultSet.class);
    QuerySolution qs1 = mock(QuerySolution.class);
    QuerySolution qs2 = mock(QuerySolution.class);
    Resource r1 = mock(Resource.class);
    Resource r2 = mock(Resource.class);
    when(r1.getURI()).thenReturn("http://res/1");
    when(r2.getURI()).thenReturn("http://res/2");
    when(qs1.getResource("resource")).thenReturn(r1);
    when(qs2.getResource("resource")).thenReturn(r2);
    when(rs.hasNext()).thenReturn(true, true, false);
    when(rs.next()).thenReturn(qs1, qs2);
    when(tripleStore.select(anyString())).thenReturn(Future.succeededFuture(rs));

    JsonArray arr = service.listResources(type).result();
    assertEquals(2, arr.size());
    assertEquals("http://res/1", arr.getString(0));
    assertEquals("http://res/2", arr.getString(1));
  }

  @Test
  void listResources_unknownType_404() {
    when(profile.extraResourceIsSet("Nope")).thenReturn(false);
    Future<JsonArray> fut = service.listResources("Nope");
    assertTrue(fut.failed());
    assertInstanceOf(ServiceException.class, fut.cause());
    assertEquals(404, ((ServiceException) fut.cause()).failureCode());
  }

  // -------- getResource --------
  @Test
  void getResource_modelPresent_serializes() {
    // build a non-empty model
    Model model = ModelFactory.createDefaultModel();
    Resource resource = model.createResource("http://s");
    resource.addProperty(RDF.type, RDF.Property);

    String expected = "SERIALIZED";
    try (MockedStatic<Prefixes> prefStatic = mockStatic(Prefixes.class);
        MockedStatic<Piveau> pivStatic = mockStatic(Piveau.class)) {

      // TS returns the model
      when(tripleStore.construct(anyString())).thenReturn(Future.succeededFuture(model));
      // Prefixes no-op
      prefStatic.when(() -> Prefixes.setNsPrefixesFiltered(any(Model.class)))
          .thenAnswer(inv -> null);
      // Serialize
      pivStatic.when(() -> Piveau.presentAs(eq(model), eq("application/ld+json")))
          .thenReturn(expected);

      String out = service.getResource("ID1", "TypeX", "application/ld+json").result();
      assertEquals(expected, out);
    }
  }

  @Test
  void getResource_notFound_404() {
    // empty model -> 404
    when(tripleStore.construct(anyString())).thenReturn(
        Future.succeededFuture(ModelFactory.createDefaultModel()));
    Future<String> fut = service.getResource("ID1", "TypeX", "application/ld+json");
    assertTrue(fut.failed());
    assertInstanceOf(ServiceException.class, fut.cause());
    assertEquals(404, ((ServiceException) fut.cause()).failureCode());
  }

  // -------- deleteResource --------
  @Test
  void deleteResource_success_callsIndexService_andCompletes() {
    when(tripleStore.deleteGraph(startsWith("http://healthdata-local.com/set/resource/TypeX/ID1")))
        .thenReturn(Future.succeededFuture());

    // indexService.deleteResource should succeed via handler
    doAnswer(inv -> indexService).when(indexService)
        .deleteResource(anyString(), anyString(), any());

    // Use doAnswer-style to invoke the handler (as defined in IndexService interface)
    doAnswer(inv -> {
      Handler<AsyncResult<Void>> h = inv.getArgument(2);
      h.handle(Future.succeededFuture());
      return indexService;
    }).when(indexService).deleteResource(eq("ID1"), eq("TypeX"), any());

    Future<Void> fut = service.deleteResource("ID1", "TypeX");
    assertTrue(fut.succeeded());
  }

  @Test
  void deleteResource_notFound_404() {
    when(tripleStore.deleteGraph(anyString()))
        .thenReturn(Future.failedFuture(new RuntimeException("Not Found: graph")));
    Future<Void> fut = service.deleteResource("ID1", "TypeX");
    assertTrue(fut.failed());
    assertInstanceOf(ServiceException.class, fut.cause());
    assertEquals(404, ((ServiceException) fut.cause()).failureCode());
  }

  // -------- putResource --------
  @Test
  void putResource_success_setsGraph_indexes_andReturnsSetGraphResult() {
    String type = "TypeA";
    String typeUri = "http://example.org/types/TypeA";

    when(profile.extraResourceIsSet(type)).thenReturn(true);
    ExtraResource er = mock(ExtraResource.class);
    when(profile.getExtraResource(type)).thenReturn(er);
    when(er.getTypeURI()).thenReturn(typeUri);
    when(er.getIndexingInstructions()).thenReturn(new JsonObject());

    // Build a model that contains a subject with rdf:type <typeUri>
    Model m = ModelFactory.createDefaultModel();
    Resource subj = m.createResource("http://tmp");
    Resource typeRes = m.createResource(typeUri);
    subj.addProperty(RDF.type, typeRes);

    try (MockedStatic<Piveau> pStatic = mockStatic(Piveau.class);
        MockedStatic<Indexer> idxStatic = mockStatic(Indexer.class)) {

      // Piveau.toModel -> our prepared model
      pStatic.when(() -> Piveau.toModel(any(byte[].class), eq("text/turtle"))).thenReturn(m);

      // setGraph returns "OK"
      when(tripleStore.setGraph(startsWith("http://healthdata-local.com/set/resource/TypeA/ID7"),
          eq(m), eq(true))).thenReturn(Future.succeededFuture("OK"));

      // Indexer.indexingResource returns JsonObject
      idxStatic.when(
              () -> Indexer.indexingResource(any(Resource.class), isNull(), any(JsonObject.class),
                  eq("en"), eq("other"), eq(false), isNull(), eq(false)))
          .thenReturn(Future.succeededFuture(new JsonObject().put("indexed", true)));

      // indexService.putResource should succeed via handler
      doAnswer(inv -> {
        Handler<AsyncResult<Void>> h = inv.getArgument(3);
        h.handle(Future.succeededFuture());
        return indexService;
      }).when(indexService).putResource(eq("ID7"), eq("TypeA"), any(JsonObject.class), any());

      String result = service.putResource("ID7", "TypeA", "@prefix : <x> .", "text/turtle")
          .result();
      assertEquals("OK", result);

      // Verify we attempted to index + push to search
      verify(indexService).putResource(eq("ID7"), eq("TypeA"), any(JsonObject.class), any());
    }
  }

  @Test
  void putResource_typeNotFoundInPayload_400() {
    String type = "TypeA";
    String typeUri = "http://example.org/types/TypeA";
    when(profile.extraResourceIsSet(type)).thenReturn(true);
    ExtraResource er = mock(ExtraResource.class);
    when(profile.getExtraResource(type)).thenReturn(er);
    when(er.getTypeURI()).thenReturn(typeUri);

    // Model without required rdf:type
    Model model = ModelFactory.createDefaultModel();

    try (MockedStatic<Piveau> pStatic = mockStatic(Piveau.class)) {
      pStatic.when(() -> Piveau.toModel(any(byte[].class), eq("application/ld+json")))
          .thenReturn(model);

      Future<String> fut = service.putResource("ID8", type, "{}", "application/ld+json");
      assertTrue(fut.failed());
      assertInstanceOf(ServiceException.class, fut.cause());
      assertEquals(400, ((ServiceException) fut.cause()).failureCode());
    }
  }

  @Test
  void putResource_unknownType_404() {
    when(profile.extraResourceIsSet("Nope")).thenReturn(false);
    Future<String> fut = service.putResource("ID9", "Nope", "{}", "application/ld+json");
    assertTrue(fut.failed());
    assertInstanceOf(ServiceException.class, fut.cause());
    assertEquals(404, ((ServiceException) fut.cause()).failureCode());
  }
}
