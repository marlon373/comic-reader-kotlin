package com.codecademy.comicreader.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.codecademy.comicreader.R
import androidx.core.content.edit
import com.codecademy.comicreader.theme.ThemeManager

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
                val prefs = requireContext().getSharedPreferences("comicPrefs", Context.MODE_PRIVATE)
                prefs.edit { putBoolean("isNightMode", enableNight) }

                ThemeManager.applyTheme(requireContext())
                requireActivity().recreate()
                true
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