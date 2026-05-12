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

class CVTest {

  /** The canonical namespace URI defined by the production class. */
  private static final String EXPECTED_NS = "http://data.europa.eu/m8g/";

  @Test
  void namespaceUriIsCorrect() {
    assertEquals(EXPECTED_NS, CV.NS);
    // getURI() must return exactly the same value as the NS constant
    assertEquals(CV.NS, CV.getURI());
  }

  @Test
  void namespaceResourceMatchesNs() {
    assertNotNull(CV.NAMESPACE);
    assertEquals(CV.NS, CV.NAMESPACE.getURI());
  }

  @Test
  void contactPointClassIsCorrect() {
    assertNotNull(CV.ContactPoint);
    assertEquals(CV.NS + "ContactPoint", CV.ContactPoint.getURI());
  }

  @Test
  void contactPointPropertyIsCorrect() {
    assertNotNull(CV.contactPoint);
    assertEquals(CV.NS + "contactPoint", CV.contactPoint.getURI());
    assertEquals(
        ResourceFactory.createProperty(CV.NS + "contactPoint"),
        CV.contactPoint
    );
  }

  @Test
  void emailPropertyIsCorrect() {
    assertNotNull(CV.email);
    assertEquals(CV.NS + "email", CV.email.getURI());
  }

  @Test
  void contactPagePropertyIsCorrect() {
    assertNotNull(CV.contactPage);
    assertEquals(CV.NS + "contactPage", CV.contactPage.getURI());
  }

  @Test
  void telephonePropertyIsCorrect() {
    assertNotNull(CV.telephone);
    assertEquals(CV.NS + "telephone", CV.telephone.getURI());
  }

  @Test
  void openingHoursPropertyIsCorrect() {
    assertNotNull(CV.openingHours);
    assertEquals(CV.NS + "openingHours", CV.openingHours.getURI());
  }

  @Test
  void specialOpeningHoursSpecificationPropertyIsCorrect() {
    assertNotNull(CV.specialOpeningHoursSpecification);
    assertEquals(CV.NS + "specialOpeningHoursSpecification", CV.specialOpeningHoursSpecification.getURI());
  }

  @Test
  void frequencyPropertyIsCorrect() {
    assertNotNull(CV.frequency);
    assertEquals(CV.NS + "frequency", CV.frequency.getURI());
  }

  @Test
  void constructorInstantiatesWithoutErrors() {
    CV cv = new CV();
    assertNotNull(cv);
  }
}
