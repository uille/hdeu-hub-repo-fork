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
@file:JvmName("HubRepo")

package io.piveau

import io.piveau.dcatap.TripleStoreException
import io.piveau.rdf.RDFMimeTypes
import io.piveau.rdf.extractDatasetAsModel
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.serviceproxy.ServiceException
import org.apache.jena.rdf.model.Model
import org.apache.jena.vocabulary.DCAT
import org.apache.jena.vocabulary.RDF

fun failureResponse(routingContext: RoutingContext, cause: Throwable) = when (cause) {
    is ServiceException -> routingContext.fail(cause.failureCode(), cause.cause ?: cause)
    is TripleStoreException -> routingContext.fail(cause.code, cause.cause ?: cause)
    else -> routingContext.fail(cause)
}

// Useful for junit tests
enum class RDFMimeTypesEnum(val value: String, val charset: String) {
    JSONLD(RDFMimeTypes.JSONLD, "UTF-8"),
    RDFXML(RDFMimeTypes.RDFXML, "UTF-8"),
    NTRIPLES(RDFMimeTypes.NTRIPLES, "UTF-8"),
    NQUADS(RDFMimeTypes.NQUADS, "UTF-8"),
    TRIG(RDFMimeTypes.TRIG, "UTF-8"),
    TRIX(RDFMimeTypes.TRIX, "UTF-8"),
    TURTLE(RDFMimeTypes.TURTLE, "UTF-8"),
    N3(RDFMimeTypes.N3, "UTF-8")
}

enum class ValueTypesEnum {
    uriRefs, identifiers, originalIds, metadata
}

fun Model.extractDatasetModel(): Model? {
    val dataset = listSubjectsWithProperty(RDF.type, DCAT.Dataset).toList()
    return dataset.first()?.extractDatasetAsModel()
}

object FeatureFlags {

    @JvmStatic
    var config: JsonObject = JsonObject()

    @JvmStatic
    fun isEnabled(flag: String): Boolean = config.getBoolean(flag, false)

    @JvmStatic
    fun isSet(flag: String): Boolean = config.containsKey(flag)
}

