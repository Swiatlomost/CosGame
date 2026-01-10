package com.cosgame.costrack.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.state.AppMode
import com.cosgame.costrack.state.AppStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val appStateManager = AppStateManager.getInstance(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeAppState()
    }

    private fun observeAppState() {
        viewModelScope.launch {
            appStateManager.appMode.collect { mode ->
                _uiState.value = _uiState.value.copy(appMode = mode)
            }
        }
    }

    fun updateSensorStatus(active: Boolean) {
        _uiState.value = _uiState.value.copy(sensorsActive = active)
    }

    fun updateClassifierStatus(ready: Boolean) {
        _uiState.value = _uiState.value.copy(classifierReady = ready)
    }

    fun updateCurrentActivity(activity: String?) {
        _uiState.value = _uiState.value.copy(currentActivity = activity)
    }
}

/**
 * UI State for Home screen.
 */
data class HomeUiState(
    val appMode: AppMode = AppMode.USER,
    val sensorsActive: Boolean = false,
    val classifierReady: Boolean = false,
    val currentActivity: String? = null
) {
    val isDevMode: Boolean get() = appMode == AppMode.DEVELOPER
}
