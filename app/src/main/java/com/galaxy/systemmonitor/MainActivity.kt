package com.galaxy.systemmonitor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.galaxy.systemmonitor.ui.AppNavigation
import com.galaxy.systemmonitor.ui.MonitorViewModel
import com.galaxy.systemmonitor.ui.theme.GalaxyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MonitorViewModel by viewModels { MonitorViewModel.Factory }
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingRoute.value = Destinations.fromIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val route by pendingRoute.collectAsStateWithLifecycle()
            GalaxyTheme(settings.themeMode, settings.amoledBlack) {
                AppNavigation(viewModel, route, onRouteHandled = { pendingRoute.value = null })
            }
        }

        // Live updates only while the UI is visible; all of this stops in onStop.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refresh(manual = false)
                launch { viewModel.batteryChanges().collect { viewModel.onBatteryChanged(it) } }
                launch {
                    while (true) {
                        delay(LIVE_REFRESH_MILLIS)
                        viewModel.refreshLiveOnly()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Destinations.fromIntent(intent)?.let { pendingRoute.value = it }
    }

    private companion object {
        const val LIVE_REFRESH_MILLIS = 30_000L
    }
}
