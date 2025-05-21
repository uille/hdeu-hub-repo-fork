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

    Future<JsonObject> getDataUploadInformation(String datasetId, String catalogueId, String resultDataset);

}
