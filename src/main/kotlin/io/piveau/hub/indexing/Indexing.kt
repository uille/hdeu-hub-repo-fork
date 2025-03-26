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
@file:JvmName("Indexing")

package io.piveau.hub.indexing

import io.piveau.dcatap.*
import io.piveau.dcatap.visitors.GeoSpatialVisitor
import io.piveau.hub.services.healthdata.HealthDataService
import io.piveau.hub.util.rdf.DPV
import io.piveau.hub.util.rdf.HEALTHDCATAP
import io.piveau.hub.util.rdf.R5R

import io.piveau.json.*
import io.piveau.rdf.*
import io.piveau.vocabularies.vocabulary.*
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.runBlocking
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.rdf.model.Literal
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.sparql.vocabulary.FOAF
import org.apache.jena.vocabulary.*
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import io.piveau.profile.indexingResource
import io.piveau.utils.normalizeDateTime
import io.piveau.vocabularies.*
import java.util.UUID


val log: Logger = LoggerFactory.getLogger("io.piveau.hub.Indexing")
lateinit var catalogInstr: JsonObject
lateinit var datasetInstr: JsonObject

// create a singleton HealthDataService object
object HealthDataServiceSingleton {
    private val vertx = Vertx.vertx()

    // Singleton instance of HealthDataService
    val instance: HealthDataService by lazy {
        HealthDataService(vertx)
    }
}


fun init(catalogInstructions: JsonObject, datasetInstructions: JsonObject) {
    catalogInstr = catalogInstructions
    datasetInstr = datasetInstructions
}

fun indexingCatalogue(catalogue: Resource): Future<JsonObject> {
    return indexingResource(
        catalogue,
        null,
        catalogInstr,
        "en",
        "DCATAP",
        false,
        null,
        true
    )
}

fun indexingDataset(
    dataset: Resource,
    catalogRecord: Resource,
    catalogueId: String? = null,
    defaultLang: String? = "en",
): JsonObject {
    val result = runBlocking {
        indexingResource(
            dataset,
            catalogRecord,
            datasetInstr,
            defaultLang ?: "en",
            "DCATAP",
            false,
            catalogueId,
            false
        ).coAwait()
    }
    return try {
        runBlocking {
            indexHealthDcatAp(result, dataset)
        }
    } catch (e: Exception) {
        log.error("Error in indexingDataset: $e", e)
        result
    }
}

fun indexHealthDcatAp(
    result: JsonObject,
    dataset: Resource
): JsonObject = result.apply {
    if (dataset.isURIResource) {
        try {
            dataset.listProperties().forEachRemaining { (_, predicate, obj) -> // limit to 1000

                if (predicate.nameSpace == HEALTHDCATAP.getURI()) {  // Check the namespace of the predicate
                    try {
                        when (predicate) {
                            HEALTHDCATAP.maxTypicalAge -> {
                                putIfNotNull("max_typical_age", indexingInteger(obj))
                            }

                            HEALTHDCATAP.minTypicalAge -> {
                                putIfNotNull("min_typical_age", indexingInteger(obj))
                            }

                            HEALTHDCATAP.numberOfUniqueIndividuals -> {
                                putIfNotNull("number_of_unique_individuals", indexingInteger(obj))
                            }

                            HEALTHDCATAP.numberOfRecords -> {
                                putIfNotNull("number_of_records", indexingInteger(obj))
                            }

                            HEALTHDCATAP.publisherNote -> indexingSimpleMultiLang(obj).apply {
                                withJsonObject("publisher_note").putIfNotBlank(first, second)
                            }

                            HEALTHDCATAP.retentionPeriod -> if (obj.isResource) withJsonArray("retention_period")
                                .addIfNotEmpty(indexingTemporal(obj.asResource()))

                            HEALTHDCATAP.healthCategory -> withJsonArray("health_category")
                                .addIfNotEmpty(indexingHealthCategoryHealthData(obj, "/"))

                            HEALTHDCATAP.publisherType -> withJsonArray("publisher_type")
                                .addIfNotBlank(obj.visitWith(StandardVisitor) as String?)

                            // Health Data Access Body
                            HEALTHDCATAP.hdab -> putIfNotEmpty("hdab", indexingAgent(obj))

                            // Analytics distributions
                            HEALTHDCATAP.analytics -> withJsonArray("distributions_analytics").addIfNotEmpty(
                                indexingDistributionHealthData(obj, "en")
                            )

                            HEALTHDCATAP.populationCoverage -> indexingSimpleMultiLang(obj).apply {
                                withJsonObject("population_coverage").putIfNotBlank(first, second)
                            }

                            HEALTHDCATAP.hasCodeValues -> withJsonArray("code_values")
                                .addIfNotEmpty(indexingCodeValuesHealthData(obj))


                            HEALTHDCATAP.hasCodingSystem -> withJsonArray("has_coding_system")
                                .addIfNotEmpty(indexingHasCodingSystemsHealthData(obj))


                            HEALTHDCATAP.healthTheme -> withJsonArray("health_theme")
                                .addIfNotEmpty(indexingWikidataLabeledResource(obj))

                            else -> {
                                log.warn("Unknown predicate for HEALTHDCATAP namespace: $predicate")
                            }
                        }
                    } catch (e: Exception) {
                        log.error("Error when handling the predicate: $predicate", e)
                    }
                }

                if (predicate.nameSpace == DPV.getURI()) {
                    try {
                        when (predicate) {

                            DPV.hasPersonalData -> withJsonArray("has_personal_data")
                                .addIfNotEmpty(indexingCustomLabeledResource(obj, "#"))

                            DPV.hasPurpose -> indexingHasPurposeHealthData(obj)?.apply {
                                withJsonObject("has_purpose").putIfNotBlank(first, second)
                            }

                            DPV.hasLegalBasis -> putIfNotNull(
                                "has_legal_basis",
                                indexingHasLegalBasisHealthData(obj)
                            )
                        }
                    } catch (e: Exception) {
                        log.error("Error when handling the predicate: $predicate", e)
                    }
                }

                if (predicate.nameSpace == ADMS.NS) {  // Check the namespace of the predicate
                    try {
                        when (predicate) {
                            // Sample distributions
                            ADMS.sample -> withJsonArray("distributions_sample").addIfNotEmpty(
                                indexingDistributionHealthData(obj,"en"))
                        }
                    } catch (e: Exception) {
                        log.error("Error when handling the predicate: $predicate", e)
                    }
                }

                if (predicate.nameSpace == DCTerms.NS) {  // Check the namespace of the predicate
                    try {
                        when (predicate) {
                            DCTerms.alternative -> indexingSimpleMultiLang(obj).apply {
                                withJsonObject("alternative").putIfNotBlank(first, second)
                            }

                            DCTerms.conformsTo -> withJsonArray("conforms_to")
                                .addIfNotEmpty(indexingConformsToHealthData(obj))
                        }
                    } catch (e: Exception) {
                        log.error("Error when handling the predicate: $predicate", e)
                    }
                }

                if (predicate.nameSpace == PROV.NS) {  // Check the namespace of the predicate
                    try {
                        when (predicate) {
                            PROV.wasGeneratedBy -> withJsonArray("was_generated_by")
                                .addIfNotBlank(indexingWasGeneratedByHealthData(obj))
                        }
                    } catch (e: Exception) {
                        log.error("Error when handling the predicate: $predicate", e)
                    }
                }
                if (predicate.nameSpace == DCAT.NS) {  // Check the namespace of the predicate
                    try {
                        when (predicate) {
                                DCAT.landingPage -> withJsonArray("landing_page")
                                .addIfNotEmpty(indexingLandingPageHealthData(obj))
                        }
                    } catch (e: Exception) {
                        log.error("Error when handling the predicate: $predicate", e)
                    }
                }


            }
        } catch (e: Exception) {
            log.error("Error in indexingHealthDcatAp: $e", e)
        }
    }
}


