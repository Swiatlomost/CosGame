package com.cosgame.costrack.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cosgame.costrack.data.SensorRingBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages accelerometer sensor data collection.
 * Provides both callback-based and Flow-based APIs for sensor data.
 */
class AccelerometerManager(
    private val context: Context,
    private val config: SensorConfig = SensorConfig.DEFAULT
) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _latestReading = MutableStateFlow<SensorReading?>(null)
    val latestReading: StateFlow<SensorReading?> = _latestReading.asStateFlow()

    private val _isAvailable = MutableStateFlow(accelerometer != null)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /** Ring buffer for windowed data - used by classifiers */
    val ringBuffer = SensorRingBuffer(config.bufferSize)

    private var listener: SensorDataListener? = null
    private var readingCount: Long = 0

    /**
     * Check if accelerometer is available on this device.
     */
    fun isAccelerometerAvailable(): Boolean = accelerometer != null

    /**
     * Get sensor information.
     */
    fun getSensorInfo(): AccelerometerInfo? {
        return accelerometer?.let {
            AccelerometerInfo(
                name = it.name,
                vendor = it.vendor,
                version = it.version,
                resolution = it.resolution,
                maxRange = it.maximumRange,
                minDelay = it.minDelay,
                maxDelay = it.maxDelay,
                power = it.power
            )
        }
    }

    /**
     * Start collecting accelerometer data.
     *
     * @param dataListener Optional listener for sensor events
     * @return true if started successfully, false if sensor not available
     */
    fun start(dataListener: SensorDataListener? = null): Boolean {
        if (accelerometer == null) {
            dataListener?.onSensorError(
                SensorType.ACCELEROMETER,
                "Accelerometer not available on this device"
            )
            return false
        }

        if (_isActive.value) {
            return true // Already running
        }

        listener = dataListener
        readingCount = 0
        ringBuffer.clear()

        val registered = sensorManager.registerListener(
            this,
            accelerometer,
            config.samplingPeriodUs,
            config.maxReportLatencyUs
        )

        if (registered) {
            _isActive.value = true
        } else {
            dataListener?.onSensorError(
                SensorType.ACCELEROMETER,
                "Failed to register sensor listener"
            )
        }

        return registered
    }

    /**
     * Stop collecting accelerometer data.
     */
    fun stop() {
        if (!_isActive.value) return

        sensorManager.unregisterListener(this)
        _isActive.value = false
        listener = null
    }

    /**
     * Get current buffer fill percentage (0.0 to 1.0).
     */
    fun getBufferFillRatio(): Float {
        return ringBuffer.size.toFloat() / config.bufferSize
    }

    /**
     * Check if buffer has enough data for classification.
     */
    fun isBufferReady(): Boolean = ringBuffer.isFull

    /**
     * Get the number of readings collected since start.
     */
    fun getReadingCount(): Long = readingCount

    // SensorEventListener implementation

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val reading = SensorReading(
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
            timestamp = event.timestamp
        )

        // Update ring buffer
        ringBuffer.push(reading.x, reading.y, reading.z)

        // Update state flow
        _latestReading.value = reading

        // Increment counter
        readingCount++

        // Notify listener
        listener?.onSensorData(SensorType.ACCELEROMETER, reading)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_ACCELEROMETER) {
            listener?.onSensorAccuracyChanged(SensorType.ACCELEROMETER, accuracy)
        }
    }
}

/**
 * Information about the accelerometer sensor.
 */
data class AccelerometerInfo(
    val name: String,
    val vendor: String,
    val version: Int,
    val resolution: Float,  // m/s²
    val maxRange: Float,    // m/s²
    val minDelay: Int,      // microseconds
    val maxDelay: Int,      // microseconds
    val power: Float        // mA
) {
    val maxFrequencyHz: Int
        get() = if (minDelay > 0) 1_000_000 / minDelay else 0

    val minFrequencyHz: Int
        get() = if (maxDelay > 0) 1_000_000 / maxDelay else 0
}
