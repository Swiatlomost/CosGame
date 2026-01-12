package com.cosgame.costrack.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.learn.*
import com.cosgame.costrack.sensor.AccelerometerManager
import com.cosgame.costrack.sensor.GyroscopeManager
import com.cosgame.costrack.sensor.SensorConfig
import com.cosgame.costrack.touch.TouchFeatureExtractor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Test state enum.
 */
enum class CategoryTestState {
    IDLE,
    TESTING,
    PAUSED
}

/**
 * Result from category testing.
 */
data class CategoryPrediction(
    val category: String,
    val confidence: Float,
    val probabilities: Map<String, Float>,
    val sensorType: SensorType,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * UI State for category testing.
 */
data class CategoryTestUiState(
    val testState: CategoryTestState = CategoryTestState.IDLE,
    val hasTouchModel: Boolean = false,
    val hasMovementModel: Boolean = false,
    val touchPrediction: CategoryPrediction? = null,
    val movementPrediction: CategoryPrediction? = null,
    val combinedPrediction: String = "",
    val combinedConfidence: Float = 0f,
    val fusionMethod: FusionMethod = FusionMethod.WEIGHTED_AVERAGE,
    val testCount: Int = 0,
    val correctCount: Int = 0,
    val categories: List<String> = emptyList(),
    val message: String = ""
)

/**
 * ViewModel for testing trained category models in real-time.
 */
class CategoryTestViewModel(application: Application) : AndroidViewModel(application) {

    // Classifiers
    private var touchClassifier: TouchSensorClassifier? = null
    private var accelClassifier: AccelerometerSensorClassifier? = null
    private var metaClassifier: MetaClassifier? = null

    // Sensors
    private val sensorConfig = SensorConfig.DEFAULT
    private val accelerometerManager = AccelerometerManager(application, sensorConfig)
    private val gyroscopeManager = GyroscopeManager(application, sensorConfig)

    private val featureExtractor = TouchFeatureExtractor()

    private val _uiState = MutableStateFlow(CategoryTestUiState())
    val uiState: StateFlow<CategoryTestUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null
    private val accelBuffer = mutableListOf<FloatArray>()
    private val windowSize = 50 // 1 second at 50Hz

    init {
        loadModels()
    }

    private fun loadModels() {
        val context = getApplication<Application>()

        touchClassifier = TouchSensorClassifier(context).takeIf { it.hasTrainedModel() }
        accelClassifier = AccelerometerSensorClassifier(context).takeIf { it.hasTrainedModel() }

        val classifiers = mutableListOf<SensorClassifier>()
        touchClassifier?.let { classifiers.add(it) }
        accelClassifier?.let { classifiers.add(it) }

        if (classifiers.isNotEmpty()) {
            metaClassifier = MetaClassifier(classifiers)
        }

        // Get categories from touch or movement model
        val categories = touchClassifier?.labels()
            ?: accelClassifier?.labels()
            ?: emptyList()

        _uiState.value = _uiState.value.copy(
            hasTouchModel = touchClassifier?.hasTrainedModel() ?: false,
            hasMovementModel = accelClassifier?.hasTrainedModel() ?: false,
            categories = categories
        )
    }

    fun startTesting() {
        if (_uiState.value.testState == CategoryTestState.TESTING) return

        if (!_uiState.value.hasMovementModel) {
            _uiState.value = _uiState.value.copy(
                message = "No trained model available. Train a model first."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            testState = CategoryTestState.TESTING,
            message = "Testing..."
        )

        // Start sensors
        accelerometerManager.start()
        gyroscopeManager.start()

        testJob = viewModelScope.launch {
            accelBuffer.clear()

            while (isActive) {
                // Collect accelerometer data
                val accelReading = accelerometerManager.latestReading.value
                if (accelReading != null) {
                    accelBuffer.add(floatArrayOf(accelReading.x, accelReading.y, accelReading.z))
                }

                // When we have enough data, classify
                if (accelBuffer.size >= windowSize) {
                    classifyMovement()
                    // Keep half for overlap
                    while (accelBuffer.size > windowSize / 2) {
                        accelBuffer.removeAt(0)
                    }
                }

                delay(20) // 50Hz
            }
        }
    }

    private fun classifyMovement() {
        val classifier = accelClassifier ?: return

        // Extract features from buffer
        val features = extractAccelFeatures(accelBuffer.toList())

        val result = classifier.predict(features)

        val prediction = CategoryPrediction(
            category = result.label,
            confidence = result.confidence,
            probabilities = result.probabilities,
            sensorType = SensorType.ACCELEROMETER
        )

        _uiState.value = _uiState.value.copy(
            movementPrediction = prediction,
            combinedPrediction = result.label,
            combinedConfidence = result.confidence,
            testCount = _uiState.value.testCount + 1
        )
    }

    private fun extractAccelFeatures(samples: List<FloatArray>): FloatArray {
        val xValues = samples.map { it[0] }
        val yValues = samples.map { it[1] }
        val zValues = samples.map { it[2] }

        return floatArrayOf(
            // Mean
            xValues.average().toFloat(),
            yValues.average().toFloat(),
            zValues.average().toFloat(),
            // Std
            xValues.std(),
            yValues.std(),
            zValues.std(),
            // Min
            xValues.minOrNull() ?: 0f,
            yValues.minOrNull() ?: 0f,
            zValues.minOrNull() ?: 0f,
            // Max
            xValues.maxOrNull() ?: 0f,
            yValues.maxOrNull() ?: 0f,
            zValues.maxOrNull() ?: 0f,
            // Range
            (xValues.maxOrNull() ?: 0f) - (xValues.minOrNull() ?: 0f),
            (yValues.maxOrNull() ?: 0f) - (yValues.minOrNull() ?: 0f),
            (zValues.maxOrNull() ?: 0f) - (zValues.minOrNull() ?: 0f),
            // Energy
            xValues.map { it * it }.average().toFloat(),
            yValues.map { it * it }.average().toFloat(),
            zValues.map { it * it }.average().toFloat()
        )
    }

    private fun List<Float>.std(): Float {
        val mean = this.average()
        val variance = this.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }

    fun stopTesting() {
        testJob?.cancel()
        testJob = null

        accelerometerManager.stop()
        gyroscopeManager.stop()

        _uiState.value = _uiState.value.copy(
            testState = CategoryTestState.IDLE,
            message = "Testing stopped"
        )
    }

    fun pauseTesting() {
        testJob?.cancel()
        testJob = null

        accelerometerManager.stop()
        gyroscopeManager.stop()

        _uiState.value = _uiState.value.copy(
            testState = CategoryTestState.PAUSED,
            message = "Testing paused"
        )
    }

    fun resumeTesting() {
        startTesting()
    }

    fun setFusionMethod(method: FusionMethod) {
        _uiState.value = _uiState.value.copy(fusionMethod = method)
        metaClassifier?.fusionMethod = method
    }

    fun markCorrect() {
        _uiState.value = _uiState.value.copy(
            correctCount = _uiState.value.correctCount + 1
        )
    }

    fun resetStats() {
        _uiState.value = _uiState.value.copy(
            testCount = 0,
            correctCount = 0
        )
    }

    fun refresh() {
        loadModels()
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = "")
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
        accelerometerManager.stop()
        gyroscopeManager.stop()
    }
}
