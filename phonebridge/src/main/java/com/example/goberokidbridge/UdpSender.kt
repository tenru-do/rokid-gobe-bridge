package com.example.goberokidbridge

import android.content.Context
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.Charset

object UdpSender {
    fun send(context: Context, payload: HealthPayload) {
        if (!payload.hasAnyMetric()) return
        PayloadStore(context).save(payload)
        val settings = BridgeSettings(context)
        if (settings.glassesHost.isBlank()) return
        val body = payload.encode().toByteArray(Charset.forName("UTF-8"))
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
