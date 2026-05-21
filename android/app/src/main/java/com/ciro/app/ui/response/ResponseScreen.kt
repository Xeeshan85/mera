package com.ciro.app.ui.response

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.Agency
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.Resource
import com.ciro.app.ui.theme.CiroColors

/**
 * Response Screen — Operations center.
 * Shows active operations timeline, agency cards, and resource readiness.
 */
@Composable
fun ResponseScreen(
    incidents: List<Incident>,
    agencies: List<Agency>,
    resources: List<Resource>,
    onAgencyClick: (String) -> Unit,
    onIncidentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeOps = incidents.filter { it.state == "CONFIRMED" || it.state == "VERIFICATION_REQUESTED" }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // ── Header ───────────────────────────────────────────────
        item {
            Column {
                Text(
                    text = "RESPONSE CENTER",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "${activeOps.size} active operations • ${agencies.size} agencies",
                    color = CiroColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        // ── Resource Readiness ───────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RESOURCE READINESS",
                        color = CiroColors.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.height(12.dp))

                    // Overall readiness — prefer agency resources, fallback to resources collection
                    val agencyResources = agencies.flatMap { it.resources }
                    val allResources = if (agencyResources.isNotEmpty()) agencyResources else emptyList()

                    // Merge: use agency resources for type breakdown, but also include
                    // the resources collection data for overall stats
                    val totalFromResources = resources.size
                    val availFromResources = resources.count { it.state == "AVAILABLE" }
                    val dispatchedFromResources = resources.count { it.state == "DISPATCHED" || it.state == "SHADOW_COMMITTED" }
                    val offlineFromResources = totalFromResources - availFromResources - dispatchedFromResources

                    val total = if (allResources.isNotEmpty()) allResources.size else totalFromResources.coerceAtLeast(1)
                    val available = if (allResources.isNotEmpty()) allResources.count { it.state == "AVAILABLE" } else availFromResources
                    val dispatched = if (allResources.isNotEmpty()) allResources.count { it.state == "DISPATCHED" } else dispatchedFromResources
                    val maintenance = if (allResources.isNotEmpty()) {
                        allResources.count { it.state == "MAINTENANCE" || it.state == "OFF_DUTY" }
                    } else offlineFromResources
                    val readinessPercent = if (total > 0) (available.toFloat() / total * 100).toInt() else 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$readinessPercent%",
                            color = if (readinessPercent > 60) CiroColors.AccentGreen else CiroColors.AccentOrange,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("overall readiness", color = CiroColors.TextMuted, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { available.toFloat() / total },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = CiroColors.AccentGreen,
                                trackColor = CiroColors.SurfaceBorder.copy(alpha = 0.2f),
                                strokeCap = StrokeCap.Round,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ResourceStatChip("Available", available, CiroColors.AccentGreen)
                        ResourceStatChip("Dispatched", dispatched, CiroColors.AccentOrange)
                        ResourceStatChip("Offline", maintenance, CiroColors.TextMuted)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Per-type breakdown — fallback to resources collection when agencies empty
                    val typeData: List<Pair<String, String>> = if (allResources.isNotEmpty()) {
                        allResources.map { it.type to it.state }
                    } else {
                        resources.map { it.type to it.state }
                    }
                    val types = typeData.groupBy { it.first }
                    types.forEach { (type, items) ->
                        val typeAvail = items.count { it.second == "AVAILABLE" }
                        ResourceTypeBar(
                            label = type.replace("_", " ").uppercase(),
                            available = typeAvail,
                            total = items.size,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        // ── Active Operations Timeline ───────────────────────────
        if (activeOps.isNotEmpty()) {
            item {
                Text(
                    text = "ACTIVE OPERATIONS",
                    color = CiroColors.TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
            items(activeOps, key = { it.incident_id }) { incident ->
                OperationTimelineCard(
                    incident = incident,
                    onClick = { onIncidentClick(incident.incident_id) },
                )
            }
        }

        // ── Agencies ─────────────────────────────────────────────
        item {
            Text(
                text = "RESPONDING AGENCIES",
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 16.dp),
            ) {
                items(agencies, key = { it.agency_id }) { agency ->
                    AgencyCard(
                        agency = agency,
                        onClick = { onAgencyClick(agency.agency_id) },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun ResourceStatChip(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            color = CiroColors.TextMuted,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun ResourceTypeBar(label: String, available: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = CiroColors.TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(90.dp),
        )
        LinearProgressIndicator(
            progress = { if (total > 0) available.toFloat() / total else 0f },
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = CiroColors.AccentGreen,
            trackColor = CiroColors.SurfaceBorder.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$available/$total",
            color = CiroColors.TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun OperationTimelineCard(incident: Incident, onClick: () -> Unit) {
    val sevColor = CiroColors.severityColor(incident.severity_level)
    val icon = CiroColors.crisisTypeIcon(incident.crisis_type)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, sevColor.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = incident.crisis_type.uppercase().replace("_", " "),
                        color = CiroColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = incident.location.area_name,
                        color = CiroColors.TextSecondary,
                        fontSize = 10.sp,
                    )
                }
                Text(
                    text = "SEV ${incident.severity_level}",
                    color = sevColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(sevColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Timeline events from audit log
            val events = incident.audit_log.takeLast(4)
            events.forEachIndexed { idx, entry ->
                Row(verticalAlignment = Alignment.Top) {
                    // Timeline dot and line
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(20.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (idx == events.lastIndex) sevColor
                                    else CiroColors.TextMuted.copy(alpha = 0.3f),
                                ),
                        )
                        if (idx < events.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(16.dp)
                                    .background(CiroColors.SurfaceBorder.copy(alpha = 0.2f)),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = entry.action,
                            color = CiroColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = formatTime(entry.timestamp),
                            color = CiroColors.TextMuted,
                            fontSize = 8.sp,
                        )
                    }
                }
            }

            if (events.isEmpty()) {
                Text(
                    text = "Operation in progress...",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun AgencyCard(agency: Agency, onClick: () -> Unit) {
    val availableCount = agency.resources.count { it.state == "AVAILABLE" }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = agency.logo_emoji, fontSize = 28.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = agency.name,
                color = CiroColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = agency.jurisdiction,
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$availableCount",
                        color = CiroColors.AccentGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text("ready", color = CiroColors.TextMuted, fontSize = 8.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${agency.stats.incidents_responded}",
                        color = CiroColors.AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text("ops", color = CiroColors.TextMuted, fontSize = 8.sp)
                }
            }
        }
    }
}

private fun formatTime(iso: String): String = try {
    iso.substringAfter("T").substringBefore(".").take(5)
} catch (_: Exception) { iso.takeLast(5) }
