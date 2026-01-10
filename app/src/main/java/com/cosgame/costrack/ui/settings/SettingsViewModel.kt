package com.cosgame.costrack.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.sensor.SensorConfig
import com.cosgame.costrack.state.AppMode
import com.cosgame.costrack.state.AppStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 * Manages app settings and developer options.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appStateManager = AppStateManager.getInstance(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeAppState()
    }

    private fun observeAppState() {
        viewModelScope.launch {
            appStateManager.appMode.collect { mode ->
                _uiState.value = _uiState.value.copy(appMode = mode)
            }
        }

        viewModelScope.launch {
            appStateManager.devModeUnlocked.collect { unlocked ->
                _uiState.value = _uiState.value.copy(devModeUnlocked = unlocked)
            }
        }
    }

    /**
     * Handle tap on version/build number for dev mode unlock.
     */
    fun onVersionTap(): UnlockResult {
        if (_uiState.value.devModeUnlocked) {
            return UnlockResult.AlreadyUnlocked
        }

        val unlocked = appStateManager.registerUnlockTap()
        val remaining = appStateManager.getRemainingTapsToUnlock()

        return if (unlocked) {
            UnlockResult.JustUnlocked
        } else {
            UnlockResult.TapsRemaining(remaining)
        }
    }

    /**
     * Toggle between User and Developer mode.
     */
    fun toggleDevMode() {
        appStateManager.toggleMode()
    }

    /**
     * Set specific mode.
     */
    fun setMode(mode: AppMode) {
        appStateManager.setMode(mode)
    }

    /**
     * Lock developer mode (remove access).
     */
    fun lockDevMode() {
        appStateManager.lockDevMode()
    }

    /**
     * Reset all settings to defaults.
     */
    fun resetAllSettings() {
        appStateManager.reset()
        _uiState.value = _uiState.value.copy(
            showResetConfirmation = false
        )
    }

    fun showResetConfirmation() {
        _uiState.value = _uiState.value.copy(showResetConfirmation = true)
    }

    fun hideResetConfirmation() {
        _uiState.value = _uiState.value.copy(showResetConfirmation = false)
    }

    fun setSamplingRate(rate: SamplingRate) {
        _uiState.value = _uiState.value.copy(selectedSamplingRate = rate)
        // In a full implementation, this would update the sensor config
    }
}

/**
 * UI State for Settings screen.
 */
data class SettingsUiState(
    val appMode: AppMode = AppMode.USER,
    val devModeUnlocked: Boolean = false,
    val selectedSamplingRate: SamplingRate = SamplingRate.NORMAL,
    val showResetConfirmation: Boolean = false
) {
    val isDevMode: Boolean get() = appMode == AppMode.DEVELOPER
    val canAccessDevOptions: Boolean get() = devModeUnlocked
}

/**
 * Result of unlock tap gesture.
 */
sealed class UnlockResult {
    object AlreadyUnlocked : UnlockResult()
    object JustUnlocked : UnlockResult()
    data class TapsRemaining(val count: Int) : UnlockResult()
}

/**
 * Sampling rate options.
 */
enum class SamplingRate(val hz: Int, val label: String) {
    LOW_POWER(25, "25 Hz (Battery Saver)"),
    NORMAL(50, "50 Hz (Normal)"),
    HIGH(100, "100 Hz (High Accuracy)")
}
