package com.cosgame.costrack.integration

import com.cosgame.costrack.aggregator.AggregationStrategy
import com.cosgame.costrack.aggregator.DnaAggregator
import com.cosgame.costrack.aggregator.DnaConfig
import com.cosgame.costrack.classifier.ClassificationResult
import com.cosgame.costrack.classifier.HarActivity
import com.cosgame.costrack.data.SensorRingBuffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

/**
 * Integration tests for the sensor → classifier → aggregator flow.
 * Tests the data pipeline without requiring Android context.
 */
class SensorClassifierIntegrationTest {

    private lateinit var accelBuffer: SensorRingBuffer
    private lateinit var gyroBuffer: SensorRingBuffer
    private lateinit var aggregator: DnaAggregator

    @Before
    fun setUp() {
        accelBuffer = SensorRingBuffer(128)
        gyroBuffer = SensorRingBuffer(128)
        aggregator = DnaAggregator(DnaConfig(
            historySize = 10,
            strategy = AggregationStrategy.RECENT_WEIGHTED,
            stabilityThreshold = 3
        ))
    }

    @Test
    fun `sensor buffers fill correctly with simulated data`() {
        // Simulate sensor data collection at 50Hz for 2.56 seconds (128 samples)
        repeat(128) { i ->
            val t = i * 0.02f // 20ms intervals
            // Simulate walking pattern
            accelBuffer.push(
                sin(t * 2 * Math.PI.toFloat()) * 2f,      // x: lateral sway
                9.8f + sin(t * 4 * Math.PI.toFloat()),    // y: vertical bounce
                sin(t * 2 * Math.PI.toFloat()) * 0.5f     // z: forward motion
            )
            gyroBuffer.push(
                sin(t * 2 * Math.PI.toFloat()) * 0.3f,    // pitch
                sin(t * Math.PI.toFloat()) * 0.1f,        // yaw
                sin(t * 2 * Math.PI.toFloat()) * 0.2f     // roll
            )
        }

        assertTrue("Accel buffer should be full", accelBuffer.isFull)
        assertTrue("Gyro buffer should be full", gyroBuffer.isFull)
        assertEquals(128, accelBuffer.size)
        assertEquals(128, gyroBuffer.size)
    }

    @Test
    fun `interleaved arrays have correct shape for classifier`() {
        fillBuffersWithWalkingData()

        val accelData = accelBuffer.toInterleavedArray()
        val gyroData = gyroBuffer.toInterleavedArray()

        assertEquals("Accel should have 128*3 values", 128 * 3, accelData.size)
        assertEquals("Gyro should have 128*3 values", 128 * 3, gyroData.size)

        // Verify interleaved format [x0,y0,z0,x1,y1,z1,...]
        for (i in 0 until 128) {
            val baseIdx = i * 3
            // Values should be in [x,y,z] triplets
            assertTrue("X value at sample $i", accelData[baseIdx].isFinite())
            assertTrue("Y value at sample $i", accelData[baseIdx + 1].isFinite())
            assertTrue("Z value at sample $i", accelData[baseIdx + 2].isFinite())
        }
    }

    @Test
    fun `aggregator accumulates results correctly`() {
        // Simulate a series of classification results
        val results = listOf(
            createMockResult("walking", 0.8f),
            createMockResult("walking", 0.75f),
            createMockResult("standing", 0.6f),
            createMockResult("walking", 0.85f),
            createMockResult("walking", 0.9f)
        )

        results.forEach { aggregator.addResult(it) }

        val aggregated = aggregator.aggregatedResult.value
        assertNotNull("Should have aggregated result", aggregated)
        assertEquals("Should have 5 samples", 5, aggregated!!.sampleCount)
    }

    @Test
    fun `aggregator stability detection works`() {
        // Feed consistent results
        repeat(5) {
            aggregator.addResult(createMockResult("sitting", 0.9f))
        }

        assertTrue("Should be stable after consistent inputs", aggregator.isStable.value)
        assertEquals("sitting", aggregator.getStableLabel())
    }

