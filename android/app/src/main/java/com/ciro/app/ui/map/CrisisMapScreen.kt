package com.ciro.app.ui.map

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.Resource
import com.ciro.app.ui.theme.CiroColors
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Full-screen Google Maps view with:
 *   • Incident markers colour-coded by severity (BitmapDescriptorFactory hue)
 *   • Affected-radius circle overlays
 *   • Resource markers (different hue per resource type)
 *   • Bottom sheet with incident details on marker tap
 *
 * Islamabad centre: 33.6844°N, 73.0479°E
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrisisMapScreen(
    incidents: List<Incident>,
    resources: List<Resource>,
    modifier: Modifier = Modifier,
) {
    // Islamabad centre
    val islamabadCenter = LatLng(33.6938, 73.0550)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(islamabadCenter, 12.5f)
    }

    var selectedIncident by remember { mutableStateOf<Incident?>(null) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            IncidentBottomSheet(incident = selectedIncident)
        },
        sheetPeekHeight = if (selectedIncident != null) 240.dp else 0.dp,
        sheetContainerColor = CiroColors.SurfaceCard,
        sheetContentColor = CiroColors.TextPrimary,
        containerColor = CiroColors.Surface,
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraState,
                properties = MapProperties(
                    mapType = MapType.NORMAL,
                    isMyLocationEnabled = false,
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    compassEnabled = true,
                    mapToolbarEnabled = false,
                ),
            ) {
                // ── Incident Markers + Radius Circles ────────────────
                incidents.forEach { incident ->
                    val position = LatLng(incident.location.lat, incident.location.lng)
                    val hue = severityToHue(incident.severity_level)

                    Marker(
                        state = MarkerState(position = position),
                        title = "${incident.crisis_type.uppercase()} — ${incident.location.area_name}",
                        snippet = "Severity ${incident.severity_level} | ${incident.state} | Confidence ${
                            String.format("%.0f", incident.confidence_score * 100)
                        }%",
                        icon = BitmapDescriptorFactory.defaultMarker(hue),
                        onClick = {
                            selectedIncident = incident
                            false // return false to show info window
                        },
                    )

                    // Affected radius circle overlay
                    Circle(
                        center = position,
                        radius = incident.location.affected_radius_km * 1000.0, // km → m
                        strokeColor = CiroColors.severityColor(incident.severity_level).copy(alpha = 0.7f),
                        fillColor = CiroColors.severityColor(incident.severity_level).copy(alpha = 0.12f),
                        strokeWidth = 2f,
                    )
                }

                // ── Resource Markers ─────────────────────────────────
                resources.forEach { resource ->
                    val position = LatLng(resource.location.lat, resource.location.lng)
                    val hue = resourceTypeToHue(resource.type)
                    val alpha = if (resource.isAvailable) 1f else 0.5f

                    Marker(
                        state = MarkerState(position = position),
                        title = "${resource.typeIcon} ${resource.unit_name}",
                        snippet = "${resource.type.uppercase()} | ${resource.state} | ${resource.location.name}",
                        icon = BitmapDescriptorFactory.defaultMarker(hue),
                        alpha = alpha,
                    )
                }
            }

            // ── Map legend overlay ───────────────────────────────────
            MapLegend(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            )
        }
    }
}

// ── Bottom Sheet ─────────────────────────────────────────────────────────────

@Composable
private fun IncidentBottomSheet(incident: Incident?) {
    if (incident == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Tap an incident marker", color = CiroColors.TextSecondary, fontSize = 14.sp)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        // Header row: crisis type + severity badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(CiroColors.severityColor(incident.severity_level))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = incident.crisis_type.uppercase().replace("_", " "),
                color = CiroColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = incident.state,
                color = CiroColors.stateColor(incident.state),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(
                        CiroColors.stateColor(incident.state).copy(alpha = 0.15f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Stats grid
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem("Severity", "${incident.severity_level}/5", Modifier.weight(1f))
            StatItem("Confidence", "${String.format("%.0f", incident.confidence_score * 100)}%", Modifier.weight(1f))
            StatItem("Population", "${incident.affected_population_estimate}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItem("Location", incident.location.area_name, Modifier.weight(1f))
            StatItem("Duration", "${incident.expected_duration_hours}h", Modifier.weight(1f))
            StatItem("Spread", incident.spread_risk.uppercase(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        // Resources allocated
        if (incident.resources_allocated.isNotEmpty()) {
            Text("Resources: ${incident.resources_allocated.size} units", color = CiroColors.TextSecondary, fontSize = 12.sp)
        }

        // Trade-off narrative
        incident.trade_off_narrative?.let { narrative ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "⚖️ $narrative",
                color = CiroColors.AccentOrange,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        // Retraction banner
        if (incident.state == "RETRACTED") {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "⚠️ THIS INCIDENT WAS RETRACTED — FALSE ALARM",
                color = CiroColors.AccentRed,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CiroColors.AccentRed.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 2.dp)) {
        Text(text = label, color = CiroColors.TextMuted, fontSize = 10.sp)
        Text(text = value, color = CiroColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Map Legend ───────────────────────────────────────────────────────────────

@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(CiroColors.SurfaceCard.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text("CIRO Live Map", color = CiroColors.AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        LegendRow(CiroColors.Severity5, "Sev 5 — Catastrophic")
        LegendRow(CiroColors.Severity4, "Sev 4 — Severe")
        LegendRow(CiroColors.Severity3, "Sev 3 — Significant")
        LegendRow(CiroColors.Severity2, "Sev 2 — Moderate")
        LegendRow(CiroColors.Severity1, "Sev 1 — Minor")
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = CiroColors.TextSecondary, fontSize = 9.sp)
    }
}

// ── Hue Mapping ─────────────────────────────────────────────────────────────

/** Map severity 1–5 to Google Maps marker hue values (0–360). */
private fun severityToHue(level: Int): Float = when (level) {
    5 -> BitmapDescriptorFactory.HUE_RED           // 0f
    4 -> BitmapDescriptorFactory.HUE_ORANGE        // 30f
    3 -> BitmapDescriptorFactory.HUE_YELLOW        // 60f
    2 -> BitmapDescriptorFactory.HUE_AZURE         // 210f
    1 -> BitmapDescriptorFactory.HUE_GREEN         // 120f
    else -> BitmapDescriptorFactory.HUE_VIOLET
}

/** Map resource type to a distinct marker hue for visual separation. */
private fun resourceTypeToHue(type: String): Float = when (type) {
    "ambulance" -> BitmapDescriptorFactory.HUE_ROSE        // 330f
    "police_unit" -> BitmapDescriptorFactory.HUE_BLUE      // 240f
    "rescue_team" -> BitmapDescriptorFactory.HUE_CYAN      // 180f
    "water_tanker" -> BitmapDescriptorFactory.HUE_AZURE    // 210f
    "field_team" -> BitmapDescriptorFactory.HUE_MAGENTA    // 300f
    "shelter" -> BitmapDescriptorFactory.HUE_GREEN         // 120f
    "generator" -> BitmapDescriptorFactory.HUE_YELLOW      // 60f
    else -> BitmapDescriptorFactory.HUE_VIOLET
}
