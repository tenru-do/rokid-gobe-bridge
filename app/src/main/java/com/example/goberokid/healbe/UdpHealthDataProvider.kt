package com.example.goberokid.healbe

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.nio.charset.Charset

class UdpHealthDataProvider(
    private val context: Context,
    private val port: Int = DEFAULT_PORT
) : HealthDataProvider {
    @Volatile
    private var running = false
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null
    private var requester: Thread? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var callback: HealthDataProvider.Callback? = null

    override fun start(callback: HealthDataProvider.Callback) {
        this.callback = callback
        running = true
        multicastLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("gobe-rokid-request")
            ?.apply {
                setReferenceCounted(false)
                runCatching { acquire() }
            }
        callback.onConnecting(
            "Requesting latest HEALBE data from phone...\n" +
                "UDP port: $port\n" +
                "No fake values are shown."
        )

        worker = Thread({
            runCatching {
                DatagramSocket(port).use { datagramSocket ->
                    socket = datagramSocket
                    val buffer = ByteArray(2048)
                    while (running) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        datagramSocket.receive(packet)
                        val payload = String(packet.data, packet.offset, packet.length, Charset.forName("UTF-8"))
                        val snapshot = HealthPayloadParser.parse(payload, defaultSource = "PHONE")
                        callback.onSnapshot(snapshot)
                    }
                }
            }.onFailure { throwable ->
                if (running && throwable !is SocketException) {
                    callback.onError("UDP receiver stopped: ${throwable.localizedMessage}", throwable)
                }
            }
        }, "gobe-udp-health").apply {
            isDaemon = true
            start()
        }

        requester = Thread({
            while (running) {
                sendLatestRequest()
                Thread.sleep(REQUEST_INTERVAL_MS)
            }
        }, "gobe-udp-request").apply {
            isDaemon = true
            start()
        }
    }

    override fun stop() {
        running = false
        socket?.close()
        multicastLock?.runCatching { release() }
        worker = null
        requester = null
        callback = null
    }

    private fun sendLatestRequest() {
        runCatching {
            val body = "request=latest;replyPort=$port;source=ROKID"
                .toByteArray(Charset.forName("UTF-8"))
            DatagramSocket().use { datagramSocket ->
                datagramSocket.broadcast = true
                requestTargets().forEach { target ->
                    val packet = DatagramPacket(body, body.size, target, REQUEST_PORT)
                    datagramSocket.send(packet)
                }
            }
        }
    }

    private fun requestTargets(): List<InetAddress> {
        val targets = mutableListOf(
            "255.255.255.255",
            "192.168.0.255",
            "192.168.1.255"
        ).mapTo(mutableListOf()) { InetAddress.getByName(it) }

        targets += localIpv4Addresses()
            .flatMap { address -> subnetTargets(address) }

        return targets.distinctBy { it.hostAddress }
    }

    private fun localIpv4Addresses(): List<String> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { networkInterface -> networkInterface.inetAddresses.toList() }
                .mapNotNull { address ->
                    address.hostAddress
                        ?.takeIf { it.count { char -> char == '.' } == 3 }
                        ?.takeUnless { it.startsWith("127.") }
                }
        }.getOrDefault(emptyList())
    }

    private fun subnetTargets(address: String): List<InetAddress> {
        val parts = address.split('.')
        if (parts.size != 4) return emptyList()
        val prefix = parts.take(3).joinToString(".")
        val self = parts[3].toIntOrNull() ?: return emptyList()
        return (1..254)
            .asSequence()
            .filter { it != self }
            .map { InetAddress.getByName("$prefix.$it") }
            .toList()
    }

    companion object {
        const val DEFAULT_PORT = 45454
        const val REQUEST_PORT = 45455
        private const val REQUEST_INTERVAL_MS = 10_000L
    }
}
