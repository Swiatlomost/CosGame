package com.cosgame.costrack.touch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State of a running touch mission.
 */
enum class MissionState {
    IDLE,
    COUNTDOWN,
    RUNNING,
    COMPLETED,
    CANCELLED
}

/**
 * Runs touch missions and manages the collection process.
 */
class TouchMissionRunner(
    private val collector: TouchSessionCollector,
    private val repository: TouchRepository? = null
) {
    private val _state = MutableStateFlow(MissionState.IDLE)
    val state: StateFlow<MissionState> = _state.asStateFlow()

    private val _currentMission = MutableStateFlow<TouchMissionType?>(null)
    val currentMission: StateFlow<TouchMissionType?> = _currentMission.asStateFlow()

    private val _timeRemaining = MutableStateFlow(0)
    val timeRemaining: StateFlow<Int> = _timeRemaining.asStateFlow()

    private val _countdownValue = MutableStateFlow(0)
    val countdownValue: StateFlow<Int> = _countdownValue.asStateFlow()

    private val _result = MutableStateFlow<TouchMissionResult?>(null)
    val result: StateFlow<TouchMissionResult?> = _result.asStateFlow()

    private var missionStartTime: Long = 0
    private var label: String? = null

    /**
     * Start a mission with countdown.
     */
    fun startMission(missionType: TouchMissionType, withLabel: String? = null) {
        _currentMission.value = missionType
        _timeRemaining.value = missionType.durationSeconds
        _countdownValue.value = 3
        _result.value = null
        _state.value = MissionState.COUNTDOWN
        label = withLabel ?: missionType.id
    }

    /**
     * Called each second during countdown.
     * Returns true when countdown is done and mission should start.
     */
    fun tickCountdown(): Boolean {
        if (_state.value != MissionState.COUNTDOWN) return false

        val current = _countdownValue.value
        if (current > 1) {
            _countdownValue.value = current - 1
            return false
        } else {
            // Countdown done, start mission
            beginCollection()
            return true
        }
    }

    /**
     * Begin actual collection (called after countdown).
     */
    private fun beginCollection() {
        _state.value = MissionState.RUNNING
        missionStartTime = System.currentTimeMillis()
        collector.startSession()
    }

    /**
     * Called each second during mission.
     * Returns true when mission time is up.
     */
    fun tickMission(): Boolean {
        if (_state.value != MissionState.RUNNING) return false

        val remaining = _timeRemaining.value
        if (remaining > 1) {
            _timeRemaining.value = remaining - 1
            return false
        } else {
            // Time's up
            completeMission()
            return true
        }
    }

    /**
     * Complete the mission and save results.
     */
    private fun completeMission() {
        val session = collector.stopSession()
        val mission = _currentMission.value ?: return

        // Add label to session
        val labeledSession = session?.copy(
            label = label,
            missionType = mission.id,
            missionCompleted = true
        )

        _result.value = TouchMissionResult(
            missionType = mission,
            session = labeledSession,
            completed = true,
            tapCount = session?.tapCount ?: 0,
            swipeCount = session?.swipeCount ?: 0,
            avgPressure = session?.avgPressure ?: 0f,
            duration = session?.duration ?: 0
        )

        _state.value = MissionState.COMPLETED
    }

    /**
     * Cancel the current mission.
     */
    fun cancelMission() {
        collector.stopSession()
        _state.value = MissionState.CANCELLED
        _currentMission.value = null
    }

    /**
     * Reset to idle state.
     */
    fun reset() {
        _state.value = MissionState.IDLE
        _currentMission.value = null
        _timeRemaining.value = 0
        _countdownValue.value = 0
        _result.value = null
        label = null
    }

    /**
     * Save the completed session to repository.
     */
    suspend fun saveResult(): Long? {
        val session = _result.value?.session ?: return null
        return repository?.insert(session)
    }

    /**
     * Get current stats during mission.
     */
    fun getCurrentStats(): TouchSessionStats {
        return collector.stats.value
    }

    /**
     * Check if mission is active (countdown or running).
     */
    fun isActive(): Boolean = _state.value in listOf(MissionState.COUNTDOWN, MissionState.RUNNING)
}
