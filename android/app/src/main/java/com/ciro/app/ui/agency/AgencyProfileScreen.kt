package com.ciro.app.ui.agency

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.Agency
import com.ciro.app.data.model.AgencyResource
import com.ciro.app.ui.theme.CiroColors

/**
 * Agency Profile Screen — Shows agency details, resource inventory,
 * and performance statistics.
 */
@Composable
fun AgencyProfileScreen(
    agency: Agency?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (agency == null) {
        Box(
            modifier = modifier.fillMaxSize().background(CiroColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Text("Agency not found", color = CiroColors.TextMuted, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Header ───────────────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = CiroColors.TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Text(text = agency.logo_emoji, fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = agency.name,
                        color = CiroColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = agency.full_name,
                        color = CiroColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ── About ────────────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow("Jurisdiction", agency.jurisdiction)
                    InfoRow("Contact", agency.contact)
                    InfoRow("Type", agency.type.replace("_", " ").uppercase())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = agency.description,
                        color = CiroColors.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }

        // ── Performance Stats ────────────────────────────────────
        item {
            Text(
                text = "PERFORMANCE",
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    value = "${agency.stats.incidents_responded}",
                    label = "Incidents Responded",
                    color = CiroColors.AccentCyan,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = String.format("%.1f min", agency.stats.avg_response_time_min),
                    label = "Avg Response Time",
                    color = CiroColors.AccentGreen,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    value = "${agency.stats.resources_deployed_total}",
                    label = "Total Deployments",
                    color = CiroColors.AccentOrange,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = "${agency.stats.false_alarms_handled}",
                    label = "False Alarms Handled",
                    color = CiroColors.AccentPurple,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Resource Inventory ───────────────────────────────────
        item {
            Text(
                text = "RESOURCE INVENTORY",
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }

        val grouped = agency.resources.groupBy { it.type }
        grouped.forEach { (type, resources) ->
            item {
                Text(
                    text = type.replace("_", " ").uppercase(),
                    color = CiroColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(resources, key = { it.resource_id }) { resource ->
                ResourceRow(resource)
            }
        }

        // ── Readiness summary ────────────────────────────────────
        item {
            val total = agency.resources.size.coerceAtLeast(1)
            val available = agency.resources.count { it.state == "AVAILABLE" }
            val readinessPercent = (available.toFloat() / total * 100).toInt()

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "READINESS",
                            color = CiroColors.TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = "$readinessPercent%",
                            color = if (readinessPercent > 60) CiroColors.AccentGreen else CiroColors.AccentOrange,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "$available of $total resources available",
                        color = CiroColors.TextMuted,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = CiroColors.TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            color = CiroColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResourceRow(resource: AgencyResource) {
    val stateColor = when (resource.state) {
        "AVAILABLE" -> CiroColors.AccentGreen
        "DISPATCHED" -> CiroColors.AccentRed
        "MAINTENANCE" -> CiroColors.AccentOrange
        "OFF_DUTY" -> CiroColors.TextMuted
        else -> CiroColors.TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CiroColors.SurfaceCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // State indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(stateColor),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = resource.unit_name,
                color = CiroColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Capacity: ${resource.capacity}",
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
            )
        }
        Text(
            text = resource.state.replace("_", " "),
            color = stateColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(stateColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
