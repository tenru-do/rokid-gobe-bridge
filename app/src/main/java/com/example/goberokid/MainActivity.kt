package com.example.goberokid

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.goberokid.healbe.BroadcastHealthDataProvider
import com.example.goberokid.healbe.CombinedHealthDataProvider
import com.example.goberokid.healbe.HealthDataProvider
import com.example.goberokid.healbe.HealthSnapshot
import com.example.goberokid.healbe.UdpHealthDataProvider

class MainActivity : Activity(), HealthDataProvider.Callback {
    private lateinit var tvStatus: TextView
    private lateinit var rootView: LinearLayout
    private lateinit var healthDataProvider: HealthDataProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        rootView = findViewById(R.id.rootView)
        rootView.setOnClickListener { finish() }

        healthDataProvider = CombinedHealthDataProvider(
            UdpHealthDataProvider(this),
            BroadcastHealthDataProvider(this)
        )
        healthDataProvider.start(this)
    }

    override fun onDestroy() {
        healthDataProvider.stop()
        super.onDestroy()
    }

    override fun onConnecting(message: String) {
        runOnUiThread {
            tvStatus.text = buildString {
                appendLine("=======================")
                appendLine("   [ HEALBE STATUS ]")
                appendLine("=======================")
                appendLine(message)
            }
        }
    }

    override fun onSnapshot(snapshot: HealthSnapshot) {
        runOnUiThread {
            tvStatus.text = formatSnapshot(snapshot)
        }
    }

    override fun onError(message: String, throwable: Throwable?) {
        runOnUiThread {
            tvStatus.text = buildString {
                appendLine("=======================")
                appendLine("   [ HEALBE STATUS ]")
                appendLine("=======================")
                appendLine("ERROR")
                appendLine(message)
                throwable?.localizedMessage?.let { appendLine(it) }
                appendLine("=======================")
                append("[ tap to close ]")
            }
        }
    }

    private fun ensureBlePermissionsThenStart() {
        val missingPermissions = requiredBlePermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            healthDataProvider.start(this)
        } else {
            requestPermissions(missingPermissions.toTypedArray(), BLE_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLE_REQUEST && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            healthDataProvider.start(this)
        } else {
            onError("Bluetooth permissions are required to connect to GoBe U.")
        }
    }

    private fun requiredBlePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun formatSnapshot(snapshot: HealthSnapshot): String = buildString {
        appendLine("=======================")
        appendLine("   [ HEALBE STATUS ]")
        appendLine("=======================")
        appendLine("Water  ${waterText(snapshot)}")
        appendLine("Energy ${formatSigned(snapshot.energyBalanceKcal)} kcal ${energyHint(snapshot.energyBalanceKcal)}")
        appendLine("Steps  ${valueText(snapshot.stepsToday)}")
        appendLine("Pulse  ${valueText(snapshot.pulseBpm)} bpm")
        appendLine("Stress ${snapshot.stressLevel}")
        appendLine("Battery ${percentText(snapshot.batteryPercent)}")
        appendLine("Updated ${timeText(snapshot.updatedAtMillis)}  ${snapshot.source}")
        appendLine("=======================")
        append("[ tap to close ]")
    }

    private fun bar(percent: Int): String {
        if (percent < 0) return "[..........]"
        val filled = (percent.coerceIn(0, 100) / 10).coerceIn(0, 10)
        return "[" + "#".repeat(filled) + ".".repeat(10 - filled) + "]"
    }

    private fun percentText(value: Int): String = if (value < 0) "--%" else "$value%"

    private fun waterText(snapshot: HealthSnapshot): String {
        snapshot.waterStatus?.takeIf { it.isNotBlank() }?.let { status ->
            return if (snapshot.waterBalancePercent >= 0) "$status ${percentText(snapshot.waterBalancePercent)}" else status
        }
        return "${bar(snapshot.waterBalancePercent)} ${percentText(snapshot.waterBalancePercent)}"
    }

    private fun valueText(value: Int): String = if (value <= 0) "--" else value.toString()

    private fun formatSigned(value: Int): String = if (value > 0) "+$value" else value.toString()

    private fun energyHint(value: Int): String {
        return when {
            value <= -300 -> "DEFICIT"
            value >= 300 -> "SURPLUS"
            else -> "OK"
        }
    }

    private fun timeText(value: Long): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(value))
    }

    companion object {
        private const val BLE_REQUEST = 1001
    }
}
