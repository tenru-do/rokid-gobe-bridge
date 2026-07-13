package com.example.goberokid.healbe

import android.content.Context

/**
 * Replace the TODOs in this class with the exact types and calls from the
 * HEALBE Android SDK package used by your project.
 */
class HealbeSdkProvider(
    private val context: Context
) : HealthDataProvider {
    private var callback: HealthDataProvider.Callback? = null

    override fun start(callback: HealthDataProvider.Callback) {
        this.callback = callback
        callback.onConnecting("HEALBE SDK adapter is present but not wired.")

        // TODO:
        // 1. Add the official HEALBE SDK dependency or local AAR/JAR.
        // 2. Import the SDK client and HealthData model classes.
        // 3. Connect to GoBe U using the SDK's BLE connection callback.
        // 4. Subscribe to health updates or request the latest HealthData.
        // 5. Map SDK values into HealthSnapshot and call callback.onSnapshot(...).
        // 6. Include the device battery level and current timestamp when available.
        //
        // Keep this adapter as the only file that knows HEALBE SDK symbols.
        callback.onError(
            "Real HEALBE SDK calls need the official SDK dependency and API names. " +
                "Use SimulatedHealbeProvider until those are added."
        )
    }

    override fun stop() {
        // TODO: Disconnect/unsubscribe using the HEALBE SDK.
        callback = null
    }
}
