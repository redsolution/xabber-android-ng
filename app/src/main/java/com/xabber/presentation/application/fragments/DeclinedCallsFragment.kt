package com.xabber.presentation.application.fragments

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.DeclinedCallsBinding
import com.xabber.presentation.application.contract.navigator

class DeclinedCallsFragment : BaseFragment(R.layout.declined_calls) {
    private val binding by viewBinding(DeclinedCallsBinding::bind)



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarActions()
    }
    private fun toolbarActions() {
        binding.callsToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.callsToolbar.setNavigationOnClickListener {
            navigator().close()
        }
    }

}