package com.galaxy.systemmonitor.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import com.galaxy.systemmonitor.appContainer
import com.galaxy.systemmonitor.data.db.SampleSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Widget state that reloads whenever [WidgetUpdater.version] changes. */
@Composable
fun rememberWidgetState(context: Context, initial: WidgetState): WidgetState {
    val version by WidgetUpdater.version.collectAsState()
    var state by remember { mutableStateOf(initial) }
    var loadedVersion by remember { mutableStateOf(WidgetUpdater.version.value) }
    LaunchedEffect(version) {
        if (version != loadedVersion) {
            state = WidgetStateLoader.load(context)
            loadedVersion = version
        }
    }
    return state
}

object WidgetUpdater {
    private val _version = MutableStateFlow(0L)

    /**
     * Bumped on every update request. A Glance session that is still alive only
     * recomposes on updateAll() (provideGlance is not called again), so widget content
     * observes this to reload its data.
     */
    val version: StateFlow<Long> = _version.asStateFlow()

    /** Re-renders placed widgets; does nothing when none are on the home screen. */
    suspend fun updateAll(context: Context) {
        _version.value = System.nanoTime()
        try {
            val manager = GlanceAppWidgetManager(context)
            if (manager.getGlanceIds(SmallWidget::class.java).isNotEmpty()) SmallWidget().updateAll(context)
            if (manager.getGlanceIds(LargeWidget::class.java).isNotEmpty()) LargeWidget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed widget refresh must never crash sampling or the app; the
            // widget keeps showing its previous content with its own timestamp.
        }
    }
}

/** Manual refresh button on the widgets. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.repository.captureAndStore(SampleSource.MANUAL, minSpacingMillis = 0)
        WidgetUpdater.updateAll(context)
    }
}
