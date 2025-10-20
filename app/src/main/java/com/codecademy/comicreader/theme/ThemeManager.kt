package com.codecademy.comicreader.theme

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

object ThemeManager {
    private const val PREFS_NAME = "comicPrefs"
    private const val KEY_THEME = "isNightMode"

    fun isNightMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_THEME, false)
    }

    fun applyTheme(context: Context) {
        val isNight = isNightMode(context)
        AppCompatDelegate.setDefaultNightMode(
            if (isNight) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun toggleTheme(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isNight = prefs.getBoolean(KEY_THEME, false)
        prefs.edit { putBoolean(KEY_THEME, !isNight) }

        AppCompatDelegate.setDefaultNightMode(
            if (isNight) AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES
        )

        activity.recreate()
    }
}

