package com.ciro.app.data.model

/**
 * Kotlin data class mirroring the Firestore `notifications` collection schema.
 */
data class CiroNotification(
    val notification_id: String = "",
    val incident_id: String = "",
    val stakeholder_type: String = "",
    val channel: String = "dashboard",
    val message_title: String = "",
    val message_body: String = "",
    val is_retraction: Boolean = false,
    val sent_at: String = "",
    val delivery_status: String = "sent",
)
