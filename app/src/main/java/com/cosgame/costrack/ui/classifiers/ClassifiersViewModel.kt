package com.cosgame.costrack.ui.classifiers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.aggregator.AggregatedResult
import com.cosgame.costrack.aggregator.AggregationStrategy
import com.cosgame.costrack.aggregator.DnaAggregator
import com.cosgame.costrack.aggregator.DnaConfig
import com.cosgame.costrack.classifier.ClassificationResult
import com.cosgame.costrack.classifier.HarActivity
import com.cosgame.costrack.classifier.HarClassifier
import com.cosgame.costrack.sensor.AccelerometerManager
import com.cosgame.costrack.sensor.GyroscopeManager
import com.cosgame.costrack.sensor.SensorConfig
import com.cosgame.costrack.state.AppMode
import com.cosgame.costrack.state.AppStateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the Classifiers screen.
 * Manages HAR classification and DNA aggregation.
 */
class ClassifiersViewModel(application: Application) : AndroidViewModel(application) {

    private val appStateManager = AppStateManager.getInstance(application)

    private val sensorConfig = SensorConfig.DEFAULT
    private val accelerometerManager = AccelerometerManager(application, sensorConfig)
    private val gyroscopeManager = GyroscopeManager(application, sensorConfig)

    private val harClassifier = HarClassifier(application)
    private val dnaAggregator = DnaAggregator(DnaConfig(
        historySize = 10,
        strategy = AggregationStrategy.RECENT_WEIGHTED,
        stabilityThreshold = 3
    ))

    private val _uiState = MutableStateFlow(ClassifiersUiState())
    val uiState: StateFlow<ClassifiersUiState> = _uiState.asStateFlow()

    private var classificationJob: Job? = null

    init {
        observeAppMode()
        observeAggregatedResults()
    }

    private fun observeAppMode() {
        viewModelScope.launch {
            appStateManager.appMode.collect { mode ->
                _uiState.value = _uiState.value.copy(appMode = mode)
            }
        }
    }

    private fun observeAggregatedResults() {
        viewModelScope.launch {
            dnaAggregator.aggregatedResult.collect { result ->
                result?.let {
                    _uiState.value = _uiState.value.copy(
                        aggregatedResult = it,
                        currentActivity = HarActivity.fromLabel(it.label),
                        isStable = dnaAggregator.isStable.value
                    )
                }
            }
        }

        viewModelScope.launch {
            dnaAggregator.isStable.collect { stable ->
                _uiState.value = _uiState.value.copy(isStable = stable)
            }
        }
    }

    fun startClassification() {
        if (_uiState.value.isRunning) return

        // Initialize classifier
        if (!harClassifier.isReady) {
            val initialized = harClassifier.initialize()
            if (!initialized) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load HAR model. Place har_model.tflite in assets/"
                )
                return
            }
        }

        // Start sensors
        accelerometerManager.start()
        gyroscopeManager.start()

        _uiState.value = _uiState.value.copy(
            isRunning = true,
            error = null
        )

        // Start classification loop
        classificationJob = viewModelScope.launch {
            while (isActive) {
                runClassification()
                delay(500) // Classify every 500ms
            }
        }
    }

    fun stopClassification() {
        classificationJob?.cancel()
        classificationJob = null

        accelerometerManager.stop()
        gyroscopeManager.stop()

        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    private fun runClassification() {
        if (!accelerometerManager.isBufferReady() || !gyroscopeManager.isBufferReady()) {
            _uiState.value = _uiState.value.copy(
                bufferProgress = minOf(
                    accelerometerManager.getBufferFillRatio(),
                    gyroscopeManager.getBufferFillRatio()
                )
            )
            return
        }

        _uiState.value = _uiState.value.copy(bufferProgress = 1f)

        val result = harClassifier.classify(
            accelerometerManager.ringBuffer,
            gyroscopeManager.ringBuffer
        )

        result?.let {
            _uiState.value = _uiState.value.copy(
                lastClassificationResult = it,
                classificationCount = _uiState.value.classificationCount + 1
            )
            dnaAggregator.addResult(it)
        }
    }

    fun resetAggregator() {
        dnaAggregator.reset()
        _uiState.value = _uiState.value.copy(
            aggregatedResult = null,
            currentActivity = HarActivity.UNKNOWN,
            isStable = false,
            classificationCount = 0
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopClassification()
        harClassifier.close()
    }
}

/**
 * UI State for Classifiers screen.
 */
data class ClassifiersUiState(
    val appMode: AppMode = AppMode.USER,
    val isRunning: Boolean = false,
    val bufferProgress: Float = 0f,

    // Current activity
    val currentActivity: HarActivity = HarActivity.UNKNOWN,
    val isStable: Boolean = false,

    // Results
    val lastClassificationResult: ClassificationResult? = null,
    val aggregatedResult: AggregatedResult? = null,
    val classificationCount: Long = 0,

    // Error state
    val error: String? = null
) {
    val isDevMode: Boolean get() = appMode == AppMode.DEVELOPER
    val confidence: Float get() = aggregatedResult?.confidence ?: 0f
    val hasResults: Boolean get() = aggregatedResult != null
}

/**
 * Get display icon for activity.
 */
fun HarActivity.toIcon(): String = when (this) {
    HarActivity.WALKING -> "🚶"
    HarActivity.WALKING_UPSTAIRS -> "🚶⬆️"
    HarActivity.WALKING_DOWNSTAIRS -> "🚶⬇️"
    HarActivity.SITTING -> "🪑"
    HarActivity.STANDING -> "🧍"
    HarActivity.LAYING -> "🛏️"
    HarActivity.UNKNOWN -> "❓"
}

/**
 * Get display name for activity.
 */
fun HarActivity.toDisplayName(): String = when (this) {
    HarActivity.WALKING -> "Walking"
    HarActivity.WALKING_UPSTAIRS -> "Walking Upstairs"
    HarActivity.WALKING_DOWNSTAIRS -> "Walking Downstairs"
    HarActivity.SITTING -> "Sitting"
    HarActivity.STANDING -> "Standing"
    HarActivity.LAYING -> "Laying"
    HarActivity.UNKNOWN -> "Unknown"
}
