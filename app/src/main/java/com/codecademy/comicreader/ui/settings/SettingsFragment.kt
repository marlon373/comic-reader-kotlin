package com.codecademy.comicreader.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.codecademy.comicreader.R
import com.codecademy.comicreader.databinding.FragmentSettingsBinding
import com.codecademy.comicreader.ui.comic.ComicViewModel

class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ViewModelProvider(this)[ComicViewModel::class.java]

        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root = binding!!.root

        // Load SettingPreferenceFragment (Preferences)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingPreferenceFragment())
                .commit()
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}