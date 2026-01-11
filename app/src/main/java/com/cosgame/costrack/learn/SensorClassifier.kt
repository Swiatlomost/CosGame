package com.cosgame.costrack.learn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Sensor types supported by classifiers.
 */
enum class SensorType(val displayName: String, val featureSize: Int) {
    TOUCH("Touch", 24),
    ACCELEROMETER("Accelerometer", 18),
    GYROSCOPE("Gyroscope", 18);

    companion object {
        fun fromCategory(category: Category): List<SensorType> = buildList {
            if (category.useTouch) add(TOUCH)
            if (category.useAccelerometer) add(ACCELEROMETER)
            if (category.useGyroscope) add(GYROSCOPE)
        }
    }
}

/**
 * Result of a sensor classification.
 */
data class SensorClassificationResult(
    val sensorType: SensorType,
    val predictedClass: Int,
    val label: String,
    val confidence: Float,
    val probabilities: Map<String, Float>,
    val inferenceTimeMs: Long = 0
) {
    fun isHighConfidence(threshold: Float = 0.7f): Boolean = confidence >= threshold
}

/**
 * Combined result from multiple sensor classifiers.
 */
data class CombinedClassificationResult(
    val finalLabel: String,
    val finalConfidence: Float,
    val sensorResults: Map<SensorType, SensorClassificationResult>,
    val fusionMethod: String = "weighted_average"
)

/**
 * Training result for a sensor classifier.
 */
data class SensorTrainingResult(
    val sensorType: SensorType,
    val success: Boolean,
    val message: String,
    val epochs: Int,
    val finalLoss: Float,
    val finalAccuracy: Float,
    val samplesUsed: Int
)

/**
 * Model information for a sensor classifier.
 */
data class SensorModelInfo(
    val sensorType: SensorType,
    val isTrained: Boolean,
    val epochs: Int,
    val classLabels: List<String>,
    val architecture: String,
    val inputSize: Int,
    val modelFilePath: String
)

/**
 * Base neural network classifier for sensor data.
 * Architecture: inputSize -> hidden1 -> hidden2 -> numClasses
 * Supports on-device training with backpropagation.
 */
