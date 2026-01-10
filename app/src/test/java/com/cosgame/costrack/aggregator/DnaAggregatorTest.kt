package com.cosgame.costrack.aggregator

import com.cosgame.costrack.classifier.ClassificationResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DnaAggregatorTest {

    private lateinit var aggregator: DnaAggregator

    private fun createResult(label: String, confidence: Float): ClassificationResult {
        return ClassificationResult(
            classifierId = "test",
            label = label,
            confidence = confidence,
            allProbabilities = mapOf(
                label to confidence,
                "other" to (1 - confidence)
            )
        )
    }

    @Before
    fun setup() {
        aggregator = DnaAggregator(DnaConfig(
            historySize = 5,
            stabilityThreshold = 3,
            minSamplesForAggregation = 2
        ))
    }

    @Test
    fun `empty aggregator returns empty result`() {
        val result = aggregator.aggregate()
        assertEquals("unknown", result.label)
        assertEquals(0f, result.confidence, 0.001f)
        assertEquals(0, result.sampleCount)
    }

    @Test
    fun `single result returns that result`() {
        aggregator.addResult(createResult("walking", 0.9f))

        val result = aggregator.aggregate()
        assertEquals("walking", result.label)
        assertEquals(1, result.sampleCount)
    }

    @Test
    fun `majority vote selects most common label`() {
        val config = DnaConfig(
            historySize = 5,
            strategy = AggregationStrategy.MAJORITY_VOTE
        )
        val agg = DnaAggregator(config)

        agg.addResult(createResult("walking", 0.8f))
        agg.addResult(createResult("walking", 0.7f))
        agg.addResult(createResult("sitting", 0.9f))

        val result = agg.aggregate()
        assertEquals("walking", result.label)
        assertEquals(AggregationStrategy.MAJORITY_VOTE, result.strategy)
    }

    @Test
    fun `stability detected after threshold reached`() {
        assertFalse(aggregator.isStable.value)

        aggregator.addResult(createResult("walking", 0.8f))
        assertFalse(aggregator.isStable.value)

        aggregator.addResult(createResult("walking", 0.7f))
        assertFalse(aggregator.isStable.value)

        aggregator.addResult(createResult("walking", 0.9f))
        assertTrue(aggregator.isStable.value)
    }

    @Test
    fun `stability resets on label change`() {
        aggregator.addResult(createResult("walking", 0.8f))
        aggregator.addResult(createResult("walking", 0.7f))
        aggregator.addResult(createResult("walking", 0.9f))
        assertTrue(aggregator.isStable.value)

        aggregator.addResult(createResult("sitting", 0.8f))
        assertFalse(aggregator.isStable.value)
    }

    @Test
    fun `getStableLabel returns null when not stable`() {
        aggregator.addResult(createResult("walking", 0.8f))
        assertNull(aggregator.getStableLabel())
    }

    @Test
    fun `getStableLabel returns label when stable`() {
        aggregator.addResult(createResult("walking", 0.8f))
        aggregator.addResult(createResult("walking", 0.7f))
        aggregator.addResult(createResult("walking", 0.9f))

        assertEquals("walking", aggregator.getStableLabel())
    }

    @Test
    fun `reset clears all state`() {
        aggregator.addResult(createResult("walking", 0.8f))
        aggregator.addResult(createResult("walking", 0.7f))
        aggregator.addResult(createResult("walking", 0.9f))

        aggregator.reset()

        assertEquals(0, aggregator.getHistorySize())
        assertFalse(aggregator.isStable.value)
        assertNull(aggregator.aggregatedResult.value)
    }

    @Test
    fun `hasEnoughData respects minimum samples`() {
        assertFalse(aggregator.hasEnoughData())

        aggregator.addResult(createResult("walking", 0.8f))
        assertFalse(aggregator.hasEnoughData())

        aggregator.addResult(createResult("walking", 0.7f))
        assertTrue(aggregator.hasEnoughData())
    }

    @Test
    fun `history respects size limit`() {
        val smallAgg = DnaAggregator(DnaConfig(historySize = 3))

        smallAgg.addResult(createResult("a", 0.8f))
        smallAgg.addResult(createResult("b", 0.8f))
        smallAgg.addResult(createResult("c", 0.8f))
        assertEquals(3, smallAgg.getHistorySize())

        smallAgg.addResult(createResult("d", 0.8f))
        assertEquals(3, smallAgg.getHistorySize()) // Still 3, oldest removed
    }
}

class AggregationStrategyTest {

    @Test
    fun `all strategies defined`() {
        assertEquals(4, AggregationStrategy.values().size)
    }

    @Test
    fun `strategies have expected names`() {
        assertNotNull(AggregationStrategy.MAJORITY_VOTE)
        assertNotNull(AggregationStrategy.WEIGHTED_AVERAGE)
        assertNotNull(AggregationStrategy.RECENT_WEIGHTED)
        assertNotNull(AggregationStrategy.CONFIDENCE_THRESHOLD)
    }
}

class DnaConfigTest {

    @Test
    fun `default config has sensible values`() {
        val config = DnaConfig()

        assertEquals(10, config.historySize)
        assertEquals(AggregationStrategy.RECENT_WEIGHTED, config.strategy)
        assertEquals(0.6f, config.confidenceThreshold, 0.001f)
        assertEquals(3, config.stabilityThreshold)
        assertEquals(3, config.minSamplesForAggregation)
        assertEquals(0.8f, config.recencyDecay, 0.001f)
    }

    @Test
    fun `custom config values work`() {
        val config = DnaConfig(
            historySize = 20,
            strategy = AggregationStrategy.MAJORITY_VOTE,
            confidenceThreshold = 0.8f,
            stabilityThreshold = 5,
            minSamplesForAggregation = 5,
            recencyDecay = 0.9f
        )

        assertEquals(20, config.historySize)
        assertEquals(AggregationStrategy.MAJORITY_VOTE, config.strategy)
        assertEquals(0.8f, config.confidenceThreshold, 0.001f)
        assertEquals(5, config.stabilityThreshold)
    }
}

class AggregatedResultTest {

    @Test
    fun `empty result is invalid`() {
        val empty = AggregatedResult.empty()
        assertFalse(empty.isValid())
        assertEquals("unknown", empty.label)
        assertEquals(0, empty.sampleCount)
    }

    @Test
    fun `valid result has positive sample count and confidence`() {
        val result = AggregatedResult(
            label = "walking",
            confidence = 0.8f,
            strategy = AggregationStrategy.MAJORITY_VOTE,
            distribution = mapOf("walking" to 0.8f),
            sampleCount = 5,
            timestamp = System.currentTimeMillis()
        )
        assertTrue(result.isValid())
    }

    @Test
    fun `result with zero confidence is invalid`() {
        val result = AggregatedResult(
            label = "walking",
            confidence = 0f,
            strategy = AggregationStrategy.MAJORITY_VOTE,
            distribution = emptyMap(),
            sampleCount = 5,
            timestamp = System.currentTimeMillis()
        )
        assertFalse(result.isValid())
    }
}
