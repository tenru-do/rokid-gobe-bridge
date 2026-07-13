package com.example.goberokid.healbe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

class BroadcastHealthDataProvider(
    private val context: Context
) : HealthDataProvider {
    private var callback: HealthDataProvider.Callback? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_UPDATE) return

            val snapshot = HealthSnapshot(
                waterBalancePercent = intent.getIntExtra(EXTRA_WATER, UNKNOWN_PERCENT),
                waterStatus = intent.getStringExtra(EXTRA_WATER_STATUS),
                energyBalanceKcal = intent.getIntExtra(EXTRA_ENERGY, 0),
                stepsToday = intent.getIntExtra(EXTRA_STEPS, 0),
                pulseBpm = intent.getIntExtra(EXTRA_PULSE, 0),
                stressLevel = intent.getStringExtra(EXTRA_STRESS) ?: "Unknown",
                batteryPercent = intent.getIntExtra(EXTRA_BATTERY, UNKNOWN_PERCENT),
                updatedAtMillis = System.currentTimeMillis(),
                source = intent.getStringExtra(EXTRA_SOURCE) ?: "PHONE"
            )
            callback?.onSnapshot(snapshot)
        }
    }

    override fun start(callback: HealthDataProvider.Callback) {
        this.callback = callback
        if (!registered) {
            val filter = IntentFilter(ACTION_UPDATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            registered = true
        }
        callback.onConnecting(
            "Waiting for phone/SDK data...\n" +
                "The display will update only when live values are received."
        )
    }

    override fun stop() {
        if (registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
        callback = null
    }

    companion object {
        const val ACTION_UPDATE = "com.example.goberokid.UPDATE_HEALTH"
        const val EXTRA_WATER = "water"
        const val EXTRA_WATER_STATUS = "waterStatus"
        const val EXTRA_ENERGY = "energy"
        const val EXTRA_STEPS = "steps"
        const val EXTRA_PULSE = "pulse"
        const val EXTRA_STRESS = "stress"
        const val EXTRA_BATTERY = "battery"
        const val EXTRA_SOURCE = "source"

        private const val UNKNOWN_PERCENT = -1
    }
}
