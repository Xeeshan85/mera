package com.ciro.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Application class for CIRO.
 *
 * Initializes Firebase and subscribes to FCM topics on app start.
 * The Python backend Agent 5 publishes notifications to these topics.
 */
class CiroApplication : Application() {

    companion object {
        private const val TAG = "CiroApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        Log.d(TAG, "Firebase initialized")

        // Subscribe to FCM topics
        val messaging = FirebaseMessaging.getInstance()

        messaging.subscribeToTopic("public_alerts")
            .addOnCompleteListener { task ->
                Log.d(TAG, "Subscribed to public_alerts: ${task.isSuccessful}")
            }

        messaging.subscribeToTopic("emergency_services")
            .addOnCompleteListener { task ->
                Log.d(TAG, "Subscribed to emergency_services: ${task.isSuccessful}")
            }

        messaging.subscribeToTopic("admin")
            .addOnCompleteListener { task ->
                Log.d(TAG, "Subscribed to admin: ${task.isSuccessful}")
            }
    }
}
