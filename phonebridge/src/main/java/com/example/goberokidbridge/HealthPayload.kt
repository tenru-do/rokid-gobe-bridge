package com.example.goberokidbridge

data class HealthPayload(
    val water: Int? = null,
    val waterStatus: String? = null,
    val energy: Int? = null,
    val steps: Int? = null,
    val pulse: Int? = null,
    val stress: String? = null,
    val battery: Int? = null,
    val source: String = "PHONE",
    val updatedAtMillis: Long = 0L
) {
    fun encode(): String {
        val parts = mutableListOf<String>()
        water?.let { parts += "water=$it" }
        waterStatus?.takeIf { it.isNotBlank() }?.let { parts += "waterStatus=${it.sanitize()}" }
        energy?.let { parts += "energy=$it" }
        steps?.let { parts += "steps=$it" }
        pulse?.let { parts += "pulse=$it" }
        stress?.takeIf { it.isNotBlank() }?.let { parts += "stress=${it.sanitize()}" }
        battery?.let { parts += "battery=$it" }
        parts += "source=${source.sanitize()}"
        if (updatedAtMillis > 0L) parts += "updated=$updatedAtMillis"
        return parts.joinToString(";")
    }

    fun hasAnyMetric(): Boolean {
        return water != null || !waterStatus.isNullOrBlank() || energy != null || steps != null || pulse != null || !stress.isNullOrBlank() || battery != null
    }

    private fun String.sanitize(): String = replace(";", " ").replace("&", " ").replace("=", " ")
}
