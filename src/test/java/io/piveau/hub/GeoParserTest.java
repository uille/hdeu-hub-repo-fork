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

import io.piveau.hub.util.rdf.GeoParser;
import io.piveau.hub.util.rdf.GeoParsingException;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Testing geo parsing")
class GeoParserTest {

    @Test
    @DisplayName("Parsing WKT")
    void testWKTtoGeoJSON() {
        GeoParser geoParser = new GeoParser();
        String match1 = "{\n" +
                "  \"type\" : \"Polygon\",\n" +
                "  \"coordinates\" : [ [ [ 13.66, 52.89 ], [ 13.83, 52.89 ], [ 13.83, 52.79 ], [ 13.66, 52.79 ], [ 13.66, 52.89 ] ] ]\n" +
                "}";

        String input1 = "POLYGON((13.66 52.89,13.83 52.89,13.83 52.79, 13.66 52.79,13.66 52.89))";

        String match2 = "{\n" +
                "    \"type\": \"Point\", \n" +
                "    \"coordinates\": [50, 50]\n" +
                "}";
        String input2 = "Point(50 50)";
        try {
            JsonObject result1 = geoParser.WKTtoGeoJSON(input1);
            assertEquals(result1, new JsonObject(match1));
        } catch (GeoParsingException e) {
            assert false : e.getMessage();
        }

        try {
            JsonObject result2 = geoParser.WKTtoGeoJSON(input2);
            assertEquals(result2, new JsonObject(match2));
        } catch (GeoParsingException e) {
            assert false : e.getMessage();
        }
    }

    @Test
    @DisplayName("Parsing GML3")
    void testGML3toGeoJSON() {
        GeoParser geoParser = new GeoParser();
        String match1 = "{\n" +
                "  \"type\" : \"Polygon\",\n" +
                "  \"coordinates\" : [ [ [ 12.915, 53.1485 ], [ 12.915, 53.1985 ], [ 12.9983, 53.1985 ], [ 12.9983, 53.1485 ], [ 12.915, 53.1485 ] ] ]\n" +
                "}";
        // language=XML
        String input1 = "<gml:Envelope srsName=\"http://www.opengis.net/def/EPSG/0/4326\"><gml:lowerCorner>53.1485 12.915</gml:lowerCorner><gml:upperCorner>53.1985 12.9983</gml:upperCorner></gml:Envelope>";
        try {
            JsonObject result1 = geoParser.GML3toGeoJSON(input1);
            assertEquals(result1, new JsonObject(match1));
        } catch (GeoParsingException e) {
            assert false : e.getMessage();
        }
    }

}
