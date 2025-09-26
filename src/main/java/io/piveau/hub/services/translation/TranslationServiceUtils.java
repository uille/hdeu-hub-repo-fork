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
package io.piveau.hub.services.translation;

import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.hub.util.rdf.DPV;
import io.piveau.hub.util.rdf.HEALTHDCATAP;
import io.piveau.vocabularies.vocabulary.ADMS;
import io.piveau.vocabularies.vocabulary.EDP;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDFS;

public class TranslationServiceUtils {

    public static JsonObject callbackParameters;
    public static List<String> translationLanguages;
    private static final String T_0_MTEC = "-t0-mtec";

    public static String buildLanguageTag(String originalLanguage, String targetLanguage) {
        return targetLanguage + "-t-" + originalLanguage + T_0_MTEC;
    }

    public static boolean isTranslationCompleted(DatasetHelper helper) {
        return helper != null && helper.recordResource().hasProperty(EDP.transStatus, EDP.TransCompleted);
    }

    public static Map<String, String> propertyToLanguageMap(Resource resource, Property property, String defaultLanguage) {
        LinkedHashMap<String, String> languages = new LinkedHashMap<>();
        resource.listProperties(property)
                .filterKeep(stm -> stm.getObject().isLiteral())
                .filterDrop(stm -> stm.getLiteral().getString().isBlank())
                .filterDrop(stm -> stm.getLiteral().getLanguage().endsWith(T_0_MTEC))
                .forEachRemaining(statement -> {
                    Literal obj = statement.getObject().asLiteral();
                    if (obj.getLanguage().isBlank()) {
                        languages.put(defaultLanguage, obj.getString());
                    } else {
                        languages.put(obj.getLanguage(), obj.getString());
                    }
                });
        return languages;
    }

