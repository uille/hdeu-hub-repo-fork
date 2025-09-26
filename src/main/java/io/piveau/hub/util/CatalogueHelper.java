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

import io.piveau.dcatap.DCATAPUriRef;
import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.rdf.Piveau;
import io.piveau.utils.JenaUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.ResourceUtils;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class CatalogueHelper {
    private final String id;
    private final Resource catalogue;

    private final Model model;

    public CatalogueHelper(String id, String contentType, String content) {
        this.id = id;
        model = Piveau.toModel(content.getBytes(), contentType);

        renameReferences();
        catalogue = model.getResource(uriRef());

        // Enforce exactly one dct:language and one dct:spatial
        validateSingleLanguageAndSpatial(catalogue);

        modified();
        catalogue.addProperty(DCTerms.issued, ZonedDateTime
                .now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_DATE_TIME), XSDDatatype.XSDdateTime);
    }

    public Model getModel() {
        return model;
    }

    public String getId() {
        return id;
    }

    public String uriRef() {
        return DCATAPUriSchema.createForCatalogue(id).getUri();
    }

    public Resource resource() {
        return model.getResource(uriRef());
    }

    public void modified() {
        catalogue.removeAll(DCTerms.modified);
        catalogue.addProperty(DCTerms.modified, ZonedDateTime
                .now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_DATE_TIME), XSDDatatype.XSDdateTime);
    }

    public void addDataset(String uriRef) {
        DCATAPUriRef schema = DCATAPUriSchema.parseUriRef(uriRef);
        catalogue.addProperty(DCAT.dataset, model.createResource(schema.getDatasetUriRef()));
        catalogue.addProperty(DCAT.record, model.createResource(schema.getRecordUriRef()));
    }


    private void renameReferences() {
        model.listSubjectsWithProperty(RDF.type, DCAT.Catalog).forEachRemaining(ds ->
                ResourceUtils.renameResource(ds, uriRef()));
    }

    /**
     * Validates that the catalog has exactly one dct:language and one dct:spatial.
     * Temp solution for Release 5
     * @param catalog the catalog resource to validate
     */
    private void validateSingleLanguageAndSpatial(Resource catalog) {
        long languageCount = catalog.listProperties(DCTerms.language).toList().size();
        long spatialCount = catalog.listProperties(DCTerms.spatial).toList().size();

        if (languageCount != 1 || spatialCount != 1) {
            throw new IllegalArgumentException(String.format(
                "Catalog must have exactly one dct:language and one dct:spatial. Found: language=%d, spatial=%d",
                languageCount, spatialCount
            ));
        }
    }
}
