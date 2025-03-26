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
package io.piveau.hub.handler;

import io.piveau.HubRepo;
import io.piveau.hub.services.translation.TranslationService;
import io.piveau.hub.util.logger.PiveauLoggerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.serviceproxy.ServiceException;

public class TranslationServiceHandler {

    private final TranslationService translationService;

    public TranslationServiceHandler(Vertx vertx) {
        translationService = TranslationService.createProxy(vertx, TranslationService.SERVICE_ADDRESS);
    }

    public void handlePostTranslation(RoutingContext context) {
        try {
            RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
            JsonObject translation = parameters.body().getJsonObject();

            PiveauLoggerFactory.getLogger(getClass()).debug(translation.encode());

            translationService.receiveTranslation(translation, ar -> {
                if (ar.succeeded()) {
                    JsonObject result = ar.result();
                    switch (result.getString("status")) {
                        case "success" -> context.response().setStatusCode(200).end();
                        case "Not found" -> context.fail(404);
                        default ->
                                HubRepo.failureResponse(context, new ServiceException(500, "Unexpected status code"));
                    }
                } else {
                    HubRepo.failureResponse(context, ar.cause());
                }
            });
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }
}
