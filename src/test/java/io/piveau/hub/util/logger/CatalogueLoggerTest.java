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
package io.piveau.hub.util.logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * Tests for CatalogueLogger.
 */
class CatalogueLoggerTest {

  private Logger slf4j;

  @BeforeEach
  void setUp() {
    slf4j = mock(Logger.class);
    when(slf4j.isInfoEnabled()).thenReturn(true);
    when(slf4j.isDebugEnabled()).thenReturn(true);
  }

  /** Helper to create a CatalogueLogger with a mocked SLF4J logger. */
  private CatalogueLogger newLogger(String catalogueId) {
    PiveauLogger.setBaseUri("https://example/base/");
    CatalogueLogger logger = new CatalogueLogger(catalogueId, CatalogueLoggerTest.class);
    logger.log = slf4j; // inject mock
    return logger;
  }

  @Test
  void prefix_withCatalogueId_usesCatalogueFormat() {
    CatalogueLogger logger = newLogger("cat-1");
    String prefix = logger.getPrefixMessage();

    String expected = "[CatalogueLoggerTest (piveau-hub)] [- (cat-1)] [https://example/base/] ";
    assertEquals(expected, prefix);
  }

  @Test
  void info_delegatesWithPrefixedMessage() {
    CatalogueLogger logger = newLogger("cat-1");
    logger.info("hello");

    verify(slf4j).info(logger.getPrefixMessage() + "hello");
  }

  @Test
  void debug_format_delegatesWithPrefixAndArg() {
    CatalogueLogger logger = newLogger("cat-1");
    logger.debug("val={}", 123);

    verify(slf4j).debug(logger.getPrefixMessage() + "val={}", 123);
  }
}
