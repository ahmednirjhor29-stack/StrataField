package com.stratafield.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Simple SharedPreferences-backed store for the user's dark mode preference.
 *
 * Exposes a single app-wide [StateFlow] so any composable can observe changes
 * and [StrataFieldTheme] can react immediately when the user flips the switch
 * in Settings.
 */
object ThemePreferences {
    private const val PREFS_NAME = "strata_prefs"
    private const val KEY_DARK_MODE = "dark_mode_enabled"

    private var darkModeFlow: MutableStateFlow<Boolean>? = null

    private fun flow(context: Context): MutableStateFlow<Boolean> {
        return darkModeFlow ?: synchronized(this) {
            darkModeFlow ?: run {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val initial = prefs.getBoolean(KEY_DARK_MODE, false)
                MutableStateFlow(initial).also { darkModeFlow = it }
            }
        }
    }

    /** Observe the current dark mode preference. Backed by SharedPreferences. */
    fun isDarkMode(context: Context): StateFlow<Boolean> = flow(context)

    /** Persist the new dark mode preference and notify all observers. */
    fun setDarkMode(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        flow(context).value = enabled
    }
}
