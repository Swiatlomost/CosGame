package com.cosgame.costrack.touch

/**
 * Represents a single touch event with all relevant data.
 */
data class TouchEvent(
    val id: Long = 0,
    val sessionId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),

    // Position
    val x: Float,
    val y: Float,

    // Touch properties
    val pressure: Float,
    val size: Float,

    // Event type
    val action: TouchAction,

    // Multi-touch support
    val fingerId: Int,
    val fingerCount: Int,

    // Screen info for normalization
    val screenWidth: Int = 0,
    val screenHeight: Int = 0
) {
    // Normalized position (0-1)
    val normalizedX: Float get() = if (screenWidth > 0) x / screenWidth else x
    val normalizedY: Float get() = if (screenHeight > 0) y / screenHeight else y

    // Screen region (3x3 grid)
    val screenRegion: ScreenRegion get() {
        val col = when {
            normalizedX < 0.33f -> 0
            normalizedX < 0.66f -> 1
            else -> 2
        }
        val row = when {
            normalizedY < 0.33f -> 0
            normalizedY < 0.66f -> 1
            else -> 2
        }
        return ScreenRegion.fromPosition(row, col)
    }
}

/**
 * Touch action types.
 */
enum class TouchAction {
    DOWN,           // Finger touched screen
    MOVE,           // Finger moved
    UP,             // Finger lifted
    POINTER_DOWN,   // Additional finger touched
    POINTER_UP,     // Additional finger lifted
    CANCEL          // Touch cancelled
}

/**
 * Screen regions (3x3 grid).
 */
enum class ScreenRegion(val row: Int, val col: Int) {
    TOP_LEFT(0, 0),
    TOP_CENTER(0, 1),
    TOP_RIGHT(0, 2),
    MIDDLE_LEFT(1, 0),
    MIDDLE_CENTER(1, 1),
    MIDDLE_RIGHT(1, 2),
    BOTTOM_LEFT(2, 0),
    BOTTOM_CENTER(2, 1),
    BOTTOM_RIGHT(2, 2);

    companion object {
        fun fromPosition(row: Int, col: Int): ScreenRegion {
            return entries.find { it.row == row && it.col == col } ?: MIDDLE_CENTER
        }
    }
}

/**
 * A sequence of touch events forming a gesture.
 */
data class TouchSequence(
    val events: List<TouchEvent>,
    val startTime: Long = events.firstOrNull()?.timestamp ?: 0,
    val endTime: Long = events.lastOrNull()?.timestamp ?: 0
) {
    val duration: Long get() = endTime - startTime
    val eventCount: Int get() = events.size

    // First and last positions
    val startX: Float get() = events.firstOrNull()?.x ?: 0f
    val startY: Float get() = events.firstOrNull()?.y ?: 0f
    val endX: Float get() = events.lastOrNull()?.x ?: 0f
    val endY: Float get() = events.lastOrNull()?.y ?: 0f

    // Distance traveled
    val totalDistance: Float get() {
        var distance = 0f
        for (i in 1 until events.size) {
            val dx = events[i].x - events[i-1].x
            val dy = events[i].y - events[i-1].y
            distance += kotlin.math.sqrt(dx * dx + dy * dy)
        }
        return distance
    }

    // Direct distance (start to end)
    val directDistance: Float get() {
        val dx = endX - startX
        val dy = endY - startY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    // Average velocity (pixels per second)
    val averageVelocity: Float get() {
        return if (duration > 0) totalDistance / (duration / 1000f) else 0f
    }

    // Average pressure
    val averagePressure: Float get() {
        return if (events.isNotEmpty()) events.map { it.pressure }.average().toFloat() else 0f
    }

    // Gesture type detection
    val gestureType: GestureType get() {
        return when {
            duration < 200 && totalDistance < 50 -> GestureType.TAP
            duration < 500 && totalDistance < 50 -> GestureType.LONG_TAP
            directDistance > 100 && averageVelocity > 500 -> GestureType.SWIPE
            totalDistance > 200 && directDistance < totalDistance * 0.3f -> GestureType.DRAW
            else -> GestureType.DRAG
        }
    }
}

/**
 * Basic gesture types.
 */
enum class GestureType {
    TAP,
    LONG_TAP,
    SWIPE,
    DRAG,
    DRAW,
    PINCH,
    ROTATE,
    UNKNOWN
}
