package com.example.goberokidbridge

import android.content.Context
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
                        if (store.isStale()) {
                            HealbeAccessibilityService.requestActiveRead()
                            DirectGoBeBleBridge.requestFreshPoll(appContext)
                            waitForFreshPayload(store)
                        }
                        val payload = store.load()
                        if (payload.hasAnyMetric()) {
                            val responsePayload = if (store.isStale()) {
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

    private fun waitForFreshPayload(store: PayloadStore) {
        val deadline = System.currentTimeMillis() + FRESH_WAIT_MS
        while (System.currentTimeMillis() < deadline && store.isStale()) {
            Thread.sleep(200)
        }
    }

    private const val FRESH_WAIT_MS = 2_500L
}
