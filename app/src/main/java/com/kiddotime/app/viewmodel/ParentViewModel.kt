package com.kiddotime.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kiddotime.app.data.AppDatabase
import com.kiddotime.app.data.AppLimit
import com.kiddotime.app.data.AppLimitRepository
import com.kiddotime.app.data.AppUsageInfo
import com.kiddotime.app.data.BadgeRepository
import com.kiddotime.app.data.BedtimeRepository
import com.kiddotime.app.data.CooldownEventRepository
import com.kiddotime.app.data.GamePreferences
import com.kiddotime.app.data.TimeRequestPreferences
import com.kiddotime.app.data.TimeRequestStats
import com.kiddotime.app.data.LimitEvent
import com.kiddotime.app.data.LimitEventRepository
import com.kiddotime.app.data.PinRepository
import com.kiddotime.app.data.ScreenTimeLimitRepository
import com.kiddotime.app.data.ScreenTimeRequest
import com.kiddotime.app.data.ScreenTimeRequestRepository
import com.kiddotime.app.data.StarRepository
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

data class CooldownStats(
    val totalStarted: Int = 0,
    val totalCompleted: Int = 0,
    val startedByType: Map<String, Int> = emptyMap(),
    val completedByType: Map<String, Int> = emptyMap(),
    val whatNextChoiceCounts: Map<String, Int> = emptyMap()
) {
    val overallCompletionRate: Float
        get() = if (totalStarted > 0) totalCompleted.toFloat() / totalStarted else 0f

    fun completionRateFor(gameType: String): Float {
        val started = startedByType[gameType] ?: 0
        val completed = completedByType[gameType] ?: 0
        return if (started > 0) completed.toFloat() / started else 0f
    }

    val topWhatNextChoice: String
        get() = whatNextChoiceCounts.entries.maxByOrNull { it.value }?.key ?: ""
}

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
    val bedtimeState: BedtimeState = BedtimeState(),
    val historyEvents: List<LimitEvent> = emptyList(),
    val pendingRequests: List<ScreenTimeRequest> = emptyList(),
    val totalCapMs: Long = 0L,
    val exportText: String? = null,
    val deleteComplete: Boolean = false,
    val cooldownStats: CooldownStats? = null,
    val requestsEnabled: Boolean = true,
    val timeRequestStats: TimeRequestStats? = null
)

class ParentViewModel(application: Application) : AndroidViewModel(application) {

    private val usageStatsHelper = UsageStatsHelper(application)
    private val db = AppDatabase.getDatabase(application)
    private val repository = AppLimitRepository(db.appLimitDao())
    private val limitEventRepo = LimitEventRepository(db.limitEventDao())
    private val requestRepo = ScreenTimeRequestRepository(db.screenTimeRequestDao())
    private val cooldownEventRepo = CooldownEventRepository(db.cooldownEventDao())
    private val screenTimeLimitRepo = com.kiddotime.app.data.ScreenTimeLimitRepository(application)
    private val timeRequestPrefs = TimeRequestPreferences(application)
    private val starRepo = StarRepository(application)
    private val badgeRepo = BadgeRepository(application)

    private val _uiState = MutableStateFlow(ParentUiState())
    val uiState: StateFlow<ParentUiState> = _uiState

    private val pinRepository = PinRepository(application)
    private val bedtimeRepository = BedtimeRepository(application)

