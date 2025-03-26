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
package io.piveau.hub.license;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseParserTest {

  @TempDir
  private Path tempDir;

  /**
   * Tests that LicenseParser generates the markdown file successfully when all input files are
   * present.
   */
  @Test
  void testLicenseParser() throws IOException {
    // Arrange
    setupDirectories();

    // Act: Execute the main method of LicenseParser
    LicenseParser.main(new String[]{});

    // Assert: Check that the markdown file is generated
    assertTrue(Files.exists(Path.of("notice.md")), "Markdown file should exist");
  }

  /**
   * Creates the directory structure expected by LicenseParser inside a temporary folder and changes
   * the current working directory to that folder.
   */
  private void setupDirectories() throws IOException {
    // Create the license directory
    Path generatedSources = tempDir.resolve("target/generated-sources/license");
    Files.createDirectories(generatedSources);

    // Create the "THIRD-PARTY.txt" file
    Path thirdPartyFile = generatedSources.resolve("THIRD-PARTY.txt");
    Files.writeString(thirdPartyFile, "(info) test-lib (Apache License-2.0)");

    // Create the copyright file
    Path copyrightFile = tempDir.resolve("copyright.txt");
    int currentYear = Instant.now().atZone(ZoneId.systemDefault()).getYear();
    Files.writeString(copyrightFile, "© " + currentYear + " European Union");

    // Create the licenses directory
    Path generatedResources = tempDir.resolve("target/generated-resources/licenses");
    Files.createDirectories(generatedResources);

    // Create an additional license file
    Path additionalLicenseFile = generatedResources.resolve("revised bsd license - licence.txt");
    Files.writeString(additionalLicenseFile, "BSD License");

    // Set the working directory to the temporary directory so that LicenseParser resolves the paths
    // based on this new user.dir
    System.setProperty("user.dir", tempDir.toString());
  }
}
