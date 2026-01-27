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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.security.PiveauAuth;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DatasetPermissionHandlerTest {

  @Test
  void resource_from_pathParam_withWildcard_allows() {
    // path param -> resource = "CAT"
    RequestParameters params = mock(RequestParameters.class);
    when(params.pathParametersNames()).thenReturn(List.of("catalogueId"));
    RequestParameter param = mock(RequestParameter.class);
    when(param.getString()).thenReturn("CAT");
    when(params.pathParameter("catalogueId")).thenReturn(param);
    when(params.queryParameter("catalogue")).thenReturn(null);

    JsonObject principal = new JsonObject().put("resources", new JsonArray().add("*"));
    RoutingContext context = rcWith(principal, params, qWith(null, false), new HashMap<>());

    DatasetPermissionHandler handler = new DatasetPermissionHandler(List.of("s1", "s2"));
    handler.handle(context);

    verify(context).next();
    verify(context, never()).fail(anyInt());
  }

  @Test
  void resource_from_pathParam_withExplicitResource_allows() {
    // path param -> "CAT"
    RequestParameters params = mock(RequestParameters.class);
    when(params.pathParametersNames()).thenReturn(List.of("catalogueId"));
    RequestParameter param = mock(RequestParameter.class);
    when(param.getString()).thenReturn("CAT");
    when(params.pathParameter("catalogueId")).thenReturn(param);
    when(params.queryParameter("catalogue")).thenReturn(null);

    JsonObject principal = new JsonObject().put("resources", new JsonArray().add("CAT"));
    RoutingContext context = rcWith(principal, params, qWith(null, false), new HashMap<>());

    DatasetPermissionHandler handler = new DatasetPermissionHandler(List.of("s1"));
    handler.handle(context);

    verify(context).next();
    verify(context, never()).fail(anyInt());
  }

  @Test
  void resource_from_pathParam_notInResources_denies403() {
    RequestParameters params = mock(RequestParameters.class);
    when(params.pathParametersNames()).thenReturn(List.of("catalogueId"));
    RequestParameter param = mock(RequestParameter.class);
    when(param.getString()).thenReturn("CAT");
    when(params.pathParameter("catalogueId")).thenReturn(param);
    when(params.queryParameter("catalogue")).thenReturn(null);

    JsonObject principal = new JsonObject().put("resources", new JsonArray().add("OTHER"));
    RoutingContext context = rcWith(principal, params, qWith(null, false), new HashMap<>());

    DatasetPermissionHandler handler = new DatasetPermissionHandler(List.of("s1"));
    handler.handle(context);

    verify(context).fail(403);
    verify(context, never()).next();
  }

  @Test
  void resource_from_validationQueryParam_operator_allows() {
    // no path param
    RequestParameters params = emptyParams();
    // validation query param present
    RequestParameter query = mock(RequestParameter.class);
    when(query.getString()).thenReturn("QCAT");
    when(params.queryParameter("catalogue")).thenReturn(query);

    JsonObject principal = new JsonObject(); // no "resources"
    RoutingContext context = rcWith(principal, params, qWith(null, false), new HashMap<>());

    DatasetPermissionHandler handler = new DatasetPermissionHandler(List.of("s1", "s2"));

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(true);
      auth.when(() -> PiveauAuth.userHasPermission(any(), anyString(), anyString()))
          .thenReturn(false); // won't be used

      handler.handle(context);

      verify(context).next();
      verify(context, never()).fail(anyInt());
    }
  }

  @Test
  void resource_from_contextAttribute_allScopesPresent_allows() {
    RequestParameters params = emptyParams(); // no path/query from validation
    MultiMap map = qWith(null, false);         // no raw query param
    Map<String, String> pathVars = new HashMap<>();
    JsonObject principal = new JsonObject();  // no "resources"

    RoutingContext context = rcWith(principal, params, map, pathVars);
    when(context.get("catalogueId")).thenReturn("CTXCAT"); // resource from context attr

    List<String> scopes = List.of("s1", "s2", "s3");
    DatasetPermissionHandler handler = new DatasetPermissionHandler(scopes);

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(false);
      // all scopes granted on CTXCAT
      for (String s : scopes) {
        auth.when(() -> PiveauAuth.userHasPermission(context.user(), "CTXCAT", s)).thenReturn(true);
      }

      handler.handle(context);

      verify(context).next();
      verify(context, never()).fail(anyInt());
    }
  }

  @Test
  void resource_from_rawQueryParam_missingOneScope_denies403() {
    RequestParameters params = emptyParams();
    // no validation query, no path param, no context attr
    RoutingContext context = rcWith(new JsonObject(), params, qWith("RAW", true), new HashMap<>());
    when(context.get("catalogueId")).thenReturn(null);

    List<String> scopes = List.of("a", "b");
    DatasetPermissionHandler handler = new DatasetPermissionHandler(scopes);

    try (MockedStatic<PiveauAuth> auth = mockStatic(PiveauAuth.class)) {
      auth.when(() -> PiveauAuth.userHasRole(context.user(), "operator")).thenReturn(false);
      auth.when(() -> PiveauAuth.userHasPermission(context.user(), "RAW", "a")).thenReturn(true);
      auth.when(() -> PiveauAuth.userHasPermission(context.user(), "RAW", "b")).thenReturn(false);

      handler.handle(context);

      verify(context).fail(403);
      verify(context, never()).next();
    }
  }

  @Test
  void noResourceAnywhere_denies403() {
    RequestParameters params = emptyParams();
    MultiMap map = qWith(null, false);
    RoutingContext context = rcWith(new JsonObject(), params, map, new HashMap<>());
    when(context.get("catalogueId")).thenReturn(null);

    DatasetPermissionHandler handler = new DatasetPermissionHandler(List.of("s1"));

    handler.handle(context);

    verify(context).fail(403);
    verify(context, never()).next();
  }

  // ----------------- helpers -----------------
  private static RoutingContext rcWith(JsonObject principal, RequestParameters params, MultiMap q,
      Map<String, String> pathVars) {
    RoutingContext context = mock(RoutingContext.class);
    User user = mock(User.class);
    when(context.user()).thenReturn(user);
    when(user.principal()).thenReturn(principal);
    when(context.get(ValidationHandler.REQUEST_CONTEXT_KEY)).thenReturn(params);
    when(context.queryParams()).thenReturn(q);
    when(context.pathParams()).thenReturn(pathVars);
    return context;
  }

  private static RequestParameters emptyParams() {
    RequestParameters params = mock(RequestParameters.class);
    when(params.pathParametersNames()).thenReturn(List.of());
    when(params.queryParameter("catalogue")).thenReturn(null);
    return params;
  }

  private static MultiMap qWith(String catalogue, boolean contains) {
    MultiMap map = mock(MultiMap.class);
    when(map.contains("catalogue")).thenReturn(contains);
    when(map.get("catalogue")).thenReturn(catalogue);
    return map;
  }
}
