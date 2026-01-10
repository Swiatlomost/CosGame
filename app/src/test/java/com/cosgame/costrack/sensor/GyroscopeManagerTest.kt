package com.cosgame.costrack.sensor

import org.junit.Assert.*
import org.junit.Test

class GyroscopeInfoTest {

    @Test
    fun `max frequency calculated from min delay`() {
        val info = GyroscopeInfo(
            name = "Test Gyroscope",
            vendor = "Test",
            version = 1,
            resolution = 0.001f,
            maxRange = 34.9f, // ~2000 deg/s
            minDelay = 5000, // 5ms = 200 Hz max
            maxDelay = 200000,
            power = 0.5f
        )
        assertEquals(200, info.maxFrequencyHz)
    }

    @Test
    fun `min frequency calculated from max delay`() {
        val info = GyroscopeInfo(
            name = "Test Gyroscope",
            vendor = "Test",
            version = 1,
            resolution = 0.001f,
            maxRange = 34.9f,
            minDelay = 5000,
            maxDelay = 200000, // 200ms = 5 Hz min
            power = 0.5f
        )
        assertEquals(5, info.minFrequencyHz)
    }

    @Test
    fun `zero delay returns zero frequency`() {
        val info = GyroscopeInfo(
            name = "Test",
            vendor = "Test",
            version = 1,
            resolution = 0.001f,
            maxRange = 34.9f,
            minDelay = 0,
            maxDelay = 0,
            power = 0.5f
        )
        assertEquals(0, info.maxFrequencyHz)
        assertEquals(0, info.minFrequencyHz)
    }

    @Test
    fun `max range converts to degrees per second`() {
        // PI rad/s = 180 deg/s
        val info = GyroscopeInfo(
            name = "Test",
            vendor = "Test",
            version = 1,
            resolution = 0.001f,
            maxRange = Math.PI.toFloat(), // PI rad/s
            minDelay = 5000,
            maxDelay = 200000,
            power = 0.5f
        )
        assertEquals(180f, info.maxRangeDegPerSec, 0.1f)
    }

    @Test
    fun `typical gyroscope range converts correctly`() {
        // ~34.9 rad/s ≈ 2000 deg/s (common smartphone gyroscope)
        val info = GyroscopeInfo(
            name = "Test",
            vendor = "Test",
            version = 1,
            resolution = 0.001f,
            maxRange = 34.9066f, // 2000 * PI / 180
            minDelay = 5000,
            maxDelay = 200000,
            power = 0.5f
        )
        assertEquals(2000f, info.maxRangeDegPerSec, 1f)
    }

    @Test
    fun `data class equality works`() {
        val g1 = GyroscopeInfo("G1", "V1", 1, 0.001f, 34.9f, 5000, 200000, 0.5f)
        val g2 = GyroscopeInfo("G1", "V1", 1, 0.001f, 34.9f, 5000, 200000, 0.5f)
        val g3 = GyroscopeInfo("G2", "V1", 1, 0.001f, 34.9f, 5000, 200000, 0.5f)

        assertEquals(g1, g2)
        assertNotEquals(g1, g3)
    }
}

class GyroscopeReadingInterpretationTest {

    @Test
    fun `stationary device has near-zero readings`() {
        // When device is stationary, gyroscope should read ~0
        val reading = SensorReading(0.01f, -0.02f, 0.005f, 0L)
        // Magnitude should be very small
        assertTrue(reading.magnitude < 0.1f)
    }

    @Test
    fun `rotation around single axis`() {
        // Rotation around Z axis only (like spinning phone on table)
        val reading = SensorReading(0f, 0f, 5.0f, 0L) // ~286 deg/s
        assertEquals(5.0f, reading.magnitude, 0.001f)
    }

    @Test
    fun `combined rotation magnitude`() {
        // Rotation around multiple axes
        val reading = SensorReading(1f, 2f, 2f, 0L)
        assertEquals(3f, reading.magnitude, 0.001f)
    }

    @Test
    fun `angular velocity to degrees conversion`() {
        // 1 rad/s = ~57.3 deg/s
        val radPerSec = 1f
        val degPerSec = radPerSec * 180f / Math.PI.toFloat()
        assertEquals(57.3f, degPerSec, 0.1f)
    }
}
