package com.cosgame.costrack.classifier

import android.content.Context
import android.util.Log
import com.cosgame.costrack.data.SensorRingBuffer
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Human Activity Recognition classifier using TensorFlow Lite.
 *
 * Expected model input: [1, windowSize, 6] - accelerometer + gyroscope data
 * Expected model output: [1, 6] - probabilities for each activity
 *
 * Input format: [ax0, ay0, az0, gx0, gy0, gz0, ax1, ay1, az1, gx1, gy1, gz1, ...]
 */
class HarClassifier(
    private val context: Context,
    private val modelFileName: String = "har_model.tflite",
    private val requestedWindowSize: Int = 128
) : Classifier {

    override val id: String = "har_classifier"
    override val name: String = "Human Activity Recognition"

    private var interpreter: Interpreter? = null
    private var _isReady = false
    override val isReady: Boolean get() = _isReady

    // Detected model dimensions
    private var modelWindowSize: Int = requestedWindowSize
    private var modelFeatures: Int = 6
    private var modelNumClasses: Int = 6
    override val windowSize: Int get() = modelWindowSize

    // Dynamically allocated buffers
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: Array<FloatArray>? = null

    private val labels = HarActivity.standardActivities.map { it.label }

    private var listener: ClassificationListener? = null

    /**
     * Initialize the classifier by loading the TFLite model.
     */
    override fun initialize(): Boolean {
        return try {
            val model = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(model, options)

            // Detect model input/output shapes
            val inputTensor = interpreter!!.getInputTensor(0)
            val outputTensor = interpreter!!.getOutputTensor(0)

            val inputShape = inputTensor.shape()
            val outputShape = outputTensor.shape()

            // Parse input shape - could be [1, window, features] or [1, features]
            when (inputShape.size) {
                3 -> {
                    modelWindowSize = inputShape[1]
                    modelFeatures = inputShape[2]
                }
                2 -> {
                    modelWindowSize = 1
                    modelFeatures = inputShape[1]
                }
                else -> {
                    modelWindowSize = requestedWindowSize
                    modelFeatures = 6
                }
            }

            // Parse output shape
            modelNumClasses = outputShape.last()

            Log.i(TAG, "Model loaded: input=${inputShape.contentToString()}, " +
                  "output=${outputShape.contentToString()}, " +
                  "windowSize=$modelWindowSize, features=$modelFeatures, classes=$modelNumClasses")

            // Allocate buffers
            val inputSize = inputShape.fold(1) { acc, dim -> acc * dim }
            inputBuffer = ByteBuffer.allocateDirect(4 * inputSize).apply {
                order(ByteOrder.nativeOrder())
            }
            outputBuffer = Array(1) { FloatArray(modelNumClasses) }

            _isReady = true
            true
        } catch (e: Exception) {
            _isReady = false
            listener?.onClassificationError(id, "Failed to load model: ${e.message}")
            false
        }
    }

    /**
     * Set listener for classification events.
     */
    fun setListener(listener: ClassificationListener?) {
        this.listener = listener
    }

    /**
     * Classify activity from accelerometer and gyroscope ring buffers.
     *
     * @param accelBuffer Accelerometer data ring buffer
     * @param gyroBuffer Gyroscope data ring buffer
     * @return Classification result or null if not ready
     */
    fun classify(
        accelBuffer: SensorRingBuffer,
        gyroBuffer: SensorRingBuffer
    ): ClassificationResult? {
        if (!_isReady || interpreter == null || inputBuffer == null || outputBuffer == null) {
            listener?.onClassificationError(id, "Classifier not initialized")
            return null
        }

        if (!accelBuffer.isFull || !gyroBuffer.isFull) {
            listener?.onClassificationError(id, "Buffers not full")
            return null
        }

        val startTime = System.nanoTime()

        // Prepare input data
        prepareInputBuffer(accelBuffer, gyroBuffer)

        // Rewind buffer for reading by interpreter
        inputBuffer!!.rewind()

        // Debug: read back first few values to verify buffer content
        val v0 = inputBuffer!!.getFloat()
        val v1 = inputBuffer!!.getFloat()
        val v2 = inputBuffer!!.getFloat()
        val debugInfo = "in[%.2f,%.2f,%.2f]".format(v0, v1, v2)
        inputBuffer!!.rewind()

        // Run inference
        try {
            interpreter?.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            listener?.onClassificationError(id, "Inference failed: ${e.message}")
            return null
        }

        val inferenceTimeMs = (System.nanoTime() - startTime) / 1_000_000

        // Process output - apply softmax to convert logits to probabilities
        val rawOutput = outputBuffer!![0].clone()
        val probabilities = softmax(rawOutput)
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val maxConfidence = probabilities[maxIndex]
        val predictedLabel = if (maxIndex < labels.size) labels[maxIndex] else "Activity_$maxIndex"

        val allProbs = probabilities.indices.associate { i ->
            val label = if (i < labels.size) labels[i] else "Activity_$i"
            label to probabilities[i]
        }

        // Log raw output for debugging
        Log.d(TAG, "RAW output: [${rawOutput.map { "%.3f".format(it) }.joinToString(",")}]")
        Log.d(TAG, "HAR Result: $predictedLabel (${(maxConfidence * 100).toInt()}%) " +
              "probs=[${probabilities.map { "%.2f".format(it) }.joinToString(",")}]")

        val result = ClassificationResult(
            classifierId = id,
            label = predictedLabel,
            confidence = maxConfidence,
            allProbabilities = allProbs,
            inferenceTimeMs = inferenceTimeMs,
            rawOutput = rawOutput,
            debugInfo = debugInfo
        )

        listener?.onClassificationResult(result)
        return result
    }

    /**
     * Classify from raw float arrays.
     *
     * @param accelData Accelerometer data [x0,y0,z0,x1,y1,z1,...]
     * @param gyroData Gyroscope data [x0,y0,z0,x1,y1,z1,...]
     */
    fun classifyRaw(accelData: FloatArray, gyroData: FloatArray): ClassificationResult? {
        if (!_isReady || interpreter == null || inputBuffer == null || outputBuffer == null) {
            return null
        }

        val startTime = System.nanoTime()

        // Prepare input: interleave accel and gyro
        inputBuffer!!.rewind()
        val samplesToUse = minOf(modelWindowSize, accelData.size / 3, gyroData.size / 3)
        for (i in 0 until samplesToUse) {
            if (modelFeatures >= 3) {
                inputBuffer!!.putFloat(accelData.getOrElse(i * 3) { 0f })     // ax
                inputBuffer!!.putFloat(accelData.getOrElse(i * 3 + 1) { 0f }) // ay
                inputBuffer!!.putFloat(accelData.getOrElse(i * 3 + 2) { 0f }) // az
            }
            if (modelFeatures >= 6) {
                inputBuffer!!.putFloat(gyroData.getOrElse(i * 3) { 0f })      // gx
                inputBuffer!!.putFloat(gyroData.getOrElse(i * 3 + 1) { 0f })  // gy
                inputBuffer!!.putFloat(gyroData.getOrElse(i * 3 + 2) { 0f })  // gz
            }
        }

        // Rewind buffer for reading by interpreter
        inputBuffer!!.rewind()

        // Run inference
        try {
            interpreter?.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            listener?.onClassificationError(id, "Inference failed: ${e.message}")
            return null
        }

        val inferenceTimeMs = (System.nanoTime() - startTime) / 1_000_000

        // Process output - apply softmax
        val rawOutput = outputBuffer!![0]
        val probabilities = softmax(rawOutput)
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val maxConfidence = probabilities[maxIndex]
        val predictedLabel = if (maxIndex < labels.size) labels[maxIndex] else "Activity_$maxIndex"

        val allProbs = probabilities.indices.associate { i ->
            val label = if (i < labels.size) labels[i] else "Activity_$i"
            label to probabilities[i]
        }

        return ClassificationResult(
            classifierId = id,
            label = predictedLabel,
            confidence = maxConfidence,
            allProbabilities = allProbs,
            inferenceTimeMs = inferenceTimeMs
        )
    }

    private fun prepareInputBuffer(
        accelBuffer: SensorRingBuffer,
        gyroBuffer: SensorRingBuffer
    ) {
        val accelData = accelBuffer.toInterleavedArray()
        val gyroData = gyroBuffer.toInterleavedArray()

        inputBuffer!!.rewind()
        val samplesToUse = minOf(modelWindowSize, accelData.size / 3, gyroData.size / 3)
        for (i in 0 until samplesToUse) {
            if (modelFeatures >= 3) {
                inputBuffer!!.putFloat(normalizeAccel(accelData.getOrElse(i * 3) { 0f }))
                inputBuffer!!.putFloat(normalizeAccel(accelData.getOrElse(i * 3 + 1) { 0f }))
                inputBuffer!!.putFloat(normalizeAccel(accelData.getOrElse(i * 3 + 2) { 0f }))
            }
            if (modelFeatures >= 6) {
                inputBuffer!!.putFloat(normalizeGyro(gyroData.getOrElse(i * 3) { 0f }))
                inputBuffer!!.putFloat(normalizeGyro(gyroData.getOrElse(i * 3 + 1) { 0f }))
                inputBuffer!!.putFloat(normalizeGyro(gyroData.getOrElse(i * 3 + 2) { 0f }))
            }
        }
    }

    private fun normalizeAccel(value: Float): Float {
        // Normalize accelerometer (m/s²) to [-1, 1] range
        // UCI HAR data was normalized per-window. Using 1g (9.8) as base gives better spread.
        // Typical walking: 0.5-1.5g, sitting: ~1g (gravity only)
        return (value / ACCEL_MAX).coerceIn(-1f, 1f)
    }

    private fun normalizeGyro(value: Float): Float {
        // Normalize gyroscope (rad/s) to [-1, 1] range
        // Typical walking gyro: 0-3 rad/s, running: up to 5 rad/s
        return (value / GYRO_MAX).coerceIn(-1f, 1f)
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val expValues = logits.map { kotlin.math.exp((it - maxLogit).toDouble()).toFloat() }
        val sumExp = expValues.sum()
        return expValues.map { it / sumExp }.toFloatArray()
    }

    companion object {
        private const val TAG = "HarClassifier"
        // Adjusted normalization: UCI HAR normalized to [-1,1] with typical activity ranges
        // Using smaller divisors gives more spread in the normalized values
        private const val ACCEL_MAX = 10f  // m/s² (~1g) - gives better spread for typical activities
        private const val GYRO_MAX = 5f    // rad/s - typical walking/movement range
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        _isReady = false
    }
}

/**
 * Configuration for HAR classifier.
 */
data class HarClassifierConfig(
    val modelFileName: String = "har_model.tflite",
    val windowSize: Int = 128,
    val confidenceThreshold: Float = 0.6f,
    val inferenceIntervalMs: Long = 500 // Run inference every 500ms
)
