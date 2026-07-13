package com.example.goberokidbridge

import android.content.Context
import android.util.Log

object BleBridgeStatus {
    private const val PREFS = "ble_bridge_status"
    private const val KEY_MESSAGE = "message"
    private const val KEY_UPDATED = "updated"
    private const val TAG = "GoBeBleBridge"

    fun update(context: Context?, message: String) {
        Log.d(TAG, message)
        val appContext = context ?: return
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MESSAGE, message)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun message(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val message = prefs.getString(KEY_MESSAGE, "Direct GoBe BLE has not reported yet.") ?: "Direct GoBe BLE has not reported yet."
        val updated = prefs.getLong(KEY_UPDATED, 0L)
        return if (updated > 0L) "$message\nBLE status updated: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(updated))}" else message
    }
}
