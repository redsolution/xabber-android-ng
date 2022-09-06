package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentProfileSettingsBinding
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.util.dp

class ProfileSettingsFragment : DetailBaseFragment(R.layout.fragment_profile_settings) {
    private val binding by viewBinding(FragmentProfileSettingsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!DisplayManager.isDualScreenMode()) {
            binding.clProfile.setPadding(68.dp, 12.dp, 8.dp, 12.dp)
            binding.clStatus.setPadding(68.dp, 12.dp, 8.dp, 12.dp)
            binding.clCircles.setPadding(68.dp, 12.dp, 8.dp, 12.dp)
            binding.clPassword.setPadding(68.dp, 12.dp, 8.dp, 12.dp)
            binding.clAccountColor.setPadding(68.dp, 12.dp, 8.dp, 12.dp)
            binding.clServerInformation.setPadding(68.dp, 12.dp, 8.dp, 12.dp)
            binding.clBlockedContacts.setPadding(68.dp, 12.dp, 8.dp, 12.dp)
            binding.clDeleteAccount.setPadding(68.dp, 12.dp, 8.dp, 12.dp)
        }
    }

}
