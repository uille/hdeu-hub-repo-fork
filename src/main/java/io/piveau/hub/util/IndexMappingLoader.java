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
package io.piveau.hub.util;

import io.piveau.hub.indexing.Indexing;
import io.piveau.profile.*;
import io.piveau.rdf.Piveau;
import io.piveau.vocabularies.vocabulary.DCATAP;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.*;

import static io.piveau.profile.Indexer.produceIndexInstructionsFromResource;

public class IndexMappingLoader {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Vertx vertx;
    private final JsonObject config;
    private final PiveauProfile piveauProfile;

    private final String defaultShape = "shapes/dcat-ap.hub.shapes.ttl";

    public IndexMappingLoader(Vertx vertx, JsonObject config, PiveauProfile piveauProfile) {
        this.vertx = vertx;
        this.config = config;
        this.piveauProfile = piveauProfile;
    }
    public Future<Void> load() {
        Promise<Void> promise = Promise.promise();

        AtomicReference<JsonObject> datasetInstr = new AtomicReference<>();
        AtomicReference<JsonObject> catalogInstr = new AtomicReference<>();
        findShapes().onSuccess(defaulModel -> {
            if (piveauProfile != null) {
                if (piveauProfile.datasetIsSet()) {
                    datasetInstr.set(produceIndexInstructionsFromResource(piveauProfile.dataset.getShaclShape().getResource(piveauProfile.dataset.getShapeUri())));
                    log.info("Loaded dataset schema from profile");
                } else {
                    datasetInstr.set(produceIndexInstructionsFromResource(defaulModel.getResource(DCATAP.DatasetShape.getURI())));
                    log.info("Loaded dataset schema from config folder");
                }
                if (piveauProfile.catalogIsSet()) {
                    catalogInstr.set(produceIndexInstructionsFromResource(piveauProfile.catalog.getShaclShape().getResource(piveauProfile.catalog.getShapeUri())));
                    log.info("Loaded catalog schema from profile");
                } else {
                    catalogInstr.set(produceIndexInstructionsFromResource(defaulModel.getResource(DCATAP.CatalogShape.getURI())));
                    log.info("Loaded catalog schema from config folder");
                }
            } else {
                datasetInstr.set(produceIndexInstructionsFromResource(defaulModel.getResource(DCATAP.DatasetShape.getURI())));
                log.info("Loaded dataset schema from config folder");
                catalogInstr.set(produceIndexInstructionsFromResource(defaulModel.getResource(DCATAP.CatalogShape.getURI())));
                log.info("Loaded catalog schema from config folder");
            }
            Indexing.init(catalogInstr.get(), datasetInstr.get());
            promise.complete();
        }).onFailure(error -> promise.fail(error.getMessage()));
        return promise.future();
    }

    private Future<Model> findShapes() {
        Promise<Model> promise = Promise.promise();
        vertx.fileSystem().readFile("conf/shapes/dcat-ap.hub.shapes.ttl")
                .onSuccess(file -> {
                    log.info("Loaded overwrite schema");
                    Model catModel = Piveau.toModel(file.getBytes(), Lang.TURTLE);
                    promise.complete(catModel);
                })
                .onFailure(ar -> vertx.fileSystem().readFile("shapes/dcat-ap.hub.shapes.ttl").onSuccess(file -> {
                    Model catModel = Piveau.toModel(file.getBytes(), Lang.TURTLE);
                    promise.complete(catModel);
                }).onFailure(promise::fail));
        return promise.future();
    }
}

