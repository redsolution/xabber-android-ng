package com.xabber.presentation.application.fragments.notifications

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.databinding.FragmentNotificationsBinding
import com.xabber.databinding.FragmentNotificationsPanelBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment

class NotificationFragment : BaseFragment(R.layout.fragment_notifications_panel) {
    private val binding by viewBinding(FragmentNotificationsPanelBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activeLinks()
        binding.notificationsToolbar.navigationIcon = null
        binding.notificationsToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.notificationsToolbar.setNavigationOnClickListener {
            navigator().closeDetail()
            navigator().goBack()}
    }


    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

}
