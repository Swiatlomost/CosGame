package com.cosgame.costrack.ui.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.training.ActivityType
import com.cosgame.costrack.training.PersonalHarTrainer
import com.cosgame.costrack.training.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the training screen.
 */
@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val trainer: PersonalHarTrainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    init {
        loadStats()
        checkModelExists()
        loadNewSamplesStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            repository.getSampleCountsPerActivityFlow().collect { counts ->
                val countsMap = counts.associate { it.activityType to it.count }
                _uiState.value = _uiState.value.copy(
                    sampleCounts = countsMap,
                    totalSamples = countsMap.values.sum()
                )
            }
        }
    }

    private fun checkModelExists() {
        val hasModel = trainer.hasTrainedModel()
        val lastTrainingTime = trainer.getLastTrainingTimestamp()
        _uiState.value = _uiState.value.copy(
            hasTrainedModel = hasModel,
            lastTrainingTimestamp = lastTrainingTime
        )
    }

    private fun loadNewSamplesStats() {
        viewModelScope.launch {
            val lastTrainingTime = trainer.getLastTrainingTimestamp()
            if (lastTrainingTime > 0) {
                repository.getNewSampleCountsPerActivityFlow(lastTrainingTime).collect { counts ->
                    val newCountsMap = counts.associate { it.activityType to it.count }
                    _uiState.value = _uiState.value.copy(
                        newSampleCounts = newCountsMap,
                        totalNewSamples = newCountsMap.values.sum()
                    )
                }
            }
        }
    }

    fun startTraining() {
        if (_uiState.value.isTraining) return

        _uiState.value = _uiState.value.copy(
            isTraining = true,
            trainingProgress = 0f,
            currentAccuracy = 0f,
            error = null
        )

        viewModelScope.launch {
            val result = trainer.train(
                epochs = 100,
                learningRate = 0.01f,
                validationSplit = 0.2f
            ) { progress, accuracy ->
                _uiState.value = _uiState.value.copy(
                    trainingProgress = progress,
                    currentAccuracy = accuracy
                )
            }

            _uiState.value = _uiState.value.copy(
                isTraining = false,
                trainingProgress = 1f,
                trainResult = result,
                hasTrainedModel = result.success,
                currentAccuracy = result.accuracy,
                error = result.error,
                // Reset new samples counter after training
                newSampleCounts = emptyMap(),
                totalNewSamples = 0,
                lastTrainingTimestamp = if (result.success) System.currentTimeMillis() else _uiState.value.lastTrainingTimestamp
            )
        }
    }

    fun deleteModel() {
        trainer.deleteModel()
        _uiState.value = _uiState.value.copy(
            hasTrainedModel = false,
            trainResult = null,
            currentAccuracy = 0f,
            newSampleCounts = emptyMap(),
            totalNewSamples = 0,
            lastTrainingTimestamp = 0
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * UI State for training screen.
 */
data class TrainingUiState(
    val sampleCounts: Map<ActivityType, Int> = emptyMap(),
    val totalSamples: Int = 0,
    val isTraining: Boolean = false,
    val trainingProgress: Float = 0f,
    val currentAccuracy: Float = 0f,
    val trainResult: PersonalHarTrainer.TrainResult? = null,
    val hasTrainedModel: Boolean = false,
    val error: String? = null,
    // New samples since last training
    val newSampleCounts: Map<ActivityType, Int> = emptyMap(),
    val totalNewSamples: Int = 0,
    val lastTrainingTimestamp: Long = 0
) {
    val minSamplesPerClass = 100

    fun getSampleCount(activityType: ActivityType): Int = sampleCounts[activityType] ?: 0

    fun getNewSampleCount(activityType: ActivityType): Int = newSampleCounts[activityType] ?: 0

    fun isReadyForTraining(): Boolean {
        return ActivityType.entries.all { getSampleCount(it) >= minSamplesPerClass }
    }

    fun getMissingActivities(): List<ActivityType> {
        return ActivityType.entries.filter { getSampleCount(it) < minSamplesPerClass }
    }

    fun hasNewSamples(): Boolean = totalNewSamples > 0
}