    public static JsonObject buildTranslationRequest(DatasetHelper helper, DatasetHelper oldHelper) {
        String defaultLanguage = helper.getCatalogueInfo().getString("langCode");

        Resource oldResource = isTranslationCompleted(oldHelper) ? oldHelper.resource() : null;

        JsonObject dict = new JsonObject();

        JsonObject datasetSubject = new JsonObject();

        // Dataset title
        JsonObject datasetTitleProperty = buildDictEntry(helper.resource(), oldResource, DCTerms.title, defaultLanguage);
        if (!datasetTitleProperty.isEmpty()) {
            datasetSubject.put(DCTerms.title.getURI(), datasetTitleProperty);
        }

        // Dataset description
        JsonObject datasetDescriptionProperty = buildDictEntry(helper.resource(), oldResource, DCTerms.description, defaultLanguage);
        if (!datasetDescriptionProperty.isEmpty()) {
            datasetSubject.put(DCTerms.description.getURI(), datasetDescriptionProperty);
        }

        // Dataset publisher note
        JsonObject datasetPublisherNoteProperty = buildDictEntry(helper.resource(), oldResource, HEALTHDCATAP.publisherNote, defaultLanguage);
        if (!datasetPublisherNoteProperty.isEmpty()) {
            datasetSubject.put(HEALTHDCATAP.publisherNote.getURI(), datasetPublisherNoteProperty);
        }

        // Dataset population coverage
        JsonObject datasetPopulationCoverageProperty = buildDictEntry(helper.resource(), oldResource, HEALTHDCATAP.populationCoverage, defaultLanguage);
        if (!datasetPopulationCoverageProperty.isEmpty()) {
            datasetSubject.put(HEALTHDCATAP.populationCoverage.getURI(), datasetPopulationCoverageProperty);
        }

        if (!datasetSubject.isEmpty()) {
            dict.put(helper.resource().getURI(), datasetSubject);
        }

        // Dataset distributions
        processDistributions(helper.resource(), oldResource, DCAT.distribution, dict, defaultLanguage);

        // Dataset sample analytics
        processDistributions(helper.resource(), oldResource, HEALTHDCATAP.analytics, dict, defaultLanguage);

        // Dataset sample distributions
        processDistributions(helper.resource(), oldResource, ADMS.sample, dict, defaultLanguage);

        // Dataset purpose
        helper.resource().listProperties(DPV.hasPurpose).forEachRemaining(statement -> {
            if (!statement.getObject().isResource()) {
                return;
            }

            Resource newPurpose = statement.getObject().asResource();
            Resource oldPurpose = oldResource != null
                ? oldResource.getModel().getProperty(DPV.hasPurpose.toString()).asResource()
                : null;

            JsonObject purposeDescriptionProperty = buildDictEntry(newPurpose, oldPurpose, DCTerms.description, defaultLanguage);
            if (purposeDescriptionProperty.isEmpty()) {
                return;
            }

            JsonObject purposeSubject = new JsonObject();
            purposeSubject.put(DCTerms.description.getURI(), purposeDescriptionProperty);
            dict.put(DPV.hasPurpose.toString() + newPurpose.toString(), purposeSubject);
        });

        // Dataset legal basis
        helper.resource().listProperties(DPV.hasLegalBasis).forEachRemaining(statement -> {
            if (!statement.getObject().isResource()) {
                return;
            }

            Resource newLegalBasis = statement.getObject().asResource();
            Resource oldLegalBasis = oldResource != null
                ? oldResource.getModel().getProperty(DPV.hasLegalBasis.toString()).asResource()
                : null;

            JsonObject legalBasisDescriptionProperty = buildDictEntry(newLegalBasis, oldLegalBasis,
                DCTerms.description, defaultLanguage);
            if (legalBasisDescriptionProperty.isEmpty()) {
                return;
            }

            JsonObject legalBasisSubject = new JsonObject();
            legalBasisSubject.put(DCTerms.description.getURI(), legalBasisDescriptionProperty);
            dict.put(newLegalBasis.toString(), legalBasisSubject);
        });

        // Dataset provenance
        helper.resource().listProperties(DCTerms.provenance).forEachRemaining(statement -> {
            if (!statement.getObject().isResource()) {
                return;
            }

            Resource newProvenance = statement.getObject().asResource();
            Resource oldProvenance = oldResource != null
                ? oldResource.getModel().getProperty(DCTerms.provenance.toString()).asResource()
                : null;

            JsonObject provenanceLabelProperty = buildDictEntry(newProvenance, oldProvenance, RDFS.label, defaultLanguage);
            if (provenanceLabelProperty.isEmpty()) {
                return;
            }

            JsonObject provenanceSubject = new JsonObject();
            provenanceSubject.put(RDFS.label.getURI(), provenanceLabelProperty);
            dict.put(DCTerms.provenance.toString() + newProvenance.toString(), provenanceSubject);
        });

        JsonObject translationRequest = new JsonObject();
        if (!dict.isEmpty()) {
            translationRequest.put("id", helper.uriRef());
            translationRequest.put("data", new JsonObject()
                    .put("catalogueId", helper.catalogueId())
                    .put("defaultLanguage", helper.getCatalogueInfo().getString("langCode")));
            translationRequest.put("dict", dict);
            translationRequest.put("callback", callbackParameters);
        }

        return translationRequest;
    }

    /**
     * For each URI‐valued object of property on newSubject, build its title and description entries
     * and put them in the dict JSON object under its URI.
     *
     * @param newSubject      The new subject resource containing the properties to translate.
     * @param oldSubject      The old subject resource to compare against, can be null if no previous
     *                        version exists.
     * @param property        The property that contains the list of resources to process.
     * @param dict            The JSON object to populate with the fields to be translated.
     * @param defaultLanguage The default language to use for translations.
     */
    private static void processDistributions(
        Resource newSubject,
        Resource oldSubject,
        Property property,
        JsonObject dict,
        String defaultLanguage) {

        newSubject.listProperties(property).forEachRemaining(statement -> {
            if (!statement.getObject().isURIResource()) {
                return;
            }

            Resource newResource = statement.getResource();
            Resource oldResource = oldSubject != null
                ? oldSubject.getModel().getResource(newResource.getURI())
                : null;

            JsonObject entry = new JsonObject();

            // Title
            JsonObject title = buildDictEntry(newResource, oldResource, DCTerms.title, defaultLanguage);
            if (!title.isEmpty()) {
                entry.put(DCTerms.title.getURI(), title);
            }

            // Description
            JsonObject desc = buildDictEntry(newResource, oldResource, DCTerms.description, defaultLanguage);
            if (!desc.isEmpty()) {
                entry.put(DCTerms.description.getURI(), desc);
            }

            if (!entry.isEmpty()) {
                dict.put(newResource.getURI(), entry);
            }
        });
    }

