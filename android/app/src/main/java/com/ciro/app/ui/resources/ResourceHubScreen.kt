package com.ciro.app.ui.resources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.Resource
import com.ciro.app.ui.components.StatusChip
import com.ciro.app.ui.theme.CiroColors
import com.ciro.app.util.IntentUtils
import com.ciro.app.viewmodel.ResourceSummary

/**
 * Resource Hub — fleet management view for authorities.
 * Group resources by type, filter by state, track ETAs.
 */
@Composable
fun ResourceHubScreen(
    resources: List<Resource>,
    summary: ResourceSummary,
    resourcesByType: Map<String, List<Resource>>,
    modifier: Modifier = Modifier,
) {
    var selectedType by remember { mutableStateOf<String?>(null) }

    val filteredResources = if (selectedType == null) resources
    else resources.filter { it.type == selectedType }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // ── Header ───────────────────────────────────────────────
        item {
            Text(
                text = "RESOURCE HUB",
                color = CiroColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Emergency fleet management",
                color = CiroColors.TextSecondary,
                fontSize = 13.sp,
            )
        }

        // ── Summary Row ──────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryChip("Available", summary.available, CiroColors.AccentGreen, Modifier.weight(1f))
                SummaryChip("Deployed", summary.dispatched, CiroColors.AccentRed, Modifier.weight(1f))
                SummaryChip("Standby", summary.shadowCommitted, CiroColors.AccentOrange, Modifier.weight(1f))
                SummaryChip("Total", summary.total, CiroColors.AccentCyan, Modifier.weight(1f))
            }
        }

        // ── Type Filter Chips ────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TypeFilterChip(
                    label = "All",
                    emoji = "🔷",
                    count = resources.size,
                    isSelected = selectedType == null,
                    onClick = { selectedType = null },
                )
                resourcesByType.forEach { (type, list) ->
                    TypeFilterChip(
                        label = typeDisplayName(type),
                        emoji = typeEmoji(type),
                        count = list.size,
                        isSelected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                    )
                }
            }
        }

        // ── Resource Cards ───────────────────────────────────────
        if (filteredResources.isEmpty()) {
            item {
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
                        Text("🚑", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No resources", color = CiroColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Resource data will load from Firestore", color = CiroColors.TextMuted, fontSize = 11.sp)
                    }
                }
            }
        } else {
            items(filteredResources, key = { it.resource_id }) { resource ->
                ResourceCard(resource = resource)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Summary Chip ─────────────────────────────────────────────────────────────

@Composable
private fun SummaryChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$count",
                color = color,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = label,
                color = CiroColors.TextMuted,
                fontSize = 9.sp,
            )
        }
    }
}

// ── Type Filter Chip ─────────────────────────────────────────────────────────

@Composable
private fun TypeFilterChip(
    label: String,
    emoji: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val chipColor = CiroColors.AccentCyan
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) chipColor.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) chipColor.copy(alpha = 0.4f) else CiroColors.SurfaceBorder.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(emoji, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = if (isSelected) chipColor else CiroColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$count",
            color = if (isSelected) chipColor else CiroColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    if (isSelected) chipColor.copy(alpha = 0.1f) else CiroColors.Surface,
                    RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

// ── Resource Card ────────────────────────────────────────────────────────────

@Composable
private fun ResourceCard(resource: Resource) {
    val stateColor = CiroColors.resourceStateColor(resource.state)
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CiroColors.SurfaceCard),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(stateColor),
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Emoji
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(stateColor.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(resource.typeIcon, fontSize = 18.sp)
                }

                Spacer(Modifier.width(12.dp))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resource.unit_name.ifBlank { resource.name }.ifBlank { resource.type },
                        color = CiroColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = typeDisplayName(resource.type),
                        color = CiroColors.TextMuted,
                        fontSize = 10.sp,
                    )
                    if (resource.contact.isNotBlank()) {
                        Text(
                            text = resource.contact,
                            color = CiroColors.TextMuted,
                            fontSize = 9.sp,
                        )
                    }
                }

                // Action icons
                if (resource.contact.isNotBlank()) {
                    IconButton(
                        onClick = { IntentUtils.openDialer(context, resource.contact) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.Call, "Call", tint = CiroColors.AccentGreen, modifier = Modifier.size(16.dp))
                    }
                }
                if (resource.location.lat != 0.0 && resource.location.lng != 0.0) {
                    IconButton(
                        onClick = { IntentUtils.openNavigation(context, resource.location.lat, resource.location.lng) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.Navigation, "Navigate", tint = CiroColors.AccentCyan, modifier = Modifier.size(16.dp))
                    }
                }

                // Right side
                Column(horizontalAlignment = Alignment.End) {
                    StatusChip(text = resource.state, color = stateColor)
                    resource.eta_minutes?.let { eta ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "ETA ${String.format("%.0f", eta)}m",
                            color = CiroColors.AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (resource.capacity > 1) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Cap: ${resource.capacity}",
                            color = CiroColors.TextMuted,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun typeDisplayName(type: String): String = when (type) {
    "ambulance" -> "Ambulance"
    "police_unit" -> "Police"
    "rescue_team" -> "Rescue"
    "water_tanker" -> "Water Tanker"
    "field_team" -> "Field Team"
    "shelter" -> "Shelter"
    "generator" -> "Generator"
    "drone" -> "Drone"
    else -> type.replaceFirstChar { it.uppercase() }.replace("_", " ")
}

private fun typeEmoji(type: String): String = when (type) {
    "ambulance" -> "🚑"
    "police_unit" -> "🚔"
    "rescue_team" -> "⛑️"
    "water_tanker" -> "🚒"
    "field_team" -> "👷"
    "shelter" -> "🏠"
    "generator" -> "⚡"
    "drone" -> "🛸"
    else -> "📍"
}
