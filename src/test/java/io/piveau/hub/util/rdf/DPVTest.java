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

class DPVTest {

  /** The canonical namespace URI defined by the production class. */
  private static final String EXPECTED_NS = "https://w3id.org/dpv#";

  @Test
  void namespaceUriIsCorrect() {
    assertEquals(EXPECTED_NS, DPV.NS);
    // getURI() must return exactly the same value as the NS constant
    assertEquals(DPV.NS, DPV.getURI());
  }

  @Test
  void namespaceResourceMatchesNs() {
    assertNotNull(DPV.NAMESPACE);
    assertEquals(DPV.NS, DPV.NAMESPACE.getURI());
  }

  @Test
  void hasPersonalDataPropertyIsCorrect() {
    assertNotNull(DPV.hasPersonalData);
    assertEquals(DPV.NS + "hasPersonalData", DPV.hasPersonalData.getURI());
    assertEquals(
        ResourceFactory.createProperty(DPV.NS + "hasPersonalData"),
        DPV.hasPersonalData
    );
  }

  @Test
  void hasPurposePropertyIsCorrect() {
    assertNotNull(DPV.hasPurpose);
    assertEquals(DPV.NS + "hasPurpose", DPV.hasPurpose.getURI());
  }

  @Test
  void hasLegalBasisPropertyIsCorrect() {
    assertNotNull(DPV.hasLegalBasis);
    assertEquals(DPV.NS + "hasLegalBasis", DPV.hasLegalBasis.getURI());
  }

  @Test
  void constructorInstantiatesWithoutErrors() {
    DPV dpv = new DPV();
    assertNotNull(dpv);
  }
}
