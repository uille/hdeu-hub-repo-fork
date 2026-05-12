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
package io.piveau.hub;

import io.piveau.FeatureFlags;
import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.dcatap.TripleStore;
import io.piveau.hub.dataobjects.DatasetHelper;
import io.piveau.hub.ext.trust.TrustHandler;
import io.piveau.hub.handler.CatalogueHandler;
import io.piveau.hub.handler.DatasetDraftHandler;
import io.piveau.hub.handler.DatasetHandler;
import io.piveau.hub.handler.DistributionHandler;
import io.piveau.hub.handler.IdentifiersHandler;
import io.piveau.hub.handler.MetricHandler;
import io.piveau.hub.handler.ResourceHandler;
import io.piveau.hub.handler.TranslationServiceHandler;
import io.piveau.hub.handler.VocabularyHandler;
import io.piveau.hub.security.CatalogueIdentifierHandler;
import io.piveau.hub.security.CataloguePermissionHandler;
import io.piveau.hub.security.DatasetDraftPermissionHandler;
import io.piveau.hub.security.DatasetIdentifierHandler;
import io.piveau.hub.security.DatasetPermissionHandler;
import io.piveau.hub.security.KeyCloakServiceVerticle;
import io.piveau.hub.services.DatasetHelperMessageCodec;
import io.piveau.hub.services.catalogues.CataloguesServiceVerticle;
import io.piveau.hub.services.datasets.DatasetsServiceVerticle;
import io.piveau.hub.services.distributions.DistributionsServiceVerticle;
import io.piveau.hub.services.drafts.DatasetDraftsServiceVerticle;
import io.piveau.hub.services.identifiers.IdentifiersServiceVerticle;
import io.piveau.hub.services.index.IndexServiceVerticle;
import io.piveau.hub.services.metrics.MetricsServiceVerticle;
import io.piveau.hub.services.resources.ResourcesServiceVerticle;
import io.piveau.hub.services.translation.TranslationServiceVerticle;
import io.piveau.hub.services.vocabularies.VocabulariesServiceVerticle;
import io.piveau.hub.shell.ShellVerticle;
import io.piveau.hub.util.IndexMappingLoader;
import io.piveau.hub.util.logger.PiveauLogger;
import io.piveau.hub.util.logger.PiveauLoggerFactory;
import io.piveau.json.ConfigHelper;
import io.piveau.profile.PiveauProfile;
import io.piveau.profile.ProfileInfo;
import io.piveau.profile.ProfileLoader;
import io.piveau.security.ApiKeyAuthProvider;
import io.piveau.security.PiveauAuth;
import io.piveau.security.PiveauAuthConfig;
import io.piveau.utils.ConfigurableAssetHandler;
import io.piveau.vocabularies.ConceptSchemes;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.APIKeyHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.openapi.RouterBuilderOptions;
import io.vertx.ext.web.validation.BadRequestException;
import io.vertx.serviceproxy.ServiceException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the main entry point of the application
 */
public class MainVerticle extends AbstractVerticle {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private CatalogueHandler catalogueHandler;

    private DatasetHandler datasetHandler;
    private MetricHandler metricHandler;
    private DistributionHandler distributionHandler;
    private VocabularyHandler vocabularyHandler;
    private DatasetDraftHandler datasetDraftHandler;
    private IdentifiersHandler identifiersHandler;
    private TranslationServiceHandler translationServiceHandler;
    private ResourceHandler resourceHandler;
    private TrustHandler trustHandler;
    private JsonObject buildInfo;

    private PiveauProfile piveauProfile;

