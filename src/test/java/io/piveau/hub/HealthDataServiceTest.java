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
package io.piveau.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.piveau.hub.services.healthdata.HealthDataService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("HealthDataService Test Suite")
@ExtendWith(VertxExtension.class)
public class HealthDataServiceTest {

    private static final String BASE_URL = "http://mocked-url.com/";

    private HealthDataService healthDataService;
    private WebClient mockClient;

    @BeforeEach
    void setUp(Vertx vertx) throws Exception {
        healthDataService = new HealthDataService(vertx);

        // Inject mocked WebClient and base URL using reflection
        mockClient = mock(WebClient.class);
        setPrivateField(healthDataService, "client", mockClient);
        setPrivateField(healthDataService, "healthCategoriesVocabularyBaseUrl", BASE_URL);
    }

    @Test
    @DisplayName("Should handle missing label gracefully in Wikidata response")
    void testFetchWikidataInfoNoLabel(VertxTestContext testContext) {
        String wikidataUrl = "https://www.wikidata.org/wiki/Q12345";
        String propertyId = "Q12345";
        String endpoint = "https://www.wikidata.org/w/api.php";

        JsonObject mockJson = new JsonObject()
                .put("entities", new JsonObject()
                        .put(propertyId, new JsonObject())); // No labels

        mockHttpGet(endpoint, req -> {
            HttpResponse<Buffer> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.bodyAsJsonObject()).thenReturn(mockJson);
            succeedRequest(req, response);
        });

        healthDataService.fetchWikidataInfo(wikidataUrl, false)
                .thenAccept(result -> {
                    assertEquals("No label found", result);
                    testContext.completeNow();
                })
                .exceptionally(ex -> {
                    testContext.failNow(ex);
                    return null;
                });
    }

    @Test
    @DisplayName("Should handle non-200 status for Wikidata JSON endpoint")
    void testFetchWikidataInfoNon200Status(VertxTestContext testContext) {
        String wikidataUrl = "https://www.wikidata.org/wiki/Q12345";
        String endpoint = "https://www.wikidata.org/w/api.php";

        mockHttpGet(endpoint, req -> {
            HttpResponse<Buffer> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(404);
            succeedRequest(req, response);
        });

        healthDataService.fetchWikidataInfo(wikidataUrl, true)
                .thenAccept(result -> {
                    assertNull(result);
                    testContext.completeNow();
                })
                .exceptionally(ex -> {
                    testContext.failNow(ex);
                    return null;
                });
    }

    @Test
    @DisplayName("Should handle empty alias array in Wikidata response")
    void testFetchWikidataInfoEmptyAliasArray(VertxTestContext testContext) {
        String wikidataUrl = "https://www.wikidata.org/wiki/Q12345";
        String propertyId = "Q12345";
        String endpoint = "https://www.wikidata.org/w/api.php";

        JsonObject mockJson = new JsonObject()
                .put("entities", new JsonObject()
                        .put(propertyId, new JsonObject()
                                .put("labels", new JsonObject()
                                        .put("en", new JsonObject()
                                                .put("language", "en")
                                                .put("value", "Mocked Label")))
                                .put("aliases", new JsonObject()
                                        .put("en", new JsonArray()))));

        mockHttpGet(endpoint, req -> {
            HttpResponse<Buffer> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.bodyAsJsonObject()).thenReturn(mockJson);
            succeedRequest(req, response);
        });

        healthDataService.fetchWikidataInfo(wikidataUrl, true)
                .thenAccept(jsonString -> {
                    JsonObject result = new JsonObject(jsonString);
                    assertEquals("Mocked Label", result.getString("label"));
                    assertEquals("", result.getString("alias"));
                    testContext.completeNow();
                })
                .exceptionally(ex -> {
                    testContext.failNow(ex);
                    return null;
                });
    }

    @Test
    @DisplayName("Should return Wikidata label from mocked API JSON")
    void fetchWikidataInfoReturnsLabel(VertxTestContext testContext) {
        String wikidataUrl = "https://www.wikidata.org/wiki/Q12345";
        String propertyId = "Q12345";
        String endpoint = "https://www.wikidata.org/w/api.php";

        mockHttpGet(endpoint, req -> {
            HttpResponse<Buffer> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.bodyAsJsonObject()).thenReturn(new JsonObject()
                    .put("entities", new JsonObject()
                            .put(propertyId, new JsonObject()
                                    .put("labels", new JsonObject()
                                            .put("en", new JsonObject()
                                                    .put("language", "en")
                                                    .put("value", "Mocked Label"))))));
            succeedRequest(req, response);
        });

        healthDataService.fetchWikidataInfo(wikidataUrl, false)
                .thenAccept(result -> {
                    assertEquals("Mocked Label", result);
                    testContext.completeNow();
                })
                .exceptionally(ex -> {
                    testContext.failNow(ex);
                    return null;
                });
    }

    @Test
    @DisplayName("Should return JSON string with label, description, and alias from Wikidata API")
    void testFetchWikidataInfoAsJsonString(VertxTestContext testContext) {
        String wikidataUrl = "https://www.wikidata.org/wiki/Q12345";
        String propertyId = "Q12345";
        String endpoint = "https://www.wikidata.org/w/api.php";

        // Setup mock JSON response
        JsonObject mockJson = new JsonObject()
                .put("entities", new JsonObject()
                        .put(propertyId, new JsonObject()
                                .put("labels", new JsonObject()
                                        .put("en", new JsonObject()
                                                .put("language", "en")
                                                .put("value", "Mocked Label")))
                                .put("descriptions", new JsonObject()
                                        .put("en", new JsonObject()
                                                .put("language", "en")
                                                .put("value", "Mocked Description")))
                                .put("aliases", new JsonObject()
                                        .put("en", new JsonArray()
                                                .add(new JsonObject()
                                                        .put("language", "en")
                                                        .put("value", "Mocked Alias"))))));

        // Setup mock HTTP request
        mockHttpGet(endpoint, req -> {
            HttpResponse<Buffer> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.bodyAsJsonObject()).thenReturn(mockJson);
            succeedRequest(req, response);
        });

        // Call fetchWikidataInfo with fetchJson = true (to trigger getWikiDataInfoAsJsonString)
        healthDataService.fetchWikidataInfo(wikidataUrl, true)
                .thenAccept(jsonString -> {
                    JsonObject result = new JsonObject(jsonString);
                    assertEquals("Mocked Label", result.getString("label"));
                    assertEquals("Mocked Description", result.getString("description"));
                    assertEquals("Mocked Alias", result.getString("alias"));
                    testContext.completeNow();
                })
                .exceptionally(ex -> {
                    testContext.failNow(ex);
                    return null;
                });
    }

    private void mockHttpGet(String url, Consumer<HttpRequest<Buffer>> setupRequest) {
        HttpRequest<Buffer> request = mock(HttpRequest.class);
        when(mockClient.getAbs(url)).thenReturn(request);
        when(request.timeout(anyLong())).thenReturn(request);
        when(request.addQueryParam(anyString(), anyString())).thenReturn(request);
        setupRequest.accept(request);
    }

    private void succeedRequest(HttpRequest<Buffer> request, HttpResponse<Buffer> response) {
        doAnswer(invocation -> {
            Handler<AsyncResult<HttpResponse<Buffer>>> handler = invocation.getArgument(0);
            handler.handle(Future.succeededFuture(response));
            return null;
        }).when(request).send(any());
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
