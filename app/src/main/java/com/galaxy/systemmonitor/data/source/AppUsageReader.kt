package com.galaxy.systemmonitor.data.source

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Process
import androidx.core.graphics.drawable.toBitmap
import com.galaxy.systemmonitor.data.model.AppScreenTime
import com.galaxy.systemmonitor.data.model.AppTraffic
import com.galaxy.systemmonitor.data.model.Reading
import com.galaxy.systemmonitor.domain.ScreenTimeCalculator
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-app foreground time (UsageStatsManager) and network traffic (NetworkStatsManager).
 * Both need the "usage access" app-op, granted by the user in Settings or via ADB.
 * All calls block; run them off the main thread.
 */
class AppUsageReader(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, Bitmap>()

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun screenTime(start: Long, end: Long): Reading<List<AppScreenTime>> = try {
        val usm = context.getSystemService(UsageStatsManager::class.java)
            ?: return Reading.Unavailable("系統未提供使用情況服務")
        val raw = usm.queryEvents(start - LOOKBACK_MILLIS, end)
            ?: return Reading.Unavailable(NO_ACCESS)
        val events = ArrayList<ScreenTimeCalculator.Event>()
        val e = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(e)
            val kind = ScreenTimeCalculator.kindOf(e.eventType) ?: continue
            events += ScreenTimeCalculator.Event(e.timeStamp, e.packageName, e.className, kind)
        }
        val list = ScreenTimeCalculator.compute(events, start, end)
            .filterValues { it >= MIN_SCREEN_TIME_MILLIS }
            .map { (pkg, ms) -> AppScreenTime(pkg, label(pkg), ms) }
            .sortedByDescending { it.millis }
        Reading.Ok(list)
    } catch (e: SecurityException) {
        Reading.Unavailable(NO_ACCESS)
    } catch (e: Exception) {
        Reading.Unavailable("讀取使用時間失敗：${e.message ?: e.javaClass.simpleName}")
    }

    @Suppress("DEPRECATION") // TYPE_WIFI / TYPE_MOBILE are still the NetworkStatsManager network types.
    fun traffic(start: Long, end: Long): Reading<List<AppTraffic>> = try {
        val nsm = context.getSystemService(NetworkStatsManager::class.java)
            ?: return Reading.Unavailable("系統未提供網路統計服務")
        data class Acc(var wifi: Long = 0, var mobile: Long = 0, var background: Long = 0)
        val byUid = HashMap<Int, Acc>()
        for (type in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            // A null subscriber ID covers all SIMs on Android 10+.
            val stats = nsm.querySummary(type, null, start, end)
            try {
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val bytes = bucket.rxBytes + bucket.txBytes
                    if (bytes <= 0) continue
                    val acc = byUid.getOrPut(bucket.uid) { Acc() }
                    if (type == ConnectivityManager.TYPE_WIFI) acc.wifi += bytes else acc.mobile += bytes
                    if (bucket.state != NetworkStats.Bucket.STATE_FOREGROUND) acc.background += bytes
                }
            } finally {
                stats.close()
            }
        }
        val list = byUid.map { (uid, acc) ->
            val (label, pkg) = uidLabel(uid)
            AppTraffic(uid, label, pkg, acc.wifi, acc.mobile, acc.background)
        }.sortedByDescending { it.totalBytes }
        Reading.Ok(list)
    } catch (e: SecurityException) {
        Reading.Unavailable(NO_ACCESS)
    } catch (e: Exception) {
        Reading.Unavailable("讀取流量統計失敗：${e.message ?: e.javaClass.simpleName}")
    }

    fun icon(packageName: String): Bitmap? = icons[packageName] ?: try {
        pm.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).also { icons[packageName] = it }
    } catch (e: Exception) {
        null
    }

    private fun label(pkg: String): String = labels.getOrPut(pkg) {
        try {
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    private fun uidLabel(uid: Int): Pair<String, String?> = when (uid) {
        NetworkStats.Bucket.UID_REMOVED -> "已解除安裝的 App" to null
        NetworkStats.Bucket.UID_TETHERING -> "網路共用（熱點）" to null
        0 -> "系統核心（root）" to null
        Process.SYSTEM_UID -> "Android 系統" to "android"
        else -> {
            val pkgs = pm.getPackagesForUid(uid)?.sorted().orEmpty()
            when (pkgs.size) {
                0 -> "UID $uid" to null
                1 -> label(pkgs[0]) to pkgs[0]
                // Shared UID: several packages share one traffic counter and cannot be split.
                else -> "${label(pkgs[0])} 等 ${pkgs.size} 個套件" to pkgs[0]
            }
        }
    }

    private companion object {
        /** Look back so apps already in the foreground when the range starts are seen. */
        const val LOOKBACK_MILLIS = 6 * 3_600_000L
        const val MIN_SCREEN_TIME_MILLIS = 1_000L
        const val ICON_PX = 96
        const val NO_ACCESS = "尚未授予「使用情況存取權」"
    }
}
