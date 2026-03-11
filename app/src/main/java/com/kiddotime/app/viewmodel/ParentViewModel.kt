package com.kiddotime.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiddotime.app.data.AppUsageInfo
import com.kiddotime.app.data.UsageStatsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ParentUiState(
    val hasPermission: Boolean = false,
    val usageStats: List<AppUsageInfo> = emptyList(),
    val isLoading: Boolean = false
)

class ParentViewModel(application: Application) : AndroidViewModel(application) {

    private val usageStatsHelper = UsageStatsHelper(application)

    private val _uiState = MutableStateFlow(ParentUiState())
    val uiState: StateFlow<ParentUiState> = _uiState

    fun checkPermissionAndLoad() {
        viewModelScope.launch {
            val hasPermission = usageStatsHelper.hasUsagePermission()
            if (hasPermission) {
                loadUsageStats()
            } else {
                _uiState.value = _uiState.value.copy(hasPermission = false)
            }
        }
    }

    fun loadUsageStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val stats = usageStatsHelper.getTodayUsageStats()
            _uiState.value = _uiState.value.copy(
                hasPermission = true,
                usageStats = stats,
                isLoading = false
            )
        }
    }

    fun formatDuration(ms: Long): String {
        return usageStatsHelper.formatDuration(ms)
    }
}