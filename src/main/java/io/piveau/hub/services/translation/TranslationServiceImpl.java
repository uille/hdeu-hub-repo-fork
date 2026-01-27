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
package io.piveau.hub.services.translation;

import static io.piveau.hub.services.translation.TranslationServiceUtils.callbackParameters;
import static io.piveau.hub.services.translation.TranslationServiceUtils.translationLanguages;

import io.piveau.dcatap.DCATAPUriRef;
import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.dcatap.DatasetManager;
import io.piveau.dcatap.TripleStore;
import io.piveau.hub.Constants;
import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.hub.indexing.Indexing;
import io.piveau.hub.services.index.IndexService;
import io.piveau.json.ConfigHelper;
import io.piveau.log.PiveauLogger;
import io.piveau.utils.PiveauContext;
import io.piveau.vocabularies.vocabulary.EDP;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TranslationServiceImpl implements TranslationService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final DatasetManager datasetManager;

    private IndexService indexService;

    private final HttpRequest<Buffer> translationServiceRequest;

    private final PiveauContext moduleContext;

    TranslationServiceImpl(
            Vertx vertx,
            WebClient client,
            JsonObject config,
            TripleStore tripleStore,
            Handler<AsyncResult<TranslationService>> readyHandler) {

        ConfigHelper configHelper = ConfigHelper.forConfig(config);

        JsonObject translationConfig = configHelper.forceJsonObject(Constants.ENV_PIVEAU_TRANSLATION_SERVICE);
        translationLanguages = translationConfig.getJsonArray("accepted_languages", new JsonArray())
                .stream()
                .map(Object::toString).toList();

        translationServiceRequest = client.postAbs(translationConfig.getString("translation_service_url", "https://example.com/translation-service/"))
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
            .putHeader("X-API-Key", translationConfig.getString("api_key", ""))
            .expect(ResponsePredicate.SC_SUCCESS)
            .timeout(10000);

        final String apiKey = config.getJsonObject(Constants.ENV_PIVEAU_HUB_API_KEYS, new JsonObject()).stream()
                .filter(entry -> entry.getValue() instanceof JsonArray)
                .filter(entry -> ((JsonArray) entry.getValue()).contains("*"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(config.getString(Constants.ENV_PIVEAU_HUB_API_KEY));

        callbackParameters = new JsonObject()
                .put("url", translationConfig.getString("callback_url", "http://localhost:8080/translation"))
                .put("method", HttpMethod.POST.name())
                .put("headers", new JsonObject()
                        .put("X-API-Key", apiKey));

        datasetManager = tripleStore.getDatasetManager();

        JsonObject indexConfig = configHelper.forceJsonObject(Constants.ENV_PIVEAU_HUB_SEARCH_SERVICE);
        if (indexConfig.getBoolean("enabled", false)) {
            indexService = IndexService.createProxy(vertx, IndexService.SERVICE_ADDRESS, IndexService.DEFAULT_TIMEOUT);
        }

        moduleContext = new PiveauContext("hub", "translation");

        readyHandler.handle(Future.succeededFuture(this));
    }

    /**
     * Extract title and description from Dataset and its Distributions and send them to the translation service.
     * <p>
     * If the Dataset is only an update of an old Dataset with no titles or descriptions changed, nothing will be send to the translation service.
     *
     * @param helper    the new Dataset with new and not yet translated fields
     * @param oldHelper the optional old Dataset with already translated fields, should be `null`, if there is no old Dataset
     * @return this {@link TranslationService} for a fluent usage
     */
    @Override
    public TranslationService initializeTranslationProcess(DatasetHelper helper, DatasetHelper oldHelper, Boolean force, Handler<AsyncResult<DatasetHelper>> handler) {
        PiveauLogger logger = moduleContext.extend(helper.piveauId()).log();
        try {
            final JsonObject translationRequest = TranslationServiceUtils.buildTranslationRequest(helper, oldHelper);
            if (!translationRequest.isEmpty()) {
                initTranslationProcess(
                    helper.recordResource(),
                    helper.getCatalogueInfo().getString("langCode", "en")
                );
                if (logger.isDebugEnabled()) {
                    logger.debug("Translation request: {}", translationRequest.encodePrettily());
                }
                translationServiceRequest.sendJsonObject(translationRequest)
                        .onSuccess(response -> logger.info("Translation initialized."))
                        .onFailure(cause -> logger.error("Translation initialization failed.", cause));
            }
            handler.handle(Future.succeededFuture(helper));
        } catch (Exception e) {
            logger.error("Translation initialization failed.", e);
            handler.handle(Future.failedFuture(e));
        }
        return this;
    }

  /**
   * Receives a translation callback and applies translated literals to the dataset RDF graph.
   *
   * <p><strong>Important concurrency guarantees:</strong>
   * <ul>
   *   <li>Translation callbacks may arrive long after the translation request was initiated.</li>
   *   <li>During that time, the dataset may have been deleted by a user.</li>
   *   <li>This method must therefore be resilient against late callbacks and must not
   *       recreate deleted datasets or re-index stale data.</li>
   * </ul>
   *
   * <p>To ensure correctness, this method:
   * <ol>
   *   <li>Checks whether the dataset graph still exists before doing any work.</li>
   *   <li>Uses {@code ensureExists(uriRef)} to verify that the dataset and record
   *       are still considered present by the DatasetManager.</li>
   *   <li>Re-checks graph existence immediately before writing, to prevent
   *       race conditions where a dataset is deleted while the translation
   *       is being applied.</li>
   *   <li>Silently ignores translation callbacks for deleted datasets
   *       (returns status {@code ignored}).</li>
   * </ol>
   *
   * <p>If all checks pass, the translated literals are applied, the catalog
   * record is marked as translation-complete, the graph is persisted, and
   * the dataset is re-indexed in OpenSearch.
   */
  @Override
  public TranslationService receiveTranslation(
      JsonObject translation,
      Handler<AsyncResult<JsonObject>> handler) {

    try {
      DCATAPUriRef uriRef = DCATAPUriSchema.parseUriRef(translation.getString("id"));
      final String graphName = uriRef.getDatasetGraphName();

      PiveauContext resourceContext = moduleContext.extend(uriRef.getId());
      if (resourceContext.log().isDebugEnabled()) {
        resourceContext.log().debug("Incoming translation: {}", translation.encodePrettily());
      }

      // First guard: if the dataset graph does not exist at all, the dataset
      // was already deleted before this callback arrived.
      datasetManager.existGraph(graphName)
          .compose(exists -> {
            if (!exists) {
              resourceContext.log().info(
                  "Translation received but dataset graph does not exist (likely deleted). Ignoring. id={}",
                  translation.getString("id")
              );
              return Future.succeededFuture((Model) null);
            }

            // Strong semantic check: ensure the dataset/record still exists
            // according to DatasetManager semantics.
            // If this fails, the dataset has been deleted in the meantime.
            return datasetManager.ensureExists(uriRef)
                .compose(v -> datasetManager.getGraph(graphName))
                .compose(model -> {
                  // Defensive guard: empty or missing model should never be written back,
                  // otherwise we risk recreating a deleted dataset.
                  if (model == null || model.isEmpty()) {
                    resourceContext.log().info(
                        "Translation received but dataset graph is empty (likely deleted). Ignoring. id={}",
                        translation.getString("id")
                    );
                    return Future.succeededFuture((Model) null);
                  }

                  // Apply translated literals to the RDF model
                  TranslationServiceUtils.applyTranslations(
                      model,
                      translation.getJsonObject("dict", new JsonObject())
                  );

                  // Mark the catalog record as translation completed
                  Resource record = model.getResource(uriRef.getRecordUriRef());
                  completeTranslationProcess(record);

                  // Final race-condition guard:
                  // the dataset may have been deleted after we loaded the graph
                  // but before we attempt to write it back.
                  return datasetManager.existGraph(graphName)
                      .compose(stillExists -> {
                        if (!stillExists) {
                          resourceContext.log().info(
                              "Dataset graph deleted before translation write. Ignoring. id={}",
                              translation.getString("id")
                          );
                          return Future.succeededFuture((Model) null);
                        }

                        // Safe to persist updated graph
                        return datasetManager
                            .setGraph(graphName, model, false)
                            .map(model);
                      });
                })
                // Any failure in ensureExists/getGraph is treated as "dataset no longer exists"
                .recover(err -> {
                  resourceContext.log().info(
                      "Translation received but dataset no longer exists. Ignoring. id={}",
                      translation.getString("id")
                  );
                  return Future.succeededFuture((Model) null);
                });
          })
          .onSuccess(model -> {
            // Ignored case (dataset deleted or missing)
            if (model == null) {
              handler.handle(
                  Future.succeededFuture(new JsonObject().put("status", "ignored"))
              );
              return;
            }

            // Index the dataset only after a successful graph update
            if (indexService != null) {
              Resource record = model.getResource(uriRef.getRecordUriRef());
              JsonObject data = translation.getJsonObject("data", new JsonObject());

              JsonObject index = Indexing.indexingDataset(
                  model.getResource(uriRef.getDatasetUriRef()),
                  record,
                  data.getString("catalogueId"),
                  data.getString("defaultLanguage")
              );

              indexService.addDatasetPut(index, ar -> {
                if (ar.succeeded()) {
                  log.debug("Dataset successfully indexed");
                } else {
                  log.error("Index dataset", ar.cause());
                }
              });
            }

            resourceContext.log().info("Translation stored");
            handler.handle(
                Future.succeededFuture(new JsonObject().put("status", "success"))
            );
          })
          .onFailure(cause -> {
            resourceContext.log().error("Translation", cause);
            handler.handle(Future.failedFuture(cause));
          });

    } catch (Exception e) {
      handler.handle(Future.failedFuture(e));
    }

    return this;
  }


    private void initTranslationProcess(Resource record, String sourceLang) {
        record.removeAll(EDP.originalLanguage);
        record.addProperty(EDP.originalLanguage, sourceLang);
        record.removeAll(EDP.transReceived);
        record.removeAll(EDP.transIssued);
        record.addProperty(EDP.transIssued, ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME), XSDDatatype.XSDdateTime);
        record.removeAll(EDP.transStatus);
        record.addProperty(EDP.transStatus, EDP.TransInProcess);
    }

    private void completeTranslationProcess(Resource record) {
        record.removeAll(EDP.transReceived);
        record.addProperty(EDP.transReceived, ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME), XSDDatatype.XSDdateTime);
        record.removeAll(EDP.transStatus);
        record.addProperty(EDP.transStatus, EDP.TransCompleted);

        record.removeAll(DCTerms.modified);
        record.addProperty(DCTerms.modified, ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME), XSDDatatype.XSDdateTime);
    }

}
