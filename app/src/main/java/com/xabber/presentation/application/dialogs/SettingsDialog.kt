package com.xabber.presentation.application.dialogs


import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
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
import com.xabber.presentation.application.fragments.settings.SettingsViewModel
import com.xabber.presentation.application.manage.DisplayManager

class SettingsDialog : DialogFragment(), AccountAdapter.Listener {
    private val binding by viewBinding(FragmentSettingsBinding::bind)
    private val viewModel: SettingsViewModel by activityViewModels()
    private var accountAdapter: AccountAdapter? = null
    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 90% of screen width
            val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
            dialog.window?.setLayout(width, height)
            dialog.window?.setGravity(Gravity.CENTER) // Center the dialog


        }
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)

    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbarActions()
        initAccountList()
        subscribeToDataUpdates()
        initializeSettingsActions()
        viewModel.loadAccounts()
        binding.toolbarSettings.navigationIcon = null
        binding.left.setOnClickListener{dismiss()}
    }

    private fun initToolbarActions() {
        binding.toolbarSettings.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.add_account -> handleAddAccount()
                R.id.reorder -> handleReorderAccounts()
            }
            true
        }
    }
private fun handleAddAccount() {
    val addAccount = AddAccountDialog()
    addAccount.show(childFragmentManager, "add Account")
//
}
    private fun handleReorderAccounts() {
        val handleReordering = ReorderAccountsDialog()
        handleReordering.show(childFragmentManager, "reordering")
//
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

            interfaceSettings.setOnClickListener {
                val interfaceD = InterfaceDialog()
                interfaceD.show(childFragmentManager, "Devices")
            //    navigator().showInterfaceSettings(false)
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
        if (DisplayManager.getWidthDp() > 600 && resources.configuration.orientation
            == Configuration.ORIENTATION_PORTRAIT || DisplayManager.getWidthDp() > 800
            && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        ) {

//            val account = AccountDialog.newInstance(id)
//            account.show(parentFragmentManager, "Account")

//            val accDialog = AccountFragment.newInstance(id)
//            accDialog.show(childFragmentManager, "")

                val account = AccountDialog.newInstance(id)
                account.show(parentFragmentManager, "Account")

        } else {
            navigator().showAccount(id)


        }
    }



    override fun onDestroy() {
        super.onDestroy()
        accountAdapter = null
    }

}

