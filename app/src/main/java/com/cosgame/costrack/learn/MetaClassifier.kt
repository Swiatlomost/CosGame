package com.cosgame.costrack.learn

import android.content.Context

/**
 * Fusion method for combining classifier results.
 */
enum class FusionMethod(val displayName: String) {
    WEIGHTED_AVERAGE("Weighted Average"),
    MAX_CONFIDENCE("Maximum Confidence"),
    VOTING("Majority Voting");
}

/**
 * Meta-classifier that combines results from multiple sensor-specific classifiers.
 * Uses late fusion to combine predictions.
 */
class MetaClassifier(
    private val context: Context
) {
    // Individual sensor classifiers
    private var touchClassifier: TouchSensorClassifier? = null
    private var accelClassifier: AccelerometerSensorClassifier? = null
    private var gyroClassifier: GyroscopeSensorClassifier? = null

    // Fusion settings
    var fusionMethod: FusionMethod = FusionMethod.WEIGHTED_AVERAGE
        private set

    // Sensor weights (for weighted average)
    private val sensorWeights = mutableMapOf(
        SensorType.TOUCH to 1.0f,
        SensorType.ACCELEROMETER to 1.0f,
        SensorType.GYROSCOPE to 1.0f
    )

    /**
     * Initialize a specific sensor classifier.
     */
    fun initializeSensor(sensorType: SensorType, numClasses: Int = 2) {
        when (sensorType) {
            SensorType.TOUCH -> {
                touchClassifier = TouchSensorClassifier(context, numClasses)
            }
            SensorType.ACCELEROMETER -> {
                accelClassifier = AccelerometerSensorClassifier(context, numClasses)
            }
            SensorType.GYROSCOPE -> {
                gyroClassifier = GyroscopeSensorClassifier(context, numClasses)
            }
        }
    }

    /**
     * Get a specific sensor classifier.
     */
    fun getClassifier(sensorType: SensorType): SensorClassifier? {
        return when (sensorType) {
            SensorType.TOUCH -> touchClassifier
            SensorType.ACCELEROMETER -> accelClassifier
            SensorType.GYROSCOPE -> gyroClassifier
        }
    }

    /**
     * Check if a specific sensor classifier is ready.
     */
    fun isSensorReady(sensorType: SensorType): Boolean {
        return getClassifier(sensorType)?.isModelTrained() ?: false
    }

    /**
     * Get all available (trained) sensor types.
     */
    fun getAvailableSensors(): List<SensorType> {
        return SensorType.values().filter { isSensorReady(it) }
    }

    /**
     * Set weight for a specific sensor.
     */
    fun setSensorWeight(sensorType: SensorType, weight: Float) {
        sensorWeights[sensorType] = weight.coerceIn(0f, 2f)
    }

    /**
     * Set fusion method.
     */
    fun setFusionMethod(method: FusionMethod) {
        fusionMethod = method
    }

    /**
     * Classify using all available sensors and fuse results.
     * @param sensorData Map of sensor type to feature data
     */
    fun classify(sensorData: Map<SensorType, FloatArray>): CombinedClassificationResult? {
        val sensorResults = mutableMapOf<SensorType, SensorClassificationResult>()

        // Get predictions from each available sensor
        for ((sensorType, features) in sensorData) {
            val classifier = getClassifier(sensorType)
            if (classifier != null && classifier.isModelTrained()) {
                val result = classifier.predict(features)
                sensorResults[sensorType] = result
            }
        }

        if (sensorResults.isEmpty()) {
            return null
        }

        // Fuse results based on selected method
        return when (fusionMethod) {
            FusionMethod.WEIGHTED_AVERAGE -> fuseWeightedAverage(sensorResults)
            FusionMethod.MAX_CONFIDENCE -> fuseMaxConfidence(sensorResults)
            FusionMethod.VOTING -> fuseVoting(sensorResults)
        }
    }

    /**
     * Weighted average fusion: combine probabilities weighted by sensor confidence and weight.
     */
    private fun fuseWeightedAverage(
        results: Map<SensorType, SensorClassificationResult>
    ): CombinedClassificationResult {
        // Get all unique labels
        val allLabels = results.values.flatMap { it.probabilities.keys }.distinct()

        // Calculate weighted probabilities
        val fusedProbabilities = mutableMapOf<String, Float>()
        var totalWeight = 0f

        for ((sensorType, result) in results) {
            val weight = sensorWeights[sensorType] ?: 1f
            val adjustedWeight = weight * result.confidence // Weight by confidence
            totalWeight += adjustedWeight

            for (label in allLabels) {
                val prob = result.probabilities[label] ?: 0f
                fusedProbabilities[label] = (fusedProbabilities[label] ?: 0f) + prob * adjustedWeight
            }
        }

        // Normalize
        if (totalWeight > 0) {
            for (label in fusedProbabilities.keys) {
                fusedProbabilities[label] = fusedProbabilities[label]!! / totalWeight
            }
        }

        val bestLabel = fusedProbabilities.maxByOrNull { it.value }?.key ?: "unknown"
        val bestConfidence = fusedProbabilities[bestLabel] ?: 0f

        return CombinedClassificationResult(
            finalLabel = bestLabel,
            finalConfidence = bestConfidence,
            sensorResults = results,
            fusionMethod = "weighted_average"
        )
    }

    /**
     * Maximum confidence fusion: select the prediction with highest confidence.
     */
    private fun fuseMaxConfidence(
        results: Map<SensorType, SensorClassificationResult>
    ): CombinedClassificationResult {
        val bestResult = results.values.maxByOrNull { it.confidence }!!

        return CombinedClassificationResult(
            finalLabel = bestResult.label,
            finalConfidence = bestResult.confidence,
            sensorResults = results,
            fusionMethod = "max_confidence"
        )
    }

    /**
     * Voting fusion: each sensor votes for its predicted class.
     */
    private fun fuseVoting(
        results: Map<SensorType, SensorClassificationResult>
    ): CombinedClassificationResult {
        // Count votes (weighted by sensor weight)
        val votes = mutableMapOf<String, Float>()

        for ((sensorType, result) in results) {
            val weight = sensorWeights[sensorType] ?: 1f
            votes[result.label] = (votes[result.label] ?: 0f) + weight
        }

        val bestLabel = votes.maxByOrNull { it.value }?.key ?: "unknown"
        val confidence = votes[bestLabel]!! / votes.values.sum()

        return CombinedClassificationResult(
            finalLabel = bestLabel,
            finalConfidence = confidence,
            sensorResults = results,
            fusionMethod = "voting"
        )
    }

    /**
     * Get model info for all initialized classifiers.
     */
    fun getAllModelInfo(): Map<SensorType, SensorModelInfo> {
        return buildMap {
            touchClassifier?.let { put(SensorType.TOUCH, it.getModelInfo()) }
            accelClassifier?.let { put(SensorType.ACCELEROMETER, it.getModelInfo()) }
            gyroClassifier?.let { put(SensorType.GYROSCOPE, it.getModelInfo()) }
        }
    }

    /**
     * Reset all classifiers.
     */
    fun resetAll() {
        touchClassifier?.reset()
        accelClassifier?.reset()
        gyroClassifier?.reset()
    }

    /**
     * Reset specific sensor classifier.
     */
    fun resetSensor(sensorType: SensorType) {
        getClassifier(sensorType)?.reset()
    }
}

/**
 * Factory for creating sensor classifiers based on category settings.
 */
object SensorClassifierFactory {

    /**
     * Create classifiers for a category's enabled sensors.
     */
    fun createForCategory(context: Context, category: Category, numClasses: Int = 2): MetaClassifier {
        val meta = MetaClassifier(context)

        if (category.useTouch) {
            meta.initializeSensor(SensorType.TOUCH, numClasses)
        }
        if (category.useAccelerometer) {
            meta.initializeSensor(SensorType.ACCELEROMETER, numClasses)
        }
        if (category.useGyroscope) {
            meta.initializeSensor(SensorType.GYROSCOPE, numClasses)
        }

        return meta
    }

    /**
     * Create all sensor classifiers.
     */
    fun createAll(context: Context, numClasses: Int = 2): MetaClassifier {
        val meta = MetaClassifier(context)
        SensorType.values().forEach { meta.initializeSensor(it, numClasses) }
        return meta
    }
}