    @Test
    fun `aggregator handles label changes`() {
        // Start with walking
        repeat(3) {
            aggregator.addResult(createMockResult("walking", 0.8f))
        }
        assertTrue(aggregator.isStable.value)

        // Transition to sitting
        aggregator.addResult(createMockResult("sitting", 0.7f))
        assertFalse("Should not be stable during transition", aggregator.isStable.value)

        // Stabilize on sitting
        repeat(3) {
            aggregator.addResult(createMockResult("sitting", 0.9f))
        }
        assertTrue("Should stabilize on new activity", aggregator.isStable.value)
        assertEquals("sitting", aggregator.getStableLabel())
    }

    @Test
    fun `confidence weighted aggregation prioritizes high confidence`() {
        aggregator = DnaAggregator(DnaConfig(
            historySize = 10,
            strategy = AggregationStrategy.WEIGHTED_AVERAGE,
            stabilityThreshold = 3
        ))

        // Low confidence walking
        aggregator.addResult(createMockResult("walking", 0.3f))
        aggregator.addResult(createMockResult("walking", 0.3f))

        // High confidence standing
        aggregator.addResult(createMockResult("standing", 0.95f))

        val result = aggregator.aggregatedResult.value
        assertNotNull(result)
        // Standing should win due to higher confidence weight
        assertEquals("standing", result!!.label)
    }

    @Test
    fun `recent weighted strategy favors newer results`() {
        aggregator = DnaAggregator(DnaConfig(
            historySize = 5,
            strategy = AggregationStrategy.RECENT_WEIGHTED,
            recencyDecay = 0.5f,
            stabilityThreshold = 2
        ))

        // Old results: walking
        repeat(3) {
            aggregator.addResult(createMockResult("walking", 0.8f))
        }

        // New results: standing
        repeat(2) {
            aggregator.addResult(createMockResult("standing", 0.8f))
        }

        val result = aggregator.aggregatedResult.value
        assertNotNull(result)
        // Standing should have more weight due to recency
        assertEquals("standing", result!!.label)
    }

    @Test
    fun `buffer data integrity during continuous operation`() {
        // Simulate continuous sensor operation with wraparound
        repeat(500) { i ->
            accelBuffer.push(i.toFloat(), (i * 2).toFloat(), (i * 3).toFloat())
            gyroBuffer.push(i * 0.1f, i * 0.2f, i * 0.3f)
        }

        // Buffer should still be at capacity
        assertEquals(128, accelBuffer.size)
        assertTrue(accelBuffer.isFull)

        // Verify latest data is preserved (last 128 samples: 372-499)
        val data = accelBuffer.toInterleavedArray()
        // First sample in buffer should be around 372
        val firstX = data[0]
        assertTrue("First X should be recent", firstX >= 372f)
    }

    @Test
    fun `aggregator reset clears state`() {
        repeat(5) {
            aggregator.addResult(createMockResult("walking", 0.8f))
        }
        assertTrue(aggregator.isStable.value)

        aggregator.reset()

        assertFalse(aggregator.isStable.value)
        assertNull(aggregator.getStableLabel())
        assertEquals(0, aggregator.getHistorySize())
    }

    @Test
    fun `all HAR activities can be represented`() {
        HarActivity.standardActivities.forEach { activity ->
            aggregator.reset()
            repeat(5) {
                aggregator.addResult(createMockResult(activity.label, 0.9f))
            }
            assertEquals(activity.label, aggregator.getStableLabel())
        }
    }

    // Helper methods

    private fun fillBuffersWithWalkingData() {
        repeat(128) { i ->
            val t = i * 0.02f
            accelBuffer.push(
                sin(t * 2 * Math.PI.toFloat()) * 2f,
                9.8f + sin(t * 4 * Math.PI.toFloat()),
                sin(t * 2 * Math.PI.toFloat()) * 0.5f
            )
            gyroBuffer.push(
                sin(t * 2 * Math.PI.toFloat()) * 0.3f,
                sin(t * Math.PI.toFloat()) * 0.1f,
                sin(t * 2 * Math.PI.toFloat()) * 0.2f
            )
        }
    }

    private fun createMockResult(
        label: String,
        confidence: Float
    ): ClassificationResult {
        val allProbs = HarActivity.standardActivities.associate { activity ->
            if (activity.label == label) {
                activity.label to confidence
            } else {
                activity.label to (1f - confidence) / (HarActivity.standardActivities.size - 1)
            }
        }

        return ClassificationResult(
            classifierId = "test_classifier",
            label = label,
            confidence = confidence,
            allProbabilities = allProbs,
            inferenceTimeMs = Random.nextLong(5, 20)
        )
    }
}
