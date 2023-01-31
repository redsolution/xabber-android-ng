package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentProfileSettingsBinding
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.utils.dp

class ProfileSettingsFragment : DetailBaseFragment(R.layout.fragment_profile_settings) {
    private val binding by viewBinding(FragmentProfileSettingsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
binding.toolbar.setNavigationIcon(null)
        binding.left.setOnClickListener { navigator().goBack() }
    }

}
