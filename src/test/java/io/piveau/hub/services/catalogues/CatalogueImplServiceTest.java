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
package io.piveau.hub.services.catalogues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.hub.Constants;
import io.piveau.rdf.Piveau;
import io.piveau.rdf.RDFMimeTypes;
import io.piveau.test.MockTripleStore;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceException;
import java.util.ArrayList;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Testing the catalogue service")
@ExtendWith(VertxExtension.class)
class CatalogueImplServiceTest {

    private CataloguesService cataloguesService;

    private String exampleCatalogue;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {

        DeploymentOptions options = new DeploymentOptions()
                .setWorker(true)
                .setConfig(new JsonObject()
                        .put(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG, MockTripleStore.getDefaultConfig()));

        Checkpoint checkpoint = testContext.checkpoint(3);

        new MockTripleStore().deploy(vertx).onSuccess(v -> checkpoint.flag()).onFailure(testContext::failNow);

        vertx.deployVerticle(CataloguesServiceVerticle.class.getName(), options, ar -> {
            if (ar.succeeded()) {
                cataloguesService = CataloguesService.createProxy(vertx, CataloguesService.SERVICE_ADDRESS);
                checkpoint.flag();
            } else {
                testContext.failNow(ar.cause());
            }
        });

        vertx.fileSystem().readFile("example_empty_catalog.ttl", ar -> {
            if (ar.succeeded()) {
                exampleCatalogue = ar.result().toString();
                checkpoint.flag();
            } else {
                testContext.failNow(ar.cause());
            }
        });

        vertx.eventBus().consumer("io.piveau.hub.index.queue", message -> {
            message.reply(new JsonObject());
        });
        vertx.eventBus().consumer("io.piveau.hub.translationservice.queue", message -> {
            message.reply(new JsonObject());
        });
    }

