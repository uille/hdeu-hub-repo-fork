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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.security.PiveauAuth;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class DatasetDraftPermissionHandlerTest {

  @Test
  void resourcesContainWildcard_allows() {
    JsonObject principal = new JsonObject().put("resources", new JsonArray().add("*"));
    MultiMap query = mockQueryParams(null);
    Map<String, String> path = new HashMap<>();

    RoutingContext context = rcWith(principal, query, path, HttpMethod.GET);
    DatasetDraftPermissionHandler handler = new DatasetDraftPermissionHandler(List.of("s1", "s2"));

    handler.handle(context);

    verify(context).next();
    verify(context, never()).fail(anyInt());
  }

  @Test
  void resourcesPresentWithoutWildcard_denies403() {
    JsonObject principal = new JsonObject().put("resources", new JsonArray().add("cat-1"));
    MultiMap query = mockQueryParams(null);
    Map<String, String> path = new HashMap<>();

    RoutingContext context = rcWith(principal, query, path, HttpMethod.GET);
    DatasetDraftPermissionHandler handler = new DatasetDraftPermissionHandler(List.of("s1"));

    handler.handle(context);

    verify(context).fail(403);
    verify(context, never()).next();
  }

  @Test
  void operatorRole_addsProvider_and_allows() {
    String sub = "user-123";
    JsonObject principal = new JsonObject().put("sub", sub); // no "resources" key
    MultiMap query = mockQueryParams(null);
    Map<String, String> path = new HashMap<>();

    RoutingContext context = rcWith(principal, query, path, HttpMethod.GET);
    DatasetDraftPermissionHandler handler = new DatasetDraftPermissionHandler(List.of("s1", "s2"));

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(true);

      handler.handle(context);

      verify(query).add("provider", sub);
      verify(context).next();
      verify(context, never()).fail(anyInt());
    }
  }

  @Test
  void getWithId_authorized_allows() {
    String requested = "cat-2";
    String sub = "u";
    JsonObject principal = new JsonObject()
        .put("sub", sub)
        .put("authorization", new JsonObject()
            .put("permissions", new JsonArray()
                .add(new JsonObject().put("rsname", "cat-1"))
                .add(new JsonObject().put("rsname", "cat-2")) // authorized
            ));
    MultiMap query = mockQueryParams(requested);
    Map<String, String> path = new HashMap<>();
    path.put("id", "123");

    RoutingContext context = rcWith(principal, query, path, HttpMethod.GET);
    DatasetDraftPermissionHandler handler = new DatasetDraftPermissionHandler(List.of("s1"));

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(false);

      handler.handle(context);

      verify(context).next();
      verify(context, never()).fail(anyInt());
    }
  }

  @Test
  void getWithId_notAuthorized_denies403() {
    String requested = "cat-9";
    JsonObject principal = new JsonObject()
        .put("authorization", new JsonObject()
            .put("permissions", new JsonArray()
                .add(new JsonObject().put("rsname", "cat-1"))
                .add(new JsonObject().put("rsname", "cat-2"))
            ));
    MultiMap query = mockQueryParams(requested);
    Map<String, String> path = new HashMap<>();
    path.put("id", "999");

    RoutingContext context = rcWith(principal, query, path, HttpMethod.GET);
    DatasetDraftPermissionHandler handler = new DatasetDraftPermissionHandler(List.of());

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(false);

      handler.handle(context);

      verify(context).fail(403);
      verify(context, never()).next();
    }
  }

  @Test
  void getWithoutId_noAuthorizedResources_denies403() {
    JsonObject principal = new JsonObject()
        .put("authorization", new JsonObject().put("permissions", new JsonArray())); // empty
    MultiMap query = mockQueryParams("cat-x");
    Map<String, String> path = new HashMap<>(); // no "id"

    RoutingContext context = rcWith(principal, query, path, HttpMethod.GET);
    DatasetDraftPermissionHandler handler = new DatasetDraftPermissionHandler(List.of());

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(false);

      handler.handle(context);

      verify(context).fail(403);
      verify(context, never()).next();
    }
  }

  @Test
  void getWithoutId_withAuthorizedResources_addsProviderAndAuthorizedList_and_allows() {
    String sub = "user-7";
    JsonObject principal = new JsonObject()
        .put("sub", sub)
        .put("authorization", new JsonObject()
            .put("permissions", new JsonArray()
                .add(new JsonObject().put("rsname", "cat-1"))
                .add(new JsonObject().put("rsname", "cat-2"))
                .add(new JsonObject()) // missing rsname -> ignored
            ));
    MultiMap query = mockQueryParams("ignored");
    Map<String, String> path = new HashMap<>(); // no "id"

    RoutingContext context = rcWith(principal, query, path, HttpMethod.GET);
    DatasetDraftPermissionHandler handler = new DatasetDraftPermissionHandler(List.of());

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(false);

      handler.handle(context);

      // provider added
      verify(query).add("provider", sub);

      // authorizedResources added (Iterable overload)
      ArgumentCaptor<Iterable<String>> listCap = ArgumentCaptor.forClass(Iterable.class);
      verify(query).add(eq("authorizedResources"), listCap.capture());

      // Ensure both resources present
      Iterable<String> captured = listCap.getValue();
      // Simple membership checks
      boolean hasCat1 = false, hasCat2 = false;
      for (String s : captured) {
        if ("cat-1".equals(s)) {
          hasCat1 = true;
        }
        if ("cat-2".equals(s)) {
          hasCat2 = true;
        }
      }
      org.junit.jupiter.api.Assertions.assertTrue(hasCat1 && hasCat2,
          "authorizedResources should contain cat-1 and cat-2");

      verify(context).next();
      verify(context, never()).fail(anyInt());
    }
  }

  @Test
  void nonGet_allScopesPresent_addsProvider_and_allows() {
    String requested = "cat-42";
    String sub = "abc";
    JsonObject principal = new JsonObject().put("sub", sub);
    MultiMap query = mockQueryParams(requested);
    Map<String, String> path = new HashMap<>();

    List<String> scopes = List.of("draft:create", "draft:update");
    DatasetDraftPermissionHandler handler = new DatasetDraftPermissionHandler(scopes);
    RoutingContext context = rcWith(principal, query, path, HttpMethod.POST);

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(false);
      // All required scopes granted on requested resource
      auth.when(() -> PiveauAuth.userHasPermission(context.user(), requested, "draft:create"))
          .thenReturn(true);
      auth.when(() -> PiveauAuth.userHasPermission(context.user(), requested, "draft:update"))
          .thenReturn(true);

      handler.handle(context);

      verify(query).add("provider", sub);
      verify(context).next();
      verify(context, never()).fail(anyInt());
    }
  }

  @Test
  void nonGet_missingOneScope_denies403() {
    String requested = "cat-42";
    JsonObject principal = new JsonObject().put("sub", "abc");
    MultiMap query = mockQueryParams(requested);
    Map<String, String> path = new HashMap<>();

    List<String> scopes = List.of("draft:create", "draft:update");
    DatasetDraftPermissionHandler handler = new DatasetDraftPermissionHandler(scopes);
    RoutingContext context = rcWith(principal, query, path, HttpMethod.PUT);

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(false);
      // Only one scope granted
      auth.when(() -> PiveauAuth.userHasPermission(context.user(), requested, "draft:create"))
          .thenReturn(true);
      auth.when(() -> PiveauAuth.userHasPermission(context.user(), requested, "draft:update"))
          .thenReturn(false);

      handler.handle(context);

      verify(context).fail(403);
      verify(context, never()).next();
      // provider must NOT be added when denied
      verify(query, never()).add(eq("provider"), anyString());
    }
  }

  // --- helpers ---------------------------------------------------------------
  private static RoutingContext rcWith(
      JsonObject principal,
      MultiMap queryParams,
      Map<String, String> pathParams,
      HttpMethod method
  ) {
    RoutingContext context = mock(RoutingContext.class);
    User user = mock(User.class);
    HttpServerRequest req = mock(HttpServerRequest.class);

    when(context.user()).thenReturn(user);
    when(user.principal()).thenReturn(principal);

    when(context.queryParams()).thenReturn(queryParams);
    when(context.pathParams()).thenReturn(pathParams);

    when(context.request()).thenReturn(req);
    when(req.method()).thenReturn(method);

    return context;
  }

  private static MultiMap mockQueryParams(String catalogue) {
    MultiMap mm = mock(MultiMap.class);
    // get("catalogue")
    when(mm.get("catalogue")).thenReturn(catalogue);
    // add providers / authorizedResources should not fail (return same mock for convenience)
    when(mm.add(anyString(), anyString())).thenReturn(mm);
    // Overload for Iterable<String> (used for "authorizedResources")
    when(mm.add(eq("authorizedResources"), (Iterable<String>) any(Iterable.class))).thenReturn(mm);
    return mm;
  }
}
