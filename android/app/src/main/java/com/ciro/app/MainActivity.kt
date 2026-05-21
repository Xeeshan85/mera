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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sensors
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
import com.ciro.app.ui.admin.AdminPanel
import com.ciro.app.ui.agency.AgencyProfileScreen
import com.ciro.app.ui.components.CrisisAlertOverlay
import com.ciro.app.ui.incidents.IncidentDetailScreen
import com.ciro.app.ui.intel.IntelScreen
import com.ciro.app.ui.map.CrisisMapScreen
import com.ciro.app.ui.pulse.PulseScreen
import com.ciro.app.ui.response.ResponseScreen
import com.ciro.app.ui.splash.SplashScreen
import com.ciro.app.ui.theme.CiroColors
import com.ciro.app.ui.theme.CiroTheme
import com.ciro.app.viewmodel.DashboardViewModel
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Entry point for the barwaqt Android application.
 *
 * Navigation: Splash → Main (4 tabs: Pulse / Intel / Map / Response) → Incident Detail
 * Hidden admin panel accessible via long-press on barwaqt logo in Pulse tab.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Subscribe to FCM topics for push notifications
        FirebaseMessaging.getInstance().subscribeToTopic("public_alerts")
        FirebaseMessaging.getInstance().subscribeToTopic("admin")
        FirebaseMessaging.getInstance().subscribeToTopic("emergency_services")

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
    const val AGENCY_DETAIL = "agency/{agencyId}"
    const val ADMIN = "admin"

    fun incidentDetail(id: String) = "incident/$id"
    fun agencyDetail(id: String) = "agency/$id"
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

            // ── Main (4 tabs) ────────────────────────────────────
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

            // ── Agency Detail ────────────────────────────────────
            composable(
                route = CiroRoutes.AGENCY_DETAIL,
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
                val agencyId = backStackEntry.arguments?.getString("agencyId") ?: ""
                val allAgencies by viewModel.agencies.collectAsState()
                val agency = allAgencies.firstOrNull { it.agency_id == agencyId }

                AgencyProfileScreen(
                    agency = agency,
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Admin Panel ──────────────────────────────────────
            composable(
                route = CiroRoutes.ADMIN,
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Up,
                        tween(300),
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Down,
                        tween(300),
                    )
                },
            ) {
                val allIncidents by viewModel.allIncidents.collectAsState()
                val metrics by viewModel.metrics.collectAsState()
                val traces by viewModel.agentTraces.collectAsState()
                val scenarioStatus by viewModel.scenarioStatus.collectAsState()

                AdminPanel(
                    incidents = allIncidents,
                    metrics = metrics,
                    traces = traces,
                    scenarioStatus = scenarioStatus,
                    onTriggerScenario = { scenarioName ->
                        viewModel.triggerScenario(scenarioName)
                    },
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

// ── Main Screen (Bottom Nav — 4 Tabs) ────────────────────────────────────────

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
        BottomNavItem("Pulse", Icons.Default.Radar),
        BottomNavItem("Intel", Icons.Default.Sensors),
        BottomNavItem("Map", Icons.Default.Map),
        BottomNavItem("Response", Icons.Default.Shield),
    )

    var selectedTab by remember { mutableIntStateOf(0) }

    // Collect all state flows
    val incidents by viewModel.incidents.collectAsState()
    val allIncidents by viewModel.allIncidents.collectAsState()
    val resources by viewModel.resources.collectAsState()
    val liveUpdates by viewModel.mergedLiveUpdates.collectAsState()
    val agencies by viewModel.agencies.collectAsState()
    val intelligence by viewModel.intelligence.collectAsState()
    val news by viewModel.news.collectAsState()

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
                0 -> PulseScreen(
                    incidents = allIncidents,
                    liveUpdates = liveUpdates,
                    resources = resources,
                    onIncidentClick = { incidentId ->
                        navController.navigate(CiroRoutes.incidentDetail(incidentId))
                    },
                    onAdminLongPress = {
                        viewModel.openAdminPanel()
                        navController.navigate(CiroRoutes.ADMIN)
                    },
                )

                1 -> IntelScreen(
                    intelligence = intelligence,
                    news = news,
                )

                2 -> CrisisMapScreen(
                    incidents = incidents,
                    resources = resources,
                    onIncidentClick = { incidentId ->
                        navController.navigate(CiroRoutes.incidentDetail(incidentId))
                    },
                )

                3 -> ResponseScreen(
                    incidents = allIncidents,
                    agencies = agencies,
                    resources = resources,
                    onAgencyClick = { agencyId ->
                        navController.navigate(CiroRoutes.agencyDetail(agencyId))
                    },
                    onIncidentClick = { incidentId ->
                        navController.navigate(CiroRoutes.incidentDetail(incidentId))
                    },
                )
            }
        }
    }
}
