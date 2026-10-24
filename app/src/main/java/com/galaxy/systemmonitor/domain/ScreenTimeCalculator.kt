package com.galaxy.systemmonitor.domain

/**
 * Computes per-app foreground time from UsageStatsManager events, clipped to a range.
 * Event-based rather than UsageStats buckets, because buckets are aligned to whole days and
 * can include time outside the requested range.
 */
object ScreenTimeCalculator {

    enum class Kind { RESUMED, PAUSED, SCREEN_OFF }

    data class Event(val timestamp: Long, val packageName: String, val className: String?, val kind: Kind)

    // Values mirror android.app.usage.UsageEvents.Event constants.
    private const val ACTIVITY_RESUMED = 1
    private const val ACTIVITY_PAUSED = 2
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val DEVICE_SHUTDOWN = 26
    private const val ACTIVITY_STOPPED = 23

    /** Maps a raw UsageEvents type to the events this calculator cares about, or null to skip it. */
    fun kindOf(eventType: Int): Kind? = when (eventType) {
        ACTIVITY_RESUMED -> Kind.RESUMED
        ACTIVITY_PAUSED, ACTIVITY_STOPPED -> Kind.PAUSED
        SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN -> Kind.SCREEN_OFF
        else -> null
    }

    /**
     * @param events events from somewhat before [start] (so already-open apps are seen) up to [end].
     * @param end usually "now"; an app still in the foreground is counted up to here.
     * @return package name to foreground milliseconds within [start, end].
     */
    fun compute(events: List<Event>, start: Long, end: Long): Map<String, Long> {
        val open = HashMap<String, MutableSet<String>>()
        val openSince = HashMap<String, Long>()
        val totals = HashMap<String, Long>()

        fun close(pkg: String, at: Long) {
            val since = openSince.remove(pkg) ?: return
            open.remove(pkg)
            val from = maxOf(since, start)
            val to = minOf(at, end)
            if (to > from) totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
        }

        for (e in events.sortedBy { it.timestamp }) {
            if (e.timestamp > end) break
            when (e.kind) {
                Kind.RESUMED -> {
                    val set = open.getOrPut(e.packageName) { mutableSetOf() }
                    if (set.isEmpty()) openSince[e.packageName] = e.timestamp
                    set += e.className.orEmpty()
                }
                Kind.PAUSED -> {
                    val set = open[e.packageName] ?: continue
                    set -= e.className.orEmpty()
                    if (set.isEmpty()) close(e.packageName, e.timestamp)
                }
                // The screen turning off ends every session, even if a pause event was never logged.
                Kind.SCREEN_OFF -> openSince.keys.toList().forEach { close(it, e.timestamp) }
            }
        }
        openSince.keys.toList().forEach { close(it, end) }
        return totals
    }
}
