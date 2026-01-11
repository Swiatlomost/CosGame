package com.cosgame.costrack.touch

import com.cosgame.costrack.data.RingBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DNA.Touch - Dynamic Neural Aggregator for touch interactions.
 *
 * Tracks and aggregates touch patterns to build a behavioral profile:
 * - Touch intensity (0-1): How forcefully/frequently the user touches
 * - Touch style: Tapper, Swiper, or Mixed
 * - Pattern stability: Consistency of touch patterns over time
 */
class TouchDna(
    private val config: TouchDnaConfig = TouchDnaConfig()
) {
    // Histories for temporal smoothing
    private val intensityHistory = RingBuffer<Float>(config.historySize)
    private val styleHistory = RingBuffer<TouchStyle>(config.historySize)
    private val pressureHistory = RingBuffer<Float>(config.historySize)
    private val velocityHistory = RingBuffer<Float>(config.historySize)
    private val tpmHistory = RingBuffer<Float>(config.historySize)

    // Gesture type counts for style determination
    private var tapCount = 0
    private var swipeCount = 0
    private var drawCount = 0
    private var totalGestures = 0

    // State flows
    private val _profile = MutableStateFlow(TouchDnaProfile())
    val profile: StateFlow<TouchDnaProfile> = _profile.asStateFlow()

    private val _isStable = MutableStateFlow(false)
    val isStable: StateFlow<Boolean> = _isStable.asStateFlow()

    private var lastStyle: TouchStyle? = null
    private var styleStabilityCounter = 0

    /**
     * Add touch data from analyzer metrics.
     */
    fun addMetrics(metrics: TouchMetrics) {
        // Update histories
        pressureHistory.push(metrics.averagePressure)
        if (metrics.averageSwipeVelocity > 0) {
            velocityHistory.push(metrics.averageSwipeVelocity)
        }
        if (metrics.touchesPerMinute > 0) {
            tpmHistory.push(metrics.touchesPerMinute)
        }

        updateProfile()
    }

    /**
     * Add a completed touch sequence.
     */
    fun addSequence(sequence: TouchSequence) {
        totalGestures++

        when (sequence.gestureType) {
            GestureType.TAP, GestureType.LONG_TAP -> tapCount++
            GestureType.SWIPE -> swipeCount++
            GestureType.DRAG -> swipeCount++
            GestureType.DRAW -> drawCount++
            else -> { }
        }

        // Update intensity based on sequence
        val sequenceIntensity = calculateSequenceIntensity(sequence)
        intensityHistory.push(sequenceIntensity)

        // Update style
        val currentStyle = calculateCurrentStyle()
        styleHistory.push(currentStyle)

        updateProfile()
        updateStability(currentStyle)
    }

    /**
     * Add raw intensity and style values directly.
     */
    fun addRawValues(intensity: Float, style: TouchStyle) {
        intensityHistory.push(intensity.coerceIn(0f, 1f))
        styleHistory.push(style)
        updateProfile()
        updateStability(style)
    }

    private fun calculateSequenceIntensity(sequence: TouchSequence): Float {
        // Combine pressure, velocity, and frequency into intensity score
        val pressureScore = sequence.averagePressure.coerceIn(0f, 1f)

        // Normalize velocity (2000 px/s considered high)
        val velocityScore = (sequence.averageVelocity / 2000f).coerceIn(0f, 1f)

        // Duration factor (shorter = more intense for taps)
        val durationFactor = when (sequence.gestureType) {
            GestureType.TAP -> (1f - sequence.duration / 500f).coerceIn(0f, 1f)
            else -> 0.5f
        }

        return (pressureScore * 0.4f + velocityScore * 0.3f + durationFactor * 0.3f)
    }

    private fun calculateCurrentStyle(): TouchStyle {
        if (totalGestures == 0) return TouchStyle.UNKNOWN

        val tapRatio = tapCount.toFloat() / totalGestures
        val swipeRatio = swipeCount.toFloat() / totalGestures

        return when {
            tapRatio > 0.7f -> TouchStyle.TAPPER
            swipeRatio > 0.7f -> TouchStyle.SWIPER
            totalGestures < 5 -> TouchStyle.UNKNOWN
            else -> TouchStyle.MIXED
        }
    }

    private fun updateProfile() {
        // Calculate aggregated intensity
        val avgIntensity = if (intensityHistory.isEmpty) 0f else {
            // Recent-weighted average
            val items = intensityHistory.toList()
            var totalWeight = 0f
            var weightedSum = 0f

            items.forEachIndexed { index, value ->
                val weight = kotlin.math.pow(config.recencyDecay.toDouble(),
                    (items.size - 1 - index).toDouble()).toFloat()
                weightedSum += value * weight
                totalWeight += weight
            }

            if (totalWeight > 0) weightedSum / totalWeight else 0f
        }

        // Calculate dominant style
        val dominantStyle = if (styleHistory.isEmpty) TouchStyle.UNKNOWN else {
            val styles = styleHistory.toList()
            val counts = styles.groupingBy { it }.eachCount()
            counts.maxByOrNull { it.value }?.key ?: TouchStyle.UNKNOWN
        }

        // Calculate style distribution
        val styleDistribution = if (totalGestures > 0) {
            mapOf(
                TouchStyle.TAPPER to tapCount.toFloat() / totalGestures,
                TouchStyle.SWIPER to swipeCount.toFloat() / totalGestures,
                TouchStyle.MIXED to drawCount.toFloat() / totalGestures
            )
        } else {
            emptyMap()
        }

        // Calculate average metrics
        val avgPressure = if (pressureHistory.isEmpty) 0f else
            pressureHistory.toList().average().toFloat()
        val avgVelocity = if (velocityHistory.isEmpty) 0f else
            velocityHistory.toList().average().toFloat()
        val avgTpm = if (tpmHistory.isEmpty) 0f else
            tpmHistory.toList().average().toFloat()

        // Calculate consistency (how stable the intensity is)
        val intensityVariance = if (intensityHistory.size > 1) {
            val items = intensityHistory.toList()
            val mean = items.average().toFloat()
            items.map { (it - mean) * (it - mean) }.average().toFloat()
        } else 0f
        val consistency = (1f - kotlin.math.sqrt(intensityVariance.toDouble()).toFloat() * 2f)
            .coerceIn(0f, 1f)

        _profile.value = TouchDnaProfile(
            intensity = avgIntensity,
            style = dominantStyle,
            styleDistribution = styleDistribution,
            averagePressure = avgPressure,
            averageVelocity = avgVelocity,
            touchesPerMinute = avgTpm,
            consistency = consistency,
            sampleCount = intensityHistory.size,
            totalGestures = totalGestures,
            tapCount = tapCount,
            swipeCount = swipeCount,
            drawCount = drawCount,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun updateStability(currentStyle: TouchStyle) {
        if (currentStyle == lastStyle) {
            styleStabilityCounter++
        } else {
            styleStabilityCounter = 1
            lastStyle = currentStyle
        }

        _isStable.value = styleStabilityCounter >= config.stabilityThreshold
    }

    /**
     * Get stable style if pattern is stable.
     */
    fun getStableStyle(): TouchStyle? {
        return if (_isStable.value) lastStyle else null
    }

    /**
     * Get intensity level as category.
     */
    fun getIntensityLevel(): IntensityLevel {
        val intensity = _profile.value.intensity
        return when {
            intensity < 0.25f -> IntensityLevel.LOW
            intensity < 0.5f -> IntensityLevel.MEDIUM
            intensity < 0.75f -> IntensityLevel.HIGH
            else -> IntensityLevel.VERY_HIGH
        }
    }

    /**
     * Reset all data.
     */
    fun reset() {
        intensityHistory.clear()
        styleHistory.clear()
        pressureHistory.clear()
        velocityHistory.clear()
        tpmHistory.clear()
        tapCount = 0
        swipeCount = 0
        drawCount = 0
        totalGestures = 0
        _profile.value = TouchDnaProfile()
        _isStable.value = false
        lastStyle = null
        styleStabilityCounter = 0
    }

    /**
     * Check if enough data for meaningful profile.
     */
    fun hasEnoughData(): Boolean = intensityHistory.size >= config.minSamplesForProfile
}

/**
 * Configuration for Touch DNA.
 */
data class TouchDnaConfig(
    val historySize: Int = 20,
    val stabilityThreshold: Int = 5,
    val minSamplesForProfile: Int = 5,
    val recencyDecay: Float = 0.85f
)

/**
 * Touch DNA behavioral profile.
 */
data class TouchDnaProfile(
    val intensity: Float = 0f,           // 0-1 overall touch intensity
    val style: TouchStyle = TouchStyle.UNKNOWN,
    val styleDistribution: Map<TouchStyle, Float> = emptyMap(),
    val averagePressure: Float = 0f,
    val averageVelocity: Float = 0f,
    val touchesPerMinute: Float = 0f,
    val consistency: Float = 0f,         // 0-1 how consistent the patterns are
    val sampleCount: Int = 0,
    val totalGestures: Int = 0,
    val tapCount: Int = 0,
    val swipeCount: Int = 0,
    val drawCount: Int = 0,
    val timestamp: Long = 0
) {
    val intensityFormatted: String get() = String.format("%.0f%%", intensity * 100)
    val pressureFormatted: String get() = String.format("%.2f", averagePressure)
    val velocityFormatted: String get() = String.format("%.0f px/s", averageVelocity)
    val tpmFormatted: String get() = String.format("%.1f", touchesPerMinute)
    val consistencyFormatted: String get() = String.format("%.0f%%", consistency * 100)

    val styleDescription: String get() = when (style) {
        TouchStyle.TAPPER -> "Tapper - prefers taps"
        TouchStyle.SWIPER -> "Swiper - prefers swipes"
        TouchStyle.MIXED -> "Mixed - uses both"
        TouchStyle.UNKNOWN -> "Unknown"
    }

    val isValid: Boolean get() = sampleCount > 0
}

/**
 * Intensity levels.
 */
enum class IntensityLevel(val displayName: String) {
    LOW("Light"),
    MEDIUM("Moderate"),
    HIGH("Firm"),
    VERY_HIGH("Intense")
}
