package com.galaxy.systemmonitor

import android.app.Application
import android.content.Context

class GalaxyMonitorApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as GalaxyMonitorApp).container
