package com.cosgame.costrack.touch

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Touch session entity - stores collected touch data.
 */
@Entity(tableName = "touch_sessions")
data class TouchSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val startTime: Long,
    val endTime: Long,

    // Counts
    val tapCount: Int,
    val swipeCount: Int,
    val totalEvents: Int,

    // Averages
    val avgPressure: Float,
    val avgTapDuration: Float,
    val avgSwipeVelocity: Float,

    // Raw data as JSON
    val touchEventsJson: String,

    // Label for training (optional)
    val label: String? = null,

    // Mission info (optional)
    val missionType: String? = null,
    val missionCompleted: Boolean = false
) {
    val duration: Long get() = endTime - startTime

    val durationFormatted: String get() {
        val seconds = duration / 1000
        return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
    }

    val dataSizeBytes: Int get() = touchEventsJson.length * 2

    val dataSizeFormatted: String get() = when {
        dataSizeBytes < 1024 -> "$dataSizeBytes B"
        dataSizeBytes < 1024 * 1024 -> String.format("%.1f KB", dataSizeBytes / 1024.0)
        else -> String.format("%.2f MB", dataSizeBytes / (1024.0 * 1024.0))
    }
}

/**
 * Statistics calculated from a touch session.
 */
data class TouchSessionStats(
    val tapCount: Int = 0,
    val swipeCount: Int = 0,
    val totalEvents: Int = 0,
    val avgPressure: Float = 0f,
    val avgTapDuration: Float = 0f,
    val avgSwipeVelocity: Float = 0f,
    val tapRate: Float = 0f,  // per minute
    val zoneDistribution: FloatArray = FloatArray(9)  // 3x3 grid
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TouchSessionStats
        return tapCount == other.tapCount &&
                swipeCount == other.swipeCount &&
                totalEvents == other.totalEvents
    }

    override fun hashCode(): Int {
        return 31 * tapCount + swipeCount + totalEvents
    }
}
