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
package io.piveau.hub.services.metrics;

import io.piveau.dcatap.TripleStore;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface MetricsService {
    String SERVICE_ADDRESS = "io.piveau.hub.metrics.queue";

    static Future<MetricsService> create(TripleStore tripleStore, TripleStore shadowTripleStore, Vertx vertx, JsonObject config) {
        return Future.future(promise -> new MetricsServiceImpl(tripleStore, shadowTripleStore, vertx, config, promise));
    }

    static MetricsService createProxy(Vertx vertx, String address) {
        return new io.piveau.hub.services.metrics.MetricsServiceVertxEBProxy(vertx, address, new DeliveryOptions().setSendTimeout(120000));
    }

    /**
     * get a Metrics graph via the normalized dataset ID
     *
     * @param datasetId
     * @param history
     * @param contentType
     * @return Future
     */
    Future<String> getMetrics(String datasetId, boolean history, String contentType);

    Future<String> putMetrics(String datasetId, String content, String contentType);

    Future<Void> deleteMetrics(String datasetId, String catalogueId);

}