abstract class SensorClassifier(
    protected val context: Context,
    protected val sensorType: SensorType,
    protected val numClasses: Int = 2
) {
    companion object {
        const val HIDDEN1_SIZE = 32
        const val HIDDEN2_SIZE = 16
        const val DEFAULT_LEARNING_RATE = 0.01f
    }

    // Input size depends on sensor type
    protected val inputSize: Int = sensorType.featureSize

    // Network weights
    protected var weights1: Array<FloatArray> = Array(inputSize) { FloatArray(HIDDEN1_SIZE) }
    protected var bias1: FloatArray = FloatArray(HIDDEN1_SIZE)
    protected var weights2: Array<FloatArray> = Array(HIDDEN1_SIZE) { FloatArray(HIDDEN2_SIZE) }
    protected var bias2: FloatArray = FloatArray(HIDDEN2_SIZE)
    protected var weights3: Array<FloatArray> = Array(HIDDEN2_SIZE) { FloatArray(numClasses) }
    protected var bias3: FloatArray = FloatArray(numClasses)

    // Class labels
    protected var classLabels: List<String> = emptyList()

    // Training state
    protected var isTrained: Boolean = false
    protected var trainingEpochs: Int = 0

    // Model file name
    protected val modelFileName: String
        get() = "${sensorType.name.lowercase()}_classifier.bin"

    init {
        initializeWeights()
        loadModel()
    }

    /**
     * Initialize weights with Xavier initialization.
     */
    protected fun initializeWeights() {
        val scale1 = sqrt(2.0 / (inputSize + HIDDEN1_SIZE)).toFloat()
        val scale2 = sqrt(2.0 / (HIDDEN1_SIZE + HIDDEN2_SIZE)).toFloat()
        val scale3 = sqrt(2.0 / (HIDDEN2_SIZE + numClasses)).toFloat()

        for (i in 0 until inputSize) {
            for (j in 0 until HIDDEN1_SIZE) {
                weights1[i][j] = (Math.random().toFloat() - 0.5f) * scale1
            }
        }

        for (i in 0 until HIDDEN1_SIZE) {
            for (j in 0 until HIDDEN2_SIZE) {
                weights2[i][j] = (Math.random().toFloat() - 0.5f) * scale2
            }
        }

        for (i in 0 until HIDDEN2_SIZE) {
            for (j in 0 until numClasses) {
                weights3[i][j] = (Math.random().toFloat() - 0.5f) * scale3
            }
        }
    }

    /**
     * ReLU activation function.
     */
    protected fun relu(x: Float): Float = maxOf(0f, x)

    /**
     * ReLU derivative for backpropagation.
     */
    protected fun reluDerivative(x: Float): Float = if (x > 0f) 1f else 0f

    /**
     * Softmax activation for output layer.
     */
    protected fun softmax(x: FloatArray): FloatArray {
        val maxVal = x.maxOrNull() ?: 0f
        val expVals = x.map { exp((it - maxVal).toDouble()).toFloat() }.toFloatArray()
        val sum = expVals.sum()
        return expVals.map { it / sum }.toFloatArray()
    }

    /**
     * Forward pass through the network.
     */
    protected fun forward(input: FloatArray): ForwardResult {
        require(input.size == inputSize) { "Input size must be $inputSize, got ${input.size}" }

        // Layer 1
        val hidden1PreAct = FloatArray(HIDDEN1_SIZE)
        val hidden1 = FloatArray(HIDDEN1_SIZE)
        for (j in 0 until HIDDEN1_SIZE) {
            var sum = bias1[j]
            for (i in 0 until inputSize) {
                sum += input[i] * weights1[i][j]
            }
            hidden1PreAct[j] = sum
            hidden1[j] = relu(sum)
        }

        // Layer 2
        val hidden2PreAct = FloatArray(HIDDEN2_SIZE)
        val hidden2 = FloatArray(HIDDEN2_SIZE)
        for (j in 0 until HIDDEN2_SIZE) {
            var sum = bias2[j]
            for (i in 0 until HIDDEN1_SIZE) {
                sum += hidden1[i] * weights2[i][j]
            }
            hidden2PreAct[j] = sum
            hidden2[j] = relu(sum)
        }

        // Output layer
        val output = FloatArray(numClasses)
        for (j in 0 until numClasses) {
            var sum = bias3[j]
            for (i in 0 until HIDDEN2_SIZE) {
                sum += hidden2[i] * weights3[i][j]
            }
            output[j] = sum
        }

        val probs = softmax(output)
        return ForwardResult(input, hidden1, hidden2, probs, hidden1PreAct, hidden2PreAct)
    }

    /**
     * Predict class for input features.
     */
    fun predict(features: FloatArray): SensorClassificationResult {
        val startTime = System.nanoTime()
        val result = forward(features)
        val predictedClass = result.output.indices.maxByOrNull { result.output[it] } ?: 0
        val confidence = result.output[predictedClass]
        val inferenceTimeMs = (System.nanoTime() - startTime) / 1_000_000

        val label = if (classLabels.isNotEmpty() && predictedClass < classLabels.size) {
            classLabels[predictedClass]
        } else {
            "class_$predictedClass"
        }

        val probabilities = result.output.indices.associate { i ->
            val l = if (i < classLabels.size) classLabels[i] else "class_$i"
            l to result.output[i]
        }

        return SensorClassificationResult(
            sensorType = sensorType,
            predictedClass = predictedClass,
            label = label,
            confidence = confidence,
            probabilities = probabilities,
            inferenceTimeMs = inferenceTimeMs
        )
    }

    /**
     * Train on a batch of samples with backpropagation.
     * Returns average loss.
     */
    fun trainBatch(
        samples: List<FloatArray>,
        labels: List<Int>,
        learningRate: Float = DEFAULT_LEARNING_RATE
    ): Float {
        require(samples.size == labels.size) { "Samples and labels must have same size" }

        var totalLoss = 0f

        for ((input, label) in samples.zip(labels)) {
            val result = forward(input)
            totalLoss += -ln(result.output[label] + 1e-7f)

            // Backpropagation
            // Output layer gradients
            val outputGrad = result.output.clone()
            outputGrad[label] -= 1f

            // Hidden2 gradients
            val hidden2Grad = FloatArray(HIDDEN2_SIZE)
            for (i in 0 until HIDDEN2_SIZE) {
                var grad = 0f
                for (j in 0 until numClasses) {
                    grad += outputGrad[j] * weights3[i][j]
                }
                hidden2Grad[i] = grad * reluDerivative(result.hidden2PreAct[i])
            }

            // Hidden1 gradients
            val hidden1Grad = FloatArray(HIDDEN1_SIZE)
            for (i in 0 until HIDDEN1_SIZE) {
                var grad = 0f
                for (j in 0 until HIDDEN2_SIZE) {
                    grad += hidden2Grad[j] * weights2[i][j]
                }
                hidden1Grad[i] = grad * reluDerivative(result.hidden1PreAct[i])
            }

            // Update weights (SGD)
            // Layer 3
            for (i in 0 until HIDDEN2_SIZE) {
                for (j in 0 until numClasses) {
                    weights3[i][j] -= learningRate * outputGrad[j] * result.hidden2[i]
                }
            }
            for (j in 0 until numClasses) {
                bias3[j] -= learningRate * outputGrad[j]
            }

            // Layer 2
            for (i in 0 until HIDDEN1_SIZE) {
                for (j in 0 until HIDDEN2_SIZE) {
                    weights2[i][j] -= learningRate * hidden2Grad[j] * result.hidden1[i]
                }
            }
            for (j in 0 until HIDDEN2_SIZE) {
                bias2[j] -= learningRate * hidden2Grad[j]
            }

            // Layer 1
            for (i in 0 until inputSize) {
                for (j in 0 until HIDDEN1_SIZE) {
                    weights1[i][j] -= learningRate * hidden1Grad[j] * result.input[i]
                }
            }
            for (j in 0 until HIDDEN1_SIZE) {
                bias1[j] -= learningRate * hidden1Grad[j]
            }
        }

        isTrained = true
        trainingEpochs++
        return totalLoss / samples.size
    }

    /**
     * Train the classifier on prepared data.
     */
    suspend fun train(
        samples: List<FloatArray>,
        labels: List<Int>,
        labelNames: List<String>,
        epochs: Int = 10,
        learningRate: Float = DEFAULT_LEARNING_RATE,
        onProgress: ((epoch: Int, loss: Float, accuracy: Float) -> Unit)? = null
    ): SensorTrainingResult = withContext(Dispatchers.Default) {
        if (samples.isEmpty()) {
            return@withContext SensorTrainingResult(
                sensorType = sensorType,
                success = false,
                message = "No training data",
                epochs = 0,
                finalLoss = 0f,
                finalAccuracy = 0f,
                samplesUsed = 0
            )
        }

        if (labelNames.size < 2) {
            return@withContext SensorTrainingResult(
                sensorType = sensorType,
                success = false,
                message = "Need at least 2 different labels",
                epochs = 0,
                finalLoss = 0f,
                finalAccuracy = 0f,
                samplesUsed = 0
            )
        }

        classLabels = labelNames

        // Reinitialize weights for fresh training
        initializeWeights()

        var finalLoss = 0f
        var finalAccuracy = 0f

        for (epoch in 0 until epochs) {
            // Shuffle data
            val indices = samples.indices.shuffled()
            val shuffledSamples = indices.map { samples[it] }
            val shuffledLabels = indices.map { labels[it] }

            val loss = trainBatch(shuffledSamples, shuffledLabels, learningRate)

            // Calculate accuracy
            var correct = 0
            for (i in samples.indices) {
                val pred = predict(samples[i])
                if (pred.predictedClass == labels[i]) correct++
            }
            val accuracy = correct.toFloat() / samples.size

            finalLoss = loss
            finalAccuracy = accuracy

            onProgress?.invoke(epoch + 1, loss, accuracy)
        }

        saveModel()

        SensorTrainingResult(
            sensorType = sensorType,
            success = true,
            message = "Trained ${sensorType.displayName} classifier on ${samples.size} samples",
            epochs = epochs,
            finalLoss = finalLoss,
            finalAccuracy = finalAccuracy,
            samplesUsed = samples.size
        )
    }

    /**
     * Save model to internal storage.
     */
    fun saveModel() {
        try {
            val file = File(context.filesDir, modelFileName)
            file.bufferedWriter().use { writer ->
                // Write metadata
                writer.write("$numClasses\n")
                writer.write("${classLabels.joinToString(",")}\n")
                writer.write("$trainingEpochs\n")
                writer.write("$inputSize\n")

                // Write weights1
                for (i in 0 until inputSize) {
                    writer.write(weights1[i].joinToString(",") + "\n")
                }

                // Write bias1
                writer.write(bias1.joinToString(",") + "\n")

                // Write weights2
                for (i in 0 until HIDDEN1_SIZE) {
                    writer.write(weights2[i].joinToString(",") + "\n")
                }

                // Write bias2
                writer.write(bias2.joinToString(",") + "\n")

                // Write weights3
                for (i in 0 until HIDDEN2_SIZE) {
                    writer.write(weights3[i].joinToString(",") + "\n")
                }

                // Write bias3
                writer.write(bias3.joinToString(",") + "\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Load model from internal storage.
     */
    protected fun loadModel() {
        try {
            val file = File(context.filesDir, modelFileName)
            if (!file.exists()) return

            file.bufferedReader().use { reader ->
                // Read metadata
                val savedClasses = reader.readLine()?.toIntOrNull() ?: return
                val labelsLine = reader.readLine() ?: ""
                classLabels = if (labelsLine.isNotEmpty()) labelsLine.split(",") else emptyList()
                trainingEpochs = reader.readLine()?.toIntOrNull() ?: 0
                val savedInputSize = reader.readLine()?.toIntOrNull() ?: inputSize

                if (savedInputSize != inputSize) {
                    // Model was trained with different input size, skip loading
                    return
                }

                // Read weights1
                for (i in 0 until inputSize) {
                    val line = reader.readLine() ?: return
                    val values = line.split(",").mapNotNull { it.toFloatOrNull() }
                    if (values.size == HIDDEN1_SIZE) {
                        weights1[i] = values.toFloatArray()
                    }
                }

                // Read bias1
                val bias1Line = reader.readLine() ?: return
                val bias1Values = bias1Line.split(",").mapNotNull { it.toFloatOrNull() }
                if (bias1Values.size == HIDDEN1_SIZE) {
                    bias1 = bias1Values.toFloatArray()
                }

                // Read weights2
                for (i in 0 until HIDDEN1_SIZE) {
                    val line = reader.readLine() ?: return
                    val values = line.split(",").mapNotNull { it.toFloatOrNull() }
                    if (values.size == HIDDEN2_SIZE) {
                        weights2[i] = values.toFloatArray()
                    }
                }

                // Read bias2
                val bias2Line = reader.readLine() ?: return
                val bias2Values = bias2Line.split(",").mapNotNull { it.toFloatOrNull() }
                if (bias2Values.size == HIDDEN2_SIZE) {
                    bias2 = bias2Values.toFloatArray()
                }

                // Read weights3
                for (i in 0 until HIDDEN2_SIZE) {
                    val line = reader.readLine() ?: return
                    val values = line.split(",").mapNotNull { it.toFloatOrNull() }
                    if (values.size >= numClasses) {
                        weights3[i] = values.take(numClasses).toFloatArray()
                    }
                }

                // Read bias3
                val bias3Line = reader.readLine() ?: return
                val bias3Values = bias3Line.split(",").mapNotNull { it.toFloatOrNull() }
                if (bias3Values.size >= numClasses) {
                    bias3 = bias3Values.take(numClasses).toFloatArray()
                }

                isTrained = trainingEpochs > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Reset model to initial state.
     */
    fun reset() {
        initializeWeights()
        classLabels = emptyList()
        isTrained = false
        trainingEpochs = 0
        val file = File(context.filesDir, modelFileName)
        if (file.exists()) file.delete()
    }

    /**
     * Get model information.
     */
    fun getModelInfo(): SensorModelInfo {
        return SensorModelInfo(
            sensorType = sensorType,
            isTrained = isTrained,
            epochs = trainingEpochs,
            classLabels = classLabels,
            architecture = "$inputSize -> $HIDDEN1_SIZE -> $HIDDEN2_SIZE -> $numClasses",
            inputSize = inputSize,
            modelFilePath = File(context.filesDir, modelFileName).absolutePath
        )
    }

    /**
     * Check if model is trained.
     */
    fun isModelTrained(): Boolean = isTrained

    /**
     * Get the labels for trained classes.
     */
    fun labels(): List<String> = classLabels

    /**
     * Abstract method to extract features from raw sensor data.
     * Implemented by concrete sensor classifiers.
     */
    abstract fun extractFeatures(rawData: Any): FloatArray?

    /**
     * Internal forward result holder.
     */
    protected data class ForwardResult(
        val input: FloatArray,
        val hidden1: FloatArray,
        val hidden2: FloatArray,
        val output: FloatArray,
        val hidden1PreAct: FloatArray,
        val hidden2PreAct: FloatArray
    )
}

/**
 * Touch sensor classifier.
 * Expects 24 features from TouchFeatureExtractor.
 */
class TouchSensorClassifier(
    context: Context,
    numClasses: Int = 2
) : SensorClassifier(context, SensorType.TOUCH, numClasses) {

    override fun extractFeatures(rawData: Any): FloatArray? {
        // rawData should be List<TouchEvent> or pre-extracted features
        return when (rawData) {
            is FloatArray -> if (rawData.size == inputSize) rawData else null
            else -> null
        }
    }
}

/**
 * Accelerometer sensor classifier.
 * Expects 18 features (statistical features from 3-axis data).
 */
class AccelerometerSensorClassifier(
    context: Context,
    numClasses: Int = 2
) : SensorClassifier(context, SensorType.ACCELEROMETER, numClasses) {

    override fun extractFeatures(rawData: Any): FloatArray? {
        return when (rawData) {
            is FloatArray -> if (rawData.size == inputSize) rawData else null
            is List<*> -> extractFromAccelData(rawData.filterIsInstance<FloatArray>())
            else -> null
        }
    }

    /**
     * Extract statistical features from raw accelerometer samples.
     * Input: List of [x, y, z] samples
     * Output: 18 features (mean, std, min, max, range, energy for each axis)
     */
    private fun extractFromAccelData(samples: List<FloatArray>): FloatArray? {
        if (samples.isEmpty()) return null

        val xValues = samples.map { it.getOrElse(0) { 0f } }
        val yValues = samples.map { it.getOrElse(1) { 0f } }
        val zValues = samples.map { it.getOrElse(2) { 0f } }

        return floatArrayOf(
            // X-axis features
            xValues.average().toFloat(),
            standardDeviation(xValues),
            xValues.minOrNull() ?: 0f,
            xValues.maxOrNull() ?: 0f,
            (xValues.maxOrNull() ?: 0f) - (xValues.minOrNull() ?: 0f),
            xValues.map { it * it }.sum() / xValues.size,

            // Y-axis features
            yValues.average().toFloat(),
            standardDeviation(yValues),
            yValues.minOrNull() ?: 0f,
            yValues.maxOrNull() ?: 0f,
            (yValues.maxOrNull() ?: 0f) - (yValues.minOrNull() ?: 0f),
            yValues.map { it * it }.sum() / yValues.size,

            // Z-axis features
            zValues.average().toFloat(),
            standardDeviation(zValues),
            zValues.minOrNull() ?: 0f,
            zValues.maxOrNull() ?: 0f,
            (zValues.maxOrNull() ?: 0f) - (zValues.minOrNull() ?: 0f),
            zValues.map { it * it }.sum() / zValues.size
        )
    }

    private fun standardDeviation(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance.toDouble()).toFloat()
    }
}

/**
 * Gyroscope sensor classifier.
 * Expects 18 features (statistical features from 3-axis data).
 */
class GyroscopeSensorClassifier(
    context: Context,
    numClasses: Int = 2
) : SensorClassifier(context, SensorType.GYROSCOPE, numClasses) {

    override fun extractFeatures(rawData: Any): FloatArray? {
        return when (rawData) {
            is FloatArray -> if (rawData.size == inputSize) rawData else null
            is List<*> -> extractFromGyroData(rawData.filterIsInstance<FloatArray>())
            else -> null
        }
    }

    /**
     * Extract statistical features from raw gyroscope samples.
     * Input: List of [x, y, z] samples
     * Output: 18 features (mean, std, min, max, range, energy for each axis)
     */
    private fun extractFromGyroData(samples: List<FloatArray>): FloatArray? {
        if (samples.isEmpty()) return null

        val xValues = samples.map { it.getOrElse(0) { 0f } }
        val yValues = samples.map { it.getOrElse(1) { 0f } }
        val zValues = samples.map { it.getOrElse(2) { 0f } }

        return floatArrayOf(
            // X-axis features
            xValues.average().toFloat(),
            standardDeviation(xValues),
            xValues.minOrNull() ?: 0f,
            xValues.maxOrNull() ?: 0f,
            (xValues.maxOrNull() ?: 0f) - (xValues.minOrNull() ?: 0f),
            xValues.map { it * it }.sum() / xValues.size,

            // Y-axis features
            yValues.average().toFloat(),
            standardDeviation(yValues),
            yValues.minOrNull() ?: 0f,
            yValues.maxOrNull() ?: 0f,
            (yValues.maxOrNull() ?: 0f) - (yValues.minOrNull() ?: 0f),
            yValues.map { it * it }.sum() / yValues.size,

            // Z-axis features
            zValues.average().toFloat(),
            standardDeviation(zValues),
            zValues.minOrNull() ?: 0f,
            zValues.maxOrNull() ?: 0f,
            (zValues.maxOrNull() ?: 0f) - (zValues.minOrNull() ?: 0f),
            zValues.map { it * it }.sum() / zValues.size
        )
    }

    private fun standardDeviation(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance.toDouble()).toFloat()
    }
}
