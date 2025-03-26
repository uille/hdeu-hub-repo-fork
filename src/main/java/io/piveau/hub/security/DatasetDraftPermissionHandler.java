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

import io.piveau.security.PiveauAuth;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

public class DatasetDraftPermissionHandler implements Handler<RoutingContext> {

    private final List<String> scopes;

    public DatasetDraftPermissionHandler(List<String> scopes) {
        this.scopes = scopes;
    }

    @Override
    public void handle(RoutingContext context) {
        if (context.user().principal().containsKey("resources")) {
            List<String> resources = context.user().principal().getJsonArray("resources").stream()
                    .map(Object::toString).toList();
            if (resources.contains("*")) {
                context.next();
            } else {
                context.fail(403);
            }
        } else if (PiveauAuth.userHasRole(context.user(), "operator")) {
            context.queryParams().add("provider", context.user().principal().getString("sub"));
            context.next();
        } else {
            String requestedResource = context.queryParams().get("catalogue");
            if (context.request().method().equals(HttpMethod.GET)) {
                List<String> authorizedResources = new ArrayList<>();

                JsonArray permissions = context.user().principal()
                        .getJsonObject("authorization", new JsonObject())
                        .getJsonArray("permissions", new JsonArray());

                for (Object obj : permissions) {
                    JsonObject permission = (JsonObject) obj;
                    String authorizedResource = permission.getString("rsname");
                    if (authorizedResource != null && !authorizedResource.isEmpty()) {
                        authorizedResources.add(authorizedResource);
                    }
                }

                if (context.pathParams().containsKey("id")) {
                    if (authorizedResources.contains(requestedResource)) {
                        context.next();
                    } else {
                        context.fail(403);
                    }
                } else {
                    if (authorizedResources.isEmpty()) {
                        context.fail(403);
                    } else {
                        context.queryParams().add("provider", context.user().principal().getString("sub"));
                        context.queryParams().add("authorizedResources", authorizedResources);
                        context.next();
                    }
                }
            } else {
                if (scopes.stream().allMatch(scope ->
                        PiveauAuth.userHasPermission(context.user(), requestedResource, scope))) {
                    context.queryParams().add("provider", context.user().principal().getString("sub"));
                    context.next();
                } else {
                    context.fail(403);
                }
            }
        }

    }

}
