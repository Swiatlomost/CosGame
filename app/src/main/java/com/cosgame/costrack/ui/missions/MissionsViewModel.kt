package com.cosgame.costrack.ui.missions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.training.ActivityType
import com.cosgame.costrack.training.TrainingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for the Missions list screen.
 */
class MissionsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrainingRepository.getInstance(application)

    private val _uiState = MutableStateFlow(MissionsUiState())
    val uiState: StateFlow<MissionsUiState> = _uiState.asStateFlow()

    init {
        observeSampleCounts()
        loadCompletionCounts()
    }

    private fun observeSampleCounts() {
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

    private fun loadCompletionCounts() {
        viewModelScope.launch {
            val completionCounts = mutableMapOf<ActivityType, Int>()
            ActivityType.values().forEach { activityType ->
                completionCounts[activityType] = repository.getCompletedCountByActivity(activityType)
            }
            _uiState.value = _uiState.value.copy(completionCounts = completionCounts)
        }
    }

    fun refresh() {
        loadCompletionCounts()
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _uiState.value = _uiState.value.copy(
                sampleCounts = emptyMap(),
                completionCounts = emptyMap(),
                totalSamples = 0
            )
        }
    }
}

/**
 * UI State for Missions screen.
 */
data class MissionsUiState(
    val missions: List<Mission> = Missions.ALL,
    val sampleCounts: Map<ActivityType, Int> = emptyMap(),
    val completionCounts: Map<ActivityType, Int> = emptyMap(),
    val totalSamples: Int = 0
) {
    fun getSampleCount(activityType: ActivityType): Int = sampleCounts[activityType] ?: 0

    fun getCompletionCount(activityType: ActivityType): Int = completionCounts[activityType] ?: 0

    fun getProgress(mission: Mission): Float {
        val count = getSampleCount(mission.activityType)
        return (count.toFloat() / mission.minSamplesRequired).coerceIn(0f, 1f)
    }

    fun isReadyForTraining(minPerClass: Int = 100): Boolean {
        return ActivityType.values().all { (sampleCounts[it] ?: 0) >= minPerClass }
    }
}
