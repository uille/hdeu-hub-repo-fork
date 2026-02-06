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

import io.piveau.dcatap.TripleStore;
import io.piveau.hub.util.DataUploadConnector;
import io.piveau.pipe.PipeLauncher;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface DatasetsService {
    String SERVICE_ADDRESS = "io.piveau.hub.datasets.queue";

    static Future<DatasetsService> create(TripleStore tripleStore, TripleStore shadowTripleStore, DataUploadConnector dataUploadConnector, JsonObject config, PipeLauncher launcher, Vertx vertx) {
        return Future.future(promise -> new DatasetsServiceImpl(tripleStore, shadowTripleStore, dataUploadConnector, config, launcher, vertx, promise));
    }

    static DatasetsService createProxy(Vertx vertx, String address) {
        return new DatasetsServiceVertxEBProxy(vertx, address, new DeliveryOptions().setSendTimeout(240000));
    }

    Future<String> listDatasets(String acceptType, String valueType, Integer limit, Integer offset, Boolean hydra, String base, Boolean usePagedCollection);

    Future<String> listCatalogueDatasets(String acceptType, String valueType, String catalogueId, Integer limit, Integer offset);

    Future<String> getDatasetOrigin(String originId, String catalogueId, String acceptType);

    Future<String> getDataset(String id, String acceptType);

    Future<JsonObject> putDataset(String id, String content, String contentType);

    Future<JsonObject> putDatasetOrigin(String id, String content, String contentType, String catalogueId, Boolean upload);

    Future<JsonObject> putDatasetOriginHealthData(String id, String content, String contentType, String catalogueId, Boolean upload, boolean isDatasetUpdate);

    Future<JsonObject> postDataset(String content, String contentType, String catalogueId, Boolean createAccessURLs);

    Future<Void> deleteDatasetOrigin(String originId, String catalogueId);

    Future<Void> deleteDataset(String id);

    Future<String> getRecord(String id, String acceptType);

    Future<JsonObject> indexDataset(String datasetId, String catalogueId);

    /**
     * Reindexes all datasets from Virtuoso to OpenSearch.
     * <p>
     * This method performs a comprehensive reindexing operation that retrieves all datasets
     * stored in the Virtuoso triple store and sends them to the OpenSearch indexing service
     * for reindexing. The operation is executed asynchronously using Vert.x Future API to
     * ensure non-blocking behavior.
     * </p>
     * <p>
     * <b>Use Cases:</b>
     * <ul>
     *   <li>After index schema changes or updates</li>
     *   <li>Recovery from index corruption or data loss</li>
     *   <li>Synchronization after bulk data updates</li>
     *   <li>Migration to a new OpenSearch cluster</li>
     * </ul>
     * </p>
     * <p>
     * <b>Performance Characteristics:</b>
     * <ul>
     *   <li>Asynchronous and non-blocking</li>
     *   <li>Processes datasets concurrently where possible</li>
     *   <li>Execution time scales with number of datasets</li>
     *   <li>Individual failures don't stop the entire operation</li>
     * </ul>
     * </p>
     * <p>
     * <b>Error Handling:</b>
     * The method is resilient to individual dataset failures. If a single dataset fails
     * to index, the operation continues with remaining datasets and reports the failure
     * count in the result.
     * </p>
     * <p>
     * <b>Thread Safety:</b>
     * This method is thread-safe and can be called concurrently. However, running multiple
     * reindex operations simultaneously may cause resource contention and is not recommended.
     * </p>
     *
     * @return A Future containing a JsonObject with the following properties:
     *         <ul>
     *           <li><b>message</b> (String) - Human-readable status message</li>
     *           <li><b>totalDatasets</b> (Integer) - Total number of datasets processed</li>
     *           <li><b>successCount</b> (Integer) - Number of successfully indexed datasets</li>
     *           <li><b>failureCount</b> (Integer) - Number of datasets that failed to index</li>
     *         </ul>
     *         The Future fails with ServiceException(500) if:
     *         <ul>
     *           <li>The indexing service is not available or disabled</li>
     *           <li>A fatal error occurs during dataset retrieval from Virtuoso</li>
     *         </ul>
     *
     * @see #indexDataset(String, String) for indexing a single dataset
     */
    Future<JsonObject> reindexAllDatasets();

    Future<JsonObject> getDataUploadInformation(String datasetId, String catalogueId, String resultDataset);

}
