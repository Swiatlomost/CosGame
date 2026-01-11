package com.cosgame.costrack.touch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Type of touch mission.
 */
enum class TouchMissionType(
    val icon: String,
    val displayName: String
) {
    TAP("👆", "Tap"),
    MULTI_TAP("✌️", "Multi-Tap"),
    SWIPE("👉", "Swipe"),
    DRAW_CIRCLE("⭕", "Draw Circle"),
    DRAW_PATTERN("✏️", "Draw Pattern"),
    LONG_PRESS("👇", "Long Press"),
    PRESSURE("💪", "Pressure Control"),
    SPEED("⚡", "Speed Challenge")
}

/**
 * Swipe direction for swipe missions.
 */
enum class SwipeDirection {
    LEFT, RIGHT, UP, DOWN, ANY
}

/**
 * Definition of a touch training mission.
 */
data class TouchMission(
    val id: String,
    val type: TouchMissionType,
    val name: String,
    val description: String,
    val targetCount: Int = 1,
    val timeLimit: Int? = null, // seconds, null = unlimited
    val requirements: TouchMissionRequirements = TouchMissionRequirements()
) {
    val icon: String get() = type.icon
    val displayTimeLimit: String get() = timeLimit?.let { "${it}s" } ?: "∞"
}

/**
 * Specific requirements for mission completion.
 */
data class TouchMissionRequirements(
    val minPressure: Float? = null,
    val maxPressure: Float? = null,
    val minVelocity: Float? = null,
    val maxVelocity: Float? = null,
    val swipeDirection: SwipeDirection? = null,
    val minDuration: Long? = null, // ms
    val maxDuration: Long? = null, // ms
    val targetRegion: ScreenRegion? = null,
    val minDistance: Float? = null,
    val circleAccuracy: Float? = null // 0-1, how close to a circle
)

/**
 * Progress tracking for a touch mission.
 */
data class TouchMissionProgress(
    val mission: TouchMission,
    val currentCount: Int = 0,
    val elapsedTime: Long = 0, // ms
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
    val validTouches: List<TouchSequence> = emptyList(),
    val invalidTouches: List<TouchSequence> = emptyList()
) {
    val progress: Float get() = if (mission.targetCount > 0) {
        minOf(currentCount.toFloat() / mission.targetCount, 1f)
    } else 0f

    val remainingCount: Int get() = maxOf(mission.targetCount - currentCount, 0)

    val timeRemaining: Long? get() = mission.timeLimit?.let { limit ->
        maxOf((limit * 1000L) - elapsedTime, 0)
    }

    val isTimedOut: Boolean get() = mission.timeLimit?.let { limit ->
        elapsedTime >= limit * 1000L
    } ?: false
}

/**
 * Manages touch mission execution and validation.
 */
class TouchMissionManager {

    private val _currentMission = MutableStateFlow<TouchMission?>(null)
    val currentMission: StateFlow<TouchMission?> = _currentMission.asStateFlow()

    private val _progress = MutableStateFlow<TouchMissionProgress?>(null)
    val progress: StateFlow<TouchMissionProgress?> = _progress.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var startTime: Long = 0
    private val validSequences = mutableListOf<TouchSequence>()
    private val invalidSequences = mutableListOf<TouchSequence>()

    /**
     * Start a mission.
     */
    fun startMission(mission: TouchMission) {
        _currentMission.value = mission
        _isActive.value = true
        startTime = System.currentTimeMillis()
        validSequences.clear()
        invalidSequences.clear()
        updateProgress()
    }

    /**
     * Stop current mission.
     */
    fun stopMission() {
        _isActive.value = false
        _currentMission.value = null
        _progress.value = null
    }

