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
 * Tests for DatasetLogger prefix formatting and SLF4J delegation.
 */
class DatasetLoggerTest {

  private Logger slf4j;

  @BeforeEach
  void setUp() {
    slf4j = mock(Logger.class);
    when(slf4j.isInfoEnabled()).thenReturn(true);
    when(slf4j.isDebugEnabled()).thenReturn(true);
  }

  /** Helper to build a DatasetLogger and inject the mock SLF4J logger. */
  private DatasetLogger newLogger(String datasetId) {
    // Ensure deterministic base URI in prefix
    PiveauLogger.setBaseUri("https://example/base/");
    DatasetLogger logger = new DatasetLogger(datasetId, DatasetLoggerTest.class);
    // inject mock underlying logger (field is package-private in same package)
    logger.log = slf4j;
    return logger;
  }

  @Test
  void prefix_withDatasetId_usesDatasetFormat() {
    DatasetLogger logger = newLogger("ds-123");
    String prefix = logger.getPrefixMessage(); // protected in superclass

    // formatString when datasetId non-empty: "[%s (piveau-hub)] [%s (-)] [%s] "
    String expected = "[DatasetLoggerTest (piveau-hub)] [ds-123 (-)] [https://example/base/] ";
    assertEquals(expected, prefix);
  }

  @Test
  void info_delegatesWithPrefixedMessage() {
    DatasetLogger logger = newLogger("ds-9");
    logger.info("hello");

    verify(slf4j).info(logger.getPrefixMessage() + "hello");
  }

  @Test
  void debug_format_delegatesWithPrefixAndArg() {
    DatasetLogger logger = newLogger("ds-9");
    logger.debug("val={}", 42);

    verify(slf4j).debug(logger.getPrefixMessage() + "val={}", 42);
  }
}
