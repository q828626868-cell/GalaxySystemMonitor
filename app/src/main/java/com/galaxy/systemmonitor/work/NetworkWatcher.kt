package com.galaxy.systemmonitor.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.galaxy.systemmonitor.data.MonitorRepository
import com.galaxy.systemmonitor.data.db.NetworkDetection
import com.galaxy.systemmonitor.data.model.valueOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Records default-network changes while this app's process is alive. It relies on system
 * callbacks only: no polling, no wake-ups and no network traffic. Changes that happen while
 * the process is not running are noticed at the next sample instead.
 */
class NetworkWatcher(
    private val context: Context,
    private val repository: MonitorRepository,
    private val scope: CoroutineScope,
) {
    private val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { trigger.tryEmit(Unit) }
                override fun onLost(network: Network) { trigger.tryEmit(Unit) }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trigger.tryEmit(Unit) }
            })
        } catch (_: Exception) {
            return
        }
        started = true
        scope.launch {
            // Capability callbacks can be chatty; settle before reading.
            trigger.debounce(1_500).collect {
                val reading = repository.readNetwork()
                repository.updateLiveNetwork(reading)
                reading.valueOrNull()?.let { repository.recordNetworkIfChanged(it, NetworkDetection.CALLBACK) }
            }
        }
    }
}
