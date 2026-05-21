package com.ciro.app.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    scenarioStatus: String? = null,
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

        // Status feedback
        if (scenarioStatus != null) {
            item {
                val isSuccess = scenarioStatus.startsWith("✓")
                Text(
                    text = scenarioStatus,
                    color = if (isSuccess) CiroColors.AccentGreen else CiroColors.AccentOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            (if (isSuccess) CiroColors.AccentGreen else CiroColors.AccentOrange).copy(alpha = 0.1f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(10.dp),
                )
            }
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
                        isLoading = scenarioStatus?.contains("Triggering") == true,
                        onClick = { onTriggerScenario(id) },
                    )
                }
            }
        }

        // ── Pipeline Metrics ─────────────────────────────────────
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
                    if (metrics.isNotEmpty()) {
                        val avgLatency = metrics.map { it.total_end_to_end_ms }.average().toLong()
                        val fpCount = metrics.count { it.false_positive }
                        val manualAvg = metrics.map { it.manual_benchmark_ms }.average().toLong()
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

                        SparklineChart(
                            values = metrics.take(10).map { it.total_end_to_end_ms.toInt() }.reversed(),
                            height = 40.dp,
                            lineColor = CiroColors.AccentCyan,
                            showDots = true,
                        )
                    } else {
                        Text(
                            text = "> No pipeline metrics yet. Trigger a scenario above.",
                            color = CiroColors.TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
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
                    else "—"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MetricValue("${incidents.size}", "total", CiroColors.TextPrimary)
                        MetricValue("$confirmed", "confirmed", CiroColors.AccentRed)
                        MetricValue("$resolved", "resolved", CiroColors.AccentGreen)
                        MetricValue("$retracted", "retracted", CiroColors.AccentOrange)
                        MetricValue("$accuracy%", "accuracy", CiroColors.AccentCyan)
                        MetricValue(avgSeverity, "avg sev", CiroColors.AccentPurple)
                    }
                }
            }
        }

        // ── AI Reasoning Logs (expandable) ───────────────────────
        item {
            Text(
                text = "$ AI REASONING LOG (${traces.size} total)",
                color = CiroColors.TextTerminalGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
            )
        }

        if (traces.isEmpty()) {
            item {
                Text(
                    text = "> No agent traces recorded yet.",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        items(traces.take(15), key = { it.trace_id }) { trace ->
            ExpandableTraceRow(trace)
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun ScenarioButton(
    title: String,
    description: String,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CiroColors.Surface)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Run",
            tint = if (isLoading) CiroColors.TextMuted else CiroColors.TextTerminalGreen,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isLoading) CiroColors.TextMuted else CiroColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
            )
        }
        if (!isLoading) {
            Text(
                text = "RUN",
                color = CiroColors.TextTerminalGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(CiroColors.TextTerminalGreen.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
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

/**
 * Expandable trace row — shows summary by default.
 * Tap to expand and show full decision details, stage breakdown,
 * and incident linkage.
 */
@Composable
private fun ExpandableTraceRow(trace: AgentTrace) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CiroColors.Surface)
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(10.dp),
    ) {
        // Header row — always visible
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Agent type badge
                val agentColor = when {
                    trace.agent.contains("orchestrator", ignoreCase = true) -> CiroColors.AccentPurple
                    trace.agent.contains("detection", ignoreCase = true) -> CiroColors.AccentCyan
                    trace.agent.contains("severity", ignoreCase = true) -> CiroColors.AccentOrange
                    trace.agent.contains("resource", ignoreCase = true) -> CiroColors.AccentGreen
                    trace.agent.contains("notification", ignoreCase = true) -> CiroColors.AccentRed
                    trace.agent.contains("retraction", ignoreCase = true) -> CiroColors.Severity3
                    else -> CiroColors.TextSecondary
                }

                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(agentColor),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = trace.agent.uppercase(),
                    color = agentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trace.routing_decision ?: "—",
                    color = CiroColors.TextTerminalGreen,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = CiroColors.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Summary — always visible
        if (trace.decision.isNotEmpty()) {
            Text(
                text = trace.decision,
                color = CiroColors.TextSecondary,
                fontSize = 10.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Timing — always visible
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

        // ── Expanded detail section ──────────────────────────────
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                HorizontalDivider(color = CiroColors.SurfaceBorder.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))

                // Incident ID
                if (!trace.incident_id.isNullOrEmpty()) {
                    DetailRow("Incident", trace.incident_id!!)
                }

                // Full decision text
                if (trace.decision.isNotEmpty()) {
                    DetailRow("Decision", trace.decision)
                }

                // Routing decision
                if (trace.routing_decision != null) {
                    DetailRow("Route", trace.routing_decision!!)
                }

                // Duration breakdown
                DetailRow("Duration", "${trace.total_duration_ms}ms end-to-end")

                // Timestamp
                DetailRow("Timestamp", trace.timestamp)

                // Stages (from trace data)
                if (trace.stages.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "STAGES:",
                        color = CiroColors.TextTerminalGreen,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    trace.stages.forEachIndexed { idx, stageMap ->
                        val stageName = stageMap["stage"]?.toString()
                            ?: stageMap["agent"]?.toString()
                            ?: "stage_${idx + 1}"
                        val stageDecision = stageMap["decision"]?.toString()
                            ?: stageMap["summary"]?.toString()
                            ?: ""
                        val stageDuration = stageMap["duration_ms"]?.toString() ?: ""
                        Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp)) {
                            Text(
                                text = "${idx + 1}.",
                                color = CiroColors.TextMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(14.dp),
                            )
                            Text(
                                text = buildString {
                                    append(stageName)
                                    if (stageDuration.isNotEmpty()) append(" (${stageDuration}ms)")
                                    if (stageDecision.isNotEmpty()) append(" — $stageDecision")
                                },
                                color = CiroColors.TextSecondary,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            color = CiroColors.TextMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(68.dp),
        )
        Text(
            text = value,
            color = CiroColors.TextSecondary,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun formatTime(iso: String): String = try {
    iso.substringAfter("T").substringBefore(".").take(8)
} catch (_: Exception) { iso.takeLast(8) }