    @Test
    @DisplayName("Create an example catalogue")
    void testCreateExampleCatalog(Vertx vertx, VertxTestContext testContext) {
        cataloguesService.putCatalogue("create-test-catalogue", exampleCatalogue, RDFMimeTypes.TURTLE)
                .onSuccess(result -> {
                    testContext.verify(() -> assertEquals("created", result));
                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Receive an example catalogue")
    void testGetExampleCatalog(Vertx vertx, VertxTestContext testContext) {
        cataloguesService.putCatalogue("get-test-catalogue", exampleCatalogue, RDFMimeTypes.TURTLE)
                .compose(result -> cataloguesService.getCatalogue("get-test-catalogue", RDFMimeTypes.TURTLE))
                .onSuccess(result -> testContext.verify(() -> {
                            assertNotNull(result);
                            Model jenaModel = Piveau.toModel(result.getBytes(), RDFMimeTypes.TURTLE);
                            assertNotNull(jenaModel);

                            Resource cat = jenaModel.getResource(DCATAPUriSchema.applyFor("get-test-catalogue").getCatalogueUriRef());
                            assertNotNull(cat);
                            assertNotNull(cat.getProperty(RDF.type));
                            assertEquals(DCAT.Catalog, cat.getProperty(RDF.type).getObject());
                            testContext.completeNow();
                        })
                )
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Delete an example catalogue")
    void testDeleteExampleCatalog(Vertx vertx, VertxTestContext testContext) {
        String datasetID = "delete-test-catalogue";
        cataloguesService.putCatalogue(datasetID, exampleCatalogue, RDFMimeTypes.TURTLE)
                .compose(result -> cataloguesService.deleteCatalogue(datasetID))
                .compose(v -> cataloguesService.getCatalogue(datasetID, RDFMimeTypes.TURTLE))
                .onSuccess(content -> testContext.failNow("Still exists"))
                .onFailure(cause -> testContext.verify(() -> {
                    assertTrue(cause instanceof ServiceException);
                    assertEquals(404, ((ServiceException) cause).failureCode());
                    testContext.completeNow();
                }));
    }

    @Test
    @DisplayName("Delete an non existing catalogue")
    void testDeleteMissingCatalog(Vertx vertx, VertxTestContext testContext) {
        String datasetID = "delete-missing-test-catalogue";
        testContext.assertFailure(cataloguesService.deleteCatalogue(datasetID));
        testContext.completeNow();
    }

    @Test
    @DisplayName("Counting zero catalogues")
    void testCountZeroCatalogs(Vertx vertx, VertxTestContext testContext) {
        cataloguesService.listCatalogues(RDFMimeTypes.NTRIPLES, "metadata", 0, 100)
                .onSuccess(content -> testContext.verify(() -> {
                    Model model = Piveau.toModel(content.getBytes(), RDFMimeTypes.NTRIPLES);
                    ResIterator it = model.listResourcesWithProperty(RDF.type, DCAT.Catalog);
                    assertTrue(it.toList().isEmpty());
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Counting one catalogue")
    void testCountOneCatalog(Vertx vertx, VertxTestContext testContext) {
        String datasetID = "count-one-test-catalogue";
        cataloguesService.putCatalogue(datasetID, exampleCatalogue, RDFMimeTypes.TURTLE)
                .compose(result -> cataloguesService.listCatalogues(RDFMimeTypes.NTRIPLES, "metadata", 0, 100))
                .onSuccess(content -> testContext.verify(() -> {
                    Model model = Piveau.toModel(content.getBytes(), RDFMimeTypes.NTRIPLES);
                    ResIterator it = model.listResourcesWithProperty(RDF.type, DCAT.Catalog);
                    assertEquals(1, it.toList().size());
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Counting two catalogues")
    void testCountTwoCatalogs(Vertx vertx, VertxTestContext testContext) {
        String datasetID = "count-two-test-catalogue";
        cataloguesService.putCatalogue(datasetID, exampleCatalogue, RDFMimeTypes.TURTLE)
                .compose(result -> cataloguesService.putCatalogue(datasetID + "2", exampleCatalogue, RDFMimeTypes.TURTLE))
                .compose(result -> cataloguesService.listCatalogues("application/json", "identifiers", 0, 100))
                .onSuccess(content -> testContext.verify(() -> {
                    JsonArray list = new JsonArray(content);
                    assertEquals(2, list.size());
                    testContext.completeNow();
                    Thread.sleep(1); // Don't know why, but this prevents exception
                }))
                .onSuccess(testContext::failNow);
    }

    @Test
    @DisplayName("Counting one thousand catalogues")
    void testCountOneThousandCatalogs(Vertx vertx, VertxTestContext testContext) {
        String datasetID = "count-hundred-test-catalogues";

        int numberToCount = 1000;

        ArrayList<Future<String>> futureList = new ArrayList<>();

        for (int i = 0; i < numberToCount; i++) {
            Promise<String> promise = Promise.promise();
            futureList.add(promise.future());
            cataloguesService.putCatalogue(datasetID + "-" + i, exampleCatalogue, RDFMimeTypes.TURTLE).onComplete(promise);
        }

        CompositeFuture.join(new ArrayList<>(futureList))
                .compose(j -> cataloguesService.listCatalogues("application/json", "identifiers", 0, 1500))
                .onSuccess(content -> testContext.verify(() -> {
                    JsonArray list = new JsonArray(content);
                    assertEquals(numberToCount, list.size());
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Put catalogue with invalid RDF format")
    void testPutCatalogueWithInvalidFormat(Vertx vertx, VertxTestContext testContext) {
        String invalidRDF = "invalid content that cannot be parsed as RDF";

        cataloguesService.putCatalogue("invalid-catalogue", invalidRDF, RDFMimeTypes.TURTLE)
                .onSuccess(res -> testContext.failNow("Expected failure but got success"))
                .onFailure(err -> testContext.verify(() -> {
                    assertTrue(err instanceof ServiceException);
                    assertEquals(400, ((ServiceException) err).failureCode());
                    testContext.completeNow();
                }));
    }

    @Test
    @DisplayName("List catalogues with valueType = uriRefs")
    void testListCataloguesUriRefs(Vertx vertx, VertxTestContext testContext) {
        cataloguesService.putCatalogue("list-uriRef-catalogue", exampleCatalogue, RDFMimeTypes.TURTLE)
                .compose(v -> cataloguesService.listCatalogues("application/json", "uriRefs", 0, 10))
                .onSuccess(content -> testContext.verify(() -> {
                    JsonArray list = new JsonArray(content);
                    assertFalse(list.isEmpty());
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("List catalogues with valueType = originalIds")
    void testListCataloguesOriginalIds(Vertx vertx, VertxTestContext testContext) {
        cataloguesService.putCatalogue("list-originalId-catalogue", exampleCatalogue, RDFMimeTypes.TURTLE)
                .compose(v -> cataloguesService.listCatalogues("application/json", "originalIds", 0, 10))
                .onSuccess(content -> testContext.verify(() -> {
                    JsonArray list = new JsonArray(content);
                    assertFalse(list.isEmpty());
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("List catalogues with offset greater than size")
    void testListCataloguesWithHighOffset(Vertx vertx, VertxTestContext testContext) {
        cataloguesService.putCatalogue("offset-test-catalogue", exampleCatalogue, RDFMimeTypes.TURTLE)
                .compose(v -> cataloguesService.listCatalogues("application/json", "uriRefs", 100, 10))
                .onSuccess(content -> testContext.verify(() -> {
                    JsonArray list = new JsonArray(content);
                    assertEquals(0, list.size());
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

  @Test
  @DisplayName("Put hidden catalogue (skip indexing)")
  void testPutHiddenCatalogue(VertxTestContext testContext) {
    String hiddenCatalogue = """
        @prefix edp: <http://data.europa.eu/piveau/edp#> .
        @prefix dcat: <http://www.w3.org/ns/dcat#> .
        @prefix dct: <http://purl.org/dc/terms/> .
        @prefix ex: <http://example.org/catalog/> .
        
        ex:hidden-catalogue a dcat:Catalog ;
            dct:language  <http://publications.europa.eu/resource/authority/language/ENG> ;
            dct:spatial <http://publications.europa.eu/resource/authority/country/ESP> ;
            edp:visibility edp:hidden .
        """;

    cataloguesService.putCatalogue("hidden-catalogue", hiddenCatalogue, RDFMimeTypes.TURTLE)
        .onSuccess(result -> testContext.verify(() -> {
          assertEquals("created", result);
          // No indexing is triggered, we rely on coverage of onFailure/onSuccess handling
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

    @Test
    @DisplayName("Update an existing catalogue")
    void testUpdateExistingCatalogue(Vertx vertx, VertxTestContext testContext) {
        String catalogueId = "update-test-catalogue";
        cataloguesService.putCatalogue(catalogueId, exampleCatalogue, RDFMimeTypes.TURTLE)
                .compose(v -> cataloguesService.putCatalogue(catalogueId, exampleCatalogue, RDFMimeTypes.TURTLE))
                .onSuccess(result -> testContext.verify(() -> {
                    assertEquals("updated", result);
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

  @Test
  @DisplayName("Delete a catalogue with datasets")
  void testDeleteCatalogueWithDatasets(VertxTestContext testContext) {
    String datasetId = "catalogue-with-datasets";
    String withDatasets = """
        @prefix dct: <http://purl.org/dc/terms/> .
        @prefix dcat: <http://www.w3.org/ns/dcat#> .
        @prefix ex: <http://example.org/> .
        
        ex:%1$s a dcat:Catalog ;
            dct:language  <http://publications.europa.eu/resource/authority/language/ENG> ;
            dct:spatial <http://publications.europa.eu/resource/authority/country/ESP> ;
            dcat:dataset ex:ds1 ;
            dcat:record ex:rec1 .
        
        ex:ds1 a dcat:Dataset .
        ex:rec1 a dcat:Resource .
        """.formatted(datasetId);

    cataloguesService.putCatalogue(datasetId, withDatasets, RDFMimeTypes.TURTLE)
        .compose(v -> cataloguesService.deleteCatalogue(datasetId))
        .onSuccess(v -> testContext.completeNow())
        .onFailure(testContext::failNow);
  }

    @Test
    @DisplayName("Indexing fails but catalogue is still created")
    void testIndexingFailure(Vertx vertx, VertxTestContext testContext) {
        vertx.eventBus().consumer("io.piveau.hub.index.queue", msg -> {
            msg.fail(500, "Simulated failure");
        });

        String catalogueId = "index-fail-catalogue";
        cataloguesService.putCatalogue(catalogueId, exampleCatalogue, RDFMimeTypes.TURTLE)
                .onSuccess(res -> testContext.verify(() -> {
                    assertEquals("created", res);
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("List catalogues with offset + limit > size")
    void testListOffsetPlusLimit(Vertx vertx, VertxTestContext testContext) {
        cataloguesService.putCatalogue("partial-list-cat", exampleCatalogue, RDFMimeTypes.TURTLE)
                .compose(v -> cataloguesService.listCatalogues("application/json", "uriRefs", 0, 5))
                .onSuccess(content -> testContext.verify(() -> {
                    JsonArray list = new JsonArray(content);
                    assertEquals(1, list.size());
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }


}
