package com.example.goberokidbridge

import android.content.Context
import android.content.Intent
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

object BridgeRequestServer {
    private const val REQUEST_PORT = 45455
    private const val DEFAULT_REPLY_PORT = 45454

    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null

    fun start(context: Context) {
        OfficialHealbeSdkBridge.start(context)
        DirectGoBeBleBridge.start(context)
        if (!running.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        Thread({
            runCatching {
                DatagramSocket(REQUEST_PORT).use { datagramSocket ->
                    socket = datagramSocket
                    val buffer = ByteArray(2048)
                    while (running.get()) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        datagramSocket.receive(packet)
                        val body = String(packet.data, packet.offset, packet.length, Charset.forName("UTF-8"))
                        if (!body.contains("request=latest")) continue
                        val replyPort = parseReplyPort(body)
                        val store = PayloadStore(appContext)
                        val requestedAt = System.currentTimeMillis()
                        if (HealbeAccessibilityService.isEnabled(appContext)) {
                            HealbeAccessibilityService.requestActiveRead()
                        } else {
                            BleBridgeStatus.update(appContext, "HEALBE screen capture is disabled. Enable GoBe Rokid Bridge in Accessibility settings.")
                        }
                        DirectGoBeBleBridge.requestFreshPoll(appContext)
                        waitForFreshPayload(store, requestedAt, ACTIVE_READ_WAIT_MS)
                        if (store.updatedAt() < requestedAt && HealbeAccessibilityService.isEnabled(appContext)) {
                            openHealbeApp(appContext)
                            waitForFreshPayload(store, requestedAt, HEALBE_OPEN_WAIT_MS)
                        }
                        val payload = store.load()
                        if (payload.hasAnyMetric()) {
                            val responsePayload = if (store.isStale() || payload.updatedAtMillis < requestedAt) {
                                payload.copy(source = "STALE_${payload.source}")
                            } else {
                                payload
                            }
                            val bytes = responsePayload.encode().toByteArray(Charset.forName("UTF-8"))
                            val response = DatagramPacket(bytes, bytes.size, packet.address, replyPort)
                            datagramSocket.send(response)
                        }
                    }
                }
            }.onFailure { throwable ->
                if (running.get() && throwable !is SocketException) {
                    running.set(false)
                }
            }
        }, "gobe-bridge-request-server").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        socket?.close()
        socket = null
    }

    private fun parseReplyPort(body: String): Int {
        return body
            .split(';', '&', '\n')
            .firstOrNull { it.startsWith("replyPort=") }
            ?.substringAfter('=')
            ?.toIntOrNull()
            ?: DEFAULT_REPLY_PORT
    }

    private fun waitForFreshPayload(store: PayloadStore, requestedAt: Long, timeoutMillis: Long) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && (store.isStale() || store.updatedAt() < requestedAt)) {
            Thread.sleep(200)
        }
    }

    private fun openHealbeApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(HEALBE_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            ?: return
        runCatching {
            context.startActivity(intent)
            BleBridgeStatus.update(context, "Opened HEALBE app to refresh screen data.")
        }.onFailure {
            BleBridgeStatus.update(context, "Could not open HEALBE app automatically: ${it.localizedMessage}")
        }
    }

    private const val ACTIVE_READ_WAIT_MS = 1_000L
    private const val HEALBE_OPEN_WAIT_MS = 5_000L
    private const val HEALBE_PACKAGE = "com.healbe.healbegobe"
}
