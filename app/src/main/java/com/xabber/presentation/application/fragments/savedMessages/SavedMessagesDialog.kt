package com.xabber.presentation.application.fragments.savedMessages

import android.content.res.Configuration
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.databinding.FragmentSavedMessagesBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.manage.DisplayManager

class SavedMessagesDialog : BaseFragment(R.layout.fragment_saved_messages) {
    private val binding by viewBinding(FragmentSavedMessagesBinding::bind)

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val widthDp = DisplayManager.getWidthDp()
            val orientation = resources.configuration.orientation
            if (widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) {
                val width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 90% of screen width
                val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER) // Center the dialog
            }
            if ((widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE)) {
                val width = (resources.displayMetrics.widthPixels * 0.48).toInt() // 90% of screen width
                val height = (resources.displayMetrics.heightPixels * 0.97).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER) // Center the dialog
            }

        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activeLinks()
        binding.savedMessagesToolbar.navigationIcon = null
        binding.savedMessagesToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.savedMessagesToolbar.setNavigationOnClickListener {
            dismiss()}
    }


    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

}
