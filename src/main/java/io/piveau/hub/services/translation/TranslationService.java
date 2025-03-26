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
package io.piveau.hub.services.translation;

import io.piveau.dcatap.TripleStore;
import io.piveau.hub.dataobjects.DatasetHelper;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

@ProxyGen
public interface TranslationService {
    String SERVICE_ADDRESS = "io.piveau.hub.translationservice.queue";

    // Required static methods
    // based on the documentation (https://github.com/vert-x3/vertx-service-proxy)
    static TranslationService create(Vertx vertx, WebClient client, JsonObject config, TripleStore tripleStore,
                                     Handler<AsyncResult<TranslationService>> readyHandler) {
        return new TranslationServiceImpl(vertx, client, config, tripleStore, readyHandler);
    }

    static TranslationService createProxy(Vertx vertx, String address) {
        return new TranslationServiceVertxEBProxy(vertx, address, new DeliveryOptions().setSendTimeout(120000));
    }

    @Fluent
    TranslationService initializeTranslationProcess(DatasetHelper helper, DatasetHelper oldHelper, Boolean force, Handler<AsyncResult<DatasetHelper>> asyncHandler);

    @Fluent
    TranslationService receiveTranslation(JsonObject translation, Handler<AsyncResult<JsonObject>> asyncHandler);
}
