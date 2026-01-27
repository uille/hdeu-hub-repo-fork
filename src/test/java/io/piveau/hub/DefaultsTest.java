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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.junit5.VertxExtension;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class DefaultsTest {

  @Test
  void constants_haveExpectedValues() {
    assertEquals("You know, for metadata!", Defaults.GREETING);
    assertEquals(8080, Defaults.SERVICE_PORT);
    assertFalse(Defaults.VALIDATOR_ENABLED);
    assertFalse(Defaults.SEARCH_SERVICE_ENABLED);
    assertTrue(Defaults.LOAD_VOCABULARIES);
    assertFalse(Defaults.LOAD_VOCABULARIES_FETCH);
    assertFalse(Defaults.METRICS_HISTORY);
    assertTrue(Defaults.XML_DECLARATION);
    assertFalse(Defaults.TRANSLATION_SERVICE_ENABLED);
    assertEquals("metrics", Defaults.METRICS_PIPE);
    assertFalse(Defaults.FORCE_UPDATES);
    assertFalse(Defaults.EXPERIMENTS);

    // 20 MiB = 20 * 1024 * 1024 = 20,971,520
    long twentyMiB = 20L * 1024 * 1024;
    assertEquals(twentyMiB, Defaults.BODY_LIMIT);
  }

  @Test
  void defaultLang_isTheExpectedJenaResource() {
    Resource res = Defaults.DEFAULT_LANG;
    assertNotNull(res);
    assertTrue(res.isURIResource(), "DEFAULT_LANG should be a URI resource");
    assertEquals(
        "http://publications.europa.eu/resource/authority/language/ENG",
        res.getURI()
    );
  }

  @Test
  void odpUrlTemplate_formatsCorrectly() {
    String host = "demo";
    String url = String.format(Defaults.ODP_URL_TEMPLATE, host);
    assertEquals("https://demo.piveau.io", url);
  }

  @Test
  void classIsUtilityStyle_privateNoArgCtor() throws Exception {
    // The class should be final with a single private no-arg constructor
    assertTrue(Modifier.isFinal(Defaults.class.getModifiers()), "Class should be final");

    Constructor<?>[] ctors = Defaults.class.getDeclaredConstructors();
    assertEquals(1, ctors.length, "Should have exactly one constructor");

    Constructor<?> ctor = ctors[0];
    assertTrue(Modifier.isPrivate(ctor.getModifiers()), "Constructor should be private");
    assertEquals(0, ctor.getParameterCount(), "Constructor should be no-arg");

    // Optional: prove it *can’t* be called in normal usage (we won’t actually invoke it).
    // If you want to bump coverage, you could set accessible=true and instantiate,
    // but typical utility-class tests just assert privacy and no-arg.
  }
}
