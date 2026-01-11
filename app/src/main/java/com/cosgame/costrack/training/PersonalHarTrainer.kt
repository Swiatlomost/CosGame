package com.cosgame.costrack.training

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * On-device trainer for personal HAR model.
 * Uses a simple feedforward neural network trained with gradient descent.
 */
class PersonalHarTrainer(private val context: Context) {

    private val repository = TrainingRepository.getInstance(context)

    // Network architecture
    private val inputSize = 24  // 6 axes * 4 features (mean, std, min, max)
    private val hiddenSize1 = 32
    private val hiddenSize2 = 16
    private val outputSize = 5  // 5 activity classes

    // Normalization parameters
    private var featureMeans = FloatArray(inputSize)
    private var featureStds = FloatArray(inputSize)

    // Network weights
    private var weights1 = Array(inputSize) { FloatArray(hiddenSize1) }
    private var bias1 = FloatArray(hiddenSize1)
    private var weights2 = Array(hiddenSize1) { FloatArray(hiddenSize2) }
    private var bias2 = FloatArray(hiddenSize2)
    private var weights3 = Array(hiddenSize2) { FloatArray(outputSize) }
    private var bias3 = FloatArray(outputSize)

    /**
     * Train result with metrics.
     */
    data class TrainResult(
        val success: Boolean,
        val accuracy: Float,
        val trainingSamples: Int,
        val validationSamples: Int,
        val epochs: Int,
        val error: String? = null
    )

