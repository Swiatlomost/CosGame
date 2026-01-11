package com.cosgame.costrack.ui.missions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.sensor.AccelerometerManager
import com.cosgame.costrack.sensor.GyroscopeManager
import com.cosgame.costrack.sensor.SensorConfig
import com.cosgame.costrack.training.ActivityType
import com.cosgame.costrack.training.TrainingRepository
import com.cosgame.costrack.training.TrainingSample
import com.cosgame.costrack.training.TrainingSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for active mission execution.
 */
class ActiveMissionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrainingRepository.getInstance(application)

    private val sensorConfig = SensorConfig.DEFAULT // 50Hz
    private val accelerometerManager = AccelerometerManager(application, sensorConfig)
    private val gyroscopeManager = GyroscopeManager(application, sensorConfig)

    private val _uiState = MutableStateFlow(ActiveMissionUiState())
    val uiState: StateFlow<ActiveMissionUiState> = _uiState.asStateFlow()

    private var missionJob: Job? = null
    private var currentSessionId: Long = 0
    private val collectedSamples = mutableListOf<TrainingSample>()

    fun setMission(mission: Mission) {
        // Don't reset if mission is already running or completed
        val currentPhase = _uiState.value.phase
        if (currentPhase == MissionPhase.COUNTDOWN ||
            currentPhase == MissionPhase.COLLECTING ||
            currentPhase == MissionPhase.COMPLETE) {
            return
        }

        _uiState.value = _uiState.value.copy(
            mission = mission,
            remainingSeconds = mission.durationSeconds,
            phase = MissionPhase.READY
        )
    }

    fun startMission() {
        val mission = _uiState.value.mission ?: return

        _uiState.value = _uiState.value.copy(
            phase = MissionPhase.COUNTDOWN,
            countdownValue = 3
        )

        missionJob = viewModelScope.launch {
            // 3-2-1 countdown
            for (i in 3 downTo 1) {
                _uiState.value = _uiState.value.copy(countdownValue = i)
                delay(1000)
            }

            // Start collecting
            startCollection(mission)
        }
    }

    private suspend fun startCollection(mission: Mission) {
        // Create session
        val session = TrainingSession.start(mission.activityType, mission.durationSeconds)
        currentSessionId = repository.insertSession(session)
        collectedSamples.clear()

        // Start sensors
        accelerometerManager.start()
        gyroscopeManager.start()

        _uiState.value = _uiState.value.copy(
            phase = MissionPhase.COLLECTING,
            remainingSeconds = mission.durationSeconds,
            samplesCollected = 0
        )

        // Collection loop
        val startTime = System.currentTimeMillis()
        val endTime = startTime + (mission.durationSeconds * 1000)

        while (System.currentTimeMillis() < endTime && missionJob?.isActive == true) {
            collectSample(mission.activityType)

            val elapsed = System.currentTimeMillis() - startTime
            val remaining = ((endTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)

            _uiState.value = _uiState.value.copy(
                remainingSeconds = remaining,
                samplesCollected = collectedSamples.size,
                progress = elapsed.toFloat() / (mission.durationSeconds * 1000)
            )

            delay(20) // ~50Hz sampling
        }

        // Stop sensors
        accelerometerManager.stop()
        gyroscopeManager.stop()

        // Save samples
        if (collectedSamples.isNotEmpty()) {
            repository.insertSamples(collectedSamples)

            // Update session
            val completedSession = session.copy(
                id = currentSessionId,
                endTime = System.currentTimeMillis(),
                samplesCount = collectedSamples.size,
                completed = true
            )
            repository.updateSession(completedSession)
        }

        _uiState.value = _uiState.value.copy(
            phase = MissionPhase.COMPLETE,
            samplesCollected = collectedSamples.size
        )
    }

    private fun collectSample(activityType: ActivityType) {
        val accel = accelerometerManager.latestReading.value ?: return
        val gyro = gyroscopeManager.latestReading.value ?: return

        val sample = TrainingSample.create(
            activityType = activityType,
            accX = accel.x,
            accY = accel.y,
            accZ = accel.z,
            gyroX = gyro.x,
            gyroY = gyro.y,
            gyroZ = gyro.z,
            sessionId = currentSessionId
        )
        collectedSamples.add(sample)
    }

    fun cancelMission() {
        missionJob?.cancel()
        missionJob = null

        accelerometerManager.stop()
        gyroscopeManager.stop()

        // Delete partial samples if any
        if (currentSessionId > 0 && collectedSamples.isNotEmpty()) {
            viewModelScope.launch {
                repository.deleteSamplesBySession(currentSessionId)
            }
        }

        collectedSamples.clear()
        _uiState.value = _uiState.value.copy(phase = MissionPhase.CANCELLED)
    }

    fun reset() {
        _uiState.value = ActiveMissionUiState()
        collectedSamples.clear()
        currentSessionId = 0
    }

    override fun onCleared() {
        super.onCleared()
        missionJob?.cancel()
        accelerometerManager.stop()
        gyroscopeManager.stop()
    }
}

/**
 * Mission execution phase.
 */
enum class MissionPhase {
    READY,      // Mission selected, waiting to start
    COUNTDOWN,  // 3-2-1 countdown
    COLLECTING, // Actively collecting data
    COMPLETE,   // Mission finished successfully
    CANCELLED   // Mission was cancelled
}

/**
 * UI State for active mission.
 */
data class ActiveMissionUiState(
    val mission: Mission? = null,
    val phase: MissionPhase = MissionPhase.READY,
    val countdownValue: Int = 3,
    val remainingSeconds: Int = 0,
    val samplesCollected: Int = 0,
    val progress: Float = 0f
)
