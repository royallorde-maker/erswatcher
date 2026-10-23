package com.erswatcher.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erswatcher.app.ui.theme.Eye

@Composable
fun SplashScreen() {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val scale by animateFloatAsState(if (started) 1f else 0.72f, tween(700), label = "splash-scale")
    val alpha by animateFloatAsState(if (started) 1f else 0f, tween(500), label = "splash-alpha")
    val titleAlpha by animateFloatAsState(if (started) 1f else 0f, tween(600, delayMillis = 220), label = "splash-title")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF050407))
    ) {
        AuroraBackdrop(Modifier.fillMaxSize(), intensity = 0.85f)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color(0xFF050407)),
                        radius = 900f
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EyeLogo(
                Modifier
                    .size(width = 252.dp, height = 142.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha),
                scanning = true
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "ERS WATCHER",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer(alpha = titleAlpha)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "SEE WHAT THE AIR CARRIES",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer(alpha = titleAlpha)
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    "CULT OF INTELLECT  ·  FIELD INSTRUMENTS",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.8.sp,
                    color = Eye.Cream.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
