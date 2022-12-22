package com.xabber.presentation.application.fragments.contacts

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentStatusBinding
import com.xabber.presentation.application.fragments.DetailBaseFragment

class StatusFragment: DetailBaseFragment(R.layout.fragment_status) {
    private val binding by viewBinding(FragmentStatusBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    //    binding.spinner.adapter = StatusModeAdapter()

    }
}