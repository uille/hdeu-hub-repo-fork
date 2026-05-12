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
package io.piveau

import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FeatureFlagsTest {

    companion object {
        private const val FLAG_UNKNOWN   = "some-unknown-flag"
        private const val FLAG_FEATURE   = "myFeature"
        private const val FLAG_PRESENT   = "presentFlag"
        private const val FLAG_ALPHA     = "alpha"
        private const val FLAG_BETA      = "beta"
        private const val FLAG_ANYTHING  = "anything"
    }

    @BeforeEach
    fun resetConfig() {
        // Reset to empty config before each test to prevent state leakage between tests
        FeatureFlags.config = JsonObject()
    }

    @Test
    fun `isEnabled returns false by default for unknown flag`() {
        assertFalse(FeatureFlags.isEnabled(FLAG_UNKNOWN))
    }

    @Test
    fun `isEnabled returns true when flag is explicitly set to true`() {
        FeatureFlags.config = JsonObject().put(FLAG_FEATURE, true)
        assertTrue(FeatureFlags.isEnabled(FLAG_FEATURE))
    }

    @Test
    fun `isEnabled returns false when flag is explicitly set to false`() {
        FeatureFlags.config = JsonObject().put(FLAG_FEATURE, false)
        assertFalse(FeatureFlags.isEnabled(FLAG_FEATURE))
    }

    @Test
    fun `isSet returns false when flag is absent`() {
        assertFalse(FeatureFlags.isSet(FLAG_UNKNOWN))
    }

    @Test
    fun `isSet returns true when flag is present regardless of value`() {
        FeatureFlags.config = JsonObject().put(FLAG_PRESENT, true)
        assertTrue(FeatureFlags.isSet(FLAG_PRESENT))

        FeatureFlags.config = JsonObject().put(FLAG_PRESENT, false)
        assertTrue(FeatureFlags.isSet(FLAG_PRESENT))
    }

    @Test
    fun `config can be replaced and new values are reflected immediately`() {
        FeatureFlags.config = JsonObject().put(FLAG_ALPHA, true)
        assertTrue(FeatureFlags.isEnabled(FLAG_ALPHA))
        assertFalse(FeatureFlags.isEnabled(FLAG_BETA))

        FeatureFlags.config = JsonObject().put(FLAG_BETA, true)
        assertFalse(FeatureFlags.isEnabled(FLAG_ALPHA))
        assertTrue(FeatureFlags.isEnabled(FLAG_BETA))
    }

    @Test
    fun `isEnabled with empty config always returns false`() {
        FeatureFlags.config = JsonObject()
        assertFalse(FeatureFlags.isEnabled(FLAG_ANYTHING))
    }
}
