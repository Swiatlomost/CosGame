package com.cosgame.costrack.state

/**
 * Application modes determining UI visibility and features.
 */
enum class AppMode {
    /**
     * User mode - simplified UI for end users.
     * - Shows only essential information
     * - Hides technical details and debug options
     * - Streamlined navigation
     */
    USER,

    /**
     * Developer mode - full access to all features.
     * - Shows all sensor data and debug information
     * - Access to dev options in settings
     * - Detailed classifier outputs
     * - Performance metrics visible
     */
    DEVELOPER;

    companion object {
        val DEFAULT = USER

        fun fromString(value: String): AppMode {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                DEFAULT
            }
        }
    }
}

/**
 * Features that can be toggled based on app mode.
 */
enum class ModeFeature(
    val availableInUserMode: Boolean,
    val availableInDevMode: Boolean
) {
    // Always available
    HOME_SCREEN(true, true),
    SENSORS_BASIC(true, true),
    CLASSIFIERS_RESULTS(true, true),
    SETTINGS_BASIC(true, true),

    // Developer only
    SENSORS_RAW_DATA(false, true),
    SENSORS_FREQUENCY(false, true),
    CLASSIFIERS_PROBABILITIES(false, true),
    CLASSIFIERS_INFERENCE_TIME(false, true),
    SETTINGS_DEV_OPTIONS(false, true),
    DEBUG_OVERLAY(false, true),
    EXPORT_DATA(false, true),
    RING_BUFFER_STATUS(false, true);

    fun isAvailable(mode: AppMode): Boolean {
        return when (mode) {
            AppMode.USER -> availableInUserMode
            AppMode.DEVELOPER -> availableInDevMode
        }
    }
}
