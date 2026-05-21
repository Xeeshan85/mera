package com.ciro.app.data.model

/**
 * Raw signal document from Firestore `signals` collection.
 * Matches the exact Firestore schema written by the Python ingestion layer.
 */
data class Signal(
    val signal_id: String = "",
    val source_type: String = "",        // "social", "weather", "traffic", "sensor", "field_report"
    val source_name: String = "",        // "apify_twitter", "openweather", etc.
    val created_at: String = "",
    val timestamp: String = "",
    val credibility_score: Double = 0.5,
    val urgency_language_score: Double = 0.5,
    val mention_velocity: Int = 0,
    val contradiction_flag: Boolean = false,
    val degraded_mode: Boolean = false,
    val processed: Boolean = false,
    val related_incident_id: String? = null,
    val location: SignalLocation = SignalLocation(),
    val raw_payload: Map<String, Any> = emptyMap(),
)

data class SignalLocation(
    val area_name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val geolocation_confidence: Double = 0.5,
    val radius_m: Double? = null,
)
