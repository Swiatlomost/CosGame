package com.cosgame.costrack.state

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages application-wide state including User/Developer mode.
 * Persists state to SharedPreferences.
 */
class AppStateManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _appMode = MutableStateFlow(loadMode())
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private val _devModeUnlocked = MutableStateFlow(loadDevModeUnlocked())
    val devModeUnlocked: StateFlow<Boolean> = _devModeUnlocked.asStateFlow()

    // Counter for dev mode unlock gesture (e.g., tap 7 times)
    private var unlockTapCount = 0
    private var lastTapTime = 0L

    /**
     * Current app mode.
     */
    val currentMode: AppMode get() = _appMode.value

    /**
     * Whether developer mode is currently active.
     */
    val isDevMode: Boolean get() = _appMode.value == AppMode.DEVELOPER

    /**
     * Whether user mode is currently active.
     */
    val isUserMode: Boolean get() = _appMode.value == AppMode.USER

    /**
     * Switch to the specified mode.
     * Developer mode requires it to be unlocked first.
     */
    fun setMode(mode: AppMode): Boolean {
        if (mode == AppMode.DEVELOPER && !_devModeUnlocked.value) {
            return false
        }
        _appMode.value = mode
        saveMode(mode)
        return true
    }

    /**
     * Toggle between User and Developer mode.
     */
    fun toggleMode(): Boolean {
        val newMode = if (_appMode.value == AppMode.USER) {
            AppMode.DEVELOPER
        } else {
            AppMode.USER
        }
        return setMode(newMode)
    }

    /**
     * Check if a feature is available in the current mode.
     */
    fun isFeatureAvailable(feature: ModeFeature): Boolean {
        return feature.isAvailable(_appMode.value)
    }

    /**
     * Register a tap for the dev mode unlock gesture.
     * Returns true if developer mode was unlocked.
     */
    fun registerUnlockTap(): Boolean {
        val now = System.currentTimeMillis()

        // Reset if too much time passed
        if (now - lastTapTime > TAP_TIMEOUT_MS) {
            unlockTapCount = 0
        }

        lastTapTime = now
        unlockTapCount++

        if (unlockTapCount >= TAPS_TO_UNLOCK) {
            unlockDevMode()
            unlockTapCount = 0
            return true
        }

        return false
    }

    /**
     * Get remaining taps needed to unlock dev mode.
     */
    fun getRemainingTapsToUnlock(): Int {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > TAP_TIMEOUT_MS) {
            return TAPS_TO_UNLOCK
        }
        return (TAPS_TO_UNLOCK - unlockTapCount).coerceAtLeast(0)
    }

    /**
     * Unlock developer mode permanently.
     */
    fun unlockDevMode() {
        _devModeUnlocked.value = true
        saveDevModeUnlocked(true)
    }

    /**
     * Lock developer mode (for testing or reset).
     */
    fun lockDevMode() {
        if (_appMode.value == AppMode.DEVELOPER) {
            setMode(AppMode.USER)
        }
        _devModeUnlocked.value = false
        saveDevModeUnlocked(false)
        unlockTapCount = 0
    }

    /**
     * Reset all state to defaults.
     */
    fun reset() {
        _appMode.value = AppMode.DEFAULT
        _devModeUnlocked.value = false
        unlockTapCount = 0
        prefs.edit().clear().apply()
    }

    private fun loadMode(): AppMode {
        val modeString = prefs.getString(KEY_APP_MODE, AppMode.DEFAULT.name) ?: AppMode.DEFAULT.name
        return AppMode.fromString(modeString)
    }

    private fun saveMode(mode: AppMode) {
        prefs.edit().putString(KEY_APP_MODE, mode.name).apply()
    }

    private fun loadDevModeUnlocked(): Boolean {
        return prefs.getBoolean(KEY_DEV_UNLOCKED, false)
    }

    private fun saveDevModeUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean(KEY_DEV_UNLOCKED, unlocked).apply()
    }

    companion object {
        private const val PREFS_NAME = "cosgame_app_state"
        private const val KEY_APP_MODE = "app_mode"
        private const val KEY_DEV_UNLOCKED = "dev_mode_unlocked"

        const val TAPS_TO_UNLOCK = 7
        const val TAP_TIMEOUT_MS = 3000L

        @Volatile
        private var instance: AppStateManager? = null

        fun getInstance(context: Context): AppStateManager {
            return instance ?: synchronized(this) {
                instance ?: AppStateManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}

/**
 * Extension to check if current mode allows a feature.
 */
fun AppStateManager.canAccess(feature: ModeFeature): Boolean = isFeatureAvailable(feature)
