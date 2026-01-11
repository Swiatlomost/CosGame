package com.cosgame.costrack.touch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Analyzes touch data and calculates metrics.
 */
class TouchAnalyzer {

    private val _metrics = MutableStateFlow(TouchMetrics())
    val metrics: StateFlow<TouchMetrics> = _metrics.asStateFlow()

    private val _heatmap = MutableStateFlow(ScreenHeatmap())
    val heatmap: StateFlow<ScreenHeatmap> = _heatmap.asStateFlow()

    // Rolling window for touches per minute calculation
    private val recentTouchTimestamps = mutableListOf<Long>()
    private val WINDOW_SIZE_MS = 60_000L // 1 minute window

    // Accumulated data for analysis
    private val allPressures = mutableListOf<Float>()
    private val allVelocities = mutableListOf<Float>()
    private val allTapDurations = mutableListOf<Long>()
    private val regionCounts = mutableMapOf<ScreenRegion, Int>()

    /**
     * Analyze a completed touch sequence.
     */
    fun analyzeSequence(sequence: TouchSequence) {
        val currentTime = System.currentTimeMillis()

        // Track touch timestamp for TPM calculation
        recentTouchTimestamps.add(sequence.startTime)
        cleanOldTimestamps(currentTime)

        // Collect pressure data
        sequence.events.forEach { event ->
            allPressures.add(event.pressure)

            // Update heatmap
            val region = event.screenRegion
            regionCounts[region] = (regionCounts[region] ?: 0) + 1
        }

        // Collect velocity if it's a swipe or drag
        if (sequence.gestureType in listOf(GestureType.SWIPE, GestureType.DRAG)) {
            allVelocities.add(sequence.averageVelocity)
        }

        // Collect tap duration
        if (sequence.gestureType == GestureType.TAP || sequence.gestureType == GestureType.LONG_TAP) {
            allTapDurations.add(sequence.duration)
        }

        // Update metrics
        updateMetrics()
        updateHeatmap()
    }

    /**
     * Analyze a single touch event (for real-time updates).
     */
    fun analyzeEvent(event: TouchEvent) {
        allPressures.add(event.pressure)

        // Update heatmap
        val region = event.screenRegion
        regionCounts[region] = (regionCounts[region] ?: 0) + 1

        // Track touch for TPM
        if (event.action == TouchAction.DOWN || event.action == TouchAction.POINTER_DOWN) {
            recentTouchTimestamps.add(event.timestamp)
            cleanOldTimestamps(System.currentTimeMillis())
        }

        updateMetrics()
        updateHeatmap()
    }

    private fun cleanOldTimestamps(currentTime: Long) {
        val cutoff = currentTime - WINDOW_SIZE_MS
        recentTouchTimestamps.removeAll { it < cutoff }
    }

    private fun updateMetrics() {
        val currentTime = System.currentTimeMillis()
        cleanOldTimestamps(currentTime)

        // Calculate touches per minute
        val touchesInWindow = recentTouchTimestamps.size
        val windowDuration = if (recentTouchTimestamps.isNotEmpty()) {
            val firstTouch = recentTouchTimestamps.first()
            val elapsed = currentTime - firstTouch
            minOf(elapsed, WINDOW_SIZE_MS)
        } else {
            WINDOW_SIZE_MS
        }
        val tpm = if (windowDuration > 0) {
            (touchesInWindow * 60_000.0 / windowDuration).toFloat()
        } else 0f

        // Calculate averages
        val avgPressure = if (allPressures.isNotEmpty()) {
            allPressures.average().toFloat()
        } else 0f

        val avgVelocity = if (allVelocities.isNotEmpty()) {
            allVelocities.average().toFloat()
        } else 0f

        val avgTapDuration = if (allTapDurations.isNotEmpty()) {
            allTapDurations.average().toLong()
        } else 0L

        // Calculate min/max pressure
        val minPressure = allPressures.minOrNull() ?: 0f
        val maxPressure = allPressures.maxOrNull() ?: 0f

        // Calculate min/max velocity
        val minVelocity = allVelocities.minOrNull() ?: 0f
        val maxVelocity = allVelocities.maxOrNull() ?: 0f

        _metrics.value = TouchMetrics(
            touchesPerMinute = tpm,
            averagePressure = avgPressure,
            minPressure = minPressure,
            maxPressure = maxPressure,
            averageSwipeVelocity = avgVelocity,
            minSwipeVelocity = minVelocity,
            maxSwipeVelocity = maxVelocity,
            averageTapDuration = avgTapDuration,
            totalTouches = recentTouchTimestamps.size,
            totalPressureSamples = allPressures.size,
            totalVelocitySamples = allVelocities.size,
            totalTapSamples = allTapDurations.size
        )
    }

