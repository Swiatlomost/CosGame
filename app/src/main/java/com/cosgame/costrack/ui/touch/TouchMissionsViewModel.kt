package com.cosgame.costrack.ui.touch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.touch.*
import com.cosgame.costrack.training.TrainingDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Tab selection for Touch Intelligence screen.
 */
enum class TouchTab {
    COLLECT,  // Data collection through missions
    TRAIN,    // Model training
    TEST      // Real-time classification
}

/**
 * Training state.
 */
enum class TrainingState {
    IDLE,
    TRAINING,
    COMPLETED,
    ERROR
}

/**
 * UI state for TouchMissionsScreen.
 */
data class TouchMissionsUiState(
    // Tab
    val currentTab: TouchTab = TouchTab.COLLECT,

    // Collect tab
    val missions: List<TouchMissionType> = TouchMissionType.ALL,
    val missionState: MissionState = MissionState.IDLE,
    val currentMission: TouchMissionType? = null,
    val countdownValue: Int = 0,
    val timeRemaining: Int = 0,
    val result: TouchMissionResult? = null,
    val currentStats: TouchSessionStats = TouchSessionStats(),
    val drawingPath: List<Pair<Float, Float>> = emptyList(),
    val completedPaths: List<List<Pair<Float, Float>>> = emptyList(),
    val selectedLabel: String = "default",

    // Data stats
    val totalSessions: Int = 0,
    val labeledSessions: Int = 0,
    val labels: List<String> = emptyList(),
    val labelCounts: Map<String, Int> = emptyMap(),

    // Train tab
    val trainingState: TrainingState = TrainingState.IDLE,
    val trainingProgress: Int = 0,
    val trainingEpochs: Int = 20,
    val currentEpoch: Int = 0,
    val currentLoss: Float = 0f,
    val currentAccuracy: Float = 0f,
    val modelInfo: ModelInfo? = null,
    val trainingMessage: String = "",

    // Test tab
    val isTestRunning: Boolean = false,
    val testResult: ClassificationResult? = null,
    val testStats: TouchSessionStats = TouchSessionStats(),
    val testDrawingPath: List<Pair<Float, Float>> = emptyList(),
    val testCompletedPaths: List<List<Pair<Float, Float>>> = emptyList()
)

/**
 * ViewModel for TouchMissionsScreen with training and testing.
 */
class TouchMissionsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TrainingDatabase.getInstance(application)
    private val repository = TouchRepository(database.touchSessionDao())
    private val collector = TouchSessionCollector()
    private val missionRunner = TouchMissionRunner(collector, repository)
    private val classifier = TouchClassifier(application, numClasses = 5)
    private val featureExtractor = TouchFeatureExtractor()

    // Test mode collector
    private val testCollector = TouchSessionCollector()

    private val _uiState = MutableStateFlow(TouchMissionsUiState())
    val uiState: StateFlow<TouchMissionsUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var currentPath = mutableListOf<Pair<Float, Float>>()
    private val completedPaths = mutableListOf<List<Pair<Float, Float>>>()

    // Test mode paths
    private var testPath = mutableListOf<Pair<Float, Float>>()
    private val testCompletedPaths = mutableListOf<List<Pair<Float, Float>>>()

    init {
        // Observe mission runner state
        viewModelScope.launch {
            missionRunner.state.collect { state ->
                _uiState.value = _uiState.value.copy(missionState = state)
            }
        }

        viewModelScope.launch {
            missionRunner.currentMission.collect { mission ->
                _uiState.value = _uiState.value.copy(currentMission = mission)
            }
        }

        viewModelScope.launch {
            missionRunner.countdownValue.collect { countdown ->
                _uiState.value = _uiState.value.copy(countdownValue = countdown)
            }
        }

        viewModelScope.launch {
            missionRunner.timeRemaining.collect { time ->
                _uiState.value = _uiState.value.copy(timeRemaining = time)
            }
        }

        viewModelScope.launch {
            missionRunner.result.collect { result ->
                _uiState.value = _uiState.value.copy(result = result)
            }
        }

        viewModelScope.launch {
            collector.stats.collect { stats ->
                _uiState.value = _uiState.value.copy(currentStats = stats)
            }
        }

        viewModelScope.launch {
            testCollector.stats.collect { stats ->
                _uiState.value = _uiState.value.copy(testStats = stats)
            }
        }

        // Load initial data
        loadData()
        loadModelInfo()
    }

    private fun loadData() {
        viewModelScope.launch {
            val count = repository.getSessionCount()
            val labeledCount = repository.getLabeledSessionCount()
            val allLabels = repository.getAllLabels()
            val counts = mutableMapOf<String, Int>()
            allLabels.forEach { label ->
                counts[label] = repository.getCountByLabel(label)
            }

            _uiState.value = _uiState.value.copy(
                totalSessions = count,
                labeledSessions = labeledCount,
                labels = allLabels,
                labelCounts = counts
            )
        }
    }

    private fun loadModelInfo() {
        val info = classifier.getModelInfo()
        _uiState.value = _uiState.value.copy(modelInfo = info)
    }

    // === TAB NAVIGATION ===

    fun selectTab(tab: TouchTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
        if (tab == TouchTab.TRAIN) {
            loadData()
            loadModelInfo()
        }
    }

    // === COLLECT TAB ===

    fun setScreenDimensions(width: Int, height: Int) {
        collector.setScreenDimensions(width, height)
        testCollector.setScreenDimensions(width, height)
    }

    fun startMission(mission: TouchMissionType) {
        currentPath.clear()
        completedPaths.clear()
        updatePaths()

        missionRunner.startMission(mission, _uiState.value.selectedLabel)
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (missionRunner.state.value == MissionState.COUNTDOWN) {
                delay(1000)
                missionRunner.tickCountdown()
            }

            while (missionRunner.state.value == MissionState.RUNNING) {
                delay(1000)
                if (missionRunner.tickMission()) {
                    saveAndRefresh()
                }
            }
        }
    }

    private fun saveAndRefresh() {
        viewModelScope.launch {
            missionRunner.saveResult()
            loadData()
        }
    }

    fun cancelMission() {
        timerJob?.cancel()
        missionRunner.cancelMission()
    }

    fun reset() {
        missionRunner.reset()
        currentPath.clear()
        completedPaths.clear()
        updatePaths()
    }

    fun setLabel(label: String) {
        _uiState.value = _uiState.value.copy(selectedLabel = label)
    }

    fun onTouchStart(x: Float, y: Float, pressure: Float) {
        currentPath = mutableListOf(Pair(x, y))
        updatePaths()
        collector.addEvent(x, y, pressure, 0.1f, TouchEventType.DOWN, 0)
    }

    fun onTouchMove(x: Float, y: Float, pressure: Float) {
        currentPath.add(Pair(x, y))
        updatePaths()
        collector.addEvent(x, y, pressure, 0.1f, TouchEventType.MOVE, 0)
    }

    fun onTouchEnd(x: Float, y: Float) {
        if (currentPath.isNotEmpty()) {
            completedPaths.add(currentPath.toList())
            currentPath.clear()
            updatePaths()
        }
        collector.addEvent(x, y, 0f, 0.1f, TouchEventType.UP, 0)
    }

    private fun updatePaths() {
        _uiState.value = _uiState.value.copy(
            drawingPath = currentPath.toList(),
            completedPaths = completedPaths.toList()
        )
    }

    // === TRAIN TAB ===

    fun setTrainingEpochs(epochs: Int) {
        _uiState.value = _uiState.value.copy(trainingEpochs = epochs.coerceIn(5, 100))
    }

    fun startTraining() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                trainingState = TrainingState.TRAINING,
                trainingProgress = 0,
                currentEpoch = 0,
                trainingMessage = "Loading data..."
            )

            val sessions = repository.getLabeledSessionsForTraining()

            if (sessions.size < 10) {
                _uiState.value = _uiState.value.copy(
                    trainingState = TrainingState.ERROR,
                    trainingMessage = "Need at least 10 labeled sessions (have ${sessions.size})"
                )
                return@launch
            }

            val labels = sessions.mapNotNull { it.label }.distinct()
            if (labels.size < 2) {
                _uiState.value = _uiState.value.copy(
                    trainingState = TrainingState.ERROR,
                    trainingMessage = "Need at least 2 different labels (have ${labels.size})"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                trainingMessage = "Training on ${sessions.size} sessions..."
            )

            val result = classifier.trainOnSessions(
                sessions = sessions,
                epochs = _uiState.value.trainingEpochs
            ) { epoch, loss, accuracy ->
                _uiState.value = _uiState.value.copy(
                    currentEpoch = epoch,
                    currentLoss = loss,
                    currentAccuracy = accuracy,
                    trainingProgress = (epoch * 100) / _uiState.value.trainingEpochs,
                    trainingMessage = "Epoch $epoch: accuracy ${String.format("%.1f", accuracy * 100)}%"
                )
            }

            if (result.success) {
                _uiState.value = _uiState.value.copy(
                    trainingState = TrainingState.COMPLETED,
                    trainingMessage = "Training complete! Accuracy: ${String.format("%.1f", result.finalAccuracy * 100)}%"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    trainingState = TrainingState.ERROR,
                    trainingMessage = result.message
                )
            }

            loadModelInfo()
        }
    }

    fun resetModel() {
        classifier.reset()
        loadModelInfo()
        _uiState.value = _uiState.value.copy(
            trainingState = TrainingState.IDLE,
            trainingMessage = "Model reset"
        )
    }

    fun dismissTrainingResult() {
        _uiState.value = _uiState.value.copy(
            trainingState = TrainingState.IDLE,
            trainingMessage = ""
        )
    }

    // === TEST TAB ===

    fun startTest() {
        testPath.clear()
        testCompletedPaths.clear()
        testCollector.startSession()
        _uiState.value = _uiState.value.copy(
            isTestRunning = true,
            testResult = null,
            testDrawingPath = emptyList(),
            testCompletedPaths = emptyList()
        )
    }

    fun stopTest() {
        val session = testCollector.stopSession()
        _uiState.value = _uiState.value.copy(isTestRunning = false)

        if (session != null && classifier.isModelTrained()) {
            viewModelScope.launch {
                val events = TouchSessionCollector.eventsFromJson(session.touchEventsJson)
                val features = featureExtractor.extractFeatures(events, session.duration)
                val result = classifier.predict(features)
                _uiState.value = _uiState.value.copy(testResult = result)
            }
        } else if (!classifier.isModelTrained()) {
            _uiState.value = _uiState.value.copy(
                testResult = ClassificationResult(
                    classIndex = -1,
                    label = "Model not trained",
                    confidence = 0f,
                    probabilities = emptyList()
                )
            )
        }
    }

    fun clearTestResult() {
        testPath.clear()
        testCompletedPaths.clear()
        _uiState.value = _uiState.value.copy(
            testResult = null,
            testDrawingPath = emptyList(),
            testCompletedPaths = emptyList()
        )
    }

    fun onTestTouchStart(x: Float, y: Float, pressure: Float) {
        testPath = mutableListOf(Pair(x, y))
        updateTestPaths()
        testCollector.addEvent(x, y, pressure, 0.1f, TouchEventType.DOWN, 0)
    }

    fun onTestTouchMove(x: Float, y: Float, pressure: Float) {
        testPath.add(Pair(x, y))
        updateTestPaths()
        testCollector.addEvent(x, y, pressure, 0.1f, TouchEventType.MOVE, 0)
    }

    fun onTestTouchEnd(x: Float, y: Float) {
        if (testPath.isNotEmpty()) {
            testCompletedPaths.add(testPath.toList())
            testPath.clear()
            updateTestPaths()
        }
        testCollector.addEvent(x, y, 0f, 0.1f, TouchEventType.UP, 0)
    }

    private fun updateTestPaths() {
        _uiState.value = _uiState.value.copy(
            testDrawingPath = testPath.toList(),
            testCompletedPaths = testCompletedPaths.toList()
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
