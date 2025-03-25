package com.xabber.presentation.application.fragments

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.IncomingCallsBinding
import com.xabber.databinding.MissedCallsBinding
import com.xabber.presentation.application.contract.navigator

class IncomingCallsFragment : BaseFragment(R.layout.incoming_calls) {
    private val binding by viewBinding(IncomingCallsBinding::bind)



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarActions()
    }
    private fun toolbarActions() {
        binding.callsToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.callsToolbar.setNavigationOnClickListener {
            navigator().closePanel()
        }
    }
}