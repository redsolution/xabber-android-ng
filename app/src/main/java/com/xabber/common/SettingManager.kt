package com.xabber.common

import android.content.Context
import android.content.SharedPreferences
import com.xabber.R
import com.xabber.presentation.XabberApplication
import com.xabber.utils.prp

object SettingManager {
    private const val PREFS_NAME = "com.xabber.android.settings.common"
    private val sharedPreferences: SharedPreferences
        get() = XabberApplication.applicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    enum class DatasourceKind {
        TITLE,
        GROUP,
        BOOL,
        SELECTOR
    }

    data class Datasource(
        val key: String = "",
        val label: String = "",
        val kind: DatasourceKind = DatasourceKind.TITLE,
        var childs: List<Datasource> = emptyList(),
        val values: List<String> = emptyList(),
        val value: Any = ""
    )

    enum class PrivacyLevel(val rawValue: String) {
        INCOGNITO("incognito"),
        SERVER("server"),
        SERVER_CONTACTS("server_contacts");

        companion object {
            fun fromRaw(raw: String): PrivacyLevel = values().find { it.rawValue == raw } ?: SERVER
        }
    }

    enum class PrivacySettings(val rawValue: String) {
        TYPING_NOTIFICATION("privacy_typing_notifications")
    }

    // Placeholder for TranslationsManager (assuming a similar structure)
    object TranslationsManager {
        val languages: List<String> = listOf("en", "es", "fr", "de") // Replace with actual language codes
        var currentLang: String? = null
            get() = sharedPreferences.getString("system_language", "Default")
    }

    // Placeholder for CommonConfigManager
    object CommonConfigManager {
        object Config {
            val lockedBackground: String = "" // Replace with actual logic or default
            val defaultPrivacyLevel: String = PrivacyLevel.SERVER.rawValue
        }
    }

    val logEnabled: Boolean
        get() = sharedPreferences.getBoolean("developer_logEnabled", false)

    var chatSettings: Datasource = Datasource()
    var rosterSettings: Datasource = Datasource()
    var languageSettings: Datasource = Datasource()
    var notificationSettings: Datasource = Datasource()
    var privacySettings: Datasource = Datasource()
    var developerSettings: Datasource = Datasource()

    init {
        loadSettings()
    }

