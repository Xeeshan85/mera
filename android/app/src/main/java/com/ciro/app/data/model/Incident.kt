package com.ciro.app.data.model

/**
 * Kotlin data classes mirroring the Firestore `incidents` collection schema.
 * TEAM CONTRACT — matches the Python Pydantic models exactly.
 */
data class Incident(
    val incident_id: String = "",
    val state: String = "MONITORING",
    val crisis_type: String = "",
    val severity_level: Int = 1,
    val confidence_score: Double = 0.0,
    val conflicting_hypothesis: ConflictingHypothesis? = null,
    val location: IncidentLocation = IncidentLocation(),
    val affected_population_estimate: Int = 0,
    val expected_duration_hours: Double = 2.0,
    val peak_impact_time: String? = null,
    val spread_risk: String = "low",
    val severity_forecast: SeverityForecast? = null,
    val resources_allocated: List<String> = emptyList(),
    val stakeholder_notifications_sent: List<String> = emptyList(),
    val signal_ids: List<String> = emptyList(),
    val response_actions: List<Map<String, Any>> = emptyList(),
    val audit_log: List<AuditLogEntry> = emptyList(),
    val trade_off_narrative: String? = null,
    val created_at: String = "",
    val updated_at: String = "",
) {
    /** Severity mapped to a human-readable label. */
    val severityLabel: String
        get() = when (severity_level) {
            1 -> "Minor"
            2 -> "Moderate"
            3 -> "Significant"
            4 -> "Severe"
            5 -> "Catastrophic"
            else -> "Unknown"
        }

    /** True for terminal states that no longer need active monitoring. */
    val isTerminal: Boolean
        get() = state == "RESOLVED" || state == "RETRACTED"
}

data class IncidentLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val area_name: String = "",
    val affected_radius_km: Double = 1.0,
)

data class ConflictingHypothesis(
    val type: String = "",
    val confidence: Double = 0.0,
    val evidence_signal_ids: List<String> = emptyList(),
    val evidence_summary: String = "",
)

data class SeverityForecast(
    val t_plus_1h: Int = 1,
    val t_plus_2h: Int = 1,
    val t_plus_6h: Int = 1,
    val uncertainty_range: Int = 1,
)

data class AuditLogEntry(
    val timestamp: String = "",
    val action: String = "",
    val from_state: String? = null,
    val to_state: String? = null,
    val reason: String = "",
    val agent: String = "",
)
