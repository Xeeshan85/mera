package com.ciro.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.AgentTrace
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.PipelineMetric
import com.ciro.app.ui.components.SparklineChart
import com.ciro.app.ui.theme.CiroColors

/**
 * Admin Panel — Hidden behind long-press on barwaqt logo.
 * Provides scenario simulation triggers, operational metrics,
 * AI reasoning logs, and system diagnostics.
 */
@Composable
fun AdminPanel(
    incidents: List<Incident>,
    metrics: List<PipelineMetric>,
    traces: List<AgentTrace>,
    onTriggerScenario: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.SurfaceTerminal)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // ── Header ───────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = CiroColors.TextTerminalGreen)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ADMIN CONSOLE",
                        color = CiroColors.TextTerminalGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = "barwaqt operations control",
                        color = CiroColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // ── Scenario Simulator ───────────────────────────────────
        item {
            Text(
                text = "$ SCENARIO SIMULATOR",
                color = CiroColors.TextTerminalGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
            )
        }
        item {
            val scenarios = listOf(
                Triple("flood_g10", "Flood in G-10 Islamabad", "Flash flooding scenario — SEV 4"),
                Triple("heatwave_i8", "Heatwave in I-8 Islamabad", "Extreme heat scenario — SEV 3"),
                Triple("false_alarm", "False Alarm F-6", "Low confidence signal — auto-retraction"),
                Triple("multi_crisis", "Multi-Crisis Simulation", "Simultaneous flood + heatwave"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                scenarios.forEach { (id, title, desc) ->
                    ScenarioButton(
                        title = title,
                        description = desc,
                        onClick = { onTriggerScenario(id) },
                    )
                }
            }
        }

        // ── Pipeline Metrics ─────────────────────────────────────
        if (metrics.isNotEmpty()) {
            item {
                Text(
                    text = "$ PIPELINE METRICS",
                    color = CiroColors.TextTerminalGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CiroColors.Surface),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val avgLatency = if (metrics.isNotEmpty())
                            metrics.map { it.total_end_to_end_ms }.average().toLong() else 0L
                        val fpCount = metrics.count { it.false_positive }
                        val manualAvg = if (metrics.isNotEmpty())
                            metrics.map { it.manual_benchmark_ms }.average().toLong() else 600_000L
                        val speedup = if (avgLatency > 0) manualAvg.toFloat() / avgLatency else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            MetricValue("${avgLatency}ms", "avg latency", CiroColors.AccentCyan)
                            MetricValue("${String.format("%.0f", speedup)}×", "faster", CiroColors.AccentGreen)
                            MetricValue("${metrics.size}", "runs", CiroColors.AccentPurple)
                            MetricValue("$fpCount", "false+", CiroColors.AccentRed)
                        }

                        Spacer(Modifier.height(12.dp))

                        // Latency sparkline
                        SparklineChart(
                            values = metrics.take(10).map { it.total_end_to_end_ms.toInt() }.reversed(),
                            height = 40.dp,
                            lineColor = CiroColors.AccentCyan,
                            showDots = true,
                        )
                    }
                }
            }
        }

        // ── Operational Intelligence ─────────────────────────────
        item {
            Text(
                text = "$ OPERATIONAL INTELLIGENCE",
                color = CiroColors.TextTerminalGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CiroColors.Surface),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val confirmed = incidents.count { it.state == "CONFIRMED" }
                    val retracted = incidents.count { it.state == "RETRACTED" }
                    val resolved = incidents.count { it.state == "RESOLVED" }
                    val total = incidents.size.coerceAtLeast(1)
                    val accuracy = ((total - retracted).toFloat() / total * 100).toInt()
                    val avgSeverity = if (incidents.isNotEmpty())
                        String.format("%.1f", incidents.map { it.severity_level }.average())
                    else "0"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MetricValue("$confirmed", "confirmed", CiroColors.AccentRed)
                        MetricValue("$resolved", "resolved", CiroColors.AccentGreen)
                        MetricValue("$retracted", "retracted", CiroColors.AccentOrange)
                        MetricValue("$accuracy%", "accuracy", CiroColors.AccentCyan)
                        MetricValue(avgSeverity, "avg sev", CiroColors.AccentPurple)
                    }
                }
            }
        }

        // ── AI Reasoning Logs ────────────────────────────────────
        if (traces.isNotEmpty()) {
            item {
                Text(
                    text = "$ AI REASONING LOG (latest ${traces.size.coerceAtMost(10)})",
                    color = CiroColors.TextTerminalGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                )
            }
            items(traces.take(10), key = { it.trace_id }) { trace ->
                TraceRow(trace)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun ScenarioButton(title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CiroColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Run",
            tint = CiroColors.TextTerminalGreen,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = CiroColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun MetricValue(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            color = CiroColors.TextMuted,
            fontSize = 8.sp,
        )
    }
}

@Composable
private fun TraceRow(trace: AgentTrace) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CiroColors.Surface)
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = trace.agent.uppercase(),
                color = CiroColors.AccentPurple,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = trace.routing_decision ?: "—",
                color = CiroColors.TextTerminalGreen,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (trace.decision.isNotEmpty()) {
            Text(
                text = trace.decision,
                color = CiroColors.TextSecondary,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row {
            Text(
                text = "${trace.total_duration_ms}ms",
                color = CiroColors.AccentCyan,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatTime(trace.timestamp),
                color = CiroColors.TextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun formatTime(iso: String): String = try {
    iso.substringAfter("T").substringBefore(".").take(8)
} catch (_: Exception) { iso.takeLast(8) }
