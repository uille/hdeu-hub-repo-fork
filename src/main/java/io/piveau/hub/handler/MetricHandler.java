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
import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.hub.services.metrics.MetricsService;
import io.piveau.hub.util.ContentNegotiation;
import io.piveau.hub.util.ContentType;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.serviceproxy.ServiceException;

public class MetricHandler {

    private final MetricsService metricsService;

    public MetricHandler(Vertx vertx) {
        metricsService = MetricsService.createProxy(vertx, MetricsService.SERVICE_ADDRESS);
    }

    public void handleGetMetric(RoutingContext context) {
        try {
            RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

            boolean fetchHistoricGraph = false;
            if (parameters.queryParameter("historic") != null) {
                fetchHistoricGraph = parameters.queryParameter("historic").getBoolean();
            }

            ContentNegotiation contentNegotiation = new ContentNegotiation(context, "datasetId", ContentType.JSON_LD);

            String id = contentNegotiation.getId();
            String acceptType = contentNegotiation.getAcceptType();

            metricsService.getMetrics(id, fetchHistoricGraph, acceptType)
                    .onSuccess(contentNegotiation::headOrGetResponse)
                    .onFailure(cause -> HubRepo.failureResponse(context, cause));
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void handlePutMetric(RoutingContext context) {
        try {
            RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

            String id = parameters.pathParameter("datasetId").getString();

            RequestParameter body = parameters.body();
            if (body.isEmpty()) {
                context.fail(400, new ServiceException(400, "Body required"));
                return;
            }
            String content = body.toString();

            String contentType = context.parsedHeaders().contentType().value();

            metricsService.putMetrics(id, content, contentType)
                    .onSuccess(result -> {
                        switch (result) {
                            case "created" -> context.response()
                                    .setStatusCode(201)
                                    .putHeader(
                                            HttpHeaders.LOCATION,
                                            DCATAPUriSchema.createFor(id, DCATAPUriSchema.getMetricsContext()).getMetricsUriRef())
                                    .end();
                            case "updated" -> context.response().setStatusCode(204).end();
                            default ->
                                    HubRepo.failureResponse(context, new ServiceException(500, "Unexpected status code"));
                        }
                    })
                    .onFailure(cause -> HubRepo.failureResponse(context, cause));
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void handleDeleteMetric(RoutingContext context) {
        try {
            RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

            RequestParameter idParameter = parameters.pathParameter("datasetId");
            String datasetId = idParameter == null ? parameters.queryParameter("id").getString() : idParameter.getString();

            // TODO check is parameter is required
            String catalogueId = parameters.queryParameter("catalogue").getString();

            metricsService.deleteMetrics(datasetId, catalogueId)
                    .onSuccess(v -> context.response().setStatusCode(204).end())
                    .onFailure(cause -> HubRepo.failureResponse(context, cause));
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

}
