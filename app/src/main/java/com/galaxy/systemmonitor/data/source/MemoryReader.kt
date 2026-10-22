package com.galaxy.systemmonitor.data.source

import android.app.ActivityManager
import android.content.Context
import com.galaxy.systemmonitor.data.model.MemoryInfo
import com.galaxy.systemmonitor.data.model.Reading

/**
 * System-wide memory from ActivityManager.getMemoryInfo(). This is device RAM as seen
 * by the kernel, not this app's own heap.
 */
class MemoryReader(private val context: Context) {

    fun read(): Reading<MemoryInfo> = try {
        val am = context.getSystemService(ActivityManager::class.java)
            ?: return Reading.Unavailable("無法取得 ActivityManager")
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.totalMem <= 0 || info.availMem < 0 || info.availMem > info.totalMem) {
            Reading.Unavailable("系統回報的記憶體數值異常")
        } else {
            Reading.Ok(
                MemoryInfo(
                    totalBytes = info.totalMem,
                    availableBytes = info.availMem,
                    lowMemoryThresholdBytes = info.threshold.takeIf { it > 0 },
                    lowMemory = info.lowMemory,
                ),
            )
        }
    } catch (e: Exception) {
        Reading.Unavailable("讀取記憶體資訊失敗：${e.message ?: e.javaClass.simpleName}")
    }
}
