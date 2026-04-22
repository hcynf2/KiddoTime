package com.kiddotime.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiddotime.app.data.AppDatabase
import com.kiddotime.app.data.LimitEventRepository
import com.kiddotime.app.data.StarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class ChildUiState(
    val todayOnTimeStops: Int = 0,
    val todayTotalStops: Int = 0,
    val streakDays: Int = 0,
    val starBalance: Int = 0,   // populated in Part B; 0 until then
    val isLoading: Boolean = true
)

class ChildViewModel(application: Application) : AndroidViewModel(application) {

    private val limitEventRepo = LimitEventRepository(
        AppDatabase.getDatabase(application).limitEventDao()
    )
    private val starRepo = StarRepository(application)

    private val _uiState = MutableStateFlow(ChildUiState())
    val uiState: StateFlow<ChildUiState> = _uiState

    init {
        // Reload stats automatically whenever a limit event is inserted or updated.
        viewModelScope.launch {
            limitEventRepo.observeChanges()
                .distinctUntilChanged()
                .collect { loadStats() }
        }
    }

    private suspend fun loadStats() {
        val todayStats = limitEventRepo.getTodayStats()
        val streak     = limitEventRepo.computeCurrentSmoothStopStreak()
        _uiState.value = ChildUiState(
            todayOnTimeStops = todayStats.onTime,
            todayTotalStops  = todayStats.total,
            streakDays       = streak,
            starBalance      = starRepo.balance,
            isLoading        = false
        )
    }
}