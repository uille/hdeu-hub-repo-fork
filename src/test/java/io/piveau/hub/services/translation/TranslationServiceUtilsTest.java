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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.hub.util.rdf.DPV;
import io.piveau.rdf.RDFMimeTypes;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisplayName("TranslationServiceUtilsTest - Testing translation utilities")
@ExtendWith(VertxExtension.class)
class TranslationServiceUtilsTest {

  private static final Logger log = LoggerFactory.getLogger(TranslationServiceUtilsTest.class);

  private DatasetHelper datasetHelper;
  private DatasetHelper oldDatasetHelper;

  private static final String CSVW_NS = "http://www.w3.org/ns/csvw#";
  private static final Property CSVW_TITLES = ResourceFactory.createProperty(CSVW_NS, "titles");

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext) {
    TranslationServiceUtils.translationLanguages = List.of("en", "de", "fr", "nl", "es", "pt");
    TranslationServiceUtils.callbackParameters = new JsonObject()
        .put("url", "anyUrl")
        .put("method", HttpMethod.POST.name())
        .put("headers", new JsonObject().put("Authorization", "anyKey"));

    vertx
        .fileSystem()
        .readFile("translation/translation_example_dataset.ttl")
        .compose(buffer -> {
          datasetHelper = DatasetHelper.create(buffer.toString(), RDFMimeTypes.TURTLE);
          datasetHelper.getCatalogueInfo().put("langCode", "en");
          datasetHelper.catalogueId("test-catalogue");
          return vertx.fileSystem().readFile("translation/translation_example_dataset_old.ttl");
        })
        .onSuccess(buffer -> {
          oldDatasetHelper = DatasetHelper.create(buffer.toString(), RDFMimeTypes.TURTLE);
          oldDatasetHelper.getCatalogueInfo().put("langCode", "en");
          oldDatasetHelper.catalogueId("test-catalogue");
          testContext.completeNow();
        })
        .onFailure(testContext::failNow);
  }

  @Test
  @DisplayName("Extract property languages map")
  void propertyToLanguageMap(VertxTestContext testContext) {
    Resource distribution = datasetHelper
        .model()
        .getResource("https://piveau.io/set/distribution/3");
    Map<String, String> languages = TranslationServiceUtils.propertyToLanguageMap(
        distribution,
        DCTerms.title,
        "de"
    );
    testContext.verify(() -> {
      assertEquals(1, languages.size());
      assertEquals("Beispiel-Distribution 3", languages.get("de"));
      testContext.completeNow();
    });
  }

  @Test
  @DisplayName("Is translation completed")
  void translationCompleted(VertxTestContext testContext) {
    testContext.verify(() -> {
      assertFalse(TranslationServiceUtils.isTranslationCompleted(null));
      assertFalse(TranslationServiceUtils.isTranslationCompleted(datasetHelper));
      assertTrue(TranslationServiceUtils.isTranslationCompleted(oldDatasetHelper));
      testContext.completeNow();
    });
  }

  @Test
  @DisplayName("Build translation request without old translations (non-empty dict)")
  void buildTranslationRequest(VertxTestContext testContext) {
    JsonObject request = TranslationServiceUtils.buildTranslationRequest(datasetHelper, null);
    if (log.isDebugEnabled()) {
      log.debug(request.encodePrettily());
    }

    testContext.verify(() -> {
      assertNotNull(request.getJsonObject("dict"));
      assertTrue(request.getJsonObject("dict").size() >= 4);
      testContext.completeNow();
    });
  }

  @Test
  @DisplayName("Build translation request with just one change")
  void buildTranslationRequest2(VertxTestContext testContext) {
    JsonObject request = TranslationServiceUtils.buildTranslationRequest(
        datasetHelper,
        oldDatasetHelper
    );
    if (log.isDebugEnabled()) {
      log.debug(request.encodePrettily());
    }

    testContext.verify(() -> {
      assertNotNull(request.getJsonObject("dict"));
      assertFalse(request.getJsonObject("dict").isEmpty());
      assertFalse(request.getJsonObject("dict").containsKey(datasetHelper.uriRef()));
      assertTrue(
          datasetHelper.resource()
              .hasProperty(DCTerms.title, "Beispieldatensatz", "de-t-en-t0-mtec")
      );
      assertTrue(
          datasetHelper
              .resource()
              .hasProperty(DCTerms.description, "This is an example dataset", "en-t-de-t0-mtec")
      );
      testContext.completeNow();
    });
  }

  @Test
  @DisplayName("Apply translations to model")
  void applyTranslations_comprehensive(VertxTestContext testContext) {
    testContext.verify(() -> {
      Model model = datasetHelper.model();
      Resource dataset = datasetHelper.resource();
      String datasetUri = dataset.getURI();

      // ---- Setup for QName expansion branch ----
      // Add a resolvable QName resource and prefix mapping
      model.setNsPrefix("ex", "https://example.org/ns/");
      Resource qNameRes = model.createResource("https://example.org/ns/thing");
      qNameRes.addProperty(RDFS.label, model.createLiteral("QName label", "en"));

      // ---- Find the blank nodes created by the fixture (purpose/legalBasis/provenance) ----
      Resource purposeBNode = dataset.getPropertyResourceValue(
          model.getProperty(DPV.hasPurpose.toString())
      );
      assertNotNull(purposeBNode, "Expected dpv:hasPurpose object resource");
      assertTrue(purposeBNode.isAnon(), "Expected dpv:hasPurpose to be a blank node in fixture");

      Resource legalBasisBNode = dataset.getPropertyResourceValue(
          model.getProperty(DPV.hasLegalBasis.toString())
      );
      assertNotNull(legalBasisBNode, "Expected dpv:hasLegalBasis object resource");
      assertTrue(
          legalBasisBNode.isAnon(),
          "Expected dpv:hasLegalBasis to be a blank node in fixture"
      );

      Resource provenanceBNode = dataset.getPropertyResourceValue(DCTerms.provenance);
      assertNotNull(provenanceBNode, "Expected dct:provenance object resource");
      assertTrue(provenanceBNode.isAnon(), "Expected dct:provenance to be a blank node in fixture");

      String purposeBNodeId = purposeBNode.getId().getLabelString();
      String provenanceBNodeId = provenanceBNode.getId().getLabelString();

      // ---- Seed some old MT@EC translations to validate they get removed ----
      dataset.addProperty(DCTerms.title, model.createLiteral("ALT_DE", "de-t-en-t0-mtec"));
      dataset.addProperty(DCTerms.title, model.createLiteral("ALT_FR", "fr-t-en-t0-mtec"));

      // Also add old translations on bnodes so we verify removal there too
      purposeBNode.addProperty(
          DCTerms.description,
          model.createLiteral("ALT_PURPOSE_DE", "de-t-en-t0-mtec")
      );
      provenanceBNode.addProperty(
          RDFS.label,
          model.createLiteral("ALT_PROV_FR", "fr-t-en-t0-mtec")
      );

      // ---- Build ONE comprehensive dict that hits all resolveResource branches ----
      JsonObject dict = new JsonObject()
          // Direct dataset URI resolution + applies NEW_DE
          .put(
              datasetUri,
              new JsonObject().put(
                  DCTerms.title.getURI(),
                  new JsonObject()
                      .put("sourceLanguage", "en")
                      .put("translations", new JsonObject().put("de", "NEW_DE"))
              )
          )
          // Keyword synthetic key resolution -> resolves back to dataset -> applies NEW_FR
          // This must NOT remove NEW_DE (clean-once behavior)
          .put(
              datasetUri + "#keyword#0",
              new JsonObject().put(
                  DCTerms.title.getURI(),
                  new JsonObject()
                      .put("sourceLanguage", "en")
                      .put("translations", new JsonObject().put("fr", "NEW_FR"))
              )
          )
          // Direct blank node key resolution ("_:<id>") -> purpose description
          .put(
              "_:" + purposeBNodeId,
              new JsonObject().put(
                  DCTerms.description.getURI(),
                  new JsonObject()
                      .put("sourceLanguage", "en")
                      .put("translations", new JsonObject().put("de", "PURPOSE_DE"))
              )
          )
          //  QName expansion resolution ("ex:thing") -> applies RDFS.label translation
          .put(
              "ex:thing",
              new JsonObject().put(
                  RDFS.label.getURI(),
                  new JsonObject()
                      .put("sourceLanguage", "en")
                      .put("translations", new JsonObject().put("de", "QNAME_DE"))
              )
          )
          // Legacy "<propUri>_:<bnodeId>" branch:
          // We'll use dct:provenance as the linking property, and append "_:<id>"
          // resolveResource() will lookup any statement (?s dct:provenance _:bN) and return the object.
          .put(
              DCTerms.provenance.toString() + "_:" + provenanceBNodeId,
              new JsonObject().put(
                  RDFS.label.getURI(),
                  new JsonObject()
                      .put("sourceLanguage", "en")
                      .put("translations", new JsonObject().put("de", "PROV_DE"))
              )
          );

      // Apply everything
      TranslationServiceUtils.applyTranslations(model, dict);

      // ---- Assertions: dataset title got both NEW_DE and NEW_FR, and old ones removed ----
      assertTrue(
          dataset.hasProperty(DCTerms.title, model.createLiteral("NEW_DE", "de-t-en-t0-mtec"))
      );
      assertTrue(
          dataset.hasProperty(DCTerms.title, model.createLiteral("NEW_FR", "fr-t-en-t0-mtec"))
      );

      assertFalse(
          dataset.hasProperty(DCTerms.title, model.createLiteral("ALT_DE", "de-t-en-t0-mtec"))
      );
      assertFalse(
          dataset.hasProperty(DCTerms.title, model.createLiteral("ALT_FR", "fr-t-en-t0-mtec"))
      );

      // ---- Assertions: purpose bnode got translation and old removed ----
      assertTrue(
          purposeBNode.hasProperty(
              DCTerms.description,
              model.createLiteral("PURPOSE_DE", "de-t-en-t0-mtec")
          )
      );
      assertFalse(
          purposeBNode.hasProperty(
              DCTerms.description,
              model.createLiteral("ALT_PURPOSE_DE", "de-t-en-t0-mtec")
          )
      );

      // ---- Assertions: QName resource translated ----
      assertTrue(
          qNameRes.hasProperty(RDFS.label, model.createLiteral("QNAME_DE", "de-t-en-t0-mtec"))
      );

      // ---- Assertions: provenance bnode translated via propUri_:bnodeId key, old removed ----
      assertTrue(
          provenanceBNode.hasProperty(RDFS.label, model.createLiteral("PROV_DE", "de-t-en-t0-mtec"))
      );
      assertFalse(
          provenanceBNode.hasProperty(
              RDFS.label,
              model.createLiteral("ALT_PROV_FR", "fr-t-en-t0-mtec")
          )
      );

      testContext.completeNow();
    });
  }

  @Test
  @DisplayName(
      "Build translation request includes keyword translation units with synthetic keys (fixture-based)"
  )
  void buildTranslationRequest_includesKeywordsSyntheticKeys(VertxTestContext testContext) {
    testContext.verify(() -> {
      Resource dataset = datasetHelper.resource();
      String datasetUri = dataset.getURI();

      JsonObject req = TranslationServiceUtils.buildTranslationRequest(datasetHelper, null);
      JsonObject dict = req.getJsonObject("dict");
      assertNotNull(dict);
      assertFalse(dict.isEmpty());

      boolean hasKeywordKey = dict
          .getMap()
          .keySet()
          .stream()
          .anyMatch(k -> k.startsWith(datasetUri + "#keyword#"));
      assertTrue(hasKeywordKey, "Expected dict to contain #keyword# synthetic keys");

      // Pick one keyword entry and validate structure
      String someKeywordKey = dict
          .getMap()
          .keySet()
          .stream()
          .filter(k -> k.startsWith(datasetUri + "#keyword#"))
          .findFirst()
          .orElseThrow();

      JsonObject subject = dict.getJsonObject(someKeywordKey);
      assertNotNull(subject);
      assertTrue(subject.containsKey(DCAT.keyword.getURI()));

      JsonObject keywordProperty = subject.getJsonObject(DCAT.keyword.getURI());
      assertNotNull(keywordProperty);
      assertNotNull(keywordProperty.getString("value"));
      assertNotNull(keywordProperty.getString("sourceLanguage"));
      assertNotNull(keywordProperty.getJsonArray("targetLanguages"));
      assertFalse(keywordProperty.getJsonArray("targetLanguages").isEmpty());

      testContext.completeNow();
    });
  }

  @Test
  @DisplayName(
      "Build translation request skolemizes blank-node distributions to URI keys (fixture-based)"
  )
  void buildTranslationRequest_skolemizesBlankNodeDistributions(VertxTestContext testContext) {
    testContext.verify(() -> {
      Model model = datasetHelper.model();
      Resource dataset = datasetHelper.resource();
      String datasetUri = dataset.getURI();

      JsonObject req = TranslationServiceUtils.buildTranslationRequest(datasetHelper, null);
      JsonObject dict = req.getJsonObject("dict");
      assertNotNull(dict);

      boolean hasSkolemKey = dict
          .getMap()
          .keySet()
          .stream()
          .anyMatch(k -> k.startsWith(datasetUri + "#skolem-distribution-"));
      assertTrue(
          hasSkolemKey,
          "Expected skolemized distribution key like <dataset>#skolem-distribution-<hash>"
      );

      // Also assert the model contains that skolemized URI resource after rewrite
      boolean modelHasSkolemSubject = model
          .listSubjects()
          .toList()
          .stream()
          .filter(Resource::isURIResource)
          .anyMatch(r -> r.getURI().startsWith(datasetUri + "#skolem-distribution-"));
      assertTrue(
          modelHasSkolemSubject,
          "Expected model to contain skolemized distribution resource"
      );

      testContext.completeNow();
    });
  }

  @Test
  @DisplayName(
      "Build translation request extracts CSVW column titles/description for healthdcatap:hasVariables (fixture-based)"
  )
  void buildTranslationRequest_includesCsvwColumnsForHasVariables(VertxTestContext testContext) {
    testContext.verify(() -> {
      Resource dataset = datasetHelper.resource();
      String datasetUri = dataset.getURI();

      JsonObject req = TranslationServiceUtils.buildTranslationRequest(datasetHelper, null);
      JsonObject dict = req.getJsonObject("dict");
      assertNotNull(dict);

      // Expect at least one dict key for a skolemized CSVW column (from healthdcatap:hasVariables)
      boolean hasCsvwColumnKey = dict
          .getMap()
          .keySet()
          .stream()
          .anyMatch(k -> k.startsWith(datasetUri + "#skolem-csvw-column-"));
      assertTrue(hasCsvwColumnKey, "Expected skolemized csvw column key in dict");

      String colKey = dict
          .getMap()
          .keySet()
          .stream()
          .filter(k -> k.startsWith(datasetUri + "#skolem-csvw-column-"))
          .findFirst()
          .orElseThrow();

      JsonObject colEntry = dict.getJsonObject(colKey);
      assertNotNull(colEntry);

      boolean hasTitlesOrDesc =
          colEntry.containsKey(CSVW_TITLES.getURI()) ||
              colEntry.containsKey(DCTerms.description.getURI());
      assertTrue(
          hasTitlesOrDesc,
          "Expected CSVW titles and/or DCTerms.description in column entry"
      );

      testContext.completeNow();
    });
  }

  @Test
  @DisplayName("buildLanguageTag produces correct BCP-47 MT@EC tag")
  void buildLanguageTag_formatsCorrectly(VertxTestContext testContext) {
    testContext.verify(() -> {
      // fr translated from en => "fr-t-en-t0-mtec"
      assertEquals("fr-t-en-t0-mtec", TranslationServiceUtils.buildLanguageTag("en", "fr"));
      // de translated from el => "de-t-el-t0-mtec"
      assertEquals("de-t-el-t0-mtec", TranslationServiceUtils.buildLanguageTag("el", "de"));
      testContext.completeNow();
    });
  }

  @Test
  @DisplayName("applyTranslations silently skips entries whose key cannot be resolved")
  void applyTranslations_unresolvableKey_isSkipped(VertxTestContext testContext) {
    testContext.verify(() -> {
      Model model = datasetHelper.model();
      // A key that does not match any resource in the model and is not a known pattern
      JsonObject dict = new JsonObject()
          .put(
              "https://does-not-exist.example/no-such-resource",
              new JsonObject().put(
                  DCTerms.title.getURI(),
                  new JsonObject()
                      .put("sourceLanguage", "en")
                      .put("translations", new JsonObject().put("de", "SHOULD_NOT_APPEAR"))
              )
          );

      // Must not throw
      TranslationServiceUtils.applyTranslations(model, dict);

      // No German machine-translated title should have been added to any resource
      boolean anyMatch = model.listStatements()
          .toList()
          .stream()
          .anyMatch(st -> st.getObject().isLiteral()
              && "SHOULD_NOT_APPEAR".equals(st.getLiteral().getString()));
      assertFalse(anyMatch, "Unresolvable key should result in no translation being applied");

      testContext.completeNow();
    });
  }

  @Test
  @DisplayName("propertyToLanguageMap ignores machine-translated literals ending in -t0-mtec")
  void propertyToLanguageMap_excludesMachineTranslatedLiterals(VertxTestContext testContext) {
    testContext.verify(() -> {
      Model model = datasetHelper.model();
      Resource dataset = datasetHelper.resource();

      // Add a machine-translated literal
      dataset.addProperty(DCTerms.title, model.createLiteral("Maschinell übersetzt", "de-t-en-t0-mtec"));
      // Add a human-authored literal
      dataset.addProperty(DCTerms.title, model.createLiteral("Human Title", "en"));

      Map<String, String> langs = TranslationServiceUtils.propertyToLanguageMap(
          dataset, DCTerms.title, "en");

      // The machine-translated literal must be excluded
      assertFalse(
          langs.keySet().stream().anyMatch(k -> k.endsWith("-t0-mtec")),
          "MT@EC tagged literals should be excluded from the language map"
      );
      assertTrue(langs.containsKey("en"), "Human-authored 'en' literal should be present");

      testContext.completeNow();
    });
  }
}
