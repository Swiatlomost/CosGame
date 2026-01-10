package com.cosgame.costrack.aggregator

import com.cosgame.costrack.classifier.ClassificationResult
import com.cosgame.costrack.data.RingBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DNA (Dynamic Neural Aggregator) combines multiple classifier outputs
 * to produce a stable, consensus-based prediction.
 *
 * Features:
 * - Temporal smoothing using a sliding window of recent results
 * - Multiple aggregation strategies
 * - Confidence-weighted voting
 * - Stability detection to avoid flickering predictions
 */
class DnaAggregator(
    private val config: DnaConfig = DnaConfig()
) {
    private val resultHistory = RingBuffer<ClassificationResult>(config.historySize)
    private val labelHistory = RingBuffer<String>(config.historySize)

    private val _aggregatedResult = MutableStateFlow<AggregatedResult?>(null)
    val aggregatedResult: StateFlow<AggregatedResult?> = _aggregatedResult.asStateFlow()

    private val _isStable = MutableStateFlow(false)
    val isStable: StateFlow<Boolean> = _isStable.asStateFlow()

    private var lastStableLabel: String? = null
    private var stabilityCounter = 0

    /**
     * Add a new classification result to the aggregator.
     */
    fun addResult(result: ClassificationResult) {
        resultHistory.push(result)
        labelHistory.push(result.label)

        val aggregated = aggregate()
        _aggregatedResult.value = aggregated

        // Update stability
        updateStability(aggregated.label)
    }

    /**
     * Aggregate results using the configured strategy.
     */
    fun aggregate(): AggregatedResult {
        if (resultHistory.isEmpty) {
            return AggregatedResult.empty()
        }

        return when (config.strategy) {
            AggregationStrategy.MAJORITY_VOTE -> aggregateMajorityVote()
            AggregationStrategy.WEIGHTED_AVERAGE -> aggregateWeightedAverage()
            AggregationStrategy.RECENT_WEIGHTED -> aggregateRecentWeighted()
            AggregationStrategy.CONFIDENCE_THRESHOLD -> aggregateConfidenceThreshold()
        }
    }

    /**
     * Simple majority voting - most frequent label wins.
     */
    private fun aggregateMajorityVote(): AggregatedResult {
        val labels = labelHistory.toList()
        val counts = labels.groupingBy { it }.eachCount()
        val winner = counts.maxByOrNull { it.value } ?: return AggregatedResult.empty()

        val confidence = winner.value.toFloat() / labels.size
        val distribution = counts.mapValues { it.value.toFloat() / labels.size }

        return AggregatedResult(
            label = winner.key,
            confidence = confidence,
            strategy = AggregationStrategy.MAJORITY_VOTE,
            distribution = distribution,
            sampleCount = labels.size,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Weighted average using confidence scores.
     */
    private fun aggregateWeightedAverage(): AggregatedResult {
        val results = resultHistory.toList()
        if (results.isEmpty()) return AggregatedResult.empty()

        // Accumulate weighted probabilities for each label
        val weightedProbs = mutableMapOf<String, Float>()
        var totalWeight = 0f

        results.forEach { result ->
            val weight = result.confidence
            totalWeight += weight
            result.allProbabilities.forEach { (label, prob) ->
                weightedProbs[label] = (weightedProbs[label] ?: 0f) + prob * weight
            }
        }

        // Normalize
        if (totalWeight > 0) {
            weightedProbs.keys.forEach { key ->
                weightedProbs[key] = weightedProbs[key]!! / totalWeight
            }
        }

        val winner = weightedProbs.maxByOrNull { it.value }
        return AggregatedResult(
            label = winner?.key ?: "unknown",
            confidence = winner?.value ?: 0f,
            strategy = AggregationStrategy.WEIGHTED_AVERAGE,
            distribution = weightedProbs,
            sampleCount = results.size,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Recent results weighted more heavily (exponential decay).
     */
    private fun aggregateRecentWeighted(): AggregatedResult {
        val results = resultHistory.toList()
        if (results.isEmpty()) return AggregatedResult.empty()

        val weightedProbs = mutableMapOf<String, Float>()
        var totalWeight = 0f

        results.forEachIndexed { index, result ->
            // Exponential weight: more recent = higher weight
            val recencyWeight = Math.pow(config.recencyDecay.toDouble(),
                (results.size - 1 - index).toDouble()).toFloat()
            val weight = result.confidence * recencyWeight
            totalWeight += weight

            result.allProbabilities.forEach { (label, prob) ->
                weightedProbs[label] = (weightedProbs[label] ?: 0f) + prob * weight
            }
        }

        // Normalize
        if (totalWeight > 0) {
            weightedProbs.keys.forEach { key ->
                weightedProbs[key] = weightedProbs[key]!! / totalWeight
            }
        }

        val winner = weightedProbs.maxByOrNull { it.value }
        return AggregatedResult(
            label = winner?.key ?: "unknown",
            confidence = winner?.value ?: 0f,
            strategy = AggregationStrategy.RECENT_WEIGHTED,
            distribution = weightedProbs,
            sampleCount = results.size,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Only consider results above confidence threshold.
     */
    private fun aggregateConfidenceThreshold(): AggregatedResult {
        val results = resultHistory.toList()
            .filter { it.confidence >= config.confidenceThreshold }

        if (results.isEmpty()) {
            // Fall back to most recent if nothing passes threshold
            val latest = resultHistory.toList().lastOrNull()
            return AggregatedResult(
                label = latest?.label ?: "unknown",
                confidence = latest?.confidence ?: 0f,
                strategy = AggregationStrategy.CONFIDENCE_THRESHOLD,
                distribution = latest?.allProbabilities ?: emptyMap(),
                sampleCount = 0,
                timestamp = System.currentTimeMillis()
            )
        }

        val counts = results.groupingBy { it.label }.eachCount()
        val winner = counts.maxByOrNull { it.value }!!

        val avgConfidence = results.filter { it.label == winner.key }
            .map { it.confidence }.average().toFloat()

        return AggregatedResult(
            label = winner.key,
            confidence = avgConfidence,
            strategy = AggregationStrategy.CONFIDENCE_THRESHOLD,
            distribution = counts.mapValues { it.value.toFloat() / results.size },
            sampleCount = results.size,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun updateStability(currentLabel: String) {
        if (currentLabel == lastStableLabel) {
            stabilityCounter++
        } else {
            stabilityCounter = 1
            lastStableLabel = currentLabel
        }

        _isStable.value = stabilityCounter >= config.stabilityThreshold
    }

    /**
     * Get the current consensus label, or null if not stable.
     */
    fun getStableLabel(): String? {
        return if (_isStable.value) lastStableLabel else null
    }

    /**
     * Clear all history and reset state.
     */
    fun reset() {
        resultHistory.clear()
        labelHistory.clear()
        _aggregatedResult.value = null
        _isStable.value = false
        lastStableLabel = null
        stabilityCounter = 0
    }

    /**
     * Get current history size.
     */
    fun getHistorySize(): Int = resultHistory.size

    /**
     * Check if aggregator has enough data.
     */
    fun hasEnoughData(): Boolean = resultHistory.size >= config.minSamplesForAggregation
}

/**
 * Configuration for DNA Aggregator.
 */
data class DnaConfig(
    val historySize: Int = 10,
    val strategy: AggregationStrategy = AggregationStrategy.RECENT_WEIGHTED,
    val confidenceThreshold: Float = 0.6f,
    val stabilityThreshold: Int = 3,
    val minSamplesForAggregation: Int = 3,
    val recencyDecay: Float = 0.8f // For RECENT_WEIGHTED strategy
)

/**
 * Aggregation strategies.
 */
enum class AggregationStrategy {
    /** Simple majority voting */
    MAJORITY_VOTE,

    /** Confidence-weighted average */
    WEIGHTED_AVERAGE,

    /** Recent results weighted more heavily */
    RECENT_WEIGHTED,

    /** Only consider high-confidence results */
    CONFIDENCE_THRESHOLD
}

/**
 * Result of aggregation.
 */
data class AggregatedResult(
    val label: String,
    val confidence: Float,
    val strategy: AggregationStrategy,
    val distribution: Map<String, Float>,
    val sampleCount: Int,
    val timestamp: Long
) {
    companion object {
        fun empty() = AggregatedResult(
            label = "unknown",
            confidence = 0f,
            strategy = AggregationStrategy.MAJORITY_VOTE,
            distribution = emptyMap(),
            sampleCount = 0,
            timestamp = System.currentTimeMillis()
        )
    }

    fun isValid(): Boolean = sampleCount > 0 && confidence > 0f
}
