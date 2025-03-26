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
package io.piveau.hub.services.catalogues;

import io.piveau.dcatap.TripleStore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface CataloguesService {
    String SERVICE_ADDRESS="io.piveau.hub.catalogues.queue";

    static Future<CataloguesService> create(TripleStore tripleStore, Vertx vertx, JsonObject config) {
        return Future.future(promise -> new CataloguesServiceImpl(tripleStore, vertx, config, promise));
    }

    static CataloguesService createProxy(Vertx vertx, String address) {
        return new CataloguesServiceVertxEBProxy(vertx, address, new DeliveryOptions().setSendTimeout(120000));
    }

    Future<String> listCatalogues(String acceptType, String valueType, Integer offset, Integer limit);

    Future<String> getCatalogue(String id, String acceptType);

    Future<String> putCatalogue(String id, String content, String contentType);

    Future<Void> deleteCatalogue(String id);

}
