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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class CV {

  private static final Model m = ModelFactory.createDefaultModel();

  public static final String NS = "http://data.europa.eu/m8g/";
  public static final Resource NAMESPACE = m.createResource(NS);

  // Classes
  public static final Resource ContactPoint = m.createResource(NS + "ContactPoint");

  // Properties
  public static final Property contactPoint = m.createProperty(NS + "contactPoint");
  public static final Property email = m.createProperty(NS + "email");
  public static final Property contactPage = m.createProperty(NS + "contactPage");
  public static final Property telephone = m.createProperty(NS + "telephone");
  public static final Property openingHours = m.createProperty(NS + "openingHours");
  public static final Property specialOpeningHoursSpecification =
      m.createProperty(NS + "specialOpeningHoursSpecification");
  public static final Property frequency = m.createProperty(NS + "frequency");

  public CV() {
  }

  public static String getURI() {
    return NS;
  }
}