    /**
     * Validate a touch sequence against mission requirements.
     */
    fun validateSequence(sequence: TouchSequence): Boolean {
        val mission = _currentMission.value ?: return false
        val requirements = mission.requirements

        val isValid = when (mission.type) {
            TouchMissionType.TAP -> validateTap(sequence, requirements)
            TouchMissionType.MULTI_TAP -> validateMultiTap(sequence, requirements)
            TouchMissionType.SWIPE -> validateSwipe(sequence, requirements)
            TouchMissionType.DRAW_CIRCLE -> validateCircle(sequence, requirements)
            TouchMissionType.DRAW_PATTERN -> validatePattern(sequence, requirements)
            TouchMissionType.LONG_PRESS -> validateLongPress(sequence, requirements)
            TouchMissionType.PRESSURE -> validatePressure(sequence, requirements)
            TouchMissionType.SPEED -> validateSpeed(sequence, requirements)
        }

        if (isValid) {
            validSequences.add(sequence)
        } else {
            invalidSequences.add(sequence)
        }

        updateProgress()
        return isValid
    }

    /**
     * Update elapsed time (call periodically).
     */
    fun updateTime() {
        if (_isActive.value) {
            updateProgress()
        }
    }

    private fun updateProgress() {
        val mission = _currentMission.value ?: return
        val elapsed = System.currentTimeMillis() - startTime

        val isComplete = validSequences.size >= mission.targetCount
        val isTimedOut = mission.timeLimit?.let { elapsed >= it * 1000L } ?: false
        val isFailed = isTimedOut && !isComplete

        _progress.value = TouchMissionProgress(
            mission = mission,
            currentCount = validSequences.size,
            elapsedTime = elapsed,
            isComplete = isComplete,
            isFailed = isFailed,
            validTouches = validSequences.toList(),
            invalidTouches = invalidSequences.toList()
        )

        // Auto-stop on completion or failure
        if (isComplete || isFailed) {
            _isActive.value = false
        }
    }

    // Validation methods

    private fun validateTap(sequence: TouchSequence, req: TouchMissionRequirements): Boolean {
        if (sequence.gestureType != GestureType.TAP) return false

        req.targetRegion?.let { region ->
            if (sequence.events.firstOrNull()?.screenRegion != region) return false
        }

        req.minPressure?.let { if (sequence.averagePressure < it) return false }
        req.maxPressure?.let { if (sequence.averagePressure > it) return false }
        req.maxDuration?.let { if (sequence.duration > it) return false }

        return true
    }

    private fun validateMultiTap(sequence: TouchSequence, req: TouchMissionRequirements): Boolean {
        // For multi-tap, check if multiple fingers were involved
        val fingerCount = sequence.events.map { it.fingerId }.distinct().size
        if (fingerCount < 2) return false

        return validateTap(sequence, req)
    }

    private fun validateSwipe(sequence: TouchSequence, req: TouchMissionRequirements): Boolean {
        if (sequence.gestureType != GestureType.SWIPE) return false

        req.swipeDirection?.let { direction ->
            if (direction != SwipeDirection.ANY) {
                val actualDirection = detectSwipeDirection(sequence)
                if (actualDirection != direction) return false
            }
        }

        req.minVelocity?.let { if (sequence.averageVelocity < it) return false }
        req.maxVelocity?.let { if (sequence.averageVelocity > it) return false }
        req.minDistance?.let { if (sequence.directDistance < it) return false }

        return true
    }

    private fun validateCircle(sequence: TouchSequence, req: TouchMissionRequirements): Boolean {
        if (sequence.gestureType != GestureType.DRAW) return false

        // Calculate how circular the path is
        val circleScore = calculateCircleScore(sequence)
        val minAccuracy = req.circleAccuracy ?: 0.5f

        return circleScore >= minAccuracy
    }

    private fun validatePattern(sequence: TouchSequence, req: TouchMissionRequirements): Boolean {
        if (sequence.gestureType != GestureType.DRAW) return false

        req.minDistance?.let { if (sequence.totalDistance < it) return false }

        return true
    }

