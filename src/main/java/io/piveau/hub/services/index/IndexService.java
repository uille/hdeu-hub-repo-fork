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
package io.piveau.hub.services.index;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

@ProxyGen
public interface IndexService {
    String SERVICE_ADDRESS = "io.piveau.hub.index.queue";
    Long DEFAULT_TIMEOUT = 300000L;

    static IndexService create(WebClient client, JsonObject config, Handler<AsyncResult<IndexService>> readyHandler) {
        return new IndexServiceImpl(client, config, readyHandler);
    }

    static IndexService createProxy(Vertx vertx, String address, Long timeout) {
        return new IndexServiceVertxEBProxy(vertx, address, new DeliveryOptions().setSendTimeout(timeout));
    }

    @Fluent
    IndexService addDatasetWithoutCB(JsonObject dataset, Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    IndexService addDataset(JsonObject dataset, Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    IndexService deleteDataset(String id, Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    IndexService getDataset(String id, Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    IndexService deleteCatalog(String id, Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    IndexService addCatalog(JsonObject catalog, Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    IndexService listDatasets(String catalogue, String alias, Handler<AsyncResult<JsonArray>> handler);

    @Fluent
    IndexService addDatasetPut(JsonObject dataset, Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    IndexService modifyDataset(String datasetId, JsonObject dataset, Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    IndexService putVocabulary(String vocabularyId, JsonObject vocabulary, Handler<AsyncResult<JsonArray>> handler);

    @Fluent
    IndexService deleteVocabulary(String vocabularyId, Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    IndexService putDatasetBulk(JsonArray datasets, Handler<AsyncResult<JsonArray>> handler);

    @Fluent
    IndexService putResource(String id, String type, JsonObject payload, Handler<AsyncResult<Void>> handler);

    @Fluent
    IndexService deleteResource(String id, String type, Handler<AsyncResult<Void>> handler);
}
