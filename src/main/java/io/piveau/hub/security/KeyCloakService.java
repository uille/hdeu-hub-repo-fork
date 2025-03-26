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
package io.piveau.hub.security;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface KeyCloakService {
    String SERVICE_ADDRESS="io.piveau.hub.security.keycloakqueue";

    static KeyCloakService create(Vertx vertx, JsonObject config, Handler<AsyncResult<KeyCloakService>> readyHandler) {
        return new KeyCloakServiceImpl(vertx, config, readyHandler);
    }

    static KeyCloakService createProxy(Vertx vertx, String address) {
        return new KeyCloakServiceVertxEBProxy(vertx, address);
    }

    @Fluent
    KeyCloakService createResource(String catalogueId);

    @Fluent
    KeyCloakService deleteResource(String catalogueId);

}
