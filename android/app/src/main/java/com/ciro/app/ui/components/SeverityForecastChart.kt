package com.ciro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.SeverityForecast
import com.ciro.app.ui.theme.CiroColors
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer

/**
 * Severity forecast bar chart using the Vico charting library.
 *
 * Displays three bars:
 *   • T+1h  — predicted severity 1 hour from now
 *   • T+2h  — predicted severity 2 hours from now
 *   • T+6h  — predicted severity 6 hours from now
 *
 * Bars are colour-coded using the CIRO severity palette (1=green → 5=red).
 * An uncertainty range label is shown beneath the chart.
 */
@Composable
fun SeverityForecastChart(
    forecast: SeverityForecast,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    // Push data into the chart model whenever the forecast changes
    LaunchedEffect(forecast) {
        modelProducer.runTransaction {
            columnSeries {
                series(
                    forecast.t_plus_1h.toFloat(),
                    forecast.t_plus_2h.toFloat(),
                    forecast.t_plus_6h.toFloat(),
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CiroColors.SurfaceCard, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        // Chart
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(
                            color = severityBarColor(forecast.t_plus_1h),
                            thickness = 32.dp,
                            shape = com.patrykandpatrick.vico.core.common.shape.CorneredShape.rounded(
                                topLeftPercent = 20,
                                topRightPercent = 20,
                            ),
                        ),
                        rememberLineComponent(
                            color = severityBarColor(forecast.t_plus_2h),
                            thickness = 32.dp,
                            shape = com.patrykandpatrick.vico.core.common.shape.CorneredShape.rounded(
                                topLeftPercent = 20,
                                topRightPercent = 20,
                            ),
                        ),
                        rememberLineComponent(
                            color = severityBarColor(forecast.t_plus_6h),
                            thickness = 32.dp,
                            shape = com.patrykandpatrick.vico.core.common.shape.CorneredShape.rounded(
                                topLeftPercent = 20,
                                topRightPercent = 20,
                            ),
                        ),
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        )

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

/**
 * Map severity level to a Vico-compatible bar Color.
 * Uses the same CIRO palette: 5=red, 4=orange, 3=yellow, 2=blue, 1=green.
 */
private fun severityBarColor(level: Int): Color = when (level) {
    5 -> CiroColors.Severity5
    4 -> CiroColors.Severity4
    3 -> CiroColors.Severity3
    2 -> CiroColors.Severity2
    1 -> CiroColors.Severity1
    else -> CiroColors.TextMuted
}
