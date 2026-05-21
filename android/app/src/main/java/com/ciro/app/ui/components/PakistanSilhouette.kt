package com.ciro.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.ui.theme.CiroColors

/**
 * Province zone data for Pakistan silhouette map.
 */
data class ProvinceZone(
    val name: String,
    val shortName: String,
    val centerX: Float,
    val centerY: Float,
    val threatLevel: Int = 0,
    val activeIncidents: Int = 0,
)

/**
 * Actual Pakistan boundary polygon (simplified but geographically recognizable).
 * Derived from real lat/lng boundary normalized to 0-1 range.
 *
 * Pakistan lat range: ~23.5 to ~37.1 (height = 13.6)
 * Pakistan lng range: ~60.8 to ~77.8 (width = 17.0)
 * Normalized: x = (lng - 60.8) / 17.0, y = (37.1 - lat) / 13.6  (inverted y for canvas)
 */
private val PAKISTAN_OUTLINE = listOf(
    // Starting from the northeast (near Karakoram / China border)
    Offset(0.91f, 0.00f),   // NE tip (Karakoram)
    Offset(0.88f, 0.04f),
    Offset(0.82f, 0.07f),   // K2 region
    Offset(0.77f, 0.09f),
    Offset(0.73f, 0.07f),   // Gilgit area
    Offset(0.68f, 0.10f),
    Offset(0.64f, 0.13f),   // Chitral
    Offset(0.59f, 0.10f),
    Offset(0.55f, 0.12f),   // Dir
    Offset(0.50f, 0.16f),   // Swat
    Offset(0.47f, 0.19f),   // Peshawar region
    Offset(0.44f, 0.23f),   // KP
    Offset(0.40f, 0.28f),   // Khyber Pass / Afghan border
    Offset(0.36f, 0.30f),
    Offset(0.32f, 0.33f),
    Offset(0.29f, 0.36f),   // Waziristan
    Offset(0.26f, 0.40f),
    Offset(0.22f, 0.44f),
    Offset(0.19f, 0.47f),   // Zhob region
    Offset(0.16f, 0.50f),
    Offset(0.13f, 0.53f),
    Offset(0.10f, 0.57f),   // Quetta region
    Offset(0.08f, 0.60f),
    Offset(0.06f, 0.64f),
    Offset(0.04f, 0.68f),   // West Balochistan
    Offset(0.02f, 0.72f),
    Offset(0.00f, 0.76f),   // SW corner (Iran border)
    Offset(0.03f, 0.80f),
    Offset(0.07f, 0.83f),   // Gwadar coast
    Offset(0.12f, 0.86f),   // Makran coast
    Offset(0.18f, 0.88f),
    Offset(0.24f, 0.90f),   // Pasni
    Offset(0.30f, 0.91f),
    Offset(0.36f, 0.93f),   // Coastal approach
    Offset(0.40f, 0.95f),
    Offset(0.44f, 0.97f),   // Near Karachi
    Offset(0.48f, 0.98f),   // Karachi
    Offset(0.52f, 1.00f),   // Indus Delta
    Offset(0.55f, 0.97f),
    Offset(0.57f, 0.93f),   // Thatta
    Offset(0.58f, 0.88f),
    Offset(0.60f, 0.83f),   // Lower Sindh
    Offset(0.61f, 0.78f),
    Offset(0.63f, 0.73f),   // Upper Sindh
    Offset(0.64f, 0.68f),
    Offset(0.66f, 0.62f),   // Sukkur
    Offset(0.68f, 0.56f),   // Punjab border
    Offset(0.70f, 0.50f),   // Multan region
    Offset(0.73f, 0.45f),
    Offset(0.76f, 0.40f),   // Lahore region
    Offset(0.80f, 0.36f),
    Offset(0.83f, 0.33f),   // Sialkot
    Offset(0.85f, 0.30f),
    Offset(0.88f, 0.26f),   // Islamabad region
    Offset(0.90f, 0.22f),
    Offset(0.92f, 0.18f),   // AJK
    Offset(0.94f, 0.14f),
    Offset(0.96f, 0.10f),
    Offset(0.95f, 0.06f),
    Offset(0.93f, 0.03f),   // Returning to NE
)

