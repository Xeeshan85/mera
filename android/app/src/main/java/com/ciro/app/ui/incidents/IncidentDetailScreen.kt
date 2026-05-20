package com.ciro.app.ui.incidents

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.AgentTrace
import com.ciro.app.data.model.CiroNotification
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.Resource
import com.ciro.app.ui.components.SeverityForecastChart
import com.ciro.app.ui.components.StatusChip
import com.ciro.app.ui.theme.CiroColors

/**
 * Incident detail screen — clean, authority-focused operational view.
 */
@Composable
fun IncidentDetailScreen(
    incident: Incident?,
    allocatedResources: List<Resource>,
    relatedTraces: List<AgentTrace>,
    relatedNotifications: List<CiroNotification>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (incident == null) {
        Box(
            modifier = modifier.fillMaxSize().background(CiroColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Text("Incident not found", color = CiroColors.TextMuted, fontSize = 16.sp)
        }
        return
    }

    val severityColor = CiroColors.severityColor(incident.severity_level)
    val crisisIcon = CiroColors.crisisTypeIcon(incident.crisis_type)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Back Button ──────────────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = CiroColors.TextSecondary)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "INCIDENT",
                    color = CiroColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }

        // ── Hero Header ──────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Crisis icon in circle
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(severityColor.copy(alpha = 0.12f))
                        .border(2.dp, severityColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = crisisIcon, fontSize = 36.sp)
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = incident.crisis_type.uppercase().replace("_", " "),
                    color = CiroColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                // Badges row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(text = incident.state.replace("_", " "), color = CiroColors.stateColor(incident.state))
                    StatusChip(text = "SEV ${incident.severity_level}", color = severityColor)
                    StatusChip(
                        text = "${String.format("%.0f", incident.confidence_score * 100)}%",
                        color = CiroColors.AccentCyan,
                    )
                }

                // Confidence bar
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CiroColors.SurfaceBorder.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(incident.confidence_score.toFloat().coerceIn(0f, 1f))
                            .fillMaxSize()
                            .clip(RoundedCornerShape(2.dp))
                            .background(severityColor),
                    )
                }
            }
        }

        // ── Retraction Banner ────────────────────────────────────────
        if (incident.state == "RETRACTED") {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CiroColors.AccentRed.copy(alpha = 0.08f))
                        .border(1.dp, CiroColors.AccentRed.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⚠️", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "FALSE ALARM — RETRACTED",
                            color = CiroColors.AccentRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "All resources recalled",
                            color = CiroColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // ── Stats Grid ───────────────────────────────────────────────
        item {
            SectionLabel("SITUATION")
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatItem("📍 Location", incident.location.area_name.ifBlank { "Islamabad" }, Modifier.weight(1f))
                        StatItem("👥 Population", formatPop(incident.affected_population_estimate), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatItem("⏱️ Duration", "${incident.expected_duration_hours}h", Modifier.weight(1f))
                        StatItem("📡 Spread", incident.spread_risk.uppercase(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatItem("🛡️ Resources", "${incident.resources_allocated.size} units", Modifier.weight(1f))
                        StatItem("📏 Radius", "${incident.location.affected_radius_km}km", Modifier.weight(1f))
                    }
                }
            }
        }

        // ── AI Reasoning ─────────────────────────────────────────────
        val reasoning = relatedTraces
            .firstOrNull { !it.gemini_reasoning.isNullOrBlank() }
            ?.gemini_reasoning

        if (!reasoning.isNullOrBlank()) {
            item {
                SectionLabel("🧠 AI REASONING")
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
                ) {
                    Text(
                        text = reasoning,
                        color = CiroColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // ── Severity Forecast ────────────────────────────────────────
        if (incident.severity_forecast != null) {
            item {
                SectionLabel("📈 SEVERITY FORECAST")
                Spacer(Modifier.height(8.dp))
                SeverityForecastChart(forecast = incident.severity_forecast!!)
            }
        }

        // ── Trade-off ────────────────────────────────────────────────
        if (!incident.trade_off_narrative.isNullOrBlank()) {
            item {
                SectionLabel("⚖️ TRADE-OFF")
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CiroColors.AccentOrange.copy(alpha = 0.06f)
                    ),
                ) {
                    Text(
                        text = incident.trade_off_narrative!!,
                        color = CiroColors.AccentOrange,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // ── Resources ────────────────────────────────────────────────
        if (allocatedResources.isNotEmpty()) {
            item { SectionLabel("🚑 RESOURCES (${allocatedResources.size})") }
            items(allocatedResources, key = { it.resource_id }) { resource ->
                ResourceRow(resource = resource)
            }
        }

        // ── Notifications ────────────────────────────────────────────
        if (relatedNotifications.isNotEmpty()) {
            item { SectionLabel("📢 NOTIFICATIONS (${relatedNotifications.size})") }
            items(relatedNotifications.take(8), key = { it.notification_id }) { notif ->
                NotifRow(notification = notif)
            }
        }

        // ── Conflicting Hypothesis ───────────────────────────────────
        incident.conflicting_hypothesis?.let { conflict ->
            if (conflict.type.isNotBlank()) {
                item {
                    SectionLabel("🔀 ALT HYPOTHESIS")
                    Spacer(Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(CiroColors.crisisTypeIcon(conflict.type), fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = conflict.type.uppercase().replace("_", " "),
                                    color = CiroColors.Severity3,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${String.format("%.0f", conflict.confidence * 100)}%",
                                    color = CiroColors.TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                            if (conflict.evidence_summary.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = conflict.evidence_summary,
                                    color = CiroColors.TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Audit Log ────────────────────────────────────────────────
        if (incident.audit_log.isNotEmpty()) {
            item {
                var expanded by remember { mutableStateOf(false) }
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("📋 AUDIT LOG (${incident.audit_log.size})")
                        Spacer(Modifier.weight(1f))
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle",
                            tint = CiroColors.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    AnimatedVisibility(visible = expanded) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceTerminal),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                incident.audit_log.forEach { entry ->
                                    Column {
                                        Row {
                                            Text(
                                                text = fmtTime(entry.timestamp),
                                                color = CiroColors.TextMuted,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = entry.action,
                                                color = CiroColors.TextTerminalGreen,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        if (entry.from_state != null || entry.to_state != null) {
                                            Text(
                                                text = "  ${entry.from_state ?: "—"} → ${entry.to_state ?: "—"}",
                                                color = CiroColors.TextSecondary,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        }
                                        HorizontalDivider(
                                            color = CiroColors.SurfaceBorder.copy(alpha = 0.15f),
                                            thickness = 0.5.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, color = CiroColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(text = label, color = CiroColors.TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun ResourceRow(resource: Resource) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = resource.typeIcon, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resource.unit_name.ifBlank { resource.name },
                    color = CiroColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = resource.type.uppercase().replace("_", " "),
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(text = resource.state, color = CiroColors.resourceStateColor(resource.state))
                resource.eta_minutes?.let { eta ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "ETA ${String.format("%.0f", eta)}m",
                        color = CiroColors.AccentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotifRow(notification: CiroNotification) {
    val isRetraction = notification.is_retraction
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRetraction) CiroColors.AccentRed.copy(alpha = 0.05f) else CiroColors.SurfaceCard,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (notification.stakeholder_type) {
                    "public" -> "📢"; "emergency_services" -> "🚨"; "hospital" -> "🏥"
                    "utility" -> "⚡"; "transport" -> "🚗"; else -> "📡"
                },
                fontSize = 16.sp,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.message_title,
                    color = if (isRetraction) CiroColors.AccentRed else CiroColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = notification.stakeholder_type.replace("_", " ").uppercase(),
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                )
            }
            Text(fmtTime(notification.sent_at), color = CiroColors.TextMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = CiroColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

private fun formatPop(pop: Int): String = when {
    pop >= 1_000_000 -> "${pop / 1_000_000}M"
    pop >= 1_000 -> "${pop / 1_000}K"
    else -> "$pop"
}

private fun fmtTime(iso: String): String = try {
    iso.substringAfter("T").substringBefore(".").take(5)
} catch (_: Exception) { iso.takeLast(5) }
