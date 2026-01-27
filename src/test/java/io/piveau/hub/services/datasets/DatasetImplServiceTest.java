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
package io.piveau.hub.services.datasets;

import static io.piveau.hub.Constants.ENV_PIVEAU_HUB_SEARCH_SERVICE;
import static io.piveau.hub.Constants.ENV_PIVEAU_HUB_VALIDATOR;
import static io.piveau.hub.Constants.ENV_PIVEAU_TRANSLATION_SERVICE;
import static io.piveau.hub.Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.dcatap.TripleStore;
import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.hub.services.index.IndexService;
import io.piveau.hub.services.metrics.MetricsService;
import io.piveau.hub.services.metrics.MetricsServiceVerticle;
import io.piveau.hub.services.translation.TranslationService;
import io.piveau.hub.util.DataUploadConnector;
import io.piveau.hub.util.IndexMappingLoader;
import io.piveau.pipe.PipeLauncher;
import io.piveau.rdf.Piveau;
import io.piveau.rdf.RDFMimeTypes;
import io.piveau.test.MockTripleStore;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("DatasetImplServiceTest - Testing the datasets service")
@ExtendWith(VertxExtension.class)
class DatasetImplServiceTest {
    private static String exampleDataset;
    private final String catalogueID = "test-catalog";
    private DatasetsService datasetsService;
    private MetricsService metricsService;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        DeploymentOptions options = new DeploymentOptions()
            .setWorker(true)
            .setConfig(new JsonObject()
                .put(ENV_PIVEAU_TRIPLESTORE_CONFIG, MockTripleStore.getDefaultConfig())
                .put(ENV_PIVEAU_HUB_VALIDATOR, new JsonObject()
                    .put("url", "localhost")
                    .put("enabled", true)
                )
                .put(ENV_PIVEAU_HUB_SEARCH_SERVICE, new JsonObject().put("enabled", true))
                .put(ENV_PIVEAU_TRANSLATION_SERVICE, new JsonObject().put("enable", true)));

        Checkpoint checkpoint = testContext.checkpoint(4);

        vertx.fileSystem().readFile("example_external_dataset.ttl", readResult -> {
            if (readResult.succeeded()) {
                exampleDataset = readResult.result().toString();
                checkpoint.flag();
            } else {
                testContext.failNow(readResult.cause());
            }
        });

      // Initialize indexing
      IndexMappingLoader indexMappingLoader = new IndexMappingLoader(vertx, new JsonObject(), null);
      indexMappingLoader.load();

      new MockTripleStore()
          .loadGraph("https://piveau.io/id/catalogue/test-catalog", "example_empty_catalog.ttl")
          .loadGraph("https://piveau.io/id/catalogue/test-catalog", "example_duplicate_record.ttl")
          .deploy(vertx)
          .onSuccess(v -> checkpoint.flag())
          .onFailure(testContext::failNow);

        vertx.deployVerticle(DatasetsServiceVerticle.class.getName(), options, ar -> {
            if (ar.succeeded()) {
                datasetsService = DatasetsService.createProxy(vertx, DatasetsService.SERVICE_ADDRESS);
                checkpoint.flag();
            } else {
                testContext.failNow(ar.cause());
            }
        });
        vertx.deployVerticle(MetricsServiceVerticle.class.getName(), options, ar -> {
            if (ar.succeeded()) {
                metricsService = MetricsService.createProxy(vertx, MetricsService.SERVICE_ADDRESS);
                checkpoint.flag();
            } else {
                testContext.failNow(ar.cause());
            }
        });

