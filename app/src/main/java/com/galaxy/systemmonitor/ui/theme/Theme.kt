package com.galaxy.systemmonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.galaxy.systemmonitor.data.settings.ThemeMode
import com.galaxy.systemmonitor.domain.TempLevel

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6CC4B0),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF14403A),
    onPrimaryContainer = Color(0xFFB9EDE0),
    secondary = Color(0xFF8FB5C2),
    onSecondary = Color(0xFF0E3440),
    secondaryContainer = Color(0xFF1E3540),
    onSecondaryContainer = Color(0xFFCDE6EF),
    tertiary = Color(0xFFD8B25A),
    background = Color(0xFF0B0F10),
    onBackground = Color(0xFFE6ECEB),
    surface = Color(0xFF0B0F10),
    onSurface = Color(0xFFE6ECEB),
    surfaceVariant = Color(0xFF1B2326),
    onSurfaceVariant = Color(0xFF9DADAB),
    surfaceContainerLowest = Color(0xFF070A0B),
    surfaceContainerLow = Color(0xFF0F1416),
    surfaceContainer = Color(0xFF12181A),
    surfaceContainerHigh = Color(0xFF182023),
    surfaceContainerHighest = Color(0xFF1F282B),
    outline = Color(0xFF3A4A48),
    outlineVariant = Color(0xFF263130),
    error = Color(0xFFE57A6E),
)

private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080B0C),
    surfaceContainer = Color(0xFF0E1315),
    surfaceContainerHigh = Color(0xFF141B1D),
    surfaceContainerHighest = Color(0xFF1A2225),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D6E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDEBDF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A6872),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE6EF),
    onSecondaryContainer = Color(0xFF051F28),
    tertiary = Color(0xFF8A5F00),
    background = Color(0xFFF6F8F8),
    onBackground = Color(0xFF151D1C),
    surface = Color(0xFFF6F8F8),
    onSurface = Color(0xFF151D1C),
    surfaceVariant = Color(0xFFDCE4E3),
    onSurfaceVariant = Color(0xFF4F5E5C),
    surfaceContainerLow = Color(0xFFF0F3F3),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEAEFEE),
    surfaceContainerHighest = Color(0xFFE2E8E7),
    outline = Color(0xFF7A8987),
    outlineVariant = Color(0xFFC6D0CE),
    error = Color(0xFFB3261E),
)

@Immutable
data class StatusColors(val good: Color, val warn: Color, val danger: Color, val muted: Color) {
    fun forTemp(level: TempLevel): Color = when (level) {
        TempLevel.NORMAL -> good
        TempLevel.ELEVATED -> warn
        TempLevel.HIGH -> danger
        TempLevel.UNAVAILABLE -> muted
    }
}

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(Color(0xFF6CC4B0), Color(0xFFD8B25A), Color(0xFFE57A6E), Color(0xFF8A9A98))
}

@Composable
fun GalaxyTheme(themeMode: ThemeMode, amoledBlack: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme: ColorScheme = when {
        !dark -> LightColors
        amoledBlack -> AmoledColors
        else -> DarkColors
    }
    val status = if (dark) {
        StatusColors(Color(0xFF6CC4B0), Color(0xFFD8B25A), Color(0xFFE57A6E), Color(0xFF8A9A98))
    } else {
        StatusColors(Color(0xFF2E7D6E), Color(0xFF8A5F00), Color(0xFFB3261E), Color(0xFF6B7876))
    }
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
