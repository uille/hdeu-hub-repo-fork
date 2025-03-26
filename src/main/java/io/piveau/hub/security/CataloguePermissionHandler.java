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
import io.vertx.ext.web.RoutingContext;

import java.util.List;

public class CataloguePermissionHandler implements Handler<RoutingContext> {

    private final List<String> scopes;

    public CataloguePermissionHandler(List<String> scopes) {
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
        } else if (PiveauAuth.userHasRole(context.user(), "operator")
                || scopes.stream().allMatch(scope -> PiveauAuth.userHasPermission(context.user(), "Catalogue Resource", scope))) {
            context.next();
        } else {
            context.fail(403);
        }
    }

}
