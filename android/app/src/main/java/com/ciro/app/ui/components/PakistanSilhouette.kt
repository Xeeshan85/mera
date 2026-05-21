package com.ciro.app.ui.components

import android.graphics.Paint
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
    Offset(0.78f, 0.02f),   // Karakoram / NE highlands
    Offset(0.68f, 0.04f),
    Offset(0.58f, 0.03f),
    Offset(0.50f, 0.08f),   // Gilgit-Baltistan shoulder
    Offset(0.42f, 0.15f),
    Offset(0.36f, 0.24f),   // KP western edge
    Offset(0.30f, 0.30f),
    Offset(0.24f, 0.38f),
    Offset(0.17f, 0.47f),   // Balochistan bulge
    Offset(0.10f, 0.58f),
    Offset(0.05f, 0.70f),
    Offset(0.08f, 0.80f),   // Makran coast begins
    Offset(0.18f, 0.87f),
    Offset(0.31f, 0.90f),
    Offset(0.44f, 0.94f),
    Offset(0.52f, 0.98f),   // Indus delta / Karachi
    Offset(0.58f, 0.92f),
    Offset(0.59f, 0.83f),
    Offset(0.61f, 0.73f),   // Sindh eastern edge
    Offset(0.64f, 0.64f),
    Offset(0.68f, 0.56f),   // Punjab
    Offset(0.74f, 0.48f),
    Offset(0.80f, 0.40f),
    Offset(0.87f, 0.34f),   // Kashmir hook
    Offset(0.95f, 0.22f),
    Offset(0.92f, 0.14f),
    Offset(0.84f, 0.08f),
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

            val globePad = w.coerceAtMost(h) * 0.03f
            val globeTopLeft = Offset(globePad, globePad)
            val globeSize = Size(w - globePad * 2, h - globePad * 2)

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CiroColors.SurfaceElevated,
                        CiroColors.PakistanGreen.copy(alpha = 0.42f),
                        CiroColors.SurfaceTerminal,
                    ),
                    center = Offset(w * 0.34f, h * 0.24f),
                    radius = w.coerceAtMost(h) * 0.72f,
                ),
                topLeft = globeTopLeft,
                size = globeSize,
            )

            drawOval(
                color = CiroColors.AccentCyan.copy(alpha = 0.35f),
                topLeft = globeTopLeft,
                size = globeSize,
                style = Stroke(width = 1.3f),
            )

            // Globe latitude / longitude grid.
            listOf(0.28f, 0.50f, 0.72f).forEach { yRatio ->
                drawArc(
                    color = CiroColors.AccentCyan.copy(alpha = 0.13f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(globeTopLeft.x + globeSize.width * 0.08f, globeTopLeft.y + globeSize.height * yRatio - globeSize.height * 0.10f),
                    size = Size(globeSize.width * 0.84f, globeSize.height * 0.20f),
                    style = Stroke(width = 0.8f),
                )
            }
            listOf(0.28f, 0.50f, 0.72f).forEach { xRatio ->
                drawArc(
                    color = CiroColors.AccentCyan.copy(alpha = 0.13f),
                    startAngle = 90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(globeTopLeft.x + globeSize.width * xRatio - globeSize.width * 0.10f, globeTopLeft.y + globeSize.height * 0.08f),
                    size = Size(globeSize.width * 0.20f, globeSize.height * 0.84f),
                    style = Stroke(width = 0.8f),
                )
            }

            // Padding to keep Pakistan inside the sphere.
            val padX = w * 0.14f
            val padY = h * 0.06f
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

            // Country shadow for the raised 3D map effect.
            drawPath(
                path = Path().apply {
                    PAKISTAN_OUTLINE.forEachIndexed { idx, pt ->
                        val x = padX + pt.x * drawW + 3f
                        val y = padY + pt.y * drawH + 4f
                        if (idx == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                },
                color = Color.Black.copy(alpha = 0.28f),
            )

            // Fill with subtle gradient
            drawPath(
                path = outline,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CiroColors.PakistanGreen.copy(alpha = 0.88f),
                        CiroColors.PakistanGreen.copy(alpha = 0.52f),
                        CiroColors.AccentCyan.copy(alpha = 0.18f),
                    ),
                ),
            )

            // Border stroke
            drawPath(
                path = outline,
                color = CiroColors.PakistanWhite.copy(alpha = 0.88f),
                style = Stroke(width = 2.0f),
            )

            // Inner highlight stroke
            drawPath(
                path = outline,
                color = CiroColors.AccentCyan.copy(alpha = 0.4f),
                style = Stroke(width = 0.9f),
            )

            // Indus river trace, enough geography to make the silhouette legible.
            val river = Path().apply {
                moveTo(padX + 0.58f * drawW, padY + 0.14f * drawH)
                quadraticBezierTo(padX + 0.53f * drawW, padY + 0.29f * drawH, padX + 0.63f * drawW, padY + 0.47f * drawH)
                quadraticBezierTo(padX + 0.56f * drawW, padY + 0.66f * drawH, padX + 0.54f * drawW, padY + 0.91f * drawH)
            }
            drawPath(
                path = river,
                color = CiroColors.AccentCyan.copy(alpha = 0.72f),
                style = Stroke(width = 1.6f),
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

            val labelPaint = Paint().apply {
                color = android.graphics.Color.argb(190, 230, 237, 243)
                textAlign = Paint.Align.CENTER
                textSize = w.coerceAtMost(h) * 0.065f
                isFakeBoldText = true
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "PAKISTAN",
                w * 0.50f,
                h * 0.55f,
                labelPaint,
            )
        }

        // Overlay province labels via Column (only for active provinces)
        Column(modifier = Modifier.fillMaxSize()) {
            // Labels drawn by canvas above — no compose text overlay needed for now
        }
    }
}
