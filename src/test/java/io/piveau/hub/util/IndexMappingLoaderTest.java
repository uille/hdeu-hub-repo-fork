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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.piveau.hub.indexing.Indexing;
import io.piveau.profile.CoreResource;
import io.piveau.profile.Indexer;
import io.piveau.profile.PiveauProfile;
import io.piveau.vocabularies.vocabulary.DCATAP;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Tests for IndexMappingLoader (version that uses PiveauProfile.dataset/catalog and Indexer).
 */
class IndexMappingLoaderTest {

  /**
   * Helper to create a tiny in-memory Jena model with a resource at the given URI.
   */
  private static Model modelWithResource(String uri) {
    Model m = ModelFactory.createDefaultModel();
    m.createResource(uri); // just ensure the resource exists
    return m;
  }

  @Test
  void load_profileProvided_prefersProfileShapes_callsIndexingInit()
      throws NoSuchFieldException, IllegalAccessException {
    // --- Vertx & FS mocks: fallback (shapes/...) succeeds (we won't use its content for assertions)
    Vertx vertx = mock(Vertx.class, RETURNS_DEEP_STUBS);
    FileSystem fs = mock(FileSystem.class);
    when(vertx.fileSystem()).thenReturn(fs);

    // When reading conf/... fail, fallback to shapes/... succeed with minimal TTL
    Promise<Buffer> confRead = Promise.promise();
    confRead.fail("no conf file"); // force fail to exercise fallback
    when(fs.readFile(eq("conf/shapes/dcat-ap.hub.shapes.ttl"))).thenReturn(confRead.future());

    String minimalTtl = "# test\n";
    Promise<Buffer> fallbackRead = Promise.promise();
    fallbackRead.complete(Buffer.buffer(minimalTtl));
    when(fs.readFile(eq("shapes/dcat-ap.hub.shapes.ttl"))).thenReturn(fallbackRead.future());

    // --- Profile and nested CoreResources
    // --- Build a real PiveauProfile and set its fields via reflection
    PiveauProfile profile = new PiveauProfile();

    CoreResource datasetCore = mock(CoreResource.class);
    CoreResource catalogCore = mock(CoreResource.class);

    String dsShapeUri = "http://example.org/shape/dataset";
    String catShapeUri = "http://example.org/shape/catalog";

    when(datasetCore.getShapeUri()).thenReturn(dsShapeUri);
    when(catalogCore.getShapeUri()).thenReturn(catShapeUri);
    when(datasetCore.getShaclShape()).thenReturn(modelWithResource(dsShapeUri));
    when(catalogCore.getShaclShape()).thenReturn(modelWithResource(catShapeUri));

    // set public fields 'dataset' and 'catalog' on the real instance
    java.lang.reflect.Field fDs = PiveauProfile.class.getField("dataset");   // if not public, use getDeclaredField + setAccessible(true)
    java.lang.reflect.Field fCat = PiveauProfile.class.getField("catalog");
    fDs.set(profile, datasetCore);
    fCat.set(profile, catalogCore);

    when(datasetCore.getShapeUri()).thenReturn(dsShapeUri);
    when(catalogCore.getShapeUri()).thenReturn(catShapeUri);
    when(datasetCore.getShaclShape()).thenReturn(modelWithResource(dsShapeUri));
    when(catalogCore.getShaclShape()).thenReturn(modelWithResource(catShapeUri));

    // --- Mock static Indexer & Indexing
    JsonObject dsInstr = new JsonObject().put("k", "ds");
    JsonObject catInstr = new JsonObject().put("k", "cat");

    try (MockedStatic<Indexer> indexerStatic = mockStatic(Indexer.class);
        MockedStatic<Indexing> indexingStatic = mockStatic(Indexing.class)) {

      // produceIndexInstructionsFromResource returns known payloads
      indexerStatic.when(() -> Indexer.produceIndexInstructionsFromResource(argThat(r -> r != null)))
          .thenAnswer(inv -> {
            var res = inv.getArgument(0, org.apache.jena.rdf.model.Resource.class);
            String uri = res.getURI();
            if (dsShapeUri.equals(uri)) return dsInstr;
            if (catShapeUri.equals(uri)) return catInstr;
            return new JsonObject().put("k", "unknown");
          });

      // Capture Indexing.init(catalogInstr, datasetInstr) call
      indexingStatic.when(() -> Indexing.init(any(JsonObject.class), any(JsonObject.class)))
          .thenAnswer(inv -> null);

      // --- SUT
      IndexMappingLoader loader = new IndexMappingLoader(vertx, new JsonObject(), profile);
      Future<Void> f = loader.load();

      assertTrue(f.succeeded(), "load() should succeed");

      // Verify Indexing.init invoked with profile-produced instructions:
      indexingStatic.verify(() -> Indexing.init(eq(catInstr), eq(dsInstr)));
    }
  }

