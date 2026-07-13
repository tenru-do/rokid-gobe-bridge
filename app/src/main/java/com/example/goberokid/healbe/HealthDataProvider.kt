package com.example.goberokid.healbe

interface HealthDataProvider {
    fun start(callback: Callback)
    fun stop()

    interface Callback {
        fun onConnecting(message: String)
        fun onSnapshot(snapshot: HealthSnapshot)
        fun onError(message: String, throwable: Throwable? = null)
    }
}
