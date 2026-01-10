package com.cosgame.costrack.state

import org.junit.Assert.*
import org.junit.Test

class AppModeTest {

    @Test
    fun `default mode is USER`() {
        assertEquals(AppMode.USER, AppMode.DEFAULT)
    }

    @Test
    fun `fromString returns correct mode`() {
        assertEquals(AppMode.USER, AppMode.fromString("USER"))
        assertEquals(AppMode.DEVELOPER, AppMode.fromString("DEVELOPER"))
    }

    @Test
    fun `fromString is case insensitive`() {
        assertEquals(AppMode.USER, AppMode.fromString("user"))
        assertEquals(AppMode.USER, AppMode.fromString("User"))
        assertEquals(AppMode.DEVELOPER, AppMode.fromString("developer"))
        assertEquals(AppMode.DEVELOPER, AppMode.fromString("Developer"))
    }

    @Test
    fun `fromString returns default for invalid value`() {
        assertEquals(AppMode.DEFAULT, AppMode.fromString("invalid"))
        assertEquals(AppMode.DEFAULT, AppMode.fromString(""))
        assertEquals(AppMode.DEFAULT, AppMode.fromString("admin"))
    }

    @Test
    fun `mode enum has exactly two values`() {
        assertEquals(2, AppMode.values().size)
    }
}

class ModeFeatureTest {

    @Test
    fun `basic features available in both modes`() {
        assertTrue(ModeFeature.HOME_SCREEN.isAvailable(AppMode.USER))
        assertTrue(ModeFeature.HOME_SCREEN.isAvailable(AppMode.DEVELOPER))

        assertTrue(ModeFeature.SENSORS_BASIC.isAvailable(AppMode.USER))
        assertTrue(ModeFeature.SENSORS_BASIC.isAvailable(AppMode.DEVELOPER))

        assertTrue(ModeFeature.CLASSIFIERS_RESULTS.isAvailable(AppMode.USER))
        assertTrue(ModeFeature.CLASSIFIERS_RESULTS.isAvailable(AppMode.DEVELOPER))

        assertTrue(ModeFeature.SETTINGS_BASIC.isAvailable(AppMode.USER))
        assertTrue(ModeFeature.SETTINGS_BASIC.isAvailable(AppMode.DEVELOPER))
    }

    @Test
    fun `dev features only in developer mode`() {
        // Not available in user mode
        assertFalse(ModeFeature.SENSORS_RAW_DATA.isAvailable(AppMode.USER))
        assertFalse(ModeFeature.SENSORS_FREQUENCY.isAvailable(AppMode.USER))
        assertFalse(ModeFeature.CLASSIFIERS_PROBABILITIES.isAvailable(AppMode.USER))
        assertFalse(ModeFeature.CLASSIFIERS_INFERENCE_TIME.isAvailable(AppMode.USER))
        assertFalse(ModeFeature.SETTINGS_DEV_OPTIONS.isAvailable(AppMode.USER))
        assertFalse(ModeFeature.DEBUG_OVERLAY.isAvailable(AppMode.USER))
        assertFalse(ModeFeature.EXPORT_DATA.isAvailable(AppMode.USER))
        assertFalse(ModeFeature.RING_BUFFER_STATUS.isAvailable(AppMode.USER))

        // Available in developer mode
        assertTrue(ModeFeature.SENSORS_RAW_DATA.isAvailable(AppMode.DEVELOPER))
        assertTrue(ModeFeature.SENSORS_FREQUENCY.isAvailable(AppMode.DEVELOPER))
        assertTrue(ModeFeature.CLASSIFIERS_PROBABILITIES.isAvailable(AppMode.DEVELOPER))
        assertTrue(ModeFeature.CLASSIFIERS_INFERENCE_TIME.isAvailable(AppMode.DEVELOPER))
        assertTrue(ModeFeature.SETTINGS_DEV_OPTIONS.isAvailable(AppMode.DEVELOPER))
        assertTrue(ModeFeature.DEBUG_OVERLAY.isAvailable(AppMode.DEVELOPER))
        assertTrue(ModeFeature.EXPORT_DATA.isAvailable(AppMode.DEVELOPER))
        assertTrue(ModeFeature.RING_BUFFER_STATUS.isAvailable(AppMode.DEVELOPER))
    }

    @Test
    fun `feature flags correctly set`() {
        // Count features available in user mode
        val userFeatures = ModeFeature.values().filter { it.availableInUserMode }
        assertEquals(4, userFeatures.size)

        // All features should be available in dev mode
        val devFeatures = ModeFeature.values().filter { it.availableInDevMode }
        assertEquals(ModeFeature.values().size, devFeatures.size)
    }

    @Test
    fun `all dev features have correct flags`() {
        ModeFeature.values().forEach { feature ->
            // If available in user mode, must be available in dev mode
            if (feature.availableInUserMode) {
                assertTrue(
                    "Feature $feature available in user but not dev mode",
                    feature.availableInDevMode
                )
            }
        }
    }
}

class AppStateManagerConstantsTest {

    @Test
    fun `unlock requires 7 taps`() {
        assertEquals(7, AppStateManager.TAPS_TO_UNLOCK)
    }

    @Test
    fun `tap timeout is 3 seconds`() {
        assertEquals(3000L, AppStateManager.TAP_TIMEOUT_MS)
    }
}
