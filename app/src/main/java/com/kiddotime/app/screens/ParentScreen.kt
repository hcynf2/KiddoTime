package com.kiddotime.app.screens

import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiddotime.app.viewmodel.AppUsageWithLimit
import com.kiddotime.app.viewmodel.BedtimeState
import com.kiddotime.app.viewmodel.DashboardStats
import com.kiddotime.app.viewmodel.ParentViewModel
import kotlin.math.abs

// Which bottom sheet is currently open
private enum class DashboardSheet { MostUsed, Weekly, Limits, ParentPin, AppLimits, Bedtime }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentScreen(viewModel: ParentViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedApp by remember { mutableStateOf<AppUsageWithLimit?>(null) }
    var activeSheet by remember { mutableStateOf<DashboardSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Log.d("KiddoTime", "ParentScreen composable loaded")
    LaunchedEffect(Unit) { viewModel.checkPermissionAndLoad() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Parent Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        when {
            !uiState.hasPermission -> {
                PermissionPrompt {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }

            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                val stats = uiState.dashboardStats
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    // ── Always-visible overview ───────────────────────────────
                    if (stats != null) {
                        item { OverviewCard(stats, viewModel::formatDuration) }
                    }

                    // ── Action buttons grid ───────────────────────────────────
                    item {
                        val bedtime = uiState.bedtimeState
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionButton(
                                    icon = Icons.Outlined.Star,
                                    label = "Most Used",
                                    subtitle = stats?.topAppsToday?.firstOrNull()
                                        ?.usageInfo?.appName ?: "Top 5 today",
                                    modifier = Modifier.weight(1f),
                                    onClick = { activeSheet = DashboardSheet.MostUsed }
                                )
                                ActionButton(
                                    icon = Icons.Outlined.DateRange,
                                    label = "Weekly",
                                    subtitle = if (stats?.weeklyTopAppName?.isNotEmpty() == true)
                                        stats.weeklyTopAppName else "Last 7 days",
                                    modifier = Modifier.weight(1f),
                                    onClick = { activeSheet = DashboardSheet.Weekly }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionButton(
                                    icon = Icons.Outlined.Timer,
                                    label = "Time Limits",
                                    subtitle = if ((stats?.totalAppsWithLimits ?: 0) > 0)
                                        "${stats!!.totalAppsWithLimits} active" else "None set",
                                    modifier = Modifier.weight(1f),
                                    onClick = { activeSheet = DashboardSheet.Limits }
                                )
                                ActionButton(
                                    icon = Icons.Outlined.Lock,
                                    label = "Parent PIN",
                                    subtitle = if (uiState.hasPin) "PIN is set" else "Not set",
                                    modifier = Modifier.weight(1f),
                                    onClick = { activeSheet = DashboardSheet.ParentPin }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionButton(
                                    icon = Icons.Outlined.Apps,
                                    label = "Set App Limits",
                                    subtitle = "${uiState.appsWithLimits.size} apps installed",
                                    modifier = Modifier.weight(1f),
                                    onClick = { activeSheet = DashboardSheet.AppLimits }
                                )
                                ActionButton(
                                    icon = Icons.Outlined.Schedule,
                                    label = "Bedtime",
                                    subtitle = if (bedtime.isEnabled)
                                        "${bedtime.hour.toString().padStart(2,'0')}:${bedtime.minute.toString().padStart(2,'0')}  •  ${bedtime.selectedApps.size} apps"
                                    else "Off",
                                    modifier = Modifier.weight(1f),
                                    onClick = { activeSheet = DashboardSheet.Bedtime }
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // ── Bottom sheets ─────────────────────────────────────────────────────────
    if (activeSheet != null) {
        val stats = uiState.dashboardStats
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState
        ) {
            when (activeSheet) {
                DashboardSheet.MostUsed -> {
                    if (stats != null) {
                        MostUsedSheetContent(
                            apps = stats.topAppsToday,
                            formatDuration = viewModel::formatDuration
                        )
                    }
                }
                DashboardSheet.Weekly -> {
                    if (stats != null) {
                        WeeklySheetContent(
                            stats = stats,
                            formatDuration = viewModel::formatDuration
                        )
                    }
                }
                DashboardSheet.Limits -> {
                    if (stats != null) {
                        LimitsSheetContent(
                            stats = stats,
                            formatDuration = viewModel::formatDuration
                        )
                    }
                }
                DashboardSheet.ParentPin -> {
                    PinSheetContent(
                        hasPin = uiState.hasPin,
                        pinError = uiState.pinError,
                        pinSaved = uiState.pinSaved,
                        onSavePin = { viewModel.savePin(it) },
                        onClearPin = { viewModel.clearPin() }
                    )
                }
                DashboardSheet.AppLimits -> {
                    AppLimitsSheetContent(
                        apps = uiState.appsWithLimits,
                        formatDuration = viewModel::formatDuration,
                        onAppTap = { app ->
                            selectedApp = app
                            activeSheet = null
                        }
                    )
                }
                DashboardSheet.Bedtime -> {
                    BedtimeSheetContent(
                        apps = uiState.appsWithLimits,
                        bedtimeState = uiState.bedtimeState,
                        onSave = { enabled, hour, minute, selectedApps ->
                            viewModel.saveBedtime(enabled, hour, minute, selectedApps)
                            activeSheet = null
                        }
                    )
                }
                null -> {}
            }
        }
    }

    // ── Set-limit dialog (shown over whatever is visible) ─────────────────────
    selectedApp?.let { app ->
        SetLimitDialog(
            appWithLimit = app,
            onDismiss = { selectedApp = null },
            onSetLimit = { hours, minutes ->
                viewModel.setLimit(
                    app.usageInfo.packageName,
                    app.usageInfo.appName,
                    (hours * 60 + minutes) * 60 * 1000L
                )
                selectedApp = null
            },
            onRemoveLimit = {
                viewModel.removeLimit(app.usageInfo.packageName, app.usageInfo.appName)
                selectedApp = null
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Action button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Outlined.Apps, // chevron-right substitute
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Always-visible overview card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OverviewCard(stats: DashboardStats, formatDuration: (Long) -> String) {
    val deltaAbs = abs(stats.todayVsYesterdayMs)
    val deltaText = when {
        stats.todayVsYesterdayMs > 0 -> "▲ ${formatDuration(deltaAbs)} more"
        stats.todayVsYesterdayMs < 0 -> "▼ ${formatDuration(deltaAbs)} less"
        else -> "Same as yesterday"
    }
    val deltaColor = when {
        stats.todayVsYesterdayMs > 0 -> Color(0xFFE53935)
        stats.todayVsYesterdayMs < 0 -> Color(0xFF43A047)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "📱 Screen Time Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    modifier = Modifier.weight(1f),
                    label = "Today",
                    value = formatDuration(stats.totalScreenTimeToday),
                    large = true
                )
                StatCell(
                    modifier = Modifier.weight(1f),
                    label = "vs Yesterday",
                    value = deltaText,
                    valueColor = deltaColor
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    modifier = Modifier.weight(1f),
                    label = "Apps Used Today",
                    value = "${stats.appsUsedToday} / ${stats.totalInstalledApps}"
                )
                StatCell(
                    modifier = Modifier.weight(1f),
                    label = "7-Day Daily Avg",
                    value = formatDuration(stats.dailyAverageMs)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom sheet content composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MostUsedSheetContent(
    apps: List<AppUsageWithLimit>,
    formatDuration: (Long) -> String
) {
    val maxTime = apps.maxOfOrNull { it.usageInfo.totalTimeMs }?.toFloat()?.coerceAtLeast(1f) ?: 1f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SheetTitle("🏆 Most Used Today")
        if (apps.isEmpty()) {
            Text(
                text = "No usage data for today yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            apps.forEach { awl ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppIcon(drawable = awl.usageInfo.appIcon)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = awl.usageInfo.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatDuration(awl.usageInfo.totalTimeMs),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = (awl.usageInfo.totalTimeMs / maxTime).coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklySheetContent(stats: DashboardStats, formatDuration: (Long) -> String) {
    val hasWeekly  = stats.weeklyTopAppName.isNotEmpty() && stats.weeklyTopAppMs > 0
    val hasSession = stats.longestSessionAppName.isNotEmpty() && stats.longestSessionMs > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SheetTitle("📅 Weekly Highlights")
        if (!hasWeekly && !hasSession) {
            Text(
                text = "Not enough data yet. Check back after a few days.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (hasWeekly) {
                HighlightRow(
                    label = "🥇 Top app this week",
                    appName = stats.weeklyTopAppName,
                    detail = formatDuration(stats.weeklyTopAppMs) + " total"
                )
            }
            if (hasSession) {
                HighlightRow(
                    label = "⏱ Longest session today",
                    appName = stats.longestSessionAppName,
                    detail = formatDuration(stats.longestSessionMs)
                )
            }
        }
    }
}

@Composable
private fun LimitsSheetContent(stats: DashboardStats, formatDuration: (Long) -> String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SheetTitle("⏱ Time Limits")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${stats.totalAppsWithLimits} active",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (stats.appsWithLimitsList.isEmpty()) {
            Text(
                text = "No time limits set yet. Use \"Set App Limits\" to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Status chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (stats.appsExceededLimit.isNotEmpty()) {
                    StatusChip(
                        text = "🔴 ${stats.appsExceededLimit.size} exceeded",
                        background = Color(0xFFFFEBEE),
                        textColor = Color(0xFFB71C1C)
                    )
                }
                if (stats.appsNearingLimit.isNotEmpty()) {
                    StatusChip(
                        text = "🟡 ${stats.appsNearingLimit.size} nearing",
                        background = Color(0xFFFFF8E1),
                        textColor = Color(0xFFE65100)
                    )
                }
                val onTrack = stats.appsWithLimitsList.size -
                        stats.appsExceededLimit.size - stats.appsNearingLimit.size
                if (onTrack > 0) {
                    StatusChip(
                        text = "✅ $onTrack on track",
                        background = Color(0xFFE8F5E9),
                        textColor = Color(0xFF1B5E20)
                    )
                }
            }

            HorizontalDivider()

            stats.appsWithLimitsList.forEach { awl ->
                LimitedAppRow(awl = awl, formatDuration = formatDuration)
            }
        }
    }
}

@Composable
private fun PinSheetContent(
    hasPin: Boolean,
    pinError: String?,
    pinSaved: Boolean,
    onSavePin: (String) -> Unit,
    onClearPin: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isChangingPin by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            SheetTitle("Parent PIN")
        }

        if (hasPin && !isChangingPin) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✅ PIN is set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    TextButton(onClick = { isChangingPin = true }) { Text("Change") }
                    TextButton(onClick = onClearPin) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (pinSaved) {
                Text(
                    text = "PIN saved successfully!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Text(
                text = "Set a PIN to unlock apps after the game.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = pinInput,
                onValueChange = { if (it.length <= 6) pinInput = it.filter { c -> c.isDigit() } },
                label = { Text("Enter PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { if (it.length <= 6) confirmPin = it.filter { c -> c.isDigit() } },
                label = { Text("Confirm PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = confirmPin.isNotEmpty() && pinInput != confirmPin
            )
            if (pinError != null) {
                Text(
                    text = pinError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = {
                    if (pinInput == confirmPin) {
                        onSavePin(pinInput)
                        pinInput = ""
                        confirmPin = ""
                        isChangingPin = false
                    }
                },
                enabled = pinInput.length >= 4 && pinInput == confirmPin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isChangingPin) "Update PIN" else "Save PIN")
            }
        }
    }
}

@Composable
private fun AppLimitsSheetContent(
    apps: List<AppUsageWithLimit>,
    formatDuration: (Long) -> String,
    onAppTap: (AppUsageWithLimit) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SheetTitle("📋 Set App Limits")
            Text(
                text = "${apps.size} apps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap an app to set or change its daily time limit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps) { appWithLimit ->
                AppUsageRow(
                    appWithLimit = appWithLimit,
                    duration = formatDuration(appWithLimit.usageInfo.totalTimeMs),
                    onTap = { onAppTap(appWithLimit) }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogs & reusable rows
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SetLimitDialog(
    appWithLimit: AppUsageWithLimit,
    onDismiss: () -> Unit,
    onSetLimit: (hours: Int, minutes: Int) -> Unit,
    onRemoveLimit: () -> Unit
) {
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("30") }

    LaunchedEffect(appWithLimit) {
        appWithLimit.limit?.let { limit ->
            val totalMinutes = limit.dailyLimitMs / 1000 / 60
            hours = (totalMinutes / 60).toString()
            minutes = (totalMinutes % 60).toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
        title = { Text(appWithLimit.usageInfo.appName) },
        text = {
            Column {
                Text(
                    text = "Set a daily time limit for this app.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() } },
                        label = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                if (appWithLimit.limit != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRemoveLimit, modifier = Modifier.fillMaxWidth()) {
                        Text("Remove Limit", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val h = hours.toIntOrNull() ?: 0
                val m = minutes.toIntOrNull() ?: 0
                if (h > 0 || m > 0) onSetLimit(h, m)
            }) { Text("Set Limit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AppUsageRow(
    appWithLimit: AppUsageWithLimit,
    duration: String,
    onTap: () -> Unit
) {
    val appInfo = appWithLimit.usageInfo
    val limit = appWithLimit.limit

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(drawable = appInfo.appIcon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = appInfo.appName, style = MaterialTheme.typography.bodyLarge)
                if (limit != null) {
                    Text(
                        text = "Limit: ${formatLimitDuration(limit.dailyLimitMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = duration,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (limit != null && appInfo.totalTimeMs >= limit.dailyLimitMs)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun formatLimitDuration(ms: Long): String {
    val totalMinutes = ms / 1000 / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
fun PermissionPrompt(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📊", style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Usage Access Required",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "KiddoTime needs permission to read screen time data.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGrantClick) { Text("Grant Permission") }
    }
}

@Composable
fun AppIcon(drawable: Drawable?) {
    if (drawable != null) {
        val bitmap = remember(drawable) { drawable.toBitmap(48, 48).asImageBitmap() }
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Text("?")
        }
    }
}

@Composable
private fun BedtimeSheetContent(
    apps: List<AppUsageWithLimit>,
    bedtimeState: BedtimeState,
    onSave: (enabled: Boolean, hour: Int, minute: Int, selectedApps: Set<String>) -> Unit
) {
    var isEnabled by remember { mutableStateOf(bedtimeState.isEnabled) }
    var hourStr by remember { mutableStateOf(bedtimeState.hour.toString().padStart(2, '0')) }
    var minuteStr by remember { mutableStateOf(bedtimeState.minute.toString().padStart(2, '0')) }
    var selectedApps by remember { mutableStateOf(bedtimeState.selectedApps.toMutableSet()) }
    var inputError by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Fixed header ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SheetTitle("🌙 Set Bedtime")

            // Enable toggle
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Enable Bedtime",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Lock selected apps at a set time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
            }

            // Time input
            if (isEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Bedtime (24-hour format)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hourStr,
                            onValueChange = { if (it.length <= 2) hourStr = it.filter { c -> c.isDigit() } },
                            label = { Text("Hour") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(90.dp)
                        )
                        Text(":", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = minuteStr,
                            onValueChange = { if (it.length <= 2) minuteStr = it.filter { c -> c.isDigit() } },
                            label = { Text("Minute") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(90.dp)
                        )
                        Text(
                            text = run {
                                val h = hourStr.toIntOrNull() ?: 0
                                if (h < 12) "AM" else "PM"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (inputError != null) {
                        Text(text = inputError!!, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Info strip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "ℹ️  Warnings at 30 min and 10 min before bedtime.\nLock releases automatically at 6 AM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // App list header
            if (isEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apps to lock at bedtime",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${selectedApps.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ── Scrollable app list ───────────────────────────────────────────────
        if (isEnabled) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(apps) { awl ->
                    val pkg = awl.usageInfo.packageName
                    val checked = pkg in selectedApps
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedApps = selectedApps.toMutableSet().also {
                                    if (checked) it.remove(pkg) else it.add(pkg)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (checked)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(drawable = awl.usageInfo.appIcon)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = awl.usageInfo.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedApps = selectedApps.toMutableSet().also { set ->
                                        if (it) set.add(pkg) else set.remove(pkg)
                                    }
                                }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }

        // ── Save button ───────────────────────────────────────────────────────
        Button(
            onClick = {
                val h = hourStr.toIntOrNull()
                val m = minuteStr.toIntOrNull()
                when {
                    isEnabled && (h == null || h !in 0..23) ->
                        inputError = "Hour must be 0–23"
                    isEnabled && (m == null || m !in 0..59) ->
                        inputError = "Minute must be 0–59"
                    isEnabled && selectedApps.isEmpty() ->
                        inputError = "Select at least one app to lock"
                    else -> {
                        inputError = null
                        onSave(isEnabled, h ?: 21, m ?: 0, selectedApps.toSet())
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("Save Bedtime Settings")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helper composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun StatCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    large: Boolean = false,
    valueColor: Color? = null
) {
    Column(modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = if (large) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun HighlightRow(label: String, appName: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, background: Color, textColor: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = background) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun LimitedAppRow(awl: AppUsageWithLimit, formatDuration: (Long) -> String) {
    val limit = awl.limit ?: return
    val progress = (awl.usageInfo.totalTimeMs.toFloat() / limit.dailyLimitMs).coerceIn(0f, 1f)
    val barColor = when {
        progress >= 1f    -> Color(0xFFF44336)
        progress >= 0.75f -> Color(0xFFFF9800)
        progress >= 0.5f  -> Color(0xFFFFC107)
        else              -> Color(0xFF4CAF50)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(drawable = awl.usageInfo.appIcon)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = awl.usageInfo.appName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatDuration(awl.usageInfo.totalTimeMs)} / ${formatLimitDuration(limit.dailyLimitMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