    private fun validateLongPress(sequence: TouchSequence, req: TouchMissionRequirements): Boolean {
        if (sequence.gestureType != GestureType.LONG_TAP) return false

        req.minDuration?.let { if (sequence.duration < it) return false }
        req.maxDuration?.let { if (sequence.duration > it) return false }
        req.targetRegion?.let { region ->
            if (sequence.events.firstOrNull()?.screenRegion != region) return false
        }

        return true
    }

    private fun validatePressure(sequence: TouchSequence, req: TouchMissionRequirements): Boolean {
        req.minPressure?.let { if (sequence.averagePressure < it) return false }
        req.maxPressure?.let { if (sequence.averagePressure > it) return false }

        return true
    }

    private fun validateSpeed(sequence: TouchSequence, req: TouchMissionRequirements): Boolean {
        // Speed challenge - just check the tap was within time limit
        req.maxDuration?.let { if (sequence.duration > it) return false }

        return true
    }

    private fun detectSwipeDirection(sequence: TouchSequence): SwipeDirection {
        val dx = sequence.endX - sequence.startX
        val dy = sequence.endY - sequence.startY

        return when {
            kotlin.math.abs(dx) > kotlin.math.abs(dy) -> {
                if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
            }
            else -> {
                if (dy > 0) SwipeDirection.DOWN else SwipeDirection.UP
            }
        }
    }

    private fun calculateCircleScore(sequence: TouchSequence): Float {
        if (sequence.events.size < 10) return 0f

        // Find center of all points
        val centerX = sequence.events.map { it.x }.average().toFloat()
        val centerY = sequence.events.map { it.y }.average().toFloat()

        // Calculate average radius
        val radii = sequence.events.map { event ->
            val dx = event.x - centerX
            val dy = event.y - centerY
            kotlin.math.sqrt(dx * dx + dy * dy)
        }
        val avgRadius = radii.average().toFloat()

        // Calculate variance from average radius
        val variance = radii.map { r ->
            val diff = r - avgRadius
            diff * diff
        }.average().toFloat()

        // Normalize variance to 0-1 score (lower variance = higher score)
        val normalizedVariance = variance / (avgRadius * avgRadius + 1f)
        val score = maxOf(0f, 1f - normalizedVariance * 2f)

        // Also check if path is closed (end near start)
        val closedness = 1f - minOf(sequence.directDistance / avgRadius, 1f)

        return (score + closedness) / 2f
    }
}

/**
 * Predefined touch missions.
 */
object TouchMissions {

    // Tap missions
    val TAP_10 = TouchMission(
        id = "tap_10",
        type = TouchMissionType.TAP,
        name = "Quick Tapper",
        description = "Tap the screen 10 times",
        targetCount = 10
    )

    val TAP_20 = TouchMission(
        id = "tap_20",
        type = TouchMissionType.TAP,
        name = "Tap Master",
        description = "Tap the screen 20 times",
        targetCount = 20
    )

    val TAP_SPEED = TouchMission(
        id = "tap_speed",
        type = TouchMissionType.SPEED,
        name = "Speed Demon",
        description = "Tap 10 times in 5 seconds",
        targetCount = 10,
        timeLimit = 5,
        requirements = TouchMissionRequirements(maxDuration = 150)
    )

    // Swipe missions
    val SWIPE_LEFT_5 = TouchMission(
        id = "swipe_left_5",
        type = TouchMissionType.SWIPE,
        name = "Left Swiper",
        description = "Swipe left 5 times",
        targetCount = 5,
        requirements = TouchMissionRequirements(
            swipeDirection = SwipeDirection.LEFT,
            minDistance = 100f
        )
    )

    val SWIPE_RIGHT_5 = TouchMission(
        id = "swipe_right_5",
        type = TouchMissionType.SWIPE,
        name = "Right Swiper",
        description = "Swipe right 5 times",
        targetCount = 5,
        requirements = TouchMissionRequirements(
            swipeDirection = SwipeDirection.RIGHT,
            minDistance = 100f
        )
    )

