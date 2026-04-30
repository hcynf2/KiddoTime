package com.kiddotime.app.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kiddotime.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val alpha   = remember { Animatable(0f) }
    val scale   = remember { Animatable(0.88f) }
    val offsetY = remember { Animatable(28f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f,   animationSpec = tween(600, easing = EaseOut)) }
        launch { scale.animateTo(1f,   animationSpec = tween(600, easing = EaseOutBack)) }
        launch { offsetY.animateTo(0f, animationSpec = tween(600, easing = EaseOut)) }
        delay(1600)
        alpha.animateTo(0f, animationSpec = tween(400))
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .alpha(alpha.value),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = offsetY.value.dp)
        ) {
            // Framed family image: soft white circle backing + circular clip
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(232.dp)
                        .background(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                            CircleShape
                        )
                )
                Image(
                    painter = painterResource(R.drawable.ic_launcher_family),
                    contentDescription = "KiddoTime family",
                    modifier = Modifier
                        .size(212.dp)
                        .scale(scale.value)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "KiddoTime",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Screen time, made friendly",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}