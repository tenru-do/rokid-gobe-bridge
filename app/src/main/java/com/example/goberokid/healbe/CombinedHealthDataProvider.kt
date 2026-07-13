package com.example.goberokid.healbe

class CombinedHealthDataProvider(
    private vararg val providers: HealthDataProvider
) : HealthDataProvider {
    override fun start(callback: HealthDataProvider.Callback) {
        providers.forEach { it.start(callback) }
    }

    override fun stop() {
        providers.forEach { it.stop() }
    }
}
