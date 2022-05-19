package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSettingsBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment

class SettingsFragment : BaseFragment(R.layout.fragment_settings) {
    private val binding by viewBinding(FragmentSettingsBinding::bind)
    private var isDarkMode = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.imMode.setOnClickListener {
            isDarkMode = !isDarkMode
            if (isDarkMode) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
           else  AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        }

        binding.linearChat.setOnClickListener {
            navigator().showChatSettings()
        }
    }


}