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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.security.PiveauAuth;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Tests for CataloguePermissionHandler.
 */
class CataloguePermissionHandlerTest {

  private static RoutingContext rcWithUserPrincipal(JsonObject principal) {
    RoutingContext rc = mock(RoutingContext.class);
    User user = mock(User.class);
    when(rc.user()).thenReturn(user);
    when(user.principal()).thenReturn(principal);
    return rc;
  }

  @Test
  void resourcesContainWildcard_allows() {
    JsonObject principal = new JsonObject().put("resources", new JsonArray().add("*"));
    RoutingContext context = rcWithUserPrincipal(principal);

    CataloguePermissionHandler handler = new CataloguePermissionHandler(
        List.of("dataset:create", "dataset:update")
    );

    handler.handle(context);

    verify(context).next();
    verify(context, never()).fail(anyInt());
  }

  @Test
  void resourcesPresentWithoutWildcard_denies403() {
    JsonObject principal = new JsonObject()
        .put("resources", new JsonArray().add("cat-1").add("cat-2"));
    RoutingContext context = rcWithUserPrincipal(principal);

    CataloguePermissionHandler handler = new CataloguePermissionHandler(List.of("dataset:create"));

    handler.handle(context);

    verify(context).fail(403);
    verify(context, never()).next();
  }

  @Test
  void noResources_operatorRole_allows() {
    JsonObject principal = new JsonObject(); // no "resources" key
    RoutingContext context = rcWithUserPrincipal(principal);

    CataloguePermissionHandler handler = new CataloguePermissionHandler(
        List.of("dataset:create", "dataset:update")
    );

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(true);
      // permissions shouldn't be checked once operator is true, but stub harmlessly:
      auth.when(() -> PiveauAuth.userHasPermission(any(), anyString(), anyString()))
          .thenReturn(false);

      handler.handle(context);

      verify(context).next();
      verify(context, never()).fail(anyInt());
    }
  }

  @Test
  void noResources_hasAllRequiredScopes_allows() {
    JsonObject principal = new JsonObject(); // no "resources" key
    RoutingContext context = rcWithUserPrincipal(principal);

    // Require two scopes; all must pass
    List<String> scopes = List.of("dataset:create", "dataset:update");
    CataloguePermissionHandler handler = new CataloguePermissionHandler(scopes);

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      // not an operator
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(false);

      // Grant both required permissions on "Catalogue Resource"
      auth.when(
              () -> PiveauAuth.userHasPermission(context.user(), "Catalogue Resource",
                  "dataset:create"))
          .thenReturn(true);
      auth.when(
              () -> PiveauAuth.userHasPermission(context.user(), "Catalogue Resource",
                  "dataset:update"))
          .thenReturn(true);

      handler.handle(context);

      verify(context).next();
      verify(context, never()).fail(anyInt());
    }
  }

  @Test
  void noResources_missingOneScope_denies403() {
    JsonObject principal = new JsonObject(); // no "resources" key
    RoutingContext context = rcWithUserPrincipal(principal);

    List<String> scopes = List.of("dataset:create", "dataset:update");
    CataloguePermissionHandler handler = new CataloguePermissionHandler(scopes);

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(false);

      // Grant only one of the required scopes; allMatch should fail
      auth.when(
              () -> PiveauAuth.userHasPermission(context.user(), "Catalogue Resource",
                  "dataset:create"))
          .thenReturn(true);
      auth
          .when(
              () -> PiveauAuth.userHasPermission(context.user(), "Catalogue Resource",
                  "dataset:update"))
          .thenReturn(false);

      handler.handle(context);

      verify(context).fail(403);
      verify(context, never()).next();
    }
  }
}
