package com.scottsx.app.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.scottsx.app.MainActivity
import com.scottsx.app.R
import com.scottsx.app.SessionCache
import com.scottsx.app.data.remote.V2Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives push notifications from Firebase Cloud Messaging.
 *
 * The backend fans messages out to every device token registered against a
 * user (see notify.service.ts). Delivery is best-effort: if Firebase is not
 * configured server-side the notification is still persisted and shows up in
 * the in-app notification centre, so nothing is ever lost.
 */
class ScottsMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ORDERS = "orders"
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_PRODUCTS = "new_products"
        const val CHANNEL_GENERAL = "general"

        /**
         * Creates the notification channels. Safe to call repeatedly; Android
         * ignores a channel that already exists.
         */
        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channels = listOf(
                Triple(CHANNEL_ORDERS, "Order updates", "Payments, dispatch and delivery"),
                Triple(CHANNEL_MESSAGES, "Messages", "Chats and price offers"),
                Triple(CHANNEL_PRODUCTS, "New products", "Stores you follow post something new"),
                Triple(CHANNEL_GENERAL, "General", "Account and platform notices"),
            )
            for ((id, name, description) in channels) {
                val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
                channel.description = description
                manager.createNotificationChannel(channel)
            }
        }

        /**
         * Uploads the current FCM token so this device can receive pushes.
         * Called after sign-in — a token registered while logged out belongs to
         * nobody.
         */
        fun registerCurrentToken(context: Context) {
            if (SessionCache.authToken() == null) return
            ensureChannels(context)
            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    val token = task.result
                    if (!task.isSuccessful || token.isNullOrBlank()) return@addOnCompleteListener
                    CoroutineScope(Dispatchers.IO).launch {
                        V2Client.registerDevice(token, "android")
                    }
                }
            } catch (e: Exception) {
                // Firebase not configured in this build; in-app notifications
                // still work.
            }
        }

        /** Which channel a backend notification type belongs to. */
        private fun channelFor(type: String?): String = when (type) {
            "order_update" -> CHANNEL_ORDERS
            "message" -> CHANNEL_MESSAGES
            "new_product", "price_drop" -> CHANNEL_PRODUCTS
            else -> CHANNEL_GENERAL
        }
    }

    /** Fired when FCM issues a new token (fresh install, app data cleared…). */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (SessionCache.authToken() == null) return
        CoroutineScope(Dispatchers.IO).launch {
            V2Client.registerDevice(token, "android")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "ScottsTechX"
        val body = message.notification?.body ?: data["body"] ?: ""
        val type = data["type"]

        // Deep-link payload so tapping the notification opens the right screen.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        ensureChannels(this)

        val notification = NotificationCompat.Builder(this, channelFor(type))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.logo)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        // On Android 13+ posting without POST_NOTIFICATIONS throws.
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        try {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}
