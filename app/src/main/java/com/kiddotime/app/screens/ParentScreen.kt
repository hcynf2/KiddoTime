package com.kiddotime.app.screens

import android.content.Intent
import android.util.Log
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.kiddotime.app.data.AppUsageInfo
import com.kiddotime.app.viewmodel.AppUsageWithLimit
import com.kiddotime.app.viewmodel.ParentViewModel

@Composable
fun ParentScreen(viewModel: ParentViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Selected app for limit dialog
    var selectedApp by remember { mutableStateOf<AppUsageWithLimit?>(null) }

    Log.d("KiddoTime", "ParentScreen composable loaded")

    LaunchedEffect(Unit) {
        Log.d("KiddoTime", "LaunchedEffect triggered")
        viewModel.checkPermissionAndLoad()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Parent Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            !uiState.hasPermission -> {
                PermissionPrompt(
                    onGrantClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            }

            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // PIN setup at the top
                    item {
                        PinSetupSection(
                            hasPin = uiState.hasPin,
                            pinError = uiState.pinError,
                            pinSaved = uiState.pinSaved,
                            onSavePin = { viewModel.savePin(it) },
                            onClearPin = { viewModel.clearPin() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Usage Access Settings")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap an app to set a time limit",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // App list
                    items(uiState.appsWithLimits) { appWithLimit ->
                        AppUsageRow(
                            appWithLimit = appWithLimit,
                            duration = viewModel.formatDuration(appWithLimit.usageInfo.totalTimeMs),
                            onTap = { selectedApp = appWithLimit }
                        )
                    }
                }
            }
        }
    }

    // Show limit dialog when an app is tapped
    selectedApp?.let { app ->
        SetLimitDialog(
            appWithLimit = app,
            onDismiss = { selectedApp = null },
            onSetLimit = { hours, minutes ->
                val limitMs = (hours * 60 + minutes) * 60 * 1000L
                viewModel.setLimit(
                    app.usageInfo.packageName,
                    app.usageInfo.appName,
                    limitMs
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

@Composable
fun SetLimitDialog(
    appWithLimit: AppUsageWithLimit,
    onDismiss: () -> Unit,
    onSetLimit: (hours: Int, minutes: Int) -> Unit,
    onRemoveLimit: () -> Unit
) {
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("30") }

    // Pre-fill with existing limit if one is set
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
                    TextButton(
                        onClick = onRemoveLimit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
            }) {
                Text("Set Limit")
            }
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
                Text(
                    text = appInfo.appName,
                    style = MaterialTheme.typography.bodyLarge
                )
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
                    MaterialTheme.colorScheme.error  // Red if over limit
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
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
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
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    } else {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Text("?")
        }
    }
}

@Composable
fun PinSetupSection(
    hasPin: Boolean,
    pinError: String?,
    pinSaved: Boolean,
    onSavePin: (String) -> Unit,
    onClearPin: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isChangingPin by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Parent PIN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (hasPin && !isChangingPin) {
                    TextButton(onClick = { isChangingPin = true }) {
                        Text("Change")
                    }
                    TextButton(onClick = onClearPin) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!hasPin || isChangingPin) {
                Text(
                    text = "Set a PIN to unlock apps after the game.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 6) pinInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Enter PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(12.dp))

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

            } else {
                // PIN is set
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "✅ PIN is set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (pinSaved) {
                    Text(
                        text = "PIN saved successfully!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}