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
package io.piveau.hub.services.datasets;

import io.piveau.dcatap.TripleStore;
import io.piveau.hub.Constants;
import io.piveau.hub.util.DataUploadConnector;
import io.piveau.json.ConfigHelper;
import io.piveau.pipe.PiveauCluster;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.serviceproxy.ServiceBinder;

public class DatasetsServiceVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        WebClient client = WebClient.create(vertx);

        ConfigHelper configHelper = ConfigHelper.forConfig(config());
        JsonObject conf = configHelper.forceJsonObject(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG);
        JsonObject shadowConf = configHelper.forceJsonObject(Constants.ENV_PIVEAU_SHADOW_TRIPLESTORE_CONFIG);
        JsonObject dataUploadConf = configHelper.forceJsonObject(Constants.ENV_PIVEAU_DATA_UPLOAD);
        JsonObject clusterConfig = configHelper.forceJsonObject(Constants.ENV_PIVEAU_CLUSTER_CONFIG);

        PiveauCluster.create(vertx, clusterConfig).onComplete(init -> {
            if (init.succeeded()) {
                TripleStore tripleStore = new TripleStore(vertx, conf, null, "datasets-main");
                TripleStore shadowTripleStore;
                if (!shadowConf.isEmpty()) {
                    shadowTripleStore = new TripleStore(vertx, shadowConf, null, "datasets-shadow");
                } else {
                    shadowTripleStore = tripleStore;
                }
                DataUploadConnector dataUploadConnector = DataUploadConnector.create(client, dataUploadConf);

                init.result().pipeLauncher(vertx)
                        .compose(launcher -> DatasetsService.create(tripleStore, shadowTripleStore, dataUploadConnector, config(), launcher, vertx))
                        .onSuccess(service -> {
                            new ServiceBinder(vertx).setAddress(DatasetsService.SERVICE_ADDRESS).register(DatasetsService.class, service);
                            startPromise.complete();
                        })
                        .onFailure(startPromise::fail);
            } else {
                startPromise.fail(init.cause());
            }
        });
    }

}
