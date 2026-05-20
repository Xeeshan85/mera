package com.ciro.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.AgentTrace
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.PipelineMetric
import com.ciro.app.data.model.Resource
import com.ciro.app.ui.components.AgentTraceTerminal
import com.ciro.app.ui.components.SeverityForecastChart
import com.ciro.app.ui.theme.CiroColors
import com.ciro.app.viewmodel.ResourceSummary

/**
 * Main command-center dashboard screen.
 *
 * Layout (top → bottom):
 *   1. CIRO header with pipeline speed headline
 *   2. Severity grid — 5 colour-coded cards (sev 1–5 counts)
 *   3. Resource deployment progress bar
 *   4. Active incidents list with mini-cards
 *   5. Severity forecast chart (for first incident with forecast data)
 *   6. Agent trace terminal (AI reasoning logs)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    incidents: List<Incident>,
    resources: List<Resource>,
    agentTraces: List<AgentTrace>,
    metrics: List<PipelineMetric>,
    severityCounts: Map<Int, Int>,
    resourceSummary: ResourceSummary,
    avgLatencyMs: Long,
    improvementHeadline: String,
    onIncidentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Header ───────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            DashboardHeader(
                activeCount = incidents.size,
                avgLatencyMs = avgLatencyMs,
                improvementHeadline = improvementHeadline,
            )
        }

        // ── Severity Grid (5 cards) ──────────────────────────────────
        item {
            SectionTitle("ACTIVE CRISES BY SEVERITY")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (level in 5 downTo 1) {
                    SeverityCard(
                        level = level,
                        count = severityCounts[level] ?: 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── Resource Deployment ──────────────────────────────────────
        item {
            SectionTitle("RESOURCE DEPLOYMENT")
            Spacer(Modifier.height(8.dp))
            ResourceDeploymentCard(summary = resourceSummary)
        }

        // ── Pipeline Performance ─────────────────────────────────────
        item {
            SectionTitle("PIPELINE PERFORMANCE")
            Spacer(Modifier.height(8.dp))
            PipelineStatsRow(metrics = metrics, avgLatencyMs = avgLatencyMs)
        }

        // ── Active Incidents List ────────────────────────────────────
        item {
            SectionTitle("ACTIVE INCIDENTS (${incidents.size})")
        }

        if (incidents.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(CiroColors.SurfaceCard, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No active incidents", color = CiroColors.TextMuted, fontSize = 14.sp)
                }
            }
        } else {
            items(incidents, key = { it.incident_id }) { incident ->
                IncidentMiniCard(incident = incident, onClick = { onIncidentClick(incident.incident_id) })
            }
        }

        // ── Severity Forecast Chart ──────────────────────────────────
        val incidentWithForecast = incidents.firstOrNull { it.severity_forecast != null }
        if (incidentWithForecast?.severity_forecast != null) {
            item {
                SectionTitle("SEVERITY FORECAST — ${incidentWithForecast.location.area_name}")
                Spacer(Modifier.height(8.dp))
                SeverityForecastChart(forecast = incidentWithForecast.severity_forecast!!)
            }
        }

        // ── Agent Trace Terminal ─────────────────────────────────────
        item {
            SectionTitle("🤖 ANTIGRAVITY AI REASONING LOG")
            Spacer(Modifier.height(8.dp))
            AgentTraceTerminal(
                traces = agentTraces,
                modifier = Modifier.height(360.dp),
            )
        }

        // Bottom spacer
        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    activeCount: Int,
    avgLatencyMs: Long,
    improvementHeadline: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CIRO",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = CiroColors.AccentCyan,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Command Center",
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                color = CiroColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Pulsing dot for "live" indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CiroColors.AccentGreen)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "LIVE",
                color = CiroColors.AccentGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "$activeCount active incidents",
                color = CiroColors.TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))
            if (improvementHeadline != "—") {
                Text(
                    text = "⚡ $improvementHeadline",
                    color = CiroColors.AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(CiroColors.AccentGreen.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// ── Severity Card ────────────────────────────────────────────────────────────

@Composable
private fun SeverityCard(level: Int, count: Int, modifier: Modifier = Modifier) {
    val color = CiroColors.severityColor(level)
    val label = when (level) {
        5 -> "CRIT"
        4 -> "SEV"
        3 -> "SIG"
        2 -> "MOD"
        1 -> "MIN"
        else -> "?"
    }

    Card(
        modifier = modifier
            .height(80.dp)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "$count",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = if (count > 0) color else CiroColors.TextMuted,
            )
            Text(
                text = "$label $level",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = color.copy(alpha = 0.8f),
            )
        }
    }
}

// ── Resource Deployment Card ─────────────────────────────────────────────────

@Composable
private fun ResourceDeploymentCard(summary: ResourceSummary) {
    val deployedRatio = if (summary.total > 0) {
        (summary.dispatched + summary.shadowCommitted).toFloat() / summary.total
    } else 0f
    val animatedRatio by animateFloatAsState(
        targetValue = deployedRatio,
        animationSpec = tween(800),
        label = "deployRatio"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ResourceStatChip(Icons.Default.Shield, "${summary.available}", "Available", CiroColors.AccentGreen)
                ResourceStatChip(Icons.Default.Bolt, "${summary.dispatched}", "Dispatched", CiroColors.AccentRed)
                ResourceStatChip(Icons.Default.Speed, "${summary.shadowCommitted}", "Shadow", CiroColors.AccentOrange)
                ResourceStatChip(Icons.Default.People, "${summary.total}", "Total", CiroColors.AccentCyan)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CiroColors.AccentRed,
                trackColor = CiroColors.AccentGreen.copy(alpha = 0.3f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${String.format("%.0f", deployedRatio * 100)}% deployed",
                color = CiroColors.TextMuted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ResourceStatChip(icon: ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(text = value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = CiroColors.TextMuted, fontSize = 9.sp)
    }
}

// ── Pipeline Stats Row ───────────────────────────────────────────────────────

@Composable
private fun PipelineStatsRow(metrics: List<PipelineMetric>, avgLatencyMs: Long) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PipelineStat(
                label = "Avg Latency",
                value = if (avgLatencyMs > 0) "${avgLatencyMs / 1000.0}s" else "—",
                color = CiroColors.AccentCyan,
            )
            PipelineStat(
                label = "Pipeline Runs",
                value = "${metrics.size}",
                color = CiroColors.AccentPurple,
            )
            PipelineStat(
                label = "Manual Baseline",
                value = "10 min",
                color = CiroColors.TextMuted,
            )
            PipelineStat(
                label = "False Positives",
                value = "${metrics.count { it.false_positive }}",
                color = if (metrics.any { it.false_positive }) CiroColors.AccentRed else CiroColors.AccentGreen,
            )
        }
    }
}

@Composable
private fun PipelineStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = CiroColors.TextMuted, fontSize = 9.sp)
    }
}

// ── Incident Mini Card ───────────────────────────────────────────────────────

@Composable
private fun IncidentMiniCard(incident: Incident, onClick: () -> Unit) {
    val severityColor = CiroColors.severityColor(incident.severity_level)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Severity indicator
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(severityColor.copy(alpha = 0.3f), severityColor.copy(alpha = 0.1f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${incident.severity_level}",
                    color = severityColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = incident.crisis_type.uppercase().replace("_", " "),
                    color = CiroColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "📍 ${incident.location.area_name} · ${incident.resources_allocated.size} units",
                    color = CiroColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }

            // State badge
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = incident.state.replace("_", "\n"),
                    color = CiroColors.stateColor(incident.state),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 12.sp,
                    modifier = Modifier
                        .background(
                            CiroColors.stateColor(incident.state).copy(alpha = 0.12f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${String.format("%.0f", incident.confidence_score * 100)}%",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

// ── Section Title ────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = CiroColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}