    /**
     * Composes all function for starting the Main Verticle
     */
    @Override
    public void start(Promise<Void> startPromise) {
        PiveauLoggerFactory.getLogger(getClass()).info("Starting piveau hub...");

        Buffer buffer = vertx.fileSystem().readFileBlocking("buildInfo.json");
        if (buffer != null) {
            buildInfo = buffer.toJsonObject();
        } else {
            buildInfo = new JsonObject()
                    .put("timestamp", "Build time not available")
                    .put("version", "Version not available");
        }

        ConfigRetriever.create(vertx).getConfig()
                .compose(config -> {
                    FeatureFlags.setConfig(ConfigHelper.forConfig(config).forceJsonObject(Constants.ENV_PIVEAU_FEATURE_FLAGS));
                    return Future.succeededFuture(config);
                })
                .compose(this::bootstrapProfile)
                .compose(this::bootstrapVerticles)
                .compose(this::startServer)
                .onComplete(startPromise);
    }

    /**
     * Starts the HTTP Server based on the OpenAPI Specification
     */
    private Future<Void> startServer(JsonObject config) {
        vertx.eventBus().registerDefaultCodec(DatasetHelper.class, new DatasetHelperMessageCodec());

        PiveauLogger.setBaseUri(config.getString(Constants.ENV_PIVEAU_HUB_BASE_URI, DCATAPUriSchema.DEFAULT_BASE_URI));

        Promise<Void> promise = Promise.promise();

        JsonObject schemaConfig = ConfigHelper.forConfig(config).forceJsonObject(Constants.ENV_PIVEAU_DCATAP_SCHEMA_CONFIG);
        if (schemaConfig.isEmpty()) {
            schemaConfig.put("baseUri", config.getString(Constants.ENV_PIVEAU_HUB_BASE_URI, DCATAPUriSchema.DEFAULT_BASE_URI));
        }
        DCATAPUriSchema.setConfig(schemaConfig);

        // In test mode (PIVEAU_HUB_TEST_MODE=true in deployment config), default to port 0 so the
        // OS assigns a free ephemeral port – eliminates port collisions across concurrent test classes.
        // In production this flag must not be set, so the production default (Defaults.SERVICE_PORT) is used.
        boolean isTestMode = config().getBoolean(Constants.ENV_PIVEAU_HUB_TEST_MODE, false);
        Integer port = config.getInteger(Constants.ENV_PIVEAU_HUB_SERVICE_PORT,
                config().getInteger(Constants.ENV_PIVEAU_HUB_SERVICE_PORT,
                        isTestMode ? 0 : Defaults.SERVICE_PORT));

        Map<String, List<String>> apiKeys = new HashMap<>();
        if (config.containsKey(Constants.ENV_PIVEAU_HUB_API_KEYS)) {
            config.getJsonObject(Constants.ENV_PIVEAU_HUB_API_KEYS).stream()
                    .filter(entry -> entry.getValue() instanceof JsonArray)
                    .forEach(entry ->
                            apiKeys.put(entry.getKey(), ((JsonArray) entry.getValue()).stream()
                                    .map(String.class::cast).toList()));
        } else if (config.containsKey(Constants.ENV_PIVEAU_HUB_API_KEY)) {
            apiKeys.put(config.getString(Constants.ENV_PIVEAU_HUB_API_KEY), Collections.singletonList("*"));
        } else {
            log.warn("No API key configured");
        }


        JsonObject authorizationProcessData = ConfigHelper.forConfig(config).forceJsonObject(Constants.ENV_PIVEAU_HUB_AUTHORIZATION_PROCESS_DATA);

        String greeting = config.getString("greeting", Defaults.GREETING);

        ConceptSchemes.initRemotes(vertx,
                        config.getBoolean(Constants.ENV_PIVEAU_HUB_LOAD_VOCABULARIES_FETCH, Defaults.LOAD_VOCABULARIES_FETCH),
                        config.getBoolean(Constants.ENV_PIVEAU_HUB_LOAD_VOCABULARIES, Defaults.LOAD_VOCABULARIES))
//                .compose(v -> Vocabularies.installVocabularies(vertx, config))
                .compose(v -> {
                    if (FeatureFlags.isEnabled("piveau_trust")) {
                        return RouterBuilder.create(vertx, "webroot/openapi_trust.yaml");
                    } else {
                        return RouterBuilder.create(vertx, "webroot/openapi.yaml");
                    }
                })
                .compose(builder -> {
                    WebClient client = WebClient.create(vertx);

                    RouterBuilderOptions options = new RouterBuilderOptions().setMountNotImplementedHandler(true).setOperationModelKey("assd");
                    builder.setOptions(options);

                    JsonArray corsDomains = ConfigHelper.forConfig(config).forceJsonArray(Constants.ENV_PIVEAU_HUB_CORS_DOMAINS);
                    if (!corsDomains.isEmpty()) {

                        List<String> corsArray = corsDomains.stream()
                                .map(entry -> entry.toString().replace(".", "\\."))
                                .toList();

                        Set<String> allowedHeaders = Set.of(
                                "x-requested-with",
                                "Content-Type",
                                "Authorization",
                                "Accept",
                                "X-API-Key"
                        );

                        Set<HttpMethod> allowedMethods = Set.of(
                                HttpMethod.GET,
                                HttpMethod.HEAD,
                                HttpMethod.POST,
                                HttpMethod.PUT,
                                HttpMethod.DELETE,
                                HttpMethod.PATCH,
                                HttpMethod.OPTIONS
                        );

                        //"^(https?:\\/\\/(?:.+\\.)?(?:fokus\\.fraunhofer\\.de|localhost)(?::\\d{1,5})?)$"
                        String corsString = "^(https?:\\/\\/(?:.+\\.)?(?:" + String.join("|", corsArray) + ")(?::\\d{1,5})?)$";
                        builder.rootHandler(
                                CorsHandler.create().addRelativeOrigin(corsString)
                                        .allowedHeaders(allowedHeaders)
                                        .allowedMethods(allowedMethods)
                                        .allowCredentials(true));
                    }

                    long bodyLimit = config.getLong(Constants.ENV_PIVEAU_HTTP_BODY_LIMIT, Defaults.BODY_LIMIT);
                    builder.rootHandler(BodyHandler.create().setBodyLimit(bodyLimit));
                    builder.rootHandler(StaticHandler.create());

                    builder.securityHandler("ApiKeyAuth", APIKeyHandler.create(new ApiKeyAuthProvider(apiKeys)).header("X-API-Key"));

                    return PiveauAuth.create(vertx, new PiveauAuthConfig(authorizationProcessData))
                            .compose(piveauAuth -> {
                                builder.securityHandler("BearerAuth", piveauAuth.authHandler());

                                TripleStore tripleStore = new TripleStore(vertx, config.getJsonObject(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG, new JsonObject()));

                                // Catalogues
                                builder.operation("listCatalogues").handler(context ->
                                        catalogueHandler.handleListCatalogues(context));
                                builder.operation("headListCatalogues").handler(context ->
                                        catalogueHandler.handleListCatalogues(context));

                                builder.operation("getCatalogue").handler(context ->
                                        catalogueHandler.handleGetCatalogue(context));
                                builder.operation("headGetCatalogue").handler(context ->
                                        catalogueHandler.handleGetCatalogue(context));

                                builder.operation("putCatalogue")
                                        .handler(new CataloguePermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_CATALOGUE_CREATE,
                                                Constants.KEYCLOAK_SCOPE_CATALOGUE_UPDATE
                                        )))
                                        .handler(catalogueHandler::handlePutCatalogue);

                                builder.operation("deleteCatalogue")
                                        .handler(new CataloguePermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_CATALOGUE_DELETE
                                        )))
                                        .handler(catalogueHandler::handleDeleteCatalogue);

                                // Catalogue Datasets

                                builder.operation("listCatalogueDatasets").handler(context ->
                                        datasetHandler.handleListCatalogueDatasets(context));
                                builder.operation("headListCatalogueDatasets").handler(context ->
                                        datasetHandler.handleListCatalogueDatasets(context));

                                builder.operation("postCatalogueDataset")
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_CREATE
                                        )))
                                        .handler(context -> datasetHandler.handlePostCatalogueDataset(context));

                                // Catalogue Datasets Origin

                                builder.operation("getCatalogueDatasetsOrigin")
                                        .handler(datasetHandler::handleGetDatasetOrigin);
                                builder.operation("headGetCatalogueDatasetsOrigin")
                                        .handler(datasetHandler::handleGetDatasetOrigin);
                                builder.operation("putCatalogueDatasetsOrigin")
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_CREATE,
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(datasetHandler::handlePutDatasetOrigin);
                                builder.operation("deleteCatalogueDatasetsOrigin")
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_DELETE
                                        )))
                                        .handler(datasetHandler::handleDeleteDatasetOrigin);

                                // Datasets

                                builder.operation("listDatasets").handler(context ->
                                        datasetHandler.handleListDatasets(context));
                                builder.operation("headListDatasets").handler(context ->
                                        datasetHandler.handleListDatasets(context));

                                builder.operation("getDataset").handler(context ->
                                        datasetHandler.handleGetDataset(context));
                                builder.operation("headGetDataset").handler(context ->
                                        datasetHandler.handleGetDataset(context));

                                builder.operation("putDataset")
                                        .handler(new CatalogueIdentifierHandler(
                                                tripleStore.getDatasetManager()))
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(datasetHandler::handlePutDataset);
                                builder.operation("deleteDataset")
                                        .handler(new CatalogueIdentifierHandler(
                                                tripleStore.getDatasetManager()))
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_DELETE
                                        )))
                                        .handler(datasetHandler::handleDeleteDataset);

                                // Dataset Catalogue Record

                                builder.operation("getRecord").handler(context ->
                                        datasetHandler.handleGetRecord(context));
                                builder.operation("headGetRecord").handler(context ->
                                        datasetHandler.handleGetRecord(context));
                                builder.operation("getRecordLegacy").handler(context ->
                                        datasetHandler.handleGetRecord(context));
                                builder.operation("headGetRecordLegacy").handler(context ->
                                        datasetHandler.handleGetRecord(context));

                                // Dataset Index

                                // Reindex all datasets - uses CataloguePermissionHandler since it operates
                                // across all catalogues without a specific catalogueId
                                builder.operation("reindexAllDatasets")
                                        .handler(new CataloguePermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(datasetHandler::handleReindexAllDatasets);

                                builder.operation("getDatasetIndex")
                                        .handler(new CatalogueIdentifierHandler(
                                                tripleStore.getDatasetManager()))
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(datasetHandler::handleIndexDataset);

                                // Dataset Legacy

                                builder.operation("postCatalogueDatasetLegacy")
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_CREATE
                                        )))
                                        .handler(datasetHandler::handlePostCatalogueDatasetLegacy);
                                builder.operation("putDatasetLegacy")
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_CREATE,
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(datasetHandler::handlePutDatasetLegacy);
                                builder.operation("deleteDatasetLegacy")
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_DELETE
                                        )))
                                        .handler(datasetHandler::handleDeleteDatasetLegacy);

                                // Dataset Distributions

                                builder.operation("listDatasetDistributions")
                                        .handler(distributionHandler::handleListDatasetDistributions);
                                builder.operation("headListDatasetDistributions")
                                        .handler(distributionHandler::handleListDatasetDistributions);
                                builder.operation("postDatasetDistribution")
                                        .handler(new CatalogueIdentifierHandler(
                                                tripleStore.getDatasetManager()))
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(distributionHandler::handlePostDatasetDistribution);

                                // Distributions

                                builder.operation("getDistribution").handler(context ->
                                        distributionHandler.handleGetDistribution(context));
                                builder.operation("headGetDistribution").handler(context ->
                                        distributionHandler.handleGetDistribution(context));

                                builder.operation("putDistribution")
                                        .handler(new DatasetIdentifierHandler(
                                                tripleStore.getDatasetManager()))
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(distributionHandler::handlePutDistribution);
                                builder.operation("deleteDistribution")
                                        .handler(new DatasetIdentifierHandler(
                                                tripleStore.getDatasetManager()))
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(distributionHandler::handleDeleteDistribution);

                                // Metrics

                                builder.operation("getMetrics").handler(context ->
                                        metricHandler.handleGetMetric(context));
                                builder.operation("headGetMetrics").handler(context ->
                                        metricHandler.handleGetMetric(context));

                                builder.operation("putMetrics")
                                        .handler(new CatalogueIdentifierHandler(
                                                tripleStore.getDatasetManager()))
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(metricHandler::handlePutMetric);
                                builder.operation("deleteMetrics")
                                        .handler(new CatalogueIdentifierHandler(
                                                tripleStore.getDatasetManager()))
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(metricHandler::handleDeleteMetric);

                                // Translations

                                builder.operation("postTranslation")
                                        .handler(translationServiceHandler::handlePostTranslation);

                                // Vocabularies

                                builder.operation("listVocabularies").handler(context ->
                                        vocabularyHandler.listVocabularies(context));
                                builder.operation("headListVocabularies").handler(context ->
                                        vocabularyHandler.listVocabularies(context));
                                builder.operation("getVocabulary")
                                        .handler(context -> vocabularyHandler.getVocabulary(context));
                                builder.operation("headGetVocabulary")
                                        .handler(context -> vocabularyHandler.getVocabulary(context));
                                builder.operation("createOrUpdateVocabulary")
                                        .handler(ctx -> ctx.put("catalogueId", "vocabularies").next())
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE,
                                                Constants.KEYCLOAK_SCOPE_DATASET_CREATE
                                        )))
                                        .handler(vocabularyHandler::createOrUpdateVocabulary);
                                builder.operation("putVocabulary")
                                        .handler(ctx -> ctx.put("catalogueId", "vocabularies").next())
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE,
                                                Constants.KEYCLOAK_SCOPE_DATASET_CREATE
                                        )))
                                        .handler(vocabularyHandler::putVocabulary);
                                builder.operation("deleteVocabulary")
                                        .handler(ctx -> ctx.put("catalogueId", "vocabularies").next())
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_DELETE
                                        )))
                                        .handler(vocabularyHandler::deleteVocabulary);

                                // Drafts

                                builder.operation("listDatasetDrafts")
                                        .handler(new DatasetDraftPermissionHandler(null))
                                        .handler(datasetDraftHandler::listDatasetDrafts);
                                builder.operation("readDatasetDraft")
                                        .handler(new DatasetDraftPermissionHandler(null))
                                        .handler(datasetDraftHandler::readDatasetDraft);
                                builder.operation("createDatasetDraft")
                                        .handler(new DatasetDraftPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_CREATE
                                        )))
                                        .handler(datasetDraftHandler::createDatasetDraft);
                                builder.operation("createOrUpdateDatasetDraft")
                                        .handler(new DatasetDraftPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_CREATE,
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(datasetDraftHandler::createOrUpdateDatasetDraft);
                                builder.operation("deleteDatasetDraft")
                                        .handler(new DatasetDraftPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_DELETE
                                        )))
                                        .handler(datasetDraftHandler::deleteDatasetDraft);
                                builder.operation("publishDatasetDraft")
                                        .handler(new DatasetDraftPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(datasetDraftHandler::publishDatasetDraft);
                                builder.operation("hideDataset")
                                        .handler(new DatasetDraftPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(datasetDraftHandler::hideDataset);

                                builder.operation("createDatasetIdentifier")
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_CREATE,
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(identifiersHandler::createDatasetIdentifier);
                                builder.operation("checkIdentifierEligibility")
                                        .handler(new DatasetPermissionHandler(List.of(
                                                Constants.KEYCLOAK_SCOPE_DATASET_CREATE,
                                                Constants.KEYCLOAK_SCOPE_DATASET_UPDATE
                                        )))
                                        .handler(identifiersHandler::checkDatasetIdentifierEligibility);

                               // TODO VALEX: remove the below endpoint as this implementation is a workaround for crud operations with deprecated connectors
                              //identifiers
                              //builder.operation("postDistributionIdentifier").handler(context -> distributionHandler.handleExtractDistributionIdentifiers(context));
                              //Extract identifier
                              builder.operation("getDatasetIdentifier")
                                  .handler(context -> identifiersHandler.handleExtractDatasetIdentifier(context));

                              // Resources

                                builder.operation("listResourceTypes")
                                        .handler(resourceHandler::listResourceTypes);
                                builder.operation("listResources")
                                        .handler(resourceHandler::listResources);
                                builder.operation("getResource")
                                        .handler(resourceHandler::getResource);
                                builder.operation("headGetResource")
                                        .handler(resourceHandler::getResource);
                                builder.operation("putResource")
                                        .handler(resourceHandler::putResource);
                                builder.operation("postResource")
                                        .handler(resourceHandler::postResource);
                                builder.operation("deleteResource")
                                        .handler(resourceHandler::deleteResource);

                                if (FeatureFlags.isEnabled("piveau_trust")) {
                                    builder.operation("createDID")
                                            .handler(trustHandler::handleCreateDIDDocument);
                                    builder.operation("getDID")
                                            .handler(trustHandler::handleGetDID);
                                    builder.operation("getSetup")
                                            .handler(trustHandler::handleGetSetup);
                                    builder.operation("getSetupVC")
                                            .handler(trustHandler::handleGetSetupVC);
                                    builder.operation("getTrustByResource")
                                            .handler(trustHandler::handleGetTrustByResource);
                                }

                                Router router = builder.createRouter();

                                router.get("/info").handler(context -> healthHandler(context, greeting));

                                HealthCheckHandler hch = HealthCheckHandler.create(vertx);
                                hch.register("buildInfo", status -> status.complete(Status.OK(buildInfo)));
                                router.get("/health").handler(hch);

                                if (FeatureFlags.isEnabled("piveau_profile")) {
                                    router.route("/profile*")
                                            .subRouter(new ProfileInfo(vertx, piveauProfile).getRouter());
                                }

                                router.get("/images/logo").handler(new ConfigurableAssetHandler(config.getString(Constants.ENV_PIVEAU_LOGO_PATH, "webroot/images/logo.png"), client));
                                router.get("/images/favicon").handler(new ConfigurableAssetHandler(config.getString(Constants.ENV_PIVEAU_FAVICON_PATH, "webroot/images/favicon.png"), client));

                                router.get("/imprint").handler(context ->
                                        context.redirect(config.getString(Constants.ENV_PIVEAU_IMPRINT_URL, "/")));

                                router.get("/privacy").handler(context ->
                                        context.redirect(config.getString(Constants.ENV_PIVEAU_PRIVACY_URL, "/")));

                                router.errorHandler(400, context -> {
                                    context.response().setStatusCode(400);
                                    if (context.failure() instanceof BadRequestException bre) {
                                        context.response()
                                                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")
                                                .end(bre.toJson().encodePrettily());
                                    } else if (context.failure() instanceof ServiceException se) {
                                        context.response()
                                                .end(se.getMessage());
                                    } else {
                                        context.response().end();
                                    }
                                }).errorHandler(401, context -> {
                                    context.response().setStatusCode(401);
                                    log.debug("Unauthorized (401) request: {}", context.request().absoluteURI(), context.failure());
                                    context.response().putHeader("WWW-Authenticate", "Bearer").end();
                                }).errorHandler(403, context -> {
                                    context.response().setStatusCode(403);
                                    log.debug("Forbidden (403) request: {}", context.request().absoluteURI());
                                    context.response().end();
                                }).errorHandler(404, context -> {
                                    context.response().setStatusCode(404);
                                    if (context.failure() instanceof ServiceException se) {
                                        if (!se.getDebugInfo().isEmpty()) {
                                            context.response()
                                                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")
                                                    .end(se.getDebugInfo().encodePrettily());
                                        } else {
                                            context.response().end(se.getMessage());
                                        }
                                    } else {
                                        if (context.failure() != null) {
                                            context.response().end(context.failure().getMessage());
                                        } else {
                                            context.response().end();
                                        }
                                    }
                                }).errorHandler(304, context -> {
                                    context.response().setStatusCode(304);
                                    if (context.failure() instanceof ServiceException se) {
                                        context.response().end(se.getMessage());
                                    } else {
                                        context.response().end();
                                    }
                                }).errorHandler(500, context -> {
                                    context.response().setStatusCode(500);
                                    if (context.failure() instanceof ServiceException se) {
                                        if (!se.getDebugInfo().isEmpty()) {
                                            context.response()
                                                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")
                                                    .end(se.getDebugInfo().encodePrettily());
                                        } else {
                                            context.response().end(se.getMessage());
                                        }
                                    } else {
                                        context.response().end();
                                    }
                                }).errorHandler(415, context -> {
                                    context.response().setStatusCode(415);
                                    if (context.failure() instanceof ServiceException se) {
                                        context.response().end(se.getMessage());
                                    } else {
                                        context.response().end();
                                    }
                                }).errorHandler(422, context -> {
                                    context.response().setStatusCode(422);
                                    if (context.failure() instanceof ServiceException se) {
                                        context.response().end(se.getMessage());
                                    } else {
                                        context.response().end();
                                    }
                                });

                                return vertx.createHttpServer(new HttpServerOptions()
                                        .setPort(port)
                                        .setMaxHeaderSize(40960)
                                ).requestHandler(router).listen();
                            });
                })
                .onSuccess(server -> {
                    log.info("Successfully launched server on port {}", server.actualPort());
                    promise.complete();
                })
                .onFailure(promise::fail);
        return promise.future();
    }

    /**
     * Bootstraps all Verticles
     *
     * @return future
     */
    private Future<JsonObject> bootstrapVerticles(JsonObject config) {

        if (config().containsKey(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG)) {
            log.warn("Overriding config for unit testing (PIVEAU_TRIPLESTORE_CONFIG, PIVEAU_HUB_API_KEYS, PIVEAU_HUB_LOAD_VOCABULARIES, PIVEAU_TRUST_CONFIG)");
            // Required for the API test cases
            config.put(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG, config().getJsonObject(Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG, new JsonObject()));
            if (config().containsKey(Constants.ENV_PIVEAU_HUB_API_KEYS)) {
                config.put(Constants.ENV_PIVEAU_HUB_API_KEYS, config().getJsonObject(Constants.ENV_PIVEAU_HUB_API_KEYS, new JsonObject()));
            }
            config.put(Constants.ENV_PIVEAU_HUB_LOAD_VOCABULARIES, false);
        }

        if (log.isInfoEnabled()) {
            log.info(config.encodePrettily());
        }

        return Future.future(promise -> {
            DeploymentOptions options = new DeploymentOptions().setConfig(config);

            ResourcesServiceVerticle resourcesServiceVerticle = new ResourcesServiceVerticle(piveauProfile);

            List<Future<String>> futures = new ArrayList<>();

            futures.add(vertx.deployVerticle(IndexServiceVerticle.class.getName(), options));
            futures.add(vertx.deployVerticle(CataloguesServiceVerticle.class.getName(), options));
            futures.add(vertx.deployVerticle(DatasetsServiceVerticle.class.getName(), options)
                    .compose(id -> vertx.deployVerticle(ShellVerticle.class.getName(), options)));
            futures.add(vertx.deployVerticle(DistributionsServiceVerticle.class.getName(), options));
            futures.add(vertx.deployVerticle(MetricsServiceVerticle.class.getName(), options));
            futures.add(vertx.deployVerticle(TranslationServiceVerticle.class.getName(), options));
            futures.add(vertx.deployVerticle(DatasetDraftsServiceVerticle.class.getName(), options));
            futures.add(vertx.deployVerticle(IdentifiersServiceVerticle.class.getName(), options));
            futures.add(vertx.deployVerticle(KeyCloakServiceVerticle.class.getName(), options));
            futures.add(vertx.deployVerticle(resourcesServiceVerticle, options));
            futures.add(vertx.deployVerticle(VocabulariesServiceVerticle.class.getName(), options));

            Future.all(futures)
                    .onSuccess(f -> {
                        datasetHandler = new DatasetHandler(vertx);
                        metricHandler = new MetricHandler(vertx);
                        distributionHandler = new DistributionHandler(vertx);
                        catalogueHandler = new CatalogueHandler(vertx);
                        translationServiceHandler = new TranslationServiceHandler(vertx);
                        vocabularyHandler = new VocabularyHandler(vertx);
                        datasetDraftHandler = new DatasetDraftHandler(vertx);
                        identifiersHandler = new IdentifiersHandler(vertx);
                        resourceHandler = new ResourceHandler(vertx);

                        if (FeatureFlags.isEnabled("piveau_trust") && config.containsKey("PIVEAU_TRUST_CONFIG")) {
                            trustHandler = new TrustHandler(vertx, config);
                        }

                        promise.complete(config);
                    })
                    .onFailure(promise::fail);
        });
    }

    private Future<JsonObject> bootstrapProfile(JsonObject config) {
        Promise<JsonObject> promise = Promise.promise();

        if (FeatureFlags.isEnabled("piveau_profile") && config.containsKey(io.piveau.Constants.ENV_PIVEAU_PROFILE)) {
            JsonObject profileConfig = config.getJsonObject(io.piveau.Constants.ENV_PIVEAU_PROFILE);
            ProfileLoader profileLoader = new ProfileLoader(vertx, profileConfig);

            profileLoader.loadProfile().onComplete(ar -> {

                if (ar.succeeded()) {
                    piveauProfile = ar.result();
                    initIndexMapping(config, ar.result())
                            .onSuccess(ar2 -> promise.complete(config))
                            .onFailure(err -> promise.fail(err.getMessage()));
                } else {
                    log.debug(ar.cause().getMessage());
                    initIndexMapping(config, null)
                            .onSuccess(ar2 -> promise.complete(config))
                            .onFailure(err -> promise.fail(err.getMessage()));
                }
            });
        } else {
            initIndexMapping(config, null)
                    .onSuccess(ar -> promise.complete(config))
                    .onFailure(err -> promise.fail(err.getMessage()));
        }

        return promise.future();
    }

    private Future<Void> initIndexMapping(JsonObject config, PiveauProfile profile) {
        Promise<Void> promise = Promise.promise();
        IndexMappingLoader indexMappingLoader = new IndexMappingLoader(vertx, config, profile);
        indexMappingLoader.load()
                .onSuccess(v -> promise.complete())
                .onFailure(error -> {
                    log.error(error.getMessage());
                    promise.fail(error.getMessage());
                });
        return promise.future();
    }

    /**
     * Creates the health and info endpoint
     */
    private void healthHandler(RoutingContext context, String greeting) {
        context.response()
                .setStatusCode(200)
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")
                .end(new JsonObject()
                        .put("service", "piveau hub")
                        .put("message", greeting)
                        .put("version", "?.?.?")
                        .put("status", "ok").encodePrettily());
    }

    public static void main(String[] args) {
        String[] params = Arrays.copyOf(args, args.length + 1);
        params[params.length - 1] = MainVerticle.class.getName();
        io.vertx.core.Launcher.executeCommand("run", params);
    }

}
