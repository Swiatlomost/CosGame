package com.cosgame.costrack.touch

/**
 * Type of touch event.
 */
enum class TouchEventType {
    DOWN,
    MOVE,
    UP
}

/**
 * Single touch event with normalized coordinates.
 */
data class TouchEvent(
    val x: Float,           // Normalized 0-1
    val y: Float,           // Normalized 0-1
    val pressure: Float,    // 0-1
    val size: Float,        // 0-1
    val timestamp: Long,    // System.currentTimeMillis()
    val eventType: TouchEventType,
    val fingerId: Int
) {
    companion object {
        /**
         * Create TouchEvent from raw screen coordinates.
         */
        fun fromRaw(
            rawX: Float,
            rawY: Float,
            screenWidth: Int,
            screenHeight: Int,
            pressure: Float,
            size: Float,
            eventType: TouchEventType,
            fingerId: Int
        ): TouchEvent {
            return TouchEvent(
                x = (rawX / screenWidth).coerceIn(0f, 1f),
                y = (rawY / screenHeight).coerceIn(0f, 1f),
                pressure = pressure.coerceIn(0f, 1f),
                size = size.coerceIn(0f, 1f),
                timestamp = System.currentTimeMillis(),
                eventType = eventType,
                fingerId = fingerId
            )
        }
    }

    /**
     * Get zone index (0-8) for 3x3 grid.
     */
    fun getZone3x3(): Int {
        val col = (x * 3).toInt().coerceIn(0, 2)
        val row = (y * 3).toInt().coerceIn(0, 2)
        return row * 3 + col
    }

    /**
     * Get zone index for 10x20 grid (for heatmap).
     */
    fun getZone10x20(): Int {
        val col = (x * 10).toInt().coerceIn(0, 9)
        val row = (y * 20).toInt().coerceIn(0, 19)
        return row * 10 + col
    }
}

/**
 * Represents a complete gesture (from DOWN to UP).
 */
data class TouchGesture(
    val events: List<TouchEvent>,
    val fingerId: Int
) {
    val startTime: Long get() = events.firstOrNull()?.timestamp ?: 0
    val endTime: Long get() = events.lastOrNull()?.timestamp ?: 0
    val duration: Long get() = endTime - startTime

    val startX: Float get() = events.firstOrNull()?.x ?: 0f
    val startY: Float get() = events.firstOrNull()?.y ?: 0f
    val endX: Float get() = events.lastOrNull()?.x ?: 0f
    val endY: Float get() = events.lastOrNull()?.y ?: 0f

    val avgPressure: Float get() = if (events.isNotEmpty()) {
        events.map { it.pressure }.average().toFloat()
    } else 0f

    /**
     * Total distance traveled (sum of all segments).
     */
    val totalDistance: Float get() {
        if (events.size < 2) return 0f
        var dist = 0f
        for (i in 1 until events.size) {
            val dx = events[i].x - events[i - 1].x
            val dy = events[i].y - events[i - 1].y
            dist += kotlin.math.sqrt(dx * dx + dy * dy)
        }
        return dist
    }

    /**
     * Direct distance from start to end.
     */
    val directDistance: Float get() {
        val dx = endX - startX
        val dy = endY - startY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Velocity in normalized units per second.
     */
    val velocity: Float get() {
        return if (duration > 0) totalDistance / (duration / 1000f) else 0f
    }

    /**
     * Linearity - how straight the gesture is (1 = perfectly straight).
     */
    val linearity: Float get() {
        return if (totalDistance > 0) {
            (directDistance / totalDistance).coerceIn(0f, 1f)
        } else 1f
    }

    /**
     * Is this a tap (short duration, little movement)?
     */
    val isTap: Boolean get() = duration < 300 && totalDistance < 0.05f

    /**
     * Is this a swipe (fast, mostly linear)?
     */
    val isSwipe: Boolean get() = velocity > 0.3f && linearity > 0.7f && totalDistance > 0.1f
}
