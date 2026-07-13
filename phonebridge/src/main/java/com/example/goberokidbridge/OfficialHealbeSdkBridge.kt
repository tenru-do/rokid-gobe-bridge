package com.example.goberokidbridge

import android.content.Context
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object OfficialHealbeSdkBridge {
    private const val SDK_CLASS = "com.healbe.healbesdk.business_api.HealbeSdk"
    private val started = AtomicBoolean(false)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        Thread({
            runCatching {
                val sdk = initializeSdk(appContext) ?: return@Thread
                val healthData = sdk.readMember("HEALTH_DATA") ?: return@Thread
                subscribeHealthData(appContext, healthData)
                sdk.readMember("GOBE")?.let { subscribeGoBe(appContext, it) }
            }.onFailure {
                started.set(false)
            }
        }, "official-healbe-sdk-bridge").apply {
            isDaemon = true
            start()
        }
    }

    private fun initializeSdk(context: Context): Any? {
        val sdkClass = runCatching { Class.forName(SDK_CLASS) }.getOrNull() ?: return null
        val sdkAccess = sdkClass.kotlinObject()
            ?: sdkClass.noArgInstance()
            ?: sdkClass.staticCompanion()
            ?: return null

        sdkAccess.invokeNoThrow("init", context)?.awaitIfReactive()
        return sdkAccess.invokeNoThrow("get")?.awaitIfReactive() ?: sdkAccess
    }

    private fun subscribeHealthData(context: Context, healthData: Any) {
        healthData.subscribeAny("hydrationState") { value ->
            save(context, HealthPayload(waterStatus = value.displayName(), source = "HEALBE_SDK"))
        }
        healthData.subscribeAny("currentStressState") { value ->
            save(context, HealthPayload(stress = value.displayName(), source = "HEALBE_SDK"))
        }
        healthData.subscribeAny("currentStressLevel") { value ->
            save(context, HealthPayload(stress = value.displayName(), source = "HEALBE_SDK"))
        }
        healthData.subscribeAny("currentHeartRate") { value ->
            save(context, HealthPayload(pulse = value.findFirstInt("heart", "rate", "pulse", "bpm"), source = "HEALBE_SDK"))
        }
        healthData.subscribeAny("lastHeartData") { value ->
            save(context, HealthPayload(pulse = value.findFirstInt("heart", "rate", "pulse", "bpm"), source = "HEALBE_SDK"))
        }

        healthData.invokeCandidate(listOf("energySummary", "getEnergySummary", "observeEnergySummary"), 0)
            ?.subscribeReactive { value ->
                save(context, HealthPayload(
                    energy = value.findEnergyBalance(),
                    steps = value.findFirstInt("step"),
                    source = "HEALBE_SDK"
                ))
            }
    }

    private fun subscribeGoBe(context: Context, goBe: Any) {
        goBe.invokeNoThrow("connect")?.awaitIfReactive()
        goBe.invokeNoThrow("sensorId")?.awaitIfReactive()
        goBe.subscribeAny("batteryLevel") { value ->
            save(context, HealthPayload(battery = value.asInt(), source = "HEALBE_SDK"))
        }
    }

    private fun save(context: Context, payload: HealthPayload) {
        if (!payload.hasAnyMetric()) return
        PayloadStore(context).save(payload)
        UdpSender.send(context, payload)
    }

    private fun Any.subscribeAny(memberName: String, onValue: (Any) -> Unit) {
        val value = readMember(memberName) ?: return
        value.subscribeReactive(onValue)
    }

    private fun Any.subscribeReactive(onValue: (Any) -> Unit) {
        val reactive = this
        val subscribeMethods = reactive.javaClass.methods.filter { it.name == "subscribe" && it.parameterTypes.size == 1 }
        for (method in subscribeMethods) {
            val consumerType = method.parameterTypes.first()
            if (!consumerType.isInterface) continue
            val proxy = Proxy.newProxyInstance(
                consumerType.classLoader,
                arrayOf(consumerType)
            ) { _, invoked, args ->
                if (invoked.name == "accept" && !args.isNullOrEmpty() && args[0] != null) {
                    onValue(args[0])
                }
                null
            }
            if (runCatching { method.invoke(reactive, proxy) }.isSuccess) return
        }

        runCatching { reactive.invokeNoThrow("blockingGet") ?: reactive.invokeNoThrow("blockingFirst") }
            .getOrNull()
            ?.let(onValue)
    }

    private fun Any.awaitIfReactive(): Any? {
        invokeNoThrow("blockingAwait")?.let { return this }
        invokeNoThrow("blockingGet")?.let { return it }
        invokeNoThrow("blockingFirst")?.let { return it }
        return this
    }

    private fun Class<*>.kotlinObject(): Any? {
        return runCatching { getField("INSTANCE").get(null) }.getOrNull()
    }

    private fun Class<*>.staticCompanion(): Any? {
        return runCatching { getField("Companion").get(null) }.getOrNull()
    }

    private fun Class<*>.noArgInstance(): Any? {
        return runCatching { getDeclaredConstructor().apply { isAccessible = true }.newInstance() }.getOrNull()
    }

    private fun Any.readMember(name: String): Any? {
        val getter = "get" + name.replaceFirstChar { it.titlecase(Locale.US) }
        invokeNoThrow(getter)?.let { return it.awaitIfReactive() }
        invokeNoThrow(name)?.let { return it.awaitIfReactive() }
        return runCatching {
            javaClass.getField(name).get(this)
        }.getOrNull()
    }

    private fun Any.invokeNoThrow(name: String, vararg args: Any): Any? {
        return runCatching {
            val method = javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.size == args.size }
                ?: return null
            method.invoke(this, *args)
        }.getOrNull()
    }

    private fun Any.invokeCandidate(names: List<String>, vararg args: Any): Any? {
        for (name in names) {
            invokeNoThrow(name, *args)?.let { return it }
            readMember(name)?.let { return it }
        }
        val method = javaClass.methods.firstOrNull { method ->
            names.any { method.name.contains(it, ignoreCase = true) } &&
                method.parameterTypes.size == args.size
        }
        return runCatching { method?.invoke(this, *args) }.getOrNull()
    }

    private fun Any.displayName(): String {
        val enumName = runCatching { javaClass.getMethod("name").invoke(this) as? String }.getOrNull()
        return enumName ?: toString()
    }

    private fun Any.asInt(): Int? {
        return when (this) {
            is Number -> toInt()
            is String -> toIntOrNull()
            else -> findFirstInt("value", "level", "percent", "battery")
        }
    }

    private fun Any.findEnergyBalance(): Int? {
        val consumed = findFirstInt("consumed", "intake", "food")
        val burned = findFirstInt("burned", "spent", "total")
        if (consumed != null && burned != null) return consumed - burned
        return findFirstInt("balance", "energy", "kcal")
    }

    private fun Any.findFirstInt(vararg hints: String): Int? {
        if (this is Number) return toInt()
        val lowerHints = hints.map { it.lowercase(Locale.US) }
        return javaClass.methods
            .asSequence()
            .filter { it.parameterTypes.isEmpty() && (it.name.startsWith("get") || it.name.startsWith("is")) }
            .filter { method -> lowerHints.any { method.name.lowercase(Locale.US).contains(it) } }
            .mapNotNull { method -> method.safeInvokeInt(this) }
            .firstOrNull()
    }

    private fun Method.safeInvokeInt(target: Any): Int? {
        val value = runCatching { invoke(target) }.getOrNull()
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }
}
