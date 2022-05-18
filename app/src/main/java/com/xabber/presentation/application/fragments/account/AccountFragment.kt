package com.xabber.presentation.application.fragments.account

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentAccountBinding
import com.xabber.presentation.application.fragments.DetailBaseFragment


class AccountFragment : DetailBaseFragment(R.layout.fragment_account) {
    private val binding by viewBinding(FragmentAccountBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.accountToolbar?.setNavigationIcon(R.drawable.ic_arrow_left_white_24dp)
    }


}