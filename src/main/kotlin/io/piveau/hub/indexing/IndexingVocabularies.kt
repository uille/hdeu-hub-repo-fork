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
package io.piveau.hub.indexing

import io.piveau.json.putIfNotEmpty
import io.piveau.rdf.asNormalized
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Resource
import org.apache.jena.sparql.vocabulary.FOAF
import org.apache.jena.vocabulary.DC_11
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.SKOS

fun indexConceptScheme(id: String, uriRef: String, model: Model): JsonObject {
    val index = JsonObject()
        .put("resource", uriRef)
        .put("id", id)
    val vocab = JsonArray()
    index.put("vocab", vocab)

    model.listSubjectsWithProperty(RDF.type, SKOS.Concept).forEachRemaining { concept ->
        val conceptIndex = indexConcept(concept)
        if (conceptIndex.containsKey("pref_label")) {
            if (!conceptIndex.containsKey("in_scheme")) {
                conceptIndex.put("in_scheme", uriRef)
            }
            vocab.add(conceptIndex)
        }
    }

    return index
}

fun indexConcept(concept: Resource): JsonObject {
    val index = JsonObject()
        .put("resource", concept.uri)

    concept.getProperty(DC_11.identifier)?.let {
        index.put("id", it.literal.lexicalForm)
    } ?: index.put("id", concept.uri.substringAfterLast("/", concept.uri))

    concept.getPropertyResourceValue(SKOS.inScheme)?.let {
        index.put("in_scheme", it.uri)
    }

    concept.getProperty(FOAF.homepage)?.let {
        index.put("extensions", JsonObject().put("foaf_homepage", it.resource.uri))
    }

    // pref_label
    val prefLabels = JsonObject()
    concept.listProperties(SKOS.prefLabel).toList()
        .map { it.literal }
        .filter { it.language.length == 2 }
        .forEach { prefLabels.put(it.language, it.string) }
    index.putIfNotEmpty("pref_label", prefLabels)

    // alt_label
    val altLabels = JsonObject()
    concept.listProperties(SKOS.altLabel).toList()
        .map { it.literal }
        .filter { it.language.length == 2 }
        .forEach { altLabels.put(it.language, it.string) }
    index.putIfNotEmpty("alt_label", altLabels)

    return index
}
