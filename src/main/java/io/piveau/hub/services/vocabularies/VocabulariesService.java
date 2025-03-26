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

import io.piveau.dcatap.TripleStore;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface VocabulariesService {
    String SERVICE_ADDRESS = "io.piveau.hub.vocabularies.queue";

    static VocabulariesService create(Vertx vertx, TripleStore tripleStore, JsonObject config, Handler<AsyncResult<VocabulariesService>> readyHandler) {
        return new VocabulariesServiceImpl(vertx, tripleStore, config, readyHandler);
    }

    static VocabulariesService createProxy(Vertx vertx, String address) {
        DeliveryOptions options = new DeliveryOptions().setSendTimeout(300000);
        return new VocabulariesServiceVertxEBProxy(vertx, address, options);
    }

    Future<String> listVocabularies(String acceptType, String valueType, Integer limit, Integer offset);

    Future<String> getVocabulary(String vocabularyId, String acceptType);

    Future<JsonArray> indexVocabulary(String vocabularyId);

    Future<String> putVocabulary(String vocabularyId, String vocabularyUri, String contentType, String content);

    Future<String> putVocabularyFile(String vocabularyId, String vocabularyUri, String contentType, String file);

    Future<String> putVocabularyChunk(String vocabularyId, String vocabularyUri, String contentType,
                                                 String chunk, String hash, int chunkId, int numberOfChunks);

    Future<Void> deleteVocabulary(String vocabularyId);
}