// HEALTH DATA
//----------------------------------------------------------------------------------------------------------------------
fun indexingDistributionHealthData(node: RDFNode, defaultLang: String): JsonObject = when (node) {
    is Resource -> JsonObject().apply {
        // TODO check how they get the id in order to handle it properly if needed (for now we generate a UUID)
        //putIfNotBlank("id", DCATAPUriSchema.parseUriRef(node.asResource().uri).id)
        val myUuidAsString = UUID.randomUUID().toString()
        putIfNotBlank("id", myUuidAsString)
        node.listProperties().forEachRemaining { (_, predicate, obj) ->
            when (predicate) {
                // Mandatory property
                DCAT.accessURL -> withJsonArray("access_url").addIfNotBlank(
                    obj.visitWith(
                        StandardVisitor
                    ) as String?
                )

                // Recommended properties
                DCATAP.availability -> putIfNotNull("availability", indexingLabeledResource(obj))
                DCTerms.description -> indexingSimpleMultiLang(obj).apply {
                    withJsonObject("description").putIfNotBlank(first, second)
                }


                DCTerms.format -> putIfNotEmpty(
                    "format",
                    obj.visitWith(FormatVisitor) as JsonObject
                )

                DCTerms.license -> putIfNotEmpty(
                    "license",
                    obj.visitWith(LicenseVisitor) as JsonObject
                )


                // Optional properties
                DCTerms.title -> indexingSimpleMultiLang(obj).apply {
                    withJsonObject("title").putIfNotBlank(first, second)
                }


                DCAT.mediaType -> putIfNotNull("media_type", obj.visitWith(MediaTypeVisitor))
                DCAT.downloadURL -> withJsonArray("download_url").addIfNotBlank(
                    obj.visitWith(
                        StandardVisitor
                    ) as String?
                )

                DCAT.byteSize -> putIfNotNull("byte_size", indexingInteger(obj))
                SPDX.checksum -> if (obj.isResource) putIfNotEmpty(
                    "checksum",
                    indexingChecksum(obj.asResource())
                )

                FOAF.page -> withJsonArray("page").addIfNotEmpty(
                    indexingPage(
                        obj,
                        defaultLang
                    )
                )

                DCTerms.language -> withJsonArray("language").addIfNotNull(
                    obj.visitWith(
                        LanguageVisitor
                    )
                )

                DCTerms.conformsTo -> withJsonArray("conforms_to").addIfNotEmpty(
                    indexingLabeledResource(obj)
                )

                DCTerms.issued -> putIfNotNull("issued", indexingDateTime(obj))
                DCTerms.modified -> putIfNotNull("modified", indexingDateTime(obj))
                DCTerms.rights -> putIfNotNull("rights", indexingLabeledResource(obj))
                ADMS.status -> putIfNotNull("status", indexingConceptScheme(obj, ADMSStatus))
                DCAT.spatialResolutionInMeters -> withJsonArray("spatial_resolution_in_meters").addIfNotNull(
                    indexingInteger(obj)
                )

                DCAT.temporalResolution -> withJsonArray("temporal_resolution").addIfNotBlank(
                    obj.visitWith(
                        LabeledVisitor
                    ) as String?
                )

                DCAT.accessService -> withJsonArray("access_service").addIfNotEmpty(
                    indexingAccessService(
                        obj,
                        defaultLang
                    )
                )

                DCAT.compressFormat -> putIfNotNull(
                    "compress_format",
                    obj.visitWith(DocumentVisitor) as JsonObject?
                )

                DCAT.packageFormat -> putIfNotNull(
                    "package_format",
                    obj.visitWith(DocumentVisitor) as JsonObject?
                )

                ODRL.hasPolicy -> putIfNotBlank(
                    "has_policy",
                    obj.visitWith(StandardVisitor) as String?
                )

                DCTerms.type -> {
                    val type = indexingType(obj, DistributionType)
                    type.remove("id")
                    putIfNotNull("type", type)
                }

                R5R.applicableLegislation -> withJsonArray("applicable_legislation")
                    .addIfNotBlank(obj.visitWith(StandardVisitor) as String?)
            }
        }
    }

    else -> JsonObject() // it is not a resource
}

fun indexingHasPurposeHealthData(node: RDFNode): Pair<String, String?>? {
    // TODO check if we can ensure that the node is a Resource, similar to other indexing functions here
    var resultPair: Pair<String, String?>? = null
    node.asResource().listProperties().forEachRemaining { (_, predicate, obj) ->
        when (predicate) {
            DCTerms.description -> {
                resultPair = indexingSimpleMultiLang(obj)
            }
        }
    }
    return resultPair
}

