package com.xabber.presentation.application.fragments.savedMessages

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCallsBinding
import com.xabber.databinding.FragmentSavedMessagesBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment

class SavedMessagesFragment : BaseFragment(R.layout.fragment_saved_messages) {
    private val binding by viewBinding(FragmentSavedMessagesBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activeLinks()
        binding.savedMessagesToolbar.navigationIcon = null
        binding.savedMessagesToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.savedMessagesToolbar.setNavigationOnClickListener {
            navigator().closeDetail()
            navigator().goBack()}
    }


    private fun activeLinks() {
        binding.tvAdvertisement.movementMethod = LinkMovementMethod.getInstance()
    }

}
