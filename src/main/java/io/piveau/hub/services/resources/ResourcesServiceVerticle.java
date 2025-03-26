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
import io.piveau.hub.Constants;
import io.piveau.json.ConfigHelper;
import io.piveau.profile.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;

public class ResourcesServiceVerticle extends AbstractVerticle {

    private PiveauProfile profile;

    public ResourcesServiceVerticle(PiveauProfile profile) {
        this.profile = profile;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        JsonObject config = ConfigHelper.forConfig(config()).forceJsonObject(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG);
        TripleStore tripleStore = new TripleStore(vertx, config);

        ResourcesService.create(vertx, tripleStore, config(), profile, serviceReady -> {
            if (serviceReady.succeeded()) {
                new ServiceBinder(vertx).setAddress(ResourcesService.SERVICE_ADDRESS)
                        .register(ResourcesService.class, serviceReady.result());
                startPromise.complete();
            } else {
                startPromise.fail(serviceReady.cause());
            }
        });
    }
}
