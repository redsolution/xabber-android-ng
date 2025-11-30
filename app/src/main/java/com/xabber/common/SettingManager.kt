package com.xabber.common

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.xabber.R
import com.xabber.presentation.XabberApplication
import com.xabber.utils.prp

object SettingManager {

    private const val SUITE_NAME = "com.xabber.android.settings.common"
    private val sharedPreferences: SharedPreferences by lazy {
        XabberApplication.applicationContext()
            .getSharedPreferences(SUITE_NAME, Context.MODE_PRIVATE)
    }

    // ========================================================================
    // MARK: - Key Scopes (полностью как в Swift)
    // ========================================================================
    enum class KeyScope(val rawValue: String) {
        GLOBAL_INDEX("gc"),
        HTTP_UPLOADER("http_upl"),
        RELIABLE_MESSAGE_DELIVERY("rel_msg_del"),
        MESSAGE_DELETE_REWRITE("trust_cert_policy"),
        TRUST_CERTIFICATE_POLICY("msg_del_rewr"),
        CLIENT_SYNCHRONIZATION("client_sync"),
        ROSTER("roster"),
        MESSAGE_ARCHIVE("mam"),
        XABBER_UPLOAD_MANAGER("xabber_uploader"),
        AVATAR_UPLOAD_MANAGER("avatar_uploader"),
        AVATAR_MASKS("avatar_masks"),
        LANGUAGES("languages"),
        SECURITY("security"),
        PRODUCTS("products"),
        BURN_MESSAGES("burn_messages")
    }

    // ========================================================================
    // MARK: - Datasource
    // ========================================================================
    enum class DatasourceKind {
        TITLE, GROUP, BOOL, SELECTOR
    }

    data class Datasource(
        val key: String = "",
        val label: String = "",
        val kind: DatasourceKind = DatasourceKind.TITLE,
        var childs: List<Datasource> = emptyList(),
        val values: List<String> = emptyList(),
        var value: Any = ""
    )

    // ========================================================================
    // MARK: - Privacy
    // ========================================================================
    enum class PrivacyLevel(val rawValue: String) {
        INCOGNITO("incognito"),
        SERVER("server"),
        SERVER_CONTACTS("server_contacts");

        companion object {
            fun from(raw: String): PrivacyLevel =
                values().find { it.rawValue == raw } ?: SERVER
        }
    }

    enum class PrivacySettings(val rawValue: String) {
        TYPING_NOTIFICATION("privacy_typing_notifications")
    }

    // ========================================================================
    // MARK: - Public Datasources
    // ========================================================================
    var chatSettings: Datasource = Datasource()
    var rosterSettings: Datasource = Datasource()
    var languageSettings: Datasource = Datasource()
    var notificationSettings: Datasource = Datasource()
    var privacySettings: Datasource = Datasource()
    var developerSettings: Datasource = Datasource()

    // ========================================================================
    // MARK: - Log Enabled (как в Swift)
    // ========================================================================
    val logEnabled: Boolean
        get() = sharedPreferences.getBoolean("developer_logEnabled", false)

    // ========================================================================
    // MARK: - Init & Load
    // ========================================================================
    init {
        loadSettings()
    }

    fun loadSettings() {
        writeDefaultIfNeeded()
        val dict = sharedPreferences.all

        chatSettings = buildChatSettings(dict)
        rosterSettings = buildRosterSettings(dict)
        notificationSettings = buildNotificationSettings(dict)
        privacySettings = buildPrivacySettings(dict)
//        languageSettings = buildLanguageSettings(dict)
        developerSettings = buildDeveloperSettings(dict)
    }

    private fun writeDefaultIfNeeded() {
        if (sharedPreferences.contains("default_settingsCached")) return

//        val defaultBackground = if (CommonConfigManager.config.locked_background.isNotEmpty()) {
//            CommonConfigManager.config.locked_background
//        } else {
//            "Flowers"
//        }

        val defaults = mapOf(
            "default_settingsCached" to true,
            "chat_fontSize" to "regular",
            "chat_showStatus" to true,
            "chat_showBackground" to true,
            "chat_sendByEnter" to false,
//            "chat_chooseBackground" to defaultBackground,
            "chat_chooseBackgroundColor" to "purple",
            "roster_sorting" to "by_status",
            "roster_showAvatars" to true,
            "roster_showOfflineContacts" to true,
            "roster_showGroups" to true,
            "roster_divideByAccounts" to true,
            "roster_showEmptyGroups" to true,
            "notification_in_app_alert_last_chats" to false,
            "notification_in_app_sound" to true,
            "notification_chat_sound" to true,
            "notification_chat_vibration" to true,
            "notification_chat_badge" to "full",
            "notification_groupchat_sound" to true,
            "notification_groupchat_vibration" to true,
            "notification_groupchat_badge" to "full",
            "notification_chat_showPreviews" to true,
            "notification_groupchat_showPreviews" to true,
            "privacy_textInputNotify" to true,
            "privacy_checkServerCertificate" to true,
//            "privacy_level" to CommonConfigManager.config.default_privacy_level,
            "developer_logEnabled" to false,
            "avatar_masks_current_avatar_mask_" to "rounded",
            "burn_messages_enabled" to true,
            "burn_messages_timer" to 0,
            PrivacySettings.TYPING_NOTIFICATION.rawValue to true
        )

        sharedPreferences.edit {
            defaults.forEach { (k, v) ->
                when (v) {
                    is Boolean -> putBoolean(k, v)
                    is String -> putString(k, v)
                    is Int -> putInt(k, v)
                }
            }
        }
    }

