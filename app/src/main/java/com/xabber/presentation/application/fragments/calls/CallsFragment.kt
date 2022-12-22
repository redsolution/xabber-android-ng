package com.xabber.presentation.application.fragments.calls

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.presentation.application.BaseFragment
import com.xabber.presentation.application.activity.AccountManager

class CallsFragment : BaseFragment(R.layout.fragment_calls) {
    private val binding by viewBinding(FragmentCallsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAvatarWithMask()
        activeLinks()
    }

    private fun loadAvatarWithMask() {
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(requireContext()).load(AccountManager.avatar)
            .apply(RequestOptions.bitmapTransform(multiTransformation))
            .into(binding.imAvatar)
    }

    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

}
