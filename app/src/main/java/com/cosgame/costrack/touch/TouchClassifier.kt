package com.cosgame.costrack.touch

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.exp

/**
 * Simple neural network classifier for touch patterns.
 * Architecture: 24 features -> 32 hidden -> 16 hidden -> N classes
 * Supports on-device training.
 */
class TouchClassifier(
    private val context: Context,
    private val numClasses: Int = 2
) {
    companion object {
        const val INPUT_SIZE = 24
        const val HIDDEN1_SIZE = 32
        const val HIDDEN2_SIZE = 16
        const val MODEL_FILE = "touch_classifier.bin"
        const val LEARNING_RATE = 0.01f
    }

    // Network weights
    private var weights1: Array<FloatArray> = Array(INPUT_SIZE) { FloatArray(HIDDEN1_SIZE) }
    private var bias1: FloatArray = FloatArray(HIDDEN1_SIZE)
    private var weights2: Array<FloatArray> = Array(HIDDEN1_SIZE) { FloatArray(HIDDEN2_SIZE) }
    private var bias2: FloatArray = FloatArray(HIDDEN2_SIZE)
    private var weights3: Array<FloatArray> = Array(HIDDEN2_SIZE) { FloatArray(numClasses) }
    private var bias3: FloatArray = FloatArray(numClasses)

    // Class labels
    private var classLabels: List<String> = emptyList()

    // Training state
    private var isTrained: Boolean = false
    private var trainingEpochs: Int = 0

    init {
        initializeWeights()
        loadModel()
    }

    /**
     * Initialize weights with small random values (Xavier initialization).
     */
    private fun initializeWeights() {
        val scale1 = kotlin.math.sqrt(2.0 / (INPUT_SIZE + HIDDEN1_SIZE)).toFloat()
        val scale2 = kotlin.math.sqrt(2.0 / (HIDDEN1_SIZE + HIDDEN2_SIZE)).toFloat()
        val scale3 = kotlin.math.sqrt(2.0 / (HIDDEN2_SIZE + numClasses)).toFloat()

        for (i in 0 until INPUT_SIZE) {
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
    private fun relu(x: Float): Float = maxOf(0f, x)

    /**
     * ReLU derivative for backpropagation.
     */
    private fun reluDerivative(x: Float): Float = if (x > 0f) 1f else 0f

    /**
     * Softmax activation for output layer.
     */
    private fun softmax(x: FloatArray): FloatArray {
        val maxVal = x.maxOrNull() ?: 0f
        val expVals = x.map { exp((it - maxVal).toDouble()).toFloat() }.toFloatArray()
        val sum = expVals.sum()
        return expVals.map { it / sum }.toFloatArray()
    }

    /**
     * Forward pass through the network.
     * Returns (hidden1, hidden2, output, hidden1PreAct, hidden2PreAct)
     */
    private fun forward(input: FloatArray): ForwardResult {
        require(input.size == INPUT_SIZE) { "Input size must be $INPUT_SIZE" }

        // Layer 1
        val hidden1PreAct = FloatArray(HIDDEN1_SIZE)
        val hidden1 = FloatArray(HIDDEN1_SIZE)
        for (j in 0 until HIDDEN1_SIZE) {
            var sum = bias1[j]
            for (i in 0 until INPUT_SIZE) {
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
        return ForwardResult(hidden1, hidden2, probs, hidden1PreAct, hidden2PreAct)
    }

    /**
     * Predict class for input features.
     */
    fun predict(features: FloatArray): ClassificationResult {
        val result = forward(features)
        val predictedClass = result.output.indices.maxByOrNull { result.output[it] } ?: 0
        val confidence = result.output[predictedClass]
        val label = if (classLabels.isNotEmpty() && predictedClass < classLabels.size) {
            classLabels[predictedClass]
        } else {
            "class_$predictedClass"
        }

        return ClassificationResult(
            classIndex = predictedClass,
            label = label,
            confidence = confidence,
            probabilities = result.output.toList()
        )
    }

    /**
     * Train on a batch of samples.
     * Returns average loss.
     */
    fun trainBatch(
        samples: List<FloatArray>,
        labels: List<Int>,
        learningRate: Float = LEARNING_RATE
    ): Float {
        require(samples.size == labels.size) { "Samples and labels must have same size" }

        var totalLoss = 0f

        for ((input, label) in samples.zip(labels)) {
            val result = forward(input)
            totalLoss += -kotlin.math.ln(result.output[label] + 1e-7f)

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
            for (i in 0 until INPUT_SIZE) {
                for (j in 0 until HIDDEN1_SIZE) {
                    weights1[i][j] -= learningRate * hidden1Grad[j] * input[i]
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
     * Train on sessions from database.
     */
    suspend fun trainOnSessions(
        sessions: List<TouchSession>,
        epochs: Int = 10,
        onProgress: ((epoch: Int, loss: Float, accuracy: Float) -> Unit)? = null
    ): TrainingResult = withContext(Dispatchers.Default) {
        if (sessions.isEmpty()) {
            return@withContext TrainingResult(false, "No training data", 0, 0f)
        }

        val featureExtractor = TouchFeatureExtractor()
        val labelSet = sessions.mapNotNull { it.label }.distinct().sorted()

        if (labelSet.size < 2) {
            return@withContext TrainingResult(false, "Need at least 2 different labels", 0, 0f)
        }

        classLabels = labelSet

        // Prepare training data
        val samples = mutableListOf<FloatArray>()
        val labels = mutableListOf<Int>()

        for (session in sessions) {
            val sessionLabel = session.label ?: continue
            val labelIndex = labelSet.indexOf(sessionLabel)
            if (labelIndex < 0) continue

            val events = TouchSessionCollector.eventsFromJson(session.touchEventsJson)
            val featureArray = featureExtractor.extractFeatures(events, session.duration)

            if (featureArray.size == INPUT_SIZE) {
                samples.add(featureArray)
                labels.add(labelIndex)
            }
        }

        if (samples.isEmpty()) {
            return@withContext TrainingResult(false, "No valid samples", 0, 0f)
        }

        // Reinitialize weights for fresh training
        initializeWeights()

        var finalLoss = 0f
        var finalAccuracy = 0f

        for (epoch in 0 until epochs) {
            // Shuffle data
            val indices = samples.indices.shuffled()
            val shuffledSamples = indices.map { samples[it] }
            val shuffledLabels = indices.map { labels[it] }

            val loss = trainBatch(shuffledSamples, shuffledLabels)

            // Calculate accuracy
            var correct = 0
            for (i in samples.indices) {
                val pred = predict(samples[i])
                if (pred.classIndex == labels[i]) correct++
            }
            val accuracy = correct.toFloat() / samples.size

            finalLoss = loss
            finalAccuracy = accuracy

            onProgress?.invoke(epoch + 1, loss, accuracy)
        }

        saveModel()

        TrainingResult(
            success = true,
            message = "Trained on ${samples.size} samples with ${labelSet.size} classes",
            epochs = epochs,
            finalAccuracy = finalAccuracy
        )
    }

    /**
     * Save model to internal storage.
     */
    fun saveModel() {
        try {
            val file = File(context.filesDir, MODEL_FILE)
            file.bufferedWriter().use { writer ->
                // Write metadata
                writer.write("$numClasses\n")
                writer.write("${classLabels.joinToString(",")}\n")
                writer.write("$trainingEpochs\n")

                // Write weights1
                for (i in 0 until INPUT_SIZE) {
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
    private fun loadModel() {
        try {
            val file = File(context.filesDir, MODEL_FILE)
            if (!file.exists()) return

            file.bufferedReader().use { reader ->
                // Read metadata
                val savedClasses = reader.readLine()?.toIntOrNull() ?: return
                val labelsLine = reader.readLine() ?: ""
                classLabels = if (labelsLine.isNotEmpty()) labelsLine.split(",") else emptyList()
                trainingEpochs = reader.readLine()?.toIntOrNull() ?: 0

                // Read weights1
                for (i in 0 until INPUT_SIZE) {
                    val line = reader.readLine() ?: return
                    val values = line.split(",").map { it.toFloat() }
                    if (values.size == HIDDEN1_SIZE) {
                        weights1[i] = values.toFloatArray()
                    }
                }

                // Read bias1
                val bias1Line = reader.readLine() ?: return
                val bias1Values = bias1Line.split(",").map { it.toFloat() }
                if (bias1Values.size == HIDDEN1_SIZE) {
                    bias1 = bias1Values.toFloatArray()
                }

                // Read weights2
                for (i in 0 until HIDDEN1_SIZE) {
                    val line = reader.readLine() ?: return
                    val values = line.split(",").map { it.toFloat() }
                    if (values.size == HIDDEN2_SIZE) {
                        weights2[i] = values.toFloatArray()
                    }
                }

                // Read bias2
                val bias2Line = reader.readLine() ?: return
                val bias2Values = bias2Line.split(",").map { it.toFloat() }
                if (bias2Values.size == HIDDEN2_SIZE) {
                    bias2 = bias2Values.toFloatArray()
                }

                // Read weights3
                for (i in 0 until HIDDEN2_SIZE) {
                    val line = reader.readLine() ?: return
                    val values = line.split(",").map { it.toFloat() }
                    if (values.size >= numClasses) {
                        weights3[i] = values.take(numClasses).toFloatArray()
                    }
                }

                // Read bias3
                val bias3Line = reader.readLine() ?: return
                val bias3Values = bias3Line.split(",").map { it.toFloat() }
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
        val file = File(context.filesDir, MODEL_FILE)
        if (file.exists()) file.delete()
    }

    /**
     * Get model info.
     */
    fun getModelInfo(): ModelInfo {
        return ModelInfo(
            isTrained = isTrained,
            epochs = trainingEpochs,
            classLabels = classLabels,
            architecture = "$INPUT_SIZE -> $HIDDEN1_SIZE -> $HIDDEN2_SIZE -> $numClasses"
        )
    }

    /**
     * Check if model is trained.
     */
    fun isModelTrained(): Boolean = isTrained

    // Internal data classes
    private data class ForwardResult(
        val hidden1: FloatArray,
        val hidden2: FloatArray,
        val output: FloatArray,
        val hidden1PreAct: FloatArray,
        val hidden2PreAct: FloatArray
    )
}

/**
 * Result of classification.
 */
data class ClassificationResult(
    val classIndex: Int,
    val label: String,
    val confidence: Float,
    val probabilities: List<Float>
)

/**
 * Result of training.
 */
data class TrainingResult(
    val success: Boolean,
    val message: String,
    val epochs: Int,
    val finalAccuracy: Float
)

/**
 * Model information.
 */
data class ModelInfo(
    val isTrained: Boolean,
    val epochs: Int,
    val classLabels: List<String>,
    val architecture: String
)
