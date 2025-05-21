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
package io.piveau.hub.services.identifiers;

import io.piveau.hub.dataobjects.DatasetHelper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

import java.util.List;

/**
 * Examples: https://data.europa.eu/data/datasets/national-interoperability-framework-observatory-nifo-digital-government-factsheets-2020?locale=en
 * https://data.europa.eu/api/hub/repo/datasets/national-interoperability-framework-observatory-nifo-digital-government-factsheets-2020.ttl?useNormalizedId=true&locale=en
 */

public abstract class IdentifierRegistry {

    protected final Vertx vertx;
    protected final WebClient webClient;
    protected final JsonObject config;


    public IdentifierRegistry(Vertx vertx, WebClient webClient, JsonObject config) {
        this.vertx = vertx;
        this.webClient = webClient;
        this.config = config;
    }

    public abstract String getName();

    public abstract Future<Identifier> getIdentifier(String URI, DatasetHelper dataset, List<Identifier> existingIdentifiers);
    public abstract JsonObject getIdentifierEligibility(JsonObject metadata);

}
