package de.example.snapauto

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Notifications never start automation. Only the explicit START button can arm it. */
class SnapNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) = Unit
}
