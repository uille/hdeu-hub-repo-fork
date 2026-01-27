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
package io.piveau.hub.ext.trust.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class ComplianceServiceTest {

  private WebClient client;
  private ComplianceService service;

  @BeforeEach
  void setUp() {
    client = mock(WebClient.class);
    service = new ComplianceService(client);
  }

  @Test
  void getComplianceVC_success() {
    // Arrange
    HttpRequest<Buffer> request = mock(HttpRequest.class);
    HttpResponse<Buffer> response = mock(HttpResponse.class);

    JsonObject inputVC = new JsonObject().put("id", "vc1");
    JsonObject complianceVC = new JsonObject().put("result", "ok");

    when(client.postAbs(anyString())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    doAnswer(invocation -> {
      // emulate async Vert.x WebClient response
      var handler = invocation.getArgument(1);
      when(response.bodyAsJsonObject()).thenReturn(complianceVC);
      ((io.vertx.core.Handler<io.vertx.core.AsyncResult<HttpResponse<Buffer>>>) handler)
          .handle(Future.succeededFuture(response));
      return null;
    }).when(request).sendJsonObject(any(JsonObject.class), any());

    // Act
    JsonObject result = service.getComplianceVC(List.of(inputVC), "vc1").result();

    // Assert
    assertNotNull(result);
    assertEquals("ok", result.getString("result"));
    // Verify request interactions
    verify(client).postAbs(contains("vcid=vc1"));
    verify(request).putHeader(eq("Content-Type"), eq("application/json"));
  }

  @Test
  void getComplianceVC_failure() {
    // Arrange
    HttpRequest<Buffer> request = mock(HttpRequest.class);
    when(client.postAbs(anyString())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);
    doAnswer(invocation -> {
      var handler = invocation.getArgument(1);
      ((io.vertx.core.Handler<io.vertx.core.AsyncResult<HttpResponse<Buffer>>>) handler)
          .handle(Future.failedFuture(new RuntimeException("boom")));
      return null;
    }).when(request).sendJsonObject(any(JsonObject.class), any());

    // Act
    Future<JsonObject> future = service.getComplianceVC(
        List.of(new JsonObject().put("id", "vcX")), "vcX");

    // Assert
    assertTrue(future.failed());
    assertEquals("boom", future.cause().getMessage());
  }
}
