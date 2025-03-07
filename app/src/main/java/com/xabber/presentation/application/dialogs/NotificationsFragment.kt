package com.xabber.presentation.application.dialogs

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Switch
import android.widget.TextView
import android.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.dto.AccountDto
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.utils.toAccountDto
import io.realm.kotlin.Realm

class NotificationsFragment : DialogFragment() {

    private lateinit var sharedPreferences: SharedPreferences
    private val realm = Realm.open(defaultRealmConfig())

    companion object {
        private const val KEY_INCOMING_MESSAGES = "incoming_messages_option"
        private const val KEY_SUBSCRIPTION_REQUESTS = "subscription_requests_option"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        migratePreferences()

        val rootLayout = LinearLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        val appBarLayout = createAppBarLayout()
        rootLayout.addView(appBarLayout)

        val notificationsSoundLayout = createSectionLayout(XabberApplication.applicationContext().resources.getString(R.string.notifications_sound))
        rootLayout.addView(notificationsSoundLayout)

        val incomingMessagesDefault = sharedPreferences.getString(KEY_INCOMING_MESSAGES, "None") ?: "None"
        val incomingMessagesLayout = createOptionWithMenuLayout(XabberApplication.applicationContext().resources.getString(R.string.incoming_messages), incomingMessagesDefault, KEY_INCOMING_MESSAGES)
        rootLayout.addView(incomingMessagesLayout)

        val subscriptionRequestsDefault = sharedPreferences.getString(KEY_SUBSCRIPTION_REQUESTS, "None") ?: "None"
        val subscriptionRequestsLayout = createOptionWithMenuLayout(XabberApplication.applicationContext().resources.getString(R.string.subscription_requests), subscriptionRequestsDefault, KEY_SUBSCRIPTION_REQUESTS)
        rootLayout.addView(subscriptionRequestsLayout)

        val inAppNotificationsLayout = createSectionLayout(XabberApplication.applicationContext().resources.getString(R.string.in_app_notifications))
        rootLayout.addView(inAppNotificationsLayout)

        val inAppSoundsLayout = createOptionWithSwitchLayout(XabberApplication.applicationContext().resources.getString(R.string.in_app_sounds), "in_app_sounds", true)
        rootLayout.addView(inAppSoundsLayout)

        val messagePreviewLayout = createOptionWithSwitchLayout(
            XabberApplication.applicationContext().resources.getString(R.string.on_chat_screen_message_preview), "message_preview", true
        )
        rootLayout.addView(messagePreviewLayout)

        return rootLayout
    }

    private fun createSectionLayout(title: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(32, 24, 32, 16) }
            orientation = LinearLayout.VERTICAL

            val sectionTitle = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = title
                setTextColor(Color.BLACK)
                textSize = 16f
                typeface = ResourcesCompat.getFont(context, R.font.roboto_bold)
            }
            addView(sectionTitle)
        }
    }

    private fun createOptionWithMenuLayout(label: String, defaultValue: String, preferenceKey: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(32, 16, 32, 16) }
            orientation = LinearLayout.HORIZONTAL

            val textView = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                text = label
                setTextColor(Color.BLACK)
                textSize = 18f
                typeface = ResourcesCompat.getFont(context, R.font.roboto)
            }
            addView(textView)

            val selectedOptionTextView = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = defaultValue
                setTextColor(Color.GRAY)
                textSize = 18f
                typeface = ResourcesCompat.getFont(context, R.font.roboto)
                setOnClickListener { showOptionsMenu(it, this, preferenceKey) }
            }
            addView(selectedOptionTextView)
        }
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun createOptionWithSwitchLayout(label: String, preferenceKey: String, defaultValue: Boolean): LinearLayout {
        return LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(32, 16, 32, 16) }
            orientation = LinearLayout.HORIZONTAL

            val textView = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                text = label
                setTextColor(Color.BLACK)
                textSize = 18f
                typeface = ResourcesCompat.getFont(context, R.font.roboto)
            }
            addView(textView)

            val switch = Switch(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isChecked = getSafeBoolean(preferenceKey, defaultValue)
                setOnCheckedChangeListener { _, isChecked ->
                    savePreference(preferenceKey, isChecked)
                }
            }
            addView(switch)
        }
    }

    private fun showOptionsMenu(view: View, selectedOptionTextView: TextView, preferenceKey: String) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(R.menu.notification_mute_options, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            val selectedText = when (menuItem.itemId) {
                R.id.action_mute_1_hour -> XabberApplication.applicationContext().resources.getString(R.string.mute_for_1_hour)
                R.id.action_mute_1_day -> XabberApplication.applicationContext().resources.getString(R.string.mute_for_1_day)
                R.id.action_mute_1_week -> XabberApplication.applicationContext().resources.getString(R.string.mute_for_1_week)
                R.id.action_mute_forever -> XabberApplication.applicationContext().resources.getString(R.string.mute_forever)
                R.id.action_unmute -> XabberApplication.applicationContext().resources.getString(R.string.unmute)
                else -> XabberApplication.applicationContext().resources.getString(R.string.unmute)
            }
            selectedOptionTextView.text = selectedText
            sharedPreferences.edit().putString(preferenceKey, selectedText).apply()
            true
        }
        popupMenu.show()
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

    private fun createAppBarLayout(): AppBarLayout {
        return AppBarLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val toolbar = MaterialToolbar(requireContext()).apply {
                layoutParams = AppBarLayout.LayoutParams(
                    AppBarLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.margin_onboarding_extra_large)
                )
                setTitleTextColor(Color.WHITE)
                // Apply the theme-based color
                setupToolbarColor(this)
            }

            val backButton = ImageView(requireContext()).apply {
                layoutParams = Toolbar.LayoutParams(
                    Toolbar.LayoutParams.WRAP_CONTENT,
                    Toolbar.LayoutParams.WRAP_CONTENT
                ).apply { setPadding(22, 22, 22, 22) }
                setImageResource(R.drawable.ic_arrow_left_white)
                setOnClickListener { dismiss() }
            }
            toolbar.addView(backButton)

            val title = TextView(requireContext()).apply {
                layoutParams = Toolbar.LayoutParams(
                    Toolbar.LayoutParams.WRAP_CONTENT,
                    Toolbar.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                    marginStart= 20
                }
                text = XabberApplication.applicationContext().getString(R.string.notification_settings)
                setTextAppearance(R.style.DialogsToolbarTitle)
                typeface = ResourcesCompat.getFont(context, R.font.roboto_medium)
            }
            toolbar.addView(title)

            addView(toolbar)
        }
    }

    private fun setupToolbarColor(toolbar: MaterialToolbar) {
        val account = getPrimaryAccount()
        val colorKey = account?.colorKey ?: resources.getString(R.string.blue) // Default to blue if no account
        val colorRes = ColorManager.convertColorNameToId(colorKey)
        toolbar.setBackgroundColor(
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
        dialog?.let {
            val width = (resources.displayMetrics.widthPixels * 0.8).toInt()
            val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
            it.window?.setLayout(width, height)
            it.window?.setGravity(Gravity.CENTER)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close() // Clean up Realm instance
    }
}