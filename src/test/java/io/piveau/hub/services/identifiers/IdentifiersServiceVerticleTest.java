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
package io.piveau.hub.services.identifiers;

import static io.piveau.hub.Constants.ENV_PIVEAU_TRIPLESTORE_CONFIG;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.piveau.test.MockTripleStore;
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

@ExtendWith(VertxExtension.class)
class IdentifiersServiceVerticleTest {

  @Test
  void testDeployment(Vertx vertx) throws Exception {
    JsonObject cfg = new JsonObject()
        .put(ENV_PIVEAU_TRIPLESTORE_CONFIG, MockTripleStore.getDefaultConfig());

    // Deploy
    CountDownLatch deployed = new CountDownLatch(1);
    final String[] depId = new String[1];
    vertx.deployVerticle(
        IdentifiersServiceVerticle::new,
        new DeploymentOptions().setConfig(cfg),
        ar -> {
          if (ar.succeeded()) {
            depId[0] = ar.result();
          }
          deployed.countDown();
        });

    assertTrue(deployed.await(5, TimeUnit.SECONDS), "Deployment did not complete");
    assertNotNull(depId[0], "Verticle deployment failed");

    var proxy = IdentifiersService.createProxy(vertx, IdentifiersService.SERVICE_ADDRESS);
    assertNotNull(proxy);

    // Prove a consumer is listening on the service address.
    DeliveryOptions opts = new DeliveryOptions()
        .addHeader("action", "mintIdentifier")
        .setSendTimeout(200);

    CountDownLatch reqDone = new CountDownLatch(1);
    final ReplyException[] failure = new ReplyException[1];

    vertx.eventBus().request(
        IdentifiersService.SERVICE_ADDRESS,
        new JsonObject().put("entity", "test"),
        opts,
        ar -> {
          assertTrue(ar.failed(),
              "Unexpected reply — service should not reply to this ad-hoc request");
          assertInstanceOf(ReplyException.class, ar.cause(), "Expected a ReplyException");
          failure[0] = (ReplyException) ar.cause();
          reqDone.countDown();
        });

    assertTrue(reqDone.await(1, TimeUnit.SECONDS), "EventBus request did not complete in time");
    assertNotNull(failure[0]);
    assertNotEquals(ReplyFailure.NO_HANDLERS, failure[0].failureType(),
        "NO_HANDLERS indicates the service wasn't registered");

    // Clean up
    CountDownLatch undeployed = new CountDownLatch(1);
    vertx.undeploy(depId[0], ar -> undeployed.countDown());
    assertTrue(undeployed.await(5, TimeUnit.SECONDS), "Undeploy did not finish in time");
  }
}
