package com.galaxy.systemmonitor.data.source

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.galaxy.systemmonitor.data.model.Reading
import com.galaxy.systemmonitor.data.model.StorageInfo
import com.galaxy.systemmonitor.data.model.StorageSource

/**
 * Internal storage totals. StorageStatsManager.getTotalBytes/getFreeBytes for the default
 * volume need no permission and match the numbers in system Settings; StatFs on /data is
 * the fallback. Per-app breakdowns would need Usage Access and are intentionally not used.
 */
class StorageReader(private val context: Context) {

    fun read(): Reading<StorageInfo> {
        val fromStats = try {
            val ssm = context.getSystemService(StorageStatsManager::class.java)
            if (ssm != null) {
                val total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
                val free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
                if (total > 0 && free in 0..total) StorageInfo(total, free, StorageSource.STORAGE_STATS) else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
        if (fromStats != null) return Reading.Ok(fromStats)

        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            if (total > 0 && free in 0..total) {
                Reading.Ok(StorageInfo(total, free, StorageSource.STAT_FS))
            } else {
                Reading.Unavailable("系統回報的儲存空間數值異常")
            }
        } catch (e: Exception) {
            Reading.Unavailable("讀取儲存空間失敗：${e.message ?: e.javaClass.simpleName}")
        }
    }
}
