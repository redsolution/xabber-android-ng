package com.xabber.presentation.application.fragments.calls

import android.content.res.Configuration
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.manage.DisplayManager

class CallsFragment : BaseFragment(R.layout.fragment_calls) {
    private val binding by viewBinding(FragmentCallsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.callsToolbar.navigationIcon = null
        activeLinks()
        ifOrientationIsPortrait()

    }
    private fun ifOrientationIsPortrait() {
        val widthDp = DisplayManager.getWidthDp()
        val orientation = resources.configuration.orientation

        if (widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) {
            binding.callsToolbar.navigationIcon = null
            binding.callsToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
            binding.callsToolbar.setNavigationOnClickListener{navigator().goBack()}
        }


    }
    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

}
