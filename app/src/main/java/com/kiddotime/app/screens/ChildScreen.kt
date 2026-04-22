package com.kiddotime.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiddotime.app.data.AppLimit
import com.kiddotime.app.data.BadgeId
import com.kiddotime.app.viewmodel.ChildViewModel

@Composable
fun ChildScreen(viewModel: ChildViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Star celebration dialog
    if (uiState.showCelebration) {
        AlertDialog(
            onDismissRequest = { viewModel.clearCelebration() },
            title = { Text("You earned a star!") },
            text = { Text("⭐ You earned a star! Great job stopping on time!") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCelebration() }) {
                    Text("OK")
                }
            }
        )
    }

    // New badge dialog
    if (uiState.newBadges.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.markBadgesSeen() },
            title = { Text("New Badge!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.newBadges.forEach { badge ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = badge.emoji, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = badge.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = badge.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.markBadgesSeen() }) {
                    Text("OK")
                }
            }
        )
    }

    // Ask for more time dialog
    var showAskMoreTimeDialog by remember { mutableStateOf(false) }
    if (showAskMoreTimeDialog) {
        AskMoreTimeDialog(
            limitedApps = uiState.limitedApps,
            onRequest = { pkg, appName ->
                viewModel.submitRequest(pkg, appName)
                showAskMoreTimeDialog = false
            },
            onDismiss = { showAskMoreTimeDialog = false }
        )
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Hi there! 👋",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Here's how you're doing today",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        StarBalanceCard(stars = uiState.starBalance)
        StreakCard(streakDays = uiState.streakDays)
        TodayProgressCard(onTime = uiState.todayOnTimeStops, total = uiState.todayTotalStops)

        // Badges section
        if (uiState.earnedBadges.isNotEmpty()) {
            BadgesSection(badges = uiState.earnedBadges)
        }

        // Ask for more time button
        ElevatedButton(
            onClick = { showAskMoreTimeDialog = true },
            enabled = uiState.limitedApps.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ask for More Time")
        }
    }
}

// ── Ask for more time dialog ──────────────────────────────────────────────────

@Composable
private fun AskMoreTimeDialog(
    limitedApps: List<AppLimit>,
    onRequest: (packageName: String, appName: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask for More Time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Which app do you want more time for?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                limitedApps.forEach { appLimit ->
                    OutlinedButton(
                        onClick = { onRequest(appLimit.packageName, appLimit.appName) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ask 30 min — ${appLimit.appName}")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Badges section ────────────────────────────────────────────────────────────

@Composable
private fun BadgesSection(badges: List<BadgeId>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Your Badges",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(badges) { badge ->
                BadgeChip(badge = badge)
            }
        }
    }
}

@Composable
private fun BadgeChip(badge: BadgeId) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = badge.emoji, fontSize = 24.sp)
            Text(
                text = badge.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Star balance ──────────────────────────────────────────────────────────────

@Composable
private fun StarBalanceCard(stars: Int) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Your Stars",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (stars == 0) "Stop apps on time to earn stars!"
                           else "Keep stopping on time!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "⭐", fontSize = 32.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$stars",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Streak ────────────────────────────────────────────────────────────────────

@Composable
private fun StreakCard(streakDays: Int) {
    val (emoji, message) = when {
        streakDays == 0 -> "💪" to "Stop an app on time to start your streak!"
        streakDays == 1 -> "🌟" to "Great start! Come back tomorrow!"
        streakDays < 5  -> "🔥" to "You're on a roll! Keep it going!"
        else            -> "🏆" to "Incredible streak — you're a champion!"
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Day Streak",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text(text = emoji, fontSize = 28.sp)
                Text(
                    text = "$streakDays",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Today's progress ──────────────────────────────────────────────────────────

@Composable
private fun TodayProgressCard(onTime: Int, total: Int) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Today's Stops",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (total == 0) {
                Text(
                    text = "🎉  No limits reached yet — great job!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val progress = onTime.toFloat() / total.toFloat()
                val stopWord = if (total == 1) "stop" else "stops"
                val label = when {
                    onTime == total -> "🌟  Perfect! All $total $stopWord on time!"
                    onTime == 0     -> "💪  Try to stop faster next time"
                    else            -> "👍  $onTime of $total $stopWord on time"
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$onTime / $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}