package com.cosgame.costrack.ui.missions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.classifier.HarActivity
import com.cosgame.costrack.classifier.HarClassifier
import com.cosgame.costrack.sensor.AccelerometerManager
import com.cosgame.costrack.sensor.GyroscopeManager
import com.cosgame.costrack.sensor.SensorConfig
import com.cosgame.costrack.training.ActivityType
import com.cosgame.costrack.training.PersonalHarTrainer
import com.cosgame.costrack.training.TrainingRepository
import com.cosgame.costrack.training.TrainingSample
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for testing personal vs generic model.
 */
class TestViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorConfig = SensorConfig.DEFAULT
    private val accelerometerManager = AccelerometerManager(application, sensorConfig)
    private val gyroscopeManager = GyroscopeManager(application, sensorConfig)

    private val personalTrainer = PersonalHarTrainer(application)
    private val genericClassifier = HarClassifier(application)

    private val _uiState = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null
    private val sampleBuffer = mutableListOf<TrainingSample>()

    init {
        checkModelsAvailable()
    }

    private fun checkModelsAvailable() {
        val hasPersonal = personalTrainer.hasTrainedModel()
        val hasGeneric = genericClassifier.initialize()

        if (hasPersonal) {
            personalTrainer.loadModel()
        }

        _uiState.value = _uiState.value.copy(
            hasPersonalModel = hasPersonal,
            hasGenericModel = hasGeneric,
            usePersonalModel = hasPersonal // Default to personal if available
        )
    }

    fun setUsePersonalModel(usePersonal: Boolean) {
        _uiState.value = _uiState.value.copy(usePersonalModel = usePersonal)
    }

    fun startTesting() {
        if (_uiState.value.isRunning) return

        // Start sensors
        accelerometerManager.start()
        gyroscopeManager.start()

        _uiState.value = _uiState.value.copy(
            isRunning = true,
            error = null
        )

        sampleBuffer.clear()

        testJob = viewModelScope.launch {
            while (isActive) {
                collectAndClassify()
                delay(100) // 10 Hz classification
            }
        }
    }

    fun stopTesting() {
        testJob?.cancel()
        testJob = null

        accelerometerManager.stop()
        gyroscopeManager.stop()

        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    private fun collectAndClassify() {
        val accel = accelerometerManager.latestReading.value ?: return
        val gyro = gyroscopeManager.latestReading.value ?: return

        // Add to buffer
        val sample = TrainingSample.create(
            activityType = ActivityType.STANDING, // Placeholder
            accX = accel.x,
            accY = accel.y,
            accZ = accel.z,
            gyroX = gyro.x,
            gyroY = gyro.y,
            gyroZ = gyro.z,
            sessionId = 0
        )
        sampleBuffer.add(sample)

        // Keep only last 50 samples (1 second at 50Hz)
        while (sampleBuffer.size > 50) {
            sampleBuffer.removeAt(0)
        }

        // Need at least 50 samples for classification
        if (sampleBuffer.size < 50) {
            _uiState.value = _uiState.value.copy(
                bufferProgress = sampleBuffer.size / 50f
            )
            return
        }

        _uiState.value = _uiState.value.copy(bufferProgress = 1f)

        // Classify based on selected model
        if (_uiState.value.usePersonalModel && _uiState.value.hasPersonalModel) {
            classifyWithPersonalModel()
        } else if (_uiState.value.hasGenericModel) {
            classifyWithGenericModel()
        }
    }

    private fun classifyWithPersonalModel() {
        val result = personalTrainer.predict(sampleBuffer.toList())
        if (result != null) {
            val (activityType, confidence) = result

            val probabilities = mapOf(
                ActivityType.WALKING to 0f,
                ActivityType.RUNNING to 0f,
                ActivityType.SITTING to 0f,
                ActivityType.STANDING to 0f,
                ActivityType.LAYING to 0f
            ).toMutableMap()

            // We don't have full probabilities from personal model, just confidence
            probabilities[activityType] = confidence

            _uiState.value = _uiState.value.copy(
                currentActivity = activityType.displayName,
                currentActivityIcon = activityType.icon,
                confidence = confidence,
                probabilities = probabilities.mapKeys { it.key.displayName },
                modelUsed = "Personal"
            )
        }
    }

    private fun classifyWithGenericModel() {
        val classResult = genericClassifier.classify(
            accelerometerManager.ringBuffer,
            gyroscopeManager.ringBuffer
        )

        if (classResult != null) {
            val activity = HarActivity.fromLabel(classResult.label)

            _uiState.value = _uiState.value.copy(
                currentActivity = activity.toDisplayName(),
                currentActivityIcon = activity.toIcon(),
                confidence = classResult.confidence,
                probabilities = classResult.allProbabilities.mapKeys {
                    HarActivity.fromLabel(it.key).toDisplayName()
                },
                modelUsed = "Generic"
            )
        }
    }

    fun provideFeedback(correct: Boolean) {
        val currentState = _uiState.value
        if (correct) {
            _uiState.value = currentState.copy(
                correctCount = currentState.correctCount + 1
            )
        } else {
            _uiState.value = currentState.copy(
                incorrectCount = currentState.incorrectCount + 1
            )
        }
    }

    fun resetFeedback() {
        _uiState.value = _uiState.value.copy(
            correctCount = 0,
            incorrectCount = 0
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopTesting()
        genericClassifier.close()
    }
}

// Extension functions to convert between ActivityType and HarActivity
private fun ActivityType.toHarActivity(): HarActivity = when (this) {
    ActivityType.WALKING -> HarActivity.WALKING
    ActivityType.RUNNING -> HarActivity.WALKING // No running in HarActivity
    ActivityType.SITTING -> HarActivity.SITTING
    ActivityType.STANDING -> HarActivity.STANDING
    ActivityType.LAYING -> HarActivity.LAYING
}

private fun HarActivity.toDisplayName(): String = when (this) {
    HarActivity.WALKING -> "Walking"
    HarActivity.WALKING_UPSTAIRS -> "Walking Upstairs"
    HarActivity.WALKING_DOWNSTAIRS -> "Walking Downstairs"
    HarActivity.SITTING -> "Sitting"
    HarActivity.STANDING -> "Standing"
    HarActivity.LAYING -> "Laying"
    HarActivity.UNKNOWN -> "Unknown"
}

private fun HarActivity.toIcon(): String = when (this) {
    HarActivity.WALKING -> "🚶"
    HarActivity.WALKING_UPSTAIRS -> "🚶⬆️"
    HarActivity.WALKING_DOWNSTAIRS -> "🚶⬇️"
    HarActivity.SITTING -> "🪑"
    HarActivity.STANDING -> "🧍"
    HarActivity.LAYING -> "🛏️"
    HarActivity.UNKNOWN -> "❓"
}

/**
 * UI State for test screen.
 */
data class TestUiState(
    val isRunning: Boolean = false,
    val bufferProgress: Float = 0f,

    // Model availability
    val hasPersonalModel: Boolean = false,
    val hasGenericModel: Boolean = false,
    val usePersonalModel: Boolean = false,

    // Current prediction
    val currentActivity: String = "Unknown",
    val currentActivityIcon: String = "❓",
    val confidence: Float = 0f,
    val probabilities: Map<String, Float> = emptyMap(),
    val modelUsed: String = "",

    // Feedback stats
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,

    val error: String? = null
) {
    val totalFeedback: Int get() = correctCount + incorrectCount
    val accuracyFromFeedback: Float get() = if (totalFeedback > 0) correctCount.toFloat() / totalFeedback else 0f
}
