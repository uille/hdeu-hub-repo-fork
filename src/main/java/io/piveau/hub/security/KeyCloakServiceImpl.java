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
package io.piveau.hub.security;

import io.piveau.FeatureFlags;
import io.piveau.hub.Constants;
import io.piveau.hub.Defaults;
import io.piveau.json.ConfigHelper;
import io.piveau.security.*;
import io.piveau.utils.PiveauContext;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

public class KeyCloakServiceImpl implements KeyCloakService {

    private final PiveauContext serviceContext;

    private final PiveauAuthConfig piveauAuthConfig;

    private PiveauAuth piveauAuth;
    private KeycloakResourceHelper keycloakResourceHelper;

    private final String urlTemplate;

    public KeyCloakServiceImpl(Vertx vertx, JsonObject jsonConfig, Handler<AsyncResult<KeyCloakService>> readyHandler) {
        serviceContext = new PiveauContext("hub-repo", "keycloakService");

        urlTemplate = jsonConfig.getString(Constants.ENV_PIVEAU_HUB_ODP_URL_TEMPLATE, Defaults.ODP_URL_TEMPLATE);
        JsonObject authorizationProcessData = ConfigHelper.forConfig(jsonConfig)
                .forceJsonObject(Constants.ENV_PIVEAU_HUB_AUTHORIZATION_PROCESS_DATA);


        piveauAuthConfig = new PiveauAuthConfig(authorizationProcessData);

        if (piveauAuthConfig.getTokenServerConfig() instanceof KeycloakTokenServerConfig) {
            keycloakResourceHelper = new KeycloakResourceHelper(WebClient.create(vertx),
                    (KeycloakTokenServerConfig) piveauAuthConfig.getTokenServerConfig());

            Future<PiveauAuth> initAuthFuture = PiveauAuth.create(vertx, piveauAuthConfig);

            initAuthFuture.onSuccess(piveauAuthResult -> {
                piveauAuth = piveauAuthResult;

                // test for token validity
                piveauAuth.requestClientToken()
                        .onSuccess(token -> {
                            serviceContext.log().info("Keycloak Service initialized");
                            readyHandler.handle(Future.succeededFuture(this));
                        })
                        .onFailure(cause -> {
                            serviceContext.log().error(cause.getMessage());
                            readyHandler.handle(Future.succeededFuture(this));
                        });

            }).onFailure(cause -> {
                PiveauContext resourceContext = serviceContext.extend("init auth");
                resourceContext.log().error(cause.getMessage());
                readyHandler.handle(Future.succeededFuture(this));
            });
        } else {
            PiveauContext resourceContext = serviceContext.extend("init auth");
            resourceContext.log().info("no Keycloak configured");
            readyHandler.handle(Future.succeededFuture(this));
        }
    }

    @Override
    public KeyCloakService createResource(String catalogueId) {
        PiveauContext resourceContext = serviceContext.extend(catalogueId);

        JsonArray scopes = new JsonArray()
                .add(Constants.KEYCLOAK_SCOPE_DATASET_CREATE)
                .add(Constants.KEYCLOAK_SCOPE_DATASET_UPDATE)
                .add(Constants.KEYCLOAK_SCOPE_DATASET_DELETE);

        if (piveauAuth != null) {
            piveauAuth.requestClientToken().onSuccess(token -> {
                if (keycloakResourceHelper != null) {
                    keycloakResourceHelper.createGroupIfNotExists(token, catalogueId)
                            .compose(created -> {
                                        if (created) {
                                            resourceContext.log().info("group created");
                                        } else {
                                            resourceContext.log().debug("group already existed");
                                        }
                                        return keycloakResourceHelper.createResourceIfNotExist(token, catalogueId, "catalogue", catalogueId, piveauAuthConfig.getClientId(), true, scopes);
                                    }
                            )
                            .compose(created -> {
                                if (created) {
                                    resourceContext.log().info("resource created");
                                } else {
                                    resourceContext.log().debug("resource already existed");
                                }
                                return keycloakResourceHelper.createGroupPolicyIfNotExists(token, catalogueId, catalogueId, scopes);
                            }).compose(created -> {
                                if (created) {
                                    resourceContext.log().info("policy created");
                                } else {
                                    resourceContext.log().debug("policy already existed");
                                }
                                if (FeatureFlags.isEnabled("open_data_presence")) {

                                    String redirecturl = String.format(urlTemplate, catalogueId);
                                    return keycloakResourceHelper.addAsValidRedirectUrl(token, redirecturl);
                                } else {
                                    return Future.succeededFuture(0);
                                }

                            }).onSuccess(created -> {
                                if (created > 0) {
                                    resourceContext.log().info("redirect uri added");
                                } else if (created < 0) {
                                    resourceContext.log().debug("redirect uri already existed");
                                } else {
                                    resourceContext.log().debug("Feature flag ODP not enbaled");
                                }
                                resourceContext.log().info("finished creating");
                            }).onFailure(cause -> resourceContext.log().error("could not create: {}", cause.getMessage()));
                }
            }).onFailure(cause -> resourceContext.log().error("Could not get Client Token: {}", cause.getMessage()));
        }
        return this;
    }

    @Override
    public KeyCloakService deleteResource(String catalogueId) {
        PiveauContext resourceContext = serviceContext.extend(catalogueId);

        if (piveauAuth != null) {
            piveauAuth.requestClientToken().onSuccess(token -> {
                if (keycloakResourceHelper != null) {
                    keycloakResourceHelper.deleteGroup(token, catalogueId)
                            .onFailure(cause -> resourceContext.log().error("Could not delete group for catalogueId " + catalogueId, cause.getMessage()));
                    keycloakResourceHelper.deleteResource(token, catalogueId)
                            .onFailure(cause -> resourceContext.log().error("Could not delete resource for catalogueId " + catalogueId, cause.getMessage()));
                    if (FeatureFlags.isEnabled("open_data_presence")) {

                        String redirecturl = String.format(urlTemplate, catalogueId);
                        keycloakResourceHelper.removeAsValidRedirectUrl(token, redirecturl)
                                .onFailure(cause -> resourceContext.log().error("Could not delete redirect URL for catalogueId " + catalogueId, cause.getMessage()));
                    }
                }
            }).onFailure(cause -> resourceContext.log().error("could not request client token", cause.getMessage()));
        }

        return this;
    }

}
