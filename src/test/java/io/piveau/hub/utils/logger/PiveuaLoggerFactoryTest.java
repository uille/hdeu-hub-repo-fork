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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import io.piveau.hub.dataobjects.DatasetHelper;
import org.junit.jupiter.api.Test;

class PiveauLoggerFactoryTest {

  @Test
  void getLogger_withNullHelper_returnsSimpleLogger() {
    PiveauLogger logger = PiveauLoggerFactory.getLogger((DatasetHelper) null, PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    assertEquals(SimpleLogger.class, logger.getClass());
  }

  @Test
  void getLogger_withNonNullHelper_returnsBasePiveauLogger() {
    DatasetHelper helper = mock(DatasetHelper.class); // we don't care about internals here
    PiveauLogger logger = PiveauLoggerFactory.getLogger(helper, PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    // Factory creates a plain PiveauLogger (not Dataset/Catalogue/Simple)
    assertEquals(PiveauLogger.class, logger.getClass());
  }

  @Test
  void getLogger_withDatasetAndCatalogue_returnsBasePiveauLogger() {
    PiveauLogger logger = PiveauLoggerFactory.getLogger("ds-1", "cat-1", PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    assertEquals(PiveauLogger.class, logger.getClass());
  }

  @Test
  void getLogger_withBothEmpty_returnsSimpleLogger() {
    PiveauLogger logger = PiveauLoggerFactory.getLogger("", "", PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    assertEquals(SimpleLogger.class, logger.getClass());
  }

  @Test
  void getLogger_withBothNull_returnsSimpleLogger() {
    PiveauLogger logger = PiveauLoggerFactory.getLogger(null, null, PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    assertEquals(SimpleLogger.class, logger.getClass());
  }

  @Test
  void getLogger_withOnlyDataset_returnsDatasetLogger() {
    PiveauLogger logger = PiveauLoggerFactory.getLogger("ds-1", "", PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    assertEquals(DatasetLogger.class, logger.getClass());
  }

  @Test
  void getLogger_withOnlyCatalogue_returnsCatalogueLogger() {
    PiveauLogger logger = PiveauLoggerFactory.getLogger("", "cat-1", PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    assertEquals(CatalogueLogger.class, logger.getClass());
  }

  @Test
  void getLogger_classOnly_returnsSimpleLogger() {
    PiveauLogger logger = PiveauLoggerFactory.getLogger(PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    assertEquals(SimpleLogger.class, logger.getClass());
  }

  @Test
  void getDatasetLogger_returnsDatasetLogger() {
    PiveauLogger logger = PiveauLoggerFactory.getDatasetLogger("ds-42", PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    assertEquals(DatasetLogger.class, logger.getClass());
  }

  @Test
  void getCatalogueLogger_withId_returnsCatalogueLogger() {
    PiveauLogger logger = PiveauLoggerFactory.getCatalogueLogger("cat-99", PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    assertEquals(CatalogueLogger.class, logger.getClass());
  }

  @Test
  void getCatalogueLogger_withEmptyId_returnsSimpleLogger() {
    PiveauLogger logger = PiveauLoggerFactory.getCatalogueLogger("", PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    assertEquals(SimpleLogger.class, logger.getClass());
  }

  @Test
  void getCatalogueLogger_withNullId_returnsSimpleLogger() {
    PiveauLogger logger = PiveauLoggerFactory.getCatalogueLogger(null, PiveauLoggerFactoryTest.class);
    assertNotNull(logger);
    assertEquals(SimpleLogger.class, logger.getClass());
  }
}
