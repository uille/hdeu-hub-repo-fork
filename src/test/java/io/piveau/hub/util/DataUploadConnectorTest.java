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
package io.piveau.hub.util;

import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.piveau.dcatap.DCATAPUriRef;
import io.piveau.dcatap.DCATAPUriSchema;
import io.piveau.hub.dataobjects.DatasetHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.junit.jupiter.api.Test;

class DataUploadConnectorTest {

  @Test
  void getDataURL_concatenatesCorrectly() {
    WebClient client = mock(WebClient.class);
    JsonObject cfg = new JsonObject()
        .put("url", "https://data.example")
        .put("service_url", "https://svc.example")
        .put("api_key", "apik");
    DataUploadConnector conn = DataUploadConnector.create(client, cfg);

    assertEquals("https://data.example/v1/data/dist-123", conn.getDataURL("dist-123"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void getResponse_buildsArray_filtersByAccessUrl_andCallsService() {
    // --- WebClient + HttpRequest mocks
    // --- WebClient + HttpRequest mocks
    WebClient client = mock(WebClient.class);
    HttpRequest<Buffer> request = mock(HttpRequest.class);

    when(client.putAbs(anyString())).thenReturn(request);
    when(request.putHeader(anyString(), anyString())).thenReturn(request);

// Capture payload and invoke the handler with a fake 200 response
    AtomicReference<Object> sentBody = new AtomicReference<>();
    doAnswer(inv -> {
      Object body = inv.getArgument(0);            // the JSON payload you send
      @SuppressWarnings("unchecked")
      var handler = (io.vertx.core.Handler<AsyncResult<HttpResponse>>) inv.getArgument(1);

      sentBody.set(body);

      HttpResponse<Buffer> httpResp = mock(HttpResponse.class);
      when(httpResp.statusCode()).thenReturn(200);
      handler.handle(Future.succeededFuture(httpResp));  // simulate success

      return null; // void method
    }).when(request).sendJson(any(), any());

    // --- SUT config
    String baseUrl = "https://data.example";
    String serviceUrl = "https://svc.example";
    String apiKey = "super-secret";

    JsonObject cfg = new JsonObject()
        .put("url", baseUrl)
        .put("service_url", serviceUrl)
        .put("api_key", apiKey);

    DataUploadConnector connector = DataUploadConnector.create(client, cfg);

    // --- Build Jena model with a dataset and two distributions
    Model m = createDefaultModel();
    Resource dataset = m.createResource("https://hub.example/datasets/ds-1");

    // Distribution 1 (eligible: accessURL contains baseUrl)
    DCATAPUriRef dist1Ref = DCATAPUriSchema.createFor("dist-1", DCATAPUriSchema.getDistributionContext());
    Resource dist1 = m.createResource(dist1Ref.getUri());
    dist1.addProperty(DCAT.accessURL, m.createResource(baseUrl + "/download/1"));
    dist1.addProperty(DCTerms.title, "Dist One");
    dist1.addProperty(DCTerms.identifier, "d1");
    dist1.addLiteral(DCTerms.format, "csv");
    dataset.addProperty(DCAT.distribution, dist1);

    // Distribution 2 (filtered out: other host in accessURL)
    DCATAPUriRef dist2Ref = DCATAPUriSchema.createFor("dist-2", DCATAPUriSchema.getDistributionContext());
    Resource dist2 = m.createResource(dist2Ref.getUri());
    dist2.addProperty(DCAT.accessURL, m.createResource("https://other.example/download/2"));
    dataset.addProperty(DCAT.distribution, dist2);

    // --- Mock DatasetHelper so resource() returns our dataset
    DatasetHelper helper = mock(DatasetHelper.class);
    when(helper.resource()).thenReturn(dataset);

    // --- Exercise
    JsonArray result = connector.getResponse(helper);

    // --- Verify returned response
    assertEquals(1, result.size(), "Only distributions with allowed access_url should be included");
    JsonObject first = result.getJsonObject(0);
    assertEquals(dist1Ref.getId(), first.getString("id"));
    assertEquals(baseUrl + "/download/1", first.getString("access_url"));
    assertNotNull(first.getString("upload_token"));
    assertTrue(first.getString("upload_url").startsWith(first.getString("access_url") + "?token="));

    // --- Verify Data Service call
    verify(client).putAbs(eq(serviceUrl + "/v1/data/"));
    verify(request).putHeader(eq("Authorization"), eq(apiKey));
    verify(request).putHeader(eq("Content-Type"), eq("application/json"));
    verify(request).sendJson(any(), any());

    // Payload shape: JsonArray of { id, token }
    assertNotNull(sentBody.get(), "Expected a JSON array payload to be sent");
    assertTrue(sentBody.get() instanceof JsonArray, "Payload must be a JsonArray");
    JsonArray payload = (JsonArray) sentBody.get();
    assertEquals(1, payload.size(), "Only the eligible distribution should be sent");
    JsonObject payloadItem = payload.getJsonObject(0);
    assertEquals(dist1Ref.getId(), payloadItem.getString("id"));
    String token = payloadItem.getString("token");
    assertNotNull(token);
    assertFalse(token.isBlank());
  }
}