      vertx.eventBus()
          .consumer(IndexService.SERVICE_ADDRESS, message -> message.reply(new JsonObject()));
      vertx.eventBus().<JsonObject>consumer(TranslationService.SERVICE_ADDRESS,
          message -> message.reply(message.body().getJsonObject("helper"))
      );
    }

  @Test
  @DisplayName("listCatalogueDatasets - non-existing catalogue should return 404")
  void testListCatalogueDatasetsMissingCatalogue(VertxTestContext testContext) {
    datasetsService.listCatalogueDatasets("application/json", "originalIds", "missing-cat", 10, 0)
        .onComplete(testContext.failing(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          assertEquals(404, ((ServiceException) err).failureCode());
          assertEquals("Catalogue not found", err.getMessage());
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("listCatalogueDatasets - identifiers returns normalized IDs")
  void testListCatalogueDatasetsIdentifiers(VertxTestContext testContext) {
    String id1 = "lcd-identifiers-1";
    String id2 = "lcd-identifiers-2";

    datasetsService.putDatasetOrigin(id1, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
        .compose(r -> datasetsService.putDatasetOrigin(id2, exampleDataset, RDFMimeTypes.TURTLE,
            catalogueID, false))
        .compose(r -> datasetsService.listCatalogueDatasets("application/json", "identifiers",
            catalogueID, 50, 0))
        .onSuccess(json -> testContext.verify(() -> {
          JsonArray arr = new JsonArray(json);
          assertNotNull(arr);
          assertTrue(arr.contains(DCATAPUriSchema.applyFor(id1).getId()));
          assertTrue(arr.contains(DCATAPUriSchema.applyFor(id2).getId()));
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

  @Test
  @DisplayName("listCatalogueDatasets - uriRefs returns dataset URIs")
  void testListCatalogueDatasetsUriRefs(VertxTestContext testContext) {
    String id1 = "lcd-uri-refs-1";
    String id2 = "lcd-uri-refs-2";

    datasetsService.putDatasetOrigin(id1, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
        .compose(r -> datasetsService.putDatasetOrigin(id2, exampleDataset, RDFMimeTypes.TURTLE,
            catalogueID, false))
        .compose(
            r -> datasetsService.listCatalogueDatasets("application/json", "uriRefs", catalogueID,
                50, 0))
        .onSuccess(json -> testContext.verify(() -> {
          JsonArray arr = new JsonArray(json);
          assertNotNull(arr);
          assertTrue(arr.contains(DCATAPUriSchema.applyFor(id1).getDatasetUriRef()));
          assertTrue(arr.contains(DCATAPUriSchema.applyFor(id2).getDatasetUriRef()));
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

  @Test
  @DisplayName("listCatalogueDatasets metadata returns presentation content")
  void testListCatalogueDatasetsMetadata(VertxTestContext testContext) {
    String id = "metadata-test-dataset";
    datasetsService.putDatasetOrigin(id, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
        .compose(
            r -> datasetsService.listCatalogueDatasets(RDFMimeTypes.TURTLE, "metadata", catalogueID,
                10, 0))
        .onSuccess(content -> testContext.verify(() -> {
          assertNotNull(content);
          assertFalse(content.isEmpty());
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

  @Test
  @DisplayName("listCatalogueDatasets - metadata respects limit/offset")
  void testListCatalogueDatasetsMetadataPagination(VertxTestContext testContext) {
    String a = "lcd-meta-page-a";
    String b = "lcd-meta-page-b";
    String c = "lcd-meta-page-c";

    datasetsService.putDatasetOrigin(a, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
        .compose(r -> datasetsService.putDatasetOrigin(b, exampleDataset, RDFMimeTypes.TURTLE,
            catalogueID, false))
        .compose(r -> datasetsService.putDatasetOrigin(c, exampleDataset, RDFMimeTypes.TURTLE,
            catalogueID, false))
        .compose(r -> datasetsService.listCatalogueDatasets("application/ld+json", "metadata",
            catalogueID, 2, 0))
        .compose(
            firstPage -> datasetsService.listCatalogueDatasets("application/ld+json", "metadata",
                catalogueID, 2, 2))
        .onSuccess(secondPage -> testContext.verify(() -> {
          assertNotNull(secondPage);
          assertFalse(secondPage.isEmpty());
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

  @Test
  @DisplayName("listCatalogueDatasets - unknown valueType should fail with 400")
  void testListCatalogueDatasetsUnknownValueType(VertxTestContext testContext) {
    datasetsService.listCatalogueDatasets("application/json", "bogus", catalogueID, 10, 0)
        .onComplete(testContext.failing(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          assertEquals(400, ((ServiceException) err).failureCode());
          assertEquals("Unknown value type", err.getMessage());
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Update example dataset")
  void testUpdateExampleDataset(VertxTestContext testContext) {
        String datasetID = "update-test-dataset";

        datasetsService.putDatasetOrigin(datasetID, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
            .compose(result -> datasetsService.getDatasetOrigin(datasetID, catalogueID,
                RDFMimeTypes.TURTLE))
            .compose(result -> datasetsService.putDatasetOrigin(datasetID, exampleDataset,
                RDFMimeTypes.TURTLE, catalogueID, false))
            .onSuccess(result -> testContext.failNow("Expected failure"))
            .onFailure(cause -> testContext.verify(() -> {
              assertEquals("Dataset is up to date", cause.getMessage());
              testContext.completeNow();
            }));
    }

  @Test
  @DisplayName("putDatasetOrigin - records exist & hash different -> update path (status=updated)")
  void testPutDatasetOrigin_Update_WithChange_ReturnsUpdated(VertxTestContext testContext) {
    String originalId = "origin-hash-different";

    datasetsService.putDatasetOrigin(originalId, exampleDataset, RDFMimeTypes.TURTLE, catalogueID,
            false)
        .compose(created -> datasetsService.getDatasetOrigin(originalId, catalogueID,
            RDFMimeTypes.TURTLE))
        .compose(current -> {
          String mutated = current.replace("Example External Dataset",
              "Example External Dataset (changed)");
          return datasetsService.putDatasetOrigin(originalId, mutated, RDFMimeTypes.TURTLE,
              catalogueID, false);
        })
        .onSuccess(result -> testContext.verify(() -> {
          assertNotNull(result);
          assertEquals("updated", result.getString("status"));
          assertEquals(DCATAPUriSchema.applyFor(originalId).getId(), result.getString("id"));
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

  @Test
  @DisplayName("putDatasetOrigin - duplicates present -> removeDuplicates() empties list -> 500")
  void testPutDatasetOrigin_RemoveDuplicatesEmptiesList(VertxTestContext testContext) {
    String id = "dup-target";

    datasetsService.putDatasetOrigin(id, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
        .onSuccess(res -> testContext.failNow("Expected 500 failure due to duplicates removal"))
        .onFailure(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          ServiceException se = (ServiceException) err;
          assertEquals(500, se.failureCode());
          assertEquals("Due to unclear duplicate detection removed completely", se.getMessage());
          testContext.completeNow();
        }));
  }

  @Test
  @DisplayName("putDatasetOrigin - foreign record (non-DCATAP record URI) -> 500 Foreign catalogue record")
  void testPutDatasetOrigin_ForeignRecordDetected(VertxTestContext testContext) {
    String id = "foreign-target";

    datasetsService.putDatasetOrigin(id, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
        .onSuccess(res -> testContext.failNow("Expected 500 failure due to foreign record"))
        .onFailure(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          ServiceException se = (ServiceException) err;
          assertEquals(500, se.failureCode());
          assertEquals("Foreign catalogue record with dct:identifier detected", se.getMessage());
          testContext.completeNow();
        }));
  }

  @Test
    @DisplayName("Create an example dataset")
  void testCreateExampleDataset(VertxTestContext testContext) {
        String datasetID = "create-test-dataset";

        datasetsService.putDatasetOrigin(datasetID, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
                .onSuccess(result -> testContext.verify(() -> {
                    assertEquals("created", result.getString("status", ""));
                    String location = result.getString(HttpHeaders.LOCATION.toString());
                    assertNotNull(location);
                    assertEquals(DCATAPUriSchema.applyFor(datasetID).getDatasetUriRef(), location);
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Delete an example dataset (inclusively Dqv)")
    void testDeleteExampleDataset(Vertx vertx, VertxTestContext testContext) {
        String datasetID = "delete-test-dataset";

      datasetsService.putDatasetOrigin(datasetID, exampleDataset, RDFMimeTypes.TURTLE, catalogueID,
              false)
          .onSuccess(result -> {
            Buffer buffer = vertx.fileSystem().readFileBlocking("example_metric_1.ttl");
            metricsService.putMetrics("https://piveau.io/set/data/delete-test-dataset",
                    buffer.toString(), RDFMimeTypes.TRIG)
                .onSuccess(
                    result2 -> datasetsService.deleteDatasetOrigin(datasetID, catalogueID)
                        .compose(v -> datasetsService.getDatasetOrigin(datasetID, catalogueID,
                            RDFMimeTypes.TURTLE))
                        .onSuccess(status -> testContext.failNow("Dataset still available"))
                        .onFailure(cause -> {
                          testContext.verify(() -> {
                            assertInstanceOf(ServiceException.class, cause);
                            assertEquals(404, ((ServiceException) cause).failureCode());
                          });
                          metricsService.getMetrics(datasetID, false, RDFMimeTypes.TURTLE)
                              .onSuccess(
                                  s -> testContext.failNow(new Throwable("Metrics still available"))
                              )
                              .onFailure(cause2 -> {
                                testContext.verify(() -> {
                                  assertNotNull(cause2);
                                  assertInstanceOf(ServiceException.class, cause2);
                                  assertEquals(404, ((ServiceException) cause2).failureCode());
                                });
                                testContext.completeNow();
                              });
                        }))
                .onFailure(testContext::failNow);
          })
          .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Receive an example dataset")
    void testGetExampleDataset(VertxTestContext testContext) {
        String datasetId = "get-test-dataset";
        datasetsService.putDatasetOrigin(datasetId, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
                .compose(result -> datasetsService.getDatasetOrigin(datasetId, catalogueID, RDFMimeTypes.TURTLE))
                .onSuccess(content -> testContext.verify(() -> {
                    assertNotNull(content);
                    DatasetHelper helper = DatasetHelper.create(content, RDFMimeTypes.TURTLE);
                    assertEquals(datasetId, helper.piveauId());
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

  @Test
  @DisplayName("getDatasetOrigin - non-existing dataset should return 404")
  void testGetDatasetOriginNotFound(VertxTestContext testContext) {
    datasetsService.getDatasetOrigin("does-not-exist", catalogueID, RDFMimeTypes.TURTLE)
        .onComplete(testContext.failing(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          assertEquals(404, ((ServiceException) err).failureCode());
          assertEquals("Dataset Not Found", err.getMessage());
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Receive an example dataset from normalized id")
  void testGetExampleDatasetNormalizedID(VertxTestContext testContext) {
        String datasetId = "test-Get-normalized-Dataset .id";
        datasetsService.putDatasetOrigin(datasetId, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
                .compose(result -> datasetsService.getDataset(DCATAPUriSchema.applyFor(datasetId).getId(), RDFMimeTypes.TURTLE))
                .onSuccess(content -> testContext.verify(() -> {
                    Model jenaModel = Piveau.toModel(content.getBytes(), RDFMimeTypes.TURTLE);
                    assertNotNull(jenaModel);

                    Resource cat = jenaModel.getResource(DCATAPUriSchema.applyFor(datasetId).getDatasetUriRef());
                    assertNotNull(cat);
                    assertNotNull(cat.getProperty(RDF.type));
                    assertEquals(DCAT.Dataset, cat.getProperty(RDF.type).getObject());
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Delete a non existing dataset")
    void testDeleteMissingDataset(VertxTestContext testContext) {
        String datasetId = "delete-missing-test-dataset";
        testContext.assertFailure(datasetsService.deleteDatasetOrigin(datasetId, catalogueID));
        testContext.completeNow();
    }

    @Test
    @DisplayName("Counting three dataset")
    void testCountThreeDataset(VertxTestContext testContext) {
      String datasetID = "count-three-test-dataset";
        datasetsService.putDatasetOrigin(datasetID, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
                .compose(result -> datasetsService.listCatalogueDatasets("application/json", "originalIds", catalogueID, 20, 0))
                .onSuccess(content -> {
                    JsonArray result = new JsonArray(content);
                    testContext.verify(() -> {
                      assertNotNull(result);
                      assertFalse(result.isEmpty());
                      assertEquals(3, result.size());
                      assertEquals(datasetID, result.getString(result.size() - 1));
                      testContext.completeNow();
                    });
                })
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Counting four datasets")
    void testCountFourDatasetsInOneCat(VertxTestContext testContext) {
      String datasetID = "count-four-test-dataset";
        datasetsService.putDatasetOrigin(datasetID, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
                .compose(result -> datasetsService.putDatasetOrigin(datasetID + "2", exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false))
                .compose(result -> datasetsService.listCatalogueDatasets("application/json", "originalIds", catalogueID, 20, 0))
                .onSuccess(content -> {
                    JsonArray result = new JsonArray(content);
                    testContext.verify(() -> {
                      assertNotNull(result);
                      assertFalse(result.isEmpty());
                      assertEquals(4, result.size());
                      testContext.completeNow();
                    });
                })
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Put dataset with health data")
    void testPutDatasetOriginHealthData(VertxTestContext testContext) {
        String datasetId = "health-dataset";
        datasetsService.putDatasetOriginHealthData(datasetId, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false, false)
                .onSuccess(result -> testContext.verify(() -> {
                    assertEquals("created", result.getString("status"));
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

  @Test
  @DisplayName("putDatasetOriginHealthData - update path with missing dataset returns 404")
  void testPutDatasetOriginHealthDataUpdateMissing(VertxTestContext testContext) {
    String id = "health-update-missing";
    datasetsService.putDatasetOriginHealthData(id, exampleDataset, RDFMimeTypes.TURTLE, catalogueID,
            false, true)
        .onComplete(testContext.failing(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          assertEquals(404, ((ServiceException) err).failureCode());
          assertEquals("Dataset not found", err.getMessage());
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("putDatasetOriginHealthData - records exist & hash equal -> 304 up to date")
  void testPutDatasetOriginHealthData_Update_NoChange_Returns304(VertxTestContext testContext) {
    String originalId = "health-hash-same";
    // 1) Create dataset via HealthData (isDatasetUpdate=false)
    // 2) Call again with isDatasetUpdate=true and identical content -> should hit else-branch and return 304
    datasetsService.putDatasetOriginHealthData(originalId, exampleDataset, RDFMimeTypes.TURTLE,
            catalogueID, false, false)
        .compose(created -> datasetsService.putDatasetOriginHealthData(originalId, exampleDataset,
            RDFMimeTypes.TURTLE, catalogueID, false, true))
        .onSuccess(res -> testContext.failNow("Expected 304 ServiceException"))
        .onFailure(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          assertEquals(304, ((ServiceException) err).failureCode());
          assertEquals("Dataset is up to date", err.getMessage());
          testContext.completeNow();
        }));
  }

  @Test
  @DisplayName("putDatasetOriginHealthData - records exist & hash different -> update path (status=updated)")
  void testPutDatasetOriginHealthData_Update_WithChange_ReturnsUpdated(
      VertxTestContext testContext) {
    String originalId = "health-hash-different";

    // Create once
    datasetsService.putDatasetOriginHealthData(originalId, exampleDataset, RDFMimeTypes.TURTLE,
            catalogueID, false, false)
        // Fetch the stored dataset as Turtle so we can modify a literal to change the hash
        .compose(created -> datasetsService.getDatasetOrigin(originalId, catalogueID,
            RDFMimeTypes.TURTLE))
        .compose(current -> {
          // Change a title label to alter the hash; keep it valid Turtle
          String mutated = current.replace("Example External Dataset",
              "Example External Dataset (modified)");
          return datasetsService.putDatasetOriginHealthData(originalId, mutated,
              RDFMimeTypes.TURTLE, catalogueID, false, true);
        })
        .onSuccess(result -> testContext.verify(() -> {
          assertNotNull(result);
          assertEquals("updated", result.getString("status"));
          assertEquals(DCATAPUriSchema.applyFor(originalId).getId(), result.getString("id"));
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

  @Test
  @DisplayName("putDatasetOriginHealthData - duplicates present -> removeDuplicates() empties list -> 500")
  void testPutDatasetOriginHealthData_RemoveDuplicatesEmptiesList(VertxTestContext testContext) {
    String id = "dup-target";

    datasetsService.putDatasetOriginHealthData(id, exampleDataset, RDFMimeTypes.TURTLE, catalogueID,
            false, true)
        .onSuccess(res -> testContext.failNow("Expected 500 failure due to duplicates removal"))
        .onFailure(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          ServiceException se = (ServiceException) err;
          assertEquals(500, se.failureCode());
          assertEquals("Due to unclear duplicate detection removed completely", se.getMessage());
          testContext.completeNow();
        }));
  }

  @Test
  @DisplayName("putDatasetOriginHealthData - foreign record (non-DCATAP record URI) -> 500 Foreign catalogue record")
  void testPutDatasetOriginHealthData_ForeignRecordDetected(VertxTestContext testContext) {
    String id = "foreign-target";

    datasetsService.putDatasetOriginHealthData(id, exampleDataset, RDFMimeTypes.TURTLE, catalogueID,
            false, true)
        .onSuccess(res -> testContext.failNow("Expected 500 failure due to foreign record"))
        .onFailure(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          ServiceException se = (ServiceException) err;
          assertEquals(500, se.failureCode());
          assertEquals("Foreign catalogue record with dct:identifier detected", se.getMessage());
          testContext.completeNow();
        }));
  }

    @Test
    @DisplayName("Post dataset creation")
    void testPostDataset(VertxTestContext testContext) {
        datasetsService.postDataset(exampleDataset, RDFMimeTypes.TURTLE, catalogueID, true)
                .onSuccess(result -> testContext.verify(() -> {
                    assertNotNull(result.getString("status"));
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Get record for dataset")
    void testGetRecordForDataset(VertxTestContext testContext) {
        String datasetId = "record-dataset";
        datasetsService.putDatasetOrigin(datasetId, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
                .compose(res -> datasetsService.getRecord(datasetId, RDFMimeTypes.TURTLE))
                .onSuccess(content -> testContext.verify(() -> {
                    assertNotNull(content);
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

  @Test
  @DisplayName("getRecord - non-existing record should return 404")
  void testGetRecordNotFound(VertxTestContext testContext) {
    datasetsService.getRecord("no-record-dataset", RDFMimeTypes.TURTLE)
        .onComplete(testContext.failing(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          assertEquals(404, ((ServiceException) err).failureCode());
          assertEquals("Catalogue record not found", err.getMessage());
          testContext.completeNow();
        })));
  }

    @Test
    @DisplayName("putDataset - invalid RDF content should fail with 400")
    void testPutDatasetInvalidContent(VertxTestContext testContext) {
        String datasetId = "put-invalid-dataset";
        String invalidContent = "invalid RDF content";

        datasetsService.putDataset(datasetId, invalidContent, RDFMimeTypes.TURTLE)
                .onSuccess(res -> testContext.failNow("Expected failure"))
                .onFailure(err -> testContext.verify(() -> {
                  assertInstanceOf(ServiceException.class, err);
                  assertEquals(400, ((ServiceException) err).failureCode());
                  testContext.completeNow();
                }));
    }

  @Test
  @DisplayName("putDataset - 404 when dataset graph does not exist")
  void testPutDatasetNotFound(VertxTestContext testContext) {
    String originalId = "put-missing-ds";
    String normalizedId = DCATAPUriSchema.applyFor(originalId).getId();
    String datasetUri = DCATAPUriSchema.applyFor(originalId).getDatasetUriRef();

    // Minimal RDF that DatasetHelper can parse and that yields a DCATAP uriSchema
    String minimalTtl = """
        @prefix dcat: <http://www.w3.org/ns/dcat#> .
        <%s> a dcat:Dataset .
        """.formatted(datasetUri);

    datasetsService.putDataset(normalizedId, minimalTtl, RDFMimeTypes.TURTLE)
        .onComplete(testContext.failing(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          assertEquals(404, ((ServiceException) err).failureCode());
          assertEquals("Dataset not found", err.getMessage());
          testContext.completeNow();
        })));
  }


  @Test
  @DisplayName("putDataset - updates an existing dataset and returns status=updated")
  void testPutDatasetUpdatesExisting(VertxTestContext testContext) {
    String originalId = "put-update-existing";
    String normalizedId = DCATAPUriSchema.applyFor(originalId).getId();

    // 1) Create the dataset via putDatasetOrigin
    // 2) Retrieve the dataset (as Turtle) to use as the update payload
    // 3) Call putDataset and assert we get an "updated" response
    datasetsService.putDatasetOrigin(originalId, exampleDataset, RDFMimeTypes.TURTLE, catalogueID,
            false)
        .compose(created -> datasetsService.getDataset(normalizedId, RDFMimeTypes.TURTLE))
        .compose(currentContent -> datasetsService.putDataset(normalizedId, currentContent,
            RDFMimeTypes.TURTLE))
        .onSuccess(result -> testContext.verify(() -> {
          assertNotNull(result);
          assertEquals("updated", result.getString("status"));
          assertEquals(normalizedId, result.getString("id"));
          // For "updated" responses, no Location header is returned
          assertFalse(result.containsKey(HttpHeaders.LOCATION.toString()));
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }


  @Test
  @DisplayName("listDatasets - valueType=originalIds should fail when catalogueId is not set")
  void testListDatasetsOriginalIdsFails(VertxTestContext testContext) {
    datasetsService.listDatasets("application/json", "originalIds", 10, 0, false, null, false)
        .onComplete(testContext.failing(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          assertEquals(400, ((ServiceException) err).failureCode());
          assertEquals("Value type originalIds only allowed when catalogueId is set",
              err.getMessage());
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("listDatasets identifiers & uriRefs return expected sizes")
  void testListDatasetsIdentifiersAndUriRefs(VertxTestContext testContext) {
    String id1 = "list-ds-1";
    String id2 = "list-ds-2";

    datasetsService.putDatasetOrigin(id1, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
        .compose(r -> datasetsService.putDatasetOrigin(id2, exampleDataset, RDFMimeTypes.TURTLE,
            catalogueID, false))
        .compose(
            r -> datasetsService.listDatasets("application/json", "identifiers", 50, 0, false, null,
                false))
        .compose(json -> {
          JsonArray ids = new JsonArray(json);
          assertTrue(ids.size() >= 2); // at least our two (tests may add others)
          return datasetsService.listDatasets("application/json", "uriRefs", 50, 0, false, null,
              false);
        })
        .onSuccess(json -> testContext.verify(() -> {
          JsonArray uris = new JsonArray(json);
          assertTrue(uris.size() >= 2);
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

  @Test
  @DisplayName("listDatasets - metadata without Hydra")
  void testListDatasetsMetadataNoHydra(VertxTestContext testContext) {
    String id = "ld-metadata-no-hydra-1";

    datasetsService.putDatasetOrigin(id, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
        .compose(
            r -> datasetsService.listDatasets(RDFMimeTypes.TURTLE, "metadata", 50, 0, false, null,
                false))
        .onSuccess(content -> testContext.verify(() -> {
          assertNotNull(content);
          assertFalse(content.isEmpty());
          // Should serialize one or more datasets as RDF
          assertTrue(content.contains("dcat:Dataset") || content.contains("@prefix"));
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

  @Test
  @DisplayName("listDatasets - metadata with Hydra collection (JSON-LD) and paged collection")
  void testListDatasetsMetadataWithHydra(VertxTestContext testContext) {
    String id1 = "ld-metadata-hydra-1";
    String id2 = "ld-metadata-hydra-2";

    datasetsService.putDatasetOrigin(id1, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
        .compose(r -> datasetsService.putDatasetOrigin(id2, exampleDataset, RDFMimeTypes.TURTLE,
            catalogueID, false))
        .compose(r -> datasetsService.listDatasets("application/ld+json", "metadata", 1, 0, true,
            "https://example.test/base", true))
        .onSuccess(jsonld -> testContext.verify(() -> {
          assertNotNull(jsonld);
          assertFalse(jsonld.isEmpty());
          // Heuristic check for hydra terms in JSON-LD output
          assertTrue(jsonld.contains("hydra:") || jsonld.contains("\"@context\""));
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

  @Test
  @DisplayName("listDatasets - identifiers and uriRefs respect limit/offset")
  void testListDatasetsIdentifiersUriRefsPagination(VertxTestContext testContext) {
    String i1 = "ld-page-1";
    String i2 = "ld-page-2";
    String i3 = "ld-page-3";

    datasetsService.putDatasetOrigin(i1, exampleDataset, RDFMimeTypes.TURTLE, catalogueID, false)
        .compose(r -> datasetsService.putDatasetOrigin(i2, exampleDataset, RDFMimeTypes.TURTLE,
            catalogueID, false))
        .compose(r -> datasetsService.putDatasetOrigin(i3, exampleDataset, RDFMimeTypes.TURTLE,
            catalogueID, false))
        .compose(
            r -> datasetsService.listDatasets("application/json", "identifiers", 2, 0, false, null,
                false))
        .compose(
            first -> datasetsService.listDatasets("application/json", "uriRefs", 2, 2, false, null,
                false))
        .onSuccess(second -> testContext.verify(() -> {
          JsonArray uris = new JsonArray(second);
          assertNotNull(uris);
          assertFalse(uris.isEmpty()); // at least one item in the second page
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

    @Test
    @DisplayName("ListDatasets - valueType unknown should fail")
    void testListDatasetsUnknownFails(VertxTestContext testContext) {
        datasetsService.listDatasets("application/json", "foobar", 10, 0, false, null, false)
                .onComplete(testContext.failing(err -> testContext.verify(() -> {
                  assertInstanceOf(ServiceException.class, err);
                  assertEquals(400, ((ServiceException) err).failureCode());
                  assertEquals("Unknown value type", err.getMessage());
                  testContext.completeNow();
                })));
    }

  @Test
  @DisplayName("deleteDataset by normalized ID removes dataset")
  void testDeleteDatasetByNormalizedId(VertxTestContext testContext) {
    String originalId = "Delete Normalized Id";
    String normalizedId = DCATAPUriSchema.applyFor(originalId).getId();

    datasetsService.putDatasetOrigin(originalId, exampleDataset, RDFMimeTypes.TURTLE, catalogueID,
            false)
        .compose(res -> datasetsService.deleteDataset(normalizedId))
        .compose(v -> datasetsService.getDataset(normalizedId, RDFMimeTypes.TURTLE))
        .onComplete(testContext.failing(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          assertEquals(404, ((ServiceException) err).failureCode());
          assertEquals("Dataset not found", err.getMessage());
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("indexDataset - success when indexing is enabled")
  void testIndexDatasetSuccess(VertxTestContext testContext) {
    String originalId = "index-success";
    String normalizedId = DCATAPUriSchema.applyFor(originalId).getId();

    datasetsService.putDatasetOrigin(originalId, exampleDataset, RDFMimeTypes.TURTLE, catalogueID,
            false)
        .compose(res -> datasetsService.indexDataset(normalizedId, catalogueID))
        .onSuccess(result -> testContext.verify(() -> {
          // Our IndexService event-bus stub replies with an empty JSON {} -> just assert non-null
          assertNotNull(result);
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow);
  }

  @Test
  @DisplayName("indexDataset - 404 when dataset does not exist")
  void testIndexDatasetNotFound(VertxTestContext testContext) {
    datasetsService.indexDataset("non-existent-ds", catalogueID)
        .onSuccess(res -> testContext.failNow("Expected 404 for missing dataset"))
        .onFailure(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          ServiceException se = (ServiceException) err;
          assertEquals(404, se.failureCode());
          assertEquals("Dataset not found", se.getMessage());
          testContext.completeNow();
        }));
  }

  @Test
  @DisplayName("indexDataset - 404 when catalogue not found")
  void testIndexDatasetCatalogueNotFound(VertxTestContext testContext) {
    String originalId = "index-missing-catalogue";
    String normalizedId = DCATAPUriSchema.applyFor(originalId).getId();

    datasetsService.putDatasetOrigin(originalId, exampleDataset, RDFMimeTypes.TURTLE, catalogueID,
            false)
        .compose(res -> datasetsService.indexDataset(normalizedId, "missing-cat"))
        .onSuccess(res -> testContext.failNow("Expected 404 for missing catalogue"))
        .onFailure(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          ServiceException se = (ServiceException) err;
          assertEquals(404, se.failureCode());
          assertEquals("Catalogue not found", se.getMessage());
          testContext.completeNow();
        }));
  }

  @Test
  @DisplayName("getDataUploadInformation - mocked DataUploadConnector returns success")
  void testGetDataUploadInformation(Vertx vertx, VertxTestContext testContext) {
    // Mock dependencies we need to inject directly into the impl
    DataUploadConnector dataUploadConnector = mock(DataUploadConnector.class);
    PipeLauncher pipeLauncher = mock(PipeLauncher.class);
    TripleStore tripleStore = mock(TripleStore.class);
    when(pipeLauncher.isPipeAvailable(any())).thenReturn(false);

    // Return a predictable, non-null distributions array
    JsonArray distributions = new JsonArray().add(
        new JsonObject()
            .put("distributionId", "dist-1")
            .put("accessURL", "http://example.com/file.csv")
    );
    when(dataUploadConnector.getResponse(any())).thenReturn(distributions);

    // Minimal config (index/translation/validator off is fine here)
    JsonObject cfg = new JsonObject()
        .put(ENV_PIVEAU_TRIPLESTORE_CONFIG, MockTripleStore.getDefaultConfig())
        .put(ENV_PIVEAU_HUB_VALIDATOR, new JsonObject().put("enabled", false))
        .put(ENV_PIVEAU_TRANSLATION_SERVICE, new JsonObject().put("enable", false))
        .put(ENV_PIVEAU_HUB_SEARCH_SERVICE, new JsonObject().put("enabled", false));

    // Build the service
    Checkpoint ready = testContext.checkpoint();
    DatasetsService[] holder = new DatasetsService[1];
    new DatasetsServiceImpl(tripleStore, tripleStore, dataUploadConnector, cfg, pipeLauncher, vertx,
        ar -> {
          if (ar.succeeded()) {
            holder[0] = ar.result();
            ready.flag();
          } else {
            testContext.failNow(ar.cause());
          }
        }
    );

    // Build minimal valid N-Triples that DatasetHelper can parse
    String originalId = "upload-info-ds";
    String datasetUri = DCATAPUriSchema.applyFor(originalId).getDatasetUriRef();
    String distributionUri = datasetUri + "/dist-1";
    String ntriples = String.join("\n",
        "<" + datasetUri
            + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/ns/dcat#Dataset> .",
        "<" + datasetUri + "> <http://www.w3.org/ns/dcat#distribution> <" + distributionUri + "> .",
        "<" + distributionUri
            + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/ns/dcat#Distribution> .",
        "<" + distributionUri
            + "> <http://www.w3.org/ns/dcat#accessURL> <http://example.com/file.csv> ."
    );

    // Call the method on our directly constructed impl and assert
    vertx.runOnContext(v -> holder[0]
        .getDataUploadInformation(originalId, "test-catalog", ntriples)
        .onSuccess(json -> testContext.verify(() -> {
          assertNotNull(json);
          assertEquals("success", json.getString("status"));
          JsonArray arr = json.getJsonArray("distributions");
          assertNotNull(arr);
          assertFalse(arr.isEmpty());
          assertEquals("dist-1", arr.getJsonObject(0).getString("distributionId"));
          assertEquals("http://example.com/file.csv", arr.getJsonObject(0).getString("accessURL"));
          // Verify our mock was used exactly once
          verify(dataUploadConnector, times(1)).getResponse(any());
          testContext.completeNow();
        }))
        .onFailure(testContext::failNow)
    );
  }

  @Test
  @DisplayName("getDataUploadInformation - invalid RDF returns 500")
  void testGetDataUploadInformationInvalid(VertxTestContext testContext) {
    datasetsService.getDataUploadInformation("bad-ds", catalogueID, "this is not n-triples")
        .onSuccess(res -> testContext.failNow("Expected 500 for invalid RDF"))
        .onFailure(err -> testContext.verify(() -> {
          assertInstanceOf(ServiceException.class, err);
          ServiceException se = (ServiceException) err;
          assertEquals(500, se.failureCode());
          assertEquals("Internal Server Error", se.getMessage());
          testContext.completeNow();
        }));
  }

}
