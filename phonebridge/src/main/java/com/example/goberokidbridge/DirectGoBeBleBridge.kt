package com.example.goberokidbridge

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

object DirectGoBeBleBridge {
    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var scannerCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingPayload = HealthPayload(source = SOURCE)
    private var shortSummaryStartIdx = 0

    fun start(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        if (!running.compareAndSet(false, true)) {
            return
        }
        if (!hasBlePermissions(applicationContext)) {
            BleBridgeStatus.update(applicationContext, "Direct GoBe BLE needs Bluetooth permissions.")
            running.set(false)
            return
        }
        startScan(applicationContext)
    }

    fun restart(context: Context) {
        stop()
        start(context)
    }

    fun stop() {
        running.set(false)
        appContext?.let { context ->
            runCatching { bluetoothAdapter(context)?.bluetoothLeScanner?.stopScan(scannerCallback) }
        }
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        scannerCallback = null
        gatt = null
        dataCharacteristic = null
    }

    @SuppressLint("MissingPermission")
    private fun startScan(context: Context) {
        val adapter = bluetoothAdapter(context) ?: run {
            BleBridgeStatus.update(context, "Bluetooth adapter is not available.")
            running.set(false)
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            BleBridgeStatus.update(context, "Bluetooth LE scanner is not available.")
            running.set(false)
            return
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = runCatching { device.name }.getOrNull().orEmpty()
                val hasService = result.scanRecord?.serviceUuids?.any { it.uuid == DATA_SERVICE_UUID } == true
                if (!hasService && !name.contains("gobe", ignoreCase = true)) return
                scanner.stopScan(this)
                BleBridgeStatus.update(context, "Found GoBe device: ${name.ifBlank { device.address }}")
                connect(context, device)
            }

            override fun onScanFailed(errorCode: Int) {
                BleBridgeStatus.update(context, "GoBe BLE scan failed: $errorCode")
                running.set(false)
            }
        }
        scannerCallback = callback
        val filters = emptyList<ScanFilter>()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        BleBridgeStatus.update(context, "Scanning for GoBe BLE device...")
        scanner.startScan(filters, settings, callback)
    }

    @SuppressLint("MissingPermission")
    private fun connect(context: Context, device: BluetoothDevice) {
        BleBridgeStatus.update(context, "Connecting to GoBe BLE: ${device.address}")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!running.get()) return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                BleBridgeStatus.update(appContext, "GoBe BLE connected. Discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                BleBridgeStatus.update(appContext, "GoBe BLE disconnected. Reconnecting soon...")
                reconnectLater()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                BleBridgeStatus.update(appContext, "GoBe service discovery failed: $status")
                reconnectLater()
                return
            }
            val pin = gatt.getService(PIN_SERVICE_UUID)?.getCharacteristic(PIN_CHARACTERISTIC_UUID)
            dataCharacteristic = gatt.getService(DATA_SERVICE_UUID)?.getCharacteristic(DATA_CHARACTERISTIC_UUID)
            if (dataCharacteristic == null) {
                BleBridgeStatus.update(appContext, "GoBe data service was not found.")
                reconnectLater()
                return
            }
            BleBridgeStatus.update(appContext, "GoBe services ready. Preparing authentication...")
            enableNotifications(gatt, pin)
            dataCharacteristic?.let { enableNotifications(gatt, it) }
            mainHandler.postDelayed({ authenticateThenPoll(gatt, pin) }, 800)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(value)
        }

        @Deprecated("Kept for older Android devices.")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleNotification(characteristic.value ?: return)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (characteristic == null) return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID) ?: return
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    @SuppressLint("MissingPermission")
    private fun authenticateThenPoll(gatt: BluetoothGatt, pinCharacteristic: BluetoothGattCharacteristic?) {
        val context = appContext ?: return
        val pin = BridgeSettings(context).gobePin
        if (pin.isNotBlank() && pinCharacteristic != null) {
            BleBridgeStatus.update(context, "Sending GoBe PIN and starting data polling...")
            write(gatt, pinCharacteristic, pin.toByteArray(Charset.forName("UTF-8")))
        } else {
            BleBridgeStatus.update(context, "GoBe PIN is empty or PIN characteristic is missing. Polling may fail.")
        }
        mainHandler.postDelayed({ poll(gatt) }, 1_200)
    }

    private fun poll(gatt: BluetoothGatt) {
        if (!running.get()) return
        val commands = listOf(
            packet(TYPE_SENSOR_STATE),
            packet(TYPE_HEART_INFO),
            packet(TYPE_STRESS_LEVEL),
            packet(TYPE_FULL_SUMMARY),
            packet(TYPE_SHORT_SUMMARY_EX, recordSet(shortSummaryStartIdx, 1))
        )
        commands.forEachIndexed { index, bytes ->
            mainHandler.postDelayed({ writeData(gatt, bytes) }, index * 700L)
        }
        BleBridgeStatus.update(appContext, "GoBe BLE poll sent.")
        mainHandler.postDelayed({ poll(gatt) }, POLL_INTERVAL_MS)
    }

    @SuppressLint("MissingPermission")
    private fun writeData(gatt: BluetoothGatt, bytes: ByteArray) {
        val characteristic = dataCharacteristic ?: return
        write(gatt, characteristic, bytes)
    }

    @SuppressLint("MissingPermission")
    private fun write(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, bytes: ByteArray) {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            characteristic.value = bytes
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun handleNotification(value: ByteArray) {
        val packet = HealbePacket.parse(value) ?: run {
            BleBridgeStatus.update(appContext, "GoBe BLE notification: ${value.toHexText()}")
            return
        }
        BleBridgeStatus.update(appContext, "GoBe BLE packet received: type=0x${packet.command.toHexByte()} len=${packet.body.size}")
        when (packet.command) {
            TYPE_SENSOR_STATE -> parseSensorState(packet.body)
            TYPE_HEART_INFO -> parseHeartInfo(packet.body)
            TYPE_STRESS_LEVEL -> parseStress(packet.body)
            TYPE_FULL_SUMMARY -> parseFullSummary(packet.body)
            TYPE_SHORT_SUMMARY_EX -> parseShortSummaryEx(packet.body)
        }
    }

    private fun parseSensorState(body: ByteArray) {
        if (body.size < 40) return
        val buffer = body.leBuffer()
        buffer.position(8)
        val initFlags = buffer.int
        buffer.position(36)
        val battery = buffer.int
        val payload = HealthPayload(
            battery = battery.takeIf { it in 0..100 },
            stress = if (initFlags and FLG_SENSOR_IS_RUNNING == 0) "GoBe not running" else null,
            source = SOURCE
        )
        save(payload)
    }

    private fun parseHeartInfo(body: ByteArray) {
        if (body.size < 8) return
        val hr = body[4].toInt() and 0xFF
        save(HealthPayload(pulse = hr.takeIf { it > 0 }, source = SOURCE))
    }

    private fun parseStress(body: ByteArray) {
        if (body.size < 4) return
        val stress = body.leBuffer().int
        save(HealthPayload(stress = stressText(stress), source = SOURCE))
    }

    private fun parseFullSummary(body: ByteArray) {
        if (body.size < 64) return
        val buffer = body.leBuffer()
        buffer.position(16)
        val energyIn = buffer.float
        val energyOut = buffer.float
        buffer.position(56)
        val steps = buffer.int
        val balance = (energyIn - energyOut).roundToInt()
        save(HealthPayload(energy = balance, steps = steps.takeIf { it >= 0 }, source = SOURCE))
    }

    private fun parseShortSummaryEx(body: ByteArray) {
        if (body.size < 24) return
        val buffer = body.leBuffer()
        val totalRecords = buffer.int
        if (totalRecords > 0) {
            shortSummaryStartIdx = totalRecords - 1
        }
        val hr = body[18].toInt() and 0xFF
        val stress = body[19].toInt() and 0xFF
        val hydrationFlags = body[20].toInt() and 0xFF
        val waterStatus = when {
            hydrationFlags and API_HYDRO_FLAG_DATA_VALID == 0 -> null
            hydrationFlags and API_HYDRO_FLAG_NORMAL != 0 -> "Normal"
            else -> "Low"
        }
        save(HealthPayload(pulse = hr.takeIf { it > 0 }, stress = stressText(stress), waterStatus = waterStatus, source = SOURCE))
    }

    private fun save(payload: HealthPayload) {
        val context = appContext ?: return
        pendingPayload = pendingPayload.merge(payload)
        if (pendingPayload.hasAnyMetric()) {
            BleBridgeStatus.update(context, "GoBe BLE data saved: ${pendingPayload.encode()}")
            UdpSender.send(context, pendingPayload)
        }
    }

    private fun HealthPayload.merge(next: HealthPayload): HealthPayload {
        return HealthPayload(
            water = next.water ?: water,
            waterStatus = next.waterStatus ?: waterStatus,
            energy = next.energy ?: energy,
            steps = next.steps ?: steps,
            pulse = next.pulse ?: pulse,
            stress = next.stress ?: stress,
            battery = next.battery ?: battery,
            source = SOURCE
        )
    }

    private fun reconnectLater() {
        runCatching { gatt?.close() }
        gatt = null
        dataCharacteristic = null
        val context = appContext ?: return
        mainHandler.postDelayed({
            if (running.get()) startScan(context)
        }, 5_000)
    }

    private fun hasBlePermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun bluetoothAdapter(context: Context): BluetoothAdapter? {
        return (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private fun packet(command: Int, body: ByteArray = ByteArray(0)): ByteArray {
        val hex = buildString {
            append("<#")
            append(command.toHexByte())
            append(body.size.toHexByte())
            body.forEach { append((it.toInt() and 0xFF).toHexByte()) }
        }
        return hex.toByteArray(Charset.forName("US-ASCII"))
    }

    private fun recordSet(startIdx: Int, numRecs: Int): ByteArray {
        return ByteBuffer.allocate(6)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(startIdx)
            .putShort(numRecs.toShort())
            .array()
    }

    private fun Int.toHexByte(): String = String.format(Locale.US, "%02X", this and 0xFF)

    private fun ByteArray.leBuffer(): ByteBuffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)

    private fun ByteArray.toHexText(): String = joinToString("") { (it.toInt() and 0xFF).toHexByte() }

    private fun stressText(value: Int): String? {
        return when (value) {
            0 -> "No stress"
            1 -> "Low"
            2 -> "Moderate"
            3 -> "High"
            4 -> "Very high"
            else -> value.takeIf { it > 0 }?.toString()
        }
    }

    private object HealbePacket {
        fun parse(value: ByteArray): ParsedPacket? {
            val text = value.toString(Charset.forName("US-ASCII")).trim()
            if (!text.startsWith("<#") || text.length < 6) return null
            val command = text.substring(2, 4).toIntOrNull(16) ?: return null
            val length = text.substring(4, 6).toIntOrNull(16) ?: return null
            val hexBody = text.substring(6).take(length * 2)
            return ParsedPacket(command, hexBody.hexToBytes())
        }

        private fun String.hexToBytes(): ByteArray {
            if (length < 2) return ByteArray(0)
            return chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        }
    }

    private data class ParsedPacket(val command: Int, val body: ByteArray)

    private const val SOURCE = "GOBE_BLE"
    private const val POLL_INTERVAL_MS = 60_000L
    private const val TYPE_SENSOR_STATE = 0x01
    private const val TYPE_STRESS_LEVEL = 0x09
    private const val TYPE_FULL_SUMMARY = 0x11
    private const val TYPE_HEART_INFO = 0x12
    private const val TYPE_SHORT_SUMMARY_EX = 0x2A
    private const val FLG_SENSOR_IS_RUNNING = 0x00000008
    private const val API_HYDRO_FLAG_NORMAL = 0x01
    private const val API_HYDRO_FLAG_DATA_VALID = 0x04
    private val DATA_SERVICE_UUID = UUID.fromString("1bb21a49-4762-4939-9d50-868ddec2cb34")
    private val DATA_CHARACTERISTIC_UUID = UUID.fromString("34e6c128-c04e-4e98-98c6-3bf0b196c3fe")
    private val PIN_SERVICE_UUID = UUID.fromString("c7c49271-4a41-4223-aa5b-e9cfd76bd862")
    private val PIN_CHARACTERISTIC_UUID = UUID.fromString("f84b607f-af90-4505-b035-bf55e5c0f5c4")
    private val CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
