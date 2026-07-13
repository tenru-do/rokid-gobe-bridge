package com.example.goberokidbridge

import android.content.Context

class PayloadStore(context: Context) {
    private val preferences = context.getSharedPreferences("last_payload", Context.MODE_PRIVATE)

    fun save(payload: HealthPayload) {
        val previous = load()
        preferences.edit()
            .putInt(KEY_WATER, payload.water ?: previous.water ?: UNKNOWN)
            .putString(KEY_WATER_STATUS, payload.waterStatus ?: previous.waterStatus.orEmpty())
            .putInt(KEY_ENERGY, payload.energy ?: previous.energy ?: UNKNOWN)
            .putInt(KEY_STEPS, payload.steps ?: previous.steps ?: UNKNOWN)
            .putInt(KEY_PULSE, payload.pulse ?: previous.pulse ?: UNKNOWN)
            .putString(KEY_STRESS, payload.stress ?: previous.stress.orEmpty())
            .putInt(KEY_BATTERY, payload.battery ?: previous.battery ?: UNKNOWN)
            .putString(KEY_SOURCE, payload.source)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun load(): HealthPayload {
        return HealthPayload(
            water = preferences.optionalInt(KEY_WATER),
            waterStatus = preferences.getString(KEY_WATER_STATUS, null)?.takeIf { it.isNotBlank() },
            energy = preferences.optionalInt(KEY_ENERGY),
            steps = preferences.optionalInt(KEY_STEPS),
            pulse = preferences.optionalInt(KEY_PULSE),
            stress = preferences.getString(KEY_STRESS, null)?.takeIf { it.isNotBlank() },
            battery = preferences.optionalInt(KEY_BATTERY),
            source = preferences.getString(KEY_SOURCE, "PHONE") ?: "PHONE"
        )
    }

    fun updatedAt(): Long = preferences.getLong(KEY_UPDATED, 0L)

    private fun android.content.SharedPreferences.optionalInt(key: String): Int? {
        val value = getInt(key, UNKNOWN)
        return if (value == UNKNOWN) null else value
    }

    companion object {
        private const val UNKNOWN = Int.MIN_VALUE
        private const val KEY_WATER = "water"
        private const val KEY_WATER_STATUS = "water_status"
        private const val KEY_ENERGY = "energy"
        private const val KEY_STEPS = "steps"
        private const val KEY_PULSE = "pulse"
        private const val KEY_STRESS = "stress"
        private const val KEY_BATTERY = "battery"
        private const val KEY_SOURCE = "source"
        private const val KEY_UPDATED = "updated"
    }
}