    fun saveClientSynchronizationVersion(jid: String, version: String) {
        saveItem(jid, KeyScope.CLIENT_SYNCHRONIZATION, "version", version)
    }

    fun getClientSynchronizationVersion(jid: String): String? {
        return getKey(jid, KeyScope.CLIENT_SYNCHRONIZATION, "version")
    }

    // ========================================================================
    // MARK: - Builders (читаемо и как в Swift)
    // ========================================================================
    private fun buildChatSettings(dict: Map<String, *>): Datasource = Datasource(
        key = "chat",
        label = localize("account_settings_chat", "Chat"),
        kind = DatasourceKind.TITLE,
        childs = listOf(
            Datasource(
                key = "chat_background",
                label = localize("account_settings_background", "Background"),
                kind = DatasourceKind.GROUP,
                childs = listOf(
                    Datasource(
                        key = "chat_chooseBackground",
                        label = localize("account_settings_choose_background", "Choose background"),
                        kind = DatasourceKind.SELECTOR,
                        values = listOf("Aliens", "Summer", "Honeycomb", "Cats", "Flowers", "Flowers-daisy", "Hearts"),
                        value = dict["chat_chooseBackground"] as? String ?: "Aliens"
                    )
                )
            ),
            Datasource(
                key = "chat_design",
                label = localize("account_settings_display", "Display"),
                kind = DatasourceKind.GROUP,
                childs = listOf(
                    Datasource(
                        key = "chat_showBackground",
                        label = localize("account_settings_show_background", "Show background"),
                        kind = DatasourceKind.BOOL,
                        value = dict["chat_showBackground"] as? Boolean ?: true
                    )
                ),
                value = localize("account_settings_chat_display_settings", "Chat items display settings")
            ),
            Datasource(
                key = "chat_behaviour",
                label = localize("account_settings_messaging", "Messaging"),
                kind = DatasourceKind.GROUP,
                childs = listOf(
                    Datasource(
                        key = "chat_sendByEnter",
                        label = localize("account_settings_send_by_enter", "Send by enter"),
                        kind = DatasourceKind.BOOL,
                        value = dict["chat_sendByEnter"] as? Boolean ?: false
                    )
                ),
                value = localize("account_settings_message_sending_options", "Choose type of message sending options")
            )
        )
    )

    fun get(key: String): Any? {
        return sharedPreferences.all[key]
    }
    private fun buildRosterSettings(dict: Map<String, *>): Datasource = Datasource(
        key = "roster",
        label = localize("account_settings_contact_list", "Contact list"),
        kind = DatasourceKind.TITLE,
        childs = listOf(
            Datasource(
                key = "roster_display",
                label = localize("account_settings_display_options", "Display options"),
                kind = DatasourceKind.GROUP,
                childs = listOf(
                    Datasource(key = "roster_showOfflineContacts", label = localize("account_settings_offline_contacts", "Show offline contacts"), kind = DatasourceKind.BOOL, value = dict["roster_showOfflineContacts"] as? Boolean ?: true),
                    Datasource(key = "roster_showAvatars", label = localize("account_settings_show_avatars", "Show avatars"), kind = DatasourceKind.BOOL, value = dict["roster_showAvatars"] as? Boolean ?: true),
                    Datasource(key = "roster_showGroups", label = localize("account_settings_show_circles", "Show circles"), kind = DatasourceKind.BOOL, value = dict["roster_showGroups"] as? Boolean ?: true)
                )
            )
        )
    )