    /**
     * Called on every ON_RESUME. If the user just granted usage-stats permission in Settings
     * this will detect it and kick off the full stats load. If stats are already loaded
     * (hasPermission == true) the function returns immediately to avoid duplicate flows.
     */
    fun recheckPermission() {
        if (_uiState.value.hasPermission) return
        viewModelScope.launch {
            if (usageStatsHelper.hasUsagePermission()) {
                Log.d("KiddoTime", "recheckPermission: permission now granted — loading stats")
                loadUsageStats()
            }
        }
    }

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
                ),
                totalCapMs = screenTimeLimitRepo.capMs
            )
        }

        // Collect history events as a separate flow
        viewModelScope.launch {
            limitEventRepo.getAllEvents().collect { events ->
                _uiState.value = _uiState.value.copy(historyEvents = events)
            }
        }

        // Collect pending requests as a separate flow
        viewModelScope.launch {
            requestRepo.getPendingRequests().collect { requests ->
                _uiState.value = _uiState.value.copy(pendingRequests = requests)
            }
        }

        // One-shot load of cooldown effectiveness stats
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cooldownStats = buildCooldownStats())
        }

        // Load time-request toggle state and analysis stats
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                requestsEnabled   = timeRequestPrefs.enabled,
                timeRequestStats  = requestRepo.buildStats()
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

    fun setRequestsEnabled(enabled: Boolean) {
        timeRequestPrefs.enabled = enabled
        _uiState.value = _uiState.value.copy(requestsEnabled = enabled)
    }

    fun approveRequest(requestId: Long, packageName: String, appName: String, extraMs: Long) {
        viewModelScope.launch {
            requestRepo.approve(requestId)
            val currentLimit = repository.getLimitForApp(packageName)?.dailyLimitMs ?: 0L
            repository.setLimit(packageName, appName, currentLimit + extraMs)
            _uiState.value = _uiState.value.copy(timeRequestStats = requestRepo.buildStats())
        }
    }

    fun denyRequest(requestId: Long) {
        viewModelScope.launch {
            requestRepo.deny(requestId)
            _uiState.value = _uiState.value.copy(timeRequestStats = requestRepo.buildStats())
        }
    }

    fun setTotalCap(ms: Long) {
        screenTimeLimitRepo.capMs = ms
        _uiState.value = _uiState.value.copy(totalCapMs = ms)
    }

    fun clearTotalCap() {
        screenTimeLimitRepo.capMs = 0L
        _uiState.value = _uiState.value.copy(totalCapMs = 0L)
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

    fun requestExport() {
        viewModelScope.launch {
            val text = generateExportText()
            _uiState.value = _uiState.value.copy(exportText = text)
        }
    }

    fun clearExportText() {
        _uiState.value = _uiState.value.copy(exportText = null)
    }

    fun deleteAllData() {
        viewModelScope.launch {
            limitEventRepo.clearAll()
            requestRepo.clearAll()
            cooldownEventRepo.clearAll()
            starRepo.clearAll()
            badgeRepo.clearAll()
            screenTimeLimitRepo.capMs = 0L
            _uiState.value = _uiState.value.copy(
                totalCapMs = 0L,
                historyEvents = emptyList(),
                pendingRequests = emptyList(),
                cooldownStats = CooldownStats(),
                deleteComplete = true
            )
        }
    }

    fun clearDeleteComplete() {
        _uiState.value = _uiState.value.copy(deleteComplete = false)
    }

    private suspend fun buildCooldownStats(): CooldownStats {
        val gameTypes = listOf(GamePreferences.GAME_CARD, GamePreferences.GAME_CLEANUP, GamePreferences.GAME_WHAT_NEXT)
        val startedByType  = gameTypes.associateWith { cooldownEventRepo.getStartedByType(it) }
        val completedByType = gameTypes.associateWith { cooldownEventRepo.getCompletedByType(it) }
        return CooldownStats(
            totalStarted      = startedByType.values.sum(),
            totalCompleted    = completedByType.values.sum(),
            startedByType     = startedByType,
            completedByType   = completedByType,
            whatNextChoiceCounts = cooldownEventRepo.getWhatNextChoiceCounts()
        )
    }

    fun refreshCooldownStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cooldownStats = buildCooldownStats())
        }
    }

    private suspend fun generateExportText(): String {
        val sb = StringBuilder()
        sb.appendLine("KiddoTime Data Export")
        sb.appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine("=".repeat(40))

        val events = limitEventRepo.getAllEventsOnce()
        sb.appendLine("\nLimit Events (${events.size} total):")
        if (events.isEmpty()) {
            sb.appendLine("  No events recorded.")
        } else {
            val dayFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            events.forEach { e ->
                val closed = if (e.appClosedAt != null) {
                    val latencySec = (e.appClosedAt - e.limitReachedAt) / 1000
                    "closed ${latencySec}s later"
                } else "still open"
                sb.appendLine("  [${dayFmt.format(java.util.Date(e.limitReachedAt))}] ${e.appName} — $closed")
            }
        }

        val cooldown = buildCooldownStats()
        sb.appendLine("\nCooldown Effectiveness:")
        sb.appendLine("  Started: ${cooldown.totalStarted}  Completed: ${cooldown.totalCompleted}")
        if (cooldown.totalStarted > 0) {
            val pct = (cooldown.overallCompletionRate * 100).toInt()
            sb.appendLine("  Overall completion rate: $pct%")
        }
        if (cooldown.whatNextChoiceCounts.isNotEmpty()) {
            sb.appendLine("  Most chosen What's Next: ${cooldown.topWhatNextChoice}")
        }

        sb.appendLine("\nStars Earned: ${starRepo.balance}")
        sb.appendLine("\nAll data is stored on-device only and never transmitted.")
        return sb.toString()
    }
}