package com.codecademy.comicreader.theme


import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

object ThemeManager {

    private const val PREFS_NAME = "comicPrefs"
    private const val KEY_THEME = "isNightMode"

    fun isNightMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_THEME, false)
    }

    /*
     * Apply theme safely.
     * - Always runs on main thread
     * - Avoids re-applying if mode is already active
     * - NO recreation here (handled externally)
     */
    fun applyTheme(context: Context) {
        val isNight = isNightMode(context)

        // Ensure theme change runs on main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                applyTheme(context)
            }
            return
        }

        val mode = if (isNight) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }

        // Avoid redundant re-application (prevents flicker)
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    /*
     * Toggle theme without causing immediate Activity recreation.
     * Settings screen or caller must handle recreation safely.
     */
    fun toggleTheme(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getBoolean(KEY_THEME, false)
        prefs.edit { putBoolean(KEY_THEME, !current) }

        // Apply new theme, but DO NOT recreate here.
        applyTheme(context)
    }
}


