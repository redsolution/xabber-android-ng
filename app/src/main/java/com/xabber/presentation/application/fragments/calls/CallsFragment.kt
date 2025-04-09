package com.xabber.presentation.application.fragments.calls

import android.content.res.Configuration
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.DeclinedCallsFragment
import com.xabber.presentation.application.fragments.IncomingCallsFragment
import com.xabber.presentation.application.fragments.MissedCallsFragment
import com.xabber.presentation.application.fragments.OutgoingCallsFragment
import com.xabber.presentation.application.manage.DisplayManager

class CallsFragment : BaseFragment(R.layout.fragment_calls) {
    private val binding by viewBinding(FragmentCallsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.callsToolbar.navigationIcon = null
        binding.filter.isVisible = false
        activeLinks()
        setupOrientationLayout()
        filterIcon()
    }

    private fun setupOrientationLayout() {
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            binding.callsToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
            binding.callsToolbar.setNavigationOnClickListener {
                navigator().goBack()
            }
            binding.filter.setOnClickListener { view ->
                showPopupMenu(view)
            }
            // Ensure CallsFragment is in application_container, detail_container empty
            if (requireActivity().supportFragmentManager.findFragmentById(R.id.application_container) !is CallsFragment) {
                requireActivity().supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, this)
                    .addToBackStack("calls")
                    .commit()
            }
            if (requireActivity().supportFragmentManager.findFragmentById(R.id.detail_container) != null) {
                requireActivity().supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .remove(requireActivity().supportFragmentManager.findFragmentById(R.id.detail_container)!!)
                    .commit()
            }
        } else { // Landscape

            // Ensure CallsFragment is in detail_container, CallFiltersFragment in application_container
            if (requireActivity().supportFragmentManager.findFragmentById(R.id.detail_container) !is CallsFragment) {
                requireActivity().supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.detail_container, this)
                    .addToBackStack("calls")
                    .commit()
            }
            if (requireActivity().supportFragmentManager.findFragmentById(R.id.application_container) !is CallFiltersFragment) {
                requireActivity().supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.application_container, CallFiltersFragment())
                    .addToBackStack("call_filters")
                    .commit()
            }
        }
    }

    private fun filterIcon() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            binding.filter.isVisible = true
//            val toolbar = binding.callsToolbar
//            val navigationIconPaddingStart = toolbar.contentInsetStart
//            toolbar.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
//                override fun onGlobalLayout() {
//                    toolbar.viewTreeObserver.removeOnGlobalLayoutListener(this)
//                    val toolbarHeight = toolbar.height
//                    val filterIcon = toolbar.findViewById<ImageView>(R.id.filter)
//                    val iconHeight = filterIcon.height
//                    val verticalPadding = (toolbarHeight - iconHeight) / 2
//                    filterIcon.setPadding(navigationIconPaddingStart, verticalPadding, navigationIconPaddingStart, verticalPadding)
//                }
//            })
        }
    }

    private fun showPopupMenu(view: View): Boolean {
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.filter_menu, popup.menu)
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.missed_call_item -> {
                    replaceAppFragmentInStack(MissedCallsFragment())
                    true
                }
                R.id.incoming_call_item -> {
                    replaceAppFragmentInStack(IncomingCallsFragment())
                    true
                }
                R.id.outgoing_call_item -> {
                    replaceAppFragmentInStack(OutgoingCallsFragment())
                    true
                }
                R.id.declined_call_item -> {
                    replaceAppFragmentInStack(DeclinedCallsFragment())
                    true
                }
                else -> false
            }
        }
        popup.show()
        return true
    }

    private fun replaceAppFragmentInStack(fragment: Fragment) {
        val currentFragment = requireActivity().supportFragmentManager.findFragmentById(R.id.application_container)
        if (currentFragment != null) {
            requireActivity().supportFragmentManager.popBackStackImmediate(
                currentFragment::class.java.simpleName,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        }
        requireActivity().supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.application_container, fragment)
            .addToBackStack(fragment::class.java.simpleName)
            .commit()
    }

    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }
}