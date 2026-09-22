package com.pashurakshak.app.data.remote

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pashurakshak.app.notifications.AppNotifications

/**
 * Receives FCM push messages and shows a local notification.
 *
 * Send data-only messages so this callback fires in both foreground and background:
 *   { "title": "...", "body": "..." }
 * Tapping the notification opens the app on the Alerts screen.
 *
 * Requires app/google-services.json (see google-services.json.example / README).
 */
class PushMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"]
            ?: message.notification?.title
            ?: DEFAULT_TITLE
        val body = message.data["body"]
            ?: message.notification?.body
            ?: return
        AppNotifications.show(applicationContext, title, body)
    }

    override fun onNewToken(token: String) {
        // TODO(backend): register the device token against the farmer/vet account
        // so the server can target this device. Stored nowhere yet on purpose.
        Log.d(TAG, "FCM token refreshed (not yet registered with backend)")
    }

    private companion object {
        const val TAG = "PushMessagingService"
        const val DEFAULT_TITLE = "PashuRakshak alert"
    }
}
