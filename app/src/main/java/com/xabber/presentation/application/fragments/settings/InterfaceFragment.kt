package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentInterfaceBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment

class InterfaceFragment : DetailBaseFragment(R.layout.fragment_interface) {
    private val binding by viewBinding(FragmentInterfaceBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.avatarSettings.setOnClickListener { navigator().showMaskSettings() }
        binding.chatSettings.setOnClickListener { navigator().showChatSettings() }
    }

}
