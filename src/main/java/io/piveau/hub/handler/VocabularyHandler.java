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
import io.piveau.hub.Constants;
import io.piveau.hub.services.vocabularies.VocabulariesService;
import io.piveau.hub.util.ContentNegotiation;
import io.piveau.hub.util.ContentType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.serviceproxy.ServiceException;

public class VocabularyHandler {

    private final VocabulariesService vocabulariesService;

    public VocabularyHandler(Vertx vertx) {
        vocabulariesService = VocabulariesService.createProxy(vertx, VocabulariesService.SERVICE_ADDRESS);
    }

    public void listVocabularies(RoutingContext context) {
        try {
            ContentNegotiation contentNegotiation = new ContentNegotiation(context, "dummyId", ContentType.JSON_LD);
            RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

            Integer offset = parameters.queryParameter(Constants.OFFSET).getInteger();
            Integer limit = parameters.queryParameter(Constants.LIMIT).getInteger();

            String valueType = parameters.queryParameter("valueType").getString();
            if (!valueType.equals("metadata")) {
                contentNegotiation.setAcceptType(ContentType.JSON.getMimeType());
            }

            vocabulariesService.listVocabularies(contentNegotiation.getAcceptType(), valueType, limit, offset)
                    .onSuccess(contentNegotiation::headOrGetResponse)
                    .onFailure(cause -> HubRepo.failureResponse(context, cause));
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void getVocabulary(RoutingContext context) {
        try {
            ContentNegotiation contentNegotiation = new ContentNegotiation(context, "vocabularyId", ContentType.DEFAULT);
            vocabulariesService.getVocabulary(contentNegotiation.getId(), contentNegotiation.getAcceptType())
                    .onSuccess(contentNegotiation::headOrGetResponse)
                    .onFailure(cause -> HubRepo.failureResponse(context, cause));
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void createOrUpdateVocabulary(RoutingContext context) {
        try {
            String id = context.request().getParam("vocabularyId");
            String uri = context.request().getParam("uri");
            String hash = context.request().getParam("hash");
            String chunkIdParam = context.request().getParam("chunkId");
            String numberOfChunksParam = context.request().getParam("numberOfChunks");

            int chunkId;
            int numberOfChunks;
            if (chunkIdParam == null || numberOfChunksParam == null) {
                chunkId = 0;
                numberOfChunks = 1;
            } else {
                chunkId = Integer.parseUnsignedInt(chunkIdParam);
                numberOfChunks = Integer.parseUnsignedInt(numberOfChunksParam);
            }

            String contentType = context.parsedHeaders().contentType().rawValue();
            String payload = context.body().asString();

            Future<String> putFuture;
            if (numberOfChunks == 1) {
                putFuture = vocabulariesService.putVocabulary(id, uri, contentType, payload);
            } else {
                putFuture = vocabulariesService.putVocabularyChunk(id, uri, contentType, payload, hash, chunkId, numberOfChunks);
            }
            putFuture
                    .onSuccess(result -> {
                        switch (result) {
                            case "created" -> context.response()
                                    .putHeader(HttpHeaders.LOCATION, "/vocabularies/" + id)
                                    .setStatusCode(201)
                                    .end();
                            case "accepted" -> context.response().setStatusCode(202).end();
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

    public void putVocabulary(RoutingContext context) {
        try {
            ContentNegotiation contentNegotiation = new ContentNegotiation(context, "vocabularyId", ContentType.DEFAULT);
            RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
            RequestParameter body = parameters.body();
            if (body.isEmpty()) {
                context.fail(400, new ServiceException(400, "Body required"));
                return;
            }
            String vocabularyId = contentNegotiation.getId();
            String contentType = context.parsedHeaders().contentType().value();
            String content = body.toString();
            vocabulariesService.putVocabulary(vocabularyId, "", contentType, content)
                    .onSuccess(result -> {
                        switch (result) {
                            case "created" -> context.response()
                                    .setStatusCode(201)
                                    .putHeader(HttpHeaders.LOCATION, "/vocabularies/" + vocabularyId)
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

    public void deleteVocabulary(RoutingContext context) {
        try {
            RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
            String id = parameters.pathParameter("vocabularyId").getString();
            vocabulariesService.deleteVocabulary(id)
                    .onSuccess(v -> context.response().setStatusCode(204).end())
                    .onFailure(cause -> HubRepo.failureResponse(context, cause));
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

}
