package com.restaurantpos.app.pad.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Screensaver / idle screen.
 * Cycles through promo lines with a fade animation.
 * Tap anywhere to wake.
 *
 * Inspired by Ziosk: promotional content + "tap to order" prompt.
 */
@Composable
fun IdleScreen(
    promoLines: List<String>,
    tableDisplayName: String,
    onWake: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface

    // Animate the touch prompt pulse
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    // Cycle promo lines
    var promoIndex by remember { mutableIntStateOf(0) }
    var promoVisible by remember { mutableStateOf(true) }
    if (promoLines.isNotEmpty()) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(4_000)
                promoVisible = false
                delay(400)
                promoIndex = (promoIndex + 1) % promoLines.size
                promoVisible = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(primary.copy(alpha = 0.15f), surface),
                )
            )
            .clickable(indication = null, interactionSource = null) { onWake() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Icon(
                Icons.Default.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = primary,
            )

            Text(
                tableDisplayName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = primary,
            )

            // Promo text carousel
            if (promoLines.isNotEmpty()) {
                AnimatedVisibility(
                    visible = promoVisible,
                    enter = fadeIn(animationSpec = tween(400)),
                    exit = fadeOut(animationSpec = tween(300)),
                ) {
                    Text(
                        promoLines[promoIndex],
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.widthIn(max = 480.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Touch-to-order prompt
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(scale),
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
                Text(
                    "Tap anywhere to order",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                )
            }
        }
    }
}
