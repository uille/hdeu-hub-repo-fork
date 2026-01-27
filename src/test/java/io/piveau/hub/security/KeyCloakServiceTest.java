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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.FeatureFlags;
import io.piveau.hub.Constants;
import io.piveau.security.KeycloakResourceHelper;
import io.piveau.security.KeycloakTokenServerConfig;
import io.piveau.security.PiveauAuth;
import io.piveau.security.PiveauAuthConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

@ExtendWith(VertxExtension.class)
class KeyCloakServiceTest {

  private KeyCloakService service;

  private PiveauAuth piveauAuth;
  private KeycloakResourceHelper helper;

  private static final String CATALOGUE_ID = "cat-1";
  private static final String TOKEN = "tok123";
  private static final String URL_TEMPLATE = "https://example.org/%s";

  @BeforeEach
  void setUp(Vertx vertx) throws Exception {
    // Minimal config so constructor skips Keycloak server wiring (else-branch),
    // then we inject mocks for method tests.
    JsonObject cfg = new JsonObject()
        .put(Constants.ENV_PIVEAU_HUB_ODP_URL_TEMPLATE, URL_TEMPLATE)
        .put(Constants.ENV_PIVEAU_HUB_AUTHORIZATION_PROCESS_DATA, new JsonObject());
    service = KeyCloakService.create(vertx, cfg, ar -> {
    });

    // Inject mocks for piveauAuth / helper
    piveauAuth = mock(PiveauAuth.class);
    helper = mock(KeycloakResourceHelper.class);
    setPrivate(service, "piveauAuth", piveauAuth);
    setPrivate(service, "keycloakResourceHelper", helper);

    // Default token + chains
    when(piveauAuth.requestClientToken()).thenReturn(Future.succeededFuture(TOKEN));
    when(helper.createGroupIfNotExists(TOKEN, CATALOGUE_ID)).thenReturn(
        Future.succeededFuture(true));
    when(helper.createResourceIfNotExist(eq(TOKEN), eq(CATALOGUE_ID), eq("catalogue"),
        eq(CATALOGUE_ID), any(), eq(true), any())).thenReturn(Future.succeededFuture(true));
    when(helper.createGroupPolicyIfNotExists(eq(TOKEN), eq(CATALOGUE_ID), eq(CATALOGUE_ID), any()))
        .thenReturn(Future.succeededFuture(true));

    when(helper.deleteGroup(TOKEN, CATALOGUE_ID)).thenReturn(Future.succeededFuture());
    when(helper.deleteResource(TOKEN, CATALOGUE_ID)).thenReturn(Future.succeededFuture());

    // Start clean: no flags set
    resetFlags();
  }

  @AfterEach
  void tearDown() {
    resetFlags();
  }

  // ============ Constructor tests ============
  @Test
  void constructor_keycloakConfigured_successfulToken_initializesAndCallsReady(Vertx vertx) {
    JsonObject cfg = new JsonObject()
        .put(Constants.ENV_PIVEAU_HUB_ODP_URL_TEMPLATE, URL_TEMPLATE)
        .put(Constants.ENV_PIVEAU_HUB_AUTHORIZATION_PROCESS_DATA, new JsonObject());

    try (MockedConstruction<PiveauAuthConfig> ignored =
        mockConstruction(PiveauAuthConfig.class, (mockCfg, ctx) -> {
          // Make the constructor-created config look like "Keycloak configured"
          KeycloakTokenServerConfig kc = mock(KeycloakTokenServerConfig.class);
          when(mockCfg.getTokenServerConfig()).thenReturn(kc);
        });
        MockedConstruction<KeycloakResourceHelper> helperC =
            mockConstruction(KeycloakResourceHelper.class);
        MockedStatic<PiveauAuth> ignored1 = mockStatic(PiveauAuth.class)) {

      PiveauAuth mockAuth = mock(PiveauAuth.class);
      when(PiveauAuth.create(eq(vertx), any(PiveauAuthConfig.class)))
          .thenReturn(Future.succeededFuture(mockAuth));
      when(mockAuth.requestClientToken()).thenReturn(Future.succeededFuture("t0k"));

      final boolean[] called = {false};
      KeyCloakServiceImpl svc = new KeyCloakServiceImpl(vertx, cfg, ar -> {
        called[0] = true;
        assertTrue(ar.succeeded());
        assertNotNull(ar.result());
      });

      assertNotNull(svc);
      assertTrue(called[0], "readyHandler should be called");
      verify(mockAuth, times(1)).requestClientToken();
      // Helper constructed exactly once with whatever args (we don't assert the args here)
      assertEquals(1, helperC.constructed().size());
    }
  }

