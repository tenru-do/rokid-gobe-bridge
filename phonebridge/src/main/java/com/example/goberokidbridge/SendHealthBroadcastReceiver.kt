package com.example.goberokidbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SendHealthBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PARSE) {
            val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
            HealbeTextParser.parse(text)?.let { UdpSender.send(context, it) }
            return
        }
        if (intent.action != ACTION_SEND) return

        intent.getStringExtra(EXTRA_HOST)?.takeIf { it.isNotBlank() }?.let {
            BridgeSettings(context).glassesHost = it
        }

        UdpSender.send(
            context,
            HealthPayload(
                water = intent.optionalInt(EXTRA_WATER),
                energy = intent.optionalInt(EXTRA_ENERGY),
                steps = intent.optionalInt(EXTRA_STEPS),
                pulse = intent.optionalInt(EXTRA_PULSE),
                stress = intent.getStringExtra(EXTRA_STRESS),
                battery = intent.optionalInt(EXTRA_BATTERY),
                source = intent.getStringExtra(EXTRA_SOURCE) ?: "PHONE"
            )
        )
    }

    private fun Intent.optionalInt(name: String): Int? {
        return if (hasExtra(name)) getIntExtra(name, 0) else null
    }

    companion object {
        const val ACTION_SEND = "com.example.goberokidbridge.SEND_HEALTH"
        const val ACTION_PARSE = "com.example.goberokidbridge.PARSE_HEALTH_TEXT"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_WATER = "water"
        private const val EXTRA_ENERGY = "energy"
        private const val EXTRA_STEPS = "steps"
        private const val EXTRA_PULSE = "pulse"
        private const val EXTRA_STRESS = "stress"
        private const val EXTRA_BATTERY = "battery"
        private const val EXTRA_SOURCE = "source"
    }
}
