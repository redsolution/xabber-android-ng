package com.xabber.presentation.application.fragments

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.MissedCallsBinding
import com.xabber.databinding.OutgoingCallsBinding

class OutgoingCallsFragment : BaseFragment(R.layout.outgoing_calls) {
    private val binding by viewBinding(OutgoingCallsBinding::bind)



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

}