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
package io.piveau.hub.util;

import static org.apache.jena.vocabulary.DCAT.dataset;
import static org.apache.jena.vocabulary.DCAT.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.piveau.dcatap.DCATAPUriRef;
import io.piveau.dcatap.DCATAPUriSchema;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

class CatalogueHelperTest {

  private static final String TTL_SINGLE_OK = """
      @prefix dcat: <http://www.w3.org/ns/dcat#> .
      @prefix dct:  <http://purl.org/dc/terms/> .
      @prefix ex:   <http://example.org/> .

      ex:cat a dcat:Catalog ;
          dct:language <http://publications.europa.eu/resource/authority/language/ENG> ;
          dct:spatial  <http://publications.europa.eu/resource/authority/country/DEU> .
      """;

  private static final String TTL_NO_LANG = """
      @prefix dcat: <http://www.w3.org/ns/dcat#> .
      @prefix dct:  <http://purl.org/dc/terms/> .
      @prefix ex:   <http://example.org/> .

      ex:cat a dcat:Catalog ;
          dct:spatial  <http://publications.europa.eu/resource/authority/country/DEU> .
      """;

  private static final String TTL_DUP_LANG = """
      @prefix dcat: <http://www.w3.org/ns/dcat#> .
      @prefix dct:  <http://purl.org/dc/terms/> .
      @prefix ex:   <http://example.org/> .

      ex:cat a dcat:Catalog ;
          dct:language <http://publications.europa.eu/resource/authority/language/ENG> ,
                       <http://publications.europa.eu/resource/authority/language/DEU> ;
          dct:spatial  <http://publications.europa.eu/resource/authority/country/DEU> .
      """;

  @Test
  void ctor_parsesAndRenames_setsTimestamps_andKeepsSingleLangSpatial() {
    String catId = "cat-1";

    CatalogueHelper helper = new CatalogueHelper(catId, "text/turtle", TTL_SINGLE_OK);

    // The helper renames any dcat:Catalog subject to DCATAPUriSchema.createForCatalogue(id).getUri()
    String expectedUri = DCATAPUriSchema.createForCatalogue(catId).getUri();

    Resource cat = helper.resource();
    assertEquals(expectedUri, cat.getURI(), "catalog URI should be renamed to schema-based uri");

    // One language + one spatial
    assertEquals(1, cat.listProperties(DCTerms.language).toList().size(), "exactly one dct:language");
    assertEquals(1, cat.listProperties(DCTerms.spatial).toList().size(), "exactly one dct:spatial");

    // Timestamps created by the ctor
    assertEquals(1, cat.listProperties(DCTerms.issued).toList().size(), "issued set once");
    assertEquals(1, cat.listProperties(DCTerms.modified).toList().size(), "modified set once");

    // Sanity: model still has a Catalog with renamed subject
    Model m = helper.getModel();
    assertTrue(m.contains(cat, RDF.type, DCAT.Catalog), "model has a dcat:Catalog resource");
  }

  @Test
  void addDataset_addsDatasetAndRecordStatements() {
    String catId = "cat-2";
    CatalogueHelper helper = new CatalogueHelper(catId, "text/turtle", TTL_SINGLE_OK);
    Resource cat = helper.resource();

    // Build a parseable dataset URI using the schema helpers
    DCATAPUriRef dsRef = DCATAPUriSchema.createFor("ds-1", DCATAPUriSchema.getDatasetContext());
    String datasetUri = dsRef.getDatasetUriRef(); // this is acceptable input to addDataset(..)

    int beforeDataset = cat.listProperties(dataset).toList().size();
    int beforeRecord  = cat.listProperties(record).toList().size();

    helper.addDataset(datasetUri);

    int afterDataset = cat.listProperties(dataset).toList().size();
    int afterRecord  = cat.listProperties(record).toList().size();

    assertEquals(beforeDataset + 1, afterDataset, "dcat:dataset triple added");
    assertEquals(beforeRecord + 1,  afterRecord,  "dcat:record triple added");

    // The objects should be the schema-computed URIs
    assertTrue(
        cat.listProperties(dataset).toList().stream()
            .anyMatch(st -> st.getObject().isResource()
                && st.getResource().getURI().equals(dsRef.getDatasetUriRef())),
        "dataset object should be the dataset URI");

    assertTrue(
        cat.listProperties(record).toList().stream()
            .anyMatch(st -> st.getObject().isResource()
                && st.getResource().getURI().equals(dsRef.getRecordUriRef())),
        "record object should be the record URI");
  }

  @Test
  void ctor_throws_whenLanguageMissing() {
    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> new CatalogueHelper("cat-bad", "text/turtle", TTL_NO_LANG)
    );
    assertTrue(ex.getMessage().contains("exactly one dct:language"),
        "error mentions single language/spatial constraint");
  }

  @Test
  void ctor_throws_whenLanguageDuplicated() {
    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> new CatalogueHelper("cat-bad2", "text/turtle", TTL_DUP_LANG)
    );
    assertTrue(ex.getMessage().contains("exactly one dct:language"),
        "error mentions single language/spatial constraint");
  }
}