  @Test
  void load_profileNull_usesDefaultShapes_callsIndexingInit() {
    // --- Vertx & FS mocks: direct success on conf/... (either path is OK)
    Vertx vertx = mock(Vertx.class, RETURNS_DEEP_STUBS);
    FileSystem fs = mock(FileSystem.class);
    when(vertx.fileSystem()).thenReturn(fs);

    // Build a TTL that Piveau.toModel can parse; we don't need triples, just valid content
    // We'll rely on Piveau.toModel to create a model where we can resolve the DCATAP shapes URIs.
    // Since IndexMappingLoader looks up the resource by URI from the model, ensure the model contains them.
    // Easiest: give bytes and then *after* Piveau.toModel returns, the code calls model.getResource(URI),
    // which will create the resource if missing — so empty TTL works fine.
    Promise<Buffer> confRead = Promise.promise();
    confRead.complete(Buffer.buffer("# minimal"));
    when(fs.readFile(eq("conf/shapes/dcat-ap.hub.shapes.ttl"))).thenReturn(confRead.future());

    // --- Mock static Indexer & Indexing
    JsonObject dsInstr = new JsonObject().put("default", "ds");
    JsonObject catInstr = new JsonObject().put("default", "cat");

    try (MockedStatic<Indexer> indexerStatic = mockStatic(Indexer.class);
        MockedStatic<Indexing> indexingStatic = mockStatic(Indexing.class)) {

      // When invoked with the model's resources for DCATAP shapes, return known JSONs
      indexerStatic.when(() -> Indexer.produceIndexInstructionsFromResource(argThat(r ->
          r != null && (
              DCATAP.DatasetShape.getURI().equals(r.getURI()) ||
                  DCATAP.CatalogShape.getURI().equals(r.getURI())
          )))).thenAnswer(inv -> {
        var res = inv.getArgument(0, org.apache.jena.rdf.model.Resource.class);
        return DCATAP.DatasetShape.getURI().equals(res.getURI()) ? dsInstr : catInstr;
      });

      indexingStatic.when(() -> Indexing.init(any(JsonObject.class), any(JsonObject.class)))
          .thenAnswer(inv -> null);

      // --- SUT (profile == null)
      IndexMappingLoader loader = new IndexMappingLoader(vertx, new JsonObject(), null);
      Future<Void> f = loader.load();

      assertTrue(f.succeeded(), "load() should succeed");

      // Verify Indexing.init called with defaults (catalog first, then dataset)
      indexingStatic.verify(() -> Indexing.init(eq(catInstr), eq(dsInstr)));
    }
  }

  @Test
  void load_shapesReadFails_propagatesFailure() {
    // --- Vertx & FS mocks: both reads fail
    Vertx vertx = mock(Vertx.class, RETURNS_DEEP_STUBS);
    FileSystem fs = mock(FileSystem.class);
    when(vertx.fileSystem()).thenReturn(fs);

    Promise<Buffer> confRead = Promise.promise();
    confRead.fail("conf missing");
    when(fs.readFile(eq("conf/shapes/dcat-ap.hub.shapes.ttl"))).thenReturn(confRead.future());

    Promise<Buffer> fallbackRead = Promise.promise();
    fallbackRead.fail("shapes missing");
    when(fs.readFile(eq("shapes/dcat-ap.hub.shapes.ttl"))).thenReturn(fallbackRead.future());

    // --- SUT
    IndexMappingLoader loader = new IndexMappingLoader(vertx, new JsonObject(), null);
    Future<Void> f = loader.load();

    assertTrue(f.failed(), "load() should fail");
    assertNotNull(f.cause());
    assertTrue(f.cause().getMessage().contains("shapes missing") ||
        f.cause().getMessage().contains("conf missing"));
  }
}
