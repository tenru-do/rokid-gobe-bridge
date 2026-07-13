package com.example.goberokid.healbe

object HealthPayloadParser {
    fun parse(payload: String, defaultSource: String = "PHONE"): HealthSnapshot {
        val values = payload
            .split(';', '&', '\n')
            .mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = part.substring(0, index).trim().lowercase()
                val value = part.substring(index + 1).trim()
                key to value
            }
            .toMap()

        return HealthSnapshot(
            waterBalancePercent = values["water"]?.toIntOrNull() ?: -1,
            waterStatus = values["waterstatus"]?.ifBlank { null },
            energyBalanceKcal = values["energy"]?.toIntOrNull() ?: 0,
            stepsToday = values["steps"]?.toIntOrNull() ?: 0,
            pulseBpm = values["pulse"]?.toIntOrNull() ?: 0,
            stressLevel = values["stress"]?.ifBlank { null } ?: "Unknown",
            batteryPercent = values["battery"]?.toIntOrNull() ?: -1,
            updatedAtMillis = System.currentTimeMillis(),
            source = values["source"]?.ifBlank { null } ?: defaultSource
        )
    }
}
