package com.galaxy.systemmonitor

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Screen routes, shared by in-app navigation and widget click intents. */
object Destinations {
    const val DASHBOARD = "dashboard"
    const val BATTERY = "battery"
    const val TEMPERATURE = "temperature"
    const val MEMORY = "memory"
    const val STORAGE = "storage"
    const val NETWORK = "network"
    const val APP_USAGE = "app_usage"
    const val DEVICE = "device"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    val ALL = setOf(DASHBOARD, BATTERY, TEMPERATURE, MEMORY, STORAGE, NETWORK, APP_USAGE, DEVICE, HISTORY, SETTINGS)

    private const val EXTRA_DESTINATION = "com.galaxy.systemmonitor.extra.DESTINATION"
    private const val ACTION_OPEN = "com.galaxy.systemmonitor.action.OPEN"

    /** Explicit intent; the unique data URI keeps each widget click target's PendingIntent distinct. */
    fun intent(context: Context, route: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN
            data = Uri.parse("galaxymonitor://open/$route")
            putExtra(EXTRA_DESTINATION, route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    /** The activity is exported (launcher), so only whitelisted routes are accepted. */
    fun fromIntent(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_DESTINATION)?.takeIf { it in ALL }
}
