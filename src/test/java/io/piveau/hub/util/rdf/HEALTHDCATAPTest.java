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
package io.piveau.hub.util.rdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;

class HEALTHDCATAPTest {

  /** The canonical namespace URI defined by the production class. */
  private static final String EXPECTED_NS = "http://healthdataportal.eu/ns/health#";

  @Test
  void namespaceUriIsCorrect() {
    assertEquals(EXPECTED_NS, HEALTHDCATAP.NS);
    // getURI() must return exactly the same value as the NS constant
    assertEquals(HEALTHDCATAP.NS, HEALTHDCATAP.getURI());
  }

  @Test
  void namespaceResourceMatchesNs() {
    assertNotNull(HEALTHDCATAP.NAMESPACE);
    assertEquals(HEALTHDCATAP.NS, HEALTHDCATAP.NAMESPACE.getURI());
  }

  @Test
  void maxTypicalAgePropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.maxTypicalAge);
    assertEquals(HEALTHDCATAP.NS + "maxTypicalAge", HEALTHDCATAP.maxTypicalAge.getURI());
    assertEquals(
        ResourceFactory.createProperty(HEALTHDCATAP.NS + "maxTypicalAge"),
        HEALTHDCATAP.maxTypicalAge
    );
  }

  @Test
  void minTypicalAgePropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.minTypicalAge);
    assertEquals(HEALTHDCATAP.NS + "minTypicalAge", HEALTHDCATAP.minTypicalAge.getURI());
  }

  @Test
  void numberOfUniqueIndividualsPropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.numberOfUniqueIndividuals);
    assertEquals(HEALTHDCATAP.NS + "numberOfUniqueIndividuals", HEALTHDCATAP.numberOfUniqueIndividuals.getURI());
  }

  @Test
  void numberOfRecordsPropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.numberOfRecords);
    assertEquals(HEALTHDCATAP.NS + "numberOfRecords", HEALTHDCATAP.numberOfRecords.getURI());
  }

  @Test
  void publisherNotePropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.publisherNote);
    assertEquals(HEALTHDCATAP.NS + "publisherNote", HEALTHDCATAP.publisherNote.getURI());
  }

  @Test
  void retentionPeriodPropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.retentionPeriod);
    assertEquals(HEALTHDCATAP.NS + "retentionPeriod", HEALTHDCATAP.retentionPeriod.getURI());
  }

  @Test
  void analyticsPropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.analytics);
    assertEquals(HEALTHDCATAP.NS + "analytics", HEALTHDCATAP.analytics.getURI());
  }

  @Test
  void hdabPropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.hdab);
    assertEquals(HEALTHDCATAP.NS + "hdab", HEALTHDCATAP.hdab.getURI());
  }

  @Test
  void healthCategoryPropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.healthCategory);
    assertEquals(HEALTHDCATAP.NS + "healthCategory", HEALTHDCATAP.healthCategory.getURI());
  }

  @Test
  void publisherTypePropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.publisherType);
    assertEquals(HEALTHDCATAP.NS + "publisherType", HEALTHDCATAP.publisherType.getURI());
  }

  @Test
  void populationCoveragePropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.populationCoverage);
    assertEquals(HEALTHDCATAP.NS + "populationCoverage", HEALTHDCATAP.populationCoverage.getURI());
  }

  @Test
  void hasCodingSystemPropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.hasCodingSystem);
    assertEquals(HEALTHDCATAP.NS + "hasCodingSystem", HEALTHDCATAP.hasCodingSystem.getURI());
  }

  @Test
  void hasCodeValuesPropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.hasCodeValues);
    assertEquals(HEALTHDCATAP.NS + "hasCodeValues", HEALTHDCATAP.hasCodeValues.getURI());
  }

  @Test
  void healthThemePropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.healthTheme);
    assertEquals(HEALTHDCATAP.NS + "healthTheme", HEALTHDCATAP.healthTheme.getURI());
  }

  @Test
  void hasVariablesPropertyIsCorrect() {
    assertNotNull(HEALTHDCATAP.hasVariables);
    assertEquals(HEALTHDCATAP.NS + "hasVariables", HEALTHDCATAP.hasVariables.getURI());
  }

  @Test
  void constructorInstantiatesWithoutErrors() {
    HEALTHDCATAP h = new HEALTHDCATAP();
    assertNotNull(h);
  }
}

