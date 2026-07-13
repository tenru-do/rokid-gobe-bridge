package com.example.goberokidbridge

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class HealbeNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        BridgeRequestServer.start(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        BridgeRequestServer.start(this)
        if (sbn.packageName != HEALBE_PACKAGE) return

        // HEALBE notifications can contain partial values. Keep the UDP server alive,
        // but only the accessibility service saves complete home-screen snapshots.
    }

    companion object {
        private const val HEALBE_PACKAGE = "com.healbe.healbegobe"
    }
}
