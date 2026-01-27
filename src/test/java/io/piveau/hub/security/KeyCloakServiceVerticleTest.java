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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.piveau.hub.Constants;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests the KeyCloakServiceVerticle start-up behavior. We deploy the verticle with a minimal config
 * so KeyCloakServiceImpl initializes in the "no Keycloak configured" path and still succeeds.
 * <p>
 * We then assert the service proxy consumer is registered by sending a request with the "action"
 * header. If the consumer exists, we should get a TIMEOUT (no reply), not NO_HANDLERS.
 */
@ExtendWith(VertxExtension.class)
class KeyCloakServiceVerticleTest {

  @Test
  void testDeployment(Vertx vertx) throws Exception {
    // Minimal config: ensures KeyCloakServiceImpl constructor goes through
    // the "no Keycloak configured" branch and calls ready with success.
    JsonObject cfg = new JsonObject()
        .put(Constants.ENV_PIVEAU_HUB_ODP_URL_TEMPLATE, "https://example.org/%s")
        // present but empty, to satisfy forceJsonObject(...)
        .put(Constants.ENV_PIVEAU_HUB_AUTHORIZATION_PROCESS_DATA, new JsonObject());

    // Deploy the verticle
    CountDownLatch deployed = new CountDownLatch(1);
    final String[] deploymentId = new String[1];

    vertx.deployVerticle(
        KeyCloakServiceVerticle::new,
        new DeploymentOptions().setConfig(cfg),
        ar -> {
          if (ar.succeeded()) {
            deploymentId[0] = ar.result();
          }
          deployed.countDown();
        });

    assertTrue(deployed.await(5, TimeUnit.SECONDS), "Deployment did not complete in time");
    assertNotNull(deploymentId[0], "Verticle deployment failed");

    // Prove a consumer is actually on the Event Bus at the expected address.
    // We use the service-proxy convention: "action" header with a method name.
    // If the consumer exists but doesn't reply, we get TIMEOUT (good for our check).
    // If NO consumer is registered, we get NO_HANDLERS immediately.
    DeliveryOptions opts = new DeliveryOptions()
        .addHeader("action", "createResource")
        .setSendTimeout(200);

    CountDownLatch checked = new CountDownLatch(1);
    final ReplyException[] failure = new ReplyException[1];

    vertx.eventBus().request(
        KeyCloakService.SERVICE_ADDRESS,
        new JsonObject().put("catalogueId", "cat-1"),
        opts,
        ar -> {
          // We expect a failure (no reply) BUT NOT NO_HANDLERS.
          assertTrue(ar.failed(), "Unexpected reply — service should not respond to this request");
          assertInstanceOf(ReplyException.class, ar.cause(), "Expected ReplyException");
          failure[0] = (ReplyException) ar.cause();
          checked.countDown();
        });

    assertTrue(checked.await(2, TimeUnit.SECONDS), "EventBus request did not complete in time");

    // If the consumer exists, the failure should be TIMEOUT (or a service error), but NOT NO_HANDLERS
    assertNotNull(failure[0], "Expected a failure cause");
    assertNotEquals(
        ReplyFailure.NO_HANDLERS,
        failure[0].failureType(),
        "NO_HANDLERS indicates the service wasn't registered");

    // Optional: also create a proxy and call a method (fire-and-forget) just to ensure no exceptions
    KeyCloakService proxy = KeyCloakService.createProxy(vertx, KeyCloakService.SERVICE_ADDRESS);
    assertNotNull(proxy, "Proxy should be creatable");
    assertDoesNotThrow(() -> proxy.createResource("cat-2"));
    assertDoesNotThrow(() -> proxy.deleteResource("cat-2"));

    // Clean up the deployment explicitly (teardown will close Vert.x anyway)
    CountDownLatch undeployed = new CountDownLatch(1);
    vertx.undeploy(deploymentId[0], ar -> undeployed.countDown());
    assertTrue(undeployed.await(5, TimeUnit.SECONDS), "Undeploy did not finish in time");
  }
}
