package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.RideBooking
import com.example.data.RideStatus

/**
 * Ride & Dispatch Notification Manager for JARANWALA RIDE.
 * Alerts passengers on trip lifecycle updates, alerts captains on new dispatch requests/approvals,
 * and alerts MAHI Owner Admin on security incidents, with deep-link Intent extras.
 */
object RideNotificationManager {

    const val CHANNEL_ID = "jaranwala_ride_status_channel"
    const val CHANNEL_NAME = "Jaranwala Ride Updates"
    const val DISPATCH_CHANNEL_ID = "jaranwala_driver_dispatch_channel"
    const val DISPATCH_CHANNEL_NAME = "Captain Dispatch Alerts"
    const val SECURITY_CHANNEL_ID = "jaranwala_owner_security_channel"
    const val SECURITY_CHANNEL_NAME = "Owner Security Alerts"
    const val LOCATION_CHANNEL_ID = "jaranwala_driver_location_channel"
    const val LOCATION_CHANNEL_NAME = "Captain Live GPS Tracking"

    const val NOTIFICATION_ID_BASE = 8000
    const val EXTRA_RIDE_ID = "EXTRA_RIDE_ID"
    const val EXTRA_TARGET_TAB = "EXTRA_TARGET_TAB"
    const val EXTRA_NOTIFICATION_TYPE = "EXTRA_NOTIFICATION_TYPE"

    private val lastNotifiedMap = mutableMapOf<String, RideStatus>()

    // Exposed for testing
    var lastNotificationTitle: String? = null
        private set
    var lastNotificationBody: String? = null
        private set
    var lastNotifiedRideStatus: RideStatus? = null
        private set
    var lastNotificationType: String? = null
        private set
    var lastTargetTab: Int? = null
        private set
    var notificationCount: Int = 0
        private set

    fun resetForTesting() {
        lastNotifiedMap.clear()
        lastNotificationTitle = null
        lastNotificationBody = null
        lastNotifiedRideStatus = null
        lastNotificationType = null
        lastTargetTab = null
        notificationCount = 0
    }

