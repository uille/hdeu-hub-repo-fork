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
package io.piveau.indexing

import io.piveau.dqv.listMetricsModels
import io.piveau.hub.indexing.indexingCatalogue
import io.piveau.hub.indexing.indexingDataset
import io.piveau.hub.util.IndexMappingLoader
import io.piveau.rdf.toDataset
import io.piveau.vocabularies.loadResource
import io.piveau.vocabularies.readTurtleResource
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory


@DisplayName("Indexing test")
@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexingTest {
    private val log = LoggerFactory.getLogger("IndexingTest")

    private val modelDataset: Model = ModelFactory.createDefaultModel().apply {
        readTurtleResource("example_dataset.ttl")
    }

    private val modelCatalog: Model = ModelFactory.createDefaultModel().apply {
        readTurtleResource("example_catalog.ttl")
    }

    private val modelMetrics: Model = "example_metric_1.ttl".loadResource()
        ?.readBytes()
        ?.toDataset(Lang.TRIG)
        ?.listMetricsModels()
        ?.first()
        ?: ModelFactory.createDefaultModel()

    private val modelMetrics2: Model = ModelFactory.createDefaultModel().apply {
        readTurtleResource("test-dataset-1-metrics-graph.ttl")
    }

    /**
     * Initializes the Indexing module before tests run by loading the SHACL shapes
     * file via [IndexMappingLoader] and producing index instructions for datasets and catalogues.
     *
     * Without this, [indexingDataset] and [indexingCatalogue] rely on lateinit vars
     * [io.piveau.hub.indexing.datasetInstr] and [io.piveau.hub.indexing.catalogInstr]
     * that are only set when a full MainVerticle bootstraps — which doesn't happen in
     * unit tests that call the indexing functions directly.
     */
    @BeforeAll
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        IndexMappingLoader(vertx, JsonObject(), null)
            .load()
            .onSuccess { testContext.completeNow() }
            .onFailure(testContext::failNow)
    }

    @Test
    fun `test publisher homepage and email with example dataset`(vertx: Vertx, testContext: VertxTestContext) {
        val res: Resource = modelDataset.getResource("https://piveau.io/set/data/test-dataset")
        val resRecord: Resource = modelDataset.getResource("https://piveau.io/set/record/test-dataset")
        val obj: JsonObject = indexingDataset(res, resRecord,"test-catalog", "en")

        val publisher: JsonObject = obj.getJsonObject("publisher")

        assertNotNull(publisher)
        // foaf:homepage and foaf:mbox are not mapped by Agent_Shape, so not asserted here

        log.info("Indexing dataset: {}", obj.encodePrettily())

        testContext.completeNow()
    }

    @Test
    fun `test publisher homepage and email with example catalog`(vertx: Vertx, testContext: VertxTestContext) {
        val res: Resource = modelCatalog.getResource("https://piveau.io/id/catalogue/test-catalog")
        indexingCatalogue(res)
            .onSuccess {
                val publisher = it.getJsonObject("publisher")

                testContext.verify {
                    assertNotNull(publisher)
                    // foaf:homepage and foaf:mbox are not mapped by Agent_Shape, so not asserted here
                }

                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

    @Test
    fun `test metrics indexing`(vertx: Vertx, testContext: VertxTestContext) {

        val obj: JsonObject = io.piveau.hub.indexing.indexingMetrics(modelMetrics)

        assertEquals("test-dataset", obj.getString("id"))
        val qualityMeas: JsonObject = obj.getJsonObject("quality_meas")

        assertNotNull(qualityMeas)
        assertEquals(100, qualityMeas.getInteger("scoring"))

        assertNotNull(qualityMeas.getJsonArray("accessUrlStatusCode"))
        assertEquals(3, qualityMeas.getJsonArray("accessUrlStatusCode").size())

        log.info("Indexing catalogue: {}", obj.encodePrettily())

        testContext.completeNow()
    }

    @Test
    fun `test metrics indexing 2`(vertx: Vertx, testContext: VertxTestContext) {

        val obj: JsonObject = io.piveau.hub.indexing.indexingMetrics(modelMetrics2)

        assertEquals("test-dataset", obj.getString("id"))
        val qualityMeas: JsonObject = obj.getJsonObject("quality_meas")

        assertNotNull(qualityMeas)
        assertEquals(180, qualityMeas.getInteger("scoring"))

        assertNotNull(obj.getJsonArray("quality_ann"))
        assertEquals(10, obj.getJsonArray("quality_ann").size())

        log.info("Indexing catalogue: {}", obj.encodePrettily())

        testContext.completeNow()
    }

}
