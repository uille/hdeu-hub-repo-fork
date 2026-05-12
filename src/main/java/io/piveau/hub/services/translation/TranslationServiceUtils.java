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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDFS;

/**
 * Utility methods that collect translation candidates from RDF datasets, build translation
 * requests, and apply machine translated literals back into the model.
 */
public class TranslationServiceUtils {

  public static JsonObject callbackParameters;
  public static List<String> translationLanguages;
  private static final String T_0_MTEC = "-t0-mtec";

  private static final String CSVW_NS = "http://www.w3.org/ns/csvw#";
  private static final Property CSVW_TABLE = ResourceFactory.createProperty(CSVW_NS, "table");
  private static final Property CSVW_COLUMN = ResourceFactory.createProperty(CSVW_NS, "column");
  private static final Property CSVW_TITLES = ResourceFactory.createProperty(CSVW_NS, "titles");
  private static final Property CSVW_NAME = ResourceFactory.createProperty(CSVW_NS, "name");

  /**
   * Builds an MT@EC compliant language tag such as {@code fr-t-en-t0-mtec} indicating source and
   * target languages.
   *
   * @param originalLanguage ISO code of the source literal.
   * @param targetLanguage ISO code we are translating into.
   * @return BCP 47 compatible tag with MT@EC suffix.
   */
  public static String buildLanguageTag(String originalLanguage, String targetLanguage) {
    return targetLanguage + "-t-" + originalLanguage + T_0_MTEC;
  }

  /**
   * Checks whether the record already holds the {@code TransCompleted} status.
   *
   * @param helper dataset helper pointing to the record resource.
   * @return {@code true} if translation finished, {@code false} otherwise.
   */
  public static boolean isTranslationCompleted(DatasetHelper helper) {
    return helper != null && helper.recordResource()
        .hasProperty(EDP.transStatus, EDP.TransCompleted);
  }

