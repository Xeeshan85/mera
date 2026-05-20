package com.ciro.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ciro.app.data.model.AgentTrace
import com.ciro.app.data.model.CiroNotification
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.PipelineMetric
import com.ciro.app.data.model.Resource
import com.ciro.app.data.repository.CiroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * Central ViewModel for the CIRO command-center dashboard.
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

    // ── Selected incident for detail view ────────────────────────────────

    private val _selectedIncidentId = MutableStateFlow<String?>(null)
    val selectedIncidentId: StateFlow<String?> = _selectedIncidentId.asStateFlow()

    fun selectIncident(incidentId: String?) {
        _selectedIncidentId.value = incidentId
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
}

data class ResourceSummary(
    val total: Int,
    val available: Int,
    val dispatched: Int,
    val shadowCommitted: Int,
)
