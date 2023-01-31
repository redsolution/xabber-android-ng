package com.xabber.presentation.application.fragments.settings

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSettingsBinding
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.account.AccountAdapter

class SettingsFragment : BaseFragment(R.layout.fragment_settings) {
    private val binding by viewBinding(FragmentSettingsBinding::bind)
    private val viewModel: SettingsViewModel by viewModels()
    private var accountAdapter: AccountAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeToolbarMenu()
        initializeAccountList()
        subscribeToDataUpdates()
        initializeSettingsActions()
    }

    private fun initializeToolbarMenu() {
        binding.toolbarSettings.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.add_account -> {
                   // navigator().showAddAccountFragment()
                }
                R.id.reorder -> {
                    navigator().showReorderAccountsFragment()
                }
            }
            true
        }
    }

    private fun setupMenu(showItem: Boolean) {
        binding.toolbarSettings.menu.findItem(R.id.add_account).isVisible = false
    }

    private fun initializeAccountList() {
        binding.rvAccounts.layoutManager = LinearLayoutManager(context)
        accountAdapter = AccountAdapter( {navigator().showAccount(it.jid)}, { viewModel.setEnabled(it.jid)
        Log.d("itt", "jid = ${it.jid}")})

        binding.rvAccounts.adapter = accountAdapter
        fillAccountList()
    }

    private fun fillAccountList() {
      viewModel.getAccountList()
    }

    private fun subscribeToDataUpdates() {
        viewModel.initDataListener()
        viewModel.accounts.observe(viewLifecycleOwner) {
            Log.d("itt", "sett subsc")
            accountAdapter?.submitList(it)
           // accountAdapter?.notifyDataSetChanged()
        }
    }

    private fun initializeSettingsActions() {
        binding.settings.interfaceSettings
        with(binding.settings) {
            interfaceSettings.setOnClickListener {
                navigator().showInterfaceSettings()
            }
            notifications.setOnClickListener { navigator().showNotificationsSettings() }
            dataAndStorage.setOnClickListener { navigator().showDataAndStorageSettings() }
            privacy.setOnClickListener { navigator().showPrivacySettings() }
            connection.setOnClickListener { navigator().showConnectionSettings() }
            debug.setOnClickListener { navigator().showDebugSettings() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        accountAdapter = null
    }

}
