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
import java.io.StringReader;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.SKOS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthDataService {

  public static final String VALUE = "value";
  private final WebClient client;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final String healthCategoriesVocabularyBaseUrl;

  public HealthDataService(Vertx vertx) {
    this.client = WebClient.create(vertx);
    // Fetch the base URL from the environment variable or use a default value
    this.healthCategoriesVocabularyBaseUrl = Objects.requireNonNullElse(
        System.getenv(Constants.ENV_HUB_REPO_HEALTH_CATEGORIES_VOCABULARY_BASE_URL), null);
  }

  /**
   * This function returns the description (skos:prefLabel) for a given health category from the
   * draft controlled vocabulary implementation
   *
   * @param healthCategory the healthdcat-ap health category
   * @return the category description
   */
  public CompletableFuture<String> fetchHealthCategoryDescriptionFromVocabulary(
      String healthCategory) {
    CompletableFuture<String> future = new CompletableFuture<>();

    if (Objects.isNull(this.healthCategoriesVocabularyBaseUrl)) {
      logger.error("Health Category retrieval error: Environment variable for base url is not set");
      future.complete("");
    }

    String endpoint = this.healthCategoriesVocabularyBaseUrl + healthCategory;

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
        .timeout(
            timeoutInMillis) // Ensure request times out if health categories vocabulary service is unresponsive
        .send(ar -> {
          if (ar.succeeded()) {
            HttpResponse<io.vertx.core.buffer.Buffer> response = ar.result();
            String contentType = response.getHeader("Content-Type");
            if (contentType == null || !isRdfContentType(contentType)) {
              logger.error("Health Category retrieval error: unexpected response content type {}",
                  contentType);
              future.complete(""); // Fallback value
            }
            if (response.statusCode() == 200) {
              String healthCategoryRdf = response.bodyAsString();
              String result = extractEnglishPrefLabel(healthCategoryRdf, endpoint);
              future.complete(result);
            } else {
              // Handle non-200 responses gracefully
              logger.error("Health Category retrieval error: response {} {}",
                  response.statusCode(), response.statusMessage());
              future.complete(""); // Fallback value
            }
          } else {
            logger.error("Health Category retrieval error, unsuccessful request {}",
                ar.cause().getMessage());
            future.complete(""); // Fallback value
          }
        });

    return future;
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

  /**
   * @param rdfXml      the rdf xml string
   * @param resourceUri the resource in the rdf for which we want to find the english
   *                    skos:prefLabel
   * @return the english skos:prefLabel
   */
  private static String extractEnglishPrefLabel(String rdfXml, String resourceUri) {
    // Parse RDF from XML string using Apache Jena
    Model model = ModelFactory.createDefaultModel();
    model.read(new StringReader(rdfXml), null, "RDF/XML");

    Resource resource = model.getResource(resourceUri);
    // Iterate over all skos:prefLabel and find the one with xml:lang="en"
    StmtIterator labels = resource.listProperties(SKOS.prefLabel);
    while (labels.hasNext()) {
      var labelStatement = labels.nextStatement();
      var label = labelStatement.getObject();
      var lang = label.asLiteral().getLanguage();
      // Check if the language is English
      if ("en".equals(lang)) {
        return label.asLiteral().getString(); // Return the label if found
      }
    }
    return null;
  }

  private boolean isRdfContentType(String contentType) {
    // Normalize content type to lowercase and remove extra spaces
    String normalizedContentType = contentType.toLowerCase().trim();

    // Allow known RDF content types
    return normalizedContentType.contains("rdf+xml") ||
        normalizedContentType.contains("turtle") ||
        normalizedContentType.contains("ld+json") ||
        normalizedContentType.contains("n-triples") ||
        normalizedContentType.contains("n-quads") ||
        normalizedContentType.contains("n3") ||
        normalizedContentType.contains("xml");  // Allow 'text/xml' as RDF/XML workaround
  }
}
