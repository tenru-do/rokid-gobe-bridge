package com.example.goberokidbridge

object HealbeSdkAvailability {
    private const val SDK_CLASS = "com.healbe.healbesdk.business_api.HealbeSdk"

    fun isBundled(): Boolean {
        return runCatching {
            Class.forName(SDK_CLASS)
        }.isSuccess
    }

    fun statusText(): String {
        return if (isBundled()) {
            "Official HEALBE SDK detected. SDK data source can be enabled."
        } else {
            "Official HEALBE SDK is not bundled. Using HEALBE app screen capture fallback."
        }
    }
}
