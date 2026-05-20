package com.ciro.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ciro.app.ui.theme.CiroColors

/**
 * Mini sparkline chart for inline trend visualization.
 * Displays a smooth line with optional fill gradient.
 */
@Composable
fun SparklineChart(
    values: List<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    lineColor: Color = CiroColors.AccentCyan,
    showDots: Boolean = false,
) {
    if (values.isEmpty()) return

    val maxVal = values.max().coerceAtLeast(1).toFloat()
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800),
        label = "spark",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val w = size.width
        val h = size.height
        val padding = 4f
        val usableW = w - padding * 2
        val usableH = h - padding * 2

        if (values.size < 2) {
            // Single dot
            val y = padding + usableH * (1f - values[0] / maxVal)
            drawCircle(lineColor, 3f, Offset(w / 2, y))
            return@Canvas
        }

        val stepX = usableW / (values.size - 1)

        // Build path
        val path = Path()
        values.forEachIndexed { i, value ->
            val x = padding + i * stepX
            val normalizedY = (value / maxVal).coerceIn(0f, 1f) * animProgress
            val y = padding + usableH * (1f - normalizedY)

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Draw line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2f),
        )

        // Draw dots at each point
        if (showDots) {
            values.forEachIndexed { i, value ->
                val x = padding + i * stepX
                val normalizedY = (value / maxVal).coerceIn(0f, 1f) * animProgress
                val y = padding + usableH * (1f - normalizedY)
                drawCircle(lineColor, 3f, Offset(x, y))
            }
        }

        // Highlight last point
        val lastX = padding + (values.size - 1) * stepX
        val lastY = padding + usableH * (1f - (values.last() / maxVal).coerceIn(0f, 1f) * animProgress)
        drawCircle(lineColor, 4f, Offset(lastX, lastY))
        drawCircle(lineColor.copy(alpha = 0.3f), 8f, Offset(lastX, lastY))
    }
}
