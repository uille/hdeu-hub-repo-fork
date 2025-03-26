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
import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.hub.services.drafts.DatasetDraftsService;
import io.piveau.hub.util.ContentNegotiation;
import io.piveau.rdf.Piveau;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.serviceproxy.ServiceException;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DatasetDraftHandler {

    private final DatasetsService datasetsService;
    private final DatasetDraftsService datasetDraftsService;

    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetDraftHandler.class);

    public DatasetDraftHandler(Vertx vertx) {
        datasetsService = DatasetsService.createProxy(vertx, DatasetsService.SERVICE_ADDRESS);
        datasetDraftsService = DatasetDraftsService.createProxy(vertx, DatasetDraftsService.SERVICE_ADDRESS);
    }

    public void listDatasetDrafts(RoutingContext context) {
        try {
            HttpServerResponse response = context.response();

            boolean filterByProvider = !context.queryParam("filterByProvider").isEmpty() &&
                    !context.queryParam("filterByProvider").get(0).equals("false");

            List<String> authorizedResources = context.queryParam("authorizedResources");
            String provider = filterByProvider ? context.queryParam("provider").get(0) : "";

            datasetDraftsService.listDatasetDrafts(authorizedResources, provider, result -> {
                if (result.succeeded()) {
                    response.putHeader("Content-Type", "application/json").end(result.result().toString());
                } else {
                    HubRepo.failureResponse(context, result.cause());
                }
            });
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void createDatasetDraft(RoutingContext context) {
        try {
            String catalogueId = context.queryParam("catalogue").get(0);
            String contentType = context.parsedHeaders().contentType().value();

            String provider = context.queryParam("provider").get(0);

            datasetDraftsService.createDatasetDraft(catalogueId, context.body().asString(), contentType, provider, result -> {
                if (result.succeeded()) {
                    if ("created".equals(result.result())) {
                        context.response().setStatusCode(201).end();
                    } else {
                        // should not happen, succeeded path should only respond with 2xx codes
                        context.response().setStatusCode(400).end();
                    }
                } else {
                    HubRepo.failureResponse(context, result.cause());
                }
            });
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void readDatasetDraft(RoutingContext context) {
        try {
            ContentNegotiation contentNegotiation = new ContentNegotiation(context);
            String datasetId = contentNegotiation.getId();
            String acceptType = contentNegotiation.getAcceptType();

            String catalogueId = context.queryParam("catalogue").get(0);

            HttpServerResponse response = context.response();
            datasetDraftsService.readDatasetDraft(datasetId, catalogueId, acceptType, result -> {
                if (result.succeeded()) {
                    response.putHeader("Content-Type", acceptType).end(result.result());
                } else {
                    HubRepo.failureResponse(context, result.cause());
                }
            });
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void createOrUpdateDatasetDraft(RoutingContext context) {
        try {
            String datasetId = context.pathParam("id");
            String catalogueId = context.queryParam("catalogue").get(0);

            String contentType = context.parsedHeaders().contentType().value();

            String provider = context.queryParam("provider").isEmpty() ? "" : context.queryParam("provider").get(0);

            datasetDraftsService.createOrUpdateDatasetDraft(datasetId, catalogueId, context.body().asString(), contentType,
                    provider, result -> {
                        if (result.succeeded()) {
                            switch (result.result()) {
                                case "created" -> context.response().setStatusCode(201).end();
                                case "updated" -> context.response().setStatusCode(204).end();
                                default ->
                                    // should not happen, succeeded path should only respond with 2xx codes
                                        context.response().setStatusCode(400).end();
                            }
                        } else {
                            HubRepo.failureResponse(context, result.cause());
                        }
                    });
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void deleteDatasetDraft(RoutingContext context) {
        try {
            String datasetId = context.pathParam("id");
            String catalogueId = context.queryParam("catalogue").get(0);

            HttpServerResponse response = context.response();
            datasetDraftsService.deleteDatasetDraft(datasetId, catalogueId, result -> {
                if (result.succeeded()) {
                    response.setStatusCode(204).end();
                } else {
                    HubRepo.failureResponse(context, result.cause());
                }
            });
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void publishDatasetDraft(RoutingContext context) {
        try {
            String datasetId = context.pathParam("id");
            String catalogueId = context.queryParam("catalogue").get(0);
            String acceptType = "application/n-triples";

            HttpServerResponse response = context.response();
            datasetDraftsService.readDatasetDraft(datasetId, catalogueId, acceptType, readDatasetDraftResult -> {
                if (readDatasetDraftResult.succeeded()) {
                    datasetsService.putDatasetOrigin(datasetId, removeCatalogRecord(readDatasetDraftResult.result(), acceptType),
                                    acceptType, catalogueId, false)
                            .onSuccess(result -> datasetDraftsService.deleteDatasetDraft(datasetId, catalogueId, deleteDatasetDraftResult -> {
                                if (deleteDatasetDraftResult.succeeded()) {
                                    response.setStatusCode(204).end();
                                } else {
                                    HubRepo.failureResponse(context, deleteDatasetDraftResult.cause());
                                }
                            }))
                            .onFailure(cause -> HubRepo.failureResponse(context, cause));
                } else {
                    HubRepo.failureResponse(context, readDatasetDraftResult.cause());
                }
            });
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    public void hideDataset(RoutingContext context) {
        try {
            String datasetId = context.pathParam("id");
            String catalogueId = context.queryParam("catalogue").get(0);
            String acceptType = "application/n-triples";

            String provider = context.queryParam("provider").isEmpty() ? "" : context.queryParam("provider").get(0);

            HttpServerResponse response = context.response();
            datasetsService.getDatasetOrigin(datasetId, catalogueId, acceptType)
                    .onSuccess(content ->
                            datasetDraftsService.createOrUpdateDatasetDraft(datasetId, catalogueId,
                                    content, acceptType, provider,
                                    createOrUpdateDatasetDraftResult -> {
                                        if (createOrUpdateDatasetDraftResult.succeeded()) {
                                            datasetsService.deleteDatasetOrigin(datasetId, catalogueId)
                                                    .onSuccess(v -> response.setStatusCode(204).end())
                                                    .onFailure(cause -> HubRepo.failureResponse(context, cause));
                                        } else {
                                            HubRepo.failureResponse(context, createOrUpdateDatasetDraftResult.cause());
                                        }
                                    }))
                    .onFailure(cause -> HubRepo.failureResponse(context, cause));
        } catch (Exception e) {
            HubRepo.failureResponse(context, new ServiceException(500, "Internal Server Error"));
        }
    }

    private String removeCatalogRecord(String payload, String acceptType) {

        Model model = Piveau.toModel(payload.getBytes(), acceptType);
        ResIterator resIterator = model.listSubjectsWithProperty(RDF.type, DCAT.CatalogRecord);
        if (resIterator.hasNext()) {
            Resource catalogRecord = resIterator.next();
            List<Statement> toBeRemoved = new ArrayList<>();
            calcToBeRemoved(catalogRecord, toBeRemoved, model);
            model.remove(toBeRemoved);
            return Piveau.presentAs(model, acceptType);
        }
        return payload;

    }

    private void calcToBeRemoved(Resource rdfNode, List<Statement> toBeRemoved, Model model) {
        try {
            StmtIterator stmtIterator = model.listStatements();
            while (stmtIterator.hasNext()) {
                Statement statement = stmtIterator.next();
                if (statement.getSubject().equals(rdfNode)) {
                    toBeRemoved.add(statement);
                    if (statement.getObject().isAnon()) {
                        calcToBeRemoved(statement.getObject().asResource(), toBeRemoved, model);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("DatasetDraftHandler :: calcToBeRemoved  :: error :: {}", e.getMessage(), e);
            throw e;
        }
    }
}