  @Test
  void constructor_keycloakConfigured_tokenRequestFails_stillCallsReady(Vertx vertx) {
    JsonObject cfg = new JsonObject()
        .put(Constants.ENV_PIVEAU_HUB_ODP_URL_TEMPLATE, URL_TEMPLATE)
        .put(Constants.ENV_PIVEAU_HUB_AUTHORIZATION_PROCESS_DATA, new JsonObject());

    try (MockedConstruction<PiveauAuthConfig> ignored =
        mockConstruction(PiveauAuthConfig.class, (mockCfg, ctx) -> {
          KeycloakTokenServerConfig kc = mock(KeycloakTokenServerConfig.class);
          when(mockCfg.getTokenServerConfig()).thenReturn(kc);
        });
        MockedConstruction<KeycloakResourceHelper> helperC =
            mockConstruction(KeycloakResourceHelper.class);
        MockedStatic<PiveauAuth> ignored1 = mockStatic(PiveauAuth.class)) {

      PiveauAuth mockAuth = mock(PiveauAuth.class);
      when(PiveauAuth.create(eq(vertx), any(PiveauAuthConfig.class)))
          .thenReturn(Future.succeededFuture(mockAuth));
      when(mockAuth.requestClientToken()).thenReturn(Future.failedFuture("bad token"));

      final boolean[] called = {false};
      KeyCloakServiceImpl svc = new KeyCloakServiceImpl(vertx, cfg, ar -> {
        called[0] = true;
        assertTrue(ar.succeeded(), "constructor always completes succeededFuture(this)");
      });

      assertNotNull(svc);
      assertTrue(called[0]);
      verify(mockAuth, times(1)).requestClientToken();
      assertEquals(1, helperC.constructed().size());
    }
  }

  @Test
  void constructor_keycloakConfigured_authCreateFails_stillCallsReady(Vertx vertx) {
    JsonObject cfg = new JsonObject()
        .put(Constants.ENV_PIVEAU_HUB_ODP_URL_TEMPLATE, URL_TEMPLATE)
        .put(Constants.ENV_PIVEAU_HUB_AUTHORIZATION_PROCESS_DATA, new JsonObject());

    try (MockedConstruction<PiveauAuthConfig> ignored =
        mockConstruction(PiveauAuthConfig.class, (mockCfg, ctx) -> {
          KeycloakTokenServerConfig kc = mock(KeycloakTokenServerConfig.class);
          when(mockCfg.getTokenServerConfig()).thenReturn(kc);
        });
        MockedConstruction<KeycloakResourceHelper> helperC =
            mockConstruction(KeycloakResourceHelper.class);
        MockedStatic<PiveauAuth> ignored1 = mockStatic(PiveauAuth.class)) {

      when(PiveauAuth.create(eq(vertx), any(PiveauAuthConfig.class)))
          .thenReturn(Future.failedFuture("create failed"));

      final boolean[] called = {false};
      KeyCloakServiceImpl svc = new KeyCloakServiceImpl(vertx, cfg, ar -> {
        called[0] = true;
        assertTrue(ar.succeeded());
      });

      assertNotNull(svc);
      assertTrue(called[0]);
      assertEquals(1, helperC.constructed().size());
    }
  }

  @Test
  void constructor_noKeycloakConfigured_callsReadyViaElseBranch(Vertx vertx) {
    // No construction mocking here — rely on real PiveauAuthConfig with empty data
    JsonObject cfg = new JsonObject()
        .put(Constants.ENV_PIVEAU_HUB_ODP_URL_TEMPLATE, URL_TEMPLATE)
        .put(Constants.ENV_PIVEAU_HUB_AUTHORIZATION_PROCESS_DATA, new JsonObject());

    final boolean[] called = {false};
    KeyCloakServiceImpl svc = new KeyCloakServiceImpl(vertx, cfg, ar -> {
      called[0] = true;
      assertTrue(ar.succeeded());
      assertNotNull(ar.result());
    });

    assertNotNull(svc);
    assertTrue(called[0]);
  }

