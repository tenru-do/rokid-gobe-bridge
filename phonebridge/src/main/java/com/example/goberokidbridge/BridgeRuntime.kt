package com.example.goberokidbridge

import android.content.Context
import android.content.Intent
import android.os.Build

object BridgeRuntime {
    fun start(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, BridgeForegroundService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }.onFailure {
            BridgeRequestServer.start(appContext)
        }
    }
}
