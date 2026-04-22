package com.kiddotime.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiddotime.app.data.AppDatabase
import com.kiddotime.app.data.AppLimit
import com.kiddotime.app.data.AppLimitRepository
import com.kiddotime.app.data.AppUsageInfo
import com.kiddotime.app.data.BedtimeRepository
import com.kiddotime.app.data.LimitEventRepository
import com.kiddotime.app.data.PinRepository
import com.kiddotime.app.data.UsageStatsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppUsageWithLimit(
    val usageInfo: AppUsageInfo,
    val limit: AppLimit?           // null means no limit set
)

data class DashboardStats(
    // Overview
    val totalScreenTimeToday: Long = 0L,
    val appsUsedToday: Int = 0,
    val totalInstalledApps: Int = 0,
    val todayVsYesterdayMs: Long = 0L,   // positive = more today, negative = less
    val dailyAverageMs: Long = 0L,       // 7-day average total daily screen time
    // Top usage
    val topAppsToday: List<AppUsageWithLimit> = emptyList(),   // top 5 by today usage
    val weeklyTopAppName: String = "",
    val weeklyTopAppMs: Long = 0L,
    val longestSessionAppName: String = "",
    val longestSessionMs: Long = 0L,
    // Limits
    val totalAppsWithLimits: Int = 0,
    val appsWithLimitsList: List<AppUsageWithLimit> = emptyList(),
    val appsNearingLimit: List<AppUsageWithLimit> = emptyList(),   // 75–99 %
    val appsExceededLimit: List<AppUsageWithLimit> = emptyList(),  // ≥ 100 %
    // Behavioural metrics (require at least one recorded limit event)
    val avgTransitionLatencyMs: Long? = null,   // null = no data yet
    val hardestAppName: String = "",            // most limit hits in last 7 days
    val hardestAppHitCount: Int = 0,
    val smoothStopStreakDays: Int = 0           // consecutive days with ≥70% on-time stops
)

data class BedtimeState(
    val isEnabled: Boolean = false,
    val hour: Int = 21,
    val minute: Int = 0,
    val selectedApps: Set<String> = emptySet()
)

data class ParentUiState(
    val hasPermission: Boolean = false,
    val appsWithLimits: List<AppUsageWithLimit> = emptyList(),
    val isLoading: Boolean = false,
    val hasPin: Boolean = false,
    val pinError: String? = null,
    val pinSaved: Boolean = false,
    val dashboardStats: DashboardStats? = null,
    val bedtimeState: BedtimeState = BedtimeState()
)

class ParentViewModel(application: Application) : AndroidViewModel(application) {

    private val usageStatsHelper = UsageStatsHelper(application)
    private val db = AppDatabase.getDatabase(application)
    private val repository = AppLimitRepository(db.appLimitDao())
    private val limitEventRepo = LimitEventRepository(db.limitEventDao())

    private val _uiState = MutableStateFlow(ParentUiState())
    val uiState: StateFlow<ParentUiState> = _uiState

    private val pinRepository = PinRepository(application)
    private val bedtimeRepository = BedtimeRepository(application)

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
            _uiState.value = _uiState.value.copy(
                hasPin = pinRepository.hasPin(),
                bedtimeState = BedtimeState(
                    isEnabled    = bedtimeRepository.isEnabled,
                    hour         = bedtimeRepository.hour,
                    minute       = bedtimeRepository.minute,
                    selectedApps = bedtimeRepository.getApps()
                )
            )
        }
    }

    fun saveBedtime(isEnabled: Boolean, hour: Int, minute: Int, selectedApps: Set<String>) {
        bedtimeRepository.isEnabled    = isEnabled
        bedtimeRepository.hour         = hour
        bedtimeRepository.minute       = minute
        bedtimeRepository.setApps(selectedApps)
        _uiState.value = _uiState.value.copy(
            bedtimeState = BedtimeState(isEnabled, hour, minute, selectedApps)
        )
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

            // One-shot loads that don't depend on the limits Flow
            val stats            = usageStatsHelper.getTodayUsageStats()
            val yesterdayMs      = usageStatsHelper.getYesterdayTotal()
            val dailyAvgMs       = usageStatsHelper.get7DayDailyAverage()
            val longestSession   = usageStatsHelper.getLongestSessionToday()
            val weeklyTop        = usageStatsHelper.getWeeklyTopApp()
            val avgLatencyMs     = limitEventRepo.getAvgTransitionLatencyMs()
            val hardestApp       = limitEventRepo.getHardestApp()
            val streakDays       = limitEventRepo.computeCurrentSmoothStopStreak()
            Log.d("KiddoTime", "Stats returned: ${stats.size} apps")

            // Re-compute whenever the limits table changes
            repository.allLimits.collect { limits ->
                Log.d("KiddoTime", "Limits loaded: ${limits.size}")
                val limitsMap = limits.associateBy { it.packageName }
                val appsWithLimits = stats.map { appInfo ->
                    AppUsageWithLimit(usageInfo = appInfo, limit = limitsMap[appInfo.packageName])
                }

                val todayTotal   = stats.sumOf { it.totalTimeMs }
                val topApps      = appsWithLimits.filter { it.usageInfo.totalTimeMs > 0 }.take(5)
                val limitedApps  = appsWithLimits.filter { it.limit != null }
                val appsNearing  = limitedApps.filter { awl ->
                    val pct = awl.usageInfo.totalTimeMs.toDouble() / awl.limit!!.dailyLimitMs
                    pct in 0.75..<1.0
                }
                val appsExceeded = limitedApps.filter { awl ->
                    awl.usageInfo.totalTimeMs >= awl.limit!!.dailyLimitMs
                }

                val weeklyTopName = weeklyTop?.let { (pkg, _) ->
                    stats.find { it.packageName == pkg }?.appName ?: pkg
                } ?: ""
                val longestName = longestSession?.let { (pkg, _) ->
                    stats.find { it.packageName == pkg }?.appName ?: pkg
                } ?: ""

                val dashboardStats = DashboardStats(
                    totalScreenTimeToday  = todayTotal,
                    appsUsedToday         = stats.count { it.totalTimeMs > 0 },
                    totalInstalledApps    = stats.size,
                    todayVsYesterdayMs    = todayTotal - yesterdayMs,
                    dailyAverageMs        = dailyAvgMs,
                    topAppsToday          = topApps,
                    weeklyTopAppName      = weeklyTopName,
                    weeklyTopAppMs        = weeklyTop?.second ?: 0L,
                    longestSessionAppName = longestName,
                    longestSessionMs      = longestSession?.second ?: 0L,
                    totalAppsWithLimits   = limits.size,
                    appsWithLimitsList    = limitedApps,
                    appsNearingLimit      = appsNearing,
                    appsExceededLimit     = appsExceeded,
                    avgTransitionLatencyMs = avgLatencyMs,
                    hardestAppName        = hardestApp?.appName ?: "",
                    hardestAppHitCount    = hardestApp?.hitCount ?: 0,
                    smoothStopStreakDays  = streakDays
                )

                _uiState.value = _uiState.value.copy(
                    hasPermission  = true,
                    appsWithLimits = appsWithLimits,
                    dashboardStats = dashboardStats,
                    isLoading      = false
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