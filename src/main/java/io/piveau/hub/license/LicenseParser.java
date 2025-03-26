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

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LicenseParser {

  private static final Logger log = LoggerFactory.getLogger(LicenseParser.class);
  private static String inputFilePath;
  private static String outputFilePath;
  private static String licensesDirectory;
  private static String copyrightFilePath;

  public static void main(String[] args) {
    // File paths
    setFilePaths();

    // Prepare to store cleaned dependencies and licenses
    Map<String, Set<String>> dependencies = new LinkedHashMap<>();

    // Regular expression pattern to match license information
    Pattern pattern = Pattern.compile("\\(([^)]+)\\)\\s+([^(]+)\\s+\\(([^)]+)-([^\\)]+)\\)");

    try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath))) {
      String line;
      while ((line = reader.readLine()) != null) {
        // Clean dependency name and ensure only one license per dependency
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
          // Extract components: license, name, and URL
          // String license = matcher.group(1).trim()
          String name = matcher.group(2).trim();
          String licenseType = matcher.group(3).trim();
          // String url = matcher.group(4).trim()

          // Clean the dependency name by removing parentheses
          name = name.replaceAll("[()]", "").trim();

          // Combine license and name into the map (ensure no duplicates per dependency)
          dependencies.computeIfAbsent(name, k -> new HashSet<>()).add(licenseType);
        }
      }

      // Write the results to the markdown file
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {

        // 1. Add Copyright Notice at the beginning
        Path copyrightPath = Paths.get(copyrightFilePath);
        if (Files.exists(copyrightPath)) {
          writer.write(Files.readString(copyrightPath));
          writer.write("\n\n"); // Add spacing after copyright
        } else {
          writer.write("_Copyright file not found_\n\n");
        }
        writer.write("This product includes dynamically linked software developed by third parties which is provided under their respective licences:\n\n");

        // 2. Write the Third-Party Licenses
        writer.write("# Third Party Licenses\n\n");

        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
          writer.write("## " + entry.getKey() + "\n");
          writer.write("Licenses:\n");
          for (String license : entry.getValue()) {
            writer.write("- " + license + "\n");
          }
          writer.write("\n");
        }

        // 3. Append additional licenses
        appendAdditionalLicenses(writer, licensesDirectory);
      }

      log.info("Markdown file generated successfully at " + outputFilePath);
    } catch (IOException e) {
      log.error("An error occurred during the process.", e);
    }
  }

  /**
   * Initialize file paths lazily.
   */
  private static void setFilePaths() {
    String basePath = System.getProperty("user.dir");
    if (inputFilePath == null) {
      inputFilePath = Paths.get(basePath, "target", "generated-sources", "license",
          "THIRD-PARTY.txt").toString();
    }
    if (outputFilePath == null) {
      outputFilePath = Paths.get(basePath, "notice.md").toString();
    }
    if (licensesDirectory == null) {
      licensesDirectory = Paths.get(basePath, "target", "generated-resources", "licenses/")
          .toString();
    }
    if (copyrightFilePath == null) {
      copyrightFilePath = Paths.get(basePath, "copyright.txt").toString();
    }
  }

  private static void appendAdditionalLicenses(BufferedWriter writer, String licensesDirectory) throws IOException {
    Map<String, String> extraLicenses = new LinkedHashMap<>();
    extraLicenses.put("Apache License, Version 2.0", "apache license, version 2.0 - license-2.0.txt");
    extraLicenses.put("Revised BSD License", "revised bsd license - licence.txt");
    extraLicenses.put("Similar To Apache License", "similar to apache license but with the acknowledgment clause removed - license.txt");

    writer.write("## Additional Licenses\n\n");

    for (Map.Entry<String, String> entry : extraLicenses.entrySet()) {
      String licenseTitle = entry.getKey();
      String fileName = entry.getValue();
      Path filePath = Paths.get(licensesDirectory, fileName);

      writer.write("### " + licenseTitle + "\n\n");

      if (Files.exists(filePath)) {
        writer.write("```\n");
        writer.write(Files.readString(filePath));
        writer.write("\n```\n\n");
      } else {
        writer.write("_License file not found: " + fileName + "_\n\n");
      }
    }
  }
}
