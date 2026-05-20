package com.ciro.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.PipelineMetric
import com.ciro.app.data.model.Resource
import com.ciro.app.ui.components.AnimatedCounter
import com.ciro.app.ui.components.PulsingDot
import com.ciro.app.ui.components.StatusChip
import com.ciro.app.ui.theme.CiroColors
import com.ciro.app.viewmodel.ResourceSummary

/**
 * Main dashboard — clean, focused command-center view.
 *
 * Layout:
 *   1. Header with branding + live indicator
 *   2. Severity overview row (only 3 key levels)
 *   3. Resource deployment bar
 *   4. Pipeline speed card
 *   5. Active incidents list
 */
@Composable
fun DashboardScreen(
    incidents: List<Incident>,
    resources: List<Resource>,
    metrics: List<PipelineMetric>,
    severityCounts: Map<Int, Int>,
    resourceSummary: ResourceSummary,
    avgLatencyMs: Long,
    improvementHeadline: String,
    falsePositiveCount: Int,
    onIncidentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── Header ───────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            HeaderSection(
                activeCount = incidents.count { !it.isTerminal },
                avgLatencyMs = avgLatencyMs,
            )
        }

        // ── Severity Overview ────────────────────────────────────────
        item {
            SeverityOverviewSection(severityCounts = severityCounts)
        }

        // ── Resource + Pipeline Row ──────────────────────────────────
        item {
            ResourceAndPipelineSection(
                summary = resourceSummary,
                avgLatencyMs = avgLatencyMs,
                pipelineRuns = metrics.size,
                falsePositives = falsePositiveCount,
            )
        }

        // ── Active Incidents ─────────────────────────────────────────
        item {
            Text(
                text = "INCIDENTS",
                color = CiroColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }

        if (incidents.isEmpty()) {
            item { EmptyIncidentsCard() }
        } else {
            items(incidents, key = { it.incident_id }) { incident ->
                IncidentCard(
                    incident = incident,
                    onClick = { onIncidentClick(incident.incident_id) },
                )
            }
        }

        // Bottom spacer for nav bar
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun HeaderSection(activeCount: Int, avgLatencyMs: Long) {
    Column {
        // CIRO title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "barwaqt",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = CiroColors.AccentCyan,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.width(10.dp))
            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(CiroColors.SurfaceBorder)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Command Center",
                    color = CiroColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "NDMA · Pakistan",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Status row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PulsingDot(color = CiroColors.AccentGreen, size = 6.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "LIVE",
                color = CiroColors.AccentGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$activeCount active",
                color = CiroColors.TextSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.weight(1f))

            // AI Speed badge
            if (avgLatencyMs > 0) {
                val seconds = String.format("%.1f", avgLatencyMs / 1000.0)
                Text(
                    text = "⚡ AI: ${seconds}s",
                    color = CiroColors.AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            CiroColors.AccentGreen.copy(alpha = 0.1f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ── Severity Overview ────────────────────────────────────────────────────────

@Composable
private fun SeverityOverviewSection(severityCounts: Map<Int, Int>) {
    val totalActive = severityCounts.values.sum()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "THREAT LEVEL",
                color = CiroColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SeverityPill(5, "CRIT", severityCounts[5] ?: 0)
                SeverityPill(4, "SEV", severityCounts[4] ?: 0)
                SeverityPill(3, "SIG", severityCounts[3] ?: 0)
                SeverityPill(2, "MOD", severityCounts[2] ?: 0)
                SeverityPill(1, "MIN", severityCounts[1] ?: 0)
            }

            if (totalActive > 0) {
                Spacer(Modifier.height(14.dp))
                // Severity distribution bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CiroColors.SurfaceBorder.copy(alpha = 0.2f)),
                ) {
                    for (level in 5 downTo 1) {
                        val count = severityCounts[level] ?: 0
                        if (count > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(count.toFloat())
                                    .fillMaxSize()
                                    .background(CiroColors.severityColor(level))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityPill(level: Int, label: String, count: Int) {
    val color = CiroColors.severityColor(level)
    val isActive = count > 0

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Count circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) color.copy(alpha = 0.15f) else CiroColors.SurfaceBorder.copy(alpha = 0.1f)
                )
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) color.copy(alpha = 0.6f) else CiroColors.SurfaceBorder.copy(alpha = 0.2f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedCounter(
                targetValue = count,
                color = if (isActive) color else CiroColors.TextMuted,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = if (isActive) color.copy(alpha = 0.8f) else CiroColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

// ── Resource + Pipeline Section ──────────────────────────────────────────────

@Composable
private fun ResourceAndPipelineSection(
    summary: ResourceSummary,
    avgLatencyMs: Long,
    pipelineRuns: Int,
    falsePositives: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Resource card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "RESOURCES",
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${summary.available}",
                        color = CiroColors.AccentGreen,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "/${summary.total}",
                        color = CiroColors.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Text(
                    text = "available",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                )

                Spacer(Modifier.height(10.dp))

                // Mini bar
                val deployedRatio = if (summary.total > 0) {
                    (summary.dispatched + summary.shadowCommitted).toFloat() / summary.total
                } else 0f
                val animatedRatio by animateFloatAsState(
                    targetValue = deployedRatio,
                    animationSpec = tween(800),
                    label = "deploy",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CiroColors.SurfaceBorder.copy(alpha = 0.2f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedRatio)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(CiroColors.AccentRed, CiroColors.AccentOrange)
                                )
                            ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${summary.dispatched} deployed · ${summary.shadowCommitted} standby",
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                )
            }
        }

        // Pipeline card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "PIPELINE",
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = when {
                            avgLatencyMs <= 0 -> "—"
                            avgLatencyMs < 1000 -> "${avgLatencyMs}ms"
                            else -> "${String.format("%.1f", avgLatencyMs / 1000.0)}s"
                        },
                        color = CiroColors.AccentCyan,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    text = "avg latency",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("$pipelineRuns", color = CiroColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("runs", color = CiroColors.TextMuted, fontSize = 9.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$falsePositives",
                            color = if (falsePositives > 0) CiroColors.AccentOrange else CiroColors.AccentGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("caught", color = CiroColors.TextMuted, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

// ── Incident Card ────────────────────────────────────────────────────────────

@Composable
private fun IncidentCard(incident: Incident, onClick: () -> Unit) {
    val severityColor = CiroColors.severityColor(incident.severity_level)
    val crisisIcon = CiroColors.crisisTypeIcon(incident.crisis_type)
    val isRetracted = incident.state == "RETRACTED"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRetracted) CiroColors.SurfaceCard.copy(alpha = 0.5f)
            else CiroColors.SurfaceCard,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left severity accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(if (isRetracted) CiroColors.TextMuted else severityColor),
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Crisis emoji
                Text(text = crisisIcon, fontSize = 28.sp)

                Spacer(Modifier.width(12.dp))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = incident.crisis_type.uppercase().replace("_", " "),
                        color = if (isRetracted) CiroColors.TextMuted else CiroColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${incident.location.area_name} · ${incident.resources_allocated.size} units",
                        color = CiroColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Right: state chip
                Column(horizontalAlignment = Alignment.End) {
                    StatusChip(
                        text = incident.state.replace("_", " "),
                        color = CiroColors.stateColor(incident.state),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Sev ${incident.severity_level}",
                        color = severityColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyIncidentsCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🛡️", fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "All clear",
                color = CiroColors.AccentGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "No active incidents in Islamabad",
                color = CiroColors.TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}