fun indexingWikidataLabeledResource(node: RDFNode): JsonObject {
    // TODO check for cases where the call to wikidata can be avoided (rdf contains all the required info)
    return when (node) {
        is Resource -> {
            val link: String =
                if (node.listProperties()
                        .hasNext() && node.getProperty(DCTerms.identifier)?.string?.isNotEmpty() == true
                ) {
                    node.getProperty(DCTerms.identifier).string
                } else {
                    node.asResource().uri
                }
            // TODO check the below warning and if and how it can be avoided (we call the service from a new Vertx context)
            //  WARN  io.vertx.core.impl.VertxImpl - You're already on a Vert.x context, are you sure you want to create a new Vertx instance?
            val label = fetchWikidataLabelAsString(link)

            JsonObject()
                .putIfNotBlank("label", label)
                .putIfNotBlank("resource", link)
                .putIfNotNull("id", link)
        }

        else -> JsonObject()
    }
}

fun indexingHasCodingSystemsHealthData(node: RDFNode): JsonObject {
    // TODO check for cases where the call to wikidata can be avoided (rdf contains all the required info)
    return when (node) {
        is Resource -> {
            val link: String =
                if (node.listProperties()
                        .hasNext() && node.getProperty(DCTerms.identifier)?.string?.isNotEmpty() == true
                ) {
                    node.getProperty(DCTerms.identifier).string
                } else {
                    node.asResource().uri
                }
            // TODO check the below warning and if and how it can be avoided (we call the service from a new Vertx context)
            //  WARN  io.vertx.core.impl.VertxImpl - You're already on a Vert.x context, are you sure you want to create a new Vertx instance?
            val wikidataInfo =
                fetchWikidataInfoAsJson(link)
            val wikidataJson = JSONObject(wikidataInfo)

            JsonObject()
                .putIfNotBlank("label", wikidataJson.optString("description", ""))
                .putIfNotBlank("resource", link)
                .putIfNotNull("id", wikidataJson.optString("label", ""))
        }

        else -> JsonObject()
    }
}

fun fetchWikidataLabelAsString(wikidataLink: String): String {
    val healthDataService = HealthDataServiceSingleton.instance
    val future: CompletableFuture<String> = healthDataService.fetchWikidataInfo(wikidataLink, false)

    return try {
        future.get()  // This will block until the future is complete
    } catch (e: Exception) {
        log.error("Error occurred while fetching data from $wikidataLink", e)
        return ""
    }
}

fun fetchWikidataInfoAsJson(wikidataLink: String): String {
    val healthDataService = HealthDataServiceSingleton.instance
    val future: CompletableFuture<String> = healthDataService.fetchWikidataInfo(wikidataLink, true)

    return try {
        future.get()  // This will block until the future is complete
    } catch (e: Exception) {
        log.error("Error occurred while fetching data from $wikidataLink", e)
        return ""
    }
}

fun fetchHealthCategoryDescriptionFromControlledVocabulary(healthCategory: String): String? {
    val healthDataService = HealthDataServiceSingleton.instance
    val future: CompletableFuture<String> =
        healthDataService.fetchHealthCategoryDescriptionFromVocabulary(healthCategory)

    return try {
        future.get()  // This will block until the future is complete
    } catch (e: Exception) {
        log.error("Error occurred while fetching data for health category $healthCategory", e)
        return ""
    }
}

fun indexingCodeValuesHealthData(node: RDFNode): JsonObject {
    return when (node) {
        is Resource -> {
            // TODO check the below warning and if and how it can be avoided (we call the service from a new Vertx context)
            //  WARN  io.vertx.core.impl.VertxImpl - You're already on a Vert.x context, are you sure you want to create a new Vertx instance?
            val wikidataInfo =
                fetchWikidataInfoAsJson(node.getProperty(SKOS.hasTopConcept).resource.uri)
            val wikidataJson = JSONObject(wikidataInfo)

            JsonObject()
                .putIfNotBlank("code", node.getProperty(SKOS.notation).string)
                .putIfNotBlank("description", node.getProperty(SKOS.prefLabel).string)
                .putIfNotBlank("codingSystem", wikidataJson.optString("alias", ""))
                .putIfNotBlank("codingSystemDescription", wikidataJson.optString("description", ""))
        }

        else -> JsonObject()
    }


}

fun indexingWasGeneratedByHealthData(node: RDFNode): String? {
    return when (node) {
        is Resource -> {
            return node.getProperty(RDFS.label).string
        }

        else -> null
    }
}

fun indexingCustomLabeledResource(node: RDFNode, delimiter: String): JsonObject {
    return when (node) {
        is Resource -> {
            JsonObject()
                .putIfNotBlank("label", node.asResource().uri.substringAfterLast(delimiter))
                .putIfNotBlank("resource", node.asResource().uri)
                .putIfNotNull("id", node.asResource().uri)
        }

        else -> JsonObject()
    }
}

fun indexingHealthCategoryHealthData(node: RDFNode, delimiter: String): JsonObject {
    return when (node) {
        is Resource -> {
            val healthCategory = node.asResource().uri.substringAfterLast(delimiter)
            val healthCategoryDescription =
                fetchHealthCategoryDescriptionFromControlledVocabulary(healthCategory)

            JsonObject()
                .putIfNotBlank("label", healthCategoryDescription ?: "")
                .putIfNotBlank("resource", node.asResource().uri)
                .putIfNotNull("id", healthCategory)
        }

        else -> JsonObject()
    }
}

fun indexingHasLegalBasisHealthData(node: RDFNode): JsonArray {
    val jsonArray = JsonArray()

    if (node is Resource) {
        // Create the main object with label and resource
        val mainObject = JsonObject()
            .putIfNotBlank("label", node.getProperty(DCTerms.description)?.string)

        // Collect all DCTerms.source resources
        val sources = node.listProperties(DCTerms.source)
            .asSequence()
            .mapNotNull { it.`object`.asResource().uri }
            .toList()

        // Add the first source to the main object if available
        if (sources.isNotEmpty()) {
            mainObject.putIfNotBlank("resource", sources[0])
        }
        jsonArray.addIfNotEmpty(mainObject)


        // Add each remaining source directly to the result object
        sources.drop(1).forEach { sourceUri ->
            val jsonObject = JsonObject()
            jsonObject.put("resource", sourceUri)
            jsonArray.add(jsonObject)
        }
    }

    return jsonArray
}

