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

public class DPV {
    private static final Model m = ModelFactory.createDefaultModel();
    public static final String NS = "https://w3id.org/dpv#";
    public static final Resource NAMESPACE = m.createResource(NS);

    public static final Property hasPersonalData = m.createProperty(NS + "hasPersonalData");
    public static final Property hasPurpose = m.createProperty(NS + "hasPurpose");
    public static final Property hasLegalBasis = m.createProperty(NS + "hasLegalBasis");

    public DPV() {
    }
    public static String getURI() {
        return NS;
    }

}
