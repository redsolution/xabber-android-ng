package com.xabber.presentation.application.dialogs

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.databinding.FragmentNotificationBinding
import com.xabber.dto.AccountDto
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.manage.ColorManager
import androidx.activity.OnBackPressedCallback
import com.xabber.utils.toAccountDto
import io.realm.kotlin.Realm

class NotificationsFragmentFull : BaseFragment(R.layout.fragment_notification) {

    private lateinit var binding: FragmentNotificationBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val realm = Realm.open(defaultRealmConfig())

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            navigateBack()
        }
    }

    private fun navigateBack() {
        if (parentFragmentManager.backStackEntryCount > 0) {
            parentFragmentManager.popBackStack()
        } else {
            navigator().goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    companion object {
        private const val KEY_INCOMING_MESSAGES = "incoming_messages_option"
        private const val KEY_SUBSCRIPTION_REQUESTS = "subscription_requests_option"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNotificationBinding.inflate(inflater, container, false)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        migratePreferences()

        setupToolbar()
        setupViews()
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbar.setNavigationOnClickListener { navigateBack() }

        // Set the toolbar color based on the current account's theme
        setupToolbarColor()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { navigateBack() }
        setupToolbarColor()
    }

    private fun setupViews() {
        // Set initial values for incoming messages and subscription requests
        val incomingMessagesDefault = sharedPreferences.getString(KEY_INCOMING_MESSAGES, resources.getString(R.string.unmute)) ?: resources.getString(R.string.unmute)
        binding.tvIncomingMessagesValue.text = incomingMessagesDefault

        val subscriptionRequestsDefault = sharedPreferences.getString(KEY_SUBSCRIPTION_REQUESTS, resources.getString(R.string.unmute)) ?: resources.getString(R.string.unmute)
        binding.tvSubscriptionRequestsValue.text = subscriptionRequestsDefault

        // Set click listeners for options
        binding.tvIncomingMessagesValue.setOnClickListener {
            showOptionsDialog(binding.tvIncomingMessagesValue, KEY_INCOMING_MESSAGES)
        }

        binding.tvSubscriptionRequestsValue.setOnClickListener {
            showOptionsDialog(binding.tvSubscriptionRequestsValue, KEY_SUBSCRIPTION_REQUESTS)
        }
        // Set up switches
        binding.switchInAppSounds.isChecked = getSafeBoolean("in_app_sounds", true)
        binding.switchInAppSounds.setOnCheckedChangeListener { _, isChecked ->
            savePreference("in_app_sounds", isChecked)
        }

        binding.switchMessagePreview.isChecked = getSafeBoolean("message_preview", true)
        binding.switchMessagePreview.setOnCheckedChangeListener { _, isChecked ->
            savePreference("message_preview", isChecked)
        }
    }

    @SuppressLint("UseGetLayoutInflater")
    private fun showOptionsDialog(selectedOptionTextView: TextView, preferenceKey: String) {
        // Inflate the custom dialog layout
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_notification_options, null)

        // Create the dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Set click listeners for each option
        dialogView.findViewById<TextView>(R.id.option_mute_1_hour).setOnClickListener {
            selectedOptionTextView.text = getString(R.string.mute_for_1_hour)
            sharedPreferences.edit().putString(preferenceKey, getString(R.string.mute_for_1_hour)).apply()
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.option_mute_1_day).setOnClickListener {
            selectedOptionTextView.text = getString(R.string.mute_for_1_day)
            sharedPreferences.edit().putString(preferenceKey, getString(R.string.mute_for_1_day)).apply()
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.option_mute_1_week).setOnClickListener {
            selectedOptionTextView.text = getString(R.string.mute_for_1_week)
            sharedPreferences.edit().putString(preferenceKey, getString(R.string.mute_for_1_week)).apply()
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.option_mute_forever).setOnClickListener {
            selectedOptionTextView.text = getString(R.string.mute_forever)
            sharedPreferences.edit().putString(preferenceKey, getString(R.string.mute_forever)).apply()
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.option_unmute).setOnClickListener {
            selectedOptionTextView.text = getString(R.string.unmute)
            sharedPreferences.edit().putString(preferenceKey, getString(R.string.unmute)).apply()
            dialog.dismiss()
        }

        // Show the dialog
        dialog.show()
    }
    private fun savePreference(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    private fun getSafeBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            sharedPreferences.getBoolean(key, defaultValue)
        } catch (e: ClassCastException) {
            sharedPreferences.edit().putBoolean(key, defaultValue).apply()
            defaultValue
        }
    }

    private fun migratePreferences() {
        val switchKeys = listOf("in_app_sounds", "message_preview")
        switchKeys.forEach { key ->
            if (sharedPreferences.contains(key) && sharedPreferences.all[key] !is Boolean) {
                sharedPreferences.edit().putBoolean(key, true).apply()
            }
        }
    }

    private fun setupToolbarColor() {
        val account = getPrimaryAccount()
        val colorKey = account?.colorKey ?: resources.getString(R.string.blue) // Default to blue if no account
        val colorRes = ColorManager.convertColorNameToId(colorKey)
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

    override fun onDestroy() {
        super.onDestroy()
        onBackPressedCallback.remove()
        realm.close()
    }
}