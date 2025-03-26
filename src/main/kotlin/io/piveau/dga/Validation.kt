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
@file:JvmName("AddOns")
@file:JvmMultifileClass

package io.piveau.dga

import io.piveau.json.withJsonArray
import io.vertx.core.json.JsonObject
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.DCAT
import org.apache.jena.vocabulary.DCTerms
import org.apache.jena.vocabulary.RDF

fun validationReport(dataset: Resource) : JsonObject {
    val report = JsonObject()

    // title
    if (!dataset.hasProperty(DCTerms.title)) {
        report.withJsonArray("violations").add("Dataset title is missing")
    }

    // description
    if (!dataset.hasProperty(DCTerms.description)) {
        report.withJsonArray("violations").add("Dataset description is missing")
    }

    // publisher
    if (!dataset.hasProperty(DCTerms.publisher)) {
        report.withJsonArray("violations").add("Dataset publisher is missing")
    }

    // accessRights != open
    if (!dataset.hasProperty(DCTerms.accessRights)) {
        report.withJsonArray("violations").add("Dataset access rights is missing")
    }

    val distributions = dataset.model.listResourcesWithProperty(RDF.type, DCAT.Distribution).toList()

    // check for at least ine distribution
    if (distributions.isEmpty()) {
        report.withJsonArray("violations").add("Distribution is missing")
    }

    // Distributions:
    distributions.forEach { distribution ->
        // format
        if (!distribution.hasProperty(DCTerms.format)) {
            report.withJsonArray("violations").add("Distribution format is missing")
        }

        // byteSize
        if (!distribution.hasProperty(DCAT.byteSize)) {
            report.withJsonArray("violations").add("Distribution size is missing")
        }

        // accessURL
        if (!distribution.hasProperty(DCAT.accessURL)) {
            report.withJsonArray("violations").add("Distribution access url is missing")
        }

        // rights
        if (!distribution.hasProperty(DCTerms.rights)) {
            report.withJsonArray("violations").add("Distribution rights is missing")
        }
    }

    return report
}