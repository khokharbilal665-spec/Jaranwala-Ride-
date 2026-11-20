package com.example.util

import android.util.Log
import com.example.data.JaranwalaRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging service for JARANWALA RIDE.
 * Handles FCM registration token updates and dispatches incoming data/notification
 * payloads for passenger status updates, driver dispatch alerts, and MAHI security notices.
 */
class JaranwalaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed")
        val repository = JaranwalaRepository.getInstance()
        val uid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Throwable) {
            null
        } ?: repository.currentUser.value?.uid

        if (!uid.isNullOrBlank()) {
            repository.saveFcmToken(uid = uid, token = token, context = applicationContext)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received from=${message.from}, dataKeys=${message.data.keys}")
        val notifTitle = message.notification?.title
        val notifBody = message.notification?.body
        JaranwalaRepository.getInstance().syncFromFcmPayload(message.data)
        RideNotificationManager.handleFcmDataPayload(
            context = applicationContext,
            data = message.data,
            fallbackTitle = notifTitle,
            fallbackBody = notifBody
        )
    }

    companion object {
        private const val TAG = "JaranwalaFcmService"
    }
}