    private fun buildNotificationSettings(dict: Map<String, *>): Datasource = Datasource(
        key = "notification",
        label = localize("account_settings_notificaions", "Notifications"),
        kind = DatasourceKind.TITLE,
        childs = listOf(
            Datasource(
                key = "notification_in_app",
                label = localize("account_settings_in_app_notifications", "In-App notifications"),
                kind = DatasourceKind.GROUP,
                childs = listOf(
                    Datasource(key = "notification_in_app_alert_last_chats", label = localize("account_settings_chat_message_preview", "On chats screen message preview"), kind = DatasourceKind.BOOL, value = dict["notification_in_app_alert_last_chats"] as? Boolean ?: false),
                    Datasource(key = "notification_in_app_sound", label = localize("account_settings_in_app_sounds", "In-App sounds"), kind = DatasourceKind.BOOL, value = dict["notification_in_app_sound"] as? Boolean ?: true)
                ),
                value = localize("account_settings_notification_application_settings", "Notification settings for application")
            )
            // При необходимости можно раскомментировать блоки chat/groupchat
        )
    )

    private fun buildPrivacySettings(dict: Map<String, *>): Datasource = Datasource(
        key = "privacy",
        label = localize("account_settings_privacy", "Privacy"),
        kind = DatasourceKind.TITLE,
        childs = listOf(
            Datasource(
                key = "privacy",
                label = localize("account_settings_privacy_settings", "Privacy settings"),
                kind = DatasourceKind.GROUP,
                childs = listOf(
                    Datasource(
                        key = "privacy_textInputNotify",
                        label = localize("account_settings_typing_notification", "Send typing notification"),
                        kind = DatasourceKind.BOOL,
                        value = dict["privacy_textInputNotify"] as? Boolean ?: true
                    ),
                    Datasource(
                        key = "privacy_level",
                        label = localize("account_settings_privacy_level", "Privacy level"),
                        kind = DatasourceKind.SELECTOR,
                        values = PrivacyLevel.values().map { it.rawValue },
//                        value = dict["privacy_level"] as? String ?: CommonConfigManager.config.default_privacy_level
                    )
                )
            )
        )
    )

    private fun buildLanguageSettings(dict: Map<String, *>): Datasource {
        val languages = listOf("Default") + TranslationManager.Languages
        @Suppress("UNCHECKED_CAST")
        return Datasource(
            key = "languages",
            label = localize("settings_choose_language", "Choose language"),
            kind = DatasourceKind.GROUP,
            childs = listOf(
                Datasource(
                    key = "system_language",
                    label = "Default",
                    kind = DatasourceKind.SELECTOR,
                    values = languages as List<String>,
                    value = TranslationManager.currentLang ?: "Default"
                )
            )
        )
    }

    private fun buildDeveloperSettings(dict: Map<String, *>): Datasource = Datasource(
        key = "developer",
        label = localize("account_settings_developer", "Developer"),
        kind = DatasourceKind.TITLE,
        childs = listOf(
            Datasource(
                key = "developer",
                label = localize("account_settings_developer_mode", "Developer mode"),
                kind = DatasourceKind.GROUP,
                childs = listOf(
                    Datasource(
                        key = "developer_logEnabled",
                        label = localize("account_settings_write_log", "Write log"),
                        kind = DatasourceKind.BOOL,
                        value = dict["developer_logEnabled"] as? Boolean ?: false
                    )
                )
            )
        )
    )

    // ========================================================================
    // MARK: - Update & Save
    // ========================================================================
    fun updateValue(key: String, value: Any) {
        fun updateIn(datasource: Datasource): Boolean {
            datasource.childs.forEachIndexed { i, item ->
                if (item.key == key) {
                    datasource.childs = datasource.childs.toMutableList().apply { this[i] = item.copy(value = value) }
                    return true
                }
                item.childs.forEachIndexed { j, child ->
                    if (child.key == key) {
                        val mutable = datasource.childs.toMutableList()
                        val updatedItem = mutable[i].childs.toMutableList().apply { this[j] = child.copy(value = value) }
                        mutable[i] = mutable[i].copy(childs = updatedItem)
                        datasource.childs = mutable
                        return true
                    }
                }
            }
            return false
        }

        when {
            updateIn(chatSettings) -> return
            updateIn(rosterSettings) -> return
            updateIn(notificationSettings) -> return
            updateIn(languageSettings) -> return
            updateIn(privacySettings) -> return
            updateIn(developerSettings) -> return
        }
    }

    fun getDatasource(key: String): Datasource? = when (key) {
        "chat" -> chatSettings
        "roster" -> rosterSettings
        "languages" -> languageSettings
        "notification" -> notificationSettings
        "privacy" -> privacySettings
        "developer" -> developerSettings
        else -> null
    }

    // ========================================================================
    // MARK: - Save / Get (scoped + simple)
    // ========================================================================
    fun saveItem(jid: String, scope: KeyScope, key: String, value: String) {
        val computed = listOf(scope.rawValue, key, jid).prp()
        sharedPreferences.edit { putString(computed, value) }
    }

