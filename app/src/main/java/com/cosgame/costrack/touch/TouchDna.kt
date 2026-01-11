package com.cosgame.costrack.touch

/**
 * Touch DNA - behavioral profile derived from touch patterns.
 * Provides high-level traits that characterize user's touch behavior.
 */
data class TouchDna(
    val intensity: Float,      // 0-1: How forceful touches are (pressure-based)
    val precision: Float,      // 0-1: How accurate/consistent touches are
    val tempo: Float,          // 0-1: Touch rhythm speed (slow=0, fast=1)
    val dominantHand: HandPreference,  // Detected hand preference
    val consistency: Float,    // 0-1: How consistent behavior is across sessions
    val coverage: Float,       // 0-1: Screen usage coverage
    val sessionCount: Int      // Number of sessions used for this profile
) {
    /**
     * Get trait summary as a map.
     */
    fun toMap(): Map<String, Any> = mapOf(
        "intensity" to intensity,
        "precision" to precision,
        "tempo" to tempo,
        "dominantHand" to dominantHand.name,
        "consistency" to consistency,
        "coverage" to coverage,
        "sessionCount" to sessionCount
    )

    /**
     * Format intensity as description.
     */
    val intensityDescription: String get() = when {
        intensity < 0.3f -> "Light touch"
        intensity < 0.6f -> "Normal pressure"
        else -> "Firm grip"
    }

    /**
     * Format precision as description.
     */
    val precisionDescription: String get() = when {
        precision < 0.3f -> "Exploratory"
        precision < 0.6f -> "Average"
        else -> "Precise"
    }

    /**
     * Format tempo as description.
     */
    val tempoDescription: String get() = when {
        tempo < 0.3f -> "Relaxed"
        tempo < 0.6f -> "Moderate"
        else -> "Quick"
    }

    companion object {
        val EMPTY = TouchDna(
            intensity = 0.5f,
            precision = 0.5f,
            tempo = 0.5f,
            dominantHand = HandPreference.UNKNOWN,
            consistency = 0f,
            coverage = 0f,
            sessionCount = 0
        )
    }
}

/**
 * Hand preference detected from touch patterns.
 */
enum class HandPreference {
    LEFT,
    RIGHT,
    AMBIDEXTROUS,
    UNKNOWN
}

/**
 * Generates TouchDna from touch sessions.
 */
class TouchDnaGenerator {

    private val featureExtractor = TouchFeatureExtractor()
    private val heatmapGenerator = TouchHeatmapGenerator()

    /**
     * Generate DNA profile from sessions.
     */
    fun generate(sessions: List<TouchSession>): TouchDna {
        if (sessions.isEmpty()) return TouchDna.EMPTY

        // Extract features from all sessions
        val allFeatures = sessions.map { session ->
            featureExtractor.extractFeatures(session)
        }

        // Generate heatmaps
        val heatmaps = sessions.map { session ->
            val events = TouchSessionCollector.eventsFromJson(session.touchEventsJson)
            heatmapGenerator.generate(events)
        }

        // Calculate intensity (from pressure)
        val intensity = calculateIntensity(allFeatures)

        // Calculate precision (from tap consistency)
        val precision = calculatePrecision(allFeatures)

        // Calculate tempo (from tap rate)
        val tempo = calculateTempo(allFeatures)

        // Detect dominant hand (from spatial distribution)
        val dominantHand = detectDominantHand(heatmaps)

        // Calculate consistency (how similar features are across sessions)
        val consistency = calculateConsistency(allFeatures)

        // Calculate coverage (screen usage)
        val coverage = calculateCoverage(heatmaps)

        return TouchDna(
            intensity = intensity,
            precision = precision,
            tempo = tempo,
            dominantHand = dominantHand,
            consistency = consistency,
            coverage = coverage,
            sessionCount = sessions.size
        )
    }

    /**
     * Calculate intensity from pressure features.
     * Uses TAP_PRESSURE_MEAN feature.
     */
    private fun calculateIntensity(features: List<FloatArray>): Float {
        val pressures = features.mapNotNull { f ->
            val pressure = f[TouchFeatureExtractor.TAP_PRESSURE_MEAN]
            if (pressure > 0) pressure else null
        }
        return if (pressures.isNotEmpty()) {
            pressures.average().toFloat().coerceIn(0f, 1f)
        } else 0.5f
    }

    /**
     * Calculate precision from tap consistency.
     * Uses TAP_DURATION_STD and TAP_PRESSURE_STD (lower = more precise).
     */
    private fun calculatePrecision(features: List<FloatArray>): Float {
        val durationStds = features.map { it[TouchFeatureExtractor.TAP_DURATION_STD] }
        val pressureStds = features.map { it[TouchFeatureExtractor.TAP_PRESSURE_STD] }

        // Lower std = more precise, so invert
        val avgDurationStd = durationStds.average().toFloat()
        val avgPressureStd = pressureStds.average().toFloat()

        // Normalize (assuming typical std ranges)
        val durationPrecision = (1f - (avgDurationStd / 200f).coerceIn(0f, 1f))
        val pressurePrecision = (1f - (avgPressureStd / 0.3f).coerceIn(0f, 1f))

        return ((durationPrecision + pressurePrecision) / 2f).coerceIn(0f, 1f)
    }

    /**
     * Calculate tempo from tap rate.
     * Uses TAP_RATE_PER_MIN feature.
     */
    private fun calculateTempo(features: List<FloatArray>): Float {
        val rates = features.map { it[TouchFeatureExtractor.TAP_RATE_PER_MIN] }
        val avgRate = rates.average().toFloat()

        // Normalize (0-200 taps/min range mapped to 0-1)
        return (avgRate / 200f).coerceIn(0f, 1f)
    }

