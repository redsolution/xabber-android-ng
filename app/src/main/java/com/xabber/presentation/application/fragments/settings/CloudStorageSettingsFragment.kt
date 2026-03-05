package com.xabber.presentation.application.fragments.settings

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView

import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCloudStorageSettingsBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.xmpp.dns.DNSResolver
import com.xabber.xmpp.dns.fetchFromSrv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CloudStorageSettingsFragment : DetailBaseFragment(R.layout.fragment_cloud_storage_settings) {
    private val binding by viewBinding(FragmentCloudStorageSettingsBinding::bind)

    override fun onNavigateBack() {
        parentFragmentManager.popBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dnsTestButton = binding.dnsTestButton
        val resultText = binding.resultText
        val hostInput = binding.hostInput

        dnsTestButton.setOnClickListener {
            val host = hostInput.text.toString().trim()
            if (host.isBlank()) {
                resultText.text = "Error: Please enter a valid host"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        fetchFromSrv(host)
                    }
                    // Display the entire response history
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch SRV data: ${e.message}", e)
                    resultText.text = "Failed: ${e.message}"
                }
            }
        }
    }

}
