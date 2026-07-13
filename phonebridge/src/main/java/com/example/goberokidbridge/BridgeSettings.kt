package com.example.goberokidbridge

import android.content.Context

class BridgeSettings(context: Context) {
    private val preferences = context.getSharedPreferences("bridge", Context.MODE_PRIVATE)

    var glassesHost: String
        get() = preferences.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) {
            preferences.edit().putString(KEY_HOST, value.trim()).apply()
        }

    var glassesPort: Int
        get() = preferences.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) {
            preferences.edit().putInt(KEY_PORT, value).apply()
        }

    var gobePin: String
        get() = preferences.getString(KEY_GOBE_PIN, "") ?: ""
        set(value) {
            preferences.edit().putString(KEY_GOBE_PIN, value.trim()).apply()
        }

    companion object {
        const val DEFAULT_HOST = ""
        const val DEFAULT_PORT = 45454
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_GOBE_PIN = "gobe_pin"
    }
}
