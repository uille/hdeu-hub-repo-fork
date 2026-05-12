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
package io.piveau.indexing;

import io.piveau.hub.indexing.indexingCatalogue
import io.piveau.hub.util.IndexMappingLoader
import io.piveau.vocabularies.initRemotes
import io.piveau.vocabularies.readTurtleResource
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

@DisplayName("Indexing geonames test")
@ExtendWith(VertxExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexingGeonamesTest {

    private val model: Model = ModelFactory.createDefaultModel().apply {
        readTurtleResource("example_dataset_geonames.ttl")
    }

    @BeforeAll
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        IndexMappingLoader(vertx, JsonObject(), null)
            .load()
            .onSuccess { testContext.completeNow() }
            .onFailure(testContext::failNow)
    }

    @Test
    @Disabled("Integration test: requires live geonames.org network access. Run manually or in an integration test profile.")
    fun `example indexing dataset for geonames`(vertx: Vertx, testContext: VertxTestContext) {
        initRemotes(vertx)

        val res: Resource = model.getResource("https://piveau.io/set/data/test-dataset")
        indexingCatalogue(res)
            .onSuccess {
                val country = it.getJsonObject("country")
                testContext.verify {
                    Assertions.assertNotNull(country)
                    Assertions.assertEquals("Germany", country.getString("label"))
                    Assertions.assertEquals("DE", country.getString("id"))
                }
                testContext.completeNow()
            }
            .onFailure(testContext::failNow)
    }

}
