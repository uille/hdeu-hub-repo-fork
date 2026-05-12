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

class GEODCATAPTest {

  /** The canonical namespace URI defined by the production class. */
  private static final String EXPECTED_NS = "http://data.europa.eu/930/";

  @Test
  void namespaceUriIsCorrect() {
    assertEquals(EXPECTED_NS, GEODCATAP.NS);
    // getURI() must return exactly the same value as the NS constant
    assertEquals(GEODCATAP.NS, GEODCATAP.getURI());
  }

  @Test
  void namespaceResourceMatchesNs() {
    assertNotNull(GEODCATAP.NAMESPACE);
    assertEquals(GEODCATAP.NS, GEODCATAP.NAMESPACE.getURI());
  }

  @Test
  void custodianPropertyIsCorrect() {
    assertNotNull(GEODCATAP.custodian);
    assertEquals(GEODCATAP.NS + "custodian", GEODCATAP.custodian.getURI());
    assertEquals(
        ResourceFactory.createProperty(GEODCATAP.NS + "custodian"),
        GEODCATAP.custodian
    );
  }

  @Test
  void constructorInstantiatesWithoutErrors() {
    GEODCATAP geo = new GEODCATAP();
    assertNotNull(geo);
  }
}

