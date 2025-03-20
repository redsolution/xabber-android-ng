package com.xabber.presentation.application.fragments

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.IncomingCallsBinding
import com.xabber.databinding.MissedCallsBinding

class IncomingCallsFragment : BaseFragment(R.layout.incoming_calls) {
    private val binding by viewBinding(IncomingCallsBinding::bind)



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

}