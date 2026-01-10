package com.cosgame.costrack.classifier

import android.content.Context
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
    override val windowSize: Int = 128
) : Classifier {

    override val id: String = "har_classifier"
    override val name: String = "Human Activity Recognition"

    private var interpreter: Interpreter? = null
    private var _isReady = false
    override val isReady: Boolean get() = _isReady

    // Input buffer: [1, windowSize, 6] (accel xyz + gyro xyz)
    private val inputBuffer: ByteBuffer by lazy {
        ByteBuffer.allocateDirect(4 * windowSize * 6).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    // Output buffer: [1, numClasses]
    private val outputBuffer: Array<FloatArray> by lazy {
        Array(1) { FloatArray(HarActivity.standardActivities.size) }
    }

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
        if (!_isReady || interpreter == null) {
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

        // Run inference
        try {
            interpreter?.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            listener?.onClassificationError(id, "Inference failed: ${e.message}")
            return null
        }

        val inferenceTimeMs = (System.nanoTime() - startTime) / 1_000_000

        // Process output
        val probabilities = outputBuffer[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val maxConfidence = probabilities[maxIndex]
        val predictedLabel = labels.getOrElse(maxIndex) { HarActivity.UNKNOWN.label }

        val allProbs = labels.zip(probabilities.toList()).toMap()

        val result = ClassificationResult(
            classifierId = id,
            label = predictedLabel,
            confidence = maxConfidence,
            allProbabilities = allProbs,
            inferenceTimeMs = inferenceTimeMs
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
        if (!_isReady || interpreter == null) {
            return null
        }

        val expectedSize = windowSize * 3
        if (accelData.size != expectedSize || gyroData.size != expectedSize) {
            listener?.onClassificationError(id,
                "Invalid input size. Expected $expectedSize, got accel=${accelData.size}, gyro=${gyroData.size}")
            return null
        }

        val startTime = System.nanoTime()

        // Prepare input: interleave accel and gyro
        inputBuffer.rewind()
        for (i in 0 until windowSize) {
            inputBuffer.putFloat(accelData[i * 3])     // ax
            inputBuffer.putFloat(accelData[i * 3 + 1]) // ay
            inputBuffer.putFloat(accelData[i * 3 + 2]) // az
            inputBuffer.putFloat(gyroData[i * 3])      // gx
            inputBuffer.putFloat(gyroData[i * 3 + 1])  // gy
            inputBuffer.putFloat(gyroData[i * 3 + 2])  // gz
        }

        // Run inference
        try {
            interpreter?.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            listener?.onClassificationError(id, "Inference failed: ${e.message}")
            return null
        }

        val inferenceTimeMs = (System.nanoTime() - startTime) / 1_000_000

        // Process output
        val probabilities = outputBuffer[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val maxConfidence = probabilities[maxIndex]
        val predictedLabel = labels.getOrElse(maxIndex) { HarActivity.UNKNOWN.label }

        val allProbs = labels.zip(probabilities.toList()).toMap()

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

        inputBuffer.rewind()
        for (i in 0 until windowSize) {
            // Accelerometer x, y, z
            inputBuffer.putFloat(accelData[i * 3])
            inputBuffer.putFloat(accelData[i * 3 + 1])
            inputBuffer.putFloat(accelData[i * 3 + 2])
            // Gyroscope x, y, z
            inputBuffer.putFloat(gyroData[i * 3])
            inputBuffer.putFloat(gyroData[i * 3 + 1])
            inputBuffer.putFloat(gyroData[i * 3 + 2])
        }
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
