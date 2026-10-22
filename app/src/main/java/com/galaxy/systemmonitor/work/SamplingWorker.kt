package com.galaxy.systemmonitor.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.galaxy.systemmonitor.appContainer
import com.galaxy.systemmonitor.data.StoreResult
import com.galaxy.systemmonitor.data.db.SampleSource
import com.galaxy.systemmonitor.widget.WidgetUpdater

/** One short sampling pass: read, store, prune old rows, refresh widgets. Never loops. */
class SamplingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val outcome = container.repository.captureAndStore(SampleSource.SCHEDULED, minSpacingMillis = 0)
        container.repository.prune(container.settings.current().retentionDays)
        WidgetUpdater.updateAll(applicationContext)
        return when {
            outcome.store != StoreResult.FAILED -> Result.success()
            runAttemptCount < 2 -> Result.retry()
            else -> Result.success() // Give up for this period; the next period will try again.
        }
    }
}
