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
 * UI state for TouchMissionsScreen.
 */
data class TouchMissionsUiState(
    val missions: List<TouchMissionType> = TouchMissionType.ALL,
    val missionState: MissionState = MissionState.IDLE,
    val currentMission: TouchMissionType? = null,
    val countdownValue: Int = 0,
    val timeRemaining: Int = 0,
    val result: TouchMissionResult? = null,
    val currentStats: TouchSessionStats = TouchSessionStats(),
    val drawingPath: List<Pair<Float, Float>> = emptyList(),
    val completedPaths: List<List<Pair<Float, Float>>> = emptyList(),
    val totalSessions: Int = 0,
    val selectedLabel: String = "default"
)

/**
 * ViewModel for TouchMissionsScreen.
 */
class TouchMissionsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TrainingDatabase.getInstance(application)
    private val repository = TouchRepository(database.touchSessionDao())
    private val collector = TouchSessionCollector()
    private val missionRunner = TouchMissionRunner(collector, repository)

    private val _uiState = MutableStateFlow(TouchMissionsUiState())
    val uiState: StateFlow<TouchMissionsUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var currentPath = mutableListOf<Pair<Float, Float>>()
    private val completedPaths = mutableListOf<List<Pair<Float, Float>>>()

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

        // Load session count
        loadSessionCount()
    }

    private fun loadSessionCount() {
        viewModelScope.launch {
            val count = repository.getSessionCount()
            _uiState.value = _uiState.value.copy(totalSessions = count)
        }
    }

    /**
     * Set screen dimensions for coordinate normalization.
     */
    fun setScreenDimensions(width: Int, height: Int) {
        collector.setScreenDimensions(width, height)
    }

    /**
     * Start a mission.
     */
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
            // Countdown phase
            while (missionRunner.state.value == MissionState.COUNTDOWN) {
                delay(1000)
                missionRunner.tickCountdown()
            }

            // Running phase
            while (missionRunner.state.value == MissionState.RUNNING) {
                delay(1000)
                if (missionRunner.tickMission()) {
                    // Mission completed
                    saveAndRefresh()
                }
            }
        }
    }

    private fun saveAndRefresh() {
        viewModelScope.launch {
            missionRunner.saveResult()
            loadSessionCount()
        }
    }

    /**
     * Cancel current mission.
     */
    fun cancelMission() {
        timerJob?.cancel()
        missionRunner.cancelMission()
    }

    /**
     * Reset after viewing results.
     */
    fun reset() {
        missionRunner.reset()
        currentPath.clear()
        completedPaths.clear()
        updatePaths()
    }

    /**
     * Set label for data collection.
     */
    fun setLabel(label: String) {
        _uiState.value = _uiState.value.copy(selectedLabel = label)
    }

    // Touch event handlers
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

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
