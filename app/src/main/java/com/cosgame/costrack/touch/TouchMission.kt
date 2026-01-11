package com.cosgame.costrack.touch

/**
 * Types of touch missions.
 */
enum class TouchMissionType(
    val id: String,
    val displayName: String,
    val description: String,
    val icon: String,
    val durationSeconds: Int,
    val instructions: String
) {
    TAP_RHYTHM(
        id = "tap_rhythm",
        displayName = "Tap Rhythm",
        description = "Tap in rhythm with the beat",
        icon = "🎵",
        durationSeconds = 30,
        instructions = "Tap the screen in a steady rhythm. Try to keep a consistent pace."
    ),

    PRECISION_SWIPE(
        id = "precision_swipe",
        displayName = "Precision Swipe",
        description = "Swipe along the line",
        icon = "➡️",
        durationSeconds = 30,
        instructions = "Swipe across the screen in straight lines. Try to be as straight as possible."
    ),

    SPEED_TAP(
        id = "speed_tap",
        displayName = "Speed Tap",
        description = "Tap as fast as you can",
        icon = "⚡",
        durationSeconds = 15,
        instructions = "Tap the screen as fast as you can! Go go go!"
    ),

    DRAW_SHAPE(
        id = "draw_shape",
        displayName = "Draw Shape",
        description = "Draw circles and patterns",
        icon = "⭕",
        durationSeconds = 30,
        instructions = "Draw circles or any patterns you like on the screen."
    ),

    FREE_USAGE(
        id = "free_usage",
        displayName = "Free Usage",
        description = "Use the screen naturally",
        icon = "👆",
        durationSeconds = 60,
        instructions = "Use the screen naturally - tap, swipe, whatever feels comfortable."
    ),

    MULTI_TOUCH(
        id = "multi_touch",
        displayName = "Multi Touch",
        description = "Use multiple fingers",
        icon = "🖐️",
        durationSeconds = 30,
        instructions = "Use multiple fingers to tap and interact with the screen."
    );

    companion object {
        fun fromId(id: String): TouchMissionType? = entries.find { it.id == id }

        val ALL = entries.toList()
    }
}

/**
 * Result of a completed mission.
 */
data class TouchMissionResult(
    val missionType: TouchMissionType,
    val session: TouchSession?,
    val completed: Boolean,
    val tapCount: Int,
    val swipeCount: Int,
    val avgPressure: Float,
    val duration: Long
) {
    val score: Int get() = when (missionType) {
        TouchMissionType.SPEED_TAP -> tapCount * 10
        TouchMissionType.TAP_RHYTHM -> (100 - (avgPressure * 20).toInt()).coerceAtLeast(0)
        TouchMissionType.PRECISION_SWIPE -> swipeCount * 15
        TouchMissionType.DRAW_SHAPE -> ((session?.totalEvents ?: 0) / 10)
        TouchMissionType.FREE_USAGE -> tapCount + swipeCount * 2
        TouchMissionType.MULTI_TOUCH -> tapCount * 5
    }

    val scoreFormatted: String get() = "$score pts"
}
