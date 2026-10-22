package com.galaxy.systemmonitor.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

object WorkScheduler {
    const val SAMPLING_WORK = "periodic_sampling"

    /**
     * Periodic sampling through WorkManager. Android decides the actual run time
     * (Doze, App Standby, Samsung battery management), so runs can be late or batched.
     */
    fun apply(context: Context, enabled: Boolean, intervalMinutes: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(SAMPLING_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<SamplingWorker>(
            intervalMinutes.toLong().coerceAtLeast(15),
            TimeUnit.MINUTES,
        )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(SAMPLING_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun observeState(context: Context): Flow<WorkInfo.State?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(SAMPLING_WORK)
            .map { infos -> infos.firstOrNull()?.state }
}
