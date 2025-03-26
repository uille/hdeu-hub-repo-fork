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

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

final public class ErrorCodeResponse {

    static public final void forbidden(RoutingContext context) {
        JsonObject response = new JsonObject();
        context.response().putHeader("Content-Type", "application/json");
        context.response().setStatusCode(403).end();
    }

    static public final void forbidden(RoutingContext context, String errorCause) {
        JsonObject response = new JsonObject();
        context.response().putHeader("Content-Type", "application/json");
        context.response().setStatusCode(403);
        if (errorCause != null) {
            response.put("status", "error");
            response.put("cause", errorCause);
            context.response().end(response.toString());
        }

        context.response().end();
    }

    static public final void internalServerError(RoutingContext context, String errorCause) {
        JsonObject response = new JsonObject();
        context.response().putHeader("Content-Type", "application/json");
        context.response().setStatusCode(500);
        if (errorCause != null) {
            response.put("status", "error");
            response.put("cause", errorCause);
            context.response().end(response.toString());
        }
        context.response().end();

    }

    static public final void internalServerError(RoutingContext context) {
        JsonObject response = new JsonObject();
        context.response().putHeader("Content-Type", "application/json");
        context.response().setStatusCode(500).end();
    }

    static public final void badRequest(RoutingContext context) {
        JsonObject response = new JsonObject();
        context.response().putHeader("Content-Type", "application/json");
        context.response().setStatusCode(400).end();
    }

    static public final void badRequest(RoutingContext context, String errorCause) {
        JsonObject response = new JsonObject();
        context.response().putHeader("Content-Type", "application/json");
        context.response().setStatusCode(400);
        if (errorCause != null) {
            response.put("status", "error");
            response.put("cause", errorCause);
            context.response().end(response.toString());
        }
        context.response().end();
    }

    static public final void unauthorized(RoutingContext context, String errorCause) {
        JsonObject response = new JsonObject();
        context.response().putHeader("Content-Type", "application/json");
        context.response().setStatusCode(401);
        if (errorCause != null) {
            response.put("status", "error");
            response.put("cause", errorCause);
            context.response().end(response.toString());
        }
        context.response().end();

    }

    static public final void unauthorized(RoutingContext context) {
        JsonObject response = new JsonObject();
        context.response().putHeader("Content-Type", "application/json");
        context.response().setStatusCode(401).end();
    }

}
