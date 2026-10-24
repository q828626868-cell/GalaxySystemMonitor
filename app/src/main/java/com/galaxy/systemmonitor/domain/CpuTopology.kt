package com.galaxy.systemmonitor.domain

/** Parses the Linux cpufreq sysfs values. Pure Kotlin so it can be unit tested on the JVM. */
object CpuTopology {

    data class Cluster(val cores: List<Int>, val maxMhz: Int?, val minMhz: Int?)

    /** "0-7" or "0-3,6" (from /sys/devices/system/cpu/possible) to core indices. */
    fun parseCoreList(text: String?): List<Int> =
        text?.trim()?.split(',')?.flatMap { part ->
            val bounds = part.trim().split('-').mapNotNull { it.trim().toIntOrNull() }
            when (bounds.size) {
                1 -> listOf(bounds[0])
                2 -> (bounds[0]..bounds[1]).toList()
                else -> emptyList()
            }
        }.orEmpty()

    /** cpufreq files are in kHz. Rejects zero, garbage and values above 10 GHz. */
    fun khzToMhz(text: String?): Int? =
        text?.trim()?.toLongOrNull()?.takeIf { it in 1..10_000_000 }?.let { (it / 1000).toInt() }

    /** Groups cores with the same max frequency, fastest cluster first. Cores with unknown max are grouped together. */
    fun clusters(maxMhz: Map<Int, Int?>, minMhz: Map<Int, Int?>): List<Cluster> =
        maxMhz.entries.groupBy { it.value }
            .map { (max, entries) ->
                val cores = entries.map { it.key }.sorted()
                Cluster(cores, max, cores.firstNotNullOfOrNull { minMhz[it] })
            }
            .sortedWith(compareByDescending<Cluster> { it.maxMhz ?: -1 }.thenBy { it.cores.first() })
}
