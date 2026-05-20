package com.ciro.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.ui.theme.CiroColors

/**
 * Animated circular threat level gauge.
 *
 * @param level 0-5 threat level (0=clear, 5=critical)
 * @param label "CLEAR", "LOW", "MODERATE", "ELEVATED", "HIGH", "CRITICAL"
 * @param size diameter of the gauge
 */
@Composable
fun ThreatGauge(
    level: Int,
    activeIncidents: Int,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
) {
    val fraction = (level.coerceIn(0, 5) / 5f)
    val animatedSweep by animateFloatAsState(
        targetValue = fraction * 270f,
        animationSpec = tween(1200),
        label = "sweep",
    )

    val label = when (level) {
        0 -> "CLEAR"
        1 -> "LOW"
        2 -> "MODERATE"
        3 -> "ELEVATED"
        4 -> "HIGH"
        5 -> "CRITICAL"
        else -> "UNKNOWN"
    }

    val gaugeColor = when (level) {
        0 -> CiroColors.AccentGreen
        1 -> CiroColors.AccentGreen
        2 -> CiroColors.Severity3
        3 -> CiroColors.AccentOrange
        4 -> CiroColors.AccentRed
        5 -> Color(0xFFCC0000)
        else -> CiroColors.TextMuted
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 14f
            val padding = strokeWidth / 2 + 8f
            val arcSize = Size(this.size.width - padding * 2, this.size.height - padding * 2)
            val topLeft = Offset(padding, padding)

            // Background arc (full 270°)
            drawArc(
                color = CiroColors.SurfaceBorder.copy(alpha = 0.2f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Active arc (animated)
            if (animatedSweep > 0) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            CiroColors.AccentGreen,
                            CiroColors.Severity3,
                            CiroColors.AccentOrange,
                            CiroColors.AccentRed,
                        ),
                    ),
                    startAngle = 135f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Glow dot at the tip
                val angle = Math.toRadians((135 + animatedSweep).toDouble())
                val cx = this.size.width / 2 + (arcSize.width / 2) * kotlin.math.cos(angle).toFloat()
                val cy = this.size.height / 2 + (arcSize.height / 2) * kotlin.math.sin(angle).toFloat()
                drawCircle(
                    color = gaugeColor,
                    radius = 8f,
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = gaugeColor.copy(alpha = 0.3f),
                    radius = 14f,
                    center = Offset(cx, cy),
                )
            }
        }

        // Center text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "$level",
                color = gaugeColor,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = label,
                color = gaugeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$activeIncidents active",
                color = CiroColors.TextMuted,
                fontSize = 10.sp,
            )
        }
    }
}
