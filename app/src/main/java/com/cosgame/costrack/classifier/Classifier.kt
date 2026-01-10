package com.cosgame.costrack.classifier

/**
 * Base interface for all classifiers in the app.
 */
interface Classifier {
    /** Unique identifier for this classifier */
    val id: String

    /** Human-readable name */
    val name: String

    /** Whether the classifier is ready to make predictions */
    val isReady: Boolean

    /** Required input window size (number of samples) */
    val windowSize: Int

    /** Initialize the classifier (load model, etc.) */
    fun initialize(): Boolean

    /** Release resources */
    fun close()
}

/**
 * Result of a classification operation.
 */
data class ClassificationResult(
    val classifierId: String,
    val label: String,
    val confidence: Float,
    val allProbabilities: Map<String, Float>,
    val timestamp: Long = System.currentTimeMillis(),
    val inferenceTimeMs: Long = 0
) {
    /** Check if this is a high-confidence prediction */
    fun isHighConfidence(threshold: Float = 0.7f): Boolean = confidence >= threshold

    /** Get top N predictions sorted by confidence */
    fun topN(n: Int): List<Pair<String, Float>> {
        return allProbabilities.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }
    }
}

/**
 * Listener for classification events.
 */
interface ClassificationListener {
    fun onClassificationResult(result: ClassificationResult)
    fun onClassificationError(classifierId: String, error: String)
}

/**
 * Human Activity Recognition labels.
 * Standard HAR dataset activities.
 */
enum class HarActivity(val label: String, val description: String) {
    WALKING("walking", "Walking at normal pace"),
    WALKING_UPSTAIRS("walking_upstairs", "Walking up stairs"),
    WALKING_DOWNSTAIRS("walking_downstairs", "Walking down stairs"),
    SITTING("sitting", "Sitting down"),
    STANDING("standing", "Standing still"),
    LAYING("laying", "Laying down"),
    UNKNOWN("unknown", "Unknown activity");

    companion object {
        private val labelMap = values().associateBy { it.label }

        fun fromLabel(label: String): HarActivity {
            return labelMap[label.lowercase()] ?: UNKNOWN
        }

        fun fromIndex(index: Int): HarActivity {
            return values().getOrElse(index) { UNKNOWN }
        }

        /** Standard HAR activities (excluding UNKNOWN) */
        val standardActivities = values().filter { it != UNKNOWN }
    }
}
