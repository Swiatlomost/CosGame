package com.cosgame.costrack.ui.classifiers

import android.app.Application
import androidx.lifecycle.ViewModel
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
import com.cosgame.costrack.sensor.SensorReading
import com.cosgame.costrack.state.AppMode
import com.cosgame.costrack.state.AppStateManager
import com.cosgame.costrack.activitylog.ActivityLog
import com.cosgame.costrack.activitylog.ActivityLogRepository
import com.cosgame.costrack.activitylog.ActivitySession
import com.cosgame.costrack.training.ActivityType
import com.cosgame.costrack.training.PersonalHarTrainer
import com.cosgame.costrack.training.TrainingSample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Classifiers screen.
 * Manages HAR classification and DNA aggregation.
 */
@HiltViewModel
class ClassifiersViewModel @Inject constructor(
    private val application: Application,
    private val appStateManager: AppStateManager,
    private val personalTrainer: PersonalHarTrainer,
    private val activityLogRepository: ActivityLogRepository
) : ViewModel() {

    private val sensorConfig = SensorConfig.DEFAULT
    private val accelerometerManager = AccelerometerManager(application, sensorConfig)
    private val gyroscopeManager = GyroscopeManager(application, sensorConfig)

    private val harClassifier = HarClassifier(application)
    private val dnaAggregator = DnaAggregator(DnaConfig(
        historySize = 10,
        strategy = AggregationStrategy.RECENT_WEIGHTED,
        stabilityThreshold = 3
    ))

    // Current activity session
    private var currentSessionId: Long? = null

    private val _uiState = MutableStateFlow(ClassifiersUiState())
    val uiState: StateFlow<ClassifiersUiState> = _uiState.asStateFlow()

    private var classificationJob: Job? = null

    // Sample buffer for personal model
    private val sampleBuffer = mutableListOf<TrainingSample>()

    init {
        observeAppMode()
        observeAggregatedResults()
        observeSensorStatus()
        checkPersonalModelAvailable()
    }

    private fun observeSensorStatus() {
        viewModelScope.launch {
            accelerometerManager.isActive.collect { active ->
                _uiState.value = _uiState.value.copy(accelerometerActive = active)
            }
        }
        viewModelScope.launch {
            gyroscopeManager.isActive.collect { active ->
                _uiState.value = _uiState.value.copy(gyroscopeActive = active)
            }
        }
        viewModelScope.launch {
            accelerometerManager.latestReading.collect { reading ->
                _uiState.value = _uiState.value.copy(
                    accelerometerReading = reading,
                    accelerometerSampleCount = accelerometerManager.getReadingCount()
                )
            }
        }
        viewModelScope.launch {
            gyroscopeManager.latestReading.collect { reading ->
                _uiState.value = _uiState.value.copy(
                    gyroscopeReading = reading,
                    gyroscopeSampleCount = gyroscopeManager.getReadingCount()
                )
            }
        }
    }

    private fun checkPersonalModelAvailable() {
        val hasPersonal = personalTrainer.hasTrainedModel()
        if (hasPersonal) {
            personalTrainer.loadModel()
        }
        _uiState.value = _uiState.value.copy(
            hasPersonalModel = hasPersonal,
            selectedModel = if (hasPersonal) ModelType.PERSONAL else ModelType.GENERIC
        )
    }

    fun selectModel(modelType: ModelType) {
        if (modelType == ModelType.PERSONAL && !_uiState.value.hasPersonalModel) {
            return
        }
        _uiState.value = _uiState.value.copy(selectedModel = modelType)
        resetAggregator()
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
                        currentActivity = labelToHarActivity(it.label),
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

        // Create new activity session
        viewModelScope.launch {
            val session = ActivitySession.start(_uiState.value.selectedModel.name)
            currentSessionId = activityLogRepository.insertSession(session)
        }

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

        // Complete the activity session
        currentSessionId?.let { sessionId ->
            viewModelScope.launch {
                val session = activityLogRepository.getSession(sessionId)
                session?.let {
                    val logsCount = activityLogRepository.getLogsCount(sessionId)
                    activityLogRepository.updateSession(
                        it.copy(
                            endTime = System.currentTimeMillis(),
                            classificationsCount = logsCount,
                            completed = true
                        )
                    )
                }
            }
            currentSessionId = null
        }

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

        // Log raw sensor data
        val sensorEntry = formatSensorEntry()
        val updatedSensorLogs = (_uiState.value.sensorLogs + sensorEntry).takeLast(30)

        val result: ClassificationResult? = when (_uiState.value.selectedModel) {
            ModelType.GENERIC -> {
                harClassifier.classify(
                    accelerometerManager.ringBuffer,
                    gyroscopeManager.ringBuffer
                )
            }
            ModelType.PERSONAL -> {
                classifyWithPersonalModel()
            }
        }

        result?.let {
            // Add debug log entry
            val logEntry = formatLogEntry(it)
            val updatedLogs = (_uiState.value.debugLogs + logEntry).takeLast(50)

            _uiState.value = _uiState.value.copy(
                lastClassificationResult = it,
                classificationCount = _uiState.value.classificationCount + 1,
                debugLogs = updatedLogs,
                sensorLogs = updatedSensorLogs
            )
            dnaAggregator.addResult(it)

            // Save to database
            saveActivityLog(it)
        } ?: run {
            _uiState.value = _uiState.value.copy(sensorLogs = updatedSensorLogs)
        }
    }

    private fun saveActivityLog(result: ClassificationResult) {
        val sessionId = currentSessionId ?: return
        val acc = accelerometerManager.latestReading.value ?: return
        val gyro = gyroscopeManager.latestReading.value ?: return
        val dnaResult = dnaAggregator.aggregatedResult.value

        val probsJson = try {
            result.allProbabilities.entries.joinToString(",", "{", "}") { (k, v) ->
                "\"$k\":$v"
            }
        } catch (e: Exception) {
            "{}"
        }

        val log = ActivityLog.create(
            sessionId = sessionId,
            accX = acc.x,
            accY = acc.y,
            accZ = acc.z,
            gyroX = gyro.x,
            gyroY = gyro.y,
            gyroZ = gyro.z,
            classifiedLabel = result.label,
            classifiedConfidence = result.confidence,
            modelType = _uiState.value.selectedModel.name,
            dnaLabel = dnaResult?.label ?: result.label,
            dnaConfidence = dnaResult?.confidence ?: result.confidence,
            dnaIsStable = dnaAggregator.isStable.value,
            probabilitiesJson = probsJson
        )

        viewModelScope.launch {
            activityLogRepository.insertLog(log)
        }
    }

    private fun classifyWithPersonalModel(): ClassificationResult? {
        // Use ring buffer data directly (same as generic model)
        val accelData = accelerometerManager.ringBuffer.toInterleavedArray()
        val gyroData = gyroscopeManager.ringBuffer.toInterleavedArray()

        // Need at least 50 samples (150 values for 3 axes)
        if (accelData.size < 150 || gyroData.size < 150) return null

        // Convert ring buffer data to TrainingSample list for personal model
        val samples = mutableListOf<TrainingSample>()
        val sampleCount = minOf(accelData.size / 3, gyroData.size / 3, 50)

        // Take the last 50 samples
        val startIdx = maxOf(0, (accelData.size / 3) - 50)
        for (i in 0 until sampleCount) {
            val idx = startIdx + i
            val sample = TrainingSample.create(
                activityType = ActivityType.STANDING, // Placeholder
                accX = accelData.getOrElse(idx * 3) { 0f },
                accY = accelData.getOrElse(idx * 3 + 1) { 0f },
                accZ = accelData.getOrElse(idx * 3 + 2) { 0f },
                gyroX = gyroData.getOrElse(idx * 3) { 0f },
                gyroY = gyroData.getOrElse(idx * 3 + 1) { 0f },
                gyroZ = gyroData.getOrElse(idx * 3 + 2) { 0f },
                sessionId = 0
            )
            samples.add(sample)
        }

        if (samples.size < 50) return null

        val prediction = personalTrainer.predict(samples) ?: return null
        val (activityType, confidence) = prediction

        // Convert to ClassificationResult for compatibility with aggregator
        val allProbs = ActivityType.entries.associate { type ->
            type.name to if (type == activityType) confidence else 0f
        }

        return ClassificationResult(
            classifierId = "personal_model",
            label = activityType.name,
            confidence = confidence,
            allProbabilities = allProbs,
            inferenceTimeMs = 0,
            debugInfo = "Personal"
        )
    }

    private fun formatSensorEntry(): String {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val acc = accelerometerManager.latestReading.value
        val gyr = gyroscopeManager.latestReading.value
        val accStr = acc?.let { "A[%.1f,%.1f,%.1f]".format(it.x, it.y, it.z) } ?: "A[--]"
        val gyrStr = gyr?.let { "G[%.1f,%.1f,%.1f]".format(it.x, it.y, it.z) } ?: "G[--]"
        return "[$time] $accStr $gyrStr"
    }

    private fun formatLogEntry(result: ClassificationResult): String {
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date())
        val probs = result.allProbabilities.entries
            .sortedByDescending { it.value }
            .joinToString(" ") {
                val shortLabel = when(it.key.uppercase()) {
                    "WALKING" -> "Wlk"
                    "WALKING_UPSTAIRS" -> "Up"
                    "WALKING_DOWNSTAIRS" -> "Dn"
                    "SITTING" -> "Sit"
                    "STANDING" -> "Std"
                    "LAYING" -> "Lay"
                    else -> it.key.take(3)
                }
                "$shortLabel:${(it.value * 100).toInt()}%"
            }

        // Add debug info
        val debugInfo = result.debugInfo ?: ""

        return "[$time] $debugInfo ${result.label}(${(result.confidence * 100).toInt()}%) $probs"
    }

    private fun f(v: Float): String = "%.1f".format(v)

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(debugLogs = emptyList())
    }

    fun getLogsAsText(): String {
        return _uiState.value.debugLogs.joinToString("\n")
    }

    fun clearSensorLogs() {
        _uiState.value = _uiState.value.copy(sensorLogs = emptyList())
    }

    fun getSensorLogsAsText(): String {
        return _uiState.value.sensorLogs.joinToString("\n")
    }

    fun resetAggregator() {
        dnaAggregator.reset()
        sampleBuffer.clear()
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

    // Sensor info for DEV mode
    fun getSamplingFrequency(): Int = sensorConfig.samplingFrequencyHz
    fun getAccelerometerInfo() = accelerometerManager.getSensorInfo()
    fun getGyroscopeInfo() = gyroscopeManager.getSensorInfo()

    override fun onCleared() {
        super.onCleared()
        stopClassification()
        harClassifier.close()
    }
}

