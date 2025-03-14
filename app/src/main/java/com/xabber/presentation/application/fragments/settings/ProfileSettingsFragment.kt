package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.navigation.NavigationView
import com.xabber.R
import com.xabber.databinding.FragmentProfileSettingsBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.DialogNavigator
import com.xabber.presentation.application.contract.dialogNavigator
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.contacts.ContactAccountFragment
import com.xabber.presentation.application.fragments.contacts.ContactAccountParams

class ProfileSettingsFragment : DetailBaseFragment(R.layout.fragment_profile_settings)  {
    private val binding by viewBinding(FragmentProfileSettingsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbar.setNavigationOnClickListener{navigator().goBack()}

    }

}
