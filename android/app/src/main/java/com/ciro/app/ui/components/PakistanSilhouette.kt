package com.ciro.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ciro.app.ui.theme.CiroColors

/**
 * Province data for the Pakistan silhouette overlay on the Pulse dashboard.
 * Each province is a simplified polygon for visual effect — not geographically precise.
 */
data class ProvinceZone(
    val name: String,
    val shortName: String,
    // Normalized coords (0-1) within the canvas
    val centerX: Float,
    val centerY: Float,
    val threatLevel: Int = 0,       // 0-5
    val activeIncidents: Int = 0,
)

private val PROVINCES = listOf(
    ProvinceZone("Punjab", "PB", 0.55f, 0.55f),
    ProvinceZone("Sindh", "SD", 0.45f, 0.80f),
    ProvinceZone("Khyber Pakhtunkhwa", "KP", 0.45f, 0.28f),
    ProvinceZone("Balochistan", "BL", 0.25f, 0.60f),
    ProvinceZone("Islamabad", "ICT", 0.58f, 0.35f),
    ProvinceZone("Gilgit-Baltistan", "GB", 0.55f, 0.10f),
    ProvinceZone("Azad Kashmir", "AJK", 0.65f, 0.25f),
)

/**
 * Stylized Pakistan map silhouette with glowing province zones.
 * Each zone pulses based on its threat level.
 */
@Composable
fun PakistanSilhouette(
    provinceThreatLevels: Map<String, Int>,
    provinceIncidentCounts: Map<String, Int>,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val pulse = rememberInfiniteTransition(label = "pak_pulse")
    val glowFactor by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )

    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Draw Pakistan outline (simplified path)
            val outline = Path().apply {
                moveTo(w * 0.50f, h * 0.02f)  // Top (GB)
                lineTo(w * 0.70f, h * 0.08f)
                lineTo(w * 0.75f, h * 0.18f)  // AJK
                lineTo(w * 0.72f, h * 0.30f)
                lineTo(w * 0.78f, h * 0.42f)  // Punjab east
                lineTo(w * 0.75f, h * 0.55f)
                lineTo(w * 0.68f, h * 0.70f)  // Sindh east
                lineTo(w * 0.55f, h * 0.90f)
                lineTo(w * 0.42f, h * 0.98f)  // Karachi
                lineTo(w * 0.20f, h * 0.85f)  // Balochistan south
                lineTo(w * 0.08f, h * 0.65f)
                lineTo(w * 0.10f, h * 0.45f)  // Balochistan west
                lineTo(w * 0.18f, h * 0.30f)
                lineTo(w * 0.28f, h * 0.20f)  // KP/FATA
                lineTo(w * 0.38f, h * 0.12f)
                lineTo(w * 0.45f, h * 0.05f)
                close()
            }

            // Fill outline with subtle gradient
            drawPath(
                path = outline,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CiroColors.AccentCyan.copy(alpha = 0.05f),
                        CiroColors.SurfaceCard.copy(alpha = 0.1f),
                    ),
                ),
            )

            // Draw outline stroke
            drawPath(
                path = outline,
                color = CiroColors.AccentCyan.copy(alpha = 0.3f),
                style = Stroke(width = 1.5f),
            )

            // Draw province zones as glowing circles
            PROVINCES.forEach { province ->
                val threat = provinceThreatLevels[province.shortName] ?: province.threatLevel
                val incidents = provinceIncidentCounts[province.shortName] ?: province.activeIncidents
                val cx = w * province.centerX
                val cy = h * province.centerY

                val zoneColor = when (threat) {
                    0 -> CiroColors.AccentGreen
                    1 -> CiroColors.AccentGreen
                    2 -> CiroColors.Severity3
                    3 -> CiroColors.AccentOrange
                    4 -> CiroColors.AccentRed
                    5 -> Color(0xFFCC0000)
                    else -> CiroColors.TextMuted
                }

                val baseRadius = if (incidents > 0) 12f + incidents * 4f else 6f
                val glowRadius = baseRadius * (1f + glowFactor * 0.3f)

                // Outer glow
                if (incidents > 0) {
                    drawCircle(
                        color = zoneColor.copy(alpha = glowFactor * 0.15f),
                        radius = glowRadius * 1.8f,
                        center = Offset(cx, cy),
                    )
                }

                // Inner glow
                drawCircle(
                    color = zoneColor.copy(alpha = if (incidents > 0) 0.25f else 0.1f),
                    radius = glowRadius,
                    center = Offset(cx, cy),
                )

                // Core dot
                drawCircle(
                    color = zoneColor.copy(alpha = if (incidents > 0) 0.8f else 0.3f),
                    radius = baseRadius * 0.5f,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}
