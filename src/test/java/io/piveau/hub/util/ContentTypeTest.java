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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ContentTypeTest {

  @Test
  void defaultIsJsonLd() {
    assertEquals(ContentType.JSON_LD, ContentType.DEFAULT);
    assertEquals("application/ld+json", ContentType.DEFAULT.getMimeType());
    assertEquals("jsonld", ContentType.DEFAULT.getFileExtension());
  }

  @Test
  void allEnumValuesHaveMappings() {
    for (ContentType ct : ContentType.values()) {
      // Mime type and file ext are non-null
      assertNotNull(ct.getMimeType());
      assertNotNull(ct.getFileExtension());

      // Each file extension resolves back to the enum
      assertEquals(ct, ContentType.valueOfFileExtension(ct.getFileExtension()));

      // Each mime type resolves back to the enum
      assertEquals(ct, ContentType.valueOfMimeType(ct.getMimeType()));
    }
  }

  @Test
  void valueOfFileExtensionReturnsNullForUnknown() {
    assertNull(ContentType.valueOfFileExtension("doesnotexist"));
  }

  @Test
  void valueOfMimeTypeReturnsNullForUnknown() {
    assertNull(ContentType.valueOfMimeType("application/doesnotexist"));
  }

  @Test
  void getFileExtensionsContainsAllExtensions() {
    Set<String> extensions = ContentType.getFileExtensions();
    for (ContentType ct : ContentType.values()) {
      assertTrue(extensions.contains(ct.getFileExtension()));
    }
  }
}
