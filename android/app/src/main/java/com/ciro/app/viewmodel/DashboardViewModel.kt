package com.ciro.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ciro.app.data.model.Agency
import com.ciro.app.data.model.AgentTrace
import com.ciro.app.data.model.CiroNotification
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.IntelligenceSnapshot
import com.ciro.app.data.model.LiveUpdate
import com.ciro.app.data.model.NewsItem
import com.ciro.app.data.model.PipelineMetric
import com.ciro.app.data.model.Resource
import com.ciro.app.data.repository.CiroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Central ViewModel for the barwaqt command-center.
 *
 * Exposes live [StateFlow]s that the Compose UI collects. Each flow is backed
 * by a Firestore snapshot listener via [CiroRepository], so every Firestore
 * write from the Python agents instantly propagates to the Android UI.
 */
class DashboardViewModel(
    private val repository: CiroRepository = CiroRepository()
) : ViewModel() {

    // ── Live data streams ────────────────────────────────────────────────

    /** Active (non-terminal) incidents ordered by severity DESC. */
    val incidents: StateFlow<List<Incident>> = repository.observeActiveIncidents()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All incidents including resolved/retracted (for history). */
    val allIncidents: StateFlow<List<Incident>> = repository.observeAllIncidents()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All resources (ambulances, police, shelters, etc.). */
    val resources: StateFlow<List<Resource>> = repository.observeResources()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Stakeholder notifications. */
    val notifications: StateFlow<List<CiroNotification>> = repository.observeNotifications()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Agent execution traces — feeds the AI reasoning terminal. */
    val agentTraces: StateFlow<List<AgentTrace>> = repository.observeAgentTraces()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pipeline latency metrics. */
    val metrics: StateFlow<List<PipelineMetric>> = repository.observeMetrics()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── NEW: v2 live data streams ────────────────────────────────────────

    /** Breaking news ticker entries from live_updates collection. */
    val liveUpdates: StateFlow<List<LiveUpdate>> = repository.observeLiveUpdates()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Emergency agencies with resources and stats. */
    val agencies: StateFlow<List<Agency>> = repository.observeAgencies()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Signal intelligence snapshot (velocity, sentiment, keywords). */
    val intelligence: StateFlow<IntelligenceSnapshot> = repository.observeIntelligence()
        .catch { emit(null) }
        .map { it ?: IntelligenceSnapshot() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IntelligenceSnapshot())

    /** News headlines (loaded via REST, not Firestore listener). */
    private val _news = MutableStateFlow<List<NewsItem>>(emptyList())
    val news: StateFlow<List<NewsItem>> = _news.asStateFlow()

    /** Admin panel visibility. */
    private val _showAdminPanel = MutableStateFlow(false)
    val showAdminPanel: StateFlow<Boolean> = _showAdminPanel.asStateFlow()

    fun toggleAdminPanel() { _showAdminPanel.value = !_showAdminPanel.value }
    fun openAdminPanel() { _showAdminPanel.value = true }
    fun closeAdminPanel() { _showAdminPanel.value = false }

    // ── Selected incident for detail view ────────────────────────────────

    private val _selectedIncidentId = MutableStateFlow<String?>(null)
    val selectedIncidentId: StateFlow<String?> = _selectedIncidentId.asStateFlow()

    fun selectIncident(incidentId: String?) {
        _selectedIncidentId.value = incidentId
    }

    /** Find an incident by its ID across all incidents. */
    fun findIncidentById(id: String, allIncidents: List<Incident>): Incident? {
        return allIncidents.firstOrNull { it.incident_id == id }
    }

    /** Get resources allocated to a specific incident. */
    fun resourcesForIncident(incidentId: String, allResources: List<Resource>): List<Resource> {
        return allResources.filter { it.assigned_incident_id == incidentId }
    }

    /** Get agent traces related to a specific incident. */
    fun tracesForIncident(incidentId: String, allTraces: List<AgentTrace>): List<AgentTrace> {
        return allTraces.filter { it.incident_id == incidentId }
    }

    /** Get notifications related to a specific incident. */
    fun notificationsForIncident(
        incidentId: String,
        allNotifications: List<CiroNotification>
    ): List<CiroNotification> {
        return allNotifications.filter { it.incident_id == incidentId }
    }

    // ── Crisis alert overlay ─────────────────────────────────────────────

    private val _crisisAlertIncident = MutableStateFlow<Incident?>(null)
    val crisisAlertIncident: StateFlow<Incident?> = _crisisAlertIncident.asStateFlow()

    private val _acknowledgedIncidentIds = mutableSetOf<String>()

    /** Check if there's a new CONFIRMED incident to alert on. */
    fun checkForCrisisAlert(incidents: List<Incident>) {
        val confirmedIncidents = incidents.filter {
            it.state == "CONFIRMED" && it.incident_id !in _acknowledgedIncidentIds
        }
        if (confirmedIncidents.isNotEmpty() && _crisisAlertIncident.value == null) {
            _crisisAlertIncident.value = confirmedIncidents.first()
        }
    }

    /** Dismiss the crisis alert overlay. */
    fun dismissCrisisAlert() {
        _crisisAlertIncident.value?.let { inc ->
            _acknowledgedIncidentIds.add(inc.incident_id)
        }
        _crisisAlertIncident.value = null
    }

    // ── Notification filtering ───────────────────────────────────────────

    private val _selectedStakeholderFilter = MutableStateFlow<String?>(null)
    val selectedStakeholderFilter: StateFlow<String?> = _selectedStakeholderFilter.asStateFlow()

    fun setStakeholderFilter(type: String?) {
        _selectedStakeholderFilter.value = type
    }

    fun filteredNotifications(
        notifications: List<CiroNotification>,
        filter: String?
    ): List<CiroNotification> {
        if (filter == null) return notifications
        return notifications.filter { it.stakeholder_type == filter }
    }

    // ── Computed summary stats for the dashboard header ──────────────────

    /** Counts of active incidents grouped by severity level (1–5). */
    fun severityCounts(incidents: List<Incident>): Map<Int, Int> {
        return incidents
            .filter { !it.isTerminal }
            .groupBy { it.severity_level }
            .mapValues { it.value.size }
    }

    /** Resource availability summary. */
    fun resourceSummary(resources: List<Resource>): ResourceSummary {
        val available = resources.count { it.isAvailable }
        val dispatched = resources.count { it.isDispatched }
        val shadow = resources.count { it.isShadowCommitted }
        return ResourceSummary(
            total = resources.size,
            available = available,
            dispatched = dispatched,
            shadowCommitted = shadow,
        )
    }

    /** Resource breakdown by type. */
    fun resourceTypeBreakdown(resources: List<Resource>): Map<String, ResourceTypeStat> {
        return resources.groupBy { it.type }.mapValues { (_, res) ->
            ResourceTypeStat(
                total = res.size,
                available = res.count { it.isAvailable },
                dispatched = res.count { it.isDispatched },
            )
        }
    }

    /** Average pipeline latency from metrics collection. */
    fun avgLatencyMs(metrics: List<PipelineMetric>): Long {
        if (metrics.isEmpty()) return 0L
        return metrics.map { it.total_end_to_end_ms }.average().toLong()
    }

    /** Improvement ratio headline (e.g. "38x faster"). */
    fun improvementHeadline(metrics: List<PipelineMetric>): String {
        val avg = avgLatencyMs(metrics)
        if (avg <= 0) return "—"
        val ratio = 600_000.0 / avg // 10 min manual baseline
        return "${String.format("%.0f", ratio)}× faster than manual"
    }

    /** Average latency formatted as human-readable string. */
    fun formattedLatency(metrics: List<PipelineMetric>): String {
        val avg = avgLatencyMs(metrics)
        return when {
            avg <= 0 -> "—"
            avg < 1000 -> "${avg}ms"
            else -> "${String.format("%.1f", avg / 1000.0)}s"
        }
    }

    /** Count of false positives detected. */
    fun falsePositiveCount(allIncidents: List<Incident>): Int {
        return allIncidents.count { it.state == "RETRACTED" }
    }

    // ── Analytics helpers ────────────────────────────────────────────────

    /** Average time per pipeline stage. */
    fun avgStageBreakdown(metrics: List<PipelineMetric>): PipelineStageBreakdown {
        if (metrics.isEmpty()) return PipelineStageBreakdown()
        return PipelineStageBreakdown(
            avgDetectionMs = metrics.map { it.signal_to_detection_ms }.average().toLong(),
            avgAllocationMs = metrics.map { it.detection_to_allocation_ms }.average().toLong(),
            avgNotificationMs = metrics.map { it.allocation_to_notification_ms }.average().toLong(),
            avgTotalMs = metrics.map { it.total_end_to_end_ms }.average().toLong(),
        )
    }

    /** Accuracy rate (non-false-positive ratio). */
    fun accuracyRate(metrics: List<PipelineMetric>): Float {
        if (metrics.isEmpty()) return 1f
        val fp = metrics.count { it.false_positive }
        return 1f - (fp.toFloat() / metrics.size)
    }

    /** Per-run speed comparison: CIRO time vs manual baseline. */
    fun speedComparison(metrics: List<PipelineMetric>): List<SpeedComparisonItem> {
        return metrics.take(10).mapIndexed { index, m ->
            SpeedComparisonItem(
                label = "Run ${metrics.size - index}",
                ciroMs = m.total_end_to_end_ms,
                manualMs = m.manual_benchmark_ms,
                ratio = if (m.total_end_to_end_ms > 0) m.manual_benchmark_ms.toFloat() / m.total_end_to_end_ms else 0f,
            )
        }
    }

    /** Resources grouped by type for the Resource Hub. */
    fun resourcesByType(resources: List<Resource>): Map<String, List<Resource>> {
        return resources.groupBy { it.type }
    }
}

data class ResourceSummary(
    val total: Int,
    val available: Int,
    val dispatched: Int,
    val shadowCommitted: Int,
)

data class ResourceTypeStat(
    val total: Int,
    val available: Int,
    val dispatched: Int,
)

data class PipelineStageBreakdown(
    val avgDetectionMs: Long = 0,
    val avgAllocationMs: Long = 0,
    val avgNotificationMs: Long = 0,
    val avgTotalMs: Long = 0,
)

data class SpeedComparisonItem(
    val label: String,
    val ciroMs: Long,
    val manualMs: Long,
    val ratio: Float,
)
