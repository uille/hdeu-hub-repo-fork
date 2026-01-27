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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class MockRegistryTest {

  private MockRegistry registry;

  @BeforeEach
  void setUp(Vertx vertx) {
    WebClient webClient = mock(WebClient.class);
    registry = new MockRegistry(vertx, webClient, new JsonObject());
  }

  @Test
  void getName_returnsExpected() {
    assertEquals("Mock Identity Registry", registry.getName());
  }

  @Test
  void getIdentifier_returnsPopulatedIdentifier() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    final Identifier[] out = new Identifier[1];

    registry.getIdentifier("http://example/ds/1", null, List.of())
        .onComplete(ar -> {
          assertTrue(ar.succeeded());
          out[0] = ar.result();
          latch.countDown();
        });

    assertTrue(latch.await(1, TimeUnit.SECONDS), "Future did not complete in time");
    Identifier id = out[0];
    assertNotNull(id);

    // hard-coded by implementation
    assertEquals("http://local.io/doi/abcd/12345678", id.identifierURI);
    assertEquals("http://piveau.eu/creator", id.creatorURI);
    assertEquals("abcd/12345678", id.identifier);
    assertEquals("http://piveau.eu/identifiers/scheme", id.schema);
    assertNotNull(id.issued);
    assertEquals(ZoneOffset.UTC, id.issued.getOffset(), "issued should be in UTC");
  }

  @Test
  void getIdentifierEligibility_returnsEligibleWithNoneElement() {
    JsonObject input = new JsonObject().put("whatever", true); // content is ignored in impl
    JsonObject out = registry.getIdentifierEligibility(input);

    assertNotNull(out);
    assertEquals("Mock Identity Registry", out.getString("identifierName"));
    assertTrue(out.getBoolean("isEligible"));

    JsonObject element = out.getJsonObject("element");
    assertNotNull(element);
    assertTrue(element.getBoolean("none"));
  }
}
