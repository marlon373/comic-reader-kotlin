package com.codecademy.comicreader.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.codecademy.comicreader.R
import androidx.core.content.edit

class SettingPreferenceFragment : PreferenceFragmentCompat() {

    companion object {
        private const val PREFS_NAME = "comicPrefs"
        private const val KEY_THEME = "isNightMode"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)

        setupNightModeSwitch()
        setupOtherPreferences() // optional placeholders
    }

    private fun setupNightModeSwitch() {
        val nightModePref: SwitchPreferenceCompat? = findPreference("night_mode")

        nightModePref?.let { pref ->
            // Read current value from SharedPreferences
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isNightMode = prefs.getBoolean(KEY_THEME, false)
            pref.isChecked = isNightMode

            pref.setOnPreferenceChangeListener { _, newValue ->
                val enableNight = newValue as Boolean

                // Save preference
                prefs.edit { putBoolean(KEY_THEME, enableNight) }

                // Apply theme
                AppCompatDelegate.setDefaultNightMode(
                    if (enableNight) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )

                requireActivity().recreate() // Refresh the UI
                true // Allow value to be saved
            }
        }
    }

    private fun setupOtherPreferences() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val resumePref: SwitchPreferenceCompat? = findPreference("open_last_file")
        resumePref?.let { pref ->
            pref.isChecked = prefs.getBoolean("open_last_file", false)
            pref.setOnPreferenceChangeListener { _, newValue ->
                prefs.edit { putBoolean("open_last_file", newValue as Boolean) }
                true
            }
        }

        val volumeScrollPref: SwitchPreferenceCompat? = findPreference("scroll_with_volume")
        volumeScrollPref?.let { pref ->
            pref.isChecked = prefs.getBoolean("scroll_with_volume", false)
            pref.setOnPreferenceChangeListener { _, newValue ->
                prefs.edit { putBoolean("scroll_with_volume", newValue as Boolean) }
                true
            }
        }
    }
}