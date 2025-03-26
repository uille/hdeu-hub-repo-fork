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
import io.piveau.hub.services.resources.ResourcesService;
import io.piveau.hub.util.ContentNegotiation;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.serviceproxy.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ResourceHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ResourcesService resourcesService;

    public ResourceHandler(Vertx vertx) {
        resourcesService = ResourcesService.createProxy(vertx, ResourcesService.SERVICE_ADDRESS);
    }

    public void listResourceTypes(RoutingContext context) {
        try {
            context.response().putHeader("Access-Control-Allow-Origin", "*");
            resourcesService.listResourceTypes().onSuccess(result -> {
                context.response().putHeader("Content-Type", "application/json");
                context.response().setStatusCode(200).end(result.toString());
            }).onFailure(cause -> {
                context.response().putHeader("Content-Type", "text/plain");
                context.response().setStatusCode(400).end(cause.getMessage());
            });
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void listResources(RoutingContext context) {
        try {
            String type = context.request().getParam("type");
            context.response().putHeader("Access-Control-Allow-Origin", "*");
            resourcesService.listResources(type).onSuccess(result -> {
                context.response().putHeader("Content-Type", "application/json");
                context.response().setStatusCode(200).end(result.toString());
            }).onFailure(cause -> {
                context.response().putHeader("Content-Type", "text/plain");
                context.response().setStatusCode(400).end(cause.getMessage());
            });
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void getResource(RoutingContext context) {
        try {
            ContentNegotiation contentNegotiation = new ContentNegotiation(context);
            String id = contentNegotiation.getId();
            String type = context.request().getParam("type");
            String acceptType = contentNegotiation.getAcceptType();

            boolean headRequest = context.request().method() == HttpMethod.HEAD;

            context.response().putHeader("Access-Control-Allow-Origin", "*");
            context.response().putHeader("Content-Type", acceptType);
            resourcesService.getResource(id, type, acceptType)
                    .onSuccess(contentNegotiation::headOrGetResponse)
                    .onFailure(cause -> HubRepo.failureResponse(context, cause));
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void deleteResource(RoutingContext context) {
        try {
            String id = context.request().getParam("id");
            String type = context.request().getParam("type");

            context.response().putHeader("Access-Control-Allow-Origin", "*");
            resourcesService.deleteResource(id, type)
                    .onSuccess(v -> context.response().setStatusCode(204).end())
                    .onFailure(cause -> HubRepo.failureResponse(context, cause));
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void postResource(RoutingContext context) {
        try {
            String id = UUID.randomUUID().toString();
            context.request().params().add("id", id);
            putResource(context);
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void putResource(RoutingContext context) {
        try {
            String id = context.request().getParam("id");
            String type = context.request().getParam("type");
            String content = context.body().asString();
            String contentType = context.parsedHeaders().contentType().value();

            context.response().putHeader("Access-Control-Allow-Origin", "*");
            resourcesService.putResource(id, type, content, contentType)
                    .onSuccess(result -> {
                        switch (result) {
                            case "created" -> {
                                context.response().setStatusCode(201)
                                .putHeader(HttpHeaders.LOCATION, result)
                                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                .end(new JsonObject().put("id", id).encode());
                            }
                            case "updated" -> {
                                context.response().setStatusCode(200)
                                .putHeader(HttpHeaders.LOCATION, result)
                                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                .end(new JsonObject().put("id", id).encode());
                            }
                            default -> {
                                // should not happen, succeeded path should only respond with 2xx codes
                                logger.error("Resource could not be created: " + result);
                                context.response().setStatusCode(400).end(result);
                            }
                        }
                    })
                    .onFailure(cause -> {
                        logger.error("An error occurred in putResource: ", cause);
                        HubRepo.failureResponse(context, cause);
                    });
        } catch (Exception e) {
            logger.error("An exception occurred in putResource: ", e);
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }

    }
}
