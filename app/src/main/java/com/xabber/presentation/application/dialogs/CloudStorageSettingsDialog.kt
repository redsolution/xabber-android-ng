package com.xabber.presentation.application.dialogs

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentCloudStorageSettingsBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.dto.AccountDto
import com.xabber.utils.toAccountDto
import io.realm.kotlin.Realm

class CloudStorageSettingsDialog : DialogFragment() {
    private val binding by viewBinding(FragmentCloudStorageSettingsBinding::bind)
    private val realm = Realm.open(defaultRealmConfig())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_cloud_storage_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.navigationIcon = null
        binding.left.setOnClickListener { dismiss() }

        // Set the toolbar color based on the current account's theme
        setupToolbarColor()
    }

    private fun setupToolbarColor() {
        val account = getPrimaryAccount()
        val colorKey = account?.colorKey ?: resources.getString(R.string.blue) // Default to blue if no account
        val colorRes = ColorManager.convertColorNameToId(colorKey)
        defineColor(colorRes)
    }

    private fun defineColor(colorRes: Int) {
        binding.toolbar.setBackgroundColor(
            ResourcesCompat.getColor(
                resources,
                colorRes,
                requireContext().theme
            )
        )
    }

    private fun getPrimaryAccount(): AccountDto? {
        var accountDto: AccountDto? = null
        val realmAccounts = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
        val primaryAccount = realmAccounts.minByOrNull { it.order }
        if (primaryAccount != null) {
            accountDto = primaryAccount.toAccountDto()
        }
        return accountDto
    }

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

    override fun onDestroy() {
        super.onDestroy()
        realm.close() // Clean up Realm instance
    }
}