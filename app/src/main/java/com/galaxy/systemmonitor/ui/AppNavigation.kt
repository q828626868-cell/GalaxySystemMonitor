package com.galaxy.systemmonitor.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.galaxy.systemmonitor.Destinations
import com.galaxy.systemmonitor.ui.screens.BatteryScreen
import com.galaxy.systemmonitor.ui.screens.DashboardScreen
import com.galaxy.systemmonitor.ui.screens.HistoryScreen
import com.galaxy.systemmonitor.ui.screens.MemoryScreen
import com.galaxy.systemmonitor.ui.screens.NetworkScreen
import com.galaxy.systemmonitor.ui.screens.SettingsScreen
import com.galaxy.systemmonitor.ui.screens.StorageScreen
import com.galaxy.systemmonitor.ui.screens.TemperatureScreen

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Destinations.DASHBOARD, "儀表板", Icons.Filled.Home),
    TopLevel(Destinations.HISTORY, "歷史", Icons.AutoMirrored.Filled.List),
    TopLevel(Destinations.SETTINGS, "設定", Icons.Filled.Settings),
)

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavigation(vm: MonitorViewModel, pendingRoute: String?, onRouteHandled: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // Deep link from a widget tile: open the requested screen on top of the dashboard.
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        if (route == Destinations.DASHBOARD) {
            nav.popBackStack(Destinations.DASHBOARD, inclusive = false)
        } else {
            nav.navigate(route) {
                popUpTo(Destinations.DASHBOARD)
                launchSingleTop = true
            }
        }
        onRouteHandled()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (topLevel.any { it.route == current }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = current == item.route,
                            onClick = { nav.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Destinations.DASHBOARD,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            val back: () -> Unit = { nav.popBackStack() }
            composable(Destinations.DASHBOARD) { DashboardScreen(vm, onOpen = { nav.navigate(it) }) }
            composable(Destinations.BATTERY) { BatteryScreen(vm, back) }
            composable(Destinations.TEMPERATURE) { TemperatureScreen(vm, back) }
            composable(Destinations.MEMORY) { MemoryScreen(vm, back) }
            composable(Destinations.STORAGE) { StorageScreen(vm, back) }
            composable(Destinations.NETWORK) { NetworkScreen(vm, back) }
            composable(Destinations.HISTORY) { HistoryScreen(vm, onBack = null) }
            composable(Destinations.SETTINGS) { SettingsScreen(vm, onBack = null) }
        }
    }
}
