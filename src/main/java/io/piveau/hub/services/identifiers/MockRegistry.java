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
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

public class MockRegistry extends IdentifierRegistry {

    public MockRegistry(Vertx vertx, WebClient webClient, JsonObject config) {
        super(vertx, webClient, config);
    }

    @Override
    public String getName() {
        return "Mock Identity Registry";
    }

    @Override
    public Future<Identifier> getIdentifier(String URI, DatasetHelper dataset, List<Identifier> existingIdentifiers) {
        Promise<Identifier> promise = Promise.promise();
        //promise.fail("This identifier is already set");
        Identifier identifier =  new Identifier();
        identifier.identifierURI = "http://local.io/doi/abcd/12345678";
        identifier.issued = ZonedDateTime.now(ZoneOffset.UTC);
        identifier.creatorURI = "http://piveau.eu/creator";
        identifier.identifier = "abcd/12345678";
        identifier.schema = "http://piveau.eu/identifiers/scheme";
        promise.complete(identifier);
        return promise.future();
    }

    @Override
    public JsonObject getIdentifierEligibility(JsonObject metadata) {
        JsonObject requiredElement = new JsonObject();
        requiredElement.put("none", true);

        JsonObject result = new JsonObject();
        result.put("identifierName", getName())
                .put("isEligible", true)
                .put("element", requiredElement);

        return result;
    }

}
