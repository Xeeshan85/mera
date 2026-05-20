package com.ciro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.SeverityForecast
import com.ciro.app.ui.theme.CiroColors

/**
 * Severity forecast bar chart placeholder (Vico fallback).
 */
@Composable
fun SeverityForecastChart(
    forecast: SeverityForecast,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CiroColors.SurfaceCard, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        // Simple Placeholder Chart using Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            ChartBar(forecast.t_plus_1h)
            ChartBar(forecast.t_plus_2h)
            ChartBar(forecast.t_plus_6h)
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
private fun ChartBar(level: Int) {
    // Max level is 5, scale height relative to 5
    val heightFraction = if (level <= 0) 0.1f else (level / 5f).coerceIn(0.1f, 1.0f)
    Box(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight(heightFraction)
            .background(
                color = severityBarColor(level),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            )
    )
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
