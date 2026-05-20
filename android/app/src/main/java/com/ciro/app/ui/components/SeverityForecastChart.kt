package com.ciro.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.SeverityForecast
import com.ciro.app.ui.theme.CiroColors

/**
 * Animated severity forecast bar chart.
 *
 * Shows T+1h, T+2h, T+6h severity projections with animated bar growth,
 * severity colour coding, labels, and uncertainty range indicator.
 */
@Composable
fun SeverityForecastChart(
    forecast: SeverityForecast,
    modifier: Modifier = Modifier,
) {
    // Trigger animation on composition
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animate = true }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CiroColors.SurfaceCard)
            .border(1.dp, CiroColors.SurfaceBorder.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        // Chart area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            ChartBar(
                label = "T+1h",
                level = forecast.t_plus_1h,
                animate = animate,
                delayMs = 0,
            )
            ChartBar(
                label = "T+2h",
                level = forecast.t_plus_2h,
                animate = animate,
                delayMs = 150,
            )
            ChartBar(
                label = "T+6h",
                level = forecast.t_plus_6h,
                animate = animate,
                delayMs = 300,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ForecastLegendItem("T+1h", forecast.t_plus_1h)
            Spacer(Modifier.width(16.dp))
            ForecastLegendItem("T+2h", forecast.t_plus_2h)
            Spacer(Modifier.width(16.dp))
            ForecastLegendItem("T+6h", forecast.t_plus_6h)
            Spacer(Modifier.weight(1f))
            Text(
                text = "±${forecast.uncertainty_range}",
                color = CiroColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(CiroColors.Surface, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ChartBar(
    label: String,
    level: Int,
    animate: Boolean,
    delayMs: Int,
) {
    val targetFraction = if (level <= 0) 0.08f else (level / 5f).coerceIn(0.08f, 1.0f)
    val animatedFraction by animateFloatAsState(
        targetValue = if (animate) targetFraction else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = delayMs),
        label = "barGrow",
    )
    val color = severityBarColor(level)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp),
    ) {
        // Value label on top of bar
        Text(
            text = "$level",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        // Animated bar
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(animatedFraction)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(color, color.copy(alpha = 0.5f))
                    )
                ),
        )
        Spacer(Modifier.height(4.dp))
        // Time label
        Text(
            text = label,
            color = CiroColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ForecastLegendItem(label: String, level: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = CiroColors.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$level",
            color = CiroColors.severityColor(level),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun severityBarColor(level: Int): Color = when (level) {
    5 -> CiroColors.Severity5
    4 -> CiroColors.Severity4
    3 -> CiroColors.Severity3
    2 -> CiroColors.Severity2
    1 -> CiroColors.Severity1
    else -> CiroColors.TextMuted
}
