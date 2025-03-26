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

import io.piveau.HubRepo;
import io.piveau.dcatap.DCATAPUriRef;
import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.dcatap.DatasetManager;
import io.piveau.dcatap.TripleStoreException;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.serviceproxy.ServiceException;

public class DatasetIdentifierHandler implements Handler<RoutingContext> {

    private final DatasetManager datasetManager;

    public DatasetIdentifierHandler(DatasetManager datasetManager) {
        this.datasetManager = datasetManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        RequestParameters parameters = routingContext.get(ValidationHandler.REQUEST_CONTEXT_KEY);
        String id = parameters.pathParameter("distributionId").getString();

        DCATAPUriRef uriRef = DCATAPUriSchema.createFor(id, DCATAPUriSchema.getDistributionContext());

        datasetManager.identifyDistribution(uriRef.getUriRef())
                .compose(datasetUriRef -> {
                    routingContext.put("datasetId", datasetUriRef.getId());
                    return datasetManager.catalogue(datasetUriRef.getUriRef());
                })
                .onSuccess(resource -> {
                    routingContext.put("catalogueId", DCATAPUriSchema.parseUriRef(resource.getURI()).getId());
                    routingContext.next();
                })
                .onFailure(cause -> {
                    if (cause instanceof TripleStoreException && ((TripleStoreException) cause).getCode() == 404) {
                        HubRepo.failureResponse(routingContext, new ServiceException(404, "Distribution not found"));
                    } else {
                        HubRepo.failureResponse(routingContext, cause);
                    }
                });
    }

}