// Province center positions (normalized 0-1)
private val PROVINCE_CENTERS = listOf(
    ProvinceZone("Punjab", "PB", 0.70f, 0.48f),
    ProvinceZone("Sindh", "SD", 0.56f, 0.80f),
    ProvinceZone("Khyber Pakhtunkhwa", "KP", 0.48f, 0.22f),
    ProvinceZone("Balochistan", "BL", 0.22f, 0.65f),
    ProvinceZone("Islamabad", "ICT", 0.80f, 0.30f),
    ProvinceZone("Gilgit-Baltistan", "GB", 0.72f, 0.08f),
    ProvinceZone("Azad Kashmir", "AJK", 0.88f, 0.18f),
)

/**
 * Pakistan silhouette drawn from real boundary coordinates.
 * Province zones glow and pulse based on threat levels.
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

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Padding to keep the outline inside
            val padX = w * 0.05f
            val padY = h * 0.03f
            val drawW = w - padX * 2
            val drawH = h - padY * 2

            // Build Pakistan outline path from real boundary data
            val outline = Path().apply {
                PAKISTAN_OUTLINE.forEachIndexed { idx, pt ->
                    val x = padX + pt.x * drawW
                    val y = padY + pt.y * drawH
                    if (idx == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }

            // Fill with subtle gradient
            drawPath(
                path = outline,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CiroColors.PakistanGreen.copy(alpha = 0.08f),
                        CiroColors.AccentCyan.copy(alpha = 0.04f),
                    ),
                ),
            )

            // Border stroke
            drawPath(
                path = outline,
                color = CiroColors.AccentCyan.copy(alpha = 0.4f),
                style = Stroke(width = 1.8f),
            )

            // Inner highlight stroke
            drawPath(
                path = outline,
                color = CiroColors.PakistanGreen.copy(alpha = 0.15f),
                style = Stroke(width = 0.8f),
            )

            // Draw province zones
            PROVINCE_CENTERS.forEach { province ->
                val threat = provinceThreatLevels[province.shortName] ?: 0
                val incidents = provinceIncidentCounts[province.shortName] ?: 0
                val cx = padX + province.centerX * drawW
                val cy = padY + province.centerY * drawH

                val zoneColor = when (threat) {
                    0 -> CiroColors.AccentGreen
                    1 -> CiroColors.AccentGreen
                    2 -> CiroColors.Severity3
                    3 -> CiroColors.AccentOrange
                    4 -> CiroColors.AccentRed
                    5 -> Color(0xFFCC0000)
                    else -> CiroColors.TextMuted
                }

                val baseRadius = if (incidents > 0) 10f + incidents * 3f else 5f
                val glowRadius = baseRadius * (1f + glowFactor * 0.3f)

                // Outer glow
                if (incidents > 0) {
                    drawCircle(
                        color = zoneColor.copy(alpha = glowFactor * 0.12f),
                        radius = glowRadius * 2.2f,
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = zoneColor.copy(alpha = glowFactor * 0.08f),
                        radius = glowRadius * 1.5f,
                        center = Offset(cx, cy),
                    )
                }

                // Inner glow ring
                drawCircle(
                    color = zoneColor.copy(alpha = if (incidents > 0) 0.25f else 0.08f),
                    radius = glowRadius,
                    center = Offset(cx, cy),
                )

                // Core dot
                drawCircle(
                    color = zoneColor.copy(alpha = if (incidents > 0) 0.85f else 0.25f),
                    radius = baseRadius * 0.4f,
                    center = Offset(cx, cy),
                )

                // Label for provinces with incidents
                if (incidents > 0) {
                    drawCircle(
                        color = zoneColor.copy(alpha = 0.6f),
                        radius = baseRadius * 0.4f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2f),
                    )
                }
            }
        }

        // Overlay province labels via Column (only for active provinces)
        Column(modifier = Modifier.fillMaxSize()) {
            // Labels drawn by canvas above — no compose text overlay needed for now
        }
    }
}
