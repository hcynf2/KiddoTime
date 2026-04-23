package com.kiddotime.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiddotime.app.data.AppDatabase
import com.kiddotime.app.data.AppLimit
import com.kiddotime.app.data.AppLimitRepository
import com.kiddotime.app.data.BadgeId
import com.kiddotime.app.data.BadgeRepository
import com.kiddotime.app.data.LimitEventRepository
import com.kiddotime.app.data.ScreenTimeRequestRepository
import com.kiddotime.app.data.StarRepository
import com.kiddotime.app.data.TimeRequestPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChildUiState(
    val todayOnTimeStops: Int = 0,
    val todayTotalStops: Int = 0,
    val streakDays: Int = 0,
    val starBalance: Int = 0,
    val isLoading: Boolean = true,
    val showCelebration: Boolean = false,
    val earnedBadges: List<BadgeId> = emptyList(),
    val newBadges: List<BadgeId> = emptyList(),
    val limitedApps: List<AppLimit> = emptyList(),
    val requestsEnabled: Boolean = true,
    val todayRequestCount: Int = 0
)

class ChildViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val limitEventRepo = LimitEventRepository(db.limitEventDao())
    private val starRepo = StarRepository(application)
    private val badgeRepo = BadgeRepository(application)
    private val appLimitRepo = AppLimitRepository(db.appLimitDao())
    private val requestRepo = ScreenTimeRequestRepository(db.screenTimeRequestDao())
    private val timeRequestPrefs = TimeRequestPreferences(application)

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
        val todayStats      = limitEventRepo.getTodayStats()
        val streak          = limitEventRepo.computeCurrentSmoothStopStreak()
        val balance         = starRepo.balance
        val celebration     = starRepo.pendingCelebration
        val requestsEnabled = timeRequestPrefs.enabled
        val todayReqCount   = requestRepo.countTodayRequests()

        badgeRepo.evaluate(limitEventRepo, balance)

        val limitedApps = appLimitRepo.allLimits.first()

        _uiState.value = ChildUiState(
            todayOnTimeStops  = todayStats.onTime,
            todayTotalStops   = todayStats.total,
            streakDays        = streak,
            starBalance       = balance,
            isLoading         = false,
            showCelebration   = celebration,
            earnedBadges      = badgeRepo.getEarnedBadges(),
            newBadges         = badgeRepo.getNewBadges(),
            limitedApps       = limitedApps,
            requestsEnabled   = requestsEnabled,
            todayRequestCount = todayReqCount
        )
    }

    fun clearCelebration() {
        starRepo.pendingCelebration = false
        _uiState.value = _uiState.value.copy(showCelebration = false)
    }

    fun markBadgesSeen() {
        badgeRepo.markAllSeen()
        _uiState.value = _uiState.value.copy(newBadges = emptyList())
    }

    fun submitRequest(packageName: String, appName: String) {
        viewModelScope.launch {
            if (!timeRequestPrefs.enabled) return@launch
            if (requestRepo.countTodayRequests() >= 1) return@launch
            requestRepo.submitRequest(packageName, appName)
            _uiState.value = _uiState.value.copy(todayRequestCount = _uiState.value.todayRequestCount + 1)
        }
    }
}