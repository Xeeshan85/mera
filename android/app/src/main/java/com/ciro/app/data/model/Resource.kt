package com.ciro.app.data.model

/**
 * Kotlin data class mirroring the Firestore `resources` collection schema.
 * TEAM CONTRACT — matches the Python Pydantic Resource model exactly.
 */
data class Resource(
    val resource_id: String = "",
    val type: String = "",
    val state: String = "AVAILABLE",
    val location: ResourceLocation = ResourceLocation(),
    val name: String = "",
    val assigned_incident_id: String? = null,
    val eta_minutes: Double? = null,
    val capacity: Int = 1,
    val unit_name: String = "",
    val contact: String = "",
    val last_updated: String = "",
) {
    /** Emoji icon for map markers based on resource type. */
    val typeIcon: String
        get() = when (type) {
            "ambulance" -> "🚑"
            "police_unit" -> "🚔"
            "rescue_team" -> "⛑️"
            "water_tanker" -> "🚒"
            "field_team" -> "👷"
            "shelter" -> "🏠"
            "generator" -> "⚡"
            "drone" -> "🛸"
            else -> "📍"
        }

    val isAvailable: Boolean get() = state == "AVAILABLE"
    val isDispatched: Boolean get() = state == "DISPATCHED"
    val isShadowCommitted: Boolean get() = state == "SHADOW_COMMITTED"
}

data class ResourceLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val name: String = "",
)