    /**
     * Train the personal model on collected data.
     */
    suspend fun train(
        epochs: Int = 100,
        learningRate: Float = 0.01f,
        validationSplit: Float = 0.2f,
        onProgress: (Float, Float) -> Unit = { _, _ -> }
    ): TrainResult = withContext(Dispatchers.Default) {
        try {
            // Load all samples
            val allSamples = repository.getAllSamples()
            if (allSamples.size < 100) {
                return@withContext TrainResult(
                    success = false,
                    accuracy = 0f,
                    trainingSamples = 0,
                    validationSamples = 0,
                    epochs = 0,
                    error = "Not enough samples. Need at least 100, have ${allSamples.size}"
                )
            }

            // Group samples by activity and create windows
            val windows = createWindows(allSamples, windowSize = 50, stepSize = 25)
            if (windows.size < 50) {
                return@withContext TrainResult(
                    success = false,
                    accuracy = 0f,
                    trainingSamples = 0,
                    validationSamples = 0,
                    epochs = 0,
                    error = "Not enough windows. Need at least 50, have ${windows.size}"
                )
            }

            // Extract features from windows
            val features = windows.map { (samples, label) ->
                extractFeatures(samples) to label
            }

            // Shuffle and split into train/validation
            val shuffled = features.shuffled(Random(42))
            val splitIndex = (shuffled.size * (1 - validationSplit)).toInt()
            val trainData = shuffled.take(splitIndex)
            val valData = shuffled.drop(splitIndex)

            // Calculate normalization parameters from training data
            calculateNormalization(trainData.map { it.first })

            // Normalize all data
            val trainNorm = trainData.map { (feat, label) -> normalize(feat) to label }
            val valNorm = valData.map { (feat, label) -> normalize(feat) to label }

            // Initialize weights
            initializeWeights()

            // Training loop
            var bestAccuracy = 0f
            for (epoch in 0 until epochs) {
                var totalLoss = 0f
                val trainShuffled = trainNorm.shuffled()

                for ((input, label) in trainShuffled) {
                    val loss = trainStep(input, label, learningRate)
                    totalLoss += loss
                }

                // Evaluate on validation set
                val accuracy = evaluate(valNorm)
                if (accuracy > bestAccuracy) {
                    bestAccuracy = accuracy
                }

                onProgress(epoch.toFloat() / epochs, accuracy)
            }

            // Save the model
            saveModel()

            TrainResult(
                success = true,
                accuracy = bestAccuracy,
                trainingSamples = trainData.size,
                validationSamples = valData.size,
                epochs = epochs
            )
        } catch (e: Exception) {
            TrainResult(
                success = false,
                accuracy = 0f,
                trainingSamples = 0,
                validationSamples = 0,
                epochs = 0,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Create sliding windows from samples.
     */
    private fun createWindows(
        samples: List<TrainingSample>,
        windowSize: Int,
        stepSize: Int
    ): List<Pair<List<TrainingSample>, Int>> {
        val windows = mutableListOf<Pair<List<TrainingSample>, Int>>()

        // Group by activity type
        val grouped = samples.groupBy { it.activityType }

        for ((activityType, activitySamples) in grouped) {
            val sorted = activitySamples.sortedBy { it.timestamp }
            var i = 0
            while (i + windowSize <= sorted.size) {
                val window = sorted.subList(i, i + windowSize)
                windows.add(window to activityType.ordinal)
                i += stepSize
            }
        }

        return windows
    }

    /**
     * Extract features from a window of samples.
     * Returns 24 features: mean, std, min, max for each of 6 axes.
     */
    private fun extractFeatures(samples: List<TrainingSample>): FloatArray {
        val features = FloatArray(inputSize)

        val axes = listOf(
            samples.map { it.accX },
            samples.map { it.accY },
            samples.map { it.accZ },
            samples.map { it.gyroX },
            samples.map { it.gyroY },
            samples.map { it.gyroZ }
        )

        for ((i, axis) in axes.withIndex()) {
            val mean = axis.average().toFloat()
            val std = sqrt(axis.map { (it - mean) * (it - mean) }.average()).toFloat()
            val min = axis.minOrNull() ?: 0f
            val max = axis.maxOrNull() ?: 0f

            features[i * 4 + 0] = mean
            features[i * 4 + 1] = if (std.isNaN() || std == 0f) 1f else std
            features[i * 4 + 2] = min
            features[i * 4 + 3] = max
        }

        return features
    }

    /**
     * Calculate mean and std for normalization.
     */
    private fun calculateNormalization(data: List<FloatArray>) {
        for (i in 0 until inputSize) {
            val values = data.map { it[i] }
            featureMeans[i] = values.average().toFloat()
            val variance = values.map { (it - featureMeans[i]) * (it - featureMeans[i]) }.average()
            featureStds[i] = sqrt(variance).toFloat()
            if (featureStds[i] == 0f || featureStds[i].isNaN()) {
                featureStds[i] = 1f
            }
        }
    }

    /**
     * Normalize input features.
     */
    private fun normalize(features: FloatArray): FloatArray {
        return FloatArray(inputSize) { i ->
            (features[i] - featureMeans[i]) / featureStds[i]
        }
    }

    /**
     * Initialize weights with Xavier initialization.
     */
    private fun initializeWeights() {
        fun xavierInit(fanIn: Int, fanOut: Int): Float {
            val limit = sqrt(6.0 / (fanIn + fanOut))
            return (Random.nextFloat() * 2 * limit - limit).toFloat()
        }

        for (i in 0 until inputSize) {
            for (j in 0 until hiddenSize1) {
                weights1[i][j] = xavierInit(inputSize, hiddenSize1)
            }
        }
        bias1 = FloatArray(hiddenSize1) { 0f }

        for (i in 0 until hiddenSize1) {
            for (j in 0 until hiddenSize2) {
                weights2[i][j] = xavierInit(hiddenSize1, hiddenSize2)
            }
        }
        bias2 = FloatArray(hiddenSize2) { 0f }

        for (i in 0 until hiddenSize2) {
            for (j in 0 until outputSize) {
                weights3[i][j] = xavierInit(hiddenSize2, outputSize)
            }
        }
        bias3 = FloatArray(outputSize) { 0f }
    }

    /**
     * Forward pass through the network.
     */
    private fun forward(input: FloatArray): Triple<FloatArray, FloatArray, FloatArray> {
        // Layer 1: input -> hidden1 (ReLU)
        val hidden1 = FloatArray(hiddenSize1)
        for (j in 0 until hiddenSize1) {
            var sum = bias1[j]
            for (i in 0 until inputSize) {
                sum += input[i] * weights1[i][j]
            }
            hidden1[j] = relu(sum)
        }

        // Layer 2: hidden1 -> hidden2 (ReLU)
        val hidden2 = FloatArray(hiddenSize2)
        for (j in 0 until hiddenSize2) {
            var sum = bias2[j]
            for (i in 0 until hiddenSize1) {
                sum += hidden1[i] * weights2[i][j]
            }
            hidden2[j] = relu(sum)
        }

        // Layer 3: hidden2 -> output (Softmax)
        val output = FloatArray(outputSize)
        for (j in 0 until outputSize) {
            var sum = bias3[j]
            for (i in 0 until hiddenSize2) {
                sum += hidden2[i] * weights3[i][j]
            }
            output[j] = sum
        }

        // Apply softmax
        val softmaxOutput = softmax(output)

        return Triple(hidden1, hidden2, softmaxOutput)
    }

    /**
     * Single training step with backpropagation.
     */
    private fun trainStep(input: FloatArray, label: Int, lr: Float): Float {
        // Forward pass
        val (hidden1, hidden2, output) = forward(input)

        // Compute loss (cross-entropy)
        val loss = -kotlin.math.ln(output[label].coerceIn(1e-7f, 1f))

        // Backpropagation
        // Output layer gradients
        val dOutput = output.copyOf()
        dOutput[label] -= 1f

        // Hidden2 -> Output gradients
        val dHidden2 = FloatArray(hiddenSize2)
        for (i in 0 until hiddenSize2) {
            for (j in 0 until outputSize) {
                dHidden2[i] += dOutput[j] * weights3[i][j]
                weights3[i][j] -= lr * dOutput[j] * hidden2[i]
            }
        }
        for (j in 0 until outputSize) {
            bias3[j] -= lr * dOutput[j]
        }

        // Apply ReLU derivative
        for (i in 0 until hiddenSize2) {
            if (hidden2[i] <= 0) dHidden2[i] = 0f
        }

        // Hidden1 -> Hidden2 gradients
        val dHidden1 = FloatArray(hiddenSize1)
        for (i in 0 until hiddenSize1) {
            for (j in 0 until hiddenSize2) {
                dHidden1[i] += dHidden2[j] * weights2[i][j]
                weights2[i][j] -= lr * dHidden2[j] * hidden1[i]
            }
        }
        for (j in 0 until hiddenSize2) {
            bias2[j] -= lr * dHidden2[j]
        }

        // Apply ReLU derivative
        for (i in 0 until hiddenSize1) {
            if (hidden1[i] <= 0) dHidden1[i] = 0f
        }

        // Input -> Hidden1 gradients
        for (i in 0 until inputSize) {
            for (j in 0 until hiddenSize1) {
                weights1[i][j] -= lr * dHidden1[j] * input[i]
            }
        }
        for (j in 0 until hiddenSize1) {
            bias1[j] -= lr * dHidden1[j]
        }

        return loss
    }

    /**
     * Evaluate accuracy on a dataset.
     */
    private fun evaluate(data: List<Pair<FloatArray, Int>>): Float {
        var correct = 0
        for ((input, label) in data) {
            val (_, _, output) = forward(input)
            val predicted = output.indices.maxByOrNull { output[it] } ?: 0
            if (predicted == label) correct++
        }
        return correct.toFloat() / data.size
    }

    /**
     * Predict activity from raw sensor window.
     */
    fun predict(samples: List<TrainingSample>): Pair<ActivityType, Float>? {
        if (samples.size < 50) return null

        val features = extractFeatures(samples.takeLast(50))
        val normalized = normalize(features)
        val (_, _, output) = forward(normalized)

        val predictedIndex = output.indices.maxByOrNull { output[it] } ?: 0
        val confidence = output[predictedIndex]

        return ActivityType.fromOrdinal(predictedIndex) to confidence
    }

    /**
     * Predict from pre-extracted features.
     */
    fun predictFromFeatures(features: FloatArray): Pair<Int, FloatArray> {
        val normalized = normalize(features)
        val (_, _, output) = forward(normalized)
        val predictedIndex = output.indices.maxByOrNull { output[it] } ?: 0
        return predictedIndex to output
    }

    // Activation functions
    private fun relu(x: Float): Float = if (x > 0) x else 0f

    private fun softmax(x: FloatArray): FloatArray {
        val max = x.maxOrNull() ?: 0f
        val exp = x.map { exp((it - max).toDouble()).toFloat() }
        val sum = exp.sum()
        return exp.map { it / sum }.toFloatArray()
    }

    /**
     * Save model to file.
     */
    private fun saveModel() {
        val file = File(context.filesDir, MODEL_FILENAME)
        file.outputStream().buffered().use { out ->
            // Write normalization parameters
            featureMeans.forEach { out.write(it.toRawBits().toByteArray()) }
            featureStds.forEach { out.write(it.toRawBits().toByteArray()) }

            // Write weights
            weights1.forEach { row -> row.forEach { out.write(it.toRawBits().toByteArray()) } }
            bias1.forEach { out.write(it.toRawBits().toByteArray()) }
            weights2.forEach { row -> row.forEach { out.write(it.toRawBits().toByteArray()) } }
            bias2.forEach { out.write(it.toRawBits().toByteArray()) }
            weights3.forEach { row -> row.forEach { out.write(it.toRawBits().toByteArray()) } }
            bias3.forEach { out.write(it.toRawBits().toByteArray()) }
        }
    }

    /**
     * Load model from file.
     */
    fun loadModel(): Boolean {
        val file = File(context.filesDir, MODEL_FILENAME)
        if (!file.exists()) return false

        return try {
            file.inputStream().buffered().use { inp ->
                val buffer = ByteArray(4)

                fun readFloat(): Float {
                    inp.read(buffer)
                    return Float.fromBits(buffer.toInt())
                }

                // Read normalization parameters
                for (i in 0 until inputSize) featureMeans[i] = readFloat()
                for (i in 0 until inputSize) featureStds[i] = readFloat()

                // Read weights
                for (i in 0 until inputSize) {
                    for (j in 0 until hiddenSize1) {
                        weights1[i][j] = readFloat()
                    }
                }
                for (j in 0 until hiddenSize1) bias1[j] = readFloat()

                for (i in 0 until hiddenSize1) {
                    for (j in 0 until hiddenSize2) {
                        weights2[i][j] = readFloat()
                    }
                }
                for (j in 0 until hiddenSize2) bias2[j] = readFloat()

                for (i in 0 until hiddenSize2) {
                    for (j in 0 until outputSize) {
                        weights3[i][j] = readFloat()
                    }
                }
                for (j in 0 until outputSize) bias3[j] = readFloat()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if a trained model exists.
     */
    fun hasTrainedModel(): Boolean {
        return File(context.filesDir, MODEL_FILENAME).exists()
    }

    /**
     * Delete the trained model.
     */
    fun deleteModel() {
        File(context.filesDir, MODEL_FILENAME).delete()
    }

    companion object {
        private const val MODEL_FILENAME = "personal_har_model.bin"
    }
}

// Extension functions for byte conversion
private fun Int.toByteArray(): ByteArray {
    return byteArrayOf(
        (this shr 24).toByte(),
        (this shr 16).toByte(),
        (this shr 8).toByte(),
        this.toByte()
    )
}

private fun ByteArray.toInt(): Int {
    return (this[0].toInt() and 0xFF shl 24) or
           (this[1].toInt() and 0xFF shl 16) or
           (this[2].toInt() and 0xFF shl 8) or
           (this[3].toInt() and 0xFF)
}
