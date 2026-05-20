package com.ciro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ciro.app.ui.analytics.AnalyticsScreen
import com.ciro.app.ui.brain.AIBrainScreen
import com.ciro.app.ui.components.CrisisAlertOverlay
import com.ciro.app.ui.dashboard.DashboardScreen
import com.ciro.app.ui.incidents.IncidentDetailScreen
import com.ciro.app.ui.map.CrisisMapScreen
import com.ciro.app.ui.notifications.NotificationsScreen
import com.ciro.app.ui.resources.ResourceHubScreen
import com.ciro.app.ui.splash.SplashScreen
import com.ciro.app.ui.theme.CiroColors
import com.ciro.app.ui.theme.CiroTheme
import com.ciro.app.viewmodel.DashboardViewModel
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Entry point for the CIRO Android application.
 *
 * Wraps the entire app in CiroTheme, sets up Navigation Compose
 * for Splash → Main (5 tabs) → Incident Detail,
 * and shows a CrisisAlertOverlay when new CONFIRMED incidents arrive.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Subscribe to FCM topics for push notifications
        FirebaseMessaging.getInstance().subscribeToTopic("public_alerts")
        FirebaseMessaging.getInstance().subscribeToTopic("admin")

        setContent {
            CiroTheme {
                CiroApp()
            }
        }
    }
}

// ── Navigation Routes ────────────────────────────────────────────────────────

object CiroRoutes {
    const val SPLASH = "splash"
    const val MAIN = "main"
    const val INCIDENT_DETAIL = "incident/{incidentId}"

    fun incidentDetail(id: String) = "incident/$id"
}

// ── Root App ─────────────────────────────────────────────────────────────────

@Composable
fun CiroApp() {
    val navController = rememberNavController()
    val viewModel: DashboardViewModel = viewModel()

    // Observe for crisis alerts
    val incidents by viewModel.incidents.collectAsState()
    val crisisAlertIncident by viewModel.crisisAlertIncident.collectAsState()

    LaunchedEffect(incidents) {
        viewModel.checkForCrisisAlert(incidents)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = CiroRoutes.SPLASH,
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Splash ───────────────────────────────────────────
            composable(
                route = CiroRoutes.SPLASH,
                enterTransition = { fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(300)) },
            ) {
                SplashScreen(
                    onNavigateToMain = {
                        navController.navigate(CiroRoutes.MAIN) {
                            popUpTo(CiroRoutes.SPLASH) { inclusive = true }
                        }
                    },
                )
            }

            // ── Main (tabs) ──────────────────────────────────────
            composable(
                route = CiroRoutes.MAIN,
                enterTransition = { fadeIn(tween(300)) },
            ) {
                MainScreen(
                    viewModel = viewModel,
                    navController = navController,
                )
            }

            // ── Incident Detail ──────────────────────────────────
            composable(
                route = CiroRoutes.INCIDENT_DETAIL,
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        tween(300),
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        tween(300),
                    )
                },
            ) { backStackEntry ->
                val incidentId = backStackEntry.arguments?.getString("incidentId") ?: ""
                val allIncidents by viewModel.allIncidents.collectAsState()
                val allResources by viewModel.resources.collectAsState()
                val allTraces by viewModel.agentTraces.collectAsState()
                val allNotifications by viewModel.notifications.collectAsState()

                val incident = viewModel.findIncidentById(incidentId, allIncidents)
                val resources = viewModel.resourcesForIncident(incidentId, allResources)
                val traces = viewModel.tracesForIncident(incidentId, allTraces)
                val notifications = viewModel.notificationsForIncident(incidentId, allNotifications)

                IncidentDetailScreen(
                    incident = incident,
                    allocatedResources = resources,
                    relatedTraces = traces,
                    relatedNotifications = notifications,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // ── Crisis Alert Overlay (on top of everything) ──────────
        CrisisAlertOverlay(
            incident = crisisAlertIncident,
            visible = crisisAlertIncident != null,
            onDismiss = { viewModel.dismissCrisisAlert() },
            onViewDetails = { incidentId ->
                viewModel.dismissCrisisAlert()
                navController.navigate(CiroRoutes.incidentDetail(incidentId))
            },
        )
    }
}

// ── Main Screen (Bottom Nav Tabs) ────────────────────────────────────────────

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MainScreen(
    viewModel: DashboardViewModel,
    navController: NavHostController,
) {
    val navItems = listOf(
        BottomNavItem("Dashboard", Icons.Default.Dashboard),
        BottomNavItem("Map", Icons.Default.Map),
        BottomNavItem("Alerts", Icons.Default.Notifications),
        BottomNavItem("Analytics", Icons.Default.Analytics),
        BottomNavItem("AI Brain", Icons.Default.Psychology),
    )

    var selectedTab by remember { mutableIntStateOf(0) }

    // Collect all state flows
    val incidents by viewModel.incidents.collectAsState()
    val allIncidents by viewModel.allIncidents.collectAsState()
    val resources by viewModel.resources.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val agentTraces by viewModel.agentTraces.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val selectedFilter by viewModel.selectedStakeholderFilter.collectAsState()

    // Computed values
    val sevCounts = viewModel.severityCounts(incidents)
    val resSummary = viewModel.resourceSummary(resources)
    val avgLatency = viewModel.avgLatencyMs(metrics)
    val improvementLine = viewModel.improvementHeadline(metrics)

    Scaffold(
        containerColor = CiroColors.Surface,
        bottomBar = {
            NavigationBar(
                containerColor = CiroColors.SurfaceCard,
                contentColor = CiroColors.TextPrimary,
            ) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(item.icon, contentDescription = item.label)
                        },
                        label = {
                            Text(
                                text = item.label,
                                fontSize = 9.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CiroColors.AccentCyan,
                            selectedTextColor = CiroColors.AccentCyan,
                            unselectedIconColor = CiroColors.TextMuted,
                            unselectedTextColor = CiroColors.TextMuted,
                            indicatorColor = CiroColors.AccentCyan.copy(alpha = 0.1f),
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CiroColors.Surface),
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    incidents = incidents,
                    resources = resources,
                    metrics = metrics,
                    severityCounts = sevCounts,
                    resourceSummary = resSummary,
                    avgLatencyMs = avgLatency,
                    improvementHeadline = improvementLine,
                    falsePositiveCount = viewModel.falsePositiveCount(allIncidents),
                    onIncidentClick = { incidentId ->
                        navController.navigate(CiroRoutes.incidentDetail(incidentId))
                    },
                )

                1 -> CrisisMapScreen(
                    incidents = incidents,
                    resources = resources,
                    onIncidentClick = { incidentId ->
                        navController.navigate(CiroRoutes.incidentDetail(incidentId))
                    },
                )

                2 -> NotificationsScreen(
                    notifications = notifications,
                    selectedFilter = selectedFilter,
                    onFilterChange = { viewModel.setStakeholderFilter(it) },
                )

                3 -> AnalyticsScreen(
                    metrics = metrics,
                    stageBreakdown = viewModel.avgStageBreakdown(metrics),
                    accuracyRate = viewModel.accuracyRate(metrics),
                    speedComparison = viewModel.speedComparison(metrics),
                    avgLatencyMs = avgLatency,
                    falsePositiveCount = viewModel.falsePositiveCount(allIncidents),
                )

                4 -> AIBrainScreen(
                    traces = agentTraces,
                )
            }
        }
    }
}
