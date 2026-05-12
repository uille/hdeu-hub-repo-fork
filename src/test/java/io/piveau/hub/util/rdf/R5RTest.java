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

class R5RTest {

  @Test
  void namespaceUriIsCorrect() {
    assertEquals("http://data.europa.eu/r5r/", R5R.NS);
    assertEquals("http://data.europa.eu/r5r/", R5R.getURI());
  }

  @Test
  void namespaceResourceIsNotNullAndMatchesNs() {
    assertNotNull(R5R.NAMESPACE);
    assertEquals(R5R.NS, R5R.NAMESPACE.getURI());
  }

  @Test
  void applicableLegislationPropertyIsCorrect() {
    assertNotNull(R5R.applicableLegislation);
    assertEquals(R5R.NS + "applicableLegislation", R5R.applicableLegislation.getURI());

    // Ensure it matches a newly created property with same URI
    assertEquals(
        ResourceFactory.createProperty(R5R.NS + "applicableLegislation"),
        R5R.applicableLegislation
    );
  }

  @Test
  void constructorDoesNothingSpecial() {
    // Just ensure we can instantiate without errors (even though class is static-style)
    R5R vocab = new R5R();
    assertNotNull(vocab);
  }
}
