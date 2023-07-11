package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentInterfaceBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.manage.MaskManager

class InterfaceFragment : DetailBaseFragment(R.layout.fragment_interface) {
    private val binding by viewBinding(FragmentInterfaceBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvSubtitleAvatar.text = when (MaskManager.mask) {
            R.drawable.circle -> resources.getString(R.string.circle)
            R.drawable.ic_mask_hexagon -> resources.getString(R.string.hexagon)
            R.drawable.ic_mask_octagon -> resources.getString(R.string.octagon)
            R.drawable.ic_mask_pentagon -> resources.getString(R.string.pentagon)
            R.drawable.ic_mask_rounded -> resources.getString(R.string.rounded)
            R.drawable.ic_mask_squircle -> resources.getString(R.string.squircle)
            R.drawable.ic_mask_star -> resources.getString(R.string.star)
            else -> ""
        }
        binding.avatarSettings.setOnClickListener { navigator().showMaskSettings() }
        binding.chatSettings.setOnClickListener { navigator().showChatSettings() }
    }

}
