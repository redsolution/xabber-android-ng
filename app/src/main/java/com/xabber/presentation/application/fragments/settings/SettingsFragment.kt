package com.xabber.presentation.application.fragments.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentSettingsBinding
import com.xabber.dto.AccountDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.dialogNavigator
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.account.AccountAdapter
import com.xabber.presentation.application.fragments.account.AccountFragment
import com.xabber.presentation.application.fragments.contacts.ContactAccountFragment
import com.xabber.presentation.application.fragments.contacts.ContactAccountParams
import com.xabber.presentation.application.manage.DisplayManager

class SettingsFragment : BaseFragment(R.layout.fragment_settings), AccountAdapter.Listener {
    private val binding by viewBinding(FragmentSettingsBinding::bind)
    private val viewModel: SettingsViewModel by activityViewModels()
    private var accountAdapter: AccountAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbarActions()
        initAccountList()
        subscribeToDataUpdates()
        initializeSettingsActions()
        viewModel.loadAccounts()
    }

    private fun initToolbarActions() {
        binding.toolbarSettings.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.add_account -> navigator().showAddAccountFragment()
                R.id.reorder -> navigator().showReorderAccountsFragment()
            }
            true
        }
    }

    private fun setupMenu(isManyAccounts: Boolean) {
        binding.toolbarSettings.menu.findItem(R.id.reorder).isVisible = isManyAccounts
    }

    private fun initAccountList() {
        accountAdapter = AccountAdapter(this)
        binding.rvAccounts.layoutManager = LinearLayoutManager(context)
        binding.rvAccounts.adapter = accountAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun subscribeToDataUpdates() {
        viewModel.accounts.observe(viewLifecycleOwner) {
            val a = ArrayList<AccountDto>()
            a.addAll(it)
            accountAdapter?.submitList(a)
            setupMenu(it.size > 1)
        }

        viewModel.avatars.observe(viewLifecycleOwner) {
            accountAdapter?.notifyDataSetChanged()
        }
    }

    private fun initializeSettingsActions() {
        binding.settings.interfaceSettings
        with(binding.settings) {
            interfaceSettings.setOnClickListener { navigator().showInterfaceSettings(false) }
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
        if (DisplayManager.getWidthDp() > 600 && resources.configuration.orientation
            == Configuration.ORIENTATION_PORTRAIT || DisplayManager.getWidthDp() > 800
            && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        ) {

            parentFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.application_container, AccountFragment.newInstance(id))
                .addToBackStack(null) // Add to back stack for back navigation
                .commit()

//            val accDialog = AccountFragment.newInstance(id)
//            accDialog.show(childFragmentManager, "")

        } else {
            navigator().showAccount(id)


        }
    }


        @SuppressLint("NotifyDataSetChanged")
        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            super.onSharedPreferenceChanged(sharedPreferences, key)
            accountAdapter?.notifyDataSetChanged()
        }

        override fun onDestroy() {
            super.onDestroy()
            accountAdapter = null
        }

    }

