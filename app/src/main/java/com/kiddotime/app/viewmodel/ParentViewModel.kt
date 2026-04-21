package com.kiddotime.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiddotime.app.data.AppDatabase
import com.kiddotime.app.data.AppLimit
import com.kiddotime.app.data.AppLimitRepository
import com.kiddotime.app.data.AppUsageInfo
import com.kiddotime.app.data.PinRepository
import com.kiddotime.app.data.UsageStatsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AppUsageWithLimit(
    val usageInfo: AppUsageInfo,
    val limit: AppLimit?           // null means no limit set
)

data class ParentUiState(
    val hasPermission: Boolean = false,
    val appsWithLimits: List<AppUsageWithLimit> = emptyList(),
    val isLoading: Boolean = false,
    val hasPin: Boolean = false,
    val pinError: String? = null,
    val pinSaved: Boolean = false
)

class ParentViewModel(application: Application) : AndroidViewModel(application) {

    private val usageStatsHelper = UsageStatsHelper(application)
    private val repository = AppLimitRepository(
        AppDatabase.getDatabase(application).appLimitDao()
    )

    private val _uiState = MutableStateFlow(ParentUiState())
    val uiState: StateFlow<ParentUiState> = _uiState

    private val pinRepository = PinRepository(application)
    fun checkPermissionAndLoad() {
        viewModelScope.launch {
            val hasPermission = usageStatsHelper.hasUsagePermission()
            Log.d("KiddoTime", "Has permission: $hasPermission")
            if (hasPermission) {
                loadUsageStats()
            } else {
                Log.d("KiddoTime", "Permission not granted - showing prompt")
                _uiState.value = _uiState.value.copy(hasPermission = false)
            }
            _uiState.value = _uiState.value.copy(hasPin = pinRepository.hasPin())
        }
    }

    fun savePin(pin: String) {
        if (pin.length < 4){
            _uiState.value = _uiState.value.copy(pinError = "Pin must be at least 4 digits.")
            return
        }
        pinRepository.savePin(pin)
        _uiState.value = _uiState.value.copy(
            hasPin = true,
            pinError = null,
            pinSaved = true
        )

        // reset pinSaved after a moment
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.value = _uiState.value.copy(pinSaved = false)
        }
    }

    fun clearPin(){
        pinRepository.clearPin()
        _uiState.value = _uiState.value.copy(hasPin = false)
    }

    fun verifyPin(input: String): Boolean {
        return pinRepository.verifyPin(input)
    }

    private fun loadUsageStats() {
        viewModelScope.launch {
            Log.d("KiddoTime", "loadUsageStats() called")
            _uiState.value = _uiState.value.copy(isLoading = true)
            val stats = usageStatsHelper.getTodayUsageStats()
            Log.d("KiddoTime", "Stats returned: ${stats.size} apps")

            // Combine live usage stats with live limits from database
            repository.allLimits.collect { limits ->
                Log.d("KiddoTime", "Limits loaded: ${limits.size}")
                val limitsMap = limits.associateBy { it.packageName }
                val appsWithLimits = stats.map { appInfo ->
                    AppUsageWithLimit(
                        usageInfo = appInfo,
                        limit = limitsMap[appInfo.packageName]
                    )
                }
                _uiState.value = _uiState.value.copy(
                    hasPermission = true,
                    appsWithLimits = appsWithLimits,
                    isLoading = false
                )
            }
        }
    }

    fun setLimit(packageName: String, appName: String, limitMs: Long) {
        viewModelScope.launch {
            repository.setLimit(packageName, appName, limitMs)
        }
    }

    fun removeLimit(packageName: String, appName: String) {
        viewModelScope.launch {
            repository.removeLimit(packageName, appName)
        }
    }

    fun formatDuration(ms: Long): String {
        return usageStatsHelper.formatDuration(ms)
    }
}