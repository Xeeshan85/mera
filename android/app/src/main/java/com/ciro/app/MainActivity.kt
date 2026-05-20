package com.ciro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ciro.app.ui.dashboard.DashboardScreen
import com.ciro.app.ui.map.CrisisMapScreen
import com.ciro.app.ui.notifications.NotificationsScreen
import com.ciro.app.ui.theme.CiroColors
import com.ciro.app.viewmodel.DashboardViewModel

/**
 * Main entry point for the CIRO Android app.
 *
 * Uses a bottom navigation bar with 3 tabs:
 *   1. Dashboard — command center with cards, charts, agent traces
 *   2. Map      — Google Maps with live incident/resource markers
 *   3. Alerts   — notification feed from all stakeholder channels
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CiroApp()
        }
    }
}

@Composable
fun CiroApp(viewModel: DashboardViewModel = viewModel()) {
    // Collect all live flows
    val incidents by viewModel.incidents.collectAsState()
    val resources by viewModel.resources.collectAsState()
    val agentTraces by viewModel.agentTraces.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val notifications by viewModel.notifications.collectAsState()

    // Computed stats
    val severityCounts = viewModel.severityCounts(incidents)
    val resourceSummary = viewModel.resourceSummary(resources)
    val avgLatencyMs = viewModel.avgLatencyMs(metrics)
    val improvementHeadline = viewModel.improvementHeadline(metrics)

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = CiroColors.Surface,
        bottomBar = {
            CiroBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        },
    ) { innerPadding ->
        when (selectedTab) {
            0 -> DashboardScreen(
                incidents = incidents,
                resources = resources,
                agentTraces = agentTraces,
                metrics = metrics,
                severityCounts = severityCounts,
                resourceSummary = resourceSummary,
                avgLatencyMs = avgLatencyMs,
                improvementHeadline = improvementHeadline,
                onIncidentClick = { /* TODO: navigate to incident detail */ },
                modifier = Modifier.padding(innerPadding),
            )
            1 -> CrisisMapScreen(
                incidents = incidents,
                resources = resources,
                modifier = Modifier.padding(innerPadding),
            )
            2 -> NotificationsScreen(
                notifications = notifications,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

// ── Bottom Navigation ────────────────────────────────────────────────────────

private data class NavItem(val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("Dashboard", Icons.Default.Dashboard),
    NavItem("Map", Icons.Default.Map),
    NavItem("Alerts", Icons.Default.Notifications),
)

@Composable
private fun CiroBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = CiroColors.SurfaceCard,
        contentColor = CiroColors.TextPrimary,
    ) {
        navItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CiroColors.AccentCyan,
                    selectedTextColor = CiroColors.AccentCyan,
                    unselectedIconColor = CiroColors.TextMuted,
                    unselectedTextColor = CiroColors.TextMuted,
                    indicatorColor = CiroColors.AccentCyan.copy(alpha = 0.12f),
                ),
            )
        }
    }
}
