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

import io.piveau.dcatap.TripleStore;
import io.piveau.hub.services.translation.TranslationService;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

@ProxyGen
public interface IdentifiersService {
    String SERVICE_ADDRESS = "io.piveau.hub.identifiers.queue";

    static IdentifiersService create(Vertx vertx, WebClient client, JsonObject config, TripleStore tripleStore,
                                     Handler<AsyncResult<IdentifiersService>> readyHandler) {
        return new IdentifiersServiceImpl(vertx, client, config, tripleStore, readyHandler);
    }

    static IdentifiersService createProxy(Vertx vertx, String address) {
        return new IdentifiersServiceVertxEBProxy(vertx, address, new DeliveryOptions().setSendTimeout(120000));
    }

    @Fluent
    IdentifiersService createIdentifier(String datasetId, String catalogueId, String type, Handler<AsyncResult<JsonObject>> handler);
    @Fluent
    IdentifiersService checkIdentifierRequirement(String datasetId, String catalogueId, String type, Handler<AsyncResult<JsonObject>> handler);


}
