package com.xabber.presentation.application.fragments.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSettingsBinding
import com.xabber.presentation.application.activity.ColorManager
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.account.AccountAdapter

class SettingsFragment : BaseFragment(R.layout.fragment_settings), AccountAdapter.Listener {
    private val binding by viewBinding(FragmentSettingsBinding::bind)
    private val viewModel: SettingsViewModel by activityViewModels()
    private var accountAdapter: AccountAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
       initToolbarMenu()
        initAccountList()
        subscribeToDataUpdates()
        initializeSettingsActions()
        if (viewModel.accounts.value != null) { if (viewModel.accounts.value!![0].enabled) binding.appbar.setBackgroundResource(ColorManager.convertColorNameToId(baseViewModel.getPrimaryAccount()?.colorKey ?: "default")) }
    }

    private fun initToolbarMenu() {
        binding.toolbarSettings.setOnMenuItemClickListener {
            when (it.itemId) {
//                R.id.add_account -> {
//                    navigator().showAddAccountFragment()
//                }
                R.id.reorder -> {
                    navigator().showReorderAccountsFragment()
                }
            }
            true
        }
    }

    private fun setupMenu(showItem: Boolean) {
       // binding.toolbarSettings.menu.findItem(R.id.add_account).isVisible = false
    }

    private fun initAccountList() {
        accountAdapter = AccountAdapter(this)
        binding.rvAccounts.adapter = accountAdapter

    }

    private fun subscribeToDataUpdates() {
        viewModel.accounts.observe(viewLifecycleOwner) {
            accountAdapter?.submitList(it)
            Log.d("acc", "$it")
            accountAdapter?.notifyDataSetChanged()
        }

        viewModel.avatars.observe(viewLifecycleOwner) {
            accountAdapter?.notifyDataSetChanged()
        }
    }

    private fun initializeSettingsActions() {
        binding.settings.interfaceSettings
        with(binding.settings) {
            interfaceSettings.setOnClickListener {
                navigator().showInterfaceSettings(false)
            }
            notifications.setOnClickListener { navigator().showNotificationsSettings() }
            dataAndStorage.setOnClickListener { navigator().showDataAndStorageSettings() }
            privacy.setOnClickListener { navigator().showPrivacySettings() }
            connection.setOnClickListener { navigator().showConnectionSettings() }
            debug.setOnClickListener { navigator().showDebugSettings() }
        }
    }

    override fun setEnabled(id: String, isChecked: Boolean) {
        viewModel.setEnabled(id, isChecked)
    }

    override fun onClick(id: String) {
        navigator().showAccount(id)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        accountAdapter?.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        accountAdapter = null
    }

}
