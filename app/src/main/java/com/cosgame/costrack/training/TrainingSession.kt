package com.cosgame.costrack.training

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A training session representing one completed mission.
 */
@Entity(tableName = "training_sessions")
data class TrainingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val activityType: ActivityType,

    // User-defined category (e.g., "me", "other")
    val category: String = "",

    val startTime: Long,

    val endTime: Long = 0,

    val samplesCount: Int = 0,

    val missionDurationSeconds: Int = 60,

    val completed: Boolean = false
) {
    val durationMs: Long get() = if (endTime > 0) endTime - startTime else 0

    companion object {
        fun start(activityType: ActivityType, durationSeconds: Int, category: String = ""): TrainingSession {
            return TrainingSession(
                activityType = activityType,
                category = category,
                startTime = System.currentTimeMillis(),
                missionDurationSeconds = durationSeconds
            )
        }
    }
}
