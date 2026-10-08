package de.example.snapauto

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class SnapNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.snapchat.android") return
        if (!getSharedPreferences("snapauto", MODE_PRIVATE).getBoolean("enabled", false)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val looksLikeSnap = text.contains("Snap", ignoreCase = true) ||
            text.contains("sent you", ignoreCase = true) ||
            text.contains("gesendet", ignoreCase = true)
        if (!looksLikeSnap) return

        SnapState.pendingSender = title
        SnapState.pendingUntil = System.currentTimeMillis() + 15_000
        try {
            sbn.notification.contentIntent?.send()
        } catch (_: Exception) {
            packageManager.getLaunchIntentForPackage("com.snapchat.android")?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
            }
        }
    }
}