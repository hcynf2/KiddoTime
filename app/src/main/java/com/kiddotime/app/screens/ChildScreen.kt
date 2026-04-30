package com.kiddotime.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kiddotime.app.data.AppLimit
import com.kiddotime.app.data.BadgeId
import com.kiddotime.app.viewmodel.ChildViewModel
import java.util.Calendar

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
                TextButton(onClick = { viewModel.clearCelebration() }) { Text("OK") }
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
                TextButton(onClick = { viewModel.markBadgesSeen() }) { Text("OK") }
            }
        )
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val canAsk = uiState.limitedApps.isNotEmpty()
            && uiState.requestsEnabled
            && uiState.todayRequestCount < 1

    val askBlockReason = when {
        !uiState.requestsEnabled       -> "Asking for more time is turned off"
        uiState.todayRequestCount >= 1 -> "You've already asked today — come back tomorrow!"
        else                           -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header gradient ───────────────────────────────────────────────────
        ChildHeader(stars = uiState.starBalance, streakDays = uiState.streakDays)

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Streak week strip ─────────────────────────────────────────────
            StreakWeekStrip(streakDays = uiState.streakDays)

            // ── 3 stat cards ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    emoji = "⭐",
                    value = "${uiState.starBalance}",
                    label = "Stars",
                    accentColor = Color(0xFFFFC107)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🔥",
                    value = "${uiState.streakDays}d",
                    label = "Streak",
                    accentColor = Color(0xFFFF6D00)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    emoji = "✅",
                    value = "${uiState.todayOnTimeStops}/${uiState.todayTotalStops}",
                    label = "Stops",
                    accentColor = Color(0xFF2E7D32)
                )
            }

            // ── Badges ────────────────────────────────────────────────────────
            if (uiState.earnedBadges.isNotEmpty()) {
                BadgesSection(badges = uiState.earnedBadges)
            }

            // ── Need more time ────────────────────────────────────────────────
            if (uiState.limitedApps.isNotEmpty() || askBlockReason != null) {
                NeedMoreTimeSection(
                    limitedApps = uiState.limitedApps,
                    canAsk = canAsk,
                    askBlockReason = askBlockReason,
                    onAsk = { pkg, appName -> viewModel.submitRequest(pkg, appName) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChildHeader(stars: Int, streakDays: Int) {
    val streakText = when {
        streakDays == 0 -> "Start your streak today! 💪"
        streakDays == 1 -> "Great start — keep going! 🌟"
        streakDays < 5  -> "You're on a roll — $streakDays days! 🔥"
        else            -> "Amazing — $streakDays day streak! 🏆"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Hi there! 👋",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = streakText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Star balance badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFFC107).copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "⭐", fontSize = 18.sp)
                    Text(
                        text = "$stars",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6D4C00)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Streak week strip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StreakWeekStrip(streakDays: Int) {
    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    val todayIndex = remember {
        Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1  // 0 = Sunday
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dayLabels.forEachIndexed { index, label ->
                // A day is filled if it's before today and within the streak window
                val isFilled = index < todayIndex && index > (todayIndex - streakDays)
                val isToday  = index == todayIndex
                DayBubble(label = label, isFilled = isFilled, isToday = isToday)
            }
        }
    }
}

@Composable
private fun DayBubble(label: String, isFilled: Boolean, isToday: Boolean) {
    val primary  = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val outline  = MaterialTheme.colorScheme.outline

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isFilled) primary else Color.Transparent)
                .then(
                    when {
                        isToday  -> Modifier.border(2.dp, primary, CircleShape)
                        !isFilled -> Modifier.border(1.dp, outline.copy(alpha = 0.35f), CircleShape)
                        else     -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isFilled) "✓" else label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    isFilled -> onPrimary
                    isToday  -> primary
                    else     -> outline.copy(alpha = 0.6f)
                }
            )
        }
        // Dot indicator below today's bubble
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    if (isToday) primary else Color.Transparent,
                    CircleShape
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    emoji: String,
    value: String,
    label: String,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = emoji, fontSize = 26.sp)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor.copy(alpha = 0.85f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Badges
// ─────────────────────────────────────────────────────────────────────────────

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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = badge.emoji, fontSize = 26.sp)
            Text(
                text = badge.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Need more time
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NeedMoreTimeSection(
    limitedApps: List<AppLimit>,
    canAsk: Boolean,
    askBlockReason: String?,
    onAsk: (packageName: String, appName: String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Need more time?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (askBlockReason != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = askBlockReason,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    limitedApps.forEachIndexed { index, app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "+30 min extra",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onAsk(app.packageName, app.appName) },
                                enabled = canAsk,
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Ask", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (index < limitedApps.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
