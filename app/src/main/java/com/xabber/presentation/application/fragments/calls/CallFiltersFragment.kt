package com.xabber.presentation.application.fragments.calls

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCallsFiltersBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.DeclinedCallsFragment
import com.xabber.presentation.application.fragments.IncomingCallsFragment
import com.xabber.presentation.application.fragments.MissedCallsFragment
import com.xabber.presentation.application.fragments.OutgoingCallsFragment

class CallFiltersFragment : BaseFragment(R.layout.fragment_calls_filters) {
    private val binding by viewBinding(FragmentCallsFiltersBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activeLinks()
        toolbarActions()
        callsOptions()
    }

    private fun toolbarActions() {

        binding.callsToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.callsToolbar.setNavigationOnClickListener {
            navigator().goBack()
        }
    }

    private fun callsOptions() {
            handleMissedCall()
            handleIncomingCall()
            handleOutgoingCall()
            handleDeclinedCall()
    }

    private fun handleMissedCall() {
        binding.missedCallLayout.setOnClickListener {
            replaceDetailFragmentInStack(MissedCallsFragment())
        }
    }

    private fun handleIncomingCall() {
        binding.incomingCallLayout.setOnClickListener {
            replaceDetailFragmentInStack(IncomingCallsFragment())
        }
    }

    private fun handleOutgoingCall() {
        binding.outgoingCallLayout.setOnClickListener {
            replaceDetailFragmentInStack(OutgoingCallsFragment())
        }
    }

    private fun handleDeclinedCall() {
        binding.declinedCallLayout.setOnClickListener {
            replaceDetailFragmentInStack(DeclinedCallsFragment())
        }
    }

    private fun replaceDetailFragmentInStack(fragment: Fragment) {
        // Get the current fragment in detail_container
        val currentFragment = requireActivity().supportFragmentManager.findFragmentById(R.id.detail_container)

        // If there's an existing fragment in detail_container, remove it from the back stack
        if (currentFragment != null) {
            requireActivity().supportFragmentManager.popBackStackImmediate(
                currentFragment::class.java.simpleName,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        }

        // Launch the new fragment in detail_container, replacing the previous one
        navigator().launchDetailInStack(fragment)
    }

    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

}
