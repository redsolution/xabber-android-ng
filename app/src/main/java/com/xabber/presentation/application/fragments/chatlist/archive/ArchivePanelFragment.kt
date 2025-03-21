package com.xabber.presentation.application.fragments.chatlist.archive

import android.content.res.Configuration
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentArchivePanelBinding
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.databinding.FragmentContactBinding
import com.xabber.databinding.FragmentContactsPanelBinding
import com.xabber.databinding.FragmentNotificationsBinding
import com.xabber.databinding.FragmentNotificationsPanelBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.manage.DisplayManager

class ArchivePanelFragment : BaseFragment(R.layout.fragment_archive_panel) {
    private val binding by viewBinding(FragmentArchivePanelBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.archiveToolbar.navigationIcon = null
        activeLinks()
        ifOrientationIsPortrait()
        binding.tvTitle.isVisible = false
    }

    private fun ifOrientationIsPortrait() {
        val widthDp = DisplayManager.getWidthDp()
        val orientation = resources.configuration.orientation

        if (widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) {
            binding.archiveToolbar.navigationIcon = null
            binding.tvTitle.isVisible = true

            binding.archiveToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
            binding.archiveToolbar.setNavigationOnClickListener {
                navigator().goBack() // This will pop the back stack
            }
        }
    }

    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

}
