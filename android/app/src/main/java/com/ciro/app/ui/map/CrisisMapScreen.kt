package com.ciro.app.ui.map

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.Resource
import com.ciro.app.ui.components.StatusChip
import com.ciro.app.ui.theme.CiroColors
import com.ciro.app.util.IntentUtils
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Crisis map screen — simplified and functional.
 * Shows incident markers and resource markers on Google Maps.
 * Tapping an incident marker shows a detail panel at the bottom.
 */
@Composable
fun CrisisMapScreen(
    incidents: List<Incident>,
    resources: List<Resource>,
    onIncidentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val islamabadCenter = LatLng(33.6938, 73.0550)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(islamabadCenter, 12f)
    }

    var selectedIncident by remember { mutableStateOf<Incident?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CiroColors.Surface),
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(mapType = MapType.NORMAL),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                compassEnabled = true,
                mapToolbarEnabled = false,
            ),
        ) {
            // Incident markers
            incidents.forEach { incident ->
                val pos = LatLng(incident.location.lat, incident.location.lng)
                val hue = when (incident.severity_level) {
                    5 -> BitmapDescriptorFactory.HUE_RED
                    4 -> BitmapDescriptorFactory.HUE_ORANGE
                    3 -> BitmapDescriptorFactory.HUE_YELLOW
                    2 -> BitmapDescriptorFactory.HUE_AZURE
                    else -> BitmapDescriptorFactory.HUE_GREEN
                }

                Marker(
                    state = MarkerState(position = pos),
                    title = "${CiroColors.crisisTypeIcon(incident.crisis_type)} ${incident.crisis_type.uppercase().replace("_", " ")}",
                    snippet = "Sev ${incident.severity_level} · ${incident.state} · ${incident.location.area_name}",
                    icon = BitmapDescriptorFactory.defaultMarker(hue),
                    onClick = {
                        selectedIncident = incident
                        false
                    },
                )
            }

            // Resource markers
            resources.forEach { resource ->
                val pos = LatLng(resource.location.lat, resource.location.lng)
                Marker(
                    state = MarkerState(position = pos),
                    title = "${resource.typeIcon} ${resource.unit_name.ifBlank { resource.name }}",
                    snippet = "${resource.type.uppercase()} · ${resource.state}",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN),
                    alpha = if (resource.isAvailable) 1f else 0.5f,
                )
            }
        }

        // Bottom detail panel
        selectedIncident?.let { incident ->
            IncidentMapPanel(
                incident = incident,
                onViewDetails = {
                    onIncidentClick(incident.incident_id)
                    selectedIncident = null
                },
                onDismiss = { selectedIncident = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun IncidentMapPanel(
    incident: Incident,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val severityColor = CiroColors.severityColor(incident.severity_level)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CiroColors.SurfaceCard.copy(alpha = 0.95f))
            .clickable(enabled = false, onClick = {})
            .padding(16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = CiroColors.crisisTypeIcon(incident.crisis_type),
                fontSize = 28.sp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = incident.crisis_type.uppercase().replace("_", " "),
                    color = CiroColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = incident.location.area_name,
                    color = CiroColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
            StatusChip(text = "SEV ${incident.severity_level}", color = severityColor)
        }

        Spacer(Modifier.height(12.dp))

        // Stats
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem("State", incident.state.replace("_", " "), Modifier.weight(1f))
            StatItem("Confidence", "${String.format("%.0f", incident.confidence_score * 100)}%", Modifier.weight(1f))
            StatItem("Resources", "${incident.resources_allocated.size} units", Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        // Action buttons
        val context = LocalContext.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Navigate
            Button(
                onClick = {
                    IntentUtils.openNavigation(context, incident.location.lat, incident.location.lng)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CiroColors.AccentGreen,
                    contentColor = CiroColors.Surface,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.Navigation, "Navigate", modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Navigate", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            // View Details
            Button(
                onClick = onViewDetails,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CiroColors.AccentCyan,
                    contentColor = CiroColors.TextOnAccent,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Details", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            // Dismiss
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(0.7f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CiroColors.SurfaceBorder,
                    contentColor = CiroColors.TextSecondary,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Dismiss", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 2.dp)) {
        Text(text = value, color = CiroColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(text = label, color = CiroColors.TextMuted, fontSize = 10.sp)
    }
}