    private fun updateHeatmap() {
        val totalTouches = regionCounts.values.sum()
        if (totalTouches == 0) {
            _heatmap.value = ScreenHeatmap()
            return
        }

        val regionIntensities = ScreenRegion.entries.associateWith { region ->
            (regionCounts[region] ?: 0).toFloat() / totalTouches
        }

        // Find dominant region
        val dominantRegion = regionCounts.maxByOrNull { it.value }?.key

        _heatmap.value = ScreenHeatmap(
            regionIntensities = regionIntensities,
            dominantRegion = dominantRegion,
            totalTouches = totalTouches
        )
    }

    /**
     * Get touch style analysis (tapper vs swiper vs mixed).
     */
    fun getTouchStyle(): TouchStyle {
        val totalGestures = allTapDurations.size + allVelocities.size
        if (totalGestures == 0) return TouchStyle.UNKNOWN

        val tapRatio = allTapDurations.size.toFloat() / totalGestures
        val swipeRatio = allVelocities.size.toFloat() / totalGestures

        return when {
            tapRatio > 0.7f -> TouchStyle.TAPPER
            swipeRatio > 0.7f -> TouchStyle.SWIPER
            else -> TouchStyle.MIXED
        }
    }

    /**
     * Get touch intensity (0-1 based on pressure and frequency).
     */
    fun getTouchIntensity(): Float {
        val metrics = _metrics.value

        // Normalize TPM (assuming 60 TPM is high intensity)
        val tpmScore = minOf(metrics.touchesPerMinute / 60f, 1f)

        // Normalize pressure (pressure usually 0-1 on most devices)
        val pressureScore = metrics.averagePressure

        // Normalize velocity (assuming 2000 px/s is high)
        val velocityScore = minOf(metrics.averageSwipeVelocity / 2000f, 1f)

        // Combined intensity score
        return (tpmScore * 0.4f + pressureScore * 0.3f + velocityScore * 0.3f)
    }

    /**
     * Reset all collected data.
     */
    fun reset() {
        recentTouchTimestamps.clear()
        allPressures.clear()
        allVelocities.clear()
        allTapDurations.clear()
        regionCounts.clear()
        _metrics.value = TouchMetrics()
        _heatmap.value = ScreenHeatmap()
    }
}

/**
 * Touch metrics data.
 */
data class TouchMetrics(
    val touchesPerMinute: Float = 0f,
    val averagePressure: Float = 0f,
    val minPressure: Float = 0f,
    val maxPressure: Float = 0f,
    val averageSwipeVelocity: Float = 0f,
    val minSwipeVelocity: Float = 0f,
    val maxSwipeVelocity: Float = 0f,
    val averageTapDuration: Long = 0,
    val totalTouches: Int = 0,
    val totalPressureSamples: Int = 0,
    val totalVelocitySamples: Int = 0,
    val totalTapSamples: Int = 0
) {
    val formattedTpm: String get() = String.format("%.1f", touchesPerMinute)
    val formattedPressure: String get() = String.format("%.2f", averagePressure)
    val formattedVelocity: String get() = String.format("%.0f px/s", averageSwipeVelocity)
    val formattedTapDuration: String get() = "${averageTapDuration}ms"
}

/**
 * Screen heatmap data (3x3 grid).
 */
data class ScreenHeatmap(
    val regionIntensities: Map<ScreenRegion, Float> = ScreenRegion.entries.associateWith { 0f },
    val dominantRegion: ScreenRegion? = null,
    val totalTouches: Int = 0
) {
    fun getIntensity(region: ScreenRegion): Float = regionIntensities[region] ?: 0f

    fun getIntensityRow(row: Int): List<Float> {
        return listOf(
            getIntensity(ScreenRegion.fromPosition(row, 0)),
            getIntensity(ScreenRegion.fromPosition(row, 1)),
            getIntensity(ScreenRegion.fromPosition(row, 2))
        )
    }
}

/**
 * Touch interaction style.
 */
enum class TouchStyle {
    TAPPER,     // Primarily uses taps
    SWIPER,     // Primarily uses swipes/drags
    MIXED,      // Uses both equally
    UNKNOWN     // Not enough data
}
