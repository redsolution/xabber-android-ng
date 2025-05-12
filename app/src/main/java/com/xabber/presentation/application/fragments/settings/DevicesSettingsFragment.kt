package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentDevicesSettingsBinding
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment

class DevicesSettingsFragment : DetailBaseFragment(R.layout.fragment_devices_settings) {
    private val binding by viewBinding(FragmentDevicesSettingsBinding::bind)
    private var isLoggingOut: Boolean = false // Prevent multiple logout calls

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbar.setNavigationOnClickListener{navigator().goBack()}

        binding.logOutButton.setOnClickListener {
            if (!isLoggingOut) {
                isLoggingOut = true
                Log.d("DevicesSettingsFragment", "Logout button clicked")
                try {
                    navigator().logOut()
                    Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("DevicesSettingsFragment", "Logout failed: ${e.message}", e)
                    Toast.makeText(requireContext(), "Logout failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isLoggingOut = false
                }
            } else {
                Log.w("DevicesSettingsFragment", "Logout already in progress, skipping")
            }
        }
    }

}
