package com.ciro.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.ui.theme.CiroColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated splash screen with CIRO branding.
 *
 * Shows the CIRO logo with a pulsing glow, tagline, technology badges,
 * and Pakistan authority context. Auto-navigates to the main screen
 * after a 2.5-second delay.
 */
@Composable
fun SplashScreen(
    onNavigateToMain: () -> Unit,
) {
    val fadeInAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.5f) }

    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "splashGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    LaunchedEffect(Unit) {
        // Fade in + scale up animation
        launch { fadeInAlpha.animateTo(1f, tween(800)) }
        launch { logoScale.animateTo(1f, tween(600)) }
        delay(2500)
        onNavigateToMain()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CiroColors.Surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(fadeInAlpha.value)
                .padding(32.dp),
        ) {
            // Pulsing glow ring behind logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp),
            ) {
                // Outer glow
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(logoScale.value * 1.2f)
                        .clip(CircleShape)
                        .background(CiroColors.AccentCyan.copy(alpha = glowAlpha * 0.15f))
                )
                // Inner glow
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(logoScale.value)
                        .clip(CircleShape)
                        .background(CiroColors.AccentCyan.copy(alpha = glowAlpha * 0.25f))
                )
                // Logo text
                Text(
                    text = "🛡️",
                    fontSize = 48.sp,
                    modifier = Modifier.scale(logoScale.value),
                )
            }

            Spacer(Modifier.height(20.dp))

            // CIRO title
            Text(
                text = "CIRO",
                color = CiroColors.AccentCyan,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 8.sp,
                modifier = Modifier.scale(logoScale.value),
            )

            Spacer(Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Crisis Intelligence & Response Orchestrator",
                color = CiroColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp,
            )

            Spacer(Modifier.height(32.dp))

            // Pakistan flag accent bar
            Row(
                modifier = Modifier
                    .width(120.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .weight(3f)
                        .fillMaxSize()
                        .background(CiroColors.PakistanGreen)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(CiroColors.PakistanWhite)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Authority context
            Text(
                text = "National Disaster Management Authority",
                color = CiroColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "NDMA · PDMA · Rescue 1122 · CDA Emergency Cell",
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
            )

            Spacer(Modifier.height(40.dp))

            // Tech badges
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TechBadge("Google ADK 2.0")
                Spacer(Modifier.width(8.dp))
                TechBadge("Gemini 2.5 Flash")
                Spacer(Modifier.width(8.dp))
                TechBadge("Firebase")
            }

            Spacer(Modifier.height(24.dp))

            // Loading indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dotAlpha1 by infiniteTransition.animateFloat(
                    initialValue = 0.3f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(600), RepeatMode.Reverse
                    ), label = "dot1"
                )
                val dotAlpha2 by infiniteTransition.animateFloat(
                    initialValue = 1f, targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        tween(600, delayMillis = 200), RepeatMode.Reverse
                    ), label = "dot2"
                )
                val dotAlpha3 by infiniteTransition.animateFloat(
                    initialValue = 0.3f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(600, delayMillis = 400), RepeatMode.Reverse
                    ), label = "dot3"
                )

                Text(
                    text = "Connecting to Command Center",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(4.dp))
                Text("•", color = CiroColors.AccentCyan.copy(alpha = dotAlpha1), fontSize = 12.sp)
                Text("•", color = CiroColors.AccentCyan.copy(alpha = dotAlpha2), fontSize = 12.sp)
                Text("•", color = CiroColors.AccentCyan.copy(alpha = dotAlpha3), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TechBadge(text: String) {
    Text(
        text = text,
        color = CiroColors.AccentCyan.copy(alpha = 0.7f),
        fontSize = 8.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(
                CiroColors.AccentCyan.copy(alpha = 0.08f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