  /**
   * Collects literal values for a property keyed by language, ignoring MT@EC generated tags.
   *
   * @param resource source RDF resource.
   * @param property property to inspect.
   * @param defaultLanguage fallback language when literals are untagged.
   * @return insertion ordered language to literal map.
   */
  public static Map<String, String> propertyToLanguageMap(Resource resource, Property property,
      String defaultLanguage) {
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

  /**
   * Builds a translation request payload describing which literals need translation compared to
   * a previous version of the dataset.
   *
   * @param helper helper wrapping the current dataset.
   * @param oldHelper helper of the previously stored dataset (may be {@code null}).
   * @return translation request JSON or empty object if nothing needs translation.
   */
  public static JsonObject buildTranslationRequest(DatasetHelper helper, DatasetHelper oldHelper) {
    String defaultLanguage = helper.getCatalogueInfo().getString("langCode");

    Resource oldResource = isTranslationCompleted(oldHelper) ? oldHelper.resource() : null;

    JsonObject dict = new JsonObject();

    JsonObject datasetSubject = new JsonObject();

    // Dataset title
    JsonObject datasetTitleProperty = buildDictEntry(helper.resource(), oldResource, DCTerms.title,
        defaultLanguage);
    if (!datasetTitleProperty.isEmpty()) {
      datasetSubject.put(DCTerms.title.getURI(), datasetTitleProperty);
    }

    // Dataset description
    JsonObject datasetDescriptionProperty = buildDictEntry(helper.resource(), oldResource,
        DCTerms.description, defaultLanguage);
    if (!datasetDescriptionProperty.isEmpty()) {
      datasetSubject.put(DCTerms.description.getURI(), datasetDescriptionProperty);
    }

    // Dataset publisher note
    // TODO: deprecated property should be removed (publisher note now exists under dct:publisher->dct:description)
    JsonObject datasetPublisherNoteProperty = buildDictEntry(helper.resource(), oldResource,
        HEALTHDCATAP.publisherNote, defaultLanguage);
    if (!datasetPublisherNoteProperty.isEmpty()) {
      datasetSubject.put(HEALTHDCATAP.publisherNote.getURI(), datasetPublisherNoteProperty);
    }

    // Dataset population coverage
    JsonObject datasetPopulationCoverageProperty = buildDictEntry(helper.resource(), oldResource,
        HEALTHDCATAP.populationCoverage, defaultLanguage);
    if (!datasetPopulationCoverageProperty.isEmpty()) {
      datasetSubject.put(HEALTHDCATAP.populationCoverage.getURI(),
          datasetPopulationCoverageProperty);
    }

    if (!datasetSubject.isEmpty()) {
      dict.put(helper.resource().getURI(), datasetSubject);
    }

    // Dataset distributions
    processDistributions(helper.resource(), oldResource, DCAT.distribution, dict, defaultLanguage);

    // Dataset sample analytics
    processDistributions(helper.resource(), oldResource, HEALTHDCATAP.analytics, dict,
        defaultLanguage);

    // Dataset sample distributions
    processDistributions(helper.resource(), oldResource, ADMS.sample, dict, defaultLanguage);

    // Dataset variables – CSVWTableGroup at dataset level via healthdcatap:hasVariables
    processCsvwColumnsForHasVariables(helper.resource(), oldResource, dict, defaultLanguage);

    // Dataset purpose (blank nodes are common -> skolemize + snapshot iteration)
    List<Statement> purposeStmts = helper.resource().listProperties(DPV.hasPurpose).toList();
    for (Statement statement : purposeStmts) {
      if (!statement.getObject().isResource()) {
        continue;
      }
      Resource newPurposeRaw = statement.getObject().asResource();
      // Skolemize if blank node so we have a stable URI key
      Resource newPurpose = ensureSkolem(newPurposeRaw, helper.resource().getURI(), "purpose");
      // Resolve old purpose by URI (if present)
      Resource oldPurpose = null;
      if (oldResource != null && newPurpose.isURIResource() && newPurpose.getURI() != null) {
        Resource candidate = oldResource.getModel().getResource(newPurpose.getURI());
        if (oldResource.getModel().containsResource(candidate)) {
          oldPurpose = candidate;
        }
      }

      JsonObject purposeDescriptionProperty = buildDictEntry(
          newPurpose,
          oldPurpose,
          DCTerms.description,
          defaultLanguage
      );
      if (purposeDescriptionProperty.isEmpty()) {
        continue;
      }

      JsonObject purposeSubject = new JsonObject();
      purposeSubject.put(DCTerms.description.getURI(), purposeDescriptionProperty);

      // Use stable URI as key
      if (newPurpose.isURIResource() && newPurpose.getURI() != null) {
        dict.put(newPurpose.getURI(), purposeSubject);
      }
    }

    // Dataset legal basis (safe snapshot iteration because ensureSkolem mutates the model)
    List<Statement> legalBasisStmts = helper.resource().listProperties(DPV.hasLegalBasis).toList();
    for (Statement statement : legalBasisStmts) {
      if (!statement.getObject().isResource()) {
        continue;
      }
      Resource newLegalBasisRaw = statement.getObject().asResource();
      // Skolemize if blank node so we have a stable URI key
      Resource newLegalBasis = ensureSkolem(newLegalBasisRaw, helper.resource().getURI(), "legal-basis");

      // Resolve old legal basis by URI (if present)
      Resource oldLegalBasis = null;
      if (oldResource != null && newLegalBasis.isURIResource() && newLegalBasis.getURI() != null) {
        Resource candidate = oldResource.getModel().getResource(newLegalBasis.getURI());
        if (oldResource.getModel().containsResource(candidate)) {
          oldLegalBasis = candidate;
        }
      }

      JsonObject legalBasisDescriptionProperty =
          buildDictEntry(newLegalBasis, oldLegalBasis, DCTerms.description, defaultLanguage);

      if (legalBasisDescriptionProperty.isEmpty()) {
        continue;
      }

      JsonObject legalBasisSubject = new JsonObject();
      legalBasisSubject.put(DCTerms.description.getURI(), legalBasisDescriptionProperty);

      // Use stable URI as key
      if (newLegalBasis.isURIResource() && newLegalBasis.getURI() != null) {
        dict.put(newLegalBasis.getURI(), legalBasisSubject);
      }
    }

    // Dataset provenance (blank nodes are common -> skolemize + snapshot iteration)
    List<Statement> provStmts = helper.resource().listProperties(DCTerms.provenance).toList();
    for (Statement statement : provStmts) {
      if (!statement.getObject().isResource()) {
        continue;
      }
      Resource newProvenanceRaw = statement.getObject().asResource();
      // Skolemize if blank node so we have a stable URI key
      Resource newProvenance = ensureSkolem(newProvenanceRaw, helper.resource().getURI(), "provenance");

      // Resolve old provenance by URI (if present)
      Resource oldProvenance = null;
      if (oldResource != null && newProvenance.isURIResource() && newProvenance.getURI() != null) {
        Resource candidate = oldResource.getModel().getResource(newProvenance.getURI());
        if (oldResource.getModel().containsResource(candidate)) {
          oldProvenance = candidate;
        }
      }

      JsonObject provenanceLabelProperty = buildDictEntry(
          newProvenance,
          oldProvenance,
          RDFS.label,
          defaultLanguage
      );
      if (provenanceLabelProperty.isEmpty()) {
        continue;
      }

      JsonObject provenanceSubject = new JsonObject();
      provenanceSubject.put(RDFS.label.getURI(), provenanceLabelProperty);

      // Use stable URI as key
      if (newProvenance.isURIResource() && newProvenance.getURI() != null) {
        dict.put(newProvenance.getURI(), provenanceSubject);
      }
    }

    // Dataset keywords – submit each literal as an independent translation unit
    AtomicInteger keywordIndex = new AtomicInteger(0);

    helper.resource().listProperties(DCAT.keyword)
        .filterKeep(stm -> stm.getObject().isLiteral())
        .filterDrop(stm -> stm.getLiteral().getString().isBlank())
        // Ignore existing machine translations like "*-t0-mtec"
        .filterDrop(stm -> stm.getLiteral().getLanguage().endsWith(T_0_MTEC))
        .forEachRemaining(stm -> {
          Literal keywordLit = stm.getLiteral();

          // Determine source language (fallbacks)
          String lang = keywordLit.getLanguage();
          if (lang == null || lang.isBlank()) {
            lang = defaultLanguage; // e.g. "el"
          }
          if (lang == null || lang.isBlank()) {
            // last‐resort fallback
            lang = "en";
          }

          final String sourceLang = lang;
          final String sourcePrimary = sourceLang.split("[-_]")[0];

          // Build target languages = all translationLanguages except same primary language
          List<String> targetLangs = new ArrayList<>(
              translationLanguages != null ? translationLanguages : List.of()
          );
          targetLangs.removeIf(l -> {
            String tlPrimary = l.split("[-_]")[0];
            return tlPrimary.equalsIgnoreCase(sourcePrimary);
          });

          if (targetLangs.isEmpty()) {
            return;
          }

          // Describe the literal and language pairing passed to MT@EC
          JsonObject keywordProperty = new JsonObject()
              .put("value", keywordLit.getString())
              .put("sourceLanguage", sourceLang)
              .put("targetLanguages", targetLangs);

          JsonObject keywordSubject = new JsonObject()
              .put(DCAT.keyword.getURI(), keywordProperty);

          // Synthetic key combines dataset URI and running index to keep entries unique
          String subjectKey =
              helper.resource().getURI() + "#keyword#" + keywordIndex.getAndIncrement();

          dict.put(subjectKey, keywordSubject);
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
   * @param dataset      The new subject resource containing the properties to translate.
   * @param oldDataset      The old subject resource to compare against, can be null if no previous
   *                        version exists.
   * @param property        The property that contains the list of resources to process.
   * @param dict            The JSON object to populate with the fields to be translated.
   * @param defaultLanguage The default language to use for translations.
   */
  private static void processDistributions(
      Resource dataset,
      Resource oldDataset,
      Property property,
      JsonObject dict,
      String defaultLanguage) {

    if (dataset == null || dataset.getURI() == null) {
      // Dataset should be URI-based in DCAT; without a dataset URI we can't skolemize safely.
      return;
    }

    final String datasetUri = dataset.getURI();

    // Snapshot statements to avoid concurrent modification while skolemizing
    List<Statement> distributionStmts = dataset.listProperties(property).toList();

    for (Statement statement : distributionStmts) {
      if (!statement.getObject().isResource()) {
        continue;
      }

      // Distribution (may be URI resource OR blank node)
      Resource newResourceRaw = statement.getObject().asResource();

      // Skolemize blank nodes so downstream JSON keys always use URIs
      Resource newResource = ensureSkolem(newResourceRaw, datasetUri, "distribution");

      // Resolve the matching resource from the previous model to detect unchanged literals
      Resource oldResource = null;
      if (oldDataset != null && newResource.isURIResource() && newResource.getURI() != null) {
        Resource candidate = oldDataset.getModel().getResource(newResource.getURI());
        if (oldDataset.getModel().containsResource(candidate)) {
          oldResource = candidate;
        }
      }

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
        // newResource has a URI after ensureSkolem(...)
        dict.put(newResource.getURI(), entry);
      }
    }
  }

  /**
   * Extracts CSVW table/column titles and descriptions from the dataset-level
   * {@code healthdcatap:hasVariables} property (CSVWTableGroup_Shape).
   *
   * <p>Variables have been moved from {@code adms:sample} distributions to the dataset level.
   * Each object of {@code healthdcatap:hasVariables} is a CSVW TableGroup whose
   * {@code csvw:table} children contain {@code csvw:column} children with translatable
   * {@code csvw:titles} and {@code dct:description} literals.
   */
  private static void processCsvwColumnsForHasVariables(
      Resource dataset,
      Resource oldDataset,
      JsonObject dict,
      String defaultLanguage) {

    if (dataset == null || dataset.getURI() == null) {
      return;
    }
    final String datasetUri = dataset.getURI();

    // SNAPSHOT: healthdcatap:hasVariables statements
    List<Statement> tableGroupStmts = dataset.listProperties(HEALTHDCATAP.hasVariables).toList();
    for (Statement tgStmt : tableGroupStmts) {
      if (!tgStmt.getObject().isResource()) {
        continue;
      }

      // Skolemize the CSVW TableGroup to a deterministic URI before matching
      Resource newTableGroup = ensureSkolem(tgStmt.getResource(), datasetUri, "csvw-tablegroup");

      // oldTableGroup only resolvable via URI from oldDataset
      Resource oldTableGroup = null;
      if (oldDataset != null && newTableGroup.isURIResource() && newTableGroup.getURI() != null) {
        Resource candidate = oldDataset.getModel().getResource(newTableGroup.getURI());
        if (oldDataset.getModel().containsResource(candidate)) {
          oldTableGroup = candidate;
        }
      }

      // SNAPSHOT: csvw:table statements
      List<Statement> tableStmts = newTableGroup.listProperties(CSVW_TABLE).toList();
      for (Statement tableStmt : tableStmts) {
        if (!tableStmt.getObject().isResource()) {
          continue;
        }

        // Skolemize Table (may be bnode)
        Resource newTable = ensureSkolem(tableStmt.getResource(), datasetUri, "csvw-table");

        Resource oldTable = null;
        if (oldTableGroup != null && newTable.isURIResource() && newTable.getURI() != null) {
          Resource candidate = oldTableGroup.getModel().getResource(newTable.getURI());
          if (oldTableGroup.getModel().containsResource(candidate)) {
            oldTable = candidate;
          }
        }

        // SNAPSHOT: csvw:column statements
        List<Statement> colStmts = newTable.listProperties(CSVW_COLUMN).toList();
        for (Statement colStmt : colStmts) {
          if (!colStmt.getObject().isResource()) {
            continue;
          }

          // Skolemize Column (may be bnode) -> guaranteed URI resource
          Resource newColumn = ensureSkolemCsvwColumn(colStmt.getResource(), datasetUri, newTable);

          Resource oldColumn = null;
          if (oldTable != null && newColumn.isURIResource() && newColumn.getURI() != null) {
            Resource candidate = oldTable.getModel().getResource(newColumn.getURI());
            if (oldTable.getModel().containsResource(candidate)) {
              oldColumn = candidate;
            }
          }

          JsonObject columnEntry = new JsonObject();

          JsonObject titles = buildDictEntry(newColumn, oldColumn, CSVW_TITLES, defaultLanguage);
          if (!titles.isEmpty()) {
            columnEntry.put(CSVW_TITLES.getURI(), titles);
          }

          JsonObject desc = buildDictEntry(newColumn, oldColumn, DCTerms.description, defaultLanguage);
          if (!desc.isEmpty()) {
            columnEntry.put(DCTerms.description.getURI(), desc);
          }

          if (!columnEntry.isEmpty()) {
            String key = (newColumn.isURIResource() && newColumn.getURI() != null)
                ? newColumn.getURI()
                : datasetUri + "#skolem-csvw-column-fallback-" + newColumn.getId().getLabelString();

            dict.put(key, columnEntry);
          }
        }
      }
    }
  }

  /**
   * Creates a translation entry for a single property if it changed compared to the old resource.
   */
  private static JsonObject buildDictEntry(Resource resource, Resource oldResource,
      Property property, String defaultLanguage) {

    Map<String, String> propertyLanguages = propertyToLanguageMap(resource, property,
        defaultLanguage);
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

    List<String> targetLanguages =
        new ArrayList<>(translationLanguages != null ? translationLanguages : List.of());
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

  /**
   * Returns all MT@EC generated literals for the given property.
   */
  private static List<Literal> getAllTranslations(Resource resource, Property property) {
    return resource.listProperties(property)
        .filterKeep(stm -> stm.getObject().isLiteral())
        .filterKeep(stm -> stm.getLanguage().endsWith(T_0_MTEC))
        .mapWith(Statement::getLiteral)
        .toList();
   }

  /**
   * Removes previously generated machine translations so that new ones can be applied cleanly.
   */
  private static void removeAllTranslations(Resource resource, Property property) {
    List<Statement> statements = resource.listProperties(property)
        .filterKeep(stm -> stm.getObject().isLiteral())
        .filterKeep(stm -> stm.getLanguage().endsWith(T_0_MTEC))
        .toList();
    resource.getModel().remove(statements);
   }

  /**
   * Applies translated literals from the MT@EC response back into the RDF model.
   *
   * @param model target RDF model.
   * @param dict translation dictionary returned by the service.
   */
  public static void applyTranslations(Model model, JsonObject dict) {
    // Track which (resource, property) pairs we've already cleaned
    Set<String> cleaned = new HashSet<>();

    dict.forEach(entry -> {
      String entryKey = entry.getKey();
      Resource resource = resolveResource(model, entryKey);

      if (resource == null) {
        // Cannot apply translation without a resolvable resource
        System.err.println("Skipping unresolved resource for key: " + entryKey);
        return;
      }

      JsonObject properties = (JsonObject) entry.getValue();
      properties.forEach(propEntries -> {
        Property property = model.getProperty(propEntries.getKey());
        JsonObject propertyValues = (JsonObject) propEntries.getValue();

        String resId = resource.isURIResource()
            ? resource.getURI()
            : "_:" + resource.getId().getLabelString();

        String cleanedKey = resId + "||" + property.getURI();

        // Remove old machine translations once per (resource, property)
        if (!cleaned.contains(cleanedKey)) {
          removeAllTranslations(resource, property);
          cleaned.add(cleanedKey);
        }

        String sourceLanguage = propertyValues.getString("sourceLanguage");
        JsonObject translations = propertyValues.getJsonObject("translations");
        if (translations == null) {
          return;
        }

        translations.getMap().forEach((lang, value) -> {
          if (value instanceof String) {
            Literal literal = model.createLiteral((String) value,
                buildLanguageTag(sourceLanguage, lang));
            resource.addProperty(property, literal);
          }
        });
      });
    });
  }

  /**
   * Resolves a JSON dictionary key back to a resource inside the model, including synthetic keys
   * used for keywords and skolemized blank nodes.
   */
  private static Resource resolveResource(Model model, String key) {
    // Keywords: keys of the form "<datasetURI>#keyword#<n>"
    if (key.contains("#keyword#")) {
      String datasetUri = key.substring(0, key.indexOf("#keyword#"));
      Resource r = model.getResource(datasetUri);
      if (model.containsResource(r)) {
        return r;
      }
    }

    // Direct blank node key, e.g. "_:b0_b27"
    if (key.startsWith("_:")) {
      Resource b = model.createResource(AnonId.create(key.substring(2)));
      if (model.containsResource(b)) {
        return b;
      }
      // If it's skolemized, it should NOT come as "_:" anymore.
      return null;
    }

    // Direct URI lookup
    Resource resource = model.getResource(key);
    if (model.containsResource(resource)) {
      return resource;
    }

    // QName expansion (exclude "_:" blank nodes)
    if (!key.startsWith("http") && key.contains(":") && !key.startsWith("_:")) {
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

    // Skolemized blank nodes of the form <propURI>_:<bnodeId>  (keep if you still use it elsewhere)
    if (key.contains("_:")) {
      String propUri = key.substring(0, key.indexOf("_:"));
      Property linkProp = model.getProperty(propUri);

      StmtIterator sit = model.listStatements(null, linkProp, (RDFNode) null);
      if (sit.hasNext()) {
        RDFNode obj = sit.nextStatement().getObject();
        if (obj.isResource()) {
          return obj.asResource();
        }
      }
    }

    return null;
  }

  /**
   * Converts a blank node into a deterministic URI within the dataset namespace so it can be
   * referenced in translation payloads.
   */
  private static Resource ensureSkolem(Resource r, String datasetUri, String kind) {
    if (r == null) return null;
    if (r.isURIResource()) return r;
    if (!r.isAnon()) return r;

    Model model = r.getModel();
    String bnodeId = r.getId().getLabelString();

    String skolemUri = buildSkolemUri(datasetUri, kind, bnodeId);
    Resource skolem = model.createResource(skolemUri);

    // already skolemized
    if (model.containsResource(skolem)) {
      return skolem;
    }

    // Rewrite statements where bnode is SUBJECT: (_:x p o) -> (<skolem> p o)
    List<Statement> subjStmts = model.listStatements(r, null, (RDFNode) null).toList();
    for (Statement st : subjStmts) {
      model.add(skolem, st.getPredicate(), st.getObject());
    }

    // Rewrite statements where bnode is OBJECT: (s p _:x) -> (s p <skolem>)
    List<Statement> objStmts = model.listStatements(null, null, r).toList();
    for (Statement st : objStmts) {
      model.add(st.getSubject(), st.getPredicate(), skolem);
    }

    // Remove old statements
    model.remove(subjStmts);
    model.remove(objStmts);

    return skolem;
  }

  /**
   * Builds a dataset-scoped URI that uniquely identifies a formerly blank node.
   */
  private static String buildSkolemUri(String datasetUri, String kind, String bnodeId) {
    String hash = sha256Hex(datasetUri + "|" + kind + "|" + bnodeId);
    return datasetUri + "#skolem-" + kind + "-" + hash;
   }

  /**
   * Computes a hex encoded SHA-256 hash used for skolem URIs.
   */
  private static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Ensures that a CSVW column resource has a stable URI.
   *
   * <p>CSVW columns are often represented as blank nodes (e.g. {@code _:b0_b19}).
   * Blank node identifiers are not stable across serializations or model reloads,
   * which makes them unsafe to use as keys for translation requests or indexing.
   *
   * <p>This method replaces a blank-node column with a deterministic "skolem" URI
   * resource, derived from:
   * <ul>
   *   <li>the dataset URI,</li>
   *   <li>the parent table (to avoid collisions across tables), and</li>
   *   <li>the column identifier, preferably {@code csvw:name} (lexical value,
   *       language tag ignored), or as a fallback the blank node identifier.</li>
   * </ul>
   *
   * <p>The method rewrites the RDF model in-place:
   * <ul>
   *   <li>all triples where the blank node is the subject are reattached to the
   *       skolem URI resource,</li>
   *   <li>all triples where the blank node is the object are reattached to the
   *       skolem URI resource,</li>
   *   <li>the original blank-node triples are removed.</li>
   * </ul>
   *
   * <p>If the column already has a URI, it is returned unchanged.
   * If the column has already been skolemized in the model, the existing skolem
   * resource is reused.
   *
   * @param column     the CSVW column resource (URI resource or blank node)
   * @param datasetUri the URI of the dataset containing the column; used as a
   *                   stable namespace for skolem URI generation
   * @param table      the parent CSVW table resource, used to disambiguate columns
   *                   with the same name in different tables
   * @return a URI-based {@link Resource} representing the column, guaranteed to
   *         be stable and safe for use as a dictionary key and for later resolution
   */
  private static Resource ensureSkolemCsvwColumn(Resource column, String datasetUri, Resource table) {
    if (column == null) return null;
    if (column.isURIResource()) return column;
    if (!column.isAnon()) return column;

    Model model = column.getModel();

    // Prefer csvw:name lexical value as stable identifier
    String colName = null;
    Statement nameStmt = column.getProperty(CSVW_NAME);
    if (nameStmt != null && nameStmt.getObject().isLiteral()) {
      // getString() returns lexical form (ignores @lang)
      colName = nameStmt.getString();
    }

    // Include table context to avoid collisions across tables
    String tableId;
    if (table != null && table.isURIResource() && table.getURI() != null) {
      tableId = table.getURI();
    } else if (table != null && table.isAnon()) {
      tableId = "_:" + table.getId().getLabelString();
    } else {
      tableId = "table-unknown";
    }

    // Fallback to bnode label if csvw:name is missing
    String colId = (colName != null && !colName.isBlank())
        ? colName
        : "_:" + column.getId().getLabelString();

    String seed = datasetUri + "|csvw-column|" + tableId + "|" + colId;
    String skolemUri = datasetUri + "#skolem-csvw-column-" + sha256Hex(seed);

    Resource skolem = model.createResource(skolemUri);
    if (model.containsResource(skolem)) {
      return skolem;
    }

    // Rewrite statements where bnode is SUBJECT: (_:x p o) -> (<skolem> p o)
    List<Statement> subjStmts = model.listStatements(column, null, (RDFNode) null).toList();
    for (Statement st : subjStmts) {
      model.add(skolem, st.getPredicate(), st.getObject());
    }

    // Rewrite statements where bnode is OBJECT: (s p _:x) -> (s p <skolem>)
    List<Statement> objStmts = model.listStatements(null, null, column).toList();
    for (Statement st : objStmts) {
      model.add(st.getSubject(), st.getPredicate(), skolem);
    }

    model.remove(subjStmts);
    model.remove(objStmts);

    return skolem;
  }

}
