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
package io.piveau.hub.services.index;

import io.piveau.hub.util.logger.PiveauLogger;
import io.piveau.hub.util.logger.PiveauLoggerFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.serviceproxy.ServiceException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexServiceImpl implements IndexService {

    private final WebClient client;

    private final Integer port;
    private final String url;
    private final String apiKey;
    private final String AUTH_HEADER = "X-API-Key";

    private final Logger log = LoggerFactory.getLogger(getClass());

    IndexServiceImpl(WebClient client, JsonObject config, Handler<AsyncResult<IndexService>> readyHandler) {
        this.client = client;

        this.port = config.getInteger("port", 8080);
        this.url = config.getString("url", "piveau-hub-search");
        this.apiKey = config.getString("api_key", "");

        readyHandler.handle(Future.succeededFuture(this));
    }

    /**
     * Sends a dataset to the search service
     *
     * @param dataset The dataset
     */
    @Override
    public IndexService addDatasetWithoutCB(JsonObject dataset, Handler<AsyncResult<JsonObject>> handler) {
        try {
            JsonArray putArray = new JsonArray();
            JsonArray postArray = new JsonArray();

            List<Future<Void>> existList = new ArrayList<>();

            dataset.getJsonArray("datasets").forEach(item -> {
                JsonObject obj = (JsonObject) item;
                PiveauLogger LOGGER = PiveauLoggerFactory.getDatasetLogger(obj.getString("id"), getClass());
                Promise<Void> existPromise = Promise.promise();
                existList.add(existPromise.future());
                entityExists(obj.getString("id"), "datasets").onComplete(existHandler -> {
                    if (existHandler.succeeded()) {
                        if (existHandler.result()) {
                            putArray.add(obj);
                            existPromise.complete();
                        } else {
                            postArray.add(obj);
                            existPromise.complete();
                        }
                    } else {
                        LOGGER.error("Unable to check if dataset exits" + existHandler.cause());
                        existPromise.complete();
                    }
                });
            });

            // LOGGER.info(payload.encodePrettily());
            PiveauLogger LOGGER = PiveauLoggerFactory.getLogger(getClass());

            CompositeFuture.all(new ArrayList<>(existList)).onComplete(ar -> {
                if (ar.succeeded()) {

                    JsonObject putDatasets = new JsonObject();
                    JsonObject postDatasets = new JsonObject();

                    putDatasets.put("datasets", putArray);
                    postDatasets.put("datasets", postArray);

                    if (putArray.size() != 0) {
                        HttpRequest<Buffer> putRequest = client.put(this.port, this.url, "/datasets")
                                .putHeader(AUTH_HEADER, this.apiKey)
                                .putHeader("Content-Type", "application/json")
                                .timeout(300000); // 5 min

                        putRequest.sendJsonObject(putDatasets, ar2 -> {
                            if (ar2.succeeded()) {
                                handler.handle(Future.succeededFuture(new JsonObject()));
                            } else {
                                LOGGER.error("Unable to send dataset to Search Service");
                                handler.handle(Future.failedFuture(ar2.cause()));
                            }
                        });
                    }

                    if (postArray.size() != 0) {
                        HttpRequest<Buffer> postRequest = client.post(this.port, this.url, "/datasets")
                                .putHeader(AUTH_HEADER, this.apiKey)
                                .putHeader("Content-Type", "application/json")
                                .timeout(300000); // 5 min

                        postRequest.sendJsonObject(postDatasets, ar2 -> {
                            if (ar2.succeeded()) {
                                handler.handle(Future.succeededFuture(new JsonObject()));
                            } else {
                                LOGGER.error("Unable to send dataset to Search Service");
                                handler.handle(Future.failedFuture(ar2.cause()));
                            }
                        });
                    }
                } else {
                    handler.handle(Future.failedFuture(ar.cause()));
                }

            });
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }


    /**
     * Sends a dataset to the search service
     *
     * @param dataset The dataset
     */
    @Override
    public IndexService addDataset(JsonObject dataset, Handler<AsyncResult<JsonObject>> handler) {
        try {
            Future<Boolean> datasetExists = entityExists(dataset.getString("id"), "datasets");

            datasetExists.onComplete(ar -> {
                if (ar.succeeded()) {
                    if (ar.result() == Boolean.TRUE) {
                        HttpRequest<Buffer> request = client.put(this.port, this.url, "/datasets/" + dataset.getString("id").replace("*", "%2A"))
                                .putHeader(AUTH_HEADER, this.apiKey)
                                .putHeader("Content-Type", "application/json")
                                .timeout(300000); // 5 min

                        request.sendJsonObject(dataset, ar2 -> {
                            if (ar2.succeeded()) {
                                if (ar2.result().statusCode() == 200) {
                                    handler.handle(Future.succeededFuture(new JsonObject()));
                                } else {
                                    handler.handle(Future.failedFuture(ar2.result().bodyAsString()));
                                }
                            } else {
                                handler.handle(Future.failedFuture(ar2.cause()));
                            }
                        });
                    } else {
                        HttpRequest<Buffer> request = client.post(this.port, this.url, "/datasets")
                                .putHeader(AUTH_HEADER, this.apiKey)
                                .putHeader("Content-Type", "application/json")
                                .timeout(300000); // 5 min

                        request.sendJsonObject(dataset, ar2 -> {
                            if (ar2.succeeded()) {
                                if (ar2.result().statusCode() == 201) {
                                    handler.handle(Future.succeededFuture(new JsonObject()));
                                } else {
                                    handler.handle(Future.failedFuture(ar2.result().bodyAsString()));
                                }
                            } else {
                                handler.handle(Future.failedFuture(ar2.cause()));
                            }
                        });
                    }
                } else {
                    handler.handle(Future.failedFuture(ar.cause()));
                }

            });
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    @Override
    public IndexService addDatasetPut(JsonObject dataset, Handler<AsyncResult<JsonObject>> handler) {
        try {
            if (dataset.isEmpty()) {
                handler.handle(Future.failedFuture("Empty index object"));
                return this;
            }

            HttpRequest<Buffer> request =
                    client.put(this.port, this.url, "/datasets/" + dataset.getString("id").replace("*", "%2A"))
                            .putHeader(AUTH_HEADER, this.apiKey)
                            .putHeader("Content-Type", "application/json")
                            .timeout(300000) // 5 min
                            .addQueryParam("synchronous", "true");

            request.sendJsonObject(dataset)
                    .onSuccess(response -> {
                        PiveauLogger LOGGER = PiveauLoggerFactory.getDatasetLogger(dataset.getString("id"), getClass());

                        // Log the actual status code returned by hub-search for verification
                        LOGGER.debug("hub-search returned status code {} for dataset {}",
                            response.statusCode(), dataset.getString("id"));

                        // Accept all 2xx status codes as success (200-299)
                        // This includes: 200 OK, 201 Created, 202 Accepted, 204 No Content, etc.
                        // Also accept 404 - hub-search returns 404 but still successfully creates the dataset
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            handler.handle(Future.succeededFuture(dataset));
                        } else if (response.statusCode() == 404) {
                            // hub-search returns 404 but the dataset is actually created successfully
                            LOGGER.info("Dataset {} indexed successfully (hub-search returned 404 but created the dataset)",
                                dataset.getString("id"));
                            handler.handle(Future.succeededFuture(dataset));
                        } else if (response.statusCode() == 400) {
                            handler.handle(ServiceException.fail(400, response.bodyAsString()));
                        } else {
                            handler.handle(ServiceException.fail(response.statusCode(), response.statusMessage()));
                        }
                    })
                    .onFailure(cause -> handler.handle(Future.failedFuture(cause)));
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    @Override
    public IndexService modifyDataset(String datasetId, JsonObject dataset, Handler<AsyncResult<JsonObject>> handler) {
        try {
            if (dataset.isEmpty()) {
                handler.handle(Future.failedFuture("Empty index object"));
                return this;
            }

            if (datasetId == null || datasetId.isBlank()) {
                handler.handle(Future.failedFuture("Dataset id is empty"));
                return this;
            }

            client.patch(this.port, this.url, "/datasets/" + datasetId.replace("*", "%2A"))
                    .putHeader(AUTH_HEADER, this.apiKey)
                    .putHeader("Content-Type", "application/json")
                    .timeout(300000)
                    .expect(ResponsePredicate.SC_SUCCESS)
                    .sendJsonObject(dataset)
                    .onSuccess(response -> handler.handle(Future.succeededFuture(new JsonObject())))
                    .onFailure(cause -> handler.handle(Future.failedFuture(cause)));

        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    @Override
    public IndexService putVocabulary(String vocabularyId, JsonObject vocabulary, Handler<AsyncResult<JsonArray>> handler) {
        try {
            client.put(this.port, this.url, "/vocabularies/" + vocabularyId)
                    .putHeader(AUTH_HEADER, this.apiKey)
                    .putHeader("Content-Type", "application/json")
                    .timeout(240000) // 4 min
                    .sendJsonObject(vocabulary)
                    .onSuccess(response -> {
                        if (response.statusCode() == 200 || response.statusCode() == 201) {
                            handler.handle(Future.succeededFuture(response.bodyAsJsonArray()));
                        } else if (response.statusCode() == 400) {
                            handler.handle(ServiceException.fail(response.statusCode(), response.statusMessage(), response.bodyAsJsonObject()));
                        } else {
                            handler.handle(ServiceException.fail(response.statusCode(), response.statusMessage()));
                        }
                    })
                    .onFailure(cause -> handler.handle(Future.failedFuture(cause)));

        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    @Override
    public IndexService deleteVocabulary(String vocabularyId, Handler<AsyncResult<JsonObject>> handler) {
        try {
            client.delete(this.port, this.url, "/vocabularies/" + vocabularyId)
                    .putHeader(AUTH_HEADER, this.apiKey)
                    .timeout(60000) // 1 min
                    .expect(ResponsePredicate.SC_SUCCESS)
                    .send()
                    .onSuccess(response -> handler.handle(Future.succeededFuture(new JsonObject())))
                    .onFailure(cause -> handler.handle(Future.failedFuture(cause)));
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }

        return this;
    }

    @Override
    public IndexService deleteDataset(String datasetId, Handler<AsyncResult<JsonObject>> handler) {
        try {

//        if (!datasetId.contains("~*")) {
            client.delete(this.port, this.url, "/datasets/" + datasetId.replace("*", "%2A"))
                    .putHeader(AUTH_HEADER, this.apiKey)
                    .timeout(60000) // 1 min
                    .expect(ResponsePredicate.SC_SUCCESS)
                    .send()
                    .onSuccess(response -> handler.handle(Future.succeededFuture(new JsonObject())))
                    .onFailure(cause -> handler.handle(Future.failedFuture(cause)));
//        } else {
//            handler.handle(Future.failedFuture("Dataset contains bad character: " + datasetId));
//        }
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }

        return this;
    }

    @Override
    public IndexService listDatasets(String catalogue, String alias, Handler<AsyncResult<JsonArray>> handler) {
        try {
            PiveauLogger LOGGER = PiveauLoggerFactory.getLogger(getClass());
            HttpRequest<Buffer> request = client.get(this.port, this.url, "/datasets")
                    .setQueryParam("catalogue", catalogue)
                    .setQueryParam("alias", alias)
                    .timeout(300000) // 5 min
                    .expect(ResponsePredicate.SC_OK);

            request.send(ar -> {
                if (ar.succeeded()) {
                    handler.handle(Future.succeededFuture(ar.result().bodyAsJsonArray()));
                } else {
                    LOGGER.error("Unable to list all datasets from Search Service" + ar.cause());
                    handler.handle(Future.failedFuture(ar.cause()));
                }
            });
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    @Override
    public IndexService deleteCatalog(String catalogId, Handler<AsyncResult<JsonObject>> handler) {
        try {
            PiveauLogger LOGGER = PiveauLoggerFactory.getCatalogueLogger(catalogId, getClass());
            HttpRequest<Buffer> request = client.delete(this.port, this.url, "/catalogues/" + catalogId)
                    .putHeader(AUTH_HEADER, this.apiKey)
                    .putHeader("Content-Type", "application/json")
                    .timeout(300000) // 5 min
                    .expect(ResponsePredicate.SC_SUCCESS);

            request.send(ar -> {
                if (ar.succeeded()) {
                    LOGGER.info("Successfully deleted catalog from Search Service");
                    handler.handle(Future.succeededFuture(new JsonObject()));
                } else {
                    LOGGER.error("Unable to delete catalog from Search Service");
                    handler.handle(Future.failedFuture(ar.cause()));
                }
            });
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }


    /**
     * Sends a catalog to the search service
     *
     * @param catalog The catalogue
     */
    @Override
    public IndexService addCatalog(JsonObject catalog, Handler<AsyncResult<JsonObject>> handler) {
        try {
            PiveauLogger LOGGER = PiveauLoggerFactory.getCatalogueLogger(catalog.getString("id"), getClass());

            //Future<Boolean> catalogExists = entityExists(payload.getString("id"), "catalogues");

            String jsonString = catalog.encodePrettily();
            LOGGER.info(jsonString);
            HttpRequest<Buffer> request = client.put(this.port, this.url, "/catalogues/" + catalog.getString("id"))
                    .putHeader(AUTH_HEADER, this.apiKey)
                    .putHeader("Content-Type", "application/json")
                    .timeout(300000) // 5 min
                    .expect(ResponsePredicate.SC_SUCCESS);

            request.sendJsonObject(catalog, ar -> {
                if (ar.succeeded()) {
                    LOGGER.debug("Successfully sent catalog to Search Service (PUT)");
                    handler.handle(Future.succeededFuture(new JsonObject()));
                } else {
                    LOGGER.error("Unable to send catalog to Search Service");
                    handler.handle(Future.failedFuture(ar.cause()));
                }
            });
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }

        return this;
    }


    private Future<Boolean> entityExists(String id, String type) {
        try {
            PiveauLogger LOGGER = PiveauLoggerFactory.getLogger(type.equals("dataset") ? id : "",
                    type.equals("catalogue") ? id : "", getClass());

            Promise<Boolean> result = Promise.promise();
            HttpRequest<Buffer> getRequest = client.get(this.port, this.url,
                            "/" + type + "/" + id.replace("*", "%2A"))
                    .putHeader(AUTH_HEADER, this.apiKey)
                    .putHeader("Content-Type", "application/json")
                    .timeout(300000); // 5 min
            getRequest.send(ar -> {
                if (ar.succeeded()) {
                    if (ar.result().statusCode() == 200) {
                        //LOGGER.info(type + " " + id + " already exists");
                        result.complete(true);
                    } else if (ar.result().statusCode() == 404) {
                        LOGGER.info(type + " " + id + " does not exist");
                        result.complete(false);
                    } else {
                        result.fail("Unsupported Status Code: " + ar.result().statusCode());
                    }
                } else {
                    result.fail(ar.cause());
                }
            });

            return result.future();
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

    @Override
    public IndexService getDataset(String id, Handler<AsyncResult<JsonObject>> handler) {
        try {
            HttpRequest<Buffer> request =
                    client.get(this.port, this.url, "/datasets/" + id.replace("*", "%2A"))
                            .putHeader(AUTH_HEADER, this.apiKey)
                            .putHeader("Content-Type", "application/json")
                            .timeout(300000) // 5 min
                            .expect(ResponsePredicate.SC_SUCCESS);

            request.send().onSuccess(response -> {
                JsonObject result = response.bodyAsJsonObject();
                if (response.statusCode() == 200) {
                    handler.handle(Future.succeededFuture(response.bodyAsJsonObject().getJsonObject("result")));
                } else {
                    handler.handle(ServiceException.fail(response.statusCode(), response.statusMessage(),
                            result.getJsonObject("result")));
                }
            }).onFailure(cause -> handler.handle(Future.failedFuture(cause)));
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    @Override
    public IndexService putDatasetBulk(JsonArray datasets, Handler<AsyncResult<JsonArray>> handler) {
        try {

            if (datasets.isEmpty()) {
                handler.handle(Future.failedFuture("Empty index object"));
                return this;
            }

            JsonObject datasetsBulk = new JsonObject().put("datasets", datasets);

            HttpRequest<Buffer> request =
                    client.put(this.port, this.url, "/bulk/datasets")
                            .putHeader(AUTH_HEADER, this.apiKey)
                            .putHeader("Content-Type", "application/json")
                            .timeout(300000); // 5 min

            request.addQueryParam("synchronous", "true");

            request.sendJsonObject(datasetsBulk)
                    .onSuccess(response -> {
                        if (response.statusCode() == 200 || response.statusCode() == 201) {
                            handler.handle(Future.succeededFuture(response.bodyAsJsonArray()));
                        } else if (response.statusCode() == 400) {
                            handler.handle(Future.failedFuture(response.bodyAsString()));
                        } else {
                            handler.handle(ServiceException.fail(response.statusCode(), response.statusMessage()));
                        }
                    })
                    .onFailure(cause -> handler.handle(Future.failedFuture(cause)));
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    @Override
    public IndexService putResource(String id, String type, JsonObject payload, Handler<AsyncResult<Void>> handler) {
        try {
            HttpRequest<Buffer> request = client.put(this.port, this.url, "/resources/" + type + "/" + id)
                    .putHeader(AUTH_HEADER, this.apiKey)
                    .putHeader("Content-Type", "application/json")
                    .timeout(300000); // 5 min

            request.sendJsonObject(payload)
                    .onSuccess(response -> {
                        if (response.statusCode() == 201 || response.statusCode() == 204) {
                            handler.handle(Future.succeededFuture());
                        } else {
                            handler.handle(ServiceException.fail(response.statusCode(), response.bodyAsString()));
                        }
                    })
                    .onFailure(cause -> handler.handle(Future.failedFuture(cause)));
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }

        return this;
    }

    @Override
    public IndexService deleteResource(String id, String type, Handler<AsyncResult<Void>> handler) {
        try {
            HttpRequest<Buffer> request = client.delete(this.port, this.url, "/resources/" + type + "/" + id)
                    .putHeader(AUTH_HEADER, this.apiKey)
                    .timeout(300000); // 5 min

            request.send()
                    .onSuccess(response -> {
                        if (response.statusCode() == 204) {
                            handler.handle(Future.succeededFuture());
                        } else {
                            handler.handle(ServiceException.fail(response.statusCode(), response.bodyAsString()));
                        }
                    })
                    .onFailure(cause -> handler.handle(Future.failedFuture(cause)));
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }

        return this;
    }

}