    private static JsonObject buildDictEntry(Resource resource, Resource oldResource, Property property, String defaultLanguage) {

        Map<String, String> propertyLanguages = propertyToLanguageMap(resource, property, defaultLanguage);
        if (propertyLanguages.isEmpty()) {
            return new JsonObject();
        }

        String language = defaultLanguage;
        if (!propertyLanguages.containsKey(defaultLanguage)) {
            language = propertyLanguages.keySet().iterator().next();
        }

        String value = propertyLanguages.get(language);

        if (oldResource != null) {
            Map<String, String> oldMap = propertyToLanguageMap(oldResource, property, defaultLanguage);
            if (value.equals(oldMap.get(language))) {
                getAllTranslations(oldResource, property)
                        .forEach(literal -> resource.addLiteral(property, literal));
                return new JsonObject();
            }
        }

        List<String> targetLanguages = new ArrayList<>(translationLanguages);
        targetLanguages.removeAll(propertyLanguages.keySet());

        JsonObject entry = new JsonObject();
        if (!targetLanguages.isEmpty()) {
            entry
                    .put("value", value)
                    .put("sourceLanguage", language)
                    .put("targetLanguages", targetLanguages);
        }

        return entry;
    }

    private static List<Literal> getAllTranslations(Resource resource, Property property) {
        return resource.listProperties(property)
                .filterKeep(stm -> stm.getObject().isLiteral())
                .filterKeep(stm -> stm.getLanguage().endsWith(T_0_MTEC))
                .mapWith(Statement::getLiteral)
                .toList();
    }

    private static void removeAllTranslations(Resource resource, Property property) {
        List<Statement> statements = resource.listProperties(property)
                .filterKeep(stm -> stm.getObject().isLiteral())
                .filterKeep(stm -> stm.getLanguage().endsWith(T_0_MTEC))
                .toList();
        resource.getModel().remove(statements);
    }

    public static void applyTranslations(Model model, JsonObject dict) {
        dict.forEach(entry -> {
            String entryKey = entry.getKey();
            Resource resource = resolveResource(model, entryKey);

            // TODO: Handle case where resource is null
            // if (resource == null) {
            //   System.err.println("⚠️ Could not resolve resource for key: " + entryKey);
            //   return;
            // }
            JsonObject properties = (JsonObject) entry.getValue();
            properties.forEach(propEntries -> {
                Property property = model.getProperty(propEntries.getKey());
                removeAllTranslations(resource, property);
                JsonObject propertyValues = (JsonObject) propEntries.getValue();
                String sourceLanguage = propertyValues.getString("sourceLanguage");
                JsonObject translations = propertyValues.getJsonObject("translations");
                translations.getMap().forEach((lang, value) -> {
                    if (value instanceof String) {
                        Literal literal = model.createLiteral((String) value, buildLanguageTag(sourceLanguage, lang));
                        resource.addProperty(property, literal);
                    }
                });
            });
        });
    }

    private static Resource resolveResource(Model model, String key) {
        // Direct URI lookup
        Resource resource = model.getResource(key);
        if (model.containsResource(resource)) {
            return resource;
        }

        // QName expansion (e.g. "internalURI:hasLegalBasis0")
        if (!key.startsWith("http") && key.contains(":")) {
            String[] parts = key.split(":", 2);
            String prefix = parts[0];
            String local = parts[1];
            String ns = model.getNsPrefixURI(prefix);
            if (ns != null) {
                Resource q = model.getResource(ns + local);
                if (model.containsResource(q)) {
                    return q;
                }
            }
        }

        // Skolemized blank nodes of the form <propURI>_:<bnodeId>
        if (key.contains("_:")) {
            // Extract the linking‐property URI
            String propUri = key.substring(0, key.indexOf("_:"));
            Property linkProp = model.getProperty(propUri);

            // Find the single triple (?s linkProp _:bN)
            StmtIterator sit = model.listStatements(null, linkProp, (RDFNode) null);
            if (sit.hasNext()) {
                RDFNode obj = sit.nextStatement().getObject();
                if (obj.isResource()) {
                    return obj.asResource();
                }
            }
        }

        // If no resource could be resolved, return null
        return null;
    }

}
