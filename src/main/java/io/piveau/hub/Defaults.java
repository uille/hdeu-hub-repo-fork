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
package io.piveau.hub;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public final class Defaults {

    private Defaults() {
    }

    public static final String GREETING = "You know, for metadata!";

    public static final int SERVICE_PORT = 8080;

    public static final boolean VALIDATOR_ENABLED = false;

    public static final boolean SEARCH_SERVICE_ENABLED = false;

    public static final boolean LOAD_VOCABULARIES = true;
    public static final boolean LOAD_VOCABULARIES_FETCH = false;

    public static final boolean METRICS_HISTORY = false;

    public static final boolean XML_DECLARATION = true;

    public static final boolean TRANSLATION_SERVICE_ENABLED = false;

    public static final String METRICS_PIPE = "metrics";

    public static final boolean FORCE_UPDATES = false;

    public static final boolean EXPERIMENTS = false;

    public static final long BODY_LIMIT = 20971520L;

    public static final Resource DEFAULT_LANG = ResourceFactory.createResource("http://publications.europa.eu/resource/authority/language/ENG");

    public static final String ODP_URL_TEMPLATE = "https://%s.piveau.io";

}
