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

import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.hub.services.datasets.DatasetsServiceVerticle;
import io.piveau.hub.services.distributions.DistributionsService;
import io.piveau.hub.services.distributions.DistributionsServiceVerticle;
import io.piveau.rdf.Piveau;
import io.piveau.rdf.RDFMimeTypes;
import io.piveau.test.MockTripleStore;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testing the Distributions service")
@ExtendWith(VertxExtension.class)
public class DistributionsServiceImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionsServiceImplTest.class);
    private DistributionsService distributionsService;

    private DatasetsService datasetsService;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        DeploymentOptions options = new DeploymentOptions()
                .setConfig(new JsonObject()
                        .put(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG, MockTripleStore.getDefaultConfig()));

        Future<String> service1 = vertx.deployVerticle(DistributionsServiceVerticle.class.getName(), options);
        Future<String> service2 = vertx.deployVerticle(DatasetsServiceVerticle.class.getName(), options);

        CompositeFuture.all(service1, service2)
                .compose(id -> {
                    distributionsService = DistributionsService.createProxy(vertx, DistributionsService.SERVICE_ADDRESS);
                    datasetsService = DatasetsService.createProxy(vertx, DatasetsService.SERVICE_ADDRESS);
                    return new MockTripleStore()
                            .loadGraph("https://piveau.io/id/catalogue/test-catalog", "distribution/example_catalog.ttl")
                            .loadGraph("https://piveau.io/set/data/test-dataset", "distribution/example_dataset.ttl")
                            .deploy(vertx);
                })
                .onSuccess(id -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }

    @Test
    void testGetDistribution(Vertx vertx, VertxTestContext vertxTestContext) {
        distributionsService.getDistribution("02e103b1-ffc4-464d-8013-de1c435a5375", RDFMimeTypes.JSONLD)
                .onSuccess(content -> {
                    vertxTestContext.verify(() -> {
                        assertNotNull(content);
                        assertDoesNotThrow(() -> {
                            Model model = Piveau.toModel(content.getBytes(), RDFMimeTypes.JSONLD);
                            assertFalse(model.isEmpty());
                        });
                    });
                    vertxTestContext.completeNow();
                })
                .onFailure(vertxTestContext::failNow);
    }

    @Test
    void testPutDistribution(Vertx vertx, VertxTestContext vertxTestContext) {
        Buffer distribution = vertx.fileSystem().readFileBlocking("distribution/example_distribution_update.ttl");

        distributionsService.putDistribution("test-dataset", "02e103b1-ffc4-464d-8013-de1c435a5375", distribution.toString(), RDFMimeTypes.TURTLE)
                .compose(result -> {
                    vertxTestContext.verify(() -> {
                        assertEquals("updated", result);
                    });

                    return distributionsService.getDistribution("02e103b1-ffc4-464d-8013-de1c435a5375", RDFMimeTypes.JSONLD);
                })
                .onSuccess(content -> {
                    vertxTestContext.verify(() -> {
                        assertNotNull(content);
                        assertDoesNotThrow(() -> {
                            Model model = Piveau.toModel(content.getBytes(), RDFMimeTypes.JSONLD);
                            assertFalse(model.isEmpty());
                        });
                    });
                    vertxTestContext.completeNow();
                })
                .onFailure(vertxTestContext::failNow);
    }

    @Test
    void testDistributionExtraction(Vertx vertx, VertxTestContext vertxTestContext) {
        Buffer dataset = vertx.fileSystem().readFileBlocking("example_dataset.ttl");

        Model datasetModel = Piveau.toModel(dataset.getBytes(), Lang.TURTLE);
        Resource distribution = datasetModel.createResource("https://piveau.io/set/distribution/2");
        Model distModel = Piveau.extractAsModel(distribution);

        System.out.println(Piveau.presentAs(distModel, Lang.TURTLE));

        vertxTestContext.completeNow();
    }

    @Test
    void testDeleteDistribution(Vertx vertx, VertxTestContext vertxTestContext) {
        distributionsService.deleteDistribution("test-dataset", "02e103b1-ffc4-464d-8013-de1c435a5375")
                .compose(result -> datasetsService.getDataset("test-dataset", RDFMimeTypes.JSONLD))
                .onSuccess(content -> {
                    vertxTestContext.verify(() -> {
                        assertNotNull(content);
                        assertDoesNotThrow(() -> {
                            Model model = Piveau.toModel(content.getBytes(), RDFMimeTypes.JSONLD);
                            assertFalse(model.isEmpty());
                            assertEquals(0, model.listObjectsOfProperty(DCAT.distribution).toList().size());
                            assertFalse(model.containsResource(model.createResource("https://piveau.io/set/distribution/02e103b1-ffc4-464d-8013-de1c435a5375")));
                        });
                    });
                    vertxTestContext.completeNow();
                })
                .onFailure(vertxTestContext::failNow);
    }

    @Test
    void testPostDistribution(Vertx vertx, VertxTestContext vertxTestContext) {
        Buffer distribution = vertx.fileSystem().readFileBlocking("distribution/example_distribution.ttl");

        distributionsService.postDistribution("test-dataset", distribution.toString(), RDFMimeTypes.TURTLE)
                .compose(result -> {
                    vertxTestContext.verify(() -> {
                        assertEquals("updated", result);
                    });
                    return datasetsService.getDataset("test-dataset", RDFMimeTypes.JSONLD);
                })
                .onSuccess(content -> {
                    vertxTestContext.verify(() -> {
                        assertNotNull(content);
                        assertDoesNotThrow(() -> {
                            Model model = Piveau.toModel(content.getBytes(), RDFMimeTypes.JSONLD);
                            assertFalse(model.isEmpty());
                            assertEquals(2, model.listObjectsOfProperty(DCAT.distribution).toList().size());
                            model.listObjectsOfProperty(DCAT.distribution).forEach(distro -> {
                                if(!distro.toString().contains("02e103b1-ffc4-464d-8013-de1c435a5375")) {
                                    String title = distro.asResource().getProperty(DCTerms.title).getString();
                                    assertEquals("Extra Distribution", title);
                                }
                            });
                        });
                    });
                    vertxTestContext.completeNow();
                })
                .onFailure(vertxTestContext::failNow);
    }
}
