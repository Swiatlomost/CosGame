package com.cosgame.costrack.touch

import kotlin.math.sqrt

/**
 * Extracts 24 features from touch session data for classification.
 */
class TouchFeatureExtractor {

    companion object {
        const val FEATURE_COUNT = 24

        // Feature indices
        const val TAP_COUNT = 0
        const val TAP_RATE_PER_MIN = 1
        const val TAP_DURATION_MEAN = 2
        const val TAP_DURATION_STD = 3
        const val TAP_PRESSURE_MEAN = 4
        const val TAP_PRESSURE_STD = 5
        const val SWIPE_COUNT = 6
        const val SWIPE_VELOCITY_MEAN = 7
        const val SWIPE_LENGTH_MEAN = 8
        const val SWIPE_LINEARITY = 9
        const val ZONE_0 = 10  // 9 zones (10-18)
        const val CENTER_OF_MASS_X = 19
        const val CENTER_OF_MASS_Y = 20
        const val INTER_TAP_MEAN = 21
        const val INTER_TAP_STD = 22
        const val SESSION_DURATION = 23

        val FEATURE_NAMES = arrayOf(
            "tap_count", "tap_rate_per_min",
            "tap_duration_mean", "tap_duration_std",
            "tap_pressure_mean", "tap_pressure_std",
            "swipe_count", "swipe_velocity_mean",
            "swipe_length_mean", "swipe_linearity",
            "zone_0", "zone_1", "zone_2",
            "zone_3", "zone_4", "zone_5",
            "zone_6", "zone_7", "zone_8",
            "center_of_mass_x", "center_of_mass_y",
            "inter_tap_mean", "inter_tap_std",
            "session_duration"
        )
    }

    /**
     * Extract features from a list of touch events.
     */
    fun extractFeatures(events: List<TouchEvent>, sessionDurationMs: Long): FloatArray {
        val features = FloatArray(FEATURE_COUNT)

        if (events.isEmpty()) return features

        // Build gestures from events
        val gestures = buildGestures(events)
        val taps = gestures.filter { it.isTap }
        val swipes = gestures.filter { it.isSwipe }

        // Session duration in seconds
        val sessionDurationSec = sessionDurationMs / 1000f
        features[SESSION_DURATION] = sessionDurationSec

        // === TAP FEATURES ===
        features[TAP_COUNT] = taps.size.toFloat()
        features[TAP_RATE_PER_MIN] = if (sessionDurationSec > 0) {
            taps.size * 60f / sessionDurationSec
        } else 0f

        if (taps.isNotEmpty()) {
            val tapDurations = taps.map { it.duration.toFloat() }
            features[TAP_DURATION_MEAN] = tapDurations.average().toFloat()
            features[TAP_DURATION_STD] = standardDeviation(tapDurations)

            val tapPressures = taps.map { it.avgPressure }
            features[TAP_PRESSURE_MEAN] = tapPressures.average().toFloat()
            features[TAP_PRESSURE_STD] = standardDeviation(tapPressures)
        }

        // === SWIPE FEATURES ===
        features[SWIPE_COUNT] = swipes.size.toFloat()

        if (swipes.isNotEmpty()) {
            features[SWIPE_VELOCITY_MEAN] = swipes.map { it.velocity }.average().toFloat()
            features[SWIPE_LENGTH_MEAN] = swipes.map { it.totalDistance }.average().toFloat()
            features[SWIPE_LINEARITY] = swipes.map { it.linearity }.average().toFloat()
        }

        // === SPATIAL FEATURES ===
        // Zone distribution (3x3 grid)
        val downEvents = events.filter { it.eventType == TouchEventType.DOWN }
        val zoneCounts = IntArray(9)
        downEvents.forEach { event ->
            val zone = event.getZone3x3()
            zoneCounts[zone]++
        }
        val totalDowns = downEvents.size.toFloat().coerceAtLeast(1f)
        for (i in 0..8) {
            features[ZONE_0 + i] = zoneCounts[i] / totalDowns
        }

        // Center of mass
        if (downEvents.isNotEmpty()) {
            features[CENTER_OF_MASS_X] = downEvents.map { it.x }.average().toFloat()
            features[CENTER_OF_MASS_Y] = downEvents.map { it.y }.average().toFloat()
        } else {
            features[CENTER_OF_MASS_X] = 0.5f
            features[CENTER_OF_MASS_Y] = 0.5f
        }

        // === TEMPORAL FEATURES ===
        // Inter-tap intervals
        if (taps.size > 1) {
            val interTapIntervals = mutableListOf<Float>()
            for (i in 1 until taps.size) {
                val interval = (taps[i].startTime - taps[i - 1].endTime).toFloat()
                if (interval > 0) {
                    interTapIntervals.add(interval)
                }
            }
            if (interTapIntervals.isNotEmpty()) {
                features[INTER_TAP_MEAN] = interTapIntervals.average().toFloat()
                features[INTER_TAP_STD] = standardDeviation(interTapIntervals)
            }
        }

        return features
    }

