package com.cosgame.costrack.sensor

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class SensorReadingTest {

    @Test
    fun `magnitude calculates correctly for unit vector`() {
        val reading = SensorReading(1f, 0f, 0f, 0L)
        assertEquals(1f, reading.magnitude, 0.001f)
    }

    @Test
    fun `magnitude calculates correctly for 3-4-5 triangle`() {
        // sqrt(3² + 4² + 0²) = 5
        val reading = SensorReading(3f, 4f, 0f, 0L)
        assertEquals(5f, reading.magnitude, 0.001f)
    }

    @Test
    fun `magnitude calculates correctly for 3D vector`() {
        // sqrt(1² + 2² + 2²) = sqrt(9) = 3
        val reading = SensorReading(1f, 2f, 2f, 0L)
        assertEquals(3f, reading.magnitude, 0.001f)
    }

    @Test
    fun `magnitude is zero for zero vector`() {
        val reading = SensorReading(0f, 0f, 0f, 0L)
        assertEquals(0f, reading.magnitude, 0.001f)
    }

    @Test
    fun `toFloatArray returns correct values`() {
        val reading = SensorReading(1.5f, 2.5f, 3.5f, 12345L)
        val array = reading.toFloatArray()

        assertEquals(3, array.size)
        assertEquals(1.5f, array[0], 0.001f)
        assertEquals(2.5f, array[1], 0.001f)
        assertEquals(3.5f, array[2], 0.001f)
    }

    @Test
    fun `data class equality works`() {
        val r1 = SensorReading(1f, 2f, 3f, 100L)
        val r2 = SensorReading(1f, 2f, 3f, 100L)
        val r3 = SensorReading(1f, 2f, 3f, 200L)

        assertEquals(r1, r2)
        assertNotEquals(r1, r3)
    }

    @Test
    fun `negative values work correctly`() {
        val reading = SensorReading(-9.8f, 0f, 0f, 0L)
        assertEquals(9.8f, reading.magnitude, 0.001f)
    }

    @Test
    fun `typical gravity reading magnitude`() {
        // Typical stationary phone reading ~9.8 m/s²
        val reading = SensorReading(0f, 0f, 9.81f, 0L)
        assertEquals(9.81f, reading.magnitude, 0.01f)
    }
}

class SensorConfigTest {

    @Test
    fun `default config has 50Hz sampling`() {
        val config = SensorConfig.DEFAULT
        assertEquals(50, config.samplingFrequencyHz)
    }

    @Test
    fun `high frequency config has 100Hz sampling`() {
        val config = SensorConfig.HIGH_FREQUENCY
        assertEquals(100, config.samplingFrequencyHz)
    }

    @Test
    fun `low power config has 25Hz sampling`() {
        val config = SensorConfig.LOW_POWER
        assertEquals(25, config.samplingFrequencyHz)
    }

    @Test
    fun `custom config calculates frequency correctly`() {
        val config = SensorConfig(samplingPeriodUs = 5_000) // 200 Hz
        assertEquals(200, config.samplingFrequencyHz)
    }

    @Test
    fun `default buffer size is 128`() {
        val config = SensorConfig.DEFAULT
        assertEquals(128, config.bufferSize)
    }

    @Test
    fun `custom buffer size works`() {
        val config = SensorConfig(bufferSize = 256)
        assertEquals(256, config.bufferSize)
    }
}

class SensorTypeTest {

    @Test
    fun `sensor types are defined`() {
        assertEquals(2, SensorType.values().size)
        assertNotNull(SensorType.ACCELEROMETER)
        assertNotNull(SensorType.GYROSCOPE)
    }
}

class AccelerometerInfoTest {

    @Test
    fun `max frequency calculated from min delay`() {
        val info = AccelerometerInfo(
            name = "Test",
            vendor = "Test",
            version = 1,
            resolution = 0.001f,
            maxRange = 39.2f,
            minDelay = 5000, // 5ms = 200 Hz max
            maxDelay = 200000,
            power = 0.5f
        )
        assertEquals(200, info.maxFrequencyHz)
    }

    @Test
    fun `min frequency calculated from max delay`() {
        val info = AccelerometerInfo(
            name = "Test",
            vendor = "Test",
            version = 1,
            resolution = 0.001f,
            maxRange = 39.2f,
            minDelay = 5000,
            maxDelay = 200000, // 200ms = 5 Hz min
            power = 0.5f
        )
        assertEquals(5, info.minFrequencyHz)
    }

    @Test
    fun `zero delay returns zero frequency`() {
        val info = AccelerometerInfo(
            name = "Test",
            vendor = "Test",
            version = 1,
            resolution = 0.001f,
            maxRange = 39.2f,
            minDelay = 0,
            maxDelay = 0,
            power = 0.5f
        )
        assertEquals(0, info.maxFrequencyHz)
        assertEquals(0, info.minFrequencyHz)
    }
}
