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
package io.piveau.hub.services.drafts;

import io.piveau.dcatap.DCATAPUriRef;
import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.dcatap.Prefixes;
import io.piveau.dcatap.TripleStore;
import io.piveau.hub.Constants;
import io.piveau.hub.Defaults;
import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.rdf.Piveau;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetDraftsServiceImpl implements DatasetDraftsService {

    private final TripleStore shadowTripleStore;

    private final boolean prependXmlDeclaration;

    private final Logger log = LoggerFactory.getLogger(getClass());

    DatasetDraftsServiceImpl(TripleStore shadowTripleStore, JsonObject config, Vertx vertx,
                             Handler<AsyncResult<DatasetDraftsService>> readyHandler) {
        this.shadowTripleStore = shadowTripleStore;

        prependXmlDeclaration = config.getBoolean(Constants.ENV_PIVEAU_HUB_XML_DECLARATION, Defaults.XML_DECLARATION);

        readyHandler.handle(Future.succeededFuture(this));
    }

    public DatasetDraftsService listDatasetDrafts(List<String> catalogueIds, String provider,
                                                  Handler<AsyncResult<JsonArray>> handler) {
        try {
            String filter = "";
            String creator = "";
            if (provider != null && !provider.isEmpty()) {
                filter = "FILTER(?creator = \"" + provider + "\")";
                creator = "; <" + DCTerms.creator + "> ?creator";
            } else if (catalogueIds != null && !catalogueIds.isEmpty()) {
                List<String> catalogueUriRefs = new ArrayList<>();
                for (String catalogueId : catalogueIds) {
                    DCATAPUriRef catalogueUriRef = DCATAPUriSchema.applyFor(catalogueId, DCATAPUriSchema.getCatalogueContext());
                    catalogueUriRefs.add("<" + catalogueUriRef.getCatalogueUriRef() + ">");
                }
                filter = "FILTER(?catalog IN (" + String.join(",", catalogueUriRefs) + "))";
            }

            String query = "SELECT ?id ?catalog ?title ?description \nWHERE { \nGRAPH ?dataset { \n?record "
                    + "<" + FOAF.primaryTopic + "> ?dataset;\n"
                    + "<" + DCTerms.identifier + "> ?id;\n"
                    + "<" + DCAT.catalog + "> ?catalog\n"
                    + "OPTIONAL { ?dataset <" + DCTerms.title + "> ?title }\n"
                    + "OPTIONAL { ?dataset <" + DCTerms.description + "> ?description }\n"
                    + creator + "}\n"
                    + filter + "}";

            shadowTripleStore.select(query).onSuccess(result -> {
                JsonArray jsonResult = new JsonArray();
                while (result.hasNext()) {
                    QuerySolution qs = result.next();

                    String id = qs.getLiteral("id").getLexicalForm();
                    String catalog = StringUtils.substringAfterLast(qs.getResource("catalog").getURI(), "/");

                    JsonObject dataset = null;

                    for (Object o : jsonResult) {
                        JsonObject jsonObject = (JsonObject) o;
                        if (jsonObject.getString("id").equals(id)
                                && jsonObject.getString("catalog").equals(catalog)) dataset = jsonObject;
                    }

                    if (dataset == null) {
                        dataset = new JsonObject();
                        dataset.put("id", id);
                        dataset.put("catalog", catalog);
                        jsonResult.add(dataset);
                    }

                    if (qs.getLiteral("title") != null) {
                        if (dataset.getJsonObject("title") == null) {
                            dataset.put("title", new JsonObject());
                        }

                        dataset.getJsonObject("title").put(
                                qs.getLiteral("title").getLanguage(),
                                qs.getLiteral("title").getLexicalForm()
                        );
                    }

                    if (qs.getLiteral("description") != null) {
                        if (dataset.getJsonObject("description") == null) {
                            dataset.put("description", new JsonObject());
                        }

                        dataset.getJsonObject("description").put(
                                qs.getLiteral("description").getLanguage(),
                                qs.getLiteral("description").getLexicalForm()
                        );
                    }
                }
                handler.handle(Future.succeededFuture(jsonResult));
            }).onFailure(cause -> {
                if (cause instanceof ServiceException) {
                    handler.handle(Future.failedFuture(cause));
                } else {
                    handler.handle(ServiceException.fail(500, cause.getMessage()));
                }
            });
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    public DatasetDraftsService createDatasetDraft(String catalogueId, String payload, String contentType,
                                                   String provider, Handler<AsyncResult<String>> handler) {
        try {
            String datasetId = UUID.randomUUID().toString();
            createOrUpdateDatasetDraft(datasetId, catalogueId, payload, contentType, provider, handler);
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    public DatasetDraftsService readDatasetDraft(String datasetId, String catalogueId, String acceptType,
                                                 Handler<AsyncResult<String>> handler) {
        try {
            DCATAPUriRef catalogueUriRef = DCATAPUriSchema.createFor(catalogueId);

            String query = "SELECT ?dataset WHERE { GRAPH ?dataset { ?record "
                    + "<" + FOAF.primaryTopic + "> ?dataset;"
                    + "<" + DCTerms.identifier + "> \"" + datasetId + "\" ;"
                    + "<" + DCAT.catalog + "> <" + catalogueUriRef.getCatalogueUriRef() + "> } }";

            shadowTripleStore.select(query).onSuccess(result -> {
                if (result.hasNext()) {
                    String uri = result.next().getResource("dataset").getURI();
                    shadowTripleStore.construct("CONSTRUCT { ?s ?p ?o } WHERE { GRAPH <" + uri + "> { ?s ?p ?o } }")
                            .onSuccess(model -> {
                                if (model.isEmpty()) {
                                    handler.handle(ServiceException.fail(404, "Dataset draft not found"));
                                } else {
                                    Prefixes.setNsPrefixesFiltered(model);
                                    handler.handle(Future.succeededFuture(
                                            Piveau.presentAs(model, acceptType)));
                                }
                            }).onFailure(cause -> {
                                if (cause.getMessage().startsWith("Not found")) {
                                    handler.handle(ServiceException.fail(404, "Dataset draft not found"));
                                } else {
                                    handler.handle(ServiceException.fail(500, cause.getMessage()));
                                }
                            });
                } else {
                    handler.handle(ServiceException.fail(404, "Dataset draft not found"));
                }
            }).onFailure(cause -> {
                if (cause instanceof ServiceException) {
                    handler.handle(Future.failedFuture(cause));
                } else {
                    handler.handle(ServiceException.fail(500, cause.getMessage()));
                }
            });
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    public DatasetDraftsService createOrUpdateDatasetDraft(String datasetId, String catalogueId, String payload,
                                                           String contentType, String provider,
                                                           Handler<AsyncResult<String>> handler) {
        try {
            log.debug("Create or update draft dataset");
            DatasetHelper datasetHelper = DatasetHelper.create(datasetId, payload, contentType, catalogueId);
            findPiveauId(datasetHelper.piveauId(), catalogueId, 0, ar -> {
                if (ar.succeeded()) {
                    log.debug("Found piveau id: {}", ar.result());
                    datasetHelper.init(ar.result());

                    DCATAPUriRef catalogueUriRef = DCATAPUriSchema.createForCatalogue(catalogueId);
                    Model model = datasetHelper.model();
                    Resource catalogRecord = model.getResource(datasetHelper.recordUriRef());
                    Resource catalogResource = model.createResource(catalogueUriRef.getCatalogueUriRef());

                    Statement catalogStatement = model.createStatement(catalogRecord, DCAT.catalog, catalogResource);
                    model.add(catalogStatement);

                    if (!provider.isEmpty()) {
                        Statement providerStatement = model.createStatement(catalogRecord, DCTerms.creator, provider);
                        model.add(providerStatement);
                    }

                    log.debug("Store draft dataset in shadow triple store");
                    shadowTripleStore.setGraph(datasetHelper.graphName(), model, true)
                            .onSuccess(setGraphResult -> {
                                log.debug("Successfully stored.");
                                handler.handle(Future.succeededFuture(setGraphResult));
                            })
                            .onFailure(cause -> handler.handle(ServiceException.fail(500, cause.getMessage())));
                } else {
                    handler.handle(ServiceException.fail(500, ar.cause().getMessage()));
                }
            });
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    public DatasetDraftsService deleteDatasetDraft(String datasetId, String catalogueId,
                                                   Handler<AsyncResult<Void>> handler) {
        try {
            DCATAPUriRef catalogueUriRef = DCATAPUriSchema.applyFor(catalogueId);

            String query = "SELECT ?dataset WHERE { GRAPH ?dataset { ?record "
                    + "<" + FOAF.primaryTopic + "> ?dataset;"
                    + "<" + DCTerms.identifier + "> \"" + datasetId + "\" ;"
                    + "<" + DCAT.catalog + "> <" + catalogueUriRef.getCatalogueUriRef() + "> } }";

            shadowTripleStore.select(query).onSuccess(result -> {
                if (result.hasNext()) {
                    String uri = result.next().getResource("dataset").getURI();
                    shadowTripleStore.deleteGraph(uri)
                            .onSuccess(deleteGraphResult -> handler.handle(Future.succeededFuture()))
                            .onFailure(cause -> {
                                if (cause.getMessage().startsWith("Not found")) {
                                    handler.handle(ServiceException.fail(404, "Dataset draft not found"));
                                } else {
                                    handler.handle(ServiceException.fail(500, cause.getMessage()));
                                }
                            });
                } else {
                    handler.handle(ServiceException.fail(404, "Dataset draft not found"));
                }
            }).onFailure(cause -> {
                if (cause instanceof ServiceException) {
                    handler.handle(Future.failedFuture(cause));
                } else {
                    handler.handle(ServiceException.fail(500, cause.getMessage()));
                }
            });
        } catch (Exception e) {
            handler.handle(ServiceException.fail(500, "Internal Server Error"));
        }
        return this;
    }

    private void findPiveauId(String datasetId, String catalogueId, int counter, Handler<AsyncResult<String>> handler) {
        AtomicReference<String> checkId = new AtomicReference<>();
        if (counter > 0) {
            checkId.set(datasetId + "~~" + counter);
        } else {
            checkId.set(datasetId);
        }

        String datasetUriRef = DCATAPUriSchema.applyFor(checkId.get()).getDatasetUriRef();
        String catalogueUriRef = DCATAPUriSchema.applyFor(catalogueId).getCatalogueUriRef();

        String query1 = "ASK WHERE { GRAPH ?dataset { ?record "
                + "<" + FOAF.primaryTopic + "> <" + datasetUriRef + ">;"
                + "<" + DCTerms.identifier + "> \"" + datasetId + "\" ;"
                + "<" + DCAT.catalog + "> ?catalog } }";

        String query2 = "ASK WHERE { GRAPH ?dataset { ?record "
                + "<" + FOAF.primaryTopic + "> <" + datasetUriRef + ">;"
                + "<" + DCTerms.identifier + "> \"" + datasetId + "\" ;"
                + "<" + DCAT.catalog + "> <" + catalogueUriRef + "> } }";

        shadowTripleStore.ask(query1).onSuccess(exist1 -> {
            if (Boolean.TRUE.equals(exist1)) {
                shadowTripleStore.ask(query2).onSuccess(exist2 -> {
                    if (Boolean.TRUE.equals(exist2)) {
                        handler.handle(Future.succeededFuture(checkId.get()));
                    } else {
                        findPiveauId(datasetId, catalogueId, counter + 1, handler);
                    }
                });
            } else {
                handler.handle(Future.succeededFuture(checkId.get()));
            }
        }).onFailure(cause -> handler.handle(ServiceException.fail(500, cause.getMessage())));
    }

}
