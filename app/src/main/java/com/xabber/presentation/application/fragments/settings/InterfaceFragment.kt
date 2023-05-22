package com.xabber.presentation.application.fragments.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentInterfaceBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.utils.MaskManager
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.MessageChanger

class InterfaceFragment : DetailBaseFragment(R.layout.fragment_interface) {
    private val binding by viewBinding(FragmentInterfaceBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvMask.setOnClickListener { navigator().showMaskSettings() }
        binding.tvChatSettings.setOnClickListener { navigator().showChatSettings() }
    }

}