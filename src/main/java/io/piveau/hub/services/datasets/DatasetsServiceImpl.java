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
package io.piveau.hub.services.datasets;

import io.piveau.HubRepo;
import io.piveau.dcatap.CatalogueManager;
import io.piveau.dcatap.DCATAPUriRef;
import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.dcatap.DatasetManager;
import io.piveau.dcatap.Prefixes;
import io.piveau.dcatap.TripleStore;
import io.piveau.dcatap.TripleStoreException;
import io.piveau.dga.AddOns;
import io.piveau.hub.Constants;
import io.piveau.hub.Defaults;
import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.hub.indexing.Indexing;
import io.piveau.hub.services.index.IndexService;
import io.piveau.hub.services.translation.TranslationService;
import io.piveau.hub.util.DataUploadConnector;
import io.piveau.json.ConfigHelper;
import io.piveau.log.PiveauLogger;
import io.piveau.pipe.PipeLauncher;
import io.piveau.rdf.HydraCollection;
import io.piveau.rdf.Piveau;
import io.piveau.rdf.RDFMimeTypes;
import io.piveau.sparql.SparqlQueryPool;
import io.piveau.utils.PiveauContext;
import io.piveau.vocabularies.Concept;
import io.piveau.vocabularies.Languages;
import io.piveau.vocabularies.vocabulary.LOCN;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shared.DoesNotExistException;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.RDF;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetsServiceImpl implements DatasetsService {

  private static final String DGA_SUPER_CATALOGUE = "http://data.europa.eu/88u/catalogue/erpd";

  private static final String KEY_ENABLED = "enabled";
  private static final String KEY_VISIBILITY = "visibility";

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final PiveauContext piveauContext = new PiveauContext("hub", "repo");

  private final PiveauContext dgaContext = new PiveauContext("dga", "dga-validation");

  private final TripleStore tripleStore;
  private final TripleStore shadowTripleStore;

  private final CatalogueManager catalogueManager;
  private final DatasetManager datasetManager;

  private final DataUploadConnector dataUploadConnector;

  private IndexService indexService;
  private TranslationService translationService;

  private final JsonObject validationConfig;

  private final PipeLauncher launcher;

  private final Cache<String, JsonObject> cache;

  private final Set<String> clearGeoDataCatalogues = new HashSet<>();

  private Boolean forceUpdates = Defaults.FORCE_UPDATES;

  private Boolean archivingEnabled = Defaults.EXPERIMENTS;

  private final SparqlQueryPool sparqlQueryPool;

  /**
   * Query templates loaded synchronously from classpath at construction time.
   * Using classpath loading avoids the async race condition of SparqlQueryPool
   * where queries could be null immediately after startup.
   */
  private final String catalogueQueryTemplate;
  private final String recordQueryTemplate;
  private final String askPiveauIdQueryTemplate;

  /**
   * Loads a SPARQL query template synchronously from classpath resources.
   * Tries multiple paths to handle different classloader configurations.
   * Falls back to null if not found (caller handles this).
   */
  private static String loadQueryTemplate(String name) {
    String resourcePath = "/queries/" + name + ".sparql";
    // Try with leading slash (absolute classpath path)
    try (java.io.InputStream is = DatasetsServiceImpl.class.getResourceAsStream(resourcePath)) {
      if (is != null) {
        return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      }
    } catch (Exception e) {
      // fall through to next attempt
    }
    // Try without leading slash (relative to package, some classloaders prefer this)
    try (java.io.InputStream is = DatasetsServiceImpl.class.getClassLoader()
        .getResourceAsStream("queries/" + name + ".sparql")) {
      if (is != null) {
        return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      }
    } catch (Exception e) {
      // fall through
    }
    return null;
  }

  DatasetsServiceImpl(
      TripleStore tripleStore,
      TripleStore shadowTripleStore,
      DataUploadConnector dataUploadConnector,
      JsonObject config,
      PipeLauncher launcher,
      Vertx vertx,
      Handler<AsyncResult<DatasetsService>> readyHandler) {

    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache("datasetsService",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, JsonObject.class,
                    ResourcePoolsBuilder.newResourcePoolsBuilder().heap(100, EntryUnit.ENTRIES))
                .withExpiry(ExpiryPolicyBuilder.timeToIdleExpiration(Duration.ofHours(12))))
        .build(true);

    cache = cacheManager.getCache("datasetsService", String.class, JsonObject.class);

    this.launcher = launcher;

    this.tripleStore = tripleStore;
    this.shadowTripleStore = shadowTripleStore;

    ConfigHelper configHelper = ConfigHelper.forConfig(config);
    JsonArray list =
        configHelper.forceJsonObject(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG)
            .getJsonArray("clearGeoDataCatalogues", new JsonArray());
    List<String> entries = list.stream().map(Object::toString).toList();
    clearGeoDataCatalogues.addAll(entries);

    forceUpdates = config.getBoolean(Constants.ENV_PIVEAU_HUB_FORCE_UPDATES, forceUpdates);

    archivingEnabled = config.getBoolean(Constants.ENV_PIVEAU_ARCHIVING_ENABLED, archivingEnabled);

    catalogueManager = tripleStore.getCatalogueManager();
    datasetManager = tripleStore.getDatasetManager();

    this.dataUploadConnector = dataUploadConnector;

    JsonObject indexConfig = configHelper.forceJsonObject(Constants.ENV_PIVEAU_HUB_SEARCH_SERVICE);
    if (indexConfig.getBoolean(KEY_ENABLED, Defaults.SEARCH_SERVICE_ENABLED) == Boolean.TRUE) {
      indexService = IndexService.createProxy(vertx, IndexService.SERVICE_ADDRESS,
          IndexService.DEFAULT_TIMEOUT);
    }
    JsonObject translationConfig = configHelper.forceJsonObject(
        Constants.ENV_PIVEAU_TRANSLATION_SERVICE);
    if (translationConfig.getBoolean("enable", Defaults.TRANSLATION_SERVICE_ENABLED)
        == Boolean.TRUE) {
      translationService = TranslationService.createProxy(vertx,
          TranslationService.SERVICE_ADDRESS);
    }

    validationConfig = configHelper.forceJsonObject(Constants.ENV_PIVEAU_HUB_VALIDATOR);

    sparqlQueryPool = SparqlQueryPool.Companion.create(vertx, Path.of("queries"));

    // Load critical query templates synchronously from classpath to avoid the async
    // race condition where sparqlQueryPool.format() returns null immediately after startup
    catalogueQueryTemplate = loadQueryTemplate("catalogueQuery");
    recordQueryTemplate = loadQueryTemplate("recordQuery");
    askPiveauIdQueryTemplate = loadQueryTemplate("askPiveauId");

    if (catalogueQueryTemplate == null) {
      logger.error("Failed to load catalogueQuery.sparql from classpath");
    }
    if (recordQueryTemplate == null) {
      logger.error("Failed to load recordQuery.sparql from classpath");
    }
    if (askPiveauIdQueryTemplate == null) {
      logger.error("Failed to load askPiveauId.sparql from classpath");
    }

    readyHandler.handle(Future.succeededFuture(this));
  }

  @Override
  public Future<String> listCatalogueDatasets(String acceptType, String valueType,
      String catalogueId, Integer limit, Integer offset) {
    return catalogueManager.exist(catalogueId)
        .compose(exists -> {
          if (exists == Boolean.FALSE) {
            return Future.failedFuture(new ServiceException(404, "Catalogue not found"));
          } else {
            switch (valueType) {
              case "originalIds" -> {
                return catalogueManager.allDatasetIdentifiers(catalogueId)
                    .map(list -> new JsonArray(list).encodePrettily());
              }
              case "identifiers" -> {
                return catalogueManager.datasets(catalogueId, offset, limit)
                    .map(list -> new JsonArray(
                        list.stream().map(DCATAPUriRef::getId).toList()).encodePrettily());
              }
              case "uriRefs" -> {
                return catalogueManager.datasets(catalogueId, offset, limit)
                    .map(list -> new JsonArray(
                        list.stream().map(DCATAPUriRef::getUri).toList()).encodePrettily());
              }
              case "metadata" -> {
                return catalogueManager.listDatasets(catalogueId, offset, limit)
                    .map(list -> {
                      list.forEach(this::prepareDataset);
                      return Piveau.presentAs(list, acceptType, DCAT.Dataset);
                    });
              }
              default -> {
                return Future.failedFuture(new ServiceException(400, "Unknown value type"));
              }
            }
          }
        });
  }

  @Override
  public Future<String> listDatasets(String acceptType, String valueType, Integer limit,
      Integer offset, Boolean hydra, String base, Boolean usePagedCollection) {
    switch (valueType) {
      case "originalIds" -> {
        return Future.failedFuture(new ServiceException(400,
            "Value type originalIds only allowed when catalogueId is set"));
      }
      case "identifiers" -> {
        return datasetManager.list(offset, limit)
            .map(list -> new JsonArray(
                list.stream().map(DCATAPUriRef::getId).toList()).encodePrettily());
      }
      case "uriRefs" -> {
        return datasetManager.list(offset, limit)
            .map(list -> new JsonArray(
                list.stream().map(DCATAPUriRef::getUri).toList()).encodePrettily());
      }
      case "metadata" -> {
        return datasetManager.listModels(offset, limit)
            .compose(list -> {
              list.forEach(this::prepareDataset);
              if (hydra == Boolean.TRUE) {
                return datasetManager.count()
                    .compose(count -> {
                      HydraCollection hydraCollection = HydraCollection.build(base, count, offset,
                          limit, usePagedCollection);
                      list.add(hydraCollection.getModel());
                      return Future.succeededFuture(
                          Piveau.presentAs(list, acceptType, DCAT.Dataset));
                    });
              } else {
                return Future.succeededFuture(Piveau.presentAs(list, acceptType, DCAT.Dataset));
              }
            });
      }
      default -> {
        return Future.failedFuture(new ServiceException(400, "Unknown value type"));
      }
    }
  }

  private Model changeVirtuType(Model model) {
    List<Statement> stmtList = new ArrayList<>();
    List<Statement> stmtListToRemove = new ArrayList<>();

    logger.trace("listResourcesWithProperty");
    model.listResourcesWithProperty(LOCN.geometry).forEach(res -> {
      logger.trace("RES: {}", res);
      res.listProperties(LOCN.geometry).forEach(stmt -> {
        logger.trace("STMT: {}", stmt);
        if (stmt.getObject().isLiteral()) {
          Literal l = stmt.getLiteral();
          logger.trace("DATATYPE: {}", l.getDatatype().getURI());
          if (l.getDatatype().getURI()
              .equals("http://www.openlinksw.com/schemas/virtrdf#Geometry")) {

            stmtListToRemove.add(stmt);
            String value = l.getString();
            Literal modifiedLiteral = ResourceFactory.createTypedLiteral(value,
                NodeFactory.getType("http://www.opengis.net/ont/geosparql#wktLiteral"));

            Statement newStmt = ResourceFactory.createStatement(stmt.getSubject(),
                stmt.getPredicate(), modifiedLiteral);

            stmtList.add(newStmt);
          }

        }
      });
    });
    stmtListToRemove.forEach(model::remove);
    stmtList.forEach(model::add);
    return model;
  }

  @Override
  public Future<String> getDatasetOrigin(String originId, String catalogueId, String acceptType) {
    return catalogueManager.exist(catalogueId)
        .compose(exists -> {
          if (exists == Boolean.TRUE) {
            return datasetManager.get(originId, catalogueId);
          } else {
            return Future.failedFuture(new ServiceException(404, "Catalogue not found"));
          }
        })
        .map(model -> {
          if (model.isEmpty()) {
            throw new ServiceException(404, "Dataset not found");
          } else {
            prepareDataset(model);
            return Piveau.presentAs(model, acceptType);
          }
        })
        .recover(cause -> Future.failedFuture(mapCause(cause)));
  }

  @Override
  public Future<String> getDataset(String id, String acceptType) {
    DCATAPUriRef uriRef = DCATAPUriSchema.createForDataset(id);
    return datasetManager.getGraph(uriRef.getGraphName())
        .map(model -> {
          if (model.isEmpty()) {
            throw new ServiceException(404, "Dataset not found");
          } else {
            prepareDataset(model);
            return Piveau.presentAs(model, acceptType);
          }
        });
  }

  @Override
  public Future<String> getRecord(String id, String acceptType) {
    DCATAPUriRef uriRef = DCATAPUriSchema.createForDataset(id);
    return datasetManager.getGraph(uriRef.getDatasetGraphName())
        .map(model -> {
          if (model.isEmpty()) {
            throw new ServiceException(404, "Catalogue record not found");
          } else {
            Resource recordResource = Piveau.findRecordAsResource(model);
            if (recordResource != null) {
              Model extracted = Piveau.extractAsModel(recordResource);

              if (Piveau.isQuadFormat(acceptType)) {
                Dataset dataset = DatasetFactory.create(extracted);
                Prefixes.setNsPrefixesFiltered(dataset);
                return Piveau.presentAs(dataset, acceptType);
              } else {
                Prefixes.setNsPrefixesFiltered(extracted);
                return Piveau.presentAs(extracted, acceptType);
              }
            } else {
              throw new ServiceException(404, "Catalogue record not found");
            }
          }
        });
  }

  @Override
  public Future<JsonObject> putDataset(String datasetId, String content, String contentType) {
    PiveauLogger logPiveau = piveauContext.extend(datasetId).log();

    DatasetHelper datasetHelper;
    try {
      datasetHelper = DatasetHelper.create(content, contentType);
    } catch (Exception e) {
      return Future.failedFuture(new ServiceException(400, e.getMessage()));
    }

    return datasetManager.existGraph(datasetHelper.graphName())
        .compose(exists -> {
          if (exists == Boolean.FALSE) {
            return Future.failedFuture(new ServiceException(404, "Dataset not found"));
          } else {
            return datasetManager.catalogue(datasetHelper.uriRef());
          }
        })
        .compose(catalog -> {
          String catalogueId = DCATAPUriSchema.parseUriRef(catalog.getURI()).getId();
          datasetHelper.catalogueId(catalogueId);
          return catalogueInfo(catalogueId);
        })
        .compose(info -> {
          datasetHelper.setCatalogueInfo(info);
          return updateDataset(datasetHelper, datasetHelper.recordUriRef());
        })
        .compose(this::store)
        .map(result -> {
          if (indexService != null) {
            index(datasetHelper)
                .onFailure(cause -> {
                  if (cause instanceof ServiceException serviceException) {
                    logPiveau.error("Indexing failure: {}",
                        serviceException.getDebugInfo().encode(), cause);
                  }
                });
          } else {
            logPiveau.debug("index service disabled");
          }
          if (validationConfig.getBoolean(KEY_ENABLED, Defaults.VALIDATOR_ENABLED)
              == Boolean.TRUE) {
            systemPipes(datasetHelper);
          }
          //Log to archive
          if (archivingEnabled == Boolean.TRUE) {
            logPiveau.info(
                "[Dataset] [0] [updated] " + Piveau.presentAsRdfXml(datasetHelper.model()));
          }
          return result;
        });
  }

  @Override
  public Future<JsonObject> putDatasetOrigin(String originalId, String content, String contentType,
      String catalogueId, Boolean createAccessURLs) {
    PiveauLogger logPiveau = piveauContext.extend(originalId).log();

    DatasetHelper datasetHelper;
    try {
      datasetHelper = DatasetHelper.create(originalId, content, contentType, catalogueId);
    } catch (Exception e) {
      return Future.failedFuture(new ServiceException(400, e.getMessage()));
    }

    return catalogueInfo(catalogueId)
        .compose(catalogueInfo -> {
          datasetHelper.setCatalogueInfo(catalogueInfo);
          dgaValidation(datasetHelper);
          return getRecordHash(datasetHelper);
        })
        .compose(records -> {
          if (records.isEmpty()) {
            return createDataset(datasetHelper, createAccessURLs);
          } else {
            if (records.size() > 1) {
              removeDuplicates(datasetHelper, records);
            }
            if (records.isEmpty()) {
              return Future.failedFuture(new ServiceException(500,
                  "Due to unclear duplicate detection removed completely"));
            }
            JsonObject recordInfo = records.get(0);
            String recordUri = recordInfo.getString("recordUri");
            if (!DCATAPUriSchema.isRecordUriRef(recordUri)) {
              return Future.failedFuture(new ServiceException(500,
                  "Foreign catalogue record with dct:identifier detected"));
            } else {
              String oldHash = recordInfo.getString("hash");
              String currentHash = datasetHelper.hash();
              if (currentHash.equals(oldHash) && forceUpdates == Boolean.FALSE) {
                return Future.failedFuture(new ServiceException(304, "Dataset is up to date"));
              } else {
                return updateDataset(datasetHelper, recordUri);
              }
            }
          }
        })
        .compose(this::store)
        .recover(cause -> Future.failedFuture(mapCause(cause)))
        .onSuccess(status -> {
          if (!datasetHelper.getCatalogueInfo().getString(KEY_VISIBILITY, "").equals("hidden")
              && indexService != null) {
            index(datasetHelper)
                .onFailure(cause -> {
                  if (cause instanceof ServiceException serviceException) {
                    logPiveau.error("Indexing failure: {}",
                        serviceException.getDebugInfo().encode(), cause);
                  }
                });
          }
          if (validationConfig.getBoolean(KEY_ENABLED, Defaults.VALIDATOR_ENABLED)
              == Boolean.TRUE) {
            systemPipes(datasetHelper);
          }
          if (archivingEnabled == Boolean.TRUE) {
            piveauContext.extend(datasetHelper.piveauId()).log()
                .info("[Dataset] [{}] [{}] {}", catalogueId, status.getString("status"),
                    Piveau.presentAsRdfXml(datasetHelper.model()));
          }
        });
  }

  @Override
  public Future<JsonObject> putDatasetOriginHealthData(String originalId, String content, String contentType,
      String catalogueId, Boolean createAccessURLs, boolean isDatasetUpdate) {
    PiveauLogger logPiveau = piveauContext.extend(originalId).log();

    DatasetHelper datasetHelper;
    try {
      datasetHelper = DatasetHelper.create(originalId, content, contentType, catalogueId);
    } catch (Exception e) {
      return Future.failedFuture(new ServiceException(400, e.getMessage()));
    }

    return catalogueInfo(catalogueId)
        .compose(catalogueInfo -> {
          datasetHelper.setCatalogueInfo(catalogueInfo);
          dgaValidation(datasetHelper);
          return getRecordHash(datasetHelper);
        })
        .compose(records -> {
          if (records.isEmpty()) {
            // if no dataset was found in virtuoso, and we are on update request, we should notify
            // with a 404 that the dataset was not found
            if (isDatasetUpdate) {
              return Future.failedFuture(new ServiceException(404, "Dataset not found"));
            } else {
              return createDataset(datasetHelper, createAccessURLs);
            }
          } else {
            if (records.size() > 1) {
              removeDuplicates(datasetHelper, records);
            }
            if (records.isEmpty()) {
              return Future.failedFuture(new ServiceException(500,
                  "Due to unclear duplicate detection removed completely"));
            }
            JsonObject recordInfo = records.get(0);
            String recordUri = recordInfo.getString("recordUri");
            if (!DCATAPUriSchema.isRecordUriRef(recordUri)) {
              return Future.failedFuture(new ServiceException(500,
                  "Foreign catalogue record with dct:identifier detected"));
            } else {
              String oldHash = recordInfo.getString("hash");
              String currentHash = datasetHelper.hash();
              if (currentHash.equals(oldHash) && forceUpdates == Boolean.FALSE) {
                return Future.failedFuture(new ServiceException(304, "Dataset is up to date"));
              } else {
                return updateDataset(datasetHelper, recordUri);
              }
            }
          }
        })
        .compose(this::store)
        .recover(cause -> Future.failedFuture(mapCause(cause)))
        .onSuccess(status -> {
          if (!datasetHelper.getCatalogueInfo().getString(KEY_VISIBILITY, "").equals("hidden")
              && indexService != null) {
            index(datasetHelper)
                .onFailure(cause -> {
                  if (cause instanceof ServiceException serviceException) {
                    logPiveau.error("Indexing failure: {}",
                        serviceException.getDebugInfo().encode(), cause);
                  }
                });
          }
          if (validationConfig.getBoolean(KEY_ENABLED, Defaults.VALIDATOR_ENABLED)
              == Boolean.TRUE) {
            systemPipes(datasetHelper);
          }
          if (archivingEnabled == Boolean.TRUE) {
            piveauContext.extend(datasetHelper.piveauId()).log()
                .info("[Dataset] [{}] [{}] {}", catalogueId, status.getString("status"),
                    Piveau.presentAsRdfXml(datasetHelper.model()));
          }
        });
  }

  @Override
  public Future<JsonObject> postDataset(String content, String contentType, String catalogueId,
      Boolean createAccessURLs) {
    try {
      return putDatasetOriginHealthData(UUID.randomUUID().toString(), content, contentType, catalogueId,
          createAccessURLs, false);
    } catch (Exception e) {
      return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
    }
  }

  @Override
  public Future<Void> deleteDataset(String id) {
    return delete(DCATAPUriSchema.createForDataset(id));
  }

  @Override
  public Future<Void> deleteDatasetOrigin(String originId, String catalogueId) {
    return catalogueManager.exist(catalogueId)
        .compose(exists -> {
          if (exists == Boolean.TRUE) {
            return datasetManager.identify(originId, catalogueId);
          } else {
            return Future.failedFuture(new ServiceException(404, "Catalogue not found"));
          }
        })
        .compose(pair -> delete(DCATAPUriSchema.parseUriRef(pair.getFirst().getURI())))
        .recover(cause -> Future.failedFuture(mapCause(cause)));
  }

  private Future<Void> delete(DCATAPUriRef schema) {
    return datasetManager.deleteGraph(schema.getDatasetGraphName())
        .compose(v -> datasetManager.catalogue(schema.getDatasetUriRef()))
        .compose(catalogue -> catalogueManager.removeDatasetEntry(catalogue.getURI(),
            schema.getDatasetUriRef()))
        .map(v -> {
          if (indexService != null) {
            indexService.deleteDataset(schema.getId(), ar -> {
              if (ar.failed()) {
                logger.warn("Delete index", ar.cause());
              }
            });
          }
          if (validationConfig.getBoolean(KEY_ENABLED, Defaults.VALIDATOR_ENABLED)
              == Boolean.TRUE) {
            tripleStore.deleteGraph(schema.getMetricsGraphName())
                .onFailure(cause -> logger.warn("Delete metrics graph", cause));

            shadowTripleStore.deleteGraph(schema.getHistoricMetricsGraphName())
                .onFailure(cause -> logger.warn("Delete metrics history graph", cause));
          }
          return null;
        });
  }

  @Override
  public Future<JsonObject> indexDataset(String datasetId, String catalogueId) {
    if (indexService == null) {
      return Future.failedFuture("Indexing service disabled");
    }
    AtomicReference<JsonObject> catalogueInfo = new AtomicReference<>();
    String contentType = RDFMimeTypes.NTRIPLES;

    DCATAPUriRef datasetUriRef = DCATAPUriSchema.createForDataset(datasetId);

    return datasetManager.existGraph(datasetUriRef.getGraphName())
        .compose(exists -> {
          if (!exists) {
            return Future.failedFuture(new ServiceException(404, "Dataset not found"));
          } else {
            return catalogueInfo(catalogueId);
          }
        })
        .compose(info -> {
          catalogueInfo.set(info);
          return datasetManager.getGraph(datasetUriRef.getGraphName());
        })
        .compose(dataset -> {
          DatasetHelper helper;
          try {
            helper = DatasetHelper.create(dataset);
          } catch (Exception e) {
            logger.error("Error parsing Dataset", e);
            return Future.failedFuture(new ServiceException(400,
                "Could not decode body with Content-Type " + contentType + ": " + e.getMessage()));
          }
          JsonObject indexObject = Indexing.indexingDataset(
              helper.resource(),
              helper.recordResource(),
              catalogueId,
              catalogueInfo.get().getString("langCode"));
          return Future.future(promise -> indexService.addDatasetPut(indexObject, ir -> {
            if (ir.succeeded()) {
              promise.complete(ir.result());
            } else {
              // hub-search may return 404 during an internal GET before creating the dataset.
              // Treat this as a success — the dataset was still indexed correctly.
              String causeMsg = ir.cause() != null ? ir.cause().getMessage() : "";
              if ("Not Found".equals(causeMsg) || (ir.cause() instanceof ServiceException se && se.failureCode() == 404)) {
                logger.debug("Dataset {} indexed via create path in hub-search (404 expected on first index)", catalogueId);
                promise.complete(new JsonObject().put("status", "created"));
              } else {
                promise.fail(ir.cause());
              }
            }
          }));
        });
  }

  /**
   * Reindexes all datasets from Virtuoso to OpenSearch.
   * <p>
   * This method retrieves all datasets stored in Virtuoso and sends them to the indexing service
   * (hub-search/OpenSearch) for reindexing. The operation is performed asynchronously using Vert.x
   * Futures to ensure non-blocking behavior and optimal performance.
   * </p>
   * <p>
   * The implementation follows Vert.x best practices:
   * <ul>
   *   <li>Non-blocking I/O: All operations use Vert.x Future API</li>
   *   <li>Resilience: Individual dataset failures don't stop the entire operation</li>
   *   <li>Atomic operations: Each dataset is processed independently to avoid race conditions</li>
   *   <li>Resource management: Uses immutable data structures where possible</li>
   * </ul>
   * </p>
   * <p>
   * Processing flow:
   * <ol>
   *   <li>Validates that the indexing service is available</li>
   *   <li>Retrieves all dataset references from Virtuoso</li>
   *   <li>For each dataset:
   *     <ul>
   *       <li>Fetches the complete RDF graph</li>
   *       <li>Parses the dataset using DatasetHelper</li>
   *       <li>Retrieves catalogue metadata (including language code)</li>
   *       <li>Generates index object using Indexing.indexingDataset()</li>
   *       <li>Sends to OpenSearch via indexService</li>
   *     </ul>
   *   </li>
   *   <li>Aggregates results and returns summary</li>
   * </ol>
   * </p>
   *
   * @return Future containing a JsonObject with:
   *         <ul>
   *           <li>"message" - Status message describing the operation result</li>
   *           <li>"totalDatasets" - Total number of datasets processed</li>
   *           <li>"successCount" - Number of successfully indexed datasets (optional)</li>
   *           <li>"failureCount" - Number of failed datasets (optional)</li>
   *         </ul>
   *         The Future fails with ServiceException if:
   *         <ul>
   *           <li>Indexing service is disabled (500)</li>
   *           <li>Fatal error occurs during dataset retrieval (500)</li>
   *         </ul>
   */
  @Override
  public Future<JsonObject> reindexAllDatasets() {
    // Validate indexing service availability early
    if (indexService == null) {
      logger.error("Reindex operation failed: Indexing service is not available");
      return Future.failedFuture(new ServiceException(500, "Indexing service disabled"));
    }

    logger.info("Starting reindex operation for all datasets");

    // Retrieve all dataset references from Virtuoso
    // Note: This is a read-only operation, safe for concurrent execution
    return datasetManager.list(0, Integer.MAX_VALUE)
        .compose(datasetRefs -> {
          final int totalDatasets = datasetRefs.size();
          logger.info("Found {} datasets to reindex", totalDatasets);

          // Early return if no datasets exist
          if (totalDatasets == 0) {
            logger.info("No datasets found in repository");
            return Future.succeededFuture(new JsonObject()
                .put("message", "No datasets found to reindex")
                .put("totalDatasets", 0)
                .put("successCount", 0)
                .put("failureCount", 0));
          }

          // Thread-safe counters for success/failure tracking
          // AtomicInteger ensures thread-safety without explicit synchronization
          final java.util.concurrent.atomic.AtomicInteger successCount =
              new java.util.concurrent.atomic.AtomicInteger(0);
          final java.util.concurrent.atomic.AtomicInteger failureCount =
              new java.util.concurrent.atomic.AtomicInteger(0);

          // Build list of futures for parallel processing
          // Each future represents an independent dataset indexing operation
          // This approach prevents blocking and enables concurrent processing
          List<Future<Void>> indexFutures = new ArrayList<>(totalDatasets);

          for (DCATAPUriRef datasetRef : datasetRefs) {
            // Create a separate future for each dataset to ensure isolation
            // This prevents race conditions and ensures each dataset is processed independently
            Future<Void> indexFuture = indexSingleDataset(datasetRef, successCount, failureCount);
            indexFutures.add(indexFuture);
          }

          // Use Future.all() to wait for all operations to complete
          // This is non-blocking and follows Vert.x async patterns
          // Individual failures won't cause the composite future to fail
          return Future.all(indexFutures)
              .map(compositeFuture -> {
                final int successTotal = successCount.get();
                final int failureTotal = failureCount.get();

                logger.info("Reindex operation completed: {} total, {} succeeded, {} failed",
                    totalDatasets, successTotal, failureTotal);

                return new JsonObject()
                    .put("message", "Reindexing completed")
                    .put("totalDatasets", totalDatasets)
                    .put("successCount", successTotal)
                    .put("failureCount", failureTotal);
              });
        })
        .recover(error -> {
          // Catch-all error handler for fatal failures (e.g., database connection issues)
          logger.error("Fatal error during reindex operation: {}", error.getMessage(), error);
          return Future.failedFuture(new ServiceException(500,
              "Error during reindex operation: " + error.getMessage()));
        });
  }

  /**
   * Indexes a single dataset to OpenSearch.
   * <p>
   * This private helper method encapsulates the indexing logic for a single dataset,
   * ensuring proper error handling and isolation between datasets. The method follows
   * Vert.x best practices by returning a Future and avoiding blocking operations.
   * </p>
   * <p>
   * Thread-safety considerations:
   * <ul>
   *   <li>All operations are async and non-blocking</li>
   *   <li>AtomicInteger counters ensure thread-safe incrementing</li>
   *   <li>No shared mutable state between invocations</li>
   *   <li>Each dataset processing is isolated and independent</li>
   * </ul>
   * </p>
   *
   * @param datasetRef The dataset reference containing ID and graph name
   * @param successCount Thread-safe counter for successful indexing operations
   * @param failureCount Thread-safe counter for failed indexing operations
   * @return Future<Void> that completes when indexing finishes (success or failure)
   *         This future always succeeds to prevent one dataset failure from affecting others
   */
  private Future<Void> indexSingleDataset(
      DCATAPUriRef datasetRef,
      java.util.concurrent.atomic.AtomicInteger successCount,
      java.util.concurrent.atomic.AtomicInteger failureCount) {

    final String datasetId = datasetRef.getId();

    // First, query Virtuoso to find which catalogue this dataset belongs to
    // This is necessary because DatasetHelper.create(Model) doesn't automatically set catalogueId
    return datasetManager.catalogue(datasetRef.getUriRef())
        .<Void>compose(catalogueResource -> {
          // Extract catalogue ID from the catalogue resource URI
          final String catalogueId = DCATAPUriSchema.parseUriRef(catalogueResource.getURI()).getId();

          // Guard against null catalogueId - this should not happen if datasets always have a catalogue
          if (catalogueId == null || catalogueId.isBlank()) {
            logger.warn("Could not extract catalogueId for dataset {}, skipping indexing", datasetId);
            failureCount.incrementAndGet();
            return Future.<Void>succeededFuture();
          }

          // Log to verify correct catalogue ID is retrieved for this specific dataset
          logger.debug("Dataset {} belongs to catalogue {}", datasetId, catalogueId);

          // Now fetch the dataset graph from Virtuoso
          return datasetManager.getGraph(datasetRef.getGraphName())
              .<Void>compose(dataset -> {
                // Parse dataset into helper object
                DatasetHelper helper;
                try {
                  helper = DatasetHelper.create(dataset);
                } catch (Exception e) {
                  logger.error("Failed to parse dataset {}: {}", datasetId, e.getMessage(), e);
                  failureCount.incrementAndGet();
                  // Return succeeded future to continue processing other datasets
                  return Future.<Void>succeededFuture();
                }

                // Retrieve catalogue information (with caching for performance)
                return catalogueInfo(catalogueId)
                    .<Void>compose(catalogueInfo -> {
                      // Generate index object using the specified indexing method
                      // This is a synchronous operation but typically fast
                      JsonObject indexObject;
                      try {
                        indexObject = Indexing.indexingDataset(
                            helper.resource(),
                            helper.recordResource(),
                            catalogueId,
                            catalogueInfo.getString("langCode", "en"));

                        // Log to verify the catalogue ID is correctly included in the index object
                        logger.debug("Indexing dataset {} with catalogue {} to OpenSearch",
                            datasetId, catalogueId);
                      } catch (Exception e) {
                        logger.error("Failed to generate index object for dataset {}: {}",
                            datasetId, e.getMessage(), e);
                        failureCount.incrementAndGet();
                        return Future.<Void>succeededFuture();
                      }

                      // Send to OpenSearch asynchronously
                      // Using Future.future() to wrap the callback-based API
                      return Future.<Void>future(promise ->
                          indexService.addDatasetPut(indexObject, ir -> {
                            if (ir.succeeded()) {
                              logger.debug("Successfully indexed dataset: {}", datasetId);
                              successCount.incrementAndGet();
                              promise.complete();
                            } else {
                              // hub-search may return 404 during an internal GET check before
                              // creating the dataset — treat this as a successful "create" path
                              String causeMsg = ir.cause() != null ? ir.cause().getMessage() : "";
                              if ("Not Found".equals(causeMsg) || (ir.cause() instanceof ServiceException se && se.failureCode() == 404)) {
                                logger.debug("Dataset {} indexed via create path (404 is expected on first index)", datasetId);
                                successCount.incrementAndGet();
                              } else {
                                logger.error("Failed to index dataset {} to OpenSearch: {}",
                                    datasetId, causeMsg);
                                failureCount.incrementAndGet();
                              }
                              // Complete (not fail) in either case to continue processing other datasets
                              promise.complete();
                            }
                          })
                      );
                    })
                    .<Void>recover(error -> {
                      // Handle any errors in the dataset processing chain
                      logger.error("Error processing dataset {}: {}", datasetId, error.getMessage());
                      failureCount.incrementAndGet();
                      // Return succeeded future to continue with other datasets
                      return Future.<Void>succeededFuture();
                    });
              })
              .<Void>recover(error -> {
                // Handle errors in dataset graph retrieval
                logger.error("Failed to retrieve graph for dataset {}: {}", datasetId, error.getMessage());
                failureCount.incrementAndGet();
                return Future.<Void>succeededFuture();
              });
        })
        .<Void>recover(error -> {
          // Handle errors in catalogue lookup
          // This is expected if dataset has no catalogue reference
          if (error instanceof TripleStoreException tse && tse.getCode() == 404) {
            logger.warn("Dataset {} has no catalogue reference in Virtuoso, skipping indexing", datasetId);
          } else {
            logger.error("Failed to find catalogue for dataset {}: {}", datasetId, error.getMessage());
          }
          failureCount.incrementAndGet();
          return Future.<Void>succeededFuture();
        })
        .mapEmpty(); // Convert to Future<Void>
  }

  @Override
  public Future<JsonObject> getDataUploadInformation(String datasetId, String catalogueId,
      String resultDataset) {
    try {
      DatasetHelper helper = DatasetHelper.create(resultDataset, Lang.NTRIPLES.getHeaderString());
      JsonArray uploadResponse = dataUploadConnector.getResponse(helper);
      JsonObject result = new JsonObject();
      result.put("status", "success");
      result.put("distributions", uploadResponse);
      return Future.succeededFuture(result);
    } catch (Exception e) {
      logger.error("Error getting data upload information", e);
      return Future.failedFuture(new ServiceException(500, "Internal Server Error"));
    }
  }

  private Future<List<JsonObject>> getRecordHash(DatasetHelper helper) {
    // Use pre-loaded template to avoid race condition with async SparqlQueryPool
    if (recordQueryTemplate == null) {
      return Future.failedFuture(new ServiceException(500, "Missing recordQuery template"));
    }
    String query = String.format(recordQueryTemplate,
        helper.catalogueGraphName(),
        helper.catalogueUriRef(),
        helper.originId()
    );

    return tripleStore.select(query)
        .map(resultSet -> {
          List<JsonObject> records = new ArrayList<>();
          while (resultSet.hasNext()) {
            QuerySolution solution = resultSet.next();
            JsonObject info = new JsonObject().put("recordUri",
                solution.getResource("record").getURI());
            if (solution.contains("hash")) {
              info.put("hash", solution.getLiteral("hash").getLexicalForm());
            }
            records.add(info);
          }
          return records;
        });
  }

  private Future<JsonObject> store(DatasetHelper helper) {
    boolean clearGeoData =
        clearGeoDataCatalogues.contains(helper.catalogueId()) || clearGeoDataCatalogues.contains(
            "*");
    return datasetManager.setGraph(helper.graphName(), helper.model(), clearGeoData)
        .map(result -> {
          switch (result) {
            case "created" -> {
              return new JsonObject()
                  .put("status", "created")
                  .put("id", helper.piveauId())
                  .put("dataset", helper.stringify(Lang.NTRIPLES))
                  .put(HttpHeaders.LOCATION.toString(), helper.uriRef());
            }
            case "updated" -> {
              return new JsonObject().put("status", "updated")
                  .put("id", helper.piveauId());
            }
            default -> throw new ServiceException(500, result);
          }
        });
  }

  private Future<DatasetHelper> index(DatasetHelper helper) {
    PiveauLogger log = piveauContext.extend(helper.piveauId()).log();
    return Future.future(promise -> {
      try {
        JsonObject indexMessage = Indexing.indexingDataset(helper.resource(),
            helper.recordResource(), helper.catalogueId(),
            helper.getCatalogueInfo().getString("langCode", "en"));
        if (log.isTraceEnabled()) {
          log.trace("indexing dataset: {}", indexMessage.encodePrettily());
        }
        indexService.addDatasetPut(indexMessage, ar -> {
          if (ar.succeeded()) {
            promise.complete(helper);
          } else {
            promise.fail(ar.cause());
          }
        });
      } catch (Exception e) {
        log.error("indexing dataset failed", e);
        promise.fail(new ServiceException(500, e.getMessage()));
      }
    });
  }

  private void systemPipes(DatasetHelper helper) {
    String metricsPipe = validationConfig.getString("metricsPipeName", Defaults.METRICS_PIPE);
    if (launcher.isPipeAvailable(metricsPipe)) {
      JsonObject dataInfo = new JsonObject()
          .put("originalId", helper.originId())
          .put("catalogue", helper.catalogueId())
          .put("identifier", helper.piveauId())
          .put("source", helper.getCatalogueInfo().getString("sourceType"));

      Model model = HubRepo.extractDatasetModel(helper.model());
      Dataset dataset = DatasetFactory.create(model);
      launcher.runPipeWithData(metricsPipe, Piveau.presentAs(dataset, Lang.TRIG), RDFMimeTypes.TRIG,
          dataInfo, new JsonObject(), null);
    }
  }

  private Future<DatasetHelper> createDataset(DatasetHelper datasetHelper,
      boolean createAccessURLs) {
    return findPiveauId(datasetHelper.piveauId(), 0)
        .compose(id -> {
          datasetHelper.init(id);

          if (createAccessURLs) {
            datasetHelper.setAccessURLs(dataUploadConnector);
          }

          catalogueManager.addDatasetEntry(
                  datasetHelper.catalogueGraphName(),
                  datasetHelper.catalogueUriRef(),
                  datasetHelper.uriRef(),
                  datasetHelper.recordUriRef())
              .onFailure(cause -> {
                logger.error("Add catalogue entry for {}", datasetHelper.piveauId(), cause);
                if (cause instanceof TripleStoreException te) {
                  if (!te.getQuery().isBlank()) {
                    logger.error("Query: {}", te.getQuery());
                  }
                }
              });

          if (translationService != null) {
            logger.info("Initialize translation process for dataset {}", datasetHelper.piveauId());
            return Future.<DatasetHelper>future(
                promise -> translationService.initializeTranslationProcess(datasetHelper, null,
                    false, promise)).otherwise(datasetHelper);
          } else {
            logger.info("Translation service not available, skipping translation process for dataset {}",
                datasetHelper.piveauId());
            return Future.succeededFuture(datasetHelper);
          }
        });
  }

  private Future<DatasetHelper> updateDataset(DatasetHelper datasetHelper, String recordUriRef) {
    return datasetManager.getGraph(DCATAPUriSchema.parseUriRef(recordUriRef).getDatasetGraphName())
        .compose(model -> {
          datasetHelper.update(model, recordUriRef);

          if (translationService != null) {
            DatasetHelper helper = DatasetHelper.create(Piveau.presentAs(model, Lang.NTRIPLES),
                Lang.NTRIPLES.getContentType().getContentTypeStr());
            return Future.<DatasetHelper>future(
                    promise -> translationService.initializeTranslationProcess(datasetHelper, helper,
                        false, promise))
                .otherwise(datasetHelper);
          } else {
            return Future.succeededFuture(datasetHelper);
          }
        })
        .recover(cause ->
            Future.failedFuture(new ServiceException(500, cause.getMessage(), new JsonObject()
                .put("recordUriRef", recordUriRef)
                .put("datasetHelper", datasetHelper.toJson()))));
  }

  private Future<String> findPiveauId(String id, int counter) {
    AtomicReference<String> checkId = new AtomicReference<>();
    if (counter > 0) {
      checkId.set(id + "~~" + counter);
    } else {
      checkId.set(id);
    }
    // Use pre-loaded template to avoid race condition with async SparqlQueryPool
    if (askPiveauIdQueryTemplate == null) {
      return Future.failedFuture(new ServiceException(500, "Missing askPiveauId template"));
    }
    String askQuery = String.format(askPiveauIdQueryTemplate,
        DCATAPUriSchema.createForDataset(checkId.get()).getUriRef());
    return tripleStore.ask(askQuery)
        .compose(exist -> {
          if (exist == Boolean.TRUE) {
            return findPiveauId(id, counter + 1);
          } else {
            return Future.succeededFuture(checkId.get());
          }
        });
  }

  private Future<JsonObject> catalogueInfo(String catalogueId) {
    // Guard against null catalogueId — EhcacheBase.containsKey throws NPE for null keys
    if (catalogueId == null || catalogueId.isBlank()) {
      logger.error("catalogueInfo called with null or blank catalogueId");
      return Future.failedFuture(new ServiceException(404, "Catalogue not found"));
    }
    if (cache.containsKey(catalogueId)) {
      logger.debug("Using cached catalogue info for catalogue {}", catalogueId);
      return Future.succeededFuture(cache.get(catalogueId));
    }

    logger.debug("Querying Virtuoso for catalogue info: {}", catalogueId);
    DCATAPUriRef catalogueUriRef = DCATAPUriSchema.createForCatalogue(catalogueId);

    // Use pre-loaded template to avoid race condition with async SparqlQueryPool
    if (catalogueQueryTemplate == null) {
      return Future.failedFuture(new ServiceException(500, "Missing catalogueQuery template"));
    }
    String query = String.format(catalogueQueryTemplate,
        catalogueUriRef.getGraphName(), catalogueUriRef.getUriRef());

    return tripleStore.select(query)
        .map(resultSet -> {
          if (resultSet.hasNext()) {
            JsonObject result = new JsonObject();
            QuerySolution solution = resultSet.next();
            result.put("title", solution.get("title").asLiteral().getValue());
            if (solution.contains("lang")) {
              Concept concept = Languages.INSTANCE.getConcept(solution.getResource("lang"));
              if (concept != null) {
                String langCode = Languages.INSTANCE.iso6391Code(concept);
                if (langCode == null) {
                  langCode = Languages.INSTANCE.tedCode(concept);
                }
                if (langCode != null) {
                  result.put("langCode", langCode.toLowerCase());
                }
              }
            }
            if (solution.contains("type")) {
              result.put("type", solution.getLiteral("type").getLexicalForm());
            }
            if (solution.contains("super")) {
              result.put("superCatalogue", solution.getResource("super").getURI());
            }
            if (solution.contains(KEY_VISIBILITY)) {
              result.put(KEY_VISIBILITY, solution.getResource(KEY_VISIBILITY).getLocalName());
            }
            if (result.isEmpty()) {
              throw new ServiceException(404, "Catalogue not found");
            } else {
              // Cache the result using putIfAbsent to avoid race conditions
              // This ensures that if multiple threads try to cache the same catalogue,
              // only one will succeed, and all will use the same cached value
              cache.putIfAbsent(catalogueId, result);
              logger.debug("Cached catalogue info for catalogue {}", catalogueId);
              return result;
            }
          } else {
            throw new ServiceException(404, "Catalogue not found");
          }
        });
  }

  private ServiceException mapCause(Throwable cause) {
    if (cause instanceof ServiceException serviceException) {
      return serviceException;
    } else if (cause instanceof DoesNotExistException doesNotExistException) {
      return new ServiceException(422,
          "Class expected but not found in data: " + doesNotExistException.getMessage());
    } else if (cause instanceof RiotException riotException) {
      return new ServiceException(400, "RDF parsing exception: " + riotException.getMessage());
    } else if (cause.getMessage().startsWith("Not found")) {
      return new ServiceException(404, "Dataset not found");
    } else if (cause instanceof TripleStoreException tripleStoreException) {
      return new ServiceException(tripleStoreException.getCode(), tripleStoreException.getMessage(),
          new JsonObject().put("query", tripleStoreException.getQuery()));
    } else {
      return new ServiceException(500, cause.getMessage());
    }
  }

  private void prepareDataset(Model dataset) {
    changeVirtuType(dataset);

    Resource recordResource = Piveau.findRecordAsResource(dataset);
    if (recordResource != null) {
      Model recordModel = Piveau.extractAsModel(recordResource);
      dataset.remove(recordModel);
    }

    Prefixes.setNsPrefixesFiltered(dataset);
  }

  private void removeDuplicates(DatasetHelper datasetHelper, List<JsonObject> recordHashes) {
    Iterator<JsonObject> it = recordHashes.iterator();
    while (it.hasNext()) {
      JsonObject recordHash = it.next();
      DCATAPUriRef uriRef = DCATAPUriSchema.parseUriRef(recordHash.getString("recordUri"));

      if (!uriRef.getId().equals(Piveau.asNormalized(datasetHelper.originId()))) {
        delete(uriRef);
        it.remove();
      }
    }
  }

  private void dgaValidation(DatasetHelper datasetHelper) {
    JsonObject catalogueInfo = datasetHelper.getCatalogueInfo();
    if (catalogueInfo.containsKey("superCatalogue")
        && catalogueInfo.getString("superCatalogue").equals(DGA_SUPER_CATALOGUE)) {
      Resource datasetResource = datasetHelper.model()
          .listResourcesWithProperty(RDF.type, DCAT.Dataset).toList().get(0);
      JsonObject report = AddOns.validationReport(datasetResource);
      if (!report.isEmpty()) {
        // log violations here
        dgaContext.extend(datasetHelper.catalogueId() + "/" + datasetHelper.originId()).log()
            .warn(report.encode());
      }
    }
  }

}
