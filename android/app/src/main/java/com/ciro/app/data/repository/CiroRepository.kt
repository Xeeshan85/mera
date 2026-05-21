package com.ciro.app.data.repository

import com.ciro.app.data.model.AgentTrace
import com.ciro.app.data.model.AuditLogEntry
import com.ciro.app.data.model.CiroNotification
import com.ciro.app.data.model.ConflictingHypothesis
import com.ciro.app.data.model.Incident
import com.ciro.app.data.model.IncidentLocation
import com.ciro.app.data.model.NewsItem
import com.ciro.app.data.model.PipelineMetric
import com.ciro.app.data.model.Resource
import com.ciro.app.data.model.ResourceLocation
import com.ciro.app.data.model.SeverityForecast
import com.ciro.app.data.model.Signal
import com.ciro.app.data.model.SignalLocation
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant

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
            // .whereNotIn("state", listOf("RESOLVED", "RETRACTED")) // Temporarily removed to prevent composite index crash on first run
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

    // ── Live Updates (Breaking Ticker) ──────────────────────────────────

    /**
     * Observe live updates for the breaking news ticker.
     * Ordered by timestamp DESC, limited to the 20 most recent.
     */
    fun observeLiveUpdates(): Flow<List<com.ciro.app.data.model.LiveUpdate>> = callbackFlow {
        val registration = db.collection("live_updates")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val updates = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToLiveUpdate(it) }
                } ?: emptyList()
                trySend(updates)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Observe backend-cached media headlines.
     * Backend /api/news writes news_cache/latest; installed APKs listen here
     * so fresh headlines propagate through Firestore instead of only HTTP.
     */
    @Suppress("UNCHECKED_CAST")
    fun observeNewsHeadlines(): Flow<List<NewsItem>> = callbackFlow {
        val registration = db.collection("news_cache")
            .document("latest")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val headlines = snapshot?.data?.let { data ->
                    val rawHeadlines = data["headlines"] as? List<Map<String, Any>> ?: emptyList()
                    rawHeadlines.mapNotNull { mapToNewsItem(it) }
                } ?: emptyList()
                trySend(headlines)
            }
        awaitClose { registration.remove() }
    }

    // ── Agencies ─────────────────────────────────────────────────────────

    /**
     * Observe all emergency agencies with their resources and stats.
     */
    fun observeAgencies(): Flow<List<com.ciro.app.data.model.Agency>> = callbackFlow {
        val registration = db.collection("agencies")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val agencies = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToAgency(it) }
                } ?: emptyList()
                trySend(agencies)
            }
        awaitClose { registration.remove() }
    }

    // ── Live Intelligence ────────────────────────────────────────────────

    /**
     * Observe the latest intelligence snapshot (single doc: "latest").
     */
    fun observeIntelligence(): Flow<com.ciro.app.data.model.IntelligenceSnapshot?> = callbackFlow {
        val registration = db.collection("live_intelligence")
            .document("latest")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val intel = snapshot?.data?.let { mapToIntelligence(it) }
                trySend(intel)
            }
        awaitClose { registration.remove() }
    }

    // ── Raw Signals (for client-side Intel computation) ──────────────────

    /**
     * Observe raw signals from the `signals` collection.
     * Ordered by created_at DESC, limited to 200 most recent.
     * The ViewModel uses these to compute intelligence metrics client-side.
     */
    fun observeSignals(): Flow<List<Signal>> = callbackFlow {
        val registration = db.collection("signals")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val signals = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToSignal(it) }
                } ?: emptyList()
                trySend(signals)
            }
        awaitClose { registration.remove() }
    }

    // ── Client-side Firestore publishing fallbacks ───────────────────────────

    /**
     * Publish media headlines into the same Firestore documents the APK observes.
     * This keeps Pulse/Intel live even when the backend proxy is unavailable.
     */
    suspend fun publishNewsHeadlines(headlines: List<NewsItem>) {
        if (headlines.isEmpty()) return

        val now = Instant.now().toString()
        val batch = db.batch()
        val headlineMaps = headlines.take(20).map { it.toFirestoreMap() }

        batch.set(
            db.collection("news_cache").document("latest"),
            mapOf(
                "headlines" to headlineMaps,
                "cached_at" to now,
                "count" to headlineMaps.size,
                "source" to "android_client",
            ),
        )

        headlines.take(12).forEach { item ->
            val updateId = item.news_id.ifBlank { stableId(item.url.ifBlank { item.title }) }
            batch.set(
                db.collection("live_updates").document(updateId),
                mapOf(
                    "update_id" to updateId,
                    "headline" to item.title,
                    "crisis_type" to "",
                    "severity_level" to urgencyToSeverity(item.urgency),
                    "incident_id" to null,
                    "source" to item.source.ifBlank { "Media" },
                    "location" to "Pakistan",
                    "timestamp" to item.published_at.ifBlank { now },
                    "is_breaking" to (item.urgency == "critical" || item.urgency == "warning"),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
        }

        batch.commit().await()
    }

    /**
     * Fallback scenario runner for the admin panel.
     * Writes demo incidents/traces/metrics straight to Firestore, so the APK
     * updates in real time without a working Cloud Run endpoint.
     */
    suspend fun publishDemoScenario(scenarioName: String): List<String> {
        val specs = when (scenarioName) {
            "multi_crisis" -> listOf(scenarioSpec("flood_g10"), scenarioSpec("heatwave_i8"))
            else -> listOf(scenarioSpec(scenarioName))
        }

        val now = Instant.now().toString()
        val batch = db.batch()
        val incidentIds = mutableListOf<String>()

        specs.forEach { spec ->
            val incidentId = "demo_${spec.id}_${System.currentTimeMillis()}"
            incidentIds.add(incidentId)
            val pipelineRunId = "run_$incidentId"
            val traceId = "trace_$incidentId"
            val metricId = "metric_$incidentId"
            val updateId = "update_$incidentId"

            batch.set(db.collection("incidents").document(incidentId), spec.incidentMap(incidentId, now))
            batch.set(db.collection("live_updates").document(updateId), spec.liveUpdateMap(updateId, incidentId, now))
            batch.set(db.collection("agent_traces").document(traceId), spec.traceMap(traceId, pipelineRunId, incidentId, now))
            batch.set(db.collection("metrics").document(metricId), spec.metricMap(metricId, pipelineRunId, incidentId, now))
        }

        batch.set(db.collection("live_intelligence").document("latest"), demoIntelligenceMap(now), com.google.firebase.firestore.SetOptions.merge())
        batch.commit().await()
        return incidentIds
    }

    private fun NewsItem.toFirestoreMap(): Map<String, Any> = mapOf(
        "news_id" to news_id.ifBlank { stableId(url.ifBlank { title }) },
        "title" to title,
        "description" to description,
        "source" to source.ifBlank { "Media" },
        "url" to url,
        "image_url" to image_url,
        "published_at" to published_at,
        "urgency" to urgency,
    )

    private fun stableId(value: String): String {
        return "client_${Integer.toHexString(value.hashCode())}"
    }

    private fun urgencyToSeverity(urgency: String): Int = when (urgency) {
        "critical" -> 4
        "warning" -> 2
        else -> 0
    }

    private data class ScenarioSpec(
        val id: String,
        val crisisType: String,
        val areaName: String,
        val lat: Double,
        val lng: Double,
        val state: String,
        val severity: Int,
        val confidence: Double,
        val population: Int,
        val spreadRisk: String,
        val durationHours: Double,
        val headline: String,
        val narrative: String,
    ) {
        fun incidentMap(incidentId: String, now: String): Map<String, Any?> = mapOf(
            "incident_id" to incidentId,
            "state" to state,
            "crisis_type" to crisisType,
            "severity_level" to severity,
            "confidence_score" to confidence,
            "location" to mapOf(
                "lat" to lat,
                "lng" to lng,
                "area_name" to areaName,
                "affected_radius_km" to if (severity >= 4) 3.2 else 1.4,
            ),
            "affected_population_estimate" to population,
            "expected_duration_hours" to durationHours,
            "peak_impact_time" to now,
            "spread_risk" to spreadRisk,
            "severity_forecast" to mapOf(
                "t_plus_1h" to severity,
                "t_plus_2h" to (severity + 1).coerceAtMost(5),
                "t_plus_6h" to (severity - 1).coerceAtLeast(1),
                "uncertainty_range" to 1,
            ),
            "resources_allocated" to if (state == "CONFIRMED") listOf("ambulance_01", "rescue_01", "police_01") else emptyList<String>(),
            "stakeholder_notifications_sent" to if (state == "CONFIRMED") listOf("public_alerts", "emergency_services") else emptyList<String>(),
            "signal_ids" to listOf("android_demo_${id}_weather", "android_demo_${id}_media", "android_demo_${id}_social"),
            "response_actions" to listOf(
                mapOf("action" to "Situation created from admin simulation", "status" to "completed", "timestamp" to now),
                mapOf("action" to "Live update published to Pulse", "status" to "completed", "timestamp" to now),
            ),
            "audit_log" to listOf(
                mapOf(
                    "timestamp" to now,
                    "action" to "Scenario triggered from APK admin panel",
                    "from_state" to null,
                    "to_state" to state,
                    "reason" to narrative,
                    "agent" to "android_fallback",
                ),
            ),
            "trade_off_narrative" to narrative,
            "created_at" to now,
            "updated_at" to now,
        )

        fun liveUpdateMap(updateId: String, incidentId: String, now: String): Map<String, Any?> = mapOf(
            "update_id" to updateId,
            "headline" to headline,
            "crisis_type" to crisisType,
            "severity_level" to severity,
            "incident_id" to incidentId,
            "source" to "admin_simulation",
            "location" to areaName,
            "timestamp" to now,
            "is_breaking" to (state == "CONFIRMED" && severity >= 3),
        )

        fun traceMap(traceId: String, pipelineRunId: String, incidentId: String, now: String): Map<String, Any?> = mapOf(
            "trace_id" to traceId,
            "agent" to "android_fallback_orchestrator",
            "pipeline_run_id" to pipelineRunId,
            "incident_id" to incidentId,
            "timestamp" to now,
            "input_summary" to headline,
            "tool_calls" to listOf(
                mapOf("tool" to "firestore_direct_write", "status" to "success", "duration_ms" to 180),
                mapOf("tool" to "live_update_publish", "status" to "success", "duration_ms" to 90),
            ),
            "gemini_reasoning" to narrative,
            "decision" to "Created demo incident because backend endpoint was unavailable",
            "confidence_scores" to mapOf(crisisType to confidence),
            "duration_ms" to 420,
            "triggered_by" to "admin_simulation",
            "total_duration_ms" to 420,
            "false_alarm_recovery" to (state == "RETRACTED"),
        )

        fun metricMap(metricId: String, pipelineRunId: String, incidentId: String, now: String): Map<String, Any?> = mapOf(
            "metric_id" to metricId,
            "incident_id" to incidentId,
            "pipeline_run_id" to pipelineRunId,
            "signal_to_detection_ms" to 180,
            "detection_to_allocation_ms" to 140,
            "allocation_to_notification_ms" to 100,
            "total_end_to_end_ms" to 420,
            "agents_invoked" to listOf("android_fallback_orchestrator"),
            "api_calls_made" to mapOf("firestore" to 4),
            "fallbacks_triggered" to listOf("backend_unreachable"),
            "false_positive" to (state == "RETRACTED"),
            "recorded_at" to now,
            "manual_benchmark_ms" to 600000,
            "improvement_ratio" to 1428.0,
        )
    }

    private fun scenarioSpec(name: String): ScenarioSpec = when (name) {
        "heatwave_i8" -> ScenarioSpec(
            id = "heatwave_i8",
            crisisType = "heatwave",
            areaName = "I-8 Islamabad",
            lat = 33.6938,
            lng = 73.0551,
            state = "CONFIRMED",
            severity = 3,
            confidence = 0.82,
            population = 9200,
            spreadRisk = "medium",
            durationHours = 8.0,
            headline = "Heatwave stress reported in I-8 Islamabad; medical teams on alert",
            narrative = "High temperature reports, health complaints, and localized demand pressure indicate a confirmed heatwave response scenario.",
        )
        "false_alarm" -> ScenarioSpec(
            id = "false_alarm",
            crisisType = "flood",
            areaName = "F-6 Islamabad",
            lat = 33.7294,
            lng = 73.0931,
            state = "RETRACTED",
            severity = 1,
            confidence = 0.34,
            population = 400,
            spreadRisk = "low",
            durationHours = 1.0,
            headline = "F-6 flooding report retracted after low-confidence verification",
            narrative = "Sparse reports and weak weather confirmation indicate a false alarm; resources remain available.",
        )
        else -> ScenarioSpec(
            id = "flood_g10",
            crisisType = "flood",
            areaName = "G-10 Islamabad",
            lat = 33.6844,
            lng = 73.0479,
            state = "CONFIRMED",
            severity = 4,
            confidence = 0.88,
            population = 15400,
            spreadRisk = "high",
            durationHours = 5.0,
            headline = "Flash flooding in G-10 Islamabad; roads blocked and rescue units dispatched",
            narrative = "Heavy rain, traffic disruption, and public reports indicate a confirmed flood response requiring rescue, police, and medical coordination.",
        )
    }

    private fun demoIntelligenceMap(now: String): Map<String, Any> = mapOf(
        "snapshot_id" to "android_demo_latest",
        "timestamp" to now,
        "total_signals" to 128,
        "mention_velocity" to mapOf(
            "buckets" to listOf(
                mapOf("bucket" to "T-120m", "count" to 8),
                mapOf("bucket" to "T-105m", "count" to 11),
                mapOf("bucket" to "T-90m", "count" to 15),
                mapOf("bucket" to "T-75m", "count" to 18),
                mapOf("bucket" to "T-60m", "count" to 22),
                mapOf("bucket" to "T-45m", "count" to 28),
                mapOf("bucket" to "T-30m", "count" to 35),
                mapOf("bucket" to "T-15m", "count" to 44),
            ),
            "current_rate" to 44,
            "average_rate" to 22.6,
            "is_spike" to true,
            "trend" to "rising",
        ),
        "sentiment" to mapOf("score" to 0.73, "label" to "critical", "negative_pct" to 68),
        "credibility" to mapOf("score" to 0.81, "stars" to 4, "verified_count" to 19, "total_sources" to 36),
        "trending_keywords" to listOf(
            mapOf("keyword" to "flood", "count" to 42, "is_crisis" to true),
            mapOf("keyword" to "rescue", "count" to 31, "is_crisis" to true),
            mapOf("keyword" to "road", "count" to 26, "is_crisis" to true),
            mapOf("keyword" to "traffic", "count" to 21, "is_crisis" to false),
            mapOf("keyword" to "emergency", "count" to 18, "is_crisis" to true),
        ),
        "source_health" to listOf(
            mapOf("source" to "media", "status" to "active", "signal_count" to 20, "last_signal" to now),
            mapOf("source" to "weather", "status" to "active", "signal_count" to 16, "last_signal" to now),
            mapOf("source" to "traffic", "status" to "active", "signal_count" to 13, "last_signal" to now),
            mapOf("source" to "field_report", "status" to "active", "signal_count" to 8, "last_signal" to now),
        ),
    )

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
            triggered_by = data["triggered_by"] as? String ?: "",
            total_duration_ms = (data["total_duration_ms"] as? Number)?.toLong() ?: 0L,
            false_alarm_recovery = data["false_alarm_recovery"] as? Boolean ?: false,
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

    // ── New v2 mappers ──────────────────────────────────────────────────

    private fun mapToLiveUpdate(data: Map<String, Any>): com.ciro.app.data.model.LiveUpdate {
        return com.ciro.app.data.model.LiveUpdate(
            update_id = data["update_id"] as? String ?: "",
            headline = data["headline"] as? String ?: "",
            crisis_type = data["crisis_type"] as? String ?: "",
            severity_level = (data["severity_level"] as? Number)?.toInt() ?: 0,
            incident_id = data["incident_id"] as? String,
            source = data["source"] as? String ?: "",
            location = data["location"] as? String ?: "",
            timestamp = data["timestamp"] as? String ?: "",
            is_breaking = data["is_breaking"] as? Boolean ?: false,
        )
    }

    private fun mapToNewsItem(data: Map<String, Any>): NewsItem? {
        val title = data["title"] as? String ?: ""
        if (title.isBlank()) return null

        return NewsItem(
            news_id = data["news_id"] as? String
                ?: data["url"] as? String
                ?: title.hashCode().toString(),
            title = title,
            description = data["description"] as? String ?: "",
            source = data["source"] as? String ?: "News",
            url = data["url"] as? String ?: "",
            image_url = data["image_url"] as? String ?: data["image"] as? String ?: "",
            published_at = data["published_at"] as? String ?: data["publishedAt"] as? String ?: "",
            urgency = data["urgency"] as? String ?: "info",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToAgency(data: Map<String, Any>): com.ciro.app.data.model.Agency {
        val resourcesList = (data["resources"] as? List<Map<String, Any>>) ?: emptyList()
        val statsMap = (data["stats"] as? Map<String, Any>) ?: emptyMap()
        return com.ciro.app.data.model.Agency(
            agency_id = data["agency_id"] as? String ?: "",
            name = data["name"] as? String ?: "",
            full_name = data["full_name"] as? String ?: "",
            type = data["type"] as? String ?: "",
            logo_emoji = data["logo_emoji"] as? String ?: "🏢",
            jurisdiction = data["jurisdiction"] as? String ?: "",
            contact = data["contact"] as? String ?: "",
            description = data["description"] as? String ?: "",
            resources = resourcesList.map { r ->
                com.ciro.app.data.model.AgencyResource(
                    resource_id = r["resource_id"] as? String ?: "",
                    type = r["type"] as? String ?: "",
                    unit_name = r["unit_name"] as? String ?: "",
                    state = r["state"] as? String ?: "AVAILABLE",
                    capacity = (r["capacity"] as? Number)?.toInt() ?: 1,
                )
            },
            stats = com.ciro.app.data.model.AgencyStats(
                incidents_responded = (statsMap["incidents_responded"] as? Number)?.toInt() ?: 0,
                avg_response_time_min = (statsMap["avg_response_time_min"] as? Number)?.toDouble() ?: 0.0,
                resources_deployed_total = (statsMap["resources_deployed_total"] as? Number)?.toInt() ?: 0,
                false_alarms_handled = (statsMap["false_alarms_handled"] as? Number)?.toInt() ?: 0,
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToIntelligence(data: Map<String, Any>): com.ciro.app.data.model.IntelligenceSnapshot {
        val velocityMap = (data["mention_velocity"] as? Map<String, Any>) ?: emptyMap()
        val sentimentMap = (data["sentiment"] as? Map<String, Any>) ?: emptyMap()
        val credibilityMap = (data["credibility"] as? Map<String, Any>) ?: emptyMap()
        val keywordsList = (data["trending_keywords"] as? List<Map<String, Any>>) ?: emptyList()
        val sourceHealthList = (data["source_health"] as? List<Map<String, Any>>) ?: emptyList()
        val timelineList = (data["signal_timeline"] as? List<Map<String, Any>>) ?: emptyList()

        val bucketsList = (velocityMap["buckets"] as? List<Map<String, Any>>) ?: emptyList()

        return com.ciro.app.data.model.IntelligenceSnapshot(
            snapshot_id = data["snapshot_id"] as? String ?: "",
            timestamp = data["timestamp"] as? String ?: "",
            total_signals = (data["total_signals"] as? Number)?.toInt() ?: 0,
            mention_velocity = com.ciro.app.data.model.MentionVelocity(
                buckets = bucketsList.map { b ->
                    com.ciro.app.data.model.VelocityBucket(
                        bucket = b["bucket"] as? String ?: "",
                        count = (b["count"] as? Number)?.toInt() ?: 0,
                    )
                },
                current_rate = (velocityMap["current_rate"] as? Number)?.toInt() ?: 0,
                average_rate = (velocityMap["average_rate"] as? Number)?.toDouble() ?: 0.0,
                is_spike = velocityMap["is_spike"] as? Boolean ?: false,
                trend = velocityMap["trend"] as? String ?: "stable",
            ),
            sentiment = com.ciro.app.data.model.Sentiment(
                score = (sentimentMap["score"] as? Number)?.toDouble() ?: 0.5,
                label = sentimentMap["label"] as? String ?: "neutral",
                negative_pct = (sentimentMap["negative_pct"] as? Number)?.toInt() ?: 0,
            ),
            credibility = com.ciro.app.data.model.Credibility(
                score = (credibilityMap["score"] as? Number)?.toDouble() ?: 0.0,
                stars = (credibilityMap["stars"] as? Number)?.toInt() ?: 0,
                verified_count = (credibilityMap["verified_count"] as? Number)?.toInt() ?: 0,
                total_sources = (credibilityMap["total_sources"] as? Number)?.toInt() ?: 0,
            ),
            trending_keywords = keywordsList.map { kw ->
                com.ciro.app.data.model.TrendingKeyword(
                    keyword = kw["keyword"] as? String ?: "",
                    count = (kw["count"] as? Number)?.toInt() ?: 0,
                    is_crisis = kw["is_crisis"] as? Boolean ?: false,
                )
            },
            source_health = sourceHealthList.map { sh ->
                com.ciro.app.data.model.SourceHealth(
                    source = sh["source"] as? String ?: "",
                    status = sh["status"] as? String ?: "inactive",
                    signal_count = (sh["signal_count"] as? Number)?.toInt() ?: 0,
                    last_signal = sh["last_signal"] as? String ?: "",
                )
            },
            signal_timeline = timelineList.map { st ->
                com.ciro.app.data.model.SignalTimelineEntry(
                    time = st["time"] as? String ?: "",
                    source = st["source"] as? String ?: "",
                    area = st["area"] as? String ?: "",
                    credibility = (st["credibility"] as? Number)?.toDouble() ?: 0.0,
                    urgency = (st["urgency"] as? Number)?.toDouble() ?: 0.0,
                )
            },
        )
    }

    // ── Signal Mapper ────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun mapToSignal(data: Map<String, Any>): Signal {
        val locationMap = data["location"] as? Map<String, Any> ?: emptyMap()
        return Signal(
            signal_id = data["signal_id"] as? String ?: "",
            source_type = data["source_type"] as? String ?: "",
            source_name = data["source_name"] as? String ?: "",
            created_at = data["created_at"] as? String ?: "",
            timestamp = data["timestamp"] as? String ?: "",
            credibility_score = (data["credibility_score"] as? Number)?.toDouble() ?: 0.5,
            urgency_language_score = (data["urgency_language_score"] as? Number)?.toDouble() ?: 0.5,
            mention_velocity = (data["mention_velocity"] as? Number)?.toInt() ?: 0,
            contradiction_flag = data["contradiction_flag"] as? Boolean ?: false,
            degraded_mode = data["degraded_mode"] as? Boolean ?: false,
            processed = data["processed"] as? Boolean ?: false,
            related_incident_id = data["related_incident_id"] as? String,
            location = SignalLocation(
                area_name = locationMap["area_name"] as? String ?: "",
                lat = (locationMap["lat"] as? Number)?.toDouble() ?: 0.0,
                lng = (locationMap["lng"] as? Number)?.toDouble() ?: 0.0,
                geolocation_confidence = (locationMap["geolocation_confidence"] as? Number)?.toDouble() ?: 0.5,
                radius_m = (locationMap["radius_m"] as? Number)?.toDouble(),
            ),
            raw_payload = (data["raw_payload"] as? Map<String, Any>) ?: emptyMap(),
        )
    }
}
