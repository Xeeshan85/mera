package com.ciro.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM push notification handler for CIRO.
 *
 * The Python backend (Agent 5) sends push notifications to these FCM topics:
 *   • "public_alerts"      — public crisis alerts
 *   • "emergency_services" — dispatch/operational alerts
 *   • "admin"              — system admin notifications
 *
 * Subscribe to topics in your Application.onCreate():
 *   FirebaseMessaging.getInstance().subscribeToTopic("public_alerts")
 *   FirebaseMessaging.getInstance().subscribeToTopic("admin")
 */
class CiroFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "CiroFCM"
        private const val CHANNEL_ALERTS = "ciro_alerts"
        private const val CHANNEL_SYSTEM = "ciro_system"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        // TODO: Send token to backend if per-device targeting is needed
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message from: ${message.from}")

        val data = message.data
        val incidentId = data["incident_id"] ?: ""
        val crisisType = data["crisis_type"] ?: ""
        val isRetraction = data["is_retraction"] == "true"

        // Use notification payload if present, otherwise build from data
        val title = message.notification?.title ?: buildTitle(crisisType, isRetraction)
        val body = message.notification?.body ?: data["body"] ?: "Tap for details"

        createNotificationChannels()

        val channelId = if (isRetraction) CHANNEL_SYSTEM else CHANNEL_ALERTS
        val notificationId = incidentId.hashCode()

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (isRetraction) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_HIGH
            )
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    private fun buildTitle(crisisType: String, isRetraction: Boolean): String {
        return if (isRetraction) {
            "✅ CANCELLED: ${crisisType.uppercase()} alert"
        } else {
            "⚠️ ${crisisType.uppercase()} ALERT"
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Crisis Alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Real-time crisis alerts from CIRO"
            }

            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "System Updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "System updates and retractions"
            }

            manager.createNotificationChannel(alertChannel)
            manager.createNotificationChannel(systemChannel)
        }
    }
}