fun indexingConformsToHealthData(node: RDFNode): JsonObject {
    return when (node) {
        is Resource -> {
            if (node.asResource().uri.contains("wikidata", true)) {
                // TODO check the below warning and if and how it can be avoided (we call the service from a new Vertx context)
                //  WARN  io.vertx.core.impl.VertxImpl - You're already on a Vert.x context, are you sure you want to create a new Vertx instance?
                val wikidataInfo = fetchWikidataInfoAsJson(node.asResource().uri)
                val wikidataJson = JSONObject(wikidataInfo)

                JsonObject()
                    .putIfNotBlank("label", wikidataJson.optString("label", ""))
                    .putIfNotBlank("resource", node.asResource().uri)
                    .putIfNotBlank("id", wikidataJson.optString("alias", ""))

            } else {
                val label = node.asResource().getProperty(RDFS.label).string

                JsonObject()
                    .putIfNotBlank("label", label)
                    .putIfNotBlank("resource", node.asResource().uri)
                    .putIfNotBlank("id", label)
            }
        }

        else -> JsonObject()
    }
}

fun indexingLandingPageHealthData(node: RDFNode): JsonObject = when (node) {
    is Resource -> JsonObject().apply {
        put("resource", node.getProperty(FOAF.homepage).resource.uri)
    }

    else -> JsonObject() // it is not a resource
}

// PIVEAU
//----------------------------------------------------------------------------------------------------------------------
fun indexingDataset_old(
    dataset: Resource,
    catalogRecord: Resource,
    catalogueId: String? = null,
    defaultLang: String
): JsonObject =
    JsonObject().apply {
        if (dataset.isURIResource) {
            putIfNotBlank("id", DCATAPUriSchema.parseUriRef(dataset.uri).id)
            put("resource", dataset.uri)
            //putIfNotNull("idName", DCATAPUriSchema.parseUriRef(dataset.uri).id)
            putIfNotEmpty("catalog", JsonObject().putIfNotBlank("id", catalogueId))
            putIfNotEmpty("catalog_record", JsonObject().apply {
                if (catalogRecord.isURIResource) {
                    catalogRecord.listProperties().forEachRemaining { (_, predicate, obj) ->
                        when (predicate) {
                            DCTerms.issued -> putIfNotNull("issued", indexingDateTime(obj))
                            DCTerms.modified -> putIfNotNull("modified", indexingDateTime(obj))
                        }
                    }
                }
            })
            dataset.listProperties().forEachRemaining { (_, predicate, obj) ->
                when (predicate) {
                    // Mandatory properties
                    DCTerms.description -> withJsonObject("description").putPairIfNotNull(
                        indexingMultiLang(
                            obj,
                            defaultLang
                        )
                    )

                    DCTerms.title -> withJsonObject("title").putPairIfNotNull(
                        indexingMultiLang(
                            obj,
                            defaultLang,
                            trim = true
                        )
                    )

                    // Recommended properties
                    DCAT.contactPoint -> withJsonArray("contact_point").addIfNotEmpty(
                        indexingContactPoint(obj)
                    )

                    DCAT.distribution -> withJsonArray("distributions").addIfNotEmpty(
                        indexingDistribution(
                            obj,
                            defaultLang
                        )
                    )

                    DCAT.keyword -> withJsonArray("keywords").addIfNotEmpty(
                        indexingKeyword(
                            obj,
                            defaultLang
                        )
                    )

                    DCTerms.publisher -> putIfNotEmpty("publisher", indexingAgent(obj))
                    // ToDo Spatial not finished yet
                    DCTerms.spatial -> {
                        withJsonArray("spatial").addIfNotEmpty(obj.visitWith(GeoSpatialVisitor) as JsonObject)
                        //withJsonArray("spatial_resource").addIfNotNull(obj.visitWith(SpatialVisitor) as JsonObject)
                    }

                    DCTerms.temporal -> if (obj.isResource) withJsonArray("temporal").addIfNotEmpty(
                        indexingTemporal(obj.asResource())
                    )

                    DCAT.theme -> withJsonArray("categories").addIfNotEmpty(
                        obj.visitWith(
                            ThemeVisitor
                        ) as JsonObject
                    )

                    // Optional properties
                    DCTerms.accessRights -> putIfNotNull(
                        "access_right",
                        indexingConceptScheme(obj, AccessRight)
                    )

                    DCTerms.creator -> putIfNotEmpty("creator", indexingAgent(obj))
                    DCTerms.conformsTo -> withJsonArray("conforms_to").addIfNotEmpty(
                        indexingLabeledResource(obj)
                    )

                    FOAF.page -> withJsonArray("page").addIfNotEmpty(
                        indexingPage(
                            obj,
                            defaultLang
                        )
                    )

                    DCTerms.accrualPeriodicity -> putIfNotNull(
                        "accrual_periodicity",
                        indexingConceptScheme(obj, Frequency)
                    )

                    DCTerms.hasVersion -> if (obj.isResource) withJsonArray("has_version").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    DCTerms.isVersionOf -> if (obj.isResource) withJsonArray("is_version_of").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    DCTerms.source -> if (obj.isResource) withJsonArray("source").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    DCTerms.identifier -> withJsonArray("identifier").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    DCAT.landingPage -> withJsonArray("landing_page").addIfNotEmpty(
                        indexingPage(
                            obj,
                            defaultLang
                        )
                    )

                    DCTerms.language -> withJsonArray("language").addIfNotNull(
                        obj.visitWith(
                            LanguageVisitor
                        )
                    )

                    DCTerms.provenance -> withJsonArray("provenance").addIfNotEmpty(
                        indexingLabeledResource(obj)
                    )

                    DCTerms.issued -> putIfNotNull("issued", indexingDateTime(obj))
                    DCTerms.modified -> putIfNotNull("modified", indexingDateTime(obj))
                    OWL.versionInfo -> putIfNotBlank(
                        "version_info",
                        obj.visitWith(LabeledVisitor) as String?
                    )

                    ADMS.versionNotes -> indexingSimpleMultiLang(obj).apply {
                        withJsonObject("version_notes").putIfNotBlank(first, second)
                    }

                    DCTerms.type -> putIfNotNull("type", indexingType(obj, DatasetType))

                    DCAT.spatialResolutionInMeters -> withJsonArray("spatial_resolution_in_meters").addIfNotNull(
                        indexingInteger(obj)
                    )

                    DCAT.temporalResolution -> withJsonArray("temporal_resolution").addIfNotBlank(
                        obj.visitWith(
                            LabeledVisitor
                        ) as String?
                    )

                    DCTerms.isReferencedBy -> withJsonArray("is_referenced_by").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    PROV.wasGeneratedBy -> withJsonArray("was_generated_by").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    PROV.qualifiedAttribution -> withJsonArray("qualified_attribution").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    ADMS.sample -> withJsonArray("sample").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    ADMS.identifier -> if (obj.isResource) withJsonArray("adms_identifier").addIfNotEmpty(
                        indexingAdmsIdentifier(obj.asResource())
                    )

                    DCAT.qualifiedRelation -> if (obj.isResource) withJsonArray("qualified_relation").addIfNotEmpty(
                        indexingQualifiedRelation(obj.asResource())
                    )

                    DCTerms.relation -> withJsonArray("relation").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    DCTerms.subject -> withJsonArray("subject").addIfNotEmpty(
                        indexingConceptSchemeMultiLang(
                            obj,
                            EuroVoc
                        )
                    )

                    // StatDCAT-AP
                    STATDCATAP.attribute -> withJsonArray("attribute").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    STATDCATAP.dimension -> withJsonArray("dimension").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    STATDCATAP.numSeries -> putIfNotNull("num_series", indexingInteger(obj))
                    STATDCATAP.hasQualityAnnotation -> withJsonArray("has_quality_annotation").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    STATDCATAP.statUnitMeasure -> withJsonArray("stat_unit_measure").addIfNotBlank(
                        obj.visitWith(
                            StandardVisitor
                        ) as String?
                    )

                    // DCATAP.de
                    DCATAPDE.legalBase -> putIfNotEmpty("legal_base", indexingLiteralWithId(obj))
                    DCATAPDE.geocodingDescription -> putIfNotEmpty(
                        "geocoding_description",
                        indexingLiteralWithId(obj)
                    )

                    // SENIAS
                    SENIAS.deadline -> putIfNotNull("deadline", indexingDateTime(obj))

                    // Metadata extensions
                    DEXT.metadataExtension -> putIfNotEmpty(
                        "extended_metadata",
                        indexingMetadataExtension(obj)
                    )
                }
            }
            legacyTranslationMeta(this, dataset)
        }
    }

