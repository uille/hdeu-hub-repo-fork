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
import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.hub.util.ContentNegotiation;
import io.piveau.hub.util.ContentType;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.serviceproxy.ServiceException;
import java.io.StringReader;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetHandler {

  private static final String LIMIT = "limit";
  private static final String OFFSET = "offset";

  private static final String HYDRA = "hydra";

  private static final String USE_PAGED_COLLECTION = "usePagedCollection";

  private static final String QUERY_PARAM_CATALOGUE = "catalogue";
  private static final String PATH_PARAM_CATALOGUE = "catalogueId";
  private static final String PATH_PARAM_DATASET = "datasetId";
  private static final String QUERY_PARAM_DATASET = "originalId";
  private static final String RESULT_STATUS_KEY = "status";
  private static final String RESULT_STATUS_CREATED = "created";
  private static final String RESULT_STATUS_UPDATED = "updated";
  private static final String DATASET_ITB_VALIDATION_ERROR_0101 = "DATASET_ITB_VALIDATION_ERROR_0101";
  private static final String DATASET_ITB_VALIDATION_ERROR_0102 = "DATASET_ITB_VALIDATION_ERROR_0102";
  private static final String DATASET_ITB_UNEXPECTED_ERROR = "DATASET_ITB_UNEXPECTED_ERROR";
  private static final String PUBLIC = "public";

  private final Logger logger = LoggerFactory.getLogger(getClass());


  private final DatasetsService datasetsService;

  public DatasetHandler(Vertx vertx) {
    datasetsService = DatasetsService.createProxy(vertx, DatasetsService.SERVICE_ADDRESS);
  }

  public void handleListDatasets(RoutingContext context) {
    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

    Integer offset = parameters.queryParameter(OFFSET).getInteger();
    Integer limit = parameters.queryParameter(LIMIT).getInteger();
    Boolean hydra = parameters.queryParameter(HYDRA).getBoolean();
    Boolean usePagedCollection = parameters.queryParameter(USE_PAGED_COLLECTION).getBoolean();

    if (hydra == null) {
      hydra = false; // default (false)
    }

    String catalogueId = "";
    RequestParameter param = parameters.queryParameter(QUERY_PARAM_CATALOGUE);
    if (param != null) {
      catalogueId = param.getString();
    }
//        String catalogueId = parameters.queryParametersNames().contains(QUERY_PARAM_CATALOGUE) ? parameters.queryParameter(QUERY_PARAM_CATALOGUE).getString() : "";

    ContentNegotiation contentNegotiation = new ContentNegotiation(context, "dummyId",
        ContentType.JSON_LD);

    String valueType = parameters.queryParameter("valueType").getString();

    Boolean sourceIds = parameters.queryParameter("sourceIds").getBoolean();
    if (sourceIds == Boolean.TRUE) {
      valueType = "originalIds";
    }

    if (!valueType.equals("metadata")) {
      contentNegotiation.setAcceptType(ContentType.JSON.getMimeType());
    }

    String currentURI = context.request().absoluteURI();
    if (context.request().headers().contains("X-Original-URI")) {

      //test if X-Original-URI contains a valid absolute URI and then assign it to currentURI
      currentURI = context.request().headers().get("X-Original-URI");
      if (!currentURI.startsWith("http")) {
        currentURI = context.request().absoluteURI();
        logger.warn("X-Original-URI is not a valid absolute URI. Using current URI: {}",
            currentURI);
      }
    }

    if (catalogueId.isBlank()) {
      datasetsService.listDatasets(contentNegotiation.getAcceptType(), valueType, limit, offset,
              hydra, currentURI, usePagedCollection)
          .onSuccess(contentNegotiation::headOrGetResponse)
          .onFailure(cause -> {
            logger.error("Failed to list datasets", cause);
            HubRepo.failureResponse(context, cause);
          });
    } else {
      datasetsService.listCatalogueDatasets(contentNegotiation.getAcceptType(), valueType,
              catalogueId, limit, offset)
          .onSuccess(contentNegotiation::headOrGetResponse)
          .onFailure(cause -> HubRepo.failureResponse(context, cause));
    }
  }

  public void handleGetDataset(RoutingContext context) {
    ContentNegotiation contentNegotiation = new ContentNegotiation(context, PATH_PARAM_DATASET,
        ContentType.JSON_LD);
    String id = contentNegotiation.getId();
    String acceptType = contentNegotiation.getAcceptType();

    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    if (parameters.queryParameter(QUERY_PARAM_CATALOGUE) == null) {
      datasetsService.getDataset(id, acceptType)
          .onSuccess(contentNegotiation::headOrGetResponse)
          .onFailure(cause -> HubRepo.failureResponse(context, cause));
    } else {
      datasetsService.getDatasetOrigin(id,
              parameters.queryParameter(QUERY_PARAM_CATALOGUE).getString(), acceptType)
          .onSuccess(contentNegotiation::headOrGetResponse)
          .onFailure(cause -> HubRepo.failureResponse(context, cause));
    }
  }

  public void handleGetDatasetOrigin(RoutingContext context) {
    ContentNegotiation contentNegotiation = new ContentNegotiation(context, PATH_PARAM_CATALOGUE,
        ContentType.JSON_LD);
    String catalogueId = contentNegotiation.getId();

    String acceptType = contentNegotiation.getAcceptType();

    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    String originalId = parameters.queryParameter(QUERY_PARAM_DATASET).getString();
    datasetsService.getDatasetOrigin(originalId, catalogueId, acceptType)
        .onSuccess(contentNegotiation::headOrGetResponse)
        .onFailure(cause -> HubRepo.failureResponse(context, cause));
  }

  public void handleGetRecord(RoutingContext context) {
    ContentNegotiation contentNegotiation = new ContentNegotiation(context, PATH_PARAM_DATASET,
        ContentType.JSON_LD);
    String datasetId = contentNegotiation.getId();
    String acceptType = contentNegotiation.getAcceptType();

    datasetsService.getRecord(datasetId, acceptType)
        .onSuccess(contentNegotiation::headOrGetResponse)
        .onFailure(cause -> HubRepo.failureResponse(context, cause));
  }

  public void handlePutDataset(RoutingContext context) {
    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    String id = parameters.pathParameter(PATH_PARAM_DATASET).getString();

    RequestParameter body = parameters.body();
    if (body.isEmpty()) {
      context.fail(400, new ServiceException(400, "Body required"));
      return;
    }
    String content = body.toString();

    String contentType;
    try {
      contentType = ContentType.valueOfMimeType(context.parsedHeaders().contentType().value())
          .getMimeType();
    } catch (Exception e) {
      HubRepo.failureResponse(context, new ServiceException(415,
          "Wrong Content-Type. Please use one of the Content-Types described in the API description"));
      return;
    }

    if (parameters.queryParameter(QUERY_PARAM_CATALOGUE) == null) {
      datasetsService.putDataset(id, content, contentType)
          .onSuccess(status -> {
            switch (status.getString(RESULT_STATUS_KEY)) {
              case RESULT_STATUS_UPDATED -> context.response()
                  .setStatusCode(204)
                  .putHeader(HttpHeaders.LOCATION,
                      status.getString(HttpHeaders.LOCATION.toString(), ""))
                  .end();
              default -> HubRepo.failureResponse(context,
                  new ServiceException(500, "Unexpected status code"));
            }
          })
          .onFailure(cause -> HubRepo.failureResponse(context, cause));
    } else {
      String catalogueId = parameters.queryParameter(QUERY_PARAM_CATALOGUE).getString();
      datasetsService.putDatasetOrigin(id, content, contentType, catalogueId, false)
          .onSuccess(status -> {
            switch (status.getString(RESULT_STATUS_KEY)) {
              case RESULT_STATUS_CREATED -> context.response().setStatusCode(201)
                  .putHeader(HttpHeaders.LOCATION,
                      status.getString(HttpHeaders.LOCATION.toString(), "")).end();
              case RESULT_STATUS_UPDATED -> context.response().setStatusCode(204)
                  .putHeader(HttpHeaders.LOCATION,
                      status.getString(HttpHeaders.LOCATION.toString(), "")).end();
              default ->
                // should not happen, succeeded path should only respond with 2xx codes
                  context.response().setStatusCode(400).end();
            }
          })
          .onFailure(cause -> HubRepo.failureResponse(context, cause));
    }
  }

  public void handlePutDatasetOrigin(RoutingContext context) {
    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

    String catalogueId = parameters.pathParameter(PATH_PARAM_CATALOGUE).getString();
    String originId = parameters.queryParameter(QUERY_PARAM_DATASET).getString();

    RequestParameter body = parameters.body();
    if (body.isEmpty()) {
      context.fail(400, new ServiceException(400, "Body required"));
      return;
    }
    String content = body.toString();

    String contentType = context.parsedHeaders().contentType().value();

    // validate the dataset using the ITB validator
    if (datasetIsInvalid(context, catalogueId, contentType)) {
      return;
    }

    datasetsService.putDatasetOriginHealthData(originId, content, contentType, catalogueId, false, true)
        .onSuccess(status -> {
          switch (status.getString(RESULT_STATUS_KEY)) {
            case RESULT_STATUS_CREATED -> context.response().setStatusCode(201)
                .putHeader(HttpHeaders.LOCATION,
                    status.getString(HttpHeaders.LOCATION.toString(), "")).end();
            case RESULT_STATUS_UPDATED -> context.response().setStatusCode(204)
                .putHeader(HttpHeaders.LOCATION,
                    status.getString(HttpHeaders.LOCATION.toString(), "")).end();
            default ->
              // should not happen, succeeded path should only respond with 2xx codes
                context.response().setStatusCode(400).end();
          }
        })
        .onFailure(cause -> HubRepo.failureResponse(context, cause));
  }

  /**
   * @param context     the routing context
   * @param catalogueId the id of the catalogue for which the dataset will be created
   * @param contentType the dataset format i.e. text/turtle, application/rdf+xml etc.
   */
  public boolean datasetIsInvalid(RoutingContext context, String catalogueId, String contentType) {
    // Skip validation if the ITB validator endpoint is not configured (e.g. in tests)
    String itbEndpoint = System.getenv(Constants.ENV_PIVEAU_ITB_VALIDATOR_ENDPOINT);
    if (itbEndpoint == null || itbEndpoint.isBlank()) {
      return false;
    }
    try {
      String rdfContent = context.body().asString();
      // Validate the dataset based on dcat-ap and healthdcat-ap specifications
      // using the ITB validator API
      JsonObject itbReport = validateDatasetInItb(rdfContent, contentType);

      if (itbReport.getString(DATASET_ITB_UNEXPECTED_ERROR) != null) {
        logger.error(DATASET_ITB_UNEXPECTED_ERROR + ": {}",
            itbReport.getString(DATASET_ITB_UNEXPECTED_ERROR));
        JsonObject errorMessage = new JsonObject().put("error", DATASET_ITB_UNEXPECTED_ERROR)
            .put("message", itbReport.getString(DATASET_ITB_UNEXPECTED_ERROR));
        HubRepo.failureResponse(context, new ServiceException(400, errorMessage.encodePrettily()));
        return true;
      }

      JsonObject counters = itbReport.getJsonObject("counters", new JsonObject());
      boolean hasErrors = counters.getInteger("nrOfErrors", 0) > 0;

      if (Objects.nonNull(itbReport.getString(DATASET_ITB_VALIDATION_ERROR_0102))) {
        // ITB validation error, ERR_HD_0102 Unexpected characters
        String message = itbReport.getString(DATASET_ITB_VALIDATION_ERROR_0102);
        logger.error("{} when uploading dataset for catalogue with id '{}': {}",
            DATASET_ITB_VALIDATION_ERROR_0102, catalogueId, message);
        JsonObject errorMessage = new JsonObject()
            .put("error", DATASET_ITB_VALIDATION_ERROR_0102)
            .put("message", message);

        HubRepo.failureResponse(context, new ServiceException(400, errorMessage.encodePrettily()));
        return true;
      } else if (hasErrors) {
        // ITB validation error, ERR_HD_0101 Invalid or missing fields
        logger.error(
            DATASET_ITB_VALIDATION_ERROR_0101
                + " when uploading dataset for catalogue with id '{}': {}",
            catalogueId,
            itbReport.encodePrettily());
        JsonObject errorMessage = new JsonObject().put("error", DATASET_ITB_VALIDATION_ERROR_0101)
            .put("message", itbReport);

        HubRepo.failureResponse(context, new ServiceException(400, errorMessage.encodePrettily()));
        return true;
      }
    } catch (ExecutionException | InterruptedException e) {
      logger.error(DATASET_ITB_UNEXPECTED_ERROR, e);
      JsonObject errorMessage = new JsonObject().put("error", DATASET_ITB_UNEXPECTED_ERROR)
          .put("message",
              new JsonObject().put("exception", e.getMessage())
                  .put("stackTrace", e.getStackTrace()));
      HubRepo.failureResponse(context, new ServiceException(400, errorMessage.encodePrettily()));
      return true;
    }

    return false;
  }

  public void handlePutDatasetLegacy(RoutingContext context) {
    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

    String catalogueId = parameters.queryParameter(QUERY_PARAM_CATALOGUE).getString();
    String originId = parameters.queryParameter("id").getString();

    RequestParameter body = parameters.body();
    if (body.isEmpty()) {
      context.fail(400, new ServiceException(400, "Body required"));
      return;
    }
    String content = body.toString();

    String contentType = context.parsedHeaders().contentType().value();

    datasetsService.putDatasetOrigin(originId, content, contentType, catalogueId, false)
        .onSuccess(status -> {
          switch (status.getString(RESULT_STATUS_KEY)) {
            case RESULT_STATUS_CREATED -> context.response().setStatusCode(201)
                .putHeader(HttpHeaders.LOCATION,
                    status.getString(HttpHeaders.LOCATION.toString(), "")).end();
            case RESULT_STATUS_UPDATED -> context.response().setStatusCode(204)
                .putHeader(HttpHeaders.LOCATION,
                    status.getString(HttpHeaders.LOCATION.toString(), "")).end();
            default ->
              // should not happen, succeeded path should only respond with 2xx codes
                context.response().setStatusCode(400).end();
          }
        })
        .onFailure(cause -> HubRepo.failureResponse(context, cause));
  }

  public void handleDeleteDatasetOrigin(RoutingContext context) {
    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    String catalogueId = parameters.pathParameter(PATH_PARAM_CATALOGUE).getString();
    String originalId = parameters.queryParameter(QUERY_PARAM_DATASET).getString();

    datasetsService.deleteDatasetOrigin(originalId, catalogueId)
        .onSuccess(v -> context.response().setStatusCode(204).end())
        .onFailure(cause -> HubRepo.failureResponse(context, cause));
  }

  public void handleDeleteDataset(RoutingContext context) {
    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    String id = parameters.pathParameter(PATH_PARAM_DATASET).getString();

    if (parameters.queryParameter(QUERY_PARAM_CATALOGUE) == null) {
      datasetsService.deleteDataset(id)
          .onSuccess(v -> context.response().setStatusCode(204).end())
          .onFailure(cause -> HubRepo.failureResponse(context, cause));
    } else {
      String catalogueId = parameters.queryParameter(QUERY_PARAM_CATALOGUE).getString();
      datasetsService.deleteDatasetOrigin(id, catalogueId)
          .onSuccess(v -> context.response().setStatusCode(204).end())
          .onFailure(cause -> HubRepo.failureResponse(context, cause));
    }
  }

  public void handleDeleteDatasetLegacy(RoutingContext context) {
    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

    String id = parameters.queryParameter("id").getString();
    String catalogueId = parameters.queryParameter(QUERY_PARAM_CATALOGUE).getString();

    datasetsService.deleteDatasetOrigin(id, catalogueId)
        .onSuccess(v -> context.response().setStatusCode(204).end())
        .onFailure(cause -> HubRepo.failureResponse(context, cause));
  }

  public void handleListCatalogueDatasets(RoutingContext context) {
    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

    Integer offset = parameters.queryParameter(OFFSET).getInteger();
    Integer limit = parameters.queryParameter(LIMIT).getInteger();

    String valueType = parameters.queryParameter("valueType").getString();

    ContentNegotiation contentNegotiation = new ContentNegotiation(context, PATH_PARAM_CATALOGUE,
        ContentType.JSON_LD);

    if (!valueType.equals("metadata")) {
      contentNegotiation.setAcceptType(ContentType.JSON.getMimeType());
    }

    datasetsService.listCatalogueDatasets(contentNegotiation.getAcceptType(), valueType,
            contentNegotiation.getId(), limit, offset)
        .onSuccess(contentNegotiation::headOrGetResponse)
        .onFailure(cause -> HubRepo.failureResponse(context, cause));
  }

  public void handlePostCatalogueDataset(RoutingContext context) {
    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    String catalogueId = parameters.pathParameter(PATH_PARAM_CATALOGUE).getString();

    String contentType;
    try {
      contentType = ContentType.valueOfMimeType(context.parsedHeaders().contentType().value())
          .getMimeType();
    } catch (Exception e) {
      HubRepo.failureResponse(context, new ServiceException(415,
          "Wrong Content-Type. Please use one of the Content-Types described in the API description"));
      return;
    }

    if (context.body().asString() == null) {
      HubRepo.failureResponse(context, new ServiceException(400, "Body required"));
      return;
    }

    // validate the dataset using the ITB validator
    if (datasetIsInvalid(context, catalogueId, contentType)) {
      return;
    }

    datasetsService.postDataset(context.body().asString(), contentType, catalogueId, false)
        .onSuccess(status -> {
          switch (status.getString(RESULT_STATUS_KEY)) {
            case RESULT_STATUS_CREATED -> context.response()
                .setStatusCode(201)
                .putHeader(HttpHeaders.LOCATION,
                    status.getString(HttpHeaders.LOCATION.toString(), ""))
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(new JsonObject().put("id", status.getString("id"))
                    .put("status", status.getString("status")).encode());
            case RESULT_STATUS_UPDATED -> context.response()
                .setStatusCode(200)
                .putHeader(HttpHeaders.LOCATION,
                    status.getString(HttpHeaders.LOCATION.toString(), ""))
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(new JsonObject().put("id", status.getString("id"))
                    .put("status", status.getString("status")).encode());
            default -> HubRepo.failureResponse(context,
                new ServiceException(500, "Unexpected status code", status));
          }
        })
        .onFailure(cause -> HubRepo.failureResponse(context, cause));
  }

  public void handlePostCatalogueDatasetLegacy(RoutingContext context) {
    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);

    String catalogueId = parameters.queryParameter(QUERY_PARAM_CATALOGUE).getString();

    RequestParameter body = parameters.body();
    if (body.isEmpty()) {
      context.fail(400, new ServiceException(400, "Body required"));
      return;
    }
    String content = body.getBuffer().toString();

    String contentType = context.parsedHeaders().contentType().value();

    // Handle Post Dataset
    datasetsService.postDataset(content, contentType, catalogueId, false)
        .onSuccess(status -> {
          if (status.getString(RESULT_STATUS_KEY).equals(RESULT_STATUS_CREATED)) {
            context.response().setStatusCode(201)
                .putHeader(HttpHeaders.LOCATION,
                    status.getString(HttpHeaders.LOCATION.toString(), ""))
                .end();
          } else { // Should not happen
            HubRepo.failureResponse(context,
                new ServiceException(500, "Unexpected status code", status));
          }
        })
        .onFailure(cause -> HubRepo.failureResponse(context, cause));
  }

  public void handleIndexDataset(RoutingContext context) {
    RequestParameters parameters = context.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    String datasetId = parameters.pathParameter(PATH_PARAM_DATASET).getString();
    String catalogueId = context.get("catalogueId");
    datasetsService.indexDataset(datasetId, catalogueId)
        .onSuccess(result -> context.response()
            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setStatusCode(200)
            .end(result.encodePrettily()))
        .onFailure(cause -> HubRepo.failureResponse(context, cause));
  }

  /**
   * Handles HTTP requests to reindex all datasets from Virtuoso to OpenSearch.
   * <p>
   * This endpoint triggers a comprehensive reindexing operation that retrieves all datasets
   * from the Virtuoso triple store and sends them to the OpenSearch indexing service.
   * The operation is performed asynchronously to avoid blocking the event loop.
   * </p>
   * <p>
   * <b>Security:</b> This endpoint requires authentication and DATASET_UPDATE permission.
   * It is reserved for internal administrative use only.
   * </p>
   * <p>
   * <b>HTTP Method:</b> POST<br>
   * <b>Path:</b> /datasets/index<br>
   * <b>Content-Type:</b> application/json
   * </p>
   * <p>
   * <b>Success Response (200):</b>
   * <pre>
   * {
   *   "message": "Reindexing completed",
   *   "totalDatasets": 150,
   *   "successCount": 148,
   *   "failureCount": 2
   * }
   * </pre>
   * </p>
   * <p>
   * <b>Error Responses:</b>
   * <ul>
   *   <li>401 Unauthorized - Missing or invalid authentication</li>
   *   <li>403 Forbidden - Insufficient permissions</li>
   *   <li>500 Internal Server Error - Indexing service unavailable or fatal error</li>
   * </ul>
   * </p>
   * <p>
   * <b>Implementation notes:</b>
   * <ul>
   *   <li>Non-blocking: Uses Vert.x async patterns with Future API</li>
   *   <li>Resilient: Individual dataset failures don't stop the entire operation</li>
   *   <li>Long-running: For large datasets, this operation may take considerable time</li>
   *   <li>Idempotent: Safe to retry if the operation fails</li>
   * </ul>
   * </p>
   *
   * @param context The Vert.x routing context containing the HTTP request and response
   *                This context is managed by Vert.x and is thread-safe within the event loop
   */
  public void handleReindexAllDatasets(RoutingContext context) {
    datasetsService.reindexAllDatasets()
        .onSuccess(result -> context.response()
            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setStatusCode(200)
            .end(result.encodePrettily()))
        .onFailure(cause -> HubRepo.failureResponse(context, cause));
  }

  public JsonObject validateDatasetInItb(String rdf, String contentType)
      throws ExecutionException, InterruptedException {
    String validationType = getValidationType(rdf, contentType);
    JsonObject requestBody = createDatasetValidationRequestBody(rdf, contentType, validationType);
    CompletableFuture<JsonObject> future = new CompletableFuture<>();
    WebClient itbValidatorWebClient = WebClient.create(Vertx.vertx());
    String itbValidatorApiEndpoint = System.getenv(Constants.ENV_PIVEAU_ITB_VALIDATOR_ENDPOINT);

    // Set a default timeout of 10 seconds if the environment variable is not set
    int timeoutInMillis =
        Optional.ofNullable(System.getenv(Constants.ENV_PIVEAU_HEALTHDATA_ENDPOINTS_TIMEOUT))
            .map(s -> {
              try {
                return Integer.parseInt(s);
              } catch (NumberFormatException e) {
                return 10000;
              }
            })
            .orElse(10000);

    itbValidatorWebClient.postAbs(itbValidatorApiEndpoint)
        .timeout(
            timeoutInMillis) // Ensure request times out if service is unresponsive
        .sendJsonObject(requestBody, ar -> {
          if (ar.failed()) {
            Throwable cause = ar.cause();
            String errorMessage = getItbFailedResponseMessage(cause);
            // Return error response if the validation service is unreachable
            future.complete(new JsonObject()
                .put(DATASET_ITB_UNEXPECTED_ERROR, errorMessage));
            return;
          }

          var response = ar.result();

          // catch the case when the rdf has structural errors where ITB returns a 500 status code
          // and a response body with a message property starting with "An error occurred while reading the provided content"
          if (response.statusCode() == 500 && Objects.nonNull(response.bodyAsJsonObject())
              && response.bodyAsJsonObject().getString("message", "").trim()
              .startsWith("An error occurred while reading the provided content")) {

            String errorMessage = response.bodyAsJsonObject().getString("message");
            future.complete(new JsonObject()
                .put(DATASET_ITB_VALIDATION_ERROR_0102, errorMessage));
            return;
          }

          if (response.statusCode() != 200) {
            String errorMessage = response.statusCode() + ": " + response.statusMessage() + ", " +
                (response.bodyAsString() != null ? response.bodyAsString() : "(No response body)");
            // return error response if the validation service returns an error
            future.complete(new JsonObject()
                .put(DATASET_ITB_UNEXPECTED_ERROR, errorMessage));
            return;
          }

          future.complete(response.bodyAsJsonObject());
        });

    // Block until the future is completed
    return future.get(); // Blocking call
  }

  /**
   * Returns an error message based on the type of error that occurred while communicating with the
   * ITB validation service.
   *
   * @param cause The cause of the error
   * @return The error message
   */
  @NotNull
  private static String getItbFailedResponseMessage(Throwable cause) {
    String errorMessage;

    // Determine the error type
    if (cause instanceof java.util.concurrent.TimeoutException) {
      errorMessage = "Validation service timed out: " + cause.getMessage();
    } else if (cause instanceof java.net.ConnectException
        || cause instanceof java.net.UnknownHostException) {
      errorMessage = "Validation service unreachable: " + cause.getMessage();
    } else if (cause != null) {
      errorMessage =
          "Unexpected error while communicating with validation service: " + cause.getMessage();
    } else {
      errorMessage = "Unknown error while communicating with validation service";
    }
    return errorMessage;
  }

  /**
   * Creates the JSON body for the validation request.
   *
   * @param rdfContent The RDF content to be validated.
   * @param contentSyntax The content type of the RDF, e.g., "text/turtle".
   * @param validationType The dataset schema to be used, ("public", "restricted", "non-public")
   * @return The JSON body as a JsonObject.
   */
  private JsonObject createDatasetValidationRequestBody(
      String rdfContent,
      String contentSyntax,
      String validationType) {
    String rdfContentBase64 = Base64.getEncoder().encodeToString(rdfContent.getBytes());
    return new JsonObject()
        .put("contentToValidate", rdfContentBase64)
        .put("embeddingMethod", "BASE64")
        .put("contentSyntax", contentSyntax)
        .put("validationType", validationType)
        .put("reportSyntax", "application/json");
  }

  /**
   * Extracts the validation type from the RDF based on the dct:accessRights property.
   *
   * @param rdfContent  The RDF content as a string.
   * @param contentType The content type of the RDF, e.g., "text/turtle".
   * @return The validation type as a string ("public", "restricted", "non-public"). Defaults to
   * "public".
   */
  private String getValidationType(String rdfContent, String contentType) {
    Lang lang = switch (contentType) {
      case "application/ld+json" -> Lang.JSONLD;
      case "application/n-quads" -> Lang.NQUADS;
      case "application/n-triples" -> Lang.NTRIPLES;
      case "application/rdf+xml" -> Lang.RDFXML;
      case "application/trig" -> Lang.TRIG;
      case "text/turtle" -> Lang.TURTLE;
      default -> null;
    };
    if (lang == null) {
      return PUBLIC;
    }

    Model model = ModelFactory.createDefaultModel();
    try {
      RDFParser.create().source(new StringReader(rdfContent)).lang(lang).parse(model);
    } catch (RiotException e) {
      logger.warn("Error parsing RDF content", e);
      // This exception means there are structural errors in the RDF, so we return public to proceed
      // with the validation and let ITB handle it and return a more detailed error message
      return PUBLIC;
    }

    // Look for dct:accessRights in a dataset
    StmtIterator dsTypes = model.listStatements(null, RDF.type, DCAT.Dataset);
    while (dsTypes.hasNext()) {
      Resource dataset = dsTypes.nextStatement().getSubject();
      Statement stmt = dataset.getProperty(DCTerms.accessRights);
      if (stmt == null) {
        continue;
      }

      RDFNode object = stmt.getObject();
      String uri = object.isResource() ? object.asResource().getURI() : object.toString();

      // Map valid values to their validation type equivalents
      return switch (uri) {
        case "http://publications.europa.eu/resource/authority/access-right/RESTRICTED" ->
            "restricted";
        case "http://publications.europa.eu/resource/authority/access-right/NON_PUBLIC" ->
            "non-public";
        default -> PUBLIC;
      };
    }

    return PUBLIC;
  }
}
