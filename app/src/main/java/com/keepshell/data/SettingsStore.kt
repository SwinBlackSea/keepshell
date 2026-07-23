package com.keepshell.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val enhancedKeepAlive: Boolean = false,
    val screenshotProtection: Boolean = true,
    val terminalFontSize: Int = 14,
    val scrollbackLines: Int = 10_000,
    val darkTerminal: Boolean = true
)

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(value: AppSettings) {
        preferences.edit()
            .putBoolean("enhanced_keep_alive", value.enhancedKeepAlive)
            .putBoolean("screenshot_protection", value.screenshotProtection)
            .putInt("terminal_font_size", value.terminalFontSize)
            .putInt("scrollback_lines", value.scrollbackLines)
            .putBoolean("dark_terminal", value.darkTerminal)
            .apply()
        _settings.value = value
    }

    private fun read() = AppSettings(
        enhancedKeepAlive = preferences.getBoolean("enhanced_keep_alive", false),
        screenshotProtection = preferences.getBoolean("screenshot_protection", true),
        terminalFontSize = preferences.getInt("terminal_font_size", 14).coerceIn(10, 24),
        scrollbackLines = preferences.getInt("scrollback_lines", 10_000).coerceIn(1_000, 20_000),
        darkTerminal = preferences.getBoolean("dark_terminal", true)
    )
}
