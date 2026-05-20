package com.ciro.app.data.model

/**
 * Breaking news / live update entry from the pipeline.
 * Firestore collection: live_updates
 */
data class LiveUpdate(
    val update_id: String = "",
    val headline: String = "",
    val crisis_type: String = "",
    val severity_level: Int = 0,
    val incident_id: String? = null,
    val source: String = "",          // "signal_fusion" | "agent" | "field_report" | "admin"
    val location: String = "",
    val timestamp: String = "",
    val is_breaking: Boolean = false,
)
