package com.ciro.app.data.repository

import com.ciro.app.data.model.AgentTrace
import com.ciro.app.data.model.AuditLogEntry
import com.ciro.app.data.model.CiroNotification
import com.ciro.app.data.model.ConflictingHypothesis
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.IncidentLocation
import com.ciro.app.data.model.PipelineMetric
import com.ciro.app.data.model.Resource
import com.ciro.app.data.model.ResourceLocation
import com.ciro.app.data.model.SeverityForecast
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Single source of truth for all Firestore real-time data.
 *
 * Every public method returns a [Flow] backed by Firestore's `addSnapshotListener`,
 * so the UI gets live updates with zero polling. Uses [callbackFlow] to bridge the
 * callback-based Firestore API into Kotlin coroutines.
 *
 * Collections read:
 *   • incidents   — active crisis events
 *   • resources   — emergency units (ambulances, police, shelters, etc.)
 *   • notifications — stakeholder alerts
 *   • agent_traces — AI agent execution logs
 *   • metrics     — pipeline latency measurements
 */
class CiroRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // ── Incidents ────────────────────────────────────────────────────────────

    /**
     * Observe all non-terminal incidents ordered by severity DESC.
     * Emits a fresh list on every Firestore change.
     */
    fun observeActiveIncidents(): Flow<List<Incident>> = callbackFlow {
        val registration = db.collection("incidents")
            .whereNotIn("state", listOf("RESOLVED", "RETRACTED"))
            .orderBy("severity_level", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val incidents = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToIncident(it) }
                } ?: emptyList()
                trySend(incidents)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Observe ALL incidents (including RETRACTED / RESOLVED) for the full history view.
     */
    fun observeAllIncidents(): Flow<List<Incident>> = callbackFlow {
        val registration = db.collection("incidents")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val incidents = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToIncident(it) }
                } ?: emptyList()
                trySend(incidents)
            }
        awaitClose { registration.remove() }
    }

    // ── Resources ────────────────────────────────────────────────────────────

    /**
     * Observe all resources (any state) for map display and summary stats.
     */
    fun observeResources(): Flow<List<Resource>> = callbackFlow {
        val registration = db.collection("resources")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val resources = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToResource(it) }
                } ?: emptyList()
                trySend(resources)
            }
        awaitClose { registration.remove() }
    }

    // ── Notifications ────────────────────────────────────────────────────────

    /**
     * Observe notifications ordered by sent_at DESC. Limited to most recent 50.
     */
    fun observeNotifications(): Flow<List<CiroNotification>> = callbackFlow {
        val registration = db.collection("notifications")
            .orderBy("sent_at", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val notifications = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToNotification(it) }
                } ?: emptyList()
                trySend(notifications)
            }
        awaitClose { registration.remove() }
    }

    // ── Agent Traces ─────────────────────────────────────────────────────────

    /**
     * Observe agent execution traces ordered by timestamp DESC.
     * These feed the "AI Reasoning" terminal panel in the dashboard.
     */
    fun observeAgentTraces(): Flow<List<AgentTrace>> = callbackFlow {
        val registration = db.collection("agent_traces")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val traces = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToAgentTrace(it) }
                } ?: emptyList()
                trySend(traces)
            }
        awaitClose { registration.remove() }
    }

    // ── Pipeline Metrics ─────────────────────────────────────────────────────

    /**
     * Observe pipeline performance metrics for the analytics dashboard.
     */
    fun observeMetrics(): Flow<List<PipelineMetric>> = callbackFlow {
        val registration = db.collection("metrics")
            .orderBy("recorded_at", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val metrics = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToMetric(it) }
                } ?: emptyList()
                trySend(metrics)
            }
        awaitClose { registration.remove() }
    }

    // ── Firestore → Kotlin Mapping ───────────────────────────────────────────

    /**
     * Safely maps a raw Firestore document map to an [Incident].
     * Handles missing fields gracefully — every field has a default.
     */
    @Suppress("UNCHECKED_CAST")
    private fun mapToIncident(data: Map<String, Any>): Incident {
        val locationMap = data["location"] as? Map<String, Any> ?: emptyMap()
        val forecastMap = data["severity_forecast"] as? Map<String, Any>
        val conflictMap = data["conflicting_hypothesis"] as? Map<String, Any>
        val auditList = data["audit_log"] as? List<Map<String, Any>> ?: emptyList()

        return Incident(
            incident_id = data["incident_id"] as? String ?: "",
            state = data["state"] as? String ?: "MONITORING",
            crisis_type = data["crisis_type"] as? String ?: "",
            severity_level = (data["severity_level"] as? Number)?.toInt() ?: 1,
            confidence_score = (data["confidence_score"] as? Number)?.toDouble() ?: 0.0,
            conflicting_hypothesis = conflictMap?.let {
                ConflictingHypothesis(
                    type = it["type"] as? String ?: "",
                    confidence = (it["confidence"] as? Number)?.toDouble() ?: 0.0,
                    evidence_signal_ids = (it["evidence_signal_ids"] as? List<String>) ?: emptyList(),
                    evidence_summary = it["evidence_summary"] as? String ?: "",
                )
            },
            location = IncidentLocation(
                lat = (locationMap["lat"] as? Number)?.toDouble() ?: 0.0,
                lng = (locationMap["lng"] as? Number)?.toDouble() ?: 0.0,
                area_name = locationMap["area_name"] as? String ?: "",
                affected_radius_km = (locationMap["affected_radius_km"] as? Number)?.toDouble() ?: 1.0,
            ),
            affected_population_estimate = (data["affected_population_estimate"] as? Number)?.toInt() ?: 0,
            expected_duration_hours = (data["expected_duration_hours"] as? Number)?.toDouble() ?: 2.0,
            peak_impact_time = data["peak_impact_time"] as? String,
            spread_risk = data["spread_risk"] as? String ?: "low",
            severity_forecast = forecastMap?.let {
                SeverityForecast(
                    t_plus_1h = (it["t_plus_1h"] as? Number)?.toInt() ?: 1,
                    t_plus_2h = (it["t_plus_2h"] as? Number)?.toInt() ?: 1,
                    t_plus_6h = (it["t_plus_6h"] as? Number)?.toInt() ?: 1,
                    uncertainty_range = (it["uncertainty_range"] as? Number)?.toInt() ?: 1,
                )
            },
            resources_allocated = (data["resources_allocated"] as? List<String>) ?: emptyList(),
            stakeholder_notifications_sent = (data["stakeholder_notifications_sent"] as? List<String>) ?: emptyList(),
            signal_ids = (data["signal_ids"] as? List<String>) ?: emptyList(),
            response_actions = (data["response_actions"] as? List<Map<String, Any>>) ?: emptyList(),
            audit_log = auditList.map { entry ->
                AuditLogEntry(
                    timestamp = entry["timestamp"] as? String ?: "",
                    action = entry["action"] as? String ?: "",
                    from_state = entry["from_state"] as? String,
                    to_state = entry["to_state"] as? String,
                    reason = entry["reason"] as? String ?: "",
                    agent = entry["agent"] as? String ?: "",
                )
            },
            trade_off_narrative = data["trade_off_narrative"] as? String,
            created_at = data["created_at"] as? String ?: "",
            updated_at = data["updated_at"] as? String ?: "",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToResource(data: Map<String, Any>): Resource {
        val locationMap = data["location"] as? Map<String, Any> ?: emptyMap()
        return Resource(
            resource_id = data["resource_id"] as? String ?: "",
            type = data["type"] as? String ?: "",
            state = data["state"] as? String ?: "AVAILABLE",
            location = ResourceLocation(
                lat = (locationMap["lat"] as? Number)?.toDouble() ?: 0.0,
                lng = (locationMap["lng"] as? Number)?.toDouble() ?: 0.0,
                name = locationMap["name"] as? String ?: data["name"] as? String ?: "",
            ),
            name = data["name"] as? String ?: "",
            assigned_incident_id = data["assigned_incident_id"] as? String,
            eta_minutes = (data["eta_minutes"] as? Number)?.toDouble(),
            capacity = (data["capacity"] as? Number)?.toInt() ?: 1,
            unit_name = data["unit_name"] as? String ?: "",
            contact = data["contact"] as? String ?: "",
            last_updated = data["last_updated"] as? String ?: "",
        )
    }

    private fun mapToNotification(data: Map<String, Any>): CiroNotification {
        return CiroNotification(
            notification_id = data["notification_id"] as? String ?: "",
            incident_id = data["incident_id"] as? String ?: "",
            stakeholder_type = data["stakeholder_type"] as? String ?: "",
            channel = data["channel"] as? String ?: "dashboard",
            message_title = data["message_title"] as? String ?: "",
            message_body = data["message_body"] as? String ?: "",
            is_retraction = data["is_retraction"] as? Boolean ?: false,
            sent_at = data["sent_at"] as? String ?: "",
            delivery_status = data["delivery_status"] as? String ?: "sent",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToAgentTrace(data: Map<String, Any>): AgentTrace {
        return AgentTrace(
            trace_id = data["trace_id"] as? String ?: "",
            agent = data["agent"] as? String ?: "",
            pipeline_run_id = data["pipeline_run_id"] as? String,
            incident_id = data["incident_id"] as? String,
            timestamp = data["timestamp"] as? String ?: "",
            input_summary = data["input_summary"] as? String ?: "",
            tool_calls = (data["tool_calls"] as? List<Map<String, Any>>) ?: emptyList(),
            gemini_reasoning = data["gemini_reasoning"] as? String,
            decision = data["decision"] as? String ?: "",
            confidence_scores = (data["confidence_scores"] as? Map<String, Any>) ?: emptyMap(),
            state_transition = data["state_transition"] as? Map<String, Any>,
            trade_off_narrative = data["trade_off_narrative"] as? String,
            duration_ms = (data["duration_ms"] as? Number)?.toLong() ?: 0L,
            stages = (data["stages"] as? List<Map<String, Any>>) ?: emptyList(),
            confidence_at_detection = (data["confidence_at_detection"] as? Number)?.toDouble() ?: 0.0,
            routing_decision = data["routing_decision"] as? String,
            resource_trade_off_narrative = data["resource_trade_off_narrative"] as? String,
            actions_simulated = (data["actions_simulated"] as? List<Map<String, Any>>) ?: emptyList(),
            fallbacks_triggered = (data["fallbacks_triggered"] as? List<String>) ?: emptyList(),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToMetric(data: Map<String, Any>): PipelineMetric {
        return PipelineMetric(
            metric_id = data["metric_id"] as? String ?: "",
            incident_id = data["incident_id"] as? String ?: "",
            pipeline_run_id = data["pipeline_run_id"] as? String ?: "",
            signal_to_detection_ms = (data["signal_to_detection_ms"] as? Number)?.toLong() ?: 0L,
            detection_to_allocation_ms = (data["detection_to_allocation_ms"] as? Number)?.toLong() ?: 0L,
            allocation_to_notification_ms = (data["allocation_to_notification_ms"] as? Number)?.toLong() ?: 0L,
            total_end_to_end_ms = (data["total_end_to_end_ms"] as? Number)?.toLong() ?: 0L,
            agents_invoked = (data["agents_invoked"] as? List<String>) ?: emptyList(),
            api_calls_made = (data["api_calls_made"] as? Map<String, Any>) ?: emptyMap(),
            fallbacks_triggered = (data["fallbacks_triggered"] as? List<String>) ?: emptyList(),
            false_positive = data["false_positive"] as? Boolean ?: false,
            recorded_at = data["recorded_at"] as? String ?: "",
            manual_benchmark_ms = (data["manual_benchmark_ms"] as? Number)?.toLong() ?: 600_000L,
            improvement_ratio = (data["improvement_ratio"] as? Number)?.toDouble() ?: 0.0,
        )
    }
}
