package com.galaxy.systemmonitor

import android.content.Context

object BuildConfigInfo {
    fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
    } catch (_: Exception) {
        "未知"
    }
}
