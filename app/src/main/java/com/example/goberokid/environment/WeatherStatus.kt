package com.example.goberokid.environment

data class WeatherStatus(
    val temperatureCelsius: Double? = null,
    val weatherCode: Int? = null,
    val label: String = "Weather --",
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val temperatureText: String
        get() = temperatureCelsius?.let { String.format("%.0fC", it) } ?: "--C"
}
