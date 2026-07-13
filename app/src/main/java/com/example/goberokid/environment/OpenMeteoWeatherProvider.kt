package com.example.goberokid.environment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

class OpenMeteoWeatherProvider(private val context: Context) {
    interface Callback {
        fun onWeather(status: WeatherStatus)
    }

    @Volatile
    private var running = false

    fun start(callback: Callback) {
        if (running) return
        running = true
        Thread {
            while (running) {
                callback.onWeather(fetchWeather())
                Thread.sleep(WEATHER_REFRESH_MS)
            }
        }.start()
    }

    fun stop() {
        running = false
    }

    private fun fetchWeather(): WeatherStatus {
        return runCatching {
            val location = bestLocation()
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${location.latitude}" +
                    "&longitude=${location.longitude}" +
                    "&current=temperature_2m,weather_code" +
                    "&timezone=auto"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }
            connection.inputStream.use { stream ->
                val text = stream.readBytes().toString(Charset.forName("UTF-8"))
                val current = JSONObject(text).getJSONObject("current")
                val temp = current.optDouble("temperature_2m", Double.NaN)
                val code = current.optInt("weather_code", -1)
                WeatherStatus(
                    temperatureCelsius = temp.takeUnless { it.isNaN() },
                    weatherCode = code.takeIf { it >= 0 },
                    label = weatherLabel(code)
                )
            }
        }.getOrElse {
            WeatherStatus(label = "Weather offline")
        }
    }

    private fun bestLocation(): Coordinates {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return TOKYO
        if (!hasLocationPermission()) return TOKYO

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        val last = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull(Location::getTime)

        return last?.let { Coordinates(it.latitude, it.longitude) } ?: TOKYO
    }

    private fun hasLocationPermission(): Boolean {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        return fine || coarse
    }

    private fun weatherLabel(code: Int): String {
        return when (code) {
            0 -> "Clear"
            1, 2 -> "Partly cloudy"
            3 -> "Cloudy"
            45, 48 -> "Fog"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 65, 66, 67 -> "Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Showers"
            85, 86 -> "Snow showers"
            95, 96, 99 -> "Thunder"
            else -> "Weather"
        }
    }

    private data class Coordinates(val latitude: Double, val longitude: Double)

    companion object {
        private val TOKYO = Coordinates(35.6812, 139.7671)
        private const val WEATHER_REFRESH_MS = 10 * 60 * 1000L
    }
}