/**
 * Model type selection.
 */
enum class ModelType(val displayName: String) {
    GENERIC("Generic (UCI HAR)"),
    PERSONAL("Personal (Trained)")
}

/**
 * UI State for Classifiers screen.
 */
data class ClassifiersUiState(
    val appMode: AppMode = AppMode.USER,
    val isRunning: Boolean = false,
    val bufferProgress: Float = 0f,

    // Model selection
    val selectedModel: ModelType = ModelType.GENERIC,
    val hasPersonalModel: Boolean = false,

    // Current activity
    val currentActivity: HarActivity = HarActivity.UNKNOWN,
    val isStable: Boolean = false,

    // Results
    val lastClassificationResult: ClassificationResult? = null,
    val aggregatedResult: AggregatedResult? = null,
    val classificationCount: Long = 0,

    // Error state
    val error: String? = null,

    // Debug logs (dev mode)
    val debugLogs: List<String> = emptyList(),
    val sensorLogs: List<String> = emptyList(),

    // Sensor status
    val accelerometerActive: Boolean = false,
    val gyroscopeActive: Boolean = false,
    val accelerometerReading: SensorReading? = null,
    val gyroscopeReading: SensorReading? = null,
    val accelerometerSampleCount: Long = 0,
    val gyroscopeSampleCount: Long = 0
) {
    val isDevMode: Boolean get() = appMode == AppMode.DEVELOPER
    val confidence: Float get() = aggregatedResult?.confidence ?: 0f
    val hasResults: Boolean get() = aggregatedResult != null
    val sensorsActive: Boolean get() = accelerometerActive && gyroscopeActive
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

/**
 * Convert label string to HarActivity, supporting both HarActivity and ActivityType labels.
 */
fun labelToHarActivity(label: String): HarActivity {
    // First try standard HarActivity lookup
    val harActivity = HarActivity.fromLabel(label)
    if (harActivity != HarActivity.UNKNOWN) return harActivity

    // Try ActivityType mapping (for Personal model)
    return when (label.uppercase()) {
        "WALKING" -> HarActivity.WALKING
        "RUNNING" -> HarActivity.WALKING // Map running to walking (closest match)
        "SITTING" -> HarActivity.SITTING
        "STANDING" -> HarActivity.STANDING
        "LAYING" -> HarActivity.LAYING
        else -> HarActivity.UNKNOWN
    }
}
