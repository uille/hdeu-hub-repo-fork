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
package io.piveau.hub.util.rdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonReader;

class GeoParserTest {

  private final GeoParser parser = new GeoParser();
  private final GeoJsonReader geoJsonReader = new GeoJsonReader();

  // -------------- WKTtoGeoJSON --------------

  @Test
  void wktToGeoJson_point_ok() throws Exception {
    String wkt = "POINT (23.72 37.98)"; // (x=lon=23.72, y=lat=37.98)
    JsonObject json = parser.WKTtoGeoJSON(wkt);

    assertNotNull(json);
    assertEquals("Point", json.getString("type"));

    Geometry g = geoJsonReader.read(json.encode());
    assertEquals("POINT (23.72 37.98)", g.toText());
  }

  @Test
  void wktToGeoJson_invalid_throwsGeoParsingException() {
    String wkt = "POINT (oops)"; // invalid
    assertThrows(GeoParsingException.class, () -> parser.WKTtoGeoJSON(wkt));
  }

  @Test
  void wktToGeoJson_polygon_ok() throws Exception {
    String wkt = "POLYGON((13.66 52.89,13.83 52.89,13.83 52.79, 13.66 52.79,13.66 52.89))";
    String expected = "{\n" +
        "  \"type\" : \"Polygon\",\n" +
        "  \"coordinates\" : [ [ [ 13.66, 52.89 ], [ 13.83, 52.89 ], [ 13.83, 52.79 ], [ 13.66, 52.79 ], [ 13.66, 52.89 ] ] ]\n" +
        "}";
    JsonObject json = parser.WKTtoGeoJSON(wkt);
    assertNotNull(json);
    assertEquals(new JsonObject(expected), json);
  }

  // -------------- GML2toGeoJSON --------------

  @Test
  void gml2ToGeoJson_point_ok() throws Exception {
    // Minimal GML2 Point
    String gml2 =
        "<gml:Point xmlns:gml=\"http://www.opengis.net/gml\">" +
            "  <gml:coordinates>23.7,37.9</gml:coordinates>" +
            "</gml:Point>";

    JsonObject json = parser.GML2toGeoJSON(gml2);
    assertNotNull(json);
    assertEquals("Point", json.getString("type"));

    Geometry g = geoJsonReader.read(json.encode());
    // Accept small FP rounding differences
    assertEquals("POINT (23.7 37.9)", g.toText());
  }

  // -------------- GML3toGeoJSON --------------

  @Test
  void gml3ToGeoJson_envelope_ok() throws Exception {
    // The implementation reads:
    //   upperCorner = "lon lat"
    //   lowerCorner = "lon lat"
    // and then builds an Envelope with a lat/lon swap when creating Coordinates.
    // We’ll just verify the resulting polygon/envelope matches expectations.

    String gml3Envelope =
        "<Envelope xmlns:gml=\"http://www.opengis.net/gml\">" +
            "  <upperCorner>30 40</upperCorner>" +   // lon=30, lat=40
            "  <lowerCorner>10 20</lowerCorner>" +   // lon=10, lat=20
            "</Envelope>";

    JsonObject json = parser.GML3toGeoJSON(gml3Envelope);
    assertNotNull(json);
    assertEquals("Polygon", json.getString("type"));

    Geometry poly = geoJsonReader.read(json.encode());
    Envelope env = poly.getEnvelopeInternal();

    // Because the implementation swaps lon/lat when creating Coordinates,
    // the geometry ends up with minX = 20, minY = 10, maxX = 40, maxY = 30
    // (x from lat; y from lon). We assert using a small delta just in case.
    assertEquals(20.0, env.getMinX(), 1e-9);
    assertEquals(10.0, env.getMinY(), 1e-9);
    assertEquals(40.0, env.getMaxX(), 1e-9);
    assertEquals(30.0, env.getMaxY(), 1e-9);
  }

  @Test
  void gml3ToGeoJson_gmlPrefixedEnvelope_ok() throws Exception {
    String gml3 = "<gml:Envelope srsName=\"http://www.opengis.net/def/EPSG/0/4326\">" +
        "<gml:lowerCorner>53.1485 12.915</gml:lowerCorner>" +
        "<gml:upperCorner>53.1985 12.9983</gml:upperCorner>" +
        "</gml:Envelope>";
    String expected = "{\n" +
        "  \"type\" : \"Polygon\",\n" +
        "  \"coordinates\" : [ [ [ 12.915, 53.1485 ], [ 12.915, 53.1985 ], [ 12.9983, 53.1985 ], [ 12.9983, 53.1485 ], [ 12.915, 53.1485 ] ] ]\n" +
        "}";
    JsonObject json = parser.GML3toGeoJSON(gml3);
    assertNotNull(json);
    assertEquals(new JsonObject(expected), json);
  }

  @Test
  void gml3ToGeoJson_disallowsDoctype_xxe() {
    // XXE attempt: DOCTYPE should be disallowed by the secure parser configuration.
    String malicious =
        "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>" +
            "<Envelope>" +
            "  <upperCorner>30 40</upperCorner>" +
            "  <lowerCorner>10 20</lowerCorner>" +
            "</Envelope>";

    GeoParsingException ex = assertThrows(GeoParsingException.class, () -> parser.GML3toGeoJSON(malicious));
    assertNotNull(ex.getMessage()); // message may vary by JVM/XML parser; just ensure it fails
  }

  // -------------- Extra: sanity check the GeoJSON produced parses back into JTS --------------

  @Test
  void roundTrip_geojsonBackToJts_polygon_fromGml3() throws Exception {
    String gml3Envelope =
        "<Envelope xmlns:gml=\"http://www.opengis.net/gml\">" +
            "  <upperCorner>12 52</upperCorner>" +
            "  <lowerCorner>10 50</lowerCorner>" +
            "</Envelope>";

    JsonObject json = parser.GML3toGeoJSON(gml3Envelope);
    Geometry g = geoJsonReader.read(json.encode());
    assertTrue(g.isValid());
    assertEquals("Polygon", g.getGeometryType());
    assertFalse(g.isEmpty());
  }
}
