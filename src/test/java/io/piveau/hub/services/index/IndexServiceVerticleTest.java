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
package io.piveau.hub.services.index;

import static io.piveau.hub.Constants.ENV_PIVEAU_HUB_SEARCH_SERVICE;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class IndexServiceVerticleTest {

  @Test
  void testDeployment(Vertx vertx) throws Exception {
    // Outer config with nested search-service object (used via ConfigHelper.forceJsonObject)
    JsonObject cfg = new JsonObject()
        .put(ENV_PIVEAU_HUB_SEARCH_SERVICE, new JsonObject()
            .put("url", "localhost")
            .put("port", 8082)
            .put("api_key", "a92b814f-63f8-4a5d-abc7-8710de0d2e86")
        );

    // Deploy
    CountDownLatch deployed = new CountDownLatch(1);
    final String[] depId = new String[1];
    vertx.deployVerticle(
        IndexServiceVerticle::new,
        new DeploymentOptions().setConfig(cfg),
        ar -> {
          if (ar.succeeded()) {
            depId[0] = ar.result();
          }
          deployed.countDown();
        }
    );

    assertTrue(deployed.await(5, TimeUnit.SECONDS), "Deployment did not complete");
    assertNotNull(depId[0], "Verticle deployment failed");

    var proxy = IndexService.createProxy(vertx, IndexService.SERVICE_ADDRESS, 100L);
    assertNotNull(proxy);

    // Prove a consumer is listening on the service address.
    // We set an "action" header like a service-proxy request; if a consumer exists,
    // we should NOT get NO_HANDLERS. A TIMEOUT or other ReplyException is acceptable here.
    DeliveryOptions opts = new DeliveryOptions()
        .addHeader("action", "listDatasets")
        .setSendTimeout(200);

    CountDownLatch reqDone = new CountDownLatch(1);
    final ReplyException[] failure = new ReplyException[1];

    vertx.eventBus().request(
        IndexService.SERVICE_ADDRESS,
        new JsonObject().put("catalogue", "cat").put("alias", "al"),
        opts,
        ar -> {
          // We expect a failure (no reply or bad invocation), but not NO_HANDLERS
          assertTrue(ar.failed(),
              "Unexpected reply — service should not reply to this ad-hoc request");
          assertInstanceOf(ReplyException.class, ar.cause(), "Expected a ReplyException");
          failure[0] = (ReplyException) ar.cause();
          reqDone.countDown();
        }
    );

    assertTrue(reqDone.await(1, TimeUnit.SECONDS), "EventBus request did not complete in time");
    assertNotNull(failure[0]);
    assertNotEquals(ReplyFailure.NO_HANDLERS, failure[0].failureType(),
        "NO_HANDLERS indicates the service wasn't registered");

    // Clean up deployment explicitly (tearDown will close Vert.x anyway)
    CountDownLatch undeployed = new CountDownLatch(1);
    vertx.undeploy(depId[0], ar -> undeployed.countDown());
    assertTrue(undeployed.await(5, TimeUnit.SECONDS), "Undeploy did not finish in time");
  }

}
