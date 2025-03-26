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
package io.piveau.hub.services.resources;

import io.piveau.dcatap.TripleStore;
import io.piveau.profile.*;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface ResourcesService {
    String SERVICE_ADDRESS = "io.piveau.hub.resources.queue";

    static ResourcesService create(Vertx vertx, TripleStore tripleStore, JsonObject config, PiveauProfile profile,
                                   Handler<AsyncResult<ResourcesService>> readyHandler) {
        return new ResourcesServiceImpl(vertx, tripleStore, config, profile, readyHandler);
    }

    static ResourcesService createProxy(Vertx vertx, String address) {
        DeliveryOptions options = new DeliveryOptions().setSendTimeout(120000);
        return new ResourcesServiceVertxEBProxy(vertx, address, options);
    }

    Future<JsonArray> listResourceTypes();

    Future<JsonArray> listResources(String type);

    Future<String> getResource(String id, String type, String acceptType);

    Future<Void> deleteResource(String id, String type);

    Future<String> putResource(String id, String type, String content, String contentType);
}