    /**
     * Detect dominant hand from heatmap distribution.
     * Compares left vs right side activity.
     */
    private fun detectDominantHand(heatmaps: List<FloatArray>): HandPreference {
        if (heatmaps.isEmpty()) return HandPreference.UNKNOWN

        val combined = heatmapGenerator.combine(heatmaps)
        val stats = heatmapGenerator.getStats(combined)

        // Calculate left vs right activity
        var leftSum = 0f
        var rightSum = 0f

        for (row in 0 until TouchHeatmapGenerator.GRID_ROWS) {
            // Left side: columns 0-4
            for (col in 0 until 5) {
                leftSum += heatmapGenerator.getValue(combined, row, col)
            }
            // Right side: columns 5-9
            for (col in 5 until TouchHeatmapGenerator.GRID_COLS) {
                rightSum += heatmapGenerator.getValue(combined, row, col)
            }
        }

        val total = leftSum + rightSum
        if (total < 0.1f) return HandPreference.UNKNOWN

        val leftRatio = leftSum / total
        val rightRatio = rightSum / total

        return when {
            leftRatio > 0.6f -> HandPreference.LEFT
            rightRatio > 0.6f -> HandPreference.RIGHT
            kotlin.math.abs(leftRatio - rightRatio) < 0.15f -> HandPreference.AMBIDEXTROUS
            else -> HandPreference.UNKNOWN
        }
    }

    /**
     * Calculate consistency across sessions.
     * Lower variance = higher consistency.
     */
    private fun calculateConsistency(features: List<FloatArray>): Float {
        if (features.size < 2) return 0f

        // Calculate variance for key features
        val keyFeatureIndices = listOf(
            TouchFeatureExtractor.TAP_RATE_PER_MIN,
            TouchFeatureExtractor.TAP_PRESSURE_MEAN,
            TouchFeatureExtractor.SWIPE_VELOCITY_MEAN,
            TouchFeatureExtractor.CENTER_OF_MASS_X,
            TouchFeatureExtractor.CENTER_OF_MASS_Y
        )

        var totalConsistency = 0f

        for (featureIdx in keyFeatureIndices) {
            val values = features.map { it[featureIdx] }
            val mean = values.average().toFloat()
            if (mean > 0) {
                val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
                val cv = kotlin.math.sqrt(variance) / mean  // Coefficient of variation
                // Lower CV = more consistent
                totalConsistency += (1f - cv.coerceIn(0f, 1f))
            }
        }

        return (totalConsistency / keyFeatureIndices.size).coerceIn(0f, 1f)
    }

    /**
     * Calculate screen coverage from heatmaps.
     */
    private fun calculateCoverage(heatmaps: List<FloatArray>): Float {
        if (heatmaps.isEmpty()) return 0f

        val combined = heatmapGenerator.combine(heatmaps)
        val stats = heatmapGenerator.getStats(combined)
        return stats.coverage
    }

    /**
     * Compare two DNA profiles for similarity (0-1).
     */
    fun compareDna(dna1: TouchDna, dna2: TouchDna): Float {
        val intensityDiff = kotlin.math.abs(dna1.intensity - dna2.intensity)
        val precisionDiff = kotlin.math.abs(dna1.precision - dna2.precision)
        val tempoDiff = kotlin.math.abs(dna1.tempo - dna2.tempo)
        val handMatch = if (dna1.dominantHand == dna2.dominantHand) 0f else 0.25f

        val avgDiff = (intensityDiff + precisionDiff + tempoDiff + handMatch) / 4f
        return (1f - avgDiff).coerceIn(0f, 1f)
    }
}

/**
 * Repository for Touch DNA operations.
 */
class TouchDnaRepository(
    private val touchRepository: TouchRepository,
    private val dnaGenerator: TouchDnaGenerator = TouchDnaGenerator()
) {
    /**
     * Generate DNA from all stored sessions.
     */
    suspend fun generateDna(): TouchDna {
        val sessions = touchRepository.getAllSessions()
        return dnaGenerator.generate(sessions)
    }

    /**
     * Generate DNA for a specific label.
     */
    suspend fun generateDnaForLabel(label: String): TouchDna {
        val sessions = touchRepository.getSessionsByLabelList(label)
        return dnaGenerator.generate(sessions)
    }

    /**
     * Identify user based on touch pattern.
     * Returns label with highest similarity.
     */
    suspend fun identifyUser(newSession: TouchSession): IdentificationResult {
        val labels = touchRepository.getAllLabels()
        if (labels.isEmpty()) {
            return IdentificationResult(null, 0f, emptyMap())
        }

        val sessionDna = dnaGenerator.generate(listOf(newSession))
        val similarities = mutableMapOf<String, Float>()

        for (label in labels) {
            val labelDna = generateDnaForLabel(label)
            val similarity = dnaGenerator.compareDna(sessionDna, labelDna)
            similarities[label] = similarity
        }

        val bestMatch = similarities.maxByOrNull { it.value }
        return IdentificationResult(
            bestMatch = bestMatch?.key,
            confidence = bestMatch?.value ?: 0f,
            similarities = similarities
        )
    }
}

/**
 * Result of user identification.
 */
data class IdentificationResult(
    val bestMatch: String?,
    val confidence: Float,
    val similarities: Map<String, Float>
)