    fun saveItem(jid: String, scope: KeyScope, key: String, value: Boolean) {
        val computed = listOf(scope.rawValue, key, jid).prp()
        sharedPreferences.edit { putBoolean(computed, value) }
    }

    fun saveItem(jid: String, scope: KeyScope, key: String, value: Int) {
        val computed = listOf(scope.rawValue, key, jid).prp()
        sharedPreferences.edit { putInt(computed, value) }
    }

    fun saveItem(key: String, value: String) {
        sharedPreferences.edit { putString(key, value) }
        updateValue(key, value)
    }

    fun saveItem(key: String, value: Boolean) {
        sharedPreferences.edit { putBoolean(key, value) }
        updateValue(key, value)
    }

    fun saveItem(key: String, value: Int) {
        sharedPreferences.edit { putInt(key, value) }
    }

    fun getKey(jid: String, scope: KeyScope, key: String): String? {
        val computed = listOf(scope.rawValue, key, jid).prp()
        return sharedPreferences.getString(computed, null)
    }

    fun getInt(jid: String, scope: KeyScope, key: String, default: Int = 0): Int {
        val computed = listOf(scope.rawValue, key, jid).prp()
        return sharedPreferences.getInt(computed, default)
    }

    fun getKeyBool(jid: String, scope: KeyScope, key: String): Boolean? {
        val computed = listOf(scope.rawValue, key, jid).prp()
        return if (sharedPreferences.contains(computed)) sharedPreferences.getBoolean(computed, false) else null
    }

    fun getBool(key: String, default: Boolean = false): Boolean =
        sharedPreferences.getBoolean(key, default)

    fun getString(key: String): String? =
        sharedPreferences.getString(key, null)

    fun removeItem(jid: String, scope: KeyScope, key: String) {
        val computed = listOf(scope.rawValue, key, jid).prp()
        sharedPreferences.edit { remove(computed) }
    }

    fun clear(jid: String) {
        removeItem(jid, KeyScope.CLIENT_SYNCHRONIZATION, "version")
        removeItem(jid, KeyScope.TRUST_CERTIFICATE_POLICY, "allowed")
        removeItem(jid, KeyScope.ROSTER, "version")
        removeItem(jid, KeyScope.MESSAGE_ARCHIVE, "version")
        removeItem(jid, KeyScope.MESSAGE_ARCHIVE, "initial")
        removeItem(jid, KeyScope.XABBER_UPLOAD_MANAGER, "node")
        removeItem(jid, KeyScope.AVATAR_UPLOAD_MANAGER, "node")
        removeItem(jid, KeyScope.HTTP_UPLOADER, "node")
    }

    // ========================================================================
    // MARK: - Localization Helper
    // ========================================================================
    private fun localize(id: String, defaultValue: String): String {
        return XabberApplication.applicationContext().getString(
            when (id) {
                "account_settings_chat" -> R.string.account_settings_chat
                "account_settings_background" -> R.string.account_settings_background
                "account_settings_choose_background" -> R.string.account_settings_choose_background
                "account_settings_display" -> R.string.account_settings_display
                "account_settings_show_background" -> R.string.account_settings_show_background
                "account_settings_chat_display_settings" -> R.string.account_settings_chat_display_settings
                "account_settings_messaging" -> R.string.account_settings_messaging
                "account_settings_send_by_enter" -> R.string.account_settings_send_by_enter
                "account_settings_message_sending_options" -> R.string.account_settings_message_sending_options
                "account_settings_contact_list" -> R.string.account_settings_contact_list
                "account_settings_display_options" -> R.string.account_settings_display_options
                "account_settings_offline_contacts" -> R.string.account_settings_offline_contacts
                "account_settings_show_avatars" -> R.string.account_settings_show_avatars
                "account_settings_show_circles" -> R.string.account_settings_show_circles
                "account_settings_notificaions" -> R.string.account_settings_notificaions
                "account_settings_in_app_notifications" -> R.string.account_settings_in_app_notifications
                "account_settings_chat_message_preview" -> R.string.account_settings_chat_message_preview
                "account_settings_in_app_sounds" -> R.string.account_settings_in_app_sounds
                "account_settings_privacy" -> R.string.account_settings_privacy
                "account_settings_privacy_settings" -> R.string.account_settings_privacy_settings
                "account_settings_typing_notification" -> R.string.account_settings_typing_notification
                "account_settings_privacy_level" -> R.string.account_settings_privacy_level
                "settings_choose_language" -> R.string.settings_choose_language
                "account_settings_developer" -> R.string.account_settings_developer
                "account_settings_developer_mode" -> R.string.account_settings_developer_mode
                "account_settings_write_log" -> R.string.account_settings_write_log
                else -> return defaultValue
            }
        ) ?: defaultValue
    }
}