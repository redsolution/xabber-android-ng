package com.xabber.presentation.application.fragments

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCallsFiltersBinding
import com.xabber.databinding.MissedCallsBinding

class MissedCallsFragment : BaseFragment(R.layout.missed_calls) {
    private val binding by viewBinding(MissedCallsBinding::bind)



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

}