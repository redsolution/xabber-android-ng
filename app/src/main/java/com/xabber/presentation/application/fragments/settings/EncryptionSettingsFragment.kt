package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentEncryptionSettingsBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment

class EncryptionSettingsFragment : DetailBaseFragment(R.layout.fragment_encryption_settings) {
    private val binding by viewBinding(FragmentEncryptionSettingsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

}
