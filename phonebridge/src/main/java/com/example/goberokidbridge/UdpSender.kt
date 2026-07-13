package com.example.goberokidbridge

import android.content.Context
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.Charset

object UdpSender {
    fun send(context: Context, payload: HealthPayload, keepMissingValues: Boolean = true) {
        if (!payload.hasAnyMetric()) return
        val store = PayloadStore(context)
        store.save(payload, keepMissingValues)
        val savedPayload = store.load()
        val settings = BridgeSettings(context)
        if (settings.glassesHost.isBlank()) return
        val body = savedPayload.encode().toByteArray(Charset.forName("UTF-8"))
        Thread {
            runCatching {
                DatagramSocket().use { socket ->
                    val packet = DatagramPacket(
                        body,
                        body.size,
                        InetAddress.getByName(settings.glassesHost),
                        settings.glassesPort
                    )
                    socket.send(packet)
                }
            }
        }.start()
    }
}
