package com.cosgame.costrack.ui.sensors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosgame.costrack.sensor.AccelerometerManager
import com.cosgame.costrack.sensor.GyroscopeManager
import com.cosgame.costrack.sensor.SensorConfig
import com.cosgame.costrack.sensor.SensorReading
import com.cosgame.costrack.state.AppMode
import com.cosgame.costrack.state.AppStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for the Sensors screen.
 * Manages sensor state and provides data for both User and Developer modes.
 */
class SensorsViewModel(application: Application) : AndroidViewModel(application) {

    private val appStateManager = AppStateManager.getInstance(application)

    private val config = SensorConfig.DEFAULT
    val accelerometerManager = AccelerometerManager(application, config)
    val gyroscopeManager = GyroscopeManager(application, config)

    private val _uiState = MutableStateFlow(SensorsUiState())
    val uiState: StateFlow<SensorsUiState> = _uiState.asStateFlow()

    init {
        observeSensorData()
        observeAppMode()
    }

    private fun observeAppMode() {
        viewModelScope.launch {
            appStateManager.appMode.collect { mode ->
                _uiState.value = _uiState.value.copy(appMode = mode)
            }
        }
    }

    private fun observeSensorData() {
        viewModelScope.launch {
            accelerometerManager.latestReading.collect { reading ->
                _uiState.value = _uiState.value.copy(
                    accelerometerReading = reading,
                    accelerometerBufferFill = accelerometerManager.getBufferFillRatio(),
                    accelerometerSampleCount = accelerometerManager.getReadingCount()
                )
            }
        }

        viewModelScope.launch {
            gyroscopeManager.latestReading.collect { reading ->
                _uiState.value = _uiState.value.copy(
                    gyroscopeReading = reading,
                    gyroscopeBufferFill = gyroscopeManager.getBufferFillRatio(),
                    gyroscopeSampleCount = gyroscopeManager.getReadingCount()
                )
            }
        }

        viewModelScope.launch {
            accelerometerManager.isActive.collect { active ->
                _uiState.value = _uiState.value.copy(accelerometerEnabled = active)
            }
        }

        viewModelScope.launch {
            gyroscopeManager.isActive.collect { active ->
                _uiState.value = _uiState.value.copy(gyroscopeEnabled = active)
            }
        }
    }

    fun toggleAccelerometer() {
        if (accelerometerManager.isActive.value) {
            accelerometerManager.stop()
        } else {
            accelerometerManager.start()
        }
    }

    fun toggleGyroscope() {
        if (gyroscopeManager.isActive.value) {
            gyroscopeManager.stop()
        } else {
            gyroscopeManager.start()
        }
    }

    fun startAllSensors() {
        accelerometerManager.start()
        gyroscopeManager.start()
    }

    fun stopAllSensors() {
        accelerometerManager.stop()
        gyroscopeManager.stop()
    }

    fun getSamplingFrequency(): Int = config.samplingFrequencyHz

    fun getAccelerometerInfo() = accelerometerManager.getSensorInfo()
    fun getGyroscopeInfo() = gyroscopeManager.getSensorInfo()

    override fun onCleared() {
        super.onCleared()
        stopAllSensors()
    }
}

/**
 * UI State for Sensors screen.
 */
data class SensorsUiState(
    val appMode: AppMode = AppMode.USER,

    // Accelerometer
    val accelerometerEnabled: Boolean = false,
    val accelerometerAvailable: Boolean = true,
    val accelerometerReading: SensorReading? = null,
    val accelerometerBufferFill: Float = 0f,
    val accelerometerSampleCount: Long = 0,

    // Gyroscope
    val gyroscopeEnabled: Boolean = false,
    val gyroscopeAvailable: Boolean = true,
    val gyroscopeReading: SensorReading? = null,
    val gyroscopeBufferFill: Float = 0f,
    val gyroscopeSampleCount: Long = 0
) {
    val isDevMode: Boolean get() = appMode == AppMode.DEVELOPER
    val anySensorActive: Boolean get() = accelerometerEnabled || gyroscopeEnabled
    val allSensorsActive: Boolean get() = accelerometerEnabled && gyroscopeEnabled
}
