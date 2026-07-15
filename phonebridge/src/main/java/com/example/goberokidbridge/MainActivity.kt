package com.example.goberokidbridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var settings: BridgeSettings
    private lateinit var hostInput: EditText
    private lateinit var gobePinInput: EditText
    private lateinit var waterInput: EditText
    private lateinit var energyInput: EditText
    private lateinit var stepsInput: EditText
    private lateinit var pulseInput: EditText
    private lateinit var stressInput: EditText
    private lateinit var batteryInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = BridgeSettings(this)
        requestRuntimePermissions()
        BridgeRuntime.start(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        fillFromLastPayload()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }

        root.addView(label("GoBe Rokid Bridge"))
        root.addView(small(HealbeSdkAvailability.statusText()))
        root.addView(small("Bridge service is kept alive in the background. Rokid can request the latest cached HEALBE values without opening this screen."))

        hostInput = input("Rokid IP", settings.glassesHost, InputType.TYPE_CLASS_TEXT)
        root.addView(hostInput)
        gobePinInput = input("GoBe PIN for direct BLE", settings.gobePin, InputType.TYPE_CLASS_NUMBER)
        root.addView(gobePinInput)

        waterInput = input("Water %", "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
        energyInput = input("Energy kcal", "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
        stepsInput = input("Steps", "", InputType.TYPE_CLASS_NUMBER)
        pulseInput = input("Pulse bpm", "", InputType.TYPE_CLASS_NUMBER)
        stressInput = input("Stress", "", InputType.TYPE_CLASS_TEXT)
        batteryInput = input("Battery %", "", InputType.TYPE_CLASS_NUMBER)
        listOf(waterInput, energyInput, stepsInput, pulseInput, stressInput, batteryInput).forEach(root::addView)

        root.addView(button("Send to Rokid") { sendManual() })
        root.addView(button("Save PIN / Start GoBe BLE") {
            settings.gobePin = gobePinInput.text.toString()
            DirectGoBeBleBridge.restart(this)
            statusText.text = "Direct GoBe BLE scan started."
        })
        root.addView(button("Fill Last Values") { fillFromLastPayload() })
        root.addView(button("Open Notification Access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        root.addView(button("Open Accessibility Settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        statusText = small("Ready.")
        root.addView(statusText)

        return ScrollView(this).apply { addView(root) }
    }

    private fun fillFromLastPayload() {
        val store = PayloadStore(this)
        val payload = store.load()
        payload.water?.let { waterInput.setText(it.toString()) }
        payload.energy?.let { energyInput.setText(it.toString()) }
        payload.steps?.let { stepsInput.setText(it.toString()) }
        payload.pulse?.let { pulseInput.setText(it.toString()) }
        payload.stress?.let { stressInput.setText(it) }
        payload.battery?.let { batteryInput.setText(it.toString()) }
        val updatedAt = store.updatedAt()
        val bleStatus = BleBridgeStatus.message(this)
        val captureStatus = if (HealbeAccessibilityService.isEnabled(this)) {
            "HEALBE screen capture: enabled"
        } else {
            "HEALBE screen capture: disabled. Open Accessibility Settings and enable GoBe Rokid Bridge."
        }
        statusText.text = if (updatedAt > 0) {
            "Last: ${payload.encode()}\n${timeText(updatedAt)}\n\n$captureStatus\n\n$bleStatus"
        } else {
            "No HEALBE values captured yet.\n\n$captureStatus\n\n$bleStatus"
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 2001)
    }

    private fun sendManual() {
        settings.glassesHost = hostInput.text.toString()
        val payload = HealthPayload(
            water = waterInput.intOrNull(),
            energy = energyInput.intOrNull(),
            steps = stepsInput.intOrNull(),
            pulse = pulseInput.intOrNull(),
            stress = stressInput.text.toString().ifBlank { null },
            battery = batteryInput.intOrNull(),
            source = "MANUAL"
        )
        UdpSender.send(this, payload)
        statusText.text = "Sent: ${payload.encode()}"
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 24f
        gravity = Gravity.START
        setPadding(0, 0, 0, 16)
    }

    private fun small(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setPadding(0, 0, 0, 20)
    }

    private fun input(hint: String, value: String, type: Int): EditText = EditText(this).apply {
        this.hint = hint
        this.setText(value)
        inputType = type
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { action() }
    }

    private fun EditText.intOrNull(): Int? = text.toString().trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

    private fun timeText(value: Long): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(value))
    }
}
