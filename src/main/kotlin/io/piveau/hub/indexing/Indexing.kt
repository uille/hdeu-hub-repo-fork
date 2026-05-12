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
import io.piveau.hub.util.rdf.CV
import io.piveau.hub.util.rdf.DPV
import io.piveau.hub.util.rdf.GEODCATAP
import io.piveau.hub.util.rdf.HEALTHDCATAP
import io.piveau.json.*
import io.piveau.profile.indexingResource
import io.piveau.rdf.*
import io.piveau.utils.normalizeDateTime
import io.piveau.vocabularies.*
import io.piveau.vocabularies.vocabulary.*
import io.vertx.core.Future
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.runBlocking
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.rdf.model.*
import org.apache.jena.sparql.vocabulary.FOAF
import org.apache.jena.vocabulary.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("io.piveau.hub.Indexing")
lateinit var catalogInstr: JsonObject
lateinit var datasetInstr: JsonObject

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
                            HEALTHDCATAP.hasCodeValues -> withJsonArray("has_code_values")
                                .addIfNotEmpty(indexingCodeValuesHealthData(obj))


                            HEALTHDCATAP.hasCodingSystem -> withJsonArray("has_coding_system")
                                .addIfNotEmpty(indexingHasCodingSystemsHealthData(obj))


                            HEALTHDCATAP.healthTheme -> withJsonArray("health_theme")
                                .addIfNotEmpty(indexingHealthThemeHealthData(obj))

                            // Health Data Access Body
                            HEALTHDCATAP.hdab -> putIfNotEmpty("hdab", indexingHealthAgent(obj))

                            // Variables – CSVWTableGroup at dataset level
                            HEALTHDCATAP.hasVariables -> {
                                val vars = extractVariablesFromTableGroup(obj.asResource())
                                if (vars.size() > 0) {
                                    withJsonArray("variables").addAll(vars)
                                }
                            }

                            else -> {
                                log.debug(
                                    "Unknown predicate for HEALTHDCATAP namespace: {}",
                                    predicate
                                )
                            }
                        }
                    } catch (e: Exception) {
                        log.error("Error when handling the predicate: $predicate", e)
                    }
                }

                if (predicate.nameSpace == DPV.getURI()) {
                    try {
                        when (predicate) {
                            DPV.hasPurpose -> indexingHasPurposeHealthData(obj).forEach { (first, second) ->
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

                if (predicate.nameSpace == DCTerms.NS) {  // Check the namespace of the predicate
                    try {
                        when (predicate) {
                            DCTerms.conformsTo -> withJsonArray("conforms_to")
                                .addIfNotEmpty(indexingConformsToHealthData(obj))

                            DCTerms.provenance -> indexingProvenanceHealthData(obj).forEach { (first, second) ->
                                withJsonObject("provenance").putIfNotBlank(first, second)
                            }

                            // Publisher
                            DCTerms.publisher -> putIfNotEmpty("publisher", indexingHealthAgent(obj))
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

                if (predicate.nameSpace == GEODCATAP.NS) {  // Check the namespace of the predicate
                    try {
                        when (predicate) {
                            // Health Data Access Body
                            GEODCATAP.custodian -> putIfNotEmpty("custodian", indexingHealthAgent(obj))
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

fun indexingHasPurposeHealthData(node: RDFNode): List<Pair<String, String?>> {
    val resultPairs = mutableListOf<Pair<String, String?>>()

    val resource = node.asResource()
    resource.listProperties().forEachRemaining { stmt ->
        val predicate = stmt.predicate
        val obj = stmt.`object`

        if (predicate == DCTerms.description) {
            indexingSimpleMultiLang(obj).let { resultPairs.add(it) }
        }
    }

    return resultPairs
}

fun indexingProvenanceHealthData(node: RDFNode): List<Pair<String, String?>> {
    val resultPairs = mutableListOf<Pair<String, String?>>()

    val resource = node.asResource()
    resource.listProperties().forEachRemaining { stmt ->
        val predicate = stmt.predicate
        val obj = stmt.`object`

        if (predicate == RDFS.label) {
            indexingSimpleMultiLang(obj).let { resultPairs.add(it) }
        }
    }

    return resultPairs
}

fun indexingHealthThemeHealthData(node: RDFNode): JsonObject {
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

            JsonObject()
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
            val link: String = node.asResource().uri

            JsonObject()
                .putIfNotBlank("label", link)
                .putIfNotBlank("resource", link)
                .putIfNotNull("id", link)
        }

        else -> JsonObject()
    }
}

fun indexingHealthAgent(node: RDFNode): JsonObject = when (node) {
    is Resource -> JsonObject().apply {

        // 1. TYPE = dct:type
        node.getProperty(DCTerms.type)?.`object`?.let { typeNode ->
            putIfNotBlank("type", typeNode.visitWith(StandardVisitor) as String?)
        }

        // 2. NAME = foaf:name
        node.getProperty(FOAF.name)?.`object`?.let { nameNode ->
            putIfNotBlank("name", nameNode.visitWith(StandardVisitor) as String?)
        }

        // 3. MULTILANG NOTE = dct:description → healthAgentNote
        val notesJson = JsonObject()
        node.listProperties(DCTerms.description).forEachRemaining { stmt ->
            val obj = stmt.`object`
            if (obj.isLiteral) {
                val lit = obj.asLiteral()
                val lang = lit.language?.takeIf { it.isNotBlank() } ?: "und"
                val value = lit.string
                if (!value.isNullOrBlank()) {
                    notesJson.put(lang, value)
                }
            }
        }
        if (!notesJson.isEmpty) {
            put("healthAgentNote", notesJson)
        }

        // 4. CONTACT POINT (cv:ContactPoint)
        val contactPointRes = node.getPropertyResourceValue(CV.contactPoint)
        contactPointRes?.let { cp ->

            // EMAIL = cv:email
            cp.getProperty(CV.email)?.`object`?.let { emailNode ->
                putIfNotBlank("email", emailNode.visitWith(StandardVisitor) as String?)
            }

            // HOMEPAGE = cv:contactPage
            cp.getProperty(CV.contactPage)?.`object`?.let { homepageNode ->
                putIfNotBlank("homepage", homepageNode.visitWith(StandardVisitor) as String?)
            }
        }
    }

    else -> JsonObject()
}

fun indexingCodeValuesHealthData(node: RDFNode): JsonObject {
    return when (node) {
        is Literal -> {
            val (lang, text) = indexingSimpleMultiLang(node)

            JsonObject()
                .put("label", JsonObject().put(lang, text))
        }

        else -> JsonObject()
    }
}

fun indexingWasGeneratedByHealthData(node: RDFNode): String? {
    if (node !is Resource) return null

    val stmt = node.getProperty(DCTerms.type) ?: return null
    val obj = stmt.`object`

    return when {
        obj.isResource -> obj.asResource().uri
        obj.isLiteral -> obj.asLiteral().string
        else -> null
    }
}

fun indexingHasLegalBasisHealthData(node: RDFNode): JsonArray {
    val jsonArray = JsonArray()

    if (node is Resource) {
        // Create the main object with label and resource
        val mainObject = JsonObject()

        // Collect all multilingual descriptions into one JsonObject
        val label = JsonObject()
        node.listProperties(DCTerms.description)
            .forEachRemaining { stmt ->
                val (lang, text) = indexingSimpleMultiLang(stmt.`object`)
                if (!text.isNullOrBlank()) {
                    label.put(lang, text)
                }
            }

        // Add the label object to the main object if it is not empty
        if (!label.isEmpty) {
            mainObject.put("label", label)
        }

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
                val uri = node.asResource().uri

                JsonObject()
                    .putIfNotBlank("label", uri)
                    .putIfNotBlank("resource", uri)
                    .putIfNotBlank("id", uri)
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

/**
 * Helper that, given a CSVW TableGroup Resource (the object of healthdcatap:hasVariables),
 * extracts all its CSVW column definitions and returns them as a Vert.x JsonArray.
 *
 * @param tg The CSVWTableGroup resource pointed to by healthdcatap:hasVariables.
 * @return A JsonArray containing the extracted column/variable definitions.
 */
fun extractVariablesFromTableGroup(tg: Resource): JsonArray {
    // Define the CSVW namespace and the properties we care about
    val csvwNS   = "http://www.w3.org/ns/csvw#"
    val pTABLE   = ResourceFactory.createProperty(csvwNS, "table")
    val pCOLUMN  = ResourceFactory.createProperty(csvwNS, "column")
    val pNAME    = ResourceFactory.createProperty(csvwNS, "name")
    val pTITLES  = ResourceFactory.createProperty(csvwNS, "titles")
    val pDTYPE   = ResourceFactory.createProperty(csvwNS, "datatype")
    val pPROPURL = ResourceFactory.createProperty(csvwNS, "propertyUrl")

    val vars = JsonArray()

    // Iterate the csvw:table children of the TableGroup
    tg.listProperties(pTABLE)?.forEachRemaining { tblStmt ->
        val tbl = tblStmt.`object`.asResource()

        // For each csvw:table, iterate its csvw:column children
        tbl.listProperties(pCOLUMN).forEachRemaining { colStmt ->
            val c = colStmt.`object`.asResource()

            val titlesJson = indexingMultiLang2(c, pTITLES, "en")
            val descJson   = indexingMultiLang2(c, DCTerms.description, "en")

            val name  = c.getProperty(pNAME)?.`object`?.asLiteral()?.string.orEmpty()
            val dtype = c.getProperty(pDTYPE)?.`object`?.asLiteral()?.string.orEmpty()

            val propertyUrl = c.getProperty(pPROPURL)?.`object`?.let { node ->
                when {
                    node.isResource -> node.asResource().uri
                    node.isLiteral  -> node.asLiteral().string
                    else            -> ""
                }
            }.orEmpty()

            vars.add(
                JsonObject()
                    .put("name", name)
                    .put("titles", titlesJson)
                    .put("datatype", dtype)
                    .put("description", descJson)
                    .put("propertyUrl",  propertyUrl)
            )
        }
    }
    return vars
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
    is Literal -> Pair(node.asLiteral().language.ifBlank { "en" }.take(2), node.asLiteral().string)
    else -> Pair("en", null)
}

/**
 * Collects all literal values for a given property from a resource into a JSON object keyed by language.
 *
 * Example output:
 * { "en": "alalla", "bg": "…", "fr": "…" }
 *
 * Language tags like "bg-t-en-t0-mtec" are normalized to "bg".
 */
fun indexingMultiLang2(resource: Resource, property: Property, defaultLang: String = "en"): JsonObject {
    val out = JsonObject()

    resource.listProperties(property).forEachRemaining { st ->
        val obj = st.`object`
        if (!obj.isLiteral) return@forEachRemaining

        val lit = obj.asLiteral()
        val text = lit.string?.trim().orEmpty()
        if (text.isBlank()) return@forEachRemaining

        val lang = normalizeLang2(lit.language).ifBlank { defaultLang }
        out.put(lang, text)
    }

    // Optional: ensure there is at least defaultLang key (only if you want)
    // if (out.isEmpty) out.put(defaultLang, "")

    return out
}

/**
 * Normalizes RDF language tags to a 2-letter language code.
 *
 * Supports:
 * - Standard tags: "en", "en-GB", "pt_BR" -> "en", "pt"
 * - Machine translation tags used by this pipeline: "bg-t-en-t0-mtec" -> "bg"
 *
 * Falls back to "en" if tag is blank or cannot be parsed.
 */
fun normalizeLang2(tag: String?): String {
    val t = tag?.trim().orEmpty()
    if (t.isBlank()) return "en"

    // Machine-translation tag format: "<target>-t-<source>-t0-mtec"
    // We want the target part (before "-t-")
    val mtPrefix = t.substringBefore("-t-", missingDelimiterValue = t)

    // Reduce "en-GB" or "pt_BR" etc to primary language subtag
    val primary = mtPrefix.substringBefore('-').substringBefore('_')

    return primary.takeIf { it.length >= 2 }?.substring(0, 2)?.lowercase() ?: "en"
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