    /**
     * Extract features from a TouchSession.
     */
    fun extractFeatures(session: TouchSession): FloatArray {
        val events = TouchSessionCollector.eventsFromJson(session.touchEventsJson)
        return extractFeatures(events, session.duration)
    }

    /**
     * Build gestures from a list of events.
     */
    private fun buildGestures(events: List<TouchEvent>): List<TouchGesture> {
        val gestures = mutableListOf<TouchGesture>()
        val activeGestures = mutableMapOf<Int, MutableList<TouchEvent>>()

        events.forEach { event ->
            when (event.eventType) {
                TouchEventType.DOWN -> {
                    activeGestures[event.fingerId] = mutableListOf(event)
                }
                TouchEventType.MOVE -> {
                    activeGestures[event.fingerId]?.add(event)
                }
                TouchEventType.UP -> {
                    activeGestures[event.fingerId]?.add(event)
                    activeGestures.remove(event.fingerId)?.let { gestureEvents ->
                        if (gestureEvents.isNotEmpty()) {
                            gestures.add(TouchGesture(gestureEvents, event.fingerId))
                        }
                    }
                }
            }
        }

        return gestures
    }

    /**
     * Calculate standard deviation.
     */
    private fun standardDeviation(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    /**
     * Normalize features to 0-1 range.
     */
    fun normalizeFeatures(features: FloatArray, mins: FloatArray, maxs: FloatArray): FloatArray {
        val normalized = FloatArray(features.size)
        for (i in features.indices) {
            val range = maxs[i] - mins[i]
            normalized[i] = if (range > 0) {
                ((features[i] - mins[i]) / range).coerceIn(0f, 1f)
            } else {
                0.5f
            }
        }
        return normalized
    }

    /**
     * Calculate min/max for each feature from training data.
     */
    fun calculateNormalizationParams(sessions: List<TouchSession>): Pair<FloatArray, FloatArray> {
        val mins = FloatArray(FEATURE_COUNT) { Float.MAX_VALUE }
        val maxs = FloatArray(FEATURE_COUNT) { Float.MIN_VALUE }

        sessions.forEach { session ->
            val features = extractFeatures(session)
            for (i in features.indices) {
                if (features[i] < mins[i]) mins[i] = features[i]
                if (features[i] > maxs[i]) maxs[i] = features[i]
            }
        }

        // Handle edge cases
        for (i in mins.indices) {
            if (mins[i] == Float.MAX_VALUE) mins[i] = 0f
            if (maxs[i] == Float.MIN_VALUE) maxs[i] = 1f
            if (mins[i] == maxs[i]) {
                mins[i] = 0f
                maxs[i] = 1f
            }
        }

        return Pair(mins, maxs)
    }
}

/**
 * Container for extracted features with metadata.
 */
data class TouchFeatures(
    val values: FloatArray,
    val label: String? = null,
    val sessionId: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TouchFeatures
        return values.contentEquals(other.values) && label == other.label && sessionId == other.sessionId
    }

    override fun hashCode(): Int {
        return 31 * values.contentHashCode() + (label?.hashCode() ?: 0)
    }
}
