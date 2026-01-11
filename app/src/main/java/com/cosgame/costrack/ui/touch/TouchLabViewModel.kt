package com.cosgame.costrack.ui.touch

import android.view.MotionEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.touch.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * UI state for TouchLabScreen.
 */
data class TouchLabUiState(
    // Touch data
    val isCollecting: Boolean = false,
    val latestEvent: TouchEvent? = null,
    val currentPath: List<TouchEvent> = emptyList(),
    val touchCount: Long = 0,

    // Metrics
    val metrics: TouchMetrics = TouchMetrics(),
    val heatmap: ScreenHeatmap = ScreenHeatmap(),

    // DNA Profile
    val dnaProfile: TouchDnaProfile = TouchDnaProfile(),
    val isStable: Boolean = false,
    val intensityLevel: IntensityLevel = IntensityLevel.LOW,

    // Missions
    val availableMissions: List<TouchMission> = emptyList(),
    val activeMission: TouchMission? = null,
    val missionProgress: TouchMissionProgress? = null,

    // Drawing
    val drawingPaths: List<List<Pair<Float, Float>>> = emptyList(),
    val currentDrawingPath: List<Pair<Float, Float>> = emptyList(),

    // Screen info
    val screenWidth: Int = 0,
    val screenHeight: Int = 0
)

/**
 * ViewModel for TouchLabScreen.
 */
class TouchLabViewModel : ViewModel() {

    private val collector = TouchDataCollector()
    private val analyzer = TouchAnalyzer()
    private val touchDna = TouchDna()
    private val missionManager = TouchMissionManager()

    private val _uiState = MutableStateFlow(TouchLabUiState())
    val uiState: StateFlow<TouchLabUiState> = _uiState.asStateFlow()

    // For drawing visualization
    private val completedPaths = mutableListOf<List<Pair<Float, Float>>>()
    private var currentPath = mutableListOf<Pair<Float, Float>>()

    // Timer job for mission time updates
    private var missionTimerJob: Job? = null

    init {
        // Load available missions
        _uiState.value = _uiState.value.copy(
            availableMissions = TouchMissions.ALL
        )

        // Observe collector state
        viewModelScope.launch {
            collector.isCollecting.collect { collecting ->
                _uiState.value = _uiState.value.copy(isCollecting = collecting)
            }
        }

        viewModelScope.launch {
            collector.latestEvent.collect { event ->
                _uiState.value = _uiState.value.copy(latestEvent = event)
            }
        }

        viewModelScope.launch {
            collector.touchCount.collect { count ->
                _uiState.value = _uiState.value.copy(touchCount = count)
            }
        }

        viewModelScope.launch {
            collector.currentSequence.collect { events ->
                _uiState.value = _uiState.value.copy(currentPath = events)
            }
        }

        // Observe analyzer metrics
        viewModelScope.launch {
            analyzer.metrics.collect { metrics ->
                _uiState.value = _uiState.value.copy(metrics = metrics)
            }
        }

        viewModelScope.launch {
            analyzer.heatmap.collect { heatmap ->
                _uiState.value = _uiState.value.copy(heatmap = heatmap)
            }
        }

        // Observe DNA profile
        viewModelScope.launch {
            touchDna.profile.collect { profile ->
                _uiState.value = _uiState.value.copy(
                    dnaProfile = profile,
                    intensityLevel = touchDna.getIntensityLevel()
                )
            }
        }

        viewModelScope.launch {
            touchDna.isStable.collect { stable ->
                _uiState.value = _uiState.value.copy(isStable = stable)
            }
        }

        // Observe mission state
        viewModelScope.launch {
            missionManager.currentMission.collect { mission ->
                _uiState.value = _uiState.value.copy(activeMission = mission)
            }
        }

        viewModelScope.launch {
            missionManager.progress.collect { progress ->
                _uiState.value = _uiState.value.copy(missionProgress = progress)
            }
        }

        // Observe completed sequences for analysis
        viewModelScope.launch {
            collector.completedSequences.collect { sequences ->
                sequences.lastOrNull()?.let { sequence ->
                    analyzer.analyzeSequence(sequence)
                    touchDna.addSequence(sequence)

                    // Validate against active mission
                    if (missionManager.isActive.value) {
                        missionManager.validateSequence(sequence)
                    }
                }
            }
        }
    }

    /**
     * Set screen dimensions for normalization.
     */
    fun setScreenDimensions(width: Int, height: Int) {
        collector.setScreenDimensions(width, height)
        _uiState.value = _uiState.value.copy(
            screenWidth = width,
            screenHeight = height
        )
    }

    /**
     * Start collecting touch data.
     */
    fun startCollection() {
        collector.startCollection()
        completedPaths.clear()
        currentPath.clear()
        updateDrawingPaths()
    }

    /**
     * Stop collecting touch data.
     */
    fun stopCollection() {
        collector.stopCollection()
    }

    /**
     * Process touch event from UI.
     */
    fun onTouchEvent(event: MotionEvent) {
        collector.processTouchEvent(event)

        // Update drawing visualization
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = mutableListOf(Pair(event.x, event.y))
                updateDrawingPaths()
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.add(Pair(event.x, event.y))
                updateDrawingPaths()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentPath.isNotEmpty()) {
                    completedPaths.add(currentPath.toList())
                    currentPath.clear()
                    updateDrawingPaths()
                }
            }
        }

        // Analyze single event for real-time metrics
        collector.latestEvent.value?.let { touchEvent ->
            analyzer.analyzeEvent(touchEvent)
        }
    }

    private fun updateDrawingPaths() {
        _uiState.value = _uiState.value.copy(
            drawingPaths = completedPaths.toList(),
            currentDrawingPath = currentPath.toList()
        )
    }

    /**
     * Clear all data.
     */
    fun clearData() {
        collector.clear()
        analyzer.reset()
        touchDna.reset()
        completedPaths.clear()
        currentPath.clear()
        updateDrawingPaths()
    }

    /**
     * Start a touch mission.
     */
    fun startMission(mission: TouchMission) {
        clearData()
        startCollection()
        missionManager.startMission(mission)

        // Start timer for timed missions
        if (mission.timeLimit != null) {
            startMissionTimer()
        }
    }

    /**
     * Stop active mission.
     */
    fun stopMission() {
        missionTimerJob?.cancel()
        missionManager.stopMission()
        stopCollection()
    }

    private fun startMissionTimer() {
        missionTimerJob?.cancel()
        missionTimerJob = viewModelScope.launch {
            while (missionManager.isActive.value) {
                delay(100)
                missionManager.updateTime()
            }
        }
    }

    /**
     * Get missions by type.
     */
    fun getMissionsByType(type: TouchMissionType): List<TouchMission> {
        return TouchMissions.getByType(type)
    }

    override fun onCleared() {
        super.onCleared()
        missionTimerJob?.cancel()
        collector.stopCollection()
    }
}
