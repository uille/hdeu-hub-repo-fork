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
package io.piveau.hub.services.distributions;

import io.piveau.dcatap.TripleStore;
import io.piveau.hub.services.datasets.DatasetsService;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;

@ProxyGen
public interface DistributionsService {

    String SERVICE_ADDRESS = "io.piveau.hub.distributions.queue";

    static Future<DistributionsService> create(TripleStore tripleStore, DatasetsService datasetsService) {
        return Future.future(promise -> new DistributionsServiceImpl(tripleStore, datasetsService, promise));
    }

    static DistributionsService createProxy(Vertx vertx, String address) {
        return new DistributionsServiceVertxEBProxy(vertx, address, new DeliveryOptions().setSendTimeout(120000));
    }

    Future<String> listDatasetDistributions(String datasetId, String valueType, String acceptType);

    Future<String> getDistribution(String id, String acceptType);

    Future<String> postDistribution(String datasetId, String content, String contentType);

    Future<String> putDistribution(String datasetId, String distributionId, String content, String contentType);

    Future<Void> deleteDistribution(String datasetId, String distributionId);

}
