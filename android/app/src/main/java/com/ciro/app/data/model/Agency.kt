package com.ciro.app.data.model

/**
 * Emergency agency with resources and response statistics.
 * Firestore collection: agencies
 */
data class Agency(
    val agency_id: String = "",
    val name: String = "",
    val full_name: String = "",
    val type: String = "",                  // "rescue" | "police" | "hospital" | "utility" | "disaster_management"
    val logo_emoji: String = "🏢",
    val jurisdiction: String = "",
    val contact: String = "",
    val description: String = "",
    val resources: List<AgencyResource> = emptyList(),
    val stats: AgencyStats = AgencyStats(),
)

data class AgencyResource(
    val resource_id: String = "",
    val type: String = "",
    val unit_name: String = "",
    val state: String = "AVAILABLE",        // "AVAILABLE" | "DISPATCHED" | "MAINTENANCE" | "OFF_DUTY"
    val capacity: Int = 1,
)

data class AgencyStats(
    val incidents_responded: Int = 0,
    val avg_response_time_min: Double = 0.0,
    val resources_deployed_total: Int = 0,
    val false_alarms_handled: Int = 0,
)