    /**
     * Initializes the Android Notification Channels (required on Android 8.0+ / API 26+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val rideChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live updates when your captain is assigned, arrives at pickup, or completes the trip in Jaranwala"
                enableVibration(true)
                setShowBadge(true)
            }
            val dispatchChannel = NotificationChannel(
                DISPATCH_CHANNEL_ID,
                DISPATCH_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming passenger ride requests and Captain approval alerts"
                enableVibration(true)
                setShowBadge(true)
            }
            val securityChannel = NotificationChannel(
                SECURITY_CHANNEL_ID,
                SECURITY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority Owner Admin security & audit alerts for MAHI"
                enableVibration(true)
                setShowBadge(true)
            }
            val locationChannel = NotificationChannel(
                LOCATION_CHANNEL_ID,
                LOCATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing live GPS sharing while Captain is Online in Jaranwala Ride"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(rideChannel)
            notificationManager.createNotificationChannel(dispatchChannel)
            notificationManager.createNotificationChannel(securityChannel)
            notificationManager.createNotificationChannel(locationChannel)
        }
    }

    /**
     * Builds the ongoing foreground notification for real-time Captain GPS tracking.
     */
    fun buildLocationTrackingNotification(context: Context, rideId: String? = null): Notification {
        createNotificationChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_TAB, 1)
            if (!rideId.isNullOrBlank()) {
                putExtra(EXTRA_RIDE_ID, rideId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            8901,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = if (!rideId.isNullOrBlank()) {
            "Sharing live route & GPS to Firestore for active trip $rideId"
        } else {
            "Sharing live Captain GPS coordinates in Jaranwala & Pakistan"
        }
        return NotificationCompat.Builder(context, LOCATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Jaranwala Ride • Live GPS Active")
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * Dispatches a system push notification for ride status transitions.
     */
    fun notifyRideStatusChange(
        context: Context,
        ride: RideBooking
    ) {
        val rideId = ride.id
        val currentStatus = ride.status

        if (lastNotifiedMap[rideId] == currentStatus) {
            return
        }

        val (title, body) = when (currentStatus) {
            RideStatus.ACCEPTED -> {
                val driverName = ride.driverName ?: "Captain"
                val plate = ride.vehiclePlate ?: "Verified Vehicle"
                Pair(
                    "Captain Assigned! • کپتان تفویض ہو گیا",
                    "$driverName ($plate) has accepted your ride and is on the way to ${ride.pickup}."
                )
            }
            RideStatus.DRIVER_ARRIVED -> {
                val driverName = ride.driverName ?: "Captain"
                Pair(
                    "Captain Arrived! • کپتان پہنچ گئے",
                    "$driverName has arrived at your pickup location (${ride.pickup}). Please meet your captain."
                )
            }
            RideStatus.IN_PROGRESS -> {
                Pair(
                    "Trip Started • سفر جاری ہے",
                    "Heading towards ${ride.destination}. Safe travels with Jaranwala Ride!"
                )
            }
            RideStatus.COMPLETED -> {
                Pair(
                    "Ride Completed! • سواری مکمل ہو گئی",
                    "Arrived at ${ride.destination}. Total fare: ₨ ${ride.estimatedFarePKR}. Please rate your captain."
                )
            }
            RideStatus.CANCELLED -> {
                Pair(
                    "Ride Cancelled • سواری منسوخ ہو گئی",
                    "Your ride booking in Jaranwala has been cancelled."
                )
            }
            else -> return
        }

        lastNotifiedMap[rideId] = currentStatus
        lastNotifiedRideStatus = currentStatus
        dispatchSystemNotification(
            context = context,
            channelId = CHANNEL_ID,
            notificationId = NOTIFICATION_ID_BASE + kotlin.math.abs(rideId.hashCode() % 1000),
            title = title,
            body = body,
            notificationType = "RIDE_STATUS_${currentStatus.name}",
            rideId = rideId,
            targetTab = 0
        )
    }

    /**
     * Notifies an online, approved Captain of a new passenger dispatch request.
     */
    fun notifyDriverNewRequest(
        context: Context,
        rideId: String,
        pickup: String,
        destination: String,
        farePKR: Int,
        categoryTitle: String
    ) {
        val title = "New $categoryTitle Request • نئی سواری"
        val body = "$pickup ➔ $destination • Est. ₨ $farePKR (Tap to accept)"
        dispatchSystemNotification(
            context = context,
            channelId = DISPATCH_CHANNEL_ID,
            notificationId = NOTIFICATION_ID_BASE + 2000 + kotlin.math.abs(rideId.hashCode() % 1000),
            title = title,
            body = body,
            notificationType = "NEW_RIDE_REQUEST",
            rideId = rideId,
            targetTab = 1
        )
    }

    /**
     * Notifies a Captain when Owner Admin approves or suspends their documents.
     */
    fun notifyDriverApprovalChange(context: Context, approved: Boolean) {
        val title = if (approved) {
            "Captain Account Approved! • تصدیق مکمل"
        } else {
            "Captain Account Paused • اکاؤنٹ عارضی طور پر بند"
        }
        val body = if (approved) {
            "MAHI Owner Admin has verified your CNIC and vehicle. You can now go Online in Jaranwala Ride."
        } else {
            "Your Captain verification requires attention. Open the Captain portal for status details."
        }
        dispatchSystemNotification(
            context = context,
            channelId = DISPATCH_CHANNEL_ID,
            notificationId = NOTIFICATION_ID_BASE + 3100,
            title = title,
            body = body,
            notificationType = "DRIVER_APPROVAL",
            rideId = null,
            targetTab = 1
        )
    }

    /**
     * Notifies MAHI Owner Admin of critical security events or suspicious access attempts.
     */
    fun notifyOwnerSecurityAlert(
        context: Context,
        title: String,
        description: String
    ) {
        dispatchSystemNotification(
            context = context,
            channelId = SECURITY_CHANNEL_ID,
            notificationId = NOTIFICATION_ID_BASE + 4000 + kotlin.math.abs(description.hashCode() % 500),
            title = title,
            body = description,
            notificationType = "OWNER_SECURITY_ALERT",
            rideId = null,
            targetTab = 3
        )
    }

    /**
     * Routes incoming FCM RemoteMessage payloads (both foreground data-only and notification messages).
     */
    fun handleFcmDataPayload(
        context: Context,
        data: Map<String, String>,
        fallbackTitle: String? = null,
        fallbackBody: String? = null
    ) {
        val eventType = data["type"]?.trim()?.uppercase().orEmpty()
        val rideId = data["rideId"]?.takeIf { it.isNotBlank() }
        val explicitTab = data["targetTab"]?.toIntOrNull()

        when (eventType) {
            "NEW_RIDE_REQUEST" -> {
                val pickup = data["pickup"] ?: "Jaranwala"
                val destination = data["destination"] ?: "Destination"
                val fare = data["farePKR"]?.toIntOrNull() ?: 150
                val category = data["vehicleCategory"] ?: "Ride"
                notifyDriverNewRequest(
                    context = context,
                    rideId = rideId ?: "REQ-LIVE",
                    pickup = pickup,
                    destination = destination,
                    farePKR = fare,
                    categoryTitle = category
                )
            }
            "CAPTAIN_ASSIGNED", "CAPTAIN_ARRIVED", "TRIP_STARTED", "TRIP_COMPLETED", "RIDE_CANCELLED" -> {
                val status = when (eventType) {
                    "CAPTAIN_ASSIGNED" -> RideStatus.ACCEPTED
                    "CAPTAIN_ARRIVED" -> RideStatus.DRIVER_ARRIVED
                    "TRIP_STARTED" -> RideStatus.IN_PROGRESS
                    "TRIP_COMPLETED" -> RideStatus.COMPLETED
                    else -> RideStatus.CANCELLED
                }
                lastNotifiedRideStatus = status
                val title = fallbackTitle ?: data["title"] ?: status.display
                val body = fallbackBody ?: data["body"] ?: "Jaranwala Ride status updated to ${status.display}."
                dispatchSystemNotification(
                    context = context,
                    channelId = CHANNEL_ID,
                    notificationId = NOTIFICATION_ID_BASE + kotlin.math.abs((rideId ?: eventType).hashCode() % 1000),
                    title = title,
                    body = body,
                    notificationType = eventType,
                    rideId = rideId,
                    targetTab = explicitTab ?: 0
                )
            }
            "DRIVER_APPROVED", "DRIVER_APPROVAL" -> {
                val approved = data["approved"]?.toBooleanStrictOrNull() ?: true
                notifyDriverApprovalChange(context, approved)
            }
            "OWNER_SECURITY_ALERT", "EMERGENCY_SOS" -> {
                val title = fallbackTitle ?: data["title"] ?: "MAHI Security Alert"
                val body = fallbackBody ?: data["body"] ?: "Security event logged in Owner Admin Center."
                notifyOwnerSecurityAlert(context, title, body)
            }
            else -> {
                val title = fallbackTitle ?: data["title"] ?: "Jaranwala Ride"
                val body = fallbackBody ?: data["body"] ?: "You have a new update."
                val targetTab = explicitTab ?: if (rideId != null) 0 else 0
                dispatchSystemNotification(
                    context = context,
                    channelId = CHANNEL_ID,
                    notificationId = NOTIFICATION_ID_BASE + kotlin.math.abs((rideId ?: title).hashCode() % 1000),
                    title = title,
                    body = body,
                    notificationType = eventType.ifBlank { "GENERAL" },
                    rideId = rideId,
                    targetTab = targetTab
                )
            }
        }
    }

    private fun dispatchSystemNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        notificationType: String,
        rideId: String?,
        targetTab: Int
    ) {
        lastNotificationTitle = title
        lastNotificationBody = body
        lastNotificationType = notificationType
        lastTargetTab = targetTab
        notificationCount++

        createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!rideId.isNullOrBlank()) {
                putExtra(EXTRA_RIDE_ID, rideId)
            }
            putExtra(EXTRA_TARGET_TAB, targetTab)
            putExtra(EXTRA_NOTIFICATION_TYPE, notificationType)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            notificationManager?.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Handled safely
        }
    }
}
