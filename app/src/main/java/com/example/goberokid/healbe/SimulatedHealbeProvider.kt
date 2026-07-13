package com.example.goberokid.healbe

import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt

class SimulatedHealbeProvider : HealthDataProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var callback: HealthDataProvider.Callback? = null
    private var tick = 0

    private val updateTask = object : Runnable {
        override fun run() {
            tick += 1
            val drift = kotlin.math.sin(tick / 3.0)
            callback?.onSnapshot(
                HealthSnapshot(
                    waterBalancePercent = (84 + drift * 3).roundToInt().coerceIn(0, 100),
                    waterStatus = "Normal",
                    energyBalanceKcal = -240 + tick * 4,
                    stepsToday = 6240 + tick * 12,
                    pulseBpm = (72 + drift * 4).roundToInt(),
                    stressLevel = if (tick % 7 == 0) "Medium" else "Low",
                    batteryPercent = (94 - tick / 6).coerceIn(20, 100),
                    updatedAtMillis = System.currentTimeMillis(),
                    source = "SIM"
                )
            )
            handler.postDelayed(this, 5_000)
        }
    }

    override fun start(callback: HealthDataProvider.Callback) {
        this.callback = callback
        callback.onConnecting("Scanning for GoBe U over Bluetooth LE...")
        handler.postDelayed(updateTask, 1_500)
    }

    override fun stop() {
        handler.removeCallbacks(updateTask)
        callback = null
    }
}
