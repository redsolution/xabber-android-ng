package com.xabber.presentation.application.fragments.calls

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.utils.mask.MaskPrepare

class CallsFragment : BaseFragment(R.layout.fragment_calls) {
    private val binding by viewBinding(FragmentCallsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAvatarWithMask()
        activeLinks()
    }

    private fun loadAvatarWithMask() {
        val maskedDrawable =
            MaskPrepare.getDrawableMask(resources, R.drawable.img, UiChanger.getMask().size32)
        binding.imAvatar.setImageDrawable(maskedDrawable)
    }

    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

}