    val SWIPE_ANY_10 = TouchMission(
        id = "swipe_any_10",
        type = TouchMissionType.SWIPE,
        name = "Swipe Champion",
        description = "Swipe in any direction 10 times",
        targetCount = 10,
        requirements = TouchMissionRequirements(
            swipeDirection = SwipeDirection.ANY,
            minDistance = 100f
        )
    )

    // Draw missions
    val DRAW_CIRCLE = TouchMission(
        id = "draw_circle",
        type = TouchMissionType.DRAW_CIRCLE,
        name = "Circle Artist",
        description = "Draw a circle",
        targetCount = 1,
        requirements = TouchMissionRequirements(circleAccuracy = 0.5f)
    )

    val DRAW_CIRCLES_3 = TouchMission(
        id = "draw_circles_3",
        type = TouchMissionType.DRAW_CIRCLE,
        name = "Circle Master",
        description = "Draw 3 circles",
        targetCount = 3,
        requirements = TouchMissionRequirements(circleAccuracy = 0.4f)
    )

    val DRAW_PATTERN = TouchMission(
        id = "draw_pattern",
        type = TouchMissionType.DRAW_PATTERN,
        name = "Pattern Drawer",
        description = "Draw a pattern (at least 200px path)",
        targetCount = 5,
        requirements = TouchMissionRequirements(minDistance = 200f)
    )

    // Long press missions
    val LONG_PRESS_1S = TouchMission(
        id = "long_press_1s",
        type = TouchMissionType.LONG_PRESS,
        name = "Patient Touch",
        description = "Long press for at least 1 second",
        targetCount = 3,
        requirements = TouchMissionRequirements(minDuration = 1000)
    )

    val LONG_PRESS_2S = TouchMission(
        id = "long_press_2s",
        type = TouchMissionType.LONG_PRESS,
        name = "Very Patient",
        description = "Long press for at least 2 seconds",
        targetCount = 2,
        requirements = TouchMissionRequirements(minDuration = 2000)
    )

    // Pressure missions
    val PRESSURE_LIGHT = TouchMission(
        id = "pressure_light",
        type = TouchMissionType.PRESSURE,
        name = "Gentle Touch",
        description = "Touch with light pressure (< 0.3)",
        targetCount = 5,
        requirements = TouchMissionRequirements(maxPressure = 0.3f)
    )

    val PRESSURE_FIRM = TouchMission(
        id = "pressure_firm",
        type = TouchMissionType.PRESSURE,
        name = "Firm Touch",
        description = "Touch with firm pressure (> 0.6)",
        targetCount = 5,
        requirements = TouchMissionRequirements(minPressure = 0.6f)
    )

    // Multi-touch mission
    val MULTI_TAP = TouchMission(
        id = "multi_tap",
        type = TouchMissionType.MULTI_TAP,
        name = "Two Fingers",
        description = "Tap with two fingers 5 times",
        targetCount = 5
    )

    // All missions grouped by category
    val TAP_MISSIONS = listOf(TAP_10, TAP_20, TAP_SPEED)
    val SWIPE_MISSIONS = listOf(SWIPE_LEFT_5, SWIPE_RIGHT_5, SWIPE_ANY_10)
    val DRAW_MISSIONS = listOf(DRAW_CIRCLE, DRAW_CIRCLES_3, DRAW_PATTERN)
    val PRESS_MISSIONS = listOf(LONG_PRESS_1S, LONG_PRESS_2S)
    val PRESSURE_MISSIONS = listOf(PRESSURE_LIGHT, PRESSURE_FIRM)
    val MULTI_TOUCH_MISSIONS = listOf(MULTI_TAP)

    val ALL = TAP_MISSIONS + SWIPE_MISSIONS + DRAW_MISSIONS +
              PRESS_MISSIONS + PRESSURE_MISSIONS + MULTI_TOUCH_MISSIONS

    fun getById(id: String): TouchMission? = ALL.find { it.id == id }

    fun getByType(type: TouchMissionType): List<TouchMission> =
        ALL.filter { it.type == type }
}
