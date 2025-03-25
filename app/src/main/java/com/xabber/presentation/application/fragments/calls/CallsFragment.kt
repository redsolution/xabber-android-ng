package com.xabber.presentation.application.fragments.calls

import android.content.res.Configuration
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
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
        ifOrientationIsPortrait()
        filterIcon()
    }

    private fun ifOrientationIsPortrait() {
        val widthDp = DisplayManager.getWidthDp()
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            binding.callsToolbar.navigationIcon = null
            binding.callsToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
            binding.callsToolbar.setNavigationOnClickListener {
                navigator().goBack() // This will pop the back stack
            }
            binding.filter.setOnClickListener{
                view -> showPopupMenu(view)
            }
        }
    }

    private fun filterIcon() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            binding.filter.isVisible = true
            val toolbar = binding.callsToolbar
            val navigationIconPaddingStart = toolbar.contentInsetStart
            toolbar.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    toolbar.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val toolbarHeight = toolbar.height
                    val filterIcon = toolbar.findViewById<ImageView>(R.id.filter)
                    val iconHeight = filterIcon.height
                    val verticalPadding = (toolbarHeight - iconHeight) / 2

                    filterIcon.setPadding(navigationIconPaddingStart, verticalPadding, navigationIconPaddingStart, verticalPadding)
                }
            })
        }
    }

    private fun showPopupMenu(view: View): Boolean {
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.filter_menu, popup.menu)
        popup.setOnMenuItemClickListener {item: MenuItem ->
            when (item.itemId) {
                R.id.missed_call_item -> {
                    missedCalls()
                    true
                }
                R.id.incoming_call_item -> {
                    incomingCalls()
                    true
                }
                R.id.outgoing_call_item -> {
                    outgoingCalls()
                    true
                }
                R.id.declined_call_item -> {
                    declinedCalls()
                    true
                }
                else -> false
            }
        }
        // Show the popup menu
        popup.show()
        return true
    }

    private fun missedCalls() {
        navigator().launchDetail(MissedCallsFragment())
    }

    private fun incomingCalls() {
        navigator().launchDetail(IncomingCallsFragment())

    }
    private fun outgoingCalls() {
        navigator().launchDetail(OutgoingCallsFragment())

    }
    private fun declinedCalls() {
        navigator().launchDetail(DeclinedCallsFragment())

    }


    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

}
