package com.xabber.presentation.application.fragments.settings

import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentEncryptionSettingsBinding
import com.xabber.presentation.application.fragments.DetailBaseFragment

class EncryptionSettingsFragment : DetailBaseFragment(R.layout.fragment_encryption_settings) {
    private val binding by viewBinding(FragmentEncryptionSettingsBinding::bind)

}
