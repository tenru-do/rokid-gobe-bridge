package com.example.goberokid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.goberokid.environment.OpenMeteoWeatherProvider
import com.example.goberokid.environment.WeatherStatus
import com.example.goberokid.healbe.BroadcastHealthDataProvider
import com.example.goberokid.healbe.CombinedHealthDataProvider
import com.example.goberokid.healbe.HealthDataProvider
import com.example.goberokid.healbe.HealthSnapshot
import com.example.goberokid.healbe.UdpHealthDataProvider

class MainActivity : Activity(), HealthDataProvider.Callback, OpenMeteoWeatherProvider.Callback {
    private lateinit var tvStatus: TextView
    private lateinit var rootView: LinearLayout
    private lateinit var healthDataProvider: HealthDataProvider
    private lateinit var weatherProvider: OpenMeteoWeatherProvider
    private val handler = Handler(Looper.getMainLooper())
    private var latestSnapshot: HealthSnapshot? = null
    private var connectingMessage: String = "Connecting to GoBe U..."
    private var latestError: String? = null
    private var latestWeather = WeatherStatus()
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, CLOCK_REFRESH_MS)
        }
    }

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
        weatherProvider = OpenMeteoWeatherProvider(this)
        healthDataProvider.start(this)
        weatherProvider.start(this)
        handler.post(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        weatherProvider.stop()
        healthDataProvider.stop()
        super.onDestroy()
    }

    override fun onConnecting(message: String) {
        runOnUiThread {
            connectingMessage = message
            latestError = null
            render()
        }
    }

    override fun onSnapshot(snapshot: HealthSnapshot) {
        runOnUiThread {
            latestSnapshot = snapshot
            latestError = null
            render()
        }
    }

    override fun onError(message: String, throwable: Throwable?) {
        runOnUiThread {
            latestError = listOfNotNull("ERROR", message, throwable?.localizedMessage).joinToString("\n")
            render()
        }
    }

    override fun onWeather(status: WeatherStatus) {
        runOnUiThread {
            latestWeather = status
            render()
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

    private fun render() {
        tvStatus.text = buildScreen()
    }

    private fun buildScreen(): String = buildString {
        appendLine("Rokid ${glassesBatteryText()}   ${dateText()}")
        appendLine("${timeText(System.currentTimeMillis())}   ${latestWeather.temperatureText} ${latestWeather.label}")
        appendLine("====================")
        appendLine("[ HEALBE STATUS ]")
        appendLine("====================")
        latestError?.let {
            appendLine(it)
            appendLine("====================")
            append("[ tap to close ]")
            return@buildString
        }

        val snapshot = latestSnapshot
        if (snapshot == null) {
            appendLine(connectingMessage)
        } else {
            append(formatSnapshot(snapshot))
        }
    }

    private fun formatSnapshot(snapshot: HealthSnapshot): String = buildString {
        appendLine("Water   ${waterText(snapshot)}")
        appendLine("Energy  ${formatSigned(snapshot.energyBalanceKcal)} kcal ${energyHint(snapshot.energyBalanceKcal)}")
        appendLine("Steps   ${valueText(snapshot.stepsToday)}")
        appendLine("Pulse   ${valueText(snapshot.pulseBpm)} bpm")
        appendLine("Stress  ${snapshot.stressLevel}")
        appendLine("GoBe    ${percentText(snapshot.batteryPercent)}")
        appendLine("Updated ${timeText(snapshot.updatedAtMillis)} ${snapshot.source}")
        appendLine("====================")
        append("[ tap to close ]")
    }

    private fun glassesBatteryText(): String {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val suffix = if (charging) "+" else ""
        return "Batt ${percentText(percent)}$suffix"
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

    private fun dateText(): String {
        return SimpleDateFormat("yyyy/MM/dd E", Locale.US).format(Date())
    }

    companion object {
        private const val BLE_REQUEST = 1001
        private const val CLOCK_REFRESH_MS = 1000L
    }
}
