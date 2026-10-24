package com.galaxy.systemmonitor.domain

/**
 * Best-effort lookup tables. Android exposes model and SoC codes but no marketing names,
 * so unknown codes fall back to the raw code instead of a guess.
 */
object DeviceNames {

    /** Samsung model code prefix (region suffix stripped) to marketing name. */
    private val samsungModels = mapOf(
        "SM-S938" to "Galaxy S25 Ultra",
        "SM-S937" to "Galaxy S25 Edge",
        "SM-S936" to "Galaxy S25+",
        "SM-S931" to "Galaxy S25",
        "SM-S928" to "Galaxy S24 Ultra",
        "SM-S926" to "Galaxy S24+",
        "SM-S921" to "Galaxy S24",
        "SM-S918" to "Galaxy S23 Ultra",
        "SM-S916" to "Galaxy S23+",
        "SM-S911" to "Galaxy S23",
        "SM-F966" to "Galaxy Z Fold7",
        "SM-F766" to "Galaxy Z Flip7",
        "SM-F956" to "Galaxy Z Fold6",
        "SM-F741" to "Galaxy Z Flip6",
        "SM-A566" to "Galaxy A56",
        "SM-A366" to "Galaxy A36",
        "SM-A556" to "Galaxy A55",
        "SM-A356" to "Galaxy A35",
        "SM-A546" to "Galaxy A54",
        "SM-A346" to "Galaxy A34",
        "SM-M146" to "Galaxy M14 5G",
    )

    /** Build.SOC_MODEL to chip name. */
    private val socs = mapOf(
        "SM8850" to "Snapdragon 8 Elite Gen 5",
        "SM8750" to "Snapdragon 8 Elite",
        "SM8650" to "Snapdragon 8 Gen 3",
        "SM8550" to "Snapdragon 8 Gen 2",
        "SM8475" to "Snapdragon 8+ Gen 1",
        "SM8450" to "Snapdragon 8 Gen 1",
        "SM8350" to "Snapdragon 888",
        "SM7675" to "Snapdragon 7+ Gen 3",
        "SM7550" to "Snapdragon 7 Gen 3",
        "S5E9955" to "Exynos 2500",
        "S5E9945" to "Exynos 2400",
        "S5E9925" to "Exynos 2200",
        "S5E8855" to "Exynos 1580",
        "S5E8845" to "Exynos 1480",
        "S5E8835" to "Exynos 1380",
        "S5E8535" to "Exynos 1330",
        "MT6991" to "Dimensity 9400",
        "MT6989" to "Dimensity 9300",
    )

    /** Samsung ships "for Galaxy" variants of recent Snapdragon flagships. */
    private val forGalaxy = setOf("SM8750", "SM8650", "SM8550")

    fun marketName(manufacturer: String, model: String): String? {
        if (!manufacturer.equals("samsung", ignoreCase = true)) return null
        val key = model.uppercase().replace('_', '-')
        return samsungModels.entries.firstOrNull { key.startsWith(it.key) }?.value
    }

    fun socName(manufacturer: String, socModel: String): String? {
        val key = socModel.uppercase().substringBefore('-')
        val name = socs[key] ?: return null
        return if (key in forGalaxy && manufacturer.equals("samsung", ignoreCase = true)) "$name for Galaxy" else name
    }

    /** One UI property value such as 70000 or 80100 to "7.0" / "8.1". */
    fun oneUiVersion(raw: String?): String? {
        val v = raw?.trim()?.toIntOrNull()?.takeIf { it >= 10000 } ?: return null
        return "${v / 10000}.${(v % 10000) / 100}"
    }
}
