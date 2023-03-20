package com.xabber.presentation.application.fragments.calls

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.presentation.application.activity.MaskManager
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.contract.navigator
import io.realm.kotlin.Realm

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
