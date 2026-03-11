package com.kiddotime.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModeSelectScreen(
    onParentClick: () -> Unit,
    onChildClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "KiddoTime",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Who is using the device?",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onParentClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I'm a Parent")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onChildClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I'm a Child")
        }
    }
}