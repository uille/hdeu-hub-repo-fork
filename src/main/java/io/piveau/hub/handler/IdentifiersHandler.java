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
import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.hub.services.identifiers.IdentifiersService;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.serviceproxy.ServiceException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdentifiersHandler {

    private final IdentifiersService identifiersService;

    private final Logger log = LoggerFactory.getLogger(getClass());

    public IdentifiersHandler(Vertx vertx) {
        identifiersService = IdentifiersService.createProxy(vertx, IdentifiersService.SERVICE_ADDRESS);
    }

    public void createDatasetIdentifier(RoutingContext context) {
        String datasetId = context.pathParam("datasetId");
        String catalogueId = context.queryParam("catalogue").get(0);
        String type = context.queryParam("type").get(0);

        identifiersService.createIdentifier(datasetId, catalogueId, type, ar -> {
            if(ar.succeeded()) {
                context
                    .response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .setStatusCode(200)
                    .end(ar.result().toString());
            } else {
                HubRepo.failureResponse(context, ar.cause());
            }
        });
    }

    public void checkDatasetIdentifierEligibility(RoutingContext context) {
        String datasetId = context.pathParam("datasetId");
        String catalogueId = context.queryParam("catalogue").get(0);

        RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
        RequestParameter type = parameters.queryParameter("type");
        String identifierType = type != null ? type.getString() : null;

        identifiersService.checkIdentifierRequirement(datasetId, catalogueId, identifierType, ar -> {
            if(ar.succeeded()) {
                context
                    .response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .setStatusCode(200)
                    .end(ar.result().toString());
            } else {
                HubRepo.failureResponse(context, ar.cause());
            }
        });
    }

    /*
     * public void handleExtractDistributionIdentifiers(RoutingContext context) {
     * log.info(context.toString()); RequestParameters parameters =
     * context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
     *
     *
     * // Get the RDF content from the request body String rdfContent =
     * parameters.body().toString();
     *
     * // Use DatasetHelper to parse the RDF content and extract distribution
     * identifiers List<String> identifiers =
     * DatasetHelper.getDistributionIdentifiers(rdfContent);
     * log.info("identifiers"+identifiers.toString());
     * log.info("identifiers Array"+Arrays.toString(identifiers.toArray())); if
     * (identifiers == null || identifiers.isEmpty()) { // If no identifiers are
     * found, send a 404 response context.response() .setStatusCode(404)
     * .putHeader("Content-Type", "application/json") .end(new
     * JsonObject().put("error", "No distribution identifiers found").encode()); }
     * else { // Return the extracted identifiers in the response JsonArray response
     * = new JsonArray(identifiers); context.response() .setStatusCode(200)
     * .putHeader("Content-Type", "application/json") .end(response.encode()); } }
     */
// TODO VALEX: handleExtractDatasetIdentifier was added in R0 as workaround for crud operations with connectors
//  in order to get the uuid form the dct:ientifier field and return it in the response
//  i.e. dct:identifier "http://ehelse.healthdataportal.eu/dataset/6ad1de14-b670-458c-b847-2d77243bd343"^^xsd:anyURI;
    public void handleExtractDatasetIdentifier(RoutingContext context) {
        RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

        if (parameters.body().isEmpty()) {
            context.fail(400, new ServiceException(400, "Body required"));
            return;
        }

        String rdfContent = parameters.body().toString();
        String contentType = context.parsedHeaders().contentType().value();

        String identifier = DatasetHelper.getDatasetIdentifier(rdfContent, contentType);

        if (StringUtils.isBlank(identifier)) {

            context.response().setStatusCode(404).putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("error", "No dataset identifier found").encode());
        } else {

            JsonObject response = new JsonObject().put("dataset_id", identifier);

            context.response().setStatusCode(200).putHeader("Content-Type", "application/json").end(response.encode());
        }
    }

}
