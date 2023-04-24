package com.xabber.presentation.application.fragments.calls

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.presentation.application.fragments.BaseFragment

class CallsFragment : BaseFragment(R.layout.fragment_calls) {
    private val binding by viewBinding(FragmentCallsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activeLinks()
    }

    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

}
