package com.example.goberokidbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class BridgeForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        BridgeRequestServer.start(this)
        DirectGoBeBleBridge.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BridgeRequestServer.start(this)
        DirectGoBeBleBridge.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        DirectGoBeBleBridge.stop()
        BridgeRequestServer.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = ensureChannel()
        return Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("GoBe Rokid Bridge")
            .setContentText("Waiting for Rokid and HEALBE data.")
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GoBe Rokid Bridge",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return CHANNEL_ID
    }

    companion object {
        private const val CHANNEL_ID = "gobe_rokid_bridge"
        private const val NOTIFICATION_ID = 2001
    }
}
