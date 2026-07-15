package com.example.goberokidbridge

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class HealbeAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastPayloadText = ""
    private var lastSentAt = 0L
    private val pollTask = object : Runnable {
        override fun run() {
            readActiveHealbeWindow()
            handler.postDelayed(this, 3_000)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        BridgeRequestServer.start(this)
        if (event.packageName?.toString() != HEALBE_PACKAGE) return
        readActiveHealbeWindow()
    }

    override fun onServiceConnected() {
        currentService = this
        BridgeRequestServer.start(this)
        handler.removeCallbacks(pollTask)
        handler.post(pollTask)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (currentService === this) currentService = null
        handler.removeCallbacks(pollTask)
        super.onDestroy()
    }

    private fun readActiveHealbeWindow() {
        val root = rootInActiveWindow ?: return
        val screenText = collectText(root).joinToString("\n")
        if (!screenText.isCompleteHealbeHome()) return
        val payload = HealbeTextParser.parse(screenText) ?: return
        if (!payload.hasAnyMetric()) return

        val encoded = payload.encode()
        val now = System.currentTimeMillis()
        if (encoded == lastPayloadText && now - lastSentAt < 4_000) return

        PayloadStore(this).save(payload, keepMissingValues = false)
        UdpSender.send(this, payload, keepMissingValues = false)
        lastPayloadText = encoded
        lastSentAt = now
    }

    private fun collectText(node: AccessibilityNodeInfo): List<String> {
        val values = mutableListOf<String>()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(values::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(values::add)
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                values += collectText(child)
            }
        }
        return values
    }

    private fun String.isCompleteHealbeHome(): Boolean {
        return contains("\u30a8\u30cd\u30eb\u30ae\u30fc\u30d0\u30e9\u30f3\u30b9") &&
            contains("GBU_") &&
            contains("kcal") &&
            contains("\u6b69") &&
            (contains("\u8108\u62cd") || contains("\u6c34\u5206"))
    }

    companion object {
        private const val HEALBE_PACKAGE = "com.healbe.healbegobe"
        @Volatile
        private var currentService: HealbeAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val flattened = "${context.packageName}/${HealbeAccessibilityService::class.java.name}"
            return enabledServices.split(':').any { it.equals(flattened, ignoreCase = true) }
        }

        fun requestActiveRead() {
            currentService?.readActiveHealbeWindow()
        }
    }
}