fun indexingDistribution(node: RDFNode, defaultLang: String): JsonObject = when (node) {
    is Resource -> JsonObject().apply {
        putIfNotBlank("id", DCATAPUriSchema.parseUriRef(node.asResource().uri).id)
        node.listProperties().forEachRemaining { (_, predicate, obj) ->
            when (predicate) {
                // Mandatory property
                DCAT.accessURL -> withJsonArray("access_url").addIfNotBlank(
                    obj.visitWith(
                        StandardVisitor
                    ) as String?
                )

                // Recommended properties
                DCATAP.availability -> putIfNotNull("availability", indexingLabeledResource(obj))
                DCTerms.description -> withJsonObject("description").putPairIfNotNull(
                    indexingMultiLang(
                        obj,
                        defaultLang
                    )
                )

                DCTerms.format -> putIfNotEmpty(
                    "format",
                    obj.visitWith(FormatVisitor) as JsonObject
                )

                DCTerms.license -> putIfNotEmpty(
                    "license",
                    obj.visitWith(LicenseVisitor) as JsonObject
                )

                // Optional properties
                DCTerms.title -> withJsonObject("title").putPairIfNotNull(
                    indexingMultiLang(
                        obj,
                        defaultLang,
                        trim = true
                    )
                )

                DCAT.mediaType -> putIfNotNull("media_type", obj.visitWith(MediaTypeVisitor))
                DCAT.downloadURL -> withJsonArray("download_url").addIfNotBlank(
                    obj.visitWith(
                        StandardVisitor
                    ) as String?
                )

                DCAT.byteSize -> putIfNotNull("byte_size", indexingInteger(obj))
                SPDX.checksum -> if (obj.isResource) putIfNotEmpty(
                    "checksum",
                    indexingChecksum(obj.asResource())
                )

                FOAF.page -> withJsonArray("page").addIfNotEmpty(
                    indexingPage(
                        obj,
                        defaultLang
                    )
                )

                DCTerms.language -> withJsonArray("language").addIfNotNull(
                    obj.visitWith(
                        LanguageVisitor
                    )
                )

                DCTerms.conformsTo -> withJsonArray("conforms_to").addIfNotEmpty(
                    indexingLabeledResource(obj)
                )

                DCTerms.issued -> putIfNotNull("issued", indexingDateTime(obj))
                DCTerms.modified -> putIfNotNull("modified", indexingDateTime(obj))
                DCTerms.rights -> putIfNotNull("rights", indexingLabeledResource(obj))
                ADMS.status -> putIfNotNull("status", indexingConceptScheme(obj, ADMSStatus))
                DCAT.spatialResolutionInMeters -> withJsonArray("spatial_resolution_in_meters").addIfNotNull(
                    indexingInteger(obj)
                )

                DCAT.temporalResolution -> withJsonArray("temporal_resolution").addIfNotBlank(
                    obj.visitWith(
                        LabeledVisitor
                    ) as String?
                )

                DCAT.accessService -> withJsonArray("access_service").addIfNotEmpty(
                    indexingAccessService(
                        obj,
                        defaultLang
                    )
                )

                DCAT.compressFormat -> putIfNotNull(
                    "compress_format",
                    obj.visitWith(DocumentVisitor) as JsonObject?
                )

                DCAT.packageFormat -> putIfNotNull(
                    "package_format",
                    obj.visitWith(DocumentVisitor) as JsonObject?
                )

                ODRL.hasPolicy -> putIfNotBlank(
                    "has_policy",
                    obj.visitWith(StandardVisitor) as String?
                )

                DCTerms.type -> {
                    val type = indexingType(obj, DistributionType)
                    type.remove("id")
                    putIfNotNull("type", type)
                }
            }
        }
    }

    else -> JsonObject() // it is not a resource
}

