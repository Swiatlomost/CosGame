package com.cosgame.costrack.ui.missions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.training.ActivityType
import com.cosgame.costrack.training.PersonalHarTrainer
import com.cosgame.costrack.training.TrainingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the training screen.
 */
class TrainingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrainingRepository.getInstance(application)
    private val trainer = PersonalHarTrainer(application)

    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    init {
        loadStats()
        checkModelExists()
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
        _uiState.value = _uiState.value.copy(
            hasTrainedModel = trainer.hasTrainedModel()
        )
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
                error = result.error
            )
        }
    }

    fun deleteModel() {
        trainer.deleteModel()
        _uiState.value = _uiState.value.copy(
            hasTrainedModel = false,
            trainResult = null,
            currentAccuracy = 0f
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
    val error: String? = null
) {
    val minSamplesPerClass = 100

    fun getSampleCount(activityType: ActivityType): Int = sampleCounts[activityType] ?: 0

    fun isReadyForTraining(): Boolean {
        return ActivityType.values().all { getSampleCount(it) >= minSamplesPerClass }
    }

    fun getMissingActivities(): List<ActivityType> {
        return ActivityType.values().filter { getSampleCount(it) < minSamplesPerClass }
    }
}
