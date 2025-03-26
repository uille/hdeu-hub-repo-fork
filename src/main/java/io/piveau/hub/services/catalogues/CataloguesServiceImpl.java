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
package io.piveau.hub.services.catalogues;

import com.google.common.collect.Lists;
import io.piveau.dcatap.CatalogueManager;
import io.piveau.dcatap.DCATAPUriRef;
import io.piveau.dcatap.TripleStore;
import io.piveau.hub.Constants;
import io.piveau.hub.Defaults;
import io.piveau.hub.indexing.Indexing;
import io.piveau.hub.security.KeyCloakService;
import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.hub.services.index.IndexService;
import io.piveau.hub.util.CatalogueHelper;
import io.piveau.json.ConfigHelper;
import io.piveau.rdf.Piveau;
import io.piveau.vocabularies.vocabulary.EDP;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.DCAT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class CataloguesServiceImpl implements CataloguesService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TripleStore tripleStore;
    private final CatalogueManager catalogueManager;

    private IndexService indexService;

    private final DatasetsService datasetsService;

    private final KeyCloakService keycloakService;

    CataloguesServiceImpl(TripleStore tripleStore, Vertx vertx, JsonObject config, Handler<AsyncResult<CataloguesService>> readyHandler) {
        this.tripleStore = tripleStore;
        catalogueManager = tripleStore.getCatalogueManager();

        JsonObject indexConfig = ConfigHelper.forConfig(config).forceJsonObject(Constants.ENV_PIVEAU_HUB_SEARCH_SERVICE);

        Boolean searchServiceEnabled = indexConfig.getBoolean("enabled", Defaults.SEARCH_SERVICE_ENABLED);
        if (Boolean.TRUE.equals(searchServiceEnabled)) {
            indexService = IndexService.createProxy(vertx, IndexService.SERVICE_ADDRESS, IndexService.DEFAULT_TIMEOUT);
        }

        datasetsService = DatasetsService.createProxy(vertx, DatasetsService.SERVICE_ADDRESS);
        keycloakService = KeyCloakService.createProxy(vertx, KeyCloakService.SERVICE_ADDRESS);

        readyHandler.handle(Future.succeededFuture(this));
    }

    @Override
    public Future<String> listCatalogues(String acceptType, String valueType, Integer offset, Integer limit) {
        switch (valueType) {
            case "uriRefs" -> {
                return catalogueManager.listUris()
                        .map(list -> mapSublistToString(list, offset, limit, DCATAPUriRef::getUri));
            }
            case "identifiers", "originalIds" -> {
                return catalogueManager.listUris()
                        .map(list -> mapSublistToString(list, offset, limit, DCATAPUriRef::getId));
            }
            default -> {
                return catalogueManager.list(offset, limit)
                        .map(list -> Piveau.presentAs(list, acceptType, DCAT.Catalog));
            }
        }
    }

    @Override
    public Future<String> getCatalogue(String catalogueId, String acceptType) {
        return catalogueManager.exist(catalogueId)
                .compose(exists -> {
                    if (exists == Boolean.TRUE) {
                        return catalogueManager.getStripped(catalogueId);
                    } else {
                        return Future.failedFuture(new ServiceException(404, "Catalogue not found"));
                    }
                })
                .map(model -> Piveau.presentAs(model, acceptType));
    }

    @Override
    public Future<String> putCatalogue(String catalogueId, String content, String contentType) {
        CatalogueHelper catalogueHelper;
        try {
            catalogueHelper = new CatalogueHelper(catalogueId, contentType, content);
        } catch (RiotException e) {
            logger.error("Error parsing catalogue", e);
            return Future.failedFuture(new ServiceException(400, "Could not decode body with Content-Type " + contentType + ": " + e.getMessage()));
        }

        Model datasetsList = ModelFactory.createDefaultModel();
        return catalogueManager.exist(catalogueId)
                .compose(exists -> {
                    if (exists == Boolean.TRUE) {
                        // update
                        catalogueHelper.modified();
                        return catalogueManager.allDatasets(catalogueId)
                                .onSuccess(list ->
                                    list.forEach(dataset -> {
                                        datasetsList.add(catalogueHelper.resource(), DCAT.dataset, datasetsList.createResource(dataset.getUri()));
                                        datasetsList.add(catalogueHelper.resource(), DCAT.record, datasetsList.createResource(dataset.getRecordUriRef()));
                                    })
                                ).map("updated");
                    } else {
                        // create
                        return Future.succeededFuture("created");
                    }
                })
                .compose(result ->
                        catalogueManager.setGraph(catalogueHelper.uriRef(), catalogueHelper.getModel())
                                .onSuccess(uriRef -> keycloakService.createResource(catalogueHelper.getId()))
                                .map(result)
                )
                .compose(result -> {
                    if (datasetsList.isEmpty()) {
                        return Future.succeededFuture(result);
                    } else {
                        List<List<Statement>> chunks = Lists.partition(datasetsList.listStatements().toList(), 2500);
                        return tripleStore.postChunks(catalogueHelper.uriRef(), chunks).map(result);
                    }
                })
                .onSuccess(result -> {
                    StmtIterator it = catalogueHelper.getModel().listStatements(
                            catalogueHelper.getModel().getResource(catalogueHelper.uriRef()),
                            EDP.visibility,
                            EDP.hidden
                    );

                    if (!it.hasNext()) {
                        if (indexService != null) {
                            Indexing.indexingCatalogue(catalogueHelper.getModel().getResource(catalogueHelper.uriRef()))
                                    .onSuccess(index -> indexService.addCatalog(index, ar -> {
                                                if (ar.failed()) {
                                                    logger.error("Indexing new catalogue", ar.cause());
                                                }
                                            })
                                    );
                        }
                    } else {
                        logger.info("Skip indexing for hidden catalogue");
                    }
                }).onFailure(err -> logger.error("Error while handling catalogue", err));
    }

    @Override
    public Future<Void> deleteCatalogue(String id) {
        return catalogueManager.exist(id)
                .compose(exists -> {
                    if (exists == Boolean.TRUE) {
                        return catalogueManager.allDatasets(id);
                    } else {
                        return Future.failedFuture(new ServiceException(404, "Catalogue not found"));
                    }
                })
                .compose(list -> {
                    List<Future<Void>> futures = list.stream()
                            .map(uriRef -> datasetsService.deleteDataset(uriRef.getId()))
                            .toList();
                    return Future.join(new ArrayList<>(futures));
                })
                .compose(cf -> catalogueManager.delete(id))
                .onSuccess(v -> {
                    if (indexService != null) {
                        indexService.deleteCatalog(id, as -> {
                            if (as.failed()) {
                                logger.error("Removing catalogue from index: {}", as.cause().getMessage());
                            }
                        });
                    }
                    keycloakService.deleteResource(id);
                }).mapEmpty();
    }

    private String mapSublistToString(List<DCATAPUriRef> list, int offset, int limit, Function<? super DCATAPUriRef, String> mapper) {
        if (offset > list.size()) {
            return new JsonArray().encode();
        } else if ((offset + limit) > list.size()) {
            return new JsonArray(list.subList(offset, list.size()).stream().map(mapper).toList()).encodePrettily();
        } else {
            return new JsonArray(list.subList(offset, offset + limit).stream().map(mapper).toList()).encodePrettily();
        }
    }

}
