package com.example.goberokidbridge

object HealbeTextParser {
    fun parse(text: String): HealthPayload? {
        val normalizedText = text
            .replace(Regex("""(?<=\d)[,\u3001](?=\d{3}(\D|$))"""), "")

        val lines = normalizedText
            .replace(',', ' ')
            .replace('\u3001', ' ')
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val water = findWater(lines)
        val waterStatus = findWaterStatus(lines)
        val energy = findEnergy(lines)
        val steps = findSteps(lines)
        val pulse = findPulse(lines)
        val stress = findStress(lines.joinToString("\n"))
        val battery = findBattery(lines)

        if (water == null && waterStatus == null && energy == null && steps == null && pulse == null && stress == null && battery == null) {
            return null
        }

        return HealthPayload(
            water = water,
            waterStatus = waterStatus,
            energy = energy,
            steps = steps,
            pulse = pulse,
            stress = stress,
            battery = battery,
            source = "HEALBE"
        )
    }

    private fun findPercent(lines: List<String>, vararg hints: String): Int? {
        return lines.firstValueWithHint(Regex("""(-?\d{1,3})\s*%"""), *hints)
            ?.coerceIn(0, 100)
    }

    private fun findWater(lines: List<String>): Int? {
        return findPercent(lines, "water", "hydration", "\u6c34\u5206")
    }

    private fun findWaterStatus(lines: List<String>): String? {
        val index = lines.indexOfFirst { it == "\u6c34\u5206" || it.equals("water", ignoreCase = true) }
        if (index < 0) return null
        return lines.getOrNull(index - 1)
            ?.takeIf { it.isNotBlank() && !it.contains("%") && !Regex("""\d""").containsMatchIn(it) }
    }

    private fun findEnergy(lines: List<String>): Int? {
        val index = lines.indexOfFirst {
            it.equals("energy balance", ignoreCase = true) || it == "\u30a8\u30cd\u30eb\u30ae\u30fc\u30d0\u30e9\u30f3\u30b9"
        }
        if (index >= 0) {
            for (offset in 1..8) {
                val line = lines.getOrNull(index + offset).orEmpty()
                val next = lines.getOrNull(index + offset + 1).orEmpty()
                Regex("""([+-]\d{1,5})""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?.takeIf { next.equals("kcal", ignoreCase = true) || line.contains("kcal", ignoreCase = true) }
                    ?.let { return it }
            }
        }
        return lines.firstValueWithHint(Regex("""([+-]?\d{1,5})\s*(?:kcal|cal)?"""), "energy", "balance", "kcal")
    }

    private fun findSteps(lines: List<String>): Int? {
        lines.firstOrNull { it.contains("\u6b69") || it.contains("steps", ignoreCase = true) }
            ?.let { Regex("""(\d{1,7})\s*(?:\u6b69|steps)?""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?.let { return it }
        return null
    }

    private fun findPulse(lines: List<String>): Int? {
        val index = lines.indexOfFirst { it == "\u8108\u62cd" || it.equals("pulse", ignoreCase = true) || it.equals("heart", ignoreCase = true) }
        if (index >= 0) {
            for (offset in 1..8) {
                val line = lines.getOrNull(index + offset).orEmpty()
                val next = lines.getOrNull(index + offset + 1).orEmpty()
                val number = Regex("""(\d{1,3})""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (number != null && number in 30..220 && (next.contains("\u56de/\u5206") || next.contains("bpm", ignoreCase = true) || line.contains("bpm", ignoreCase = true))) return number
            }
        }
        return lines.firstValueWithHint(Regex("""(\d{1,3})\s*(?:bpm|\u56de/\u5206)"""), "pulse", "heart", "bpm", "\u8108\u62cd", "\u5fc3\u62cd")
    }

    private fun findBattery(lines: List<String>): Int? {
        val gobeIndex = lines.indexOfFirst { it.startsWith("GBU_") }
        if (gobeIndex >= 0) {
            for (offset in 1..8) {
                lines.getOrNull(gobeIndex + offset)
                    ?.let { Regex("""(\d{1,3})\s*%""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
                    ?.takeIf { it in 0..100 }
                    ?.let { return it }
            }
        }
        val connectedIndex = lines.indexOfFirst { it == "\u63a5\u7d9a\u6e08\u307f" || it.equals("connected", ignoreCase = true) }
        if (connectedIndex >= 0) {
            for (offset in 1..5) {
                lines.getOrNull(connectedIndex + offset)
                    ?.let { Regex("""(\d{1,3})\s*%""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
                    ?.takeIf { it in 0..100 }
                    ?.let { return it }
            }
        }
        return findPercent(lines, "gobe battery", "device battery", "band battery", "\u30d0\u30c3\u30c6\u30ea\u30fc", "\u96fb\u6c60")
    }

    private fun List<String>.firstValueWithHint(regex: Regex, vararg hints: String): Int? {
        forEachIndexed { index, line ->
            val block = listOfNotNull(
                getOrNull(index - 1),
                line,
                getOrNull(index + 1)
            ).joinToString(" ")
            val lower = block.lowercase()
            if (hints.none { lower.contains(it.lowercase()) }) return@forEachIndexed
            regex.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                return it
            }
        }
        return null
    }

    private fun findStress(text: String): String? {
        val lower = text.lowercase()
        return when {
            text.contains("\u30b9\u30c8\u30ec\u30b9\u3084\u3084\u9ad8\u3081") -> "\u3084\u3084\u9ad8\u3081"
            text.contains("\u73fe\u5728\u306e\u30ec\u30d9\u30eb") && text.contains("\u8efd\u3044") -> "\u8efd\u3044"
            text.contains("\u73fe\u5728\u306e\u30ec\u30d9\u30eb") && text.contains("\u9ad8\u3044") -> "\u9ad8\u3044"
            listOf("low", "\u4f4e").any { lower.contains(it) } && hasStressHint(lower) -> "Low"
            listOf("medium", "moderate", "\u4e2d").any { lower.contains(it) } && hasStressHint(lower) -> "Medium"
            listOf("high", "\u9ad8").any { lower.contains(it) } && hasStressHint(lower) -> "High"
            else -> null
        }
    }

    private fun hasStressHint(text: String): Boolean {
        return text.contains("stress") || text.contains("\u30b9\u30c8\u30ec\u30b9")
    }
}
