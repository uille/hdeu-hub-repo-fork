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
package io.piveau.hub.services.drafts;

import static io.piveau.hub.Constants.ENV_PIVEAU_SHADOW_TRIPLESTORE_CONFIG;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.piveau.test.MockTripleStore;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class DatasetDraftsServiceVerticleTest {

  @Test
  void testDeployment(Vertx vertx, VertxTestContext testContext) {
    JsonObject config = new JsonObject()
        .put(ENV_PIVEAU_SHADOW_TRIPLESTORE_CONFIG, MockTripleStore.getDefaultConfig());

    new MockTripleStore()
        .deploy(vertx)
        .compose(v -> vertx.deployVerticle(
            DatasetDraftsServiceVerticle::new,
            new DeploymentOptions().setConfig(config)))
        .onSuccess(depId -> {
            assertNotNull(depId, "Verticle deployment ID must not be null");

            // Probe the event bus: if a consumer is registered, failure type must NOT be NO_HANDLERS.
            DeliveryOptions opts =
                new DeliveryOptions().addHeader("action", "listDatasetDrafts").setSendTimeout(500);

            vertx.eventBus().request(
                DatasetDraftsService.SERVICE_ADDRESS,
                new JsonObject().put("catalogueId", "CAT"),
                opts,
                ar -> testContext.verify(() -> {
                    // We don't expect a real reply; we just care that a handler exists.
                    assertInstanceOf(ReplyException.class, ar.cause(), "Expected a ReplyException");
                    ReplyException ex = (ReplyException) ar.cause();
                    assertNotEquals(
                        ReplyFailure.NO_HANDLERS,
                        ex.failureType(),
                        "NO_HANDLERS indicates the service binder did not register a consumer");
                    testContext.completeNow();
                }));
        })
        .onFailure(testContext::failNow);
  }
}
