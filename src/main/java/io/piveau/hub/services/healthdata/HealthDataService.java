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
package io.piveau.hub.services.healthdata;

import io.piveau.hub.Constants;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthDataService {

  public static final String VALUE = "value";
  private final WebClient client;
  private final Logger logger = LoggerFactory.getLogger(getClass());


  public HealthDataService(Vertx vertx) {
    this.client = WebClient.create(vertx);
  }

  /**
   * This function calls Wikidata api with a given url and returns the request info either as string
   * or json object
   *
   * @param wikidataLink the wikidata url
   * @return the english label for the given wikidata resource url
   */
  public CompletableFuture<String> fetchWikidataInfo(String wikidataLink, boolean fetchJson) {
    CompletableFuture<String> future = new CompletableFuture<>();

    // Extract the property ID from the URL
    String linkLastPart = wikidataLink.substring(wikidataLink.lastIndexOf('/') + 1);
    String propertyId =
        linkLastPart.startsWith("Property:") ? extractPropertyId(linkLastPart) : linkLastPart;

    if (propertyId == null || propertyId.isBlank()) {
      future.completeExceptionally(new IllegalArgumentException("Invalid property URL"));
      return future;
    }

    String endpoint = "https://www.wikidata.org/w/api.php";

    // Set a default timeout of 10 seconds if the environment variable is not set
    int timeoutInMillis =
        Optional.ofNullable(System.getenv(Constants.ENV_PIVEAU_HEALTHDATA_ENDPOINTS_TIMEOUT))
            .map(s -> {
              try {
                return Integer.parseInt(s);
              } catch (NumberFormatException e) {
                return 10000;
              }
            })
            .orElse(10000);

    client.getAbs(endpoint)
        .addQueryParam("action", "wbgetentities")
        .addQueryParam("ids", propertyId)
        .addQueryParam("format", "json")
        .timeout(timeoutInMillis) // Ensure request times out if Wikidata is unresponsive
        .send(ar -> {
          if (ar.succeeded()) {
            HttpResponse<io.vertx.core.buffer.Buffer> response = ar.result();
            if (response.statusCode() == 200) {
              JsonObject body = response.bodyAsJsonObject();
              String result =
                  fetchJson ? getWikiDataInfoAsJsonString(body) : getWikiDataLabelString(body);
              future.complete(result);
            } else {
              logger.error("Wikidata API error, returned status {} {}", response.statusCode(),
                  response.statusMessage());
              future.complete(null);
            }
          } else {
            logger.error(
                "Failed to fetch health category description from vocabulary in timely manner (5 seconds)",
                ar.cause());
            future.complete(null);
          }
        });

    return future;
  }

  /**
   * This function gets a property url (e.g. "https://www.wikidata.org/wiki/Property:P1690") and
   * extracts the property (e.g. P1690)
   *
   * @param propertyUrl the property url
   * @return extracted property (e.g. P1690)
   */
  private String extractPropertyId(String propertyUrl) {
    String[] parts = propertyUrl.split(":");
    return parts.length > 0 ? parts[parts.length - 1] : null;
  }

  /**
   * This function extracts the english label from the wikidata response
   *
   * @param json the wikidata json response
   * @return the english label value as String
   */
  private String getWikiDataLabelString(JsonObject json) {
    JsonObject entities = json.getJsonObject("entities");
    if (entities != null) {
      for (String key : entities.fieldNames()) {
        JsonObject entity = entities.getJsonObject(key);
        JsonObject labels = entity.getJsonObject("labels");
        if (labels != null) {
          JsonObject label = labels.getJsonObject("en"); // English label
          if (label != null) {
            return label.getString(VALUE);
          }
        }
      }
    }
    return "No label found";
  }

  /**
   * This function extracts the english label, description and alias (abbreviation) from the
   * wikidata response
   *
   * @param json the wikidata json response
   * @return the english label, description and alias as key-value Json String
   */

  private String getWikiDataInfoAsJsonString(JsonObject json) {
    JsonObject entities = json.getJsonObject("entities");
    JsonObject jsonObjectBuilder = new JsonObject();
    boolean labelFound = false;
    boolean descriptionFound = false;
    boolean aliasFound = false;

    if (entities != null) {
      for (String key : entities.fieldNames()) {
        JsonObject entity = entities.getJsonObject(key);
        JsonObject labels = entity.getJsonObject("labels");
        JsonObject descriptions = entity.getJsonObject("descriptions");
        JsonObject aliases = entity.getJsonObject("aliases");

        // Fetch English label
        if (labels != null) {
          JsonObject label = labels.getJsonObject("en"); // English label
          if (label != null) {
            jsonObjectBuilder.put("label", label.getString(VALUE));
            labelFound = true;
          }
        }

        // Fetch English description
        if (descriptions != null) {
          JsonObject description = descriptions.getJsonObject("en"); // English label
          if (description != null) {
            jsonObjectBuilder.put("description", description.getString(VALUE));
            descriptionFound = true;
          }
        }

        // Fetch English alias
        if (aliases != null) {
          JsonArray aliasArray = aliases.getJsonArray("en"); // English aliases
          if (aliasArray != null && !aliasArray.isEmpty()) {
            // Get the first alias if available
            JsonObject firstAlias = aliasArray.getJsonObject(0);
            if (firstAlias != null) {
              jsonObjectBuilder.put("alias", firstAlias.getString(VALUE));
              aliasFound = true;
            }
          }
        }
      }
    }

    // If no label was found, add the empty value
    if (!labelFound) {
      jsonObjectBuilder.put("label", "");
    }

    // If no description was found, add the empty value
    if (!descriptionFound) {
      jsonObjectBuilder.put("description", "");
    }

    // If no alias was found, add the empty value
    if (!aliasFound) {
      jsonObjectBuilder.put("alias", "");
    }

    return jsonObjectBuilder.toString();
  }
}
