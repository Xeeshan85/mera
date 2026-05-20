package com.ciro.app.ui.pulse

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.LiveUpdate
import com.ciro.app.data.model.Resource
import com.ciro.app.ui.components.BreakingTicker
import com.ciro.app.ui.components.PakistanSilhouette
import com.ciro.app.ui.components.ThreatGauge
import com.ciro.app.ui.theme.CiroColors
import kotlinx.coroutines.delay

/**
 * Pulse Screen — The heartbeat of the nation.
 * Shows threat gauge, breaking ticker, Pakistan silhouette with zones,
 * city situation card, and horizontal incident carousel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PulseScreen(
    incidents: List<Incident>,
    liveUpdates: List<LiveUpdate>,
    resources: List<Resource>,
    onIncidentClick: (String) -> Unit,
    onAdminLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compute threat level from active confirmed incidents
    val activeIncidents = incidents.filter { it.state == "CONFIRMED" || it.state == "VERIFICATION_REQUESTED" }
    val maxSeverity = activeIncidents.maxOfOrNull { it.severity_level } ?: 0
    val threatLevel = when {
        activeIncidents.isEmpty() -> 0
        maxSeverity >= 5 -> 5
        maxSeverity >= 4 -> 4
        activeIncidents.size >= 3 -> 4
        maxSeverity >= 3 -> 3
        activeIncidents.size >= 2 -> 3
        maxSeverity >= 2 -> 2
        else -> 1
    }

    // Ticker auto-rotate
    var tickerIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(liveUpdates.size) {
        while (true) {
            delay(5000)
            if (liveUpdates.isNotEmpty()) {
                tickerIndex = (tickerIndex + 1) % liveUpdates.size
            }
        }
    }

    // Province threat computation
    val provinceThreatMap = remember(activeIncidents) {
        val map = mutableMapOf<String, Int>()
        activeIncidents.forEach { inc ->
            val area = inc.location.area_name.lowercase()
            val province = when {
                area.contains("islamabad") || area.contains("g-10") || area.contains("i-8")
                    || area.contains("f-6") || area.contains("f-7") || area.contains("g-11") -> "ICT"
                area.contains("lahore") || area.contains("punjab") || area.contains("rawalpindi") -> "PB"
                area.contains("karachi") || area.contains("sindh") -> "SD"
                area.contains("peshawar") || area.contains("kp") -> "KP"
                area.contains("quetta") || area.contains("balochistan") -> "BL"
                else -> "ICT"
            }
            map[province] = maxOf(map[province] ?: 0, inc.severity_level)
        }
        map
    }
    val provinceIncidentMap = remember(activeIncidents) {
        val map = mutableMapOf<String, Int>()
        activeIncidents.forEach { inc ->
            val area = inc.location.area_name.lowercase()
            val province = when {
                area.contains("islamabad") || area.contains("g-") || area.contains("i-")
                    || area.contains("f-") -> "ICT"
                area.contains("lahore") || area.contains("punjab") || area.contains("rawalpindi") -> "PB"
                area.contains("karachi") || area.contains("sindh") -> "SD"
                area.contains("peshawar") || area.contains("kp") -> "KP"
                area.contains("quetta") || area.contains("balochistan") -> "BL"
                else -> "ICT"
            }
            map[province] = (map[province] ?: 0) + 1
        }
        map
    }

    // Resource stats
    val totalResources = resources.size
    val availableResources = resources.count { it.state == "AVAILABLE" }
    val affectedPop = activeIncidents.sumOf { it.affected_population_estimate }

    // AI activity
    val pulse = rememberInfiniteTransition(label = "ai_pulse")
    val aiDot by pulse.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "ai_dot",
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Header with hidden admin ──────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { onAdminLongPress() },
                        )
                    },
                ) {
                    Text(
                        text = "barwaqt",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = CiroColors.AccentCyan,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = "Crisis Intelligence • Pakistan",
                        color = CiroColors.TextMuted,
                        fontSize = 10.sp,
                    )
                }
                // Live indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(CiroColors.AccentGreen.copy(alpha = aiDot)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "LIVE",
                        color = CiroColors.AccentGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }

        // ── Breaking Ticker ──────────────────────────────────────
        if (liveUpdates.isNotEmpty()) {
            item {
                BreakingTicker(
                    updates = liveUpdates,
                    currentIndex = tickerIndex,
                    onTap = { update ->
                        update.incident_id?.let { onIncidentClick(it) }
                    },
                )
            }
        }

        // ── Threat Gauge + Pakistan Silhouette ────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThreatGauge(
                    level = threatLevel,
                    activeIncidents = activeIncidents.size,
                    size = 150.dp,
                )

                PakistanSilhouette(
                    provinceThreatLevels = provinceThreatMap,
                    provinceIncidentCounts = provinceIncidentMap,
                    size = 150.dp,
                )
            }
        }

        // ── City Situation Snapshot ───────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SITUATION SNAPSHOT",
                        color = CiroColors.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        SituationStat("⚠️", "${activeIncidents.size}", "Active")
                        SituationStat("👥", formatPop(affectedPop), "At Risk")
                        SituationStat("🚑", "$availableResources/$totalResources", "Available")
                        SituationStat("🛡️", "${incidents.count { it.state == "RESOLVED" }}", "Resolved")
                    }
                }
            }
        }

        // ── Active Incidents Carousel ────────────────────────────
        if (activeIncidents.isNotEmpty()) {
            item {
                Text(
                    text = "ACTIVE INCIDENTS",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 16.dp),
                ) {
                    items(activeIncidents, key = { it.incident_id }) { incident ->
                        IncidentCard(
                            incident = incident,
                            onClick = { onIncidentClick(incident.incident_id) },
                        )
                    }
                }
            }
        }

        // ── AI Activity ──────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CiroColors.SurfaceTerminal.copy(alpha = 0.5f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CiroColors.AccentCyan.copy(alpha = aiDot)),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "barwaqt AI monitoring ${if (activeIncidents.isEmpty()) "3 signal sources" else "${activeIncidents.size} active crisis zones"}",
                        color = CiroColors.TextTerminalGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "Signal fusion • Severity prediction • Resource optimization",
                        color = CiroColors.TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // ── Recent Resolved (if any) ─────────────────────────────
        val recentResolved = incidents
            .filter { it.state == "RESOLVED" || it.state == "RETRACTED" }
            .take(3)

        if (recentResolved.isNotEmpty()) {
            item {
                Text(
                    text = "RECENTLY RESOLVED",
                    color = CiroColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
            items(recentResolved, key = { it.incident_id }) { incident ->
                ResolvedRow(
                    incident = incident,
                    onClick = { onIncidentClick(incident.incident_id) },
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun SituationStat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = emoji, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = CiroColors.TextPrimary,
            fontSize = 16.sp,
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
private fun IncidentCard(incident: Incident, onClick: () -> Unit) {
    val sevColor = CiroColors.severityColor(incident.severity_level)
    val icon = CiroColors.crisisTypeIcon(incident.crisis_type)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick)
            .border(1.dp, sevColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = incident.crisis_type.uppercase().replace("_", " "),
                        color = sevColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = incident.location.area_name,
                        color = CiroColors.TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("SEV ${incident.severity_level}", color = sevColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Severity", color = CiroColors.TextMuted, fontSize = 8.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(incident.confidence_score * 100).toInt()}%",
                        color = CiroColors.AccentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("AI Conf.", color = CiroColors.TextMuted, fontSize = 8.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${incident.resources_allocated.size}",
                        color = CiroColors.AccentGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Units", color = CiroColors.TextMuted, fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
private fun ResolvedRow(incident: Incident, onClick: () -> Unit) {
    val isRetracted = incident.state == "RETRACTED"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isRetracted) CiroColors.AccentRed.copy(alpha = 0.05f)
                else CiroColors.AccentGreen.copy(alpha = 0.05f),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = CiroColors.crisisTypeIcon(incident.crisis_type),
            fontSize = 18.sp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = incident.crisis_type.uppercase().replace("_", " "),
                color = CiroColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = incident.location.area_name,
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
            )
        }
        Text(
            text = if (isRetracted) "RETRACTED" else "RESOLVED",
            color = if (isRetracted) CiroColors.AccentRed else CiroColors.AccentGreen,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    (if (isRetracted) CiroColors.AccentRed else CiroColors.AccentGreen).copy(alpha = 0.1f),
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun formatPop(pop: Int): String = when {
    pop >= 1_000_000 -> "${pop / 1_000_000}M"
    pop >= 1_000 -> String.format("%.1fK", pop / 1000.0)
    else -> "$pop"
}