fun indexingAccessService(node: RDFNode, defaultLang: String): JsonObject = when (node) {
    is Resource -> JsonObject().apply {
        node.listProperties().forEachRemaining { (_, predicate, obj) ->
            when (predicate) {
                DCTerms.title -> withJsonObject("title").putPairIfNotNull(
                    indexingMultiLang(
                        obj,
                        defaultLang,
                        trim = true
                    )
                )

                DCTerms.description -> withJsonObject("description").putPairIfNotNull(
                    indexingMultiLang(
                        obj,
                        defaultLang
                    )
                )

                DCAT.endpointURL -> withJsonArray("endpoint_url").addIfNotBlank(
                    obj.visitWith(
                        StandardVisitor
                    ) as String?
                )

                DCAT.endpointDescription -> withJsonArray("endpoint_description").addIfNotBlank(
                    obj.visitWith(StandardVisitor) as String?
                )

                DCTerms.accessRights -> putIfNotNull(
                    "access_right",
                    indexingConceptScheme(obj, AccessRight)
                )

                DCTerms.license -> putIfNotEmpty(
                    "license",
                    obj.visitWith(LicenseVisitor) as JsonObject
                )

            }
        }
    }

    else -> JsonObject() // it is not a resource
}

fun indexingMetrics(metrics: Model): JsonObject = JsonObject().apply {
    metrics.listResourcesWithProperty(DQV.hasQualityMeasurement).forEach {
        if (it.isURIResource) {
            if (DCATAPUriSchema.isDatasetUriRef(it.uri)) {
                putIfNotBlank("id", DCATAPUriSchema.parseUriRef(it.uri).id)
            }

            it.listProperties().forEachRemaining { (_, predicate, obj) ->
                when (predicate) {
                    DQV.hasQualityAnnotation -> withJsonArray("quality_ann").addAll(
                        indexingAnnotations(obj)
                    )

                    DQV.hasQualityMeasurement -> withJsonObject("quality_meas").apply {
                        when (val name = measurementName(obj)) {
                            "scoring" -> putIfNotNull(name, indexingScore(obj))
                            "" -> {}
                            else -> {
                                withJsonArray(name).addIfNotEmpty(indexingMeasurements(obj))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun measurementName(node: RDFNode): String = when (node) {
    is Resource -> node.asResource().getProperty(DQV.isMeasurementOf)?.resource?.localName ?: ""
    else -> ""
}

fun indexingScore(node: RDFNode): Int? = when (node) {
    is Resource -> node.asResource().getProperty(DQV.value)?.`object`?.asLiteral()?.int
    else -> null
}

fun indexingMeasurements(node: RDFNode): JsonObject = when (node) {
    is Resource -> JsonObject().apply {
        val resource = node.asResource()
        putIfNotBlank("computedOn", resource.getProperty(DQV.computedOn)?.resource?.uri)
        putIfNotBlank("date", resource.getProperty(PROV.generatedAtTime)?.string)
        when (resource.getProperty(DQV.value).`object`.asLiteral().datatype) {
            XSDDatatype.XSDboolean -> putIfNotNull(
                "value",
                resource.getProperty(DQV.value)?.boolean
            )

            XSDDatatype.XSDinteger -> putIfNotNull("value", resource.getProperty(DQV.value)?.int)
            else -> JsonObject()
        }
    }

    else -> JsonObject()
}

fun indexingAnnotations(node: RDFNode): JsonArray = when (node) {
    is Resource -> JsonArray().apply {
        node.getProperty(OA.hasBody).resource.listProperties(SHACL.result)
            .forEach {
                add(JsonObject().apply {
                    putIfNotBlank(
                        "resultMessage",
                        it.subject.asResource().getProperty(SHACL.resultMessage)?.string
                    )
                    putIfNotBlank(
                        "resultSeverity",
                        it.subject.asResource()
                            .getProperty(SHACL.resultSeverity)?.resource?.localName
                    )
                    putIfNotBlank(
                        "sourceConstraintComponent",
                        it.subject.getProperty(SHACL.sourceConstraintComponent)?.resource?.localName
                    )
                })
            }
    }

    else -> JsonArray()
}

fun indexingSimpleMultiLang(node: RDFNode): Pair<String, String?> = when (node) {
    is Literal -> Pair(node.asLiteral().language.ifBlank { "en" }, node.asLiteral().string)
    else -> Pair("en", null)
}

fun indexingMultiLang(
    node: RDFNode,
    defaultLang: String,
    trim: Boolean = false
): Pair<String, JsonObject>? =
    when (node) {
        is Literal -> {
            if (node.asLiteral().lexicalForm.isNotBlank()) {
                val (machineTranslated, language, originalLanguage) = parseLangTag(
                    node.asLiteral().language,
                    defaultLang
                )
                val lang = JsonObject().putIfNotBlank(
                    "value",
                    node.string.let { if (trim) it.trim() else it })
                    .put("machineTranslated", machineTranslated)
                    .putIfNotBlank("originalLanguage", originalLanguage)
                Pair(language, lang)
            } else {
                null
            }
        }

        else -> null
    }

fun indexingKeyword(node: RDFNode, defaultLang: String): JsonObject = when (node) {
    is Literal -> JsonObject()
        .putIfNotBlank("id", node.asLiteral().lexicalForm.asUnicodeNormalized())
        .putIfNotBlank("label", node.asLiteral().string)
        .put("language", node.asLiteral().language.ifBlank { defaultLang })

    else -> JsonObject()
}

fun indexingLiteralWithId(node: RDFNode): JsonObject = when (node) {
    is Literal -> JsonObject()
        .putIfNotBlank("id", node.asLiteral().lexicalForm.asUnicodeNormalized())
        .putIfNotBlank("label", node.asLiteral().string)

    else -> JsonObject()
}

fun indexingLabeledResource(node: RDFNode) = when (node) {
    is Resource -> JsonObject()
        .putIfNotBlank("label", node.visitWith(LabeledVisitor) as String?)
        .putIfNotBlank("resource", node.asResource().uri)

    else -> JsonObject()
}

fun indexingConceptScheme(node: RDFNode, conceptScheme: ConceptScheme): JsonObject {
    return if (node.isURIResource && conceptScheme.getConcept(node.asResource()) != null) {
        JsonObject()
            .putIfNotBlank("label", conceptScheme.getConcept(node.asResource())?.prefLabel("en"))
            .putIfNotBlank("resource", node.asResource().uri)
    } else {
        JsonObject()
    }
}

fun indexingConceptSchemeMultiLang(node: RDFNode, conceptScheme: ConceptScheme): JsonObject {
    return if (node.isURIResource && conceptScheme.getConcept(node.asResource()) != null) {
        JsonObject()
            .putIfNotEmpty(
                "label",
                JsonObject().put("en", conceptScheme.getConcept(node.asResource())?.prefLabel("en"))
            )
            .putIfNotBlank("resource", node.asResource().uri)
    } else {
        JsonObject()
    }
}

fun indexingType(node: RDFNode, type: ConceptScheme): JsonObject {
    return if (node.isURIResource && type.getConcept(node.asResource()) != null) {
        val concept = type.getConcept(node.asResource()) as Concept
        JsonObject()
            .putIfNotBlank("id", concept.identifier)
            .putIfNotBlank("label", concept.prefLabel("en"))
            .putIfNotBlank("resource", node.asResource().uri)
    } else {
        val label = node.visitWith(LabeledVisitor) as String?
        JsonObject()
            .putIfNotBlank("id", label?.asUnicodeNormalized())
            .putIfNotBlank("label", label)
            .apply {
                if (node.isResource) putIfNotBlank("resource", node.asResource().uri)
            }
    }
}

fun indexingAdmsIdentifier(node: Resource): JsonObject = JsonObject().apply {
    if (node.isA(ADMS.Identifier)) {
        if (node.isURIResource) {
            putIfNotBlank("resource", node.asResource().uri)
        }
        node.listProperties().forEachRemaining { (_, predicate, obj) ->
            when (predicate) {
                SKOS.notation -> if (obj.isLiteral) {
                    putIfNotBlank("identifier", obj.asLiteral().string)
                    putIfNotBlank("scheme", obj.asLiteral().datatype?.uri)
                }
            }
        }
    }
}

fun indexingQualifiedRelation(node: Resource): JsonObject = JsonObject().apply {
    if (node.isA(DCAT.Relationship)) {
        node.listProperties().forEachRemaining { (_, predicate, obj) ->
            when (predicate) {
                DCTerms.relation -> if (obj.isResource) withJsonArray("relation").addIfNotBlank(
                    obj.visitWith(StandardVisitor) as String?
                )

                DCAT.hadRole -> if (obj.isResource) withJsonArray("had_role").addIfNotBlank(
                    obj.visitWith(StandardVisitor) as String?
                )
            }
        }
    }
}

fun indexingDecimal(node: RDFNode): Float? {
    return when (val value = node.visitWith(StandardVisitor)) {
        is String -> value.toFloatOrNull()
        is Float -> value
        is Int -> value.toFloat()
        else -> null
    }
}

fun indexingInteger(node: RDFNode): Int? {
    return when (val value = node.visitWith(StandardVisitor)) {
        is String -> value.toIntOrNull()
        is Int -> value
        is Float -> value.toInt()
        else -> null
    }
}

fun indexingChecksum(node: Resource): JsonObject = JsonObject().apply {
    if (node.isA(SPDX.Checksum)) {
        node.listProperties().forEachRemaining { (_, predicate, obj) ->
            when (predicate) {
                SPDX.algorithm -> if (obj.isResource) putIfNotBlank(
                    "algorithm",
                    obj.asResource().localName
                )

                SPDX.checksumValue -> if (obj.isLiteral) putIfNotBlank(
                    "checksum_value",
                    obj.asLiteral().string
                )
            }
        }
    }
}

fun indexingTemporal(node: Resource): JsonObject = JsonObject().apply {
    if (node.isA(DCTerms.PeriodOfTime)) {
        node.listProperties().forEachRemaining { (_, predicate, obj) ->
            when (predicate) {
                DCAT.startDate, SchemaDO.startDate -> if (obj.isLiteral && !containsKey("gte")) putIfNotBlank(
                    "gte",
                    normalizeDateTime(obj.asLiteral().string)
                )

                DCAT.endDate, SchemaDO.endDate -> if (obj.isLiteral && !containsKey("lte")) putIfNotBlank(
                    "lte",
                    normalizeDateTime(obj.asLiteral().string)
                )
                // time:hasBeginning (time:Instant)
                // time:hasEnd (time:Instant)
            }
        }
        val start = getString("gte")
        val end = getString("lte")
        if (start != null && end != null && start > end) {
            clear()
        }
    }
}

fun indexingPage(node: RDFNode, defaultLang: String): JsonObject = when (node) {
    is Resource -> JsonObject().apply {
        if (node.isURIResource) {
            put("resource", node.asResource().uri)
        }
        node.listProperties().forEachRemaining { (_, predicate, obj) ->
            when (predicate) {
                DCTerms.description -> withJsonObject("description").putPairIfNotNull(
                    indexingMultiLang(
                        obj,
                        defaultLang
                    )
                )

                DCTerms.title -> withJsonObject("title").putPairIfNotNull(
                    indexingMultiLang(
                        obj,
                        defaultLang,
                        trim = true
                    )
                )

                DCTerms.format -> putIfNotEmpty(
                    "format",
                    obj.visitWith(FormatVisitor) as JsonObject
                )
            }
        }
    }

    else -> JsonObject() // it is not a resource
}

fun indexingAgent(node: RDFNode): JsonObject = when (node) {
    is Resource -> JsonObject().apply {
        CorporateBodies.getConcept(node.asResource())?.let {
            putIfNotBlank(
                "type",
                it.resource.selectFrom(
                    listOf(
                        FOAF.Agent,
                        FOAF.Person,
                        FOAF.Organization
                    )
                )?.localName
                    ?: FOAF.Organization.localName
            )
            putIfNotBlank("name", it.label("en"))
            putIfNotBlank("homepage", it.resource.getPropertyResourceValue(FOAF.homepage)?.uri)
            putIfNotBlank("email", it.resource.getPropertyResourceValue(FOAF.mbox)?.uri)
            putIfNotBlank("resource", it.resource.uri)
        } ?: putIfNotBlank(
            "type",
            node.selectFrom(listOf(FOAF.Agent, FOAF.Person, FOAF.Organization))?.localName
                ?: FOAF.Agent.localName
        ).also {
            node.listProperties().forEachRemaining { (_, predicate, obj) ->
                when (predicate) {
                    FOAF.name -> putIfNotBlank("name", obj.visitWith(StandardVisitor) as String?)
                    FOAF.homepage -> putIfNotBlank(
                        "homepage",
                        obj.visitWith(StandardVisitor) as String?
                    )

                    FOAF.mbox -> putIfNotBlank("email", obj.visitWith(StandardVisitor) as String?)
                }
            }
            if (node.isURIResource) {
                putIfNotBlank("resource", node.asResource().uri)
            }
        }
    }

    else -> JsonObject() // is not a resource
}

fun indexingContactPoint(node: RDFNode): JsonObject {
    when (node) {
        is Resource -> {
            return JsonObject().apply {
                val type =
                    node.asResource().selectFrom(
                        listOf(
                            VCARD4.Kind,
                            VCARD4.Individual,
                            VCARD4.Organization
                        )
                    )?.localName
                putIfNotBlank("type", type)
                node.asResource().listProperties().forEachRemaining { (_, predicate, obj) ->
                    when (predicate) {
                        VCARD4.fn, VCARD4.hasName -> putIfNotBlank(
                            "name",
                            obj.visitWith(VCARDVisitor) as String?
                        )

                        VCARD4.hasEmail -> putIfNotBlank(
                            "email",
                            obj.visitWith(VCARDVisitor) as String?
                        )

                        VCARD4.hasAddress -> putIfNotBlank(
                            "address",
                            obj.visitWith(VCARDAddressVisitor) as String
                        )

                        VCARD4.hasTelephone -> putIfNotBlank(
                            "telephone",
                            obj.visitWith(VCARDValueVisitor) as String
                        )

                        VCARD4.hasURL -> withJsonArray("url").addIfNotBlank(
                            obj.visitWith(
                                VCARDVisitor
                            ) as String?
                        )

                        VCARD4.hasOrganizationName -> putIfNotBlank(
                            "organisation_name",
                            obj.visitWith(VCARDValueVisitor) as String
                        )
                    }
                }
                if (node.isURIResource) {
                    put("resource", node.asResource().uri)
                }
            }
        }

        else -> return JsonObject()
    }
}

fun indexingDateTime(node: RDFNode): String? = when (node) {
    is Literal -> normalizeDateTime(node.string)
    else -> null
}

fun indexingMetadataExtension(node: RDFNode): JsonObject = when (node) {
    is Resource -> {
        if (node.isA(DEXT.MetadataExtension)) {
            JsonObject().apply {
                node.listProperties().forEachRemaining { (_, p, o) ->
                    when (p) {
                        DEXT.isUsedBy -> withJsonArray("is_used_by").addIfNotBlank(o.asResource()?.uri)
                    }
                }
            }
        } else {
            JsonObject()
        }
    }

    else -> JsonObject()
}

fun legacyTranslationMeta(index: JsonObject, dataset: Resource) {
    val recordInfo = JsonObject().apply {
        DCATAPUriSchema.parseUriRef(dataset.uri).recordUriRef.let {
            val record = dataset.model.getResource(it)
            putIfNotBlank("received", record?.getProperty(EDP.transReceived)?.string)
            putIfNotBlank("issued", record?.getProperty(EDP.transIssued)?.string)
            putIfNotBlank("status", record?.getProperty(EDP.transStatus)?.resource?.let { status ->
                when (status) {
                    EDP.TransCompleted -> "completed"
                    EDP.TransInProcess -> "processing"
                    else -> "unknown"
                }
            } ?: "unknown")
        }
    }

    val translationMeta = JsonObject()
    val details = translationMeta.withJsonObject("details")
    val title = index.getJsonObject("title")
    title?.forEach { (key, value) ->
        details.withJsonObject(key)
            .putIfNotNull(
                "machine_translated",
                (value as JsonObject).getBoolean("machineTranslated")
            )
            .putIfNotBlank("original_language", value.getString("originalLanguage"))
            .putIfNotBlank("received", recordInfo.getString("received"))
            .putIfNotBlank("issued", recordInfo.getString("issued"))
        title.put(key, (value).getString("value"))
    }
    translationMeta.put("full_available_languages", details.fieldNames().toList())
    translationMeta.putIfNotBlank("status", recordInfo.getString("status"))
    index.put("translation_meta", translationMeta)

    val description = index.getJsonObject("description")
    description?.forEach { (key, value) ->
        description.put(key, (value as JsonObject).getString("value"))
    }

    index.getJsonArray("distributions", JsonArray())?.forEach {
        val distributionTitle = (it as JsonObject).getJsonObject("title")
        distributionTitle?.forEach { (key, value) ->
            distributionTitle.put(key, (value as JsonObject).getString("value"))
        }
        val distributionDescription = it.getJsonObject("description")
        distributionDescription?.forEach { (key, value) ->
            distributionDescription.put(key, (value as JsonObject).getString("value"))
        }
        val distributionPage = it.getJsonArray("page")
        distributionPage?.forEach { page ->
            val pageTitle = (page as JsonObject).getJsonObject("title")
            pageTitle?.forEach { (key, value) ->
                pageTitle.put(key, (value as JsonObject).getString("value"))
            }
            val pageDescription = page.getJsonObject("description")
            pageDescription?.forEach { (key, value) ->
                pageDescription.put(key, (value as JsonObject).getString("value"))
            }
        }
        val distributionAccessService = it.getJsonArray("access_service")
        distributionAccessService
            ?.map { elem -> elem as JsonObject }
            ?.forEach { accessService ->
                val accessServiceTitle = accessService.getJsonObject("title")
                accessServiceTitle?.forEach { (key, value) ->
                    accessServiceTitle.put(key, (value as JsonObject).getString("value"))
                }
                val accessServiceDescription = accessService.getJsonObject("description")
                accessServiceDescription?.forEach { (key, value) ->
                    accessServiceDescription.put(key, (value as JsonObject).getString("value"))
                }
            }
    }

    index.getJsonArray("page", JsonArray())
        ?.map { it as JsonObject }
        ?.forEach { page ->
            val pageTitle = page.getJsonObject("title")
            pageTitle?.forEach { (key, value) ->
                pageTitle.put(key, (value as JsonObject).getString("value"))
            }
            val pageDescription = page.getJsonObject("description")
            pageDescription?.forEach { (key, value) ->
                pageDescription.put(key, (value as JsonObject).getString("value"))
            }
        }

    index.getJsonArray("landing_page", JsonArray())
        ?.map { it as JsonObject }
        ?.forEach { landingPage ->
            val pageTitle = landingPage.getJsonObject("title")
            pageTitle?.forEach { (key, value) ->
                pageTitle.put(key, (value as JsonObject).getString("value"))
            }
            val pageDescription = landingPage.getJsonObject("description")
            pageDescription?.forEach { (key, value) ->
                pageDescription.put(key, (value as JsonObject).getString("value"))
            }
        }
}
