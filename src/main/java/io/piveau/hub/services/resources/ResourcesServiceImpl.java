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

import io.piveau.dcatap.Prefixes;
import io.piveau.dcatap.TripleStore;
import io.piveau.hub.Constants;
import io.piveau.hub.Defaults;
import io.piveau.hub.services.index.IndexService;
import io.piveau.profile.ExtraResource;
import io.piveau.profile.Indexer;
import io.piveau.profile.PiveauProfile;
import io.piveau.rdf.Piveau;
import io.piveau.utils.PiveauContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourcesServiceImpl implements ResourcesService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final PiveauContext serviceContext;
    private final TripleStore tripleStore;

    private final boolean prependXmlDeclaration;

    private final IndexService indexService;

    private final String baseUri;

    private PiveauProfile profile;

    ResourcesServiceImpl(Vertx vertx, TripleStore tripleStore, JsonObject config, PiveauProfile profile,
                         Handler<AsyncResult<ResourcesService>> handler) {
        this.tripleStore = tripleStore;
        this.profile = profile;
        serviceContext = new PiveauContext("hub-repo", "ResourcesService");

        prependXmlDeclaration = config.getBoolean(Constants.ENV_PIVEAU_HUB_XML_DECLARATION, Defaults.XML_DECLARATION);

        JsonObject indexConfig = config.getJsonObject(Constants.ENV_PIVEAU_HUB_SEARCH_SERVICE, new JsonObject());
        if (indexConfig.getBoolean("enabled", Defaults.SEARCH_SERVICE_ENABLED)) {
            indexService = IndexService.createProxy(vertx, IndexService.SERVICE_ADDRESS, IndexService.DEFAULT_TIMEOUT);
        } else {
            indexService = null;
        }

        JsonObject schemaConfig = config.getJsonObject(Constants.ENV_PIVEAU_DCATAP_SCHEMA_CONFIG, new JsonObject());
        baseUri = schemaConfig.getString("baseUri", "https://piveau.io/") +
                schemaConfig.getString("resource", "set/resource/");

        handler.handle(Future.succeededFuture(this));
    }

    @Override
    public Future<JsonArray> listResourceTypes() {
        Promise<JsonArray> promise = Promise.promise();
        JsonArray jsonResult = new JsonArray();

        profile.extraResources.keySet().forEach(jsonResult::add);

        promise.complete(jsonResult);
        return promise.future();
    }

    @Override
    public Future<JsonArray> listResources(String type) {
        try {
            Promise<JsonArray> promise = Promise.promise();

            if (profile.extraResourceIsSet(type)) {
                String typeUri = profile.getExtraResource(type).getTypeURI();

                tripleStore.select("SELECT ?resource WHERE { ?resource a <" + typeUri + "> }")
                        .onSuccess(result -> {
                            JsonArray jsonResult = new JsonArray();
                            while (result.hasNext()) {
                                QuerySolution qs = result.next();
                                jsonResult.add(qs.getResource("resource").getURI());
                            }
                            promise.complete(jsonResult);
                        })
                        .onFailure(promise::fail);
            } else {
                promise.fail(new ServiceException(404, "Resource type unknown"));
            }

            return promise.future();
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

    @Override
    public Future<String> getResource(String id, String type, String acceptType) {
        try {
            Promise<String> promise = Promise.promise();

            String graphName = baseUri + type + "/" + id;

            tripleStore.construct("CONSTRUCT { ?s ?p ?o } WHERE { GRAPH <" + graphName + "> { ?s ?p ?o } }")
                    .onSuccess(model -> {
                        if (model.isEmpty()) {
                            promise.fail(new ServiceException(404, "Resource not found"));
                        } else {
                            Prefixes.setNsPrefixesFiltered(model);
                            promise.complete(Piveau.presentAs(model, acceptType));
                        }
                    }).onFailure(e -> {
                        log.error("Failed to construct model for graph: " + graphName, e);
                        promise.fail(new ServiceException(500, "Internal Server Error"));
                    });

            return promise.future();
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

    @Override
    public Future<Void> deleteResource(String id, String type) {
        try {
            Promise<Void> promise = Promise.promise();

            String graphName = baseUri + type + "/" + id;

            tripleStore.deleteGraph(graphName)
                    .onSuccess(v -> {
                        indexService.deleteResource(id, type, deleteResourceResult -> {
                            if (deleteResourceResult.succeeded()) {
                                System.out.println("Successfully deleted resource");
                            } else {
                                System.out.println(deleteResourceResult.cause().getMessage());
                            }
                        });
                        promise.complete();
                    })
                    .onFailure(cause -> {
                        if (cause.getMessage().contains("Not Found")) {
                            promise.fail(new ServiceException(404, "Resource not found"));
                        } else {
                            promise.fail(cause);
                        }
                    });

            return promise.future();
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

    @Override
    public Future<String> putResource(String id, String type, String content, String contentType) {
        try {
            Promise<String> promise = Promise.promise();

            if (profile.extraResourceIsSet(type)) {
                Model model = Piveau.toModel(content.getBytes(), contentType);
                ExtraResource resource = profile.getExtraResource(type);
                Property typeProperty = ModelFactory.createDefaultModel().createProperty(resource.getTypeURI());

                String graphName = baseUri + type + "/" + id;

                ResIterator resIterator = model.listSubjectsWithProperty(RDF.type, typeProperty);
                if (resIterator.hasNext()) {
                    resIterator.forEachRemaining(rs -> Piveau.rename(rs, graphName));
                    tripleStore.setGraph(graphName, model, true)
                            .onSuccess(setGraphResult -> {
                                Indexer.indexingResource(
                                        model.getResource(graphName),                       // dataset resource
                                        null,                                               // catalog record resource
                                        resource.getIndexingInstructions(),           // instructions build from shacl
                                        "en",                                               // default language
                                        "other",                                            // schema
                                        false,                                              // generate modified and issued (now)
                                        null,                                               // catalog id
                                        false                                               // set country from spatial
                                ).onSuccess(indexingResult -> {
                                    indexService.putResource(id, type, indexingResult, putResourceResult -> {
                                    });
                                    log.info(indexingResult.encodePrettily());
                                }).onFailure(ar -> {
                                    log.error("Indexing Resource failed", ar);
                                });
                                promise.complete(setGraphResult);
                            })
                            .onFailure(promise::fail);
                } else {
                    promise.fail(new ServiceException(400, "Resource type not found in payload"));
                }
            } else {
                promise.fail(new ServiceException(404, "Resource type unknown"));
            }

            return promise.future();
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }
}
