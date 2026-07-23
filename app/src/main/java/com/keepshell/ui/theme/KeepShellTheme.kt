package com.keepshell.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Ink = Color(0xFF171C1D)
val Muted = Color(0xFF65706D)
val Soft = Color(0xFF8A9491)
val Line = Color(0xFFDCE2DF)
val LineStrong = Color(0xFFC9D1CD)
val Canvas = Color(0xFFF5F7F6)
val Surface = Color(0xFFFFFFFF)
val SurfaceSoft = Color(0xFFEEF2F0)
val Signal = Color(0xFF087F6B)
val SignalDark = Color(0xFF066352)
val SignalSoft = Color(0xFFE3F3EE)
val Online = Color(0xFF16A36E)
val Danger = Color(0xFFBD3F39)
val DangerSoft = Color(0xFFFCEBEA)
val Terminal = Color(0xFF101516)
val TerminalRaised = Color(0xFF171D1F)
val TerminalLine = Color(0xFF293133)
val TerminalText = Color(0xFFD9E2DF)
val TerminalMuted = Color(0xFF81908C)
val TerminalGreen = Color(0xFF68D6AE)

private val LightColors = lightColorScheme(
    primary = Signal,
    onPrimary = Color.White,
    primaryContainer = SignalSoft,
    onPrimaryContainer = SignalDark,
    secondary = Muted,
    onSecondary = Color.White,
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceSoft,
    onSurfaceVariant = Muted,
    outline = LineStrong,
    error = Danger,
    errorContainer = DangerSoft
)

private val DarkColors = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = Terminal,
    background = Terminal,
    onBackground = TerminalText,
    surface = TerminalRaised,
    onSurface = TerminalText,
    surfaceVariant = TerminalLine,
    onSurfaceVariant = TerminalMuted,
    outline = TerminalLine,
    error = Color(0xFFFF8D86)
)

@Composable
fun KeepShellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
