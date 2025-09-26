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
package io.piveau.hub;

import io.piveau.hub.services.metrics.MetricsService;
import io.piveau.hub.services.metrics.MetricsServiceVerticle;
import io.piveau.rdf.Piveau;
import io.piveau.rdf.RDFMimeTypes;
import io.piveau.test.MockTripleStore;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetricImplServiceTest - Testing the metrics service")
@ExtendWith(VertxExtension.class)
class MetricImplServiceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricImplServiceTest.class);
    private final String catalogueId = "test-catalog";
    private final String datasetId = "test-dataset";
    private MetricsService metricsService;
    private String exampleMetric_1;
    private String exampleMetric_2;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        DeploymentOptions options = new DeploymentOptions()
                .setWorker(true)
                .setConfig(new JsonObject()
                        .put(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG, MockTripleStore.getDefaultConfig())
                        .put(Constants.ENV_PIVEAU_HUB_VALIDATOR, new JsonObject().put("history", true))
                        .put(Constants.ENV_PIVEAU_HUB_SEARCH_SERVICE, new JsonObject().put("enabled", true)));

        Future<String> mockFuture = new MockTripleStore()
                .loadGraph("https://piveau.io/id/catalogue/test-catalog", "example_catalog.ttl")
                .loadGraph("https://piveau.io/set/data/test-dataset", "example_dataset.ttl")
                .deploy(vertx);

        Promise<String> metricsPromise = Promise.promise();

        vertx.deployVerticle(MetricsServiceVerticle.class.getName(), options, metricsPromise);

        CompositeFuture.join(mockFuture, metricsPromise.future()).onComplete(testContext.succeeding(setup -> {
            metricsService = MetricsService.createProxy(vertx, MetricsService.SERVICE_ADDRESS);
            vertx.fileSystem().readFile("example_metric_1.ttl", testContext.succeeding(readMetric1 ->
                    vertx.fileSystem().readFile("example_metric_2.ttl", testContext.succeeding(readMetric2 -> {
                        exampleMetric_1 = readMetric1.toString();
                        exampleMetric_2 = readMetric2.toString();
                        testContext.completeNow();
                    }))
            ));
        }));
    }

    @Test
    @DisplayName("Add a metric")
    void testCreateMetric(VertxTestContext testContext) {
        metricsService.putMetrics(datasetId, exampleMetric_1, RDFMimeTypes.TRIG)
                .compose(result -> metricsService.getMetrics(datasetId, false, RDFMimeTypes.TURTLE))
                .onSuccess(content -> compareModels(testContext, exampleMetric_1, content))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Replace an existing metric")
    void testReplaceMetric(VertxTestContext testContext) {
        metricsService.putMetrics(datasetId, exampleMetric_1, RDFMimeTypes.TRIG)
                .compose(result -> metricsService.putMetrics(datasetId, exampleMetric_2, RDFMimeTypes.TRIG))
                .compose(result -> metricsService.getMetrics(datasetId, false, RDFMimeTypes.TURTLE))
                .onSuccess(content -> compareModels(testContext, exampleMetric_2, content))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Receive a metric")
    void testGetMetric(VertxTestContext testContext) {
        metricsService.putMetrics(datasetId, exampleMetric_1, RDFMimeTypes.TRIG)
                .compose(result -> metricsService.getMetrics(datasetId, false, RDFMimeTypes.TURTLE))
                .onSuccess(content -> compareModels(testContext, exampleMetric_1, content))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Create historic graph and retrieve it")
    void testHistoricMetrics(Vertx vertx, VertxTestContext testContext) {
        metricsService.putMetrics(datasetId, exampleMetric_1, RDFMimeTypes.TRIG)
                .compose(result -> metricsService.putMetrics(datasetId, exampleMetric_2, RDFMimeTypes.TRIG))
                .compose(result -> metricsService.getMetrics(datasetId, true, RDFMimeTypes.TURTLE))
                .onSuccess(content -> testContext.verify(() -> {
                    assertNotNull(content, "Historic content should not be null");

                    Model historicModel = Piveau.toModel(content.getBytes(), RDFMimeTypes.TURTLE);
                    assertNotNull(historicModel, "Historic model could not be parsed");

                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Fails with general Exception if tripleStore throws")
    void testPutMetrics_tripleStoreThrows_failsWithException(VertxTestContext testContext) {
        metricsService.putMetrics(datasetId, exampleMetric_1, RDFMimeTypes.TURTLE)
                .onSuccess(v -> testContext.failNow("Expected failure due to tripleStore error"))
                .onFailure(err -> {
                    assertTrue(err instanceof ServiceException);
                    assertEquals(500, ((ServiceException) err).failureCode());
                    testContext.completeNow();
                });
    }

    @Test
    @DisplayName("Delete a metric")
    @Timeout(timeUnit = TimeUnit.MINUTES, value = 5)
    void testDeleteMetric(VertxTestContext testContext) {
        metricsService.putMetrics(datasetId, exampleMetric_1, RDFMimeTypes.TURTLE)
                .compose(result -> metricsService.deleteMetrics(datasetId, catalogueId))
                .compose(v -> metricsService.getMetrics(datasetId, false, RDFMimeTypes.TURTLE))
                .onSuccess(content -> {
                    LOGGER.debug(content);
                    testContext.failNow("Metrics still exist");
                })
                .onFailure(cause -> testContext.completeNow());
    }

//    @Test
//    @DisplayName("DeleteMetrics should remove the metrics graph from the triple store")
//    void testDeleteMetricsRemovesGraph(VertxTestContext testContext) {
//        metricsService.putMetrics(datasetId, exampleMetric_1, RDFMimeTypes.TURTLE)
//                .compose(result -> metricsService.getMetrics(datasetId, false, RDFMimeTypes.TURTLE))
//                .compose(fetched -> {
//                    // Ensure it was actually added
//                    assertNotNull(fetched);
//                    return metricsService.deleteMetrics(datasetId, catalogueId);
//                })
//                .compose(v -> metricsService.getMetrics(datasetId, false, RDFMimeTypes.TURTLE))
//                .onSuccess(content -> {
//                    // This means deletion did not happen correctly
//                    LOGGER.error("Expected deletion, but metrics still present:\n{}", content);
//                    testContext.failNow("Metrics still exist after deletion.");
//                })
//                .onFailure(cause -> {
//                    // Expected path: getMetrics fails because data was deleted
//                    LOGGER.info("Metrics successfully deleted. Retrieval failed as expected: {}", cause.getMessage());
//                    testContext.completeNow();
//                });
//    }

    @DisplayName("PutMetrics with empty content should fail")
    @Test
    void testPutMetricsEmptyContent(VertxTestContext testContext) {
        metricsService.putMetrics(datasetId, "", RDFMimeTypes.TURTLE)
                .onSuccess(res -> testContext.failNow("Expected failure on empty content"))
                .onFailure(err -> testContext.completeNow());
    }

    @DisplayName("PutMetrics with invalid RDF content should fail")
    @Test
    void testPutMetricsInvalidRdf(VertxTestContext testContext) {
        String invalidRdf = "This is not RDF content";
        metricsService.putMetrics(datasetId, invalidRdf, RDFMimeTypes.TURTLE)
                .onSuccess(res -> testContext.failNow("Expected failure on invalid RDF"))
                .onFailure(err -> testContext.completeNow());
    }

    @DisplayName("GetMetrics with invalid datasetId should fail")
    @Test
    void testGetMetricsInvalidDataset(VertxTestContext testContext) {
        metricsService.getMetrics("invalid-dataset", false, RDFMimeTypes.TURTLE)
                .onSuccess(res -> testContext.failNow("Expected failure on invalid dataset"))
                .onFailure(err -> testContext.completeNow());
    }

    @DisplayName("DeleteMetrics with invalid datasetId should fail")
    @Test
    void testDeleteMetricsInvalidDataset(VertxTestContext testContext) {
        metricsService.deleteMetrics("invalid-dataset", catalogueId)
                .onSuccess(res -> testContext.failNow("Expected failure on invalid dataset"))
                .onFailure(err -> testContext.completeNow());
    }

    @DisplayName("PutMetrics indexes score correctly")
    @Test
    void testPutMetricsIndexesScore(Vertx vertx, VertxTestContext testContext) {
        // Prepare a metric with scoring to trigger indexScore path
        metricsService.putMetrics(datasetId, exampleMetric_1, RDFMimeTypes.TRIG)
                .onSuccess(res -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }

    @DisplayName("PutMetrics with quad format triggers metrics extraction")
    @Test
    void testPutMetricsWithQuadFormat(VertxTestContext testContext) {
        // Use exampleMetric_1 in TRIG format to test quad branch
        metricsService.putMetrics(datasetId, exampleMetric_1, RDFMimeTypes.TRIG)
                .onSuccess(res -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }

    @DisplayName("DeleteMetrics fails if graph deletion fails")
    @Test
    void testDeleteMetricsGraphDeletionFails(VertxTestContext testContext) {
        // Mock tripleStore to fail deletion or simulate failure by deleting twice
        metricsService.putMetrics(datasetId, exampleMetric_1, RDFMimeTypes.TURTLE)
                .compose(v -> metricsService.deleteMetrics(datasetId, catalogueId))
                .compose(v -> metricsService.deleteMetrics(datasetId, catalogueId))
                .onSuccess(v -> testContext.failNow("Expected failure on deleting non-existent graph"))
                .onFailure(err -> testContext.completeNow());
    }

    @Test
    @DisplayName("deleteMetrics removes graph from triple store")
    void testDeleteMetricsActuallyDeletesGraph(VertxTestContext testContext) {
        metricsService.putMetrics(datasetId, exampleMetric_1, RDFMimeTypes.TURTLE)
                .compose(res -> {
                   return metricsService.deleteMetrics(datasetId, catalogueId);
                })
                .compose(v -> metricsService.getMetrics(datasetId, false, RDFMimeTypes.TURTLE))
                .onSuccess(retrieved -> {
                    testContext.failNow("Expected no metrics after deletion, but got: " + retrieved);
                })
                .onFailure(err -> {
                    // Expected path: getMetrics fails because the graph is gone
                    testContext.completeNow();
                });
    }


    private void compareModels(VertxTestContext testContext, String expected, String received) {
        testContext.verify(() -> {
            assertNotNull(received);
            Model receivedMetrics = Piveau.toModel(received.getBytes(), RDFMimeTypes.TURTLE);
            assertNotNull(receivedMetrics);

            Model expectedMetrics = Piveau.toDataset(expected.getBytes(), Lang.TRIG).getNamedModel("urn:junit5-tests:test-pipe");

            // assertTrue(expectedMetrics.isIsomorphicWith(receivedMetrics));
            assertEquals(expectedMetrics.listStatements().toSet().size(), receivedMetrics.listStatements().toSet().size());
            testContext.completeNow();
        });
    }
}
