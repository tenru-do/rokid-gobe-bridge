package com.example.goberokid.healbe

data class HealthSnapshot(
    val waterBalancePercent: Int,
    val waterStatus: String?,
    val energyBalanceKcal: Int,
    val stepsToday: Int,
    val pulseBpm: Int,
    val stressLevel: String,
    val batteryPercent: Int,
    val updatedAtMillis: Long,
    val source: String
)
