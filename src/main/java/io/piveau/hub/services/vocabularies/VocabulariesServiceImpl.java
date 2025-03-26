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
package io.piveau.hub.services.vocabularies;

import io.piveau.dcatap.*;
import io.piveau.hub.Defaults;
import io.piveau.hub.indexing.IndexingVocabulariesKt;
import io.piveau.hub.services.catalogues.CataloguesService;
import io.piveau.hub.services.datasets.DatasetsService;
import io.piveau.hub.services.index.IndexService;
import io.piveau.hub.Constants;
import io.piveau.hub.vocabularies.Vocabularies;
import io.piveau.rdf.Piveau;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public class VocabulariesServiceImpl implements VocabulariesService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String CACHE_ALIAS_PAYLOAD = "payload-cache";

    private static final String CACHE_ALIAS_COUNT = "count-cache";

    private static final String VOCABULARIES_CATALOGUE_ID = "vocabularies";
    private final TripleStore tripleStore;

    private final IndexService indexService;

    private final CatalogueManager catalogueManager;
    private final DatasetManager datasetManager;

    private final Cache<String, String[]> payloadCache;
    private final Cache<String, Integer> countCache;

    private final DatasetsService datasetsService;
    private final CataloguesService cataloguesService;

    private final Vertx vertx;

    VocabulariesServiceImpl(Vertx vertx, TripleStore tripleStore, JsonObject config, Handler<AsyncResult<VocabulariesService>> handler) {

        this.vertx = vertx;
        this.tripleStore = tripleStore;

        CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
        payloadCache = cacheManager.createCache(CACHE_ALIAS_PAYLOAD, CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String[].class,
                        ResourcePoolsBuilder.newResourcePoolsBuilder().heap(100, EntryUnit.ENTRIES))
                .withExpiry(ExpiryPolicyBuilder.timeToIdleExpiration(Duration.ofHours(12))));

        countCache = cacheManager.createCache(CACHE_ALIAS_COUNT, CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, Integer.class,
                        ResourcePoolsBuilder.newResourcePoolsBuilder().heap(100, EntryUnit.ENTRIES))
                .withExpiry(ExpiryPolicyBuilder.timeToIdleExpiration(Duration.ofHours(12))));

        boolean indexingEnabled = config
                .getJsonObject(Constants.ENV_PIVEAU_HUB_SEARCH_SERVICE, new JsonObject())
                .getBoolean("enabled", Defaults.SEARCH_SERVICE_ENABLED);
        if (indexingEnabled) {
            indexService = IndexService.createProxy(vertx, IndexService.SERVICE_ADDRESS, IndexService.DEFAULT_TIMEOUT);
        } else {
            indexService = null;
        }

        catalogueManager = tripleStore.getCatalogueManager();
        datasetManager = tripleStore.getDatasetManager();

        datasetsService = DatasetsService.createProxy(vertx, DatasetsService.SERVICE_ADDRESS);
        cataloguesService = CataloguesService.createProxy(vertx, CataloguesService.SERVICE_ADDRESS);

        handler.handle(Future.succeededFuture(this));
    }

    @Override
    public Future<String> listVocabularies(String acceptType, String valueType, Integer limit, Integer offset) {
        try {
            return Future.future(promise -> {
                switch (valueType) {
                    case "originalIds" -> catalogueManager.allDatasetIdentifiers(VOCABULARIES_CATALOGUE_ID)
                            .onSuccess(list -> promise.complete(new JsonArray(list).encodePrettily()))
                            .onFailure(promise::fail);
                    case "identifiers" -> catalogueManager.datasets(VOCABULARIES_CATALOGUE_ID, offset, limit)
                            .onSuccess(list -> promise.complete(new JsonArray(list.stream().map(DCATAPUriRef::getId).toList()).encodePrettily()))
                            .onFailure(promise::fail);
                    case "uriRefs" -> catalogueManager.datasets(VOCABULARIES_CATALOGUE_ID, offset, limit)
                            // TODO We need to take the accessURL of the distribution
                            .onSuccess(list -> promise.complete(new JsonArray(list.stream().map(DCATAPUriRef::getUri).toList()).encodePrettily()))
                            .onFailure(promise::fail);
                    case "metadata" -> catalogueManager.listDatasets(VOCABULARIES_CATALOGUE_ID, offset, limit)
                            // TODO We need to fetch the actual vocabulary, and not the vocabulary metadata
                            .onSuccess(list -> promise.complete(Piveau.presentAs(list, acceptType, DCAT.Dataset)))
                            .onFailure(promise::fail);
                    default -> promise.fail(new ServiceException(400, "Unknown valueType " + valueType));
                }
            });
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

    @Override
    public Future<JsonArray> indexVocabulary(String vocabularyId) {
        try {
            if (indexService == null) {
                return Future.failedFuture("Indexing service disabled");
            }

            AtomicReference<String> uriRef = new AtomicReference<>();
            return Vocabularies.findVocabularyUriRef(vocabularyId, datasetManager)
                    .compose(uri -> {
                        uriRef.set(uri);
                        return tripleStore.getGraph(uri);
                    })
                    .compose(model -> {
                        JsonObject index = IndexingVocabulariesKt.indexConceptScheme(vocabularyId, uriRef.get(), model);
                        return Future.<JsonArray>future(promise -> indexService.putVocabulary(vocabularyId, index, promise));
                    })
                    .onSuccess(result -> log.debug("Vocabulary {} successfully indexed", vocabularyId))
                    .onFailure(cause -> log.error("Indexing vocabulary {} failed", vocabularyId, cause));
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

    @Override
    public Future<String> getVocabulary(String vocabularyId, String acceptType) {
        try {
            return Vocabularies.findVocabularyUriRef(vocabularyId, datasetManager)
                    .compose(tripleStore::getGraph)
                    .map(model -> {
                        Prefixes.setNsPrefixesFiltered(model);
                        return Piveau.presentAs(model, acceptType);
                    })
                    .onFailure(cause -> {
                        log.error("Failed to get vocabulary", cause);
                    });
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

    private Future<String> postChunks(String vocabularyUri, String[] chunks, int chunkId, int numberOfChunks,
                                      String contentType) {
        try {
            String chunk = chunks[chunkId];
            Model model = Piveau.toModel(chunk.getBytes(), contentType);

            return tripleStore.postGraph(vocabularyUri, model)
                    .compose(result -> {
                        if (chunkId + 1 < numberOfChunks) {
                            return postChunks(vocabularyUri, chunks, chunkId + 1, numberOfChunks, contentType);
                        } else {
                            return Future.succeededFuture(result);
                        }
                    }).onFailure(cause -> {
                        log.error("Failed to postChunks", cause);
                    });
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

    private Future<String> putVocabularyChunks(String vocabularyUri, String[] chunks, int numberOfChunks, String contentType) {
        try {
            return tripleStore.update("CLEAR GRAPH <" + vocabularyUri + ">;")
                    .compose(clearResult -> {
                        String chunk = chunks[0];
                        Model model = Piveau.toModel(chunk.getBytes(), contentType);
                        return tripleStore.putGraph(vocabularyUri, model);
                    })
                    .compose(result -> postChunks(vocabularyUri, chunks, 1, numberOfChunks, contentType))
                    .onFailure(cause -> {
                        log.error("Failed to postChunks", cause);
                    });
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

    @Override
    public Future<String> putVocabularyFile(String vocabularyId, String vocabularyUri, String contentType, String file) {
        final AtomicReference<String> status = new AtomicReference<>();
        return vertx.<JsonArray>executeBlocking(promise -> {
                    try {
                        Path path = new File(file).toPath();
                        Model model = Piveau.toModel(path, Piveau.asRdfLang(contentType));
                        tripleStore.setGraph(vocabularyUri, model, false)
                                .compose(result -> {
                                    status.set(result);
                                    if (indexService != null) {
                                        JsonObject index = IndexingVocabulariesKt.indexConceptScheme(vocabularyId, vocabularyUri, model);
                                        return Future.future(p -> indexService.putVocabulary(vocabularyId, index, p));
                                    } else {
                                        return Future.succeededFuture(new JsonArray());
                                    }
                                }).onComplete(promise);
                    } catch (Throwable t) {
                        log.error("Something went wrong", t);
                        promise.fail(new ServiceException(500, t.getMessage()));
                    }
                })
                .onComplete(ar -> vertx.fileSystem().delete(file))
                .map(array -> {
                    log.debug("Put vocabulary succeeded");
                    return status.get();
                })
                .onFailure(cause -> log.error("Put failed", cause));
    }

    @Override
    public Future<String> putVocabulary(String vocabularyId, String vocabularyUri, String contentType, String content) {
        try {
            final AtomicReference<String> status = new AtomicReference<>();
            final AtomicReference<Model> model = new AtomicReference<>();
            final AtomicReference<String> uri = new AtomicReference<>(vocabularyUri);

            return vertx.<JsonArray>executeBlocking(promise -> {
                        try {
                            Model vocabularyModel = Piveau.toModel(content.getBytes(), contentType);
                            if (vocabularyUri.isBlank()) {
                                uri.set(vocabularyModel
                                        .listSubjectsWithProperty(RDF.type, SKOS.ConceptScheme).next().getURI());
                            }
                            model.set(vocabularyModel);
                            Vocabularies.updateVocabularyDataset(datasetsService, cataloguesService, uri.get(), vocabularyId)
                                    .compose(result ->
                                            tripleStore.setGraph(uri.get(), vocabularyModel, false))
                                    .compose(result -> {
                                        log.debug("Put success");
                                        status.set(result);
                                        if (indexService != null) {
                                            JsonObject index = IndexingVocabulariesKt.indexConceptScheme(vocabularyId, uri.get(), model.get());
                                            return Future.future(p -> indexService.putVocabulary(vocabularyId, index, p));
                                        } else {
                                            return Future.succeededFuture(new JsonArray());
                                        }
                                    }).onComplete(promise);
                        } catch (Throwable t) {
                            log.error("Something went wrong", t);
                            promise.fail(new ServiceException(500, t.getMessage()));
                        }
                    })
                    .map(array -> {
                        log.debug("Put vocabulary succeeded");
                        return status.get();
                    })
                    .onFailure(cause -> log.error("Put failed", cause));
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

    @Override
    public Future<String> putVocabularyChunk(String vocabularyId, String vocabularyUri, String contentType,
                                             String chunk, String hash, int chunkId, int numberOfChunks) {
        try {
            if (hash == null || hash.isEmpty()) {
                log.error("Put failed: hash null or empty");
                return Future.failedFuture(new ServiceException(400, "Put failed: hash null or empty"));
            }

            if (chunkId < 0 || numberOfChunks < 0 || chunkId > numberOfChunks) {
                log.error("Put failed: chunkId or numberOfChunks incorrect");
                return Future.failedFuture(new ServiceException(400,
                        "Put failed: chunkId or numberOfChunks incorrect"));
            }

            String[] currentPayload = payloadCache.get(hash);
            Integer currentCount = countCache.get(hash);

            if (currentPayload != null && currentCount != null) {
                currentPayload[chunkId] = chunk;

                currentCount = currentCount + 1;
                countCache.put(hash, currentCount);

                AtomicReference<String[]> chunks = new AtomicReference<>(currentPayload);

                if (currentCount == numberOfChunks) {
                    return Vocabularies.updateVocabularyDataset(
                                    datasetsService,
                                    cataloguesService,
                                    vocabularyUri,
                                    vocabularyId)
                            .compose(s -> putVocabularyChunks(vocabularyUri, chunks.get(), numberOfChunks, contentType))
                            .onSuccess(result -> {
                                log.debug("Put success");

                                payloadCache.remove(hash);
                                countCache.remove(hash);

                                indexVocabulary(vocabularyId)
                                        .onFailure(cause -> log.error("Indexing {} failure", vocabularyId, cause));
                            })
                            .onFailure(cause -> {
                                log.error("Put error", cause);

                                payloadCache.remove(hash);
                                countCache.remove(hash);
                            });
                } else {
                    return Future.succeededFuture("accepted");
                }
            } else {
                currentPayload = new String[numberOfChunks];
                currentPayload[chunkId] = chunk;
                payloadCache.put(hash, currentPayload);
                countCache.put(hash, 1);

                return Future.succeededFuture("accepted");
            }
        } catch (Exception e) {
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

    @Override
    public Future<Void> deleteVocabulary(String vocabularyId) {
        try {
            return Vocabularies.findVocabularyUriRef(vocabularyId, datasetManager)
                    .compose(tripleStore::deleteGraph)
                    .compose(v -> datasetsService.deleteDatasetOrigin(vocabularyId, VOCABULARIES_CATALOGUE_ID))
                    .onSuccess(result -> {
                        if (indexService != null) {
                            indexService.deleteVocabulary(vocabularyId, ar -> {
                                if (ar.failed())
                                    log.warn("Failed to delete vocabulary from index", ar.cause());
                            });
                        }
                    }).onFailure(cause -> log.error("Delete failed", cause));
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
        }
    }

}
