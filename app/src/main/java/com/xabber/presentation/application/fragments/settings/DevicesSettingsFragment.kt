package com.xabber.presentation.application.fragments.settings

import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentDevicesSettingsBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.fragments.DetailBaseFragment

class DevicesSettingsFragment : DetailBaseFragment(R.layout.fragment_devices_settings) {
    private val binding by viewBinding(FragmentDevicesSettingsBinding::bind)

}
