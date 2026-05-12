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

public class HEALTHDCATAP {
    private static final Model m = ModelFactory.createDefaultModel();
    public static final String NS = "http://healthdataportal.eu/ns/health#";
    public static final Resource NAMESPACE = m.createResource(NS);

    public static final Property maxTypicalAge = m.createProperty(NS + "maxTypicalAge");
    public static final Property minTypicalAge = m.createProperty(NS + "minTypicalAge");
    public static final Property numberOfUniqueIndividuals = m.createProperty(NS + "numberOfUniqueIndividuals");
    public static final Property numberOfRecords = m.createProperty(NS + "numberOfRecords");
    public static final Property publisherNote = m.createProperty(NS + "publisherNote");
    public static final Property retentionPeriod = m.createProperty(NS + "retentionPeriod");
    public static final Property analytics = m.createProperty(NS + "analytics");
    public static final Property hdab = m.createProperty(NS + "hdab");
    public static final Property healthCategory = m.createProperty(NS + "healthCategory");
    public static final Property publisherType = m.createProperty(NS + "publisherType");
    public static final Property populationCoverage = m.createProperty(NS + "populationCoverage");
    public static final Property hasCodingSystem = m.createProperty(NS + "hasCodingSystem");
    public static final Property hasCodeValues = m.createProperty(NS + "hasCodeValues");
    public static final Property healthTheme = m.createProperty(NS + "healthTheme");
    public static final Property hasVariables = m.createProperty(NS + "hasVariables");

    public HEALTHDCATAP() {
    }
    public static String getURI() {
        return NS;
    }

}
