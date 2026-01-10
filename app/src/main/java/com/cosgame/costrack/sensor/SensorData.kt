package com.cosgame.costrack.sensor

/**
 * Represents a single 3-axis sensor reading with timestamp.
 */
data class SensorReading(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long // nanoseconds from SystemClock.elapsedRealtimeNanos()
) {
    /**
     * Calculate magnitude of the 3D vector.
     */
    val magnitude: Float
        get() = kotlin.math.sqrt(x * x + y * y + z * z)

    /**
     * Returns values as FloatArray [x, y, z].
     */
    fun toFloatArray(): FloatArray = floatArrayOf(x, y, z)
}

/**
 * Sensor types supported by the application.
 */
enum class SensorType {
    ACCELEROMETER,
    GYROSCOPE
}

/**
 * Listener interface for sensor data updates.
 */
interface SensorDataListener {
    fun onSensorData(type: SensorType, reading: SensorReading)
    fun onSensorAccuracyChanged(type: SensorType, accuracy: Int)
    fun onSensorError(type: SensorType, error: String)
}

/**
 * Configuration for sensor sampling.
 */
data class SensorConfig(
    val samplingPeriodUs: Int = 20_000, // 50 Hz default (20ms)
    val maxReportLatencyUs: Int = 0,    // No batching by default
    val bufferSize: Int = 128           // Window size for classifiers
) {
    companion object {
        /** 50 Hz sampling - good balance of accuracy and battery */
        val DEFAULT = SensorConfig(samplingPeriodUs = 20_000)

        /** 100 Hz sampling - higher accuracy for fast movements */
        val HIGH_FREQUENCY = SensorConfig(samplingPeriodUs = 10_000)

        /** 25 Hz sampling - battery saving mode */
        val LOW_POWER = SensorConfig(samplingPeriodUs = 40_000)
    }

    val samplingFrequencyHz: Int
        get() = 1_000_000 / samplingPeriodUs
}
