package com.cosgame.costrack.training

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single sensor sample with activity label for training.
 */
@Entity(tableName = "training_samples")
data class TrainingSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val activityType: ActivityType,

    // User-defined category (e.g., "me", "other")
    val category: String = "",

    // Accelerometer data (m/s²)
    val accX: Float,
    val accY: Float,
    val accZ: Float,

    // Gyroscope data (rad/s)
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,

    // Computed magnitude of acceleration
    val magnitude: Float,

    // Session this sample belongs to
    val sessionId: Long,

    // Timestamp when sample was captured
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Create a TrainingSample from raw sensor values.
         */
        fun create(
            activityType: ActivityType,
            accX: Float,
            accY: Float,
            accZ: Float,
            gyroX: Float,
            gyroY: Float,
            gyroZ: Float,
            sessionId: Long,
            category: String = ""
        ): TrainingSample {
            val magnitude = kotlin.math.sqrt(accX * accX + accY * accY + accZ * accZ)
            return TrainingSample(
                activityType = activityType,
                category = category,
                accX = accX,
                accY = accY,
                accZ = accZ,
                gyroX = gyroX,
                gyroY = gyroY,
                gyroZ = gyroZ,
                magnitude = magnitude,
                sessionId = sessionId
            )
        }
    }
}