    fun loadSettings() {
        writeDefault()
        val dict = sharedPreferences.all
        chatSettings = Datasource(
            key = "chat",
            label = localizeString("account_settings_chat", "Chat"),
            kind = DatasourceKind.TITLE,
            childs = listOf(
                Datasource(
                    key = "chat_background",
                    label = localizeString("account_settings_background", "Background"),
                    kind = DatasourceKind.GROUP,
                    childs = listOf(
                        Datasource(
                            key = "chat_chooseBackground",
                            label = localizeString("account_settings_choose_background", "Choose background"),
                            kind = DatasourceKind.SELECTOR,
                            childs = emptyList(),
                            values = listOf("Aliens", "Summer", "Honeycomb", "Cats", "Flowers", "Flowers-daisy", "Hearts"),
                            value = dict["chat_chooseBackground"] as? String ?: "Aliens"
                        )
                    ),
                    values = emptyList(),
                    value = ""
                ),
                Datasource(
                    key = "chat_design",
                    label = localizeString("account_settings_display", "Display"),
                    kind = DatasourceKind.GROUP,
                    childs = listOf(
                        Datasource(
                            key = "chat_showBackground",
                            label = localizeString("account_settings_show_background", "Show background"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["chat_showBackground"] as? Boolean ?: true
                        )
                    ),
                    values = emptyList(),
                    value = localizeString("account_settings_chat_display_settings", "Chat items display settings")
                ),
                Datasource(
                    key = "chat_behaviour",
                    label = localizeString("account_settings_messaging", "Messaging"),
                    kind = DatasourceKind.GROUP,
                    childs = listOf(
                        Datasource(
                            key = "chat_sendByEnter",
                            label = localizeString("account_settings_send_by_enter", "Send by enter"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["chat_sendByEnter"] as? Boolean ?: false
                        )
                    ),
                    values = emptyList(),
                    value = localizeString("account_settings_message_sending_options", "Choose type of message sending options")
                )
            ),
            values = emptyList(),
            value = false
        )
        rosterSettings = Datasource(
            key = "roster",
            label = localizeString("account_settings_contact_list", "Contact list"),
            kind = DatasourceKind.TITLE,
            childs = listOf(
                Datasource(
                    key = "roster_display",
                    label = localizeString("account_settings_display_options", "Display options"),
                    kind = DatasourceKind.GROUP,
                    childs = listOf(
                        Datasource(
                            key = "roster_showOfflineContacts",
                            label = localizeString("account_settings_offline_contacts", "Show offline contacts"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["roster_showOfflineContacts"] as? Boolean ?: true
                        ),
                        Datasource(
                            key = "roster_showAvatars",
                            label = localizeString("account_settings_show_avatars", "Show avatars"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["roster_showAvatars"] as? Boolean ?: true
                        ),
                        Datasource(
                            key = "roster_showGroups",
                            label = localizeString("account_settings_show_circles", "Show circles"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["roster_showGroups"] as? Boolean ?: true
                        )
                    ),
                    values = emptyList(),
                    value = ""
                )
            ),
            values = emptyList(),
            value = false
        )
        notificationSettings = Datasource(
            key = "notification",
            label = localizeString("account_settings_notificaions", "Notifications"),
            kind = DatasourceKind.TITLE,
            childs = listOf(
                Datasource(
                    key = "notification_in_app",
                    label = localizeString("account_settings_in_app_notifications", "In-App notifications"),
                    kind = DatasourceKind.GROUP,
                    childs = listOf(
                        Datasource(
                            key = "notification_in_app_alert_last_chats",
                            label = localizeString("account_settings_chat_message_preview", "On chats screen message preview"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["notification_in_app_alert_last_chats"] as? Boolean ?: false
                        ),
                        Datasource(
                            key = "notification_in_app_sound",
                            label = localizeString("account_settings_in_app_sounds", "In-App sounds"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["notification_in_app_sound"] as? Boolean ?: true
                        )
                    ),
                    values = emptyList(),
                    value = localizeString("account_settings_notification_application_settings", "Notification settings for application")
                ),
                Datasource(
                    key = "notification_chat",
                    label = localizeString("account_settings_chat_notifications", "Chat notifications"),
                    kind = DatasourceKind.GROUP,
                    childs = listOf(
                        Datasource(
                            key = "notification_chat_sound",
                            label = localizeString("account_settings_chat_sound", "Sound"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["notification_chat_sound"] as? Boolean ?: true
                        ),
                        Datasource(
                            key = "notification_chat_vibration",
                            label = localizeString("account_settings_chat_vibration", "Vibration"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["notification_chat_vibration"] as? Boolean ?: true
                        ),
                        Datasource(
                            key = "notification_chat_showPreviews",
                            label = localizeString("account_settings_chat_show_previews", "Show previews"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["notification_chat_showPreviews"] as? Boolean ?: true
                        )
                    ),
                    values = emptyList(),
                    value = localizeString("account_settings_chat_notifications_manage", "Manage settings for notifications in private chat")
                ),
                Datasource(
                    key = "notification_groupchat",
                    label = localizeString("account_settings_groupchat_notifications", "Groupchat notifications"),
                    kind = DatasourceKind.GROUP,
                    childs = listOf(
                        Datasource(
                            key = "notification_groupchat_sound",
                            label = localizeString("account_settings_groupchat_sound", "Sound"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["notification_groupchat_sound"] as? Boolean ?: true
                        ),
                        Datasource(
                            key = "notification_groupchat_vibration",
                            label = localizeString("account_settings_groupchat_vibration", "Vibration"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["notification_groupchat_vibration"] as? Boolean ?: true
                        ),
                        Datasource(
                            key = "notification_groupchat_showPreviews",
                            label = localizeString("account_settings_groupchat_show_previews", "Show previews"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["notification_groupchat_showPreviews"] as? Boolean ?: true
                        )
                    ),
                    values = emptyList(),
                    value = localizeString("account_settings_groupchat_notifications_manage", "Manage settings for notifications in groupchats")
                )
            ),
            values = emptyList(),
            value = false
        )
        privacySettings = Datasource(
            key = "privacy",
            label = localizeString("account_settings_privacy", "Privacy"),
            kind = DatasourceKind.TITLE,
            childs = listOf(
                Datasource(
                    key = "privacy",
                    label = localizeString("account_settings_privacy_settings", "Privacy settings"),
                    kind = DatasourceKind.GROUP,
                    childs = listOf(
                        Datasource(
                            key = "privacy_textInputNotify",
                            label = localizeString("account_settings_typing_notification", "Send typing notification"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["privacy_textInputNotify"] as? Boolean ?: true
                        ),
                        Datasource(
                            key = "privacy_level",
                            label = localizeString("account_settings_privacy_level", "Privacy level"),
                            kind = DatasourceKind.SELECTOR,
                            childs = emptyList(),
                            values = PrivacyLevel.values().map { it.rawValue },
                            value = dict["privacy_level"] as? String ?: CommonConfigManager.Config.defaultPrivacyLevel
                        )
                    ),
                    values = emptyList(),
                    value = ""
                )
            ),
            values = emptyList(),
            value = false
        )
        languageSettings = Datasource(
            key = "languages",
            label = localizeString("settings_choose_language", "Choose language"),
            kind = DatasourceKind.GROUP,
            childs = listOf(
                Datasource(
                    key = "system_language",
                    label = "Default",
                    kind = DatasourceKind.SELECTOR,
                    childs = emptyList(),
                    values = listOf("Default") + TranslationsManager.languages,
                    value = TranslationsManager.currentLang ?: "Default"
                )
            ),
            values = emptyList(),
            value = ""
        )
        developerSettings = Datasource(
            key = "developer",
            label = localizeString("account_settings_developer", "Developer"),
            kind = DatasourceKind.TITLE,
            childs = listOf(
                Datasource(
                    key = "developer",
                    label = localizeString("account_settings_developer_mode", "Developer mode"),
                    kind = DatasourceKind.GROUP,
                    childs = listOf(
                        Datasource(
                            key = "developer_logEnabled",
                            label = localizeString("account_settings_write_log", "Write log"),
                            kind = DatasourceKind.BOOL,
                            childs = emptyList(),
                            values = emptyList(),
                            value = dict["developer_logEnabled"] as? Boolean ?: true
                        )
                    ),
                    values = emptyList(),
                    value = ""
                )
            ),
            values = emptyList(),
            value = false
        )
    }

    fun writeDefault() {
        if (sharedPreferences.contains("default_settingsCached")) {
            return
        }

        val defaultBackground = if (CommonConfigManager.Config.lockedBackground.isNotEmpty()) {
            CommonConfigManager.Config.lockedBackground
        } else {
            "Flowers"
        }

        val defaults = mapOf(
            "default_settingsCached" to true,
            "chat_fontSize" to "regular",
            "chat_showStatus" to true,
            "chat_showBackground" to true,
            "chat_sendByEnter" to false,
            "chat_chooseBackground" to defaultBackground,
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
            "privacy_level" to CommonConfigManager.Config.defaultPrivacyLevel,
            "developer_logEnabled" to false,
            "avatar_masks_current_avatar_mask_" to "rounded",
            "burn_messages_enabled" to true,
            "burn_messages_timer" to 0,
            PrivacySettings.TYPING_NOTIFICATION.rawValue to true
        )

        with(sharedPreferences.edit()) {
            defaults.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                }
            }
            apply()
        }
    }

    fun updateValue(key: String, value: Any) {
        fun updateItem(settings: Datasource): Boolean {
            settings.childs.forEachIndexed { index, item ->
                if (item.key == key) {
                    settings.childs = settings.childs.toMutableList().apply { this[index] = item.copy(value = value) }
                    return true
                }
                item.childs.forEachIndexed { childIndex, childItem ->
                    if (childItem.key == key) {
                        item.childs = item.childs.toMutableList().apply { this[childIndex] = childItem.copy(value = value) }
                        return true
                    }
                }
            }
            return false
        }

        when {
            updateItem(chatSettings) -> return
            updateItem(rosterSettings) -> return
            updateItem(notificationSettings) -> return
            updateItem(languageSettings) -> return
            updateItem(privacySettings) -> return
            updateItem(developerSettings) -> return
        }
    }

    fun getDatasource(key: String): Datasource? {
        return when (key) {
            "chat" -> chatSettings
            "roster" -> rosterSettings
            "languages" -> languageSettings
            "notification" -> notificationSettings
            "privacy" -> privacySettings
            "developer" -> developerSettings
            else -> null
        }
    }

    fun saveItem(jid: String, scope: KeyScope, key: String, value: Int) {
        val computedKey = listOf(scope.rawValue, key, jid).prp()
        saveItem(computedKey, value)
    }

    fun saveItem(jid: String, scope: KeyScope, key: String, value: String) {
        val computedKey = listOf(scope.rawValue, key, jid).prp()
        saveItem(computedKey, value)
    }

    fun saveItem(jid: String, scope: KeyScope, key: String, value: Boolean) {
        val computedKey = listOf(scope.rawValue, key, jid).prp()
        saveItem(computedKey, value)
    }

    fun saveItem(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).apply()
    }

    fun saveItem(key: String, string: String) {
        sharedPreferences.edit().putString(key, string).apply()
        if (chatSettings.childs.isEmpty()) {
            loadSettings()
        } else {
            updateValue(key, string)
        }
    }

    fun saveItem(key: String, bool: Boolean) {
        sharedPreferences.edit().putBoolean(key, bool).apply()
        if (chatSettings.childs.isEmpty()) {
            loadSettings()
        } else {
            updateValue(key, bool)
        }
    }

    fun saveClientSynchronizationVersion(jid: String, version: String) {
        saveItem(jid, KeyScope.CLIENT_SYNCHRONIZATION, "version", version)
    }

    fun removeItem(jid: String, scope: KeyScope, key: String) {
        val computedKey = listOf(scope.rawValue, key, jid).prp()
        sharedPreferences.edit().remove(computedKey).apply()
    }

    fun getKey(jid: String, scope: KeyScope, key: String): String? {
        val computedKey = listOf(scope.rawValue, key, jid).prp()
        return sharedPreferences.getString(computedKey, null)
    }

    fun getInt(jid: String, scope: KeyScope, key: String): Int {
        val computedKey = listOf(scope.rawValue, key, jid).prp()
        return sharedPreferences.getInt(computedKey, 0)
    }

    fun getKeyBool(jid: String, scope: KeyScope, key: String): Boolean? {
        val computedKey = listOf(scope.rawValue, key, jid).prp()
        return if (sharedPreferences.contains(computedKey)) {
            sharedPreferences.getBoolean(computedKey, false)
        } else {
            null
        }
    }

    fun get(boolKey: String): Boolean {
        return sharedPreferences.getBoolean(boolKey, false)
    }

    fun getString(key: String): String? {
        return sharedPreferences.getString(key, null)
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

    // Localization function updated with new string resources
    private fun localizeString(id: String, defaultValue: String): String {
        return when (id) {
            "account_settings_chat" -> XabberApplication.applicationContext().getString(R.string.account_settings_chat) ?: defaultValue
            "account_settings_background" -> XabberApplication.applicationContext().getString(R.string.account_settings_background) ?: defaultValue
            "account_settings_choose_background" -> XabberApplication.applicationContext().getString(R.string.account_settings_choose_background) ?: defaultValue
            "account_settings_display" -> XabberApplication.applicationContext().getString(R.string.account_settings_display) ?: defaultValue
            "account_settings_show_background" -> XabberApplication.applicationContext().getString(R.string.account_settings_show_background) ?: defaultValue
            "account_settings_chat_display_settings" -> XabberApplication.applicationContext().getString(R.string.account_settings_chat_display_settings) ?: defaultValue
            "account_settings_messaging" -> XabberApplication.applicationContext().getString(R.string.account_settings_messaging) ?: defaultValue
            "account_settings_send_by_enter" -> XabberApplication.applicationContext().getString(R.string.account_settings_send_by_enter) ?: defaultValue
            "account_settings_message_sending_options" -> XabberApplication.applicationContext().getString(R.string.account_settings_message_sending_options) ?: defaultValue
            "account_settings_contact_list" -> XabberApplication.applicationContext().getString(R.string.account_settings_contact_list) ?: defaultValue
            "account_settings_display_options" -> XabberApplication.applicationContext().getString(R.string.account_settings_display_options) ?: defaultValue
            "account_settings_offline_contacts" -> XabberApplication.applicationContext().getString(R.string.account_settings_offline_contacts) ?: defaultValue
            "account_settings_show_avatars" -> XabberApplication.applicationContext().getString(R.string.account_settings_show_avatars) ?: defaultValue
            "account_settings_show_circles" -> XabberApplication.applicationContext().getString(R.string.account_settings_show_circles) ?: defaultValue
            "account_settings_notificaions" -> XabberApplication.applicationContext().getString(R.string.account_settings_notificaions) ?: defaultValue
            "account_settings_in_app_notifications" -> XabberApplication.applicationContext().getString(R.string.account_settings_in_app_notifications) ?: defaultValue
            "account_settings_chat_message_preview" -> XabberApplication.applicationContext().getString(R.string.account_settings_chat_message_preview) ?: defaultValue
            "account_settings_in_app_sounds" -> XabberApplication.applicationContext().getString(R.string.account_settings_in_app_sounds) ?: defaultValue
            "account_settings_chat_notifications" -> XabberApplication.applicationContext().getString(R.string.account_settings_chat_notifications) ?: defaultValue
            "account_settings_chat_sound" -> XabberApplication.applicationContext().getString(R.string.account_settings_chat_sound) ?: defaultValue
            "account_settings_chat_vibration" -> XabberApplication.applicationContext().getString(R.string.account_settings_chat_vibration) ?: defaultValue
            "account_settings_chat_show_previews" -> XabberApplication.applicationContext().getString(R.string.account_settings_chat_show_previews) ?: defaultValue
            "account_settings_groupchat_notifications" -> XabberApplication.applicationContext().getString(R.string.account_settings_groupchat_notifications) ?: defaultValue
            "account_settings_groupchat_sound" -> XabberApplication.applicationContext().getString(R.string.account_settings_groupchat_sound) ?: defaultValue
            "account_settings_groupchat_vibration" -> XabberApplication.applicationContext().getString(R.string.account_settings_groupchat_vibration) ?: defaultValue
            "account_settings_groupchat_show_previews" -> XabberApplication.applicationContext().getString(R.string.account_settings_groupchat_show_previews) ?: defaultValue
            "account_settings_chat_notifications_manage" -> XabberApplication.applicationContext().getString(R.string.account_settings_chat_notifications_manage) ?: defaultValue
            "account_settings_groupchat_notifications_manage" -> XabberApplication.applicationContext().getString(R.string.account_settings_groupchat_notifications_manage) ?: defaultValue
            "account_settings_privacy" -> XabberApplication.applicationContext().getString(R.string.account_settings_privacy) ?: defaultValue
            "account_settings_privacy_settings" -> XabberApplication.applicationContext().getString(R.string.account_settings_privacy_settings) ?: defaultValue
            "account_settings_typing_notification" -> XabberApplication.applicationContext().getString(R.string.account_settings_typing_notification) ?: defaultValue
            "account_settings_privacy_level" -> XabberApplication.applicationContext().getString(R.string.account_settings_privacy_level) ?: defaultValue
            "settings_choose_language" -> XabberApplication.applicationContext().getString(R.string.settings_choose_language) ?: defaultValue
            "account_settings_developer" -> XabberApplication.applicationContext().getString(R.string.account_settings_developer) ?: defaultValue
            "account_settings_developer_mode" -> XabberApplication.applicationContext().getString(R.string.account_settings_developer_mode) ?: defaultValue
            "account_settings_write_log" -> XabberApplication.applicationContext().getString(R.string.account_settings_write_log) ?: defaultValue
            else -> defaultValue
        }
    }
}