  // ====================== Method tests ======================
  @Test
  void createResource_withFeatureFlagEnabled_addsRedirectAndChainsAllSteps() {
    enableFlag("open_data_presence");
    when(helper.addAsValidRedirectUrl(TOKEN, "https://example.org/" + CATALOGUE_ID))
        .thenReturn(Future.succeededFuture(1));

    KeyCloakService out = service.createResource(CATALOGUE_ID);
    assertNotNull(out);

    verify(piveauAuth).requestClientToken();
    verify(helper).createGroupIfNotExists(TOKEN, CATALOGUE_ID);
    verify(helper).createResourceIfNotExist(eq(TOKEN), eq(CATALOGUE_ID), eq("catalogue"),
        eq(CATALOGUE_ID), any(), eq(true), any());
    verify(helper).createGroupPolicyIfNotExists(eq(TOKEN), eq(CATALOGUE_ID), eq(CATALOGUE_ID),
        any());
    verify(helper).addAsValidRedirectUrl(TOKEN, "https://example.org/" + CATALOGUE_ID);
  }

  @Test
  void createResource_withFeatureFlagDisabled_skipsRedirect() {
    disableFlag("open_data_presence");

    service.createResource(CATALOGUE_ID);

    verify(piveauAuth).requestClientToken();
    verify(helper).createGroupIfNotExists(TOKEN, CATALOGUE_ID);
    verify(helper).createResourceIfNotExist(eq(TOKEN), eq(CATALOGUE_ID), eq("catalogue"),
        eq(CATALOGUE_ID), any(), eq(true), any());
    verify(helper).createGroupPolicyIfNotExists(eq(TOKEN), eq(CATALOGUE_ID), eq(CATALOGUE_ID),
        any());
    verify(helper, never()).addAsValidRedirectUrl(any(), any());
  }

  @Test
  void createResource_tokenFailure_doesNothing() {
    when(piveauAuth.requestClientToken()).thenReturn(Future.failedFuture("boom"));

    service.createResource(CATALOGUE_ID);

    verify(helper, never()).createGroupIfNotExists(any(), any());
    verify(helper, never()).createResourceIfNotExist(any(), any(), any(), any(), any(),
        anyBoolean(), any());
    verify(helper, never()).createGroupPolicyIfNotExists(any(), any(), any(), any());
    verify(helper, never()).addAsValidRedirectUrl(any(), any());
  }

  @Test
  void createResource_noPiveauAuth_doesNothing() throws Exception {
    setPrivate(service, "piveauAuth", null);

    service.createResource(CATALOGUE_ID);

    verify(helper, never()).createGroupIfNotExists(any(), any());
  }

  @Test
  void deleteResource_withFeatureFlagEnabled_removesRedirect() {
    enableFlag("open_data_presence");
    when(helper.removeAsValidRedirectUrl(TOKEN, "https://example.org/" + CATALOGUE_ID))
        .thenReturn(Future.succeededFuture());

    service.deleteResource(CATALOGUE_ID);

    verify(piveauAuth).requestClientToken();
    verify(helper).deleteGroup(TOKEN, CATALOGUE_ID);
    verify(helper).deleteResource(TOKEN, CATALOGUE_ID);
    verify(helper).removeAsValidRedirectUrl(TOKEN, "https://example.org/" + CATALOGUE_ID);
  }

  @Test
  void deleteResource_withFeatureFlagDisabled_skipsRedirectRemoval() {
    disableFlag("open_data_presence");

    service.deleteResource(CATALOGUE_ID);

    verify(piveauAuth).requestClientToken();
    verify(helper).deleteGroup(TOKEN, CATALOGUE_ID);
    verify(helper).deleteResource(TOKEN, CATALOGUE_ID);
    verify(helper, never()).removeAsValidRedirectUrl(any(), any());
  }

  @Test
  void deleteResource_tokenFailure_doesNothing() {
    when(piveauAuth.requestClientToken()).thenReturn(Future.failedFuture("nope"));

    service.deleteResource(CATALOGUE_ID);

    verify(helper, never()).deleteGroup(any(), any());
    verify(helper, never()).deleteResource(any(), any());
    verify(helper, never()).removeAsValidRedirectUrl(any(), any());
  }

  @Test
  void deleteResource_noPiveauAuth_doesNothing() throws Exception {
    setPrivate(service, "piveauAuth", null);

    service.deleteResource(CATALOGUE_ID);

    verify(helper, never()).deleteGroup(any(), any());
  }

  // --- helpers ---
  private static void setPrivate(Object target, String field, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static void enableFlag(String name) {
    JsonObject cfg = FeatureFlags.getConfig();
    cfg.put(name, true);
    FeatureFlags.setConfig(cfg);
  }

  private static void disableFlag(String name) {
    JsonObject cfg = FeatureFlags.getConfig();
    cfg.put(name, false);
    FeatureFlags.setConfig(cfg);
  }

  private static void resetFlags() {
    FeatureFlags.setConfig(new JsonObject());
  }
}
