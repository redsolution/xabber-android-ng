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

//    data class Datasource(
//        var key: String = "",
//        var label: String = "",
//        var kind: DatasourceKind = DatasourceKind.TITLE,
//        var childs: List<Datasource> = emptyList(),
//        var values: List<String> = emptyList(),
//        var value: Any = ""
//    ) {
//        fun copy(
//            key: String = this.key,
//            label: String = this.label,
//            kind: DatasourceKind = this.kind,
//            childs: List<Datasource> = this.childs,
//            values: List<String> = this.values,
//            value: Any = this.value
//        ): Datasource {
//            return Datasource(key, label, kind, childs, values, value)
//        }
//    }

    val logEnabled: Boolean
        get() = sharedPreferences.getBoolean("developer_logEnabled", false)

//    var chatSettings: Datasource = Datasource()
//    var rosterSettings: Datasource = Datasource()
//    var languageSettings: Datasource = Datasource()
//    var notificationSettings: Datasource = Datasource()
//    var privacySettings: Datasource = Datasource()
//    var developerSettings: Datasource = Datasource()

//    init {
//        loadSettings()
//    }

//    fun loadSettings() {
//        writeDefault()
//        val dict = sharedPreferences.all
//        chatSettings = Datasource(
//            key = "chat",
//            label = localizeString("account_settings_chat", ""),
//            kind = DatasourceKind.TITLE,
//            childs = listOf(
//                Datasource(
//                    key = "chat_background",
//                    label = localizeString("account_settings_background", ""),
//                    kind = DatasourceKind.GROUP,
//                    childs = listOf(
//                        Datasource(
//                            key = "chat_chooseBackground",
//                            label = localizeString("account_settings_choose_background", ""),
//                            kind = DatasourceKind.SELECTOR,
//                            childs = emptyList(),
//                            values = listOf("Aliens", "Summer", "Honeycomb", "Cats", "Flowers", "Flowers-daisy", "Hearts"),
//                            value = dict["chat_chooseBackground"] ?: "Aliens"
//                        )
//                    ),
//                    values = emptyList(),
//                    value = ""
//                ),
//                Datasource(
//                    key = "chat_design",
//                    label = localizeString("account_settings_display", ""),
//                    kind = DatasourceKind.GROUP,
//                    childs = listOf(
//                        Datasource(
//                            key = "chat_showBackground",
//                            label = localizeString("account_settings_show_background", ""),
//                            kind = DatasourceKind.BOOL,
//                            childs = emptyList(),
//                            values = emptyList(),
//                            value = dict["chat_showBackground"] ?: true
//                        )
//                    ),
//                    values = emptyList(),
//                    value = localizeString("account_settings_chat_display_settings", "")
//                ),
//                Datasource(
//                    key = "chat_behaviour",
//                    label = localizeString("account_settings_messaging", ""),
//                    kind = DatasourceKind.GROUP,
//                    childs = listOf(
//                        Datasource(
//                            key = "chat_sendByEnter",
//                            label = localizeString("account_settings_send_by_enter", ""),
//                            kind = DatasourceKind.BOOL,
//                            childs = emptyList(),
//                            values = emptyList(),
//                            value = dict["chat_sendByEnter"] ?: false
//                        )
//                    ),
//                    values = emptyList(),
//                    value = localizeString("account_settings_message_sending_options", "")
//                )
//            ),
//            values = emptyList(),
//            value = false
//        )
//        rosterSettings = Datasource(
//            key = "roster",
//            label = localizeString("account_settings_contact_list", ""),
//            kind = DatasourceKind.TITLE,
//            childs = listOf(
//                Datasource(
//                    key = "roster_display",
//                    label = localizeString("account_settings_display_options", ""),
//                    kind = DatasourceKind.GROUP,
//                    childs = listOf(
//                        Datasource(
//                            key = "roster_showOfflineContacts",
//                            label = localizeString("account_settings_offline_contacts", ""),
//                            kind = DatasourceKind.BOOL,
//                            childs = emptyList(),
//                            values = emptyList(),
//                            value = dict["roster_showOfflineContacts"] ?: true
//                        ),
//                        Datasource(
//                            key = "roster_showAvatars",
//                            label = localizeString("account_settings_show_avatars", ""),
//                            kind = DatasourceKind.BOOL,
//                            childs = emptyList(),
//                            values = emptyList(),
//                            value = dict["roster_showAvatars"] ?: true
//                        ),
//                        Datasource(
//                            key = "roster_showGroups",
//                            label = localizeString("account_settings_show_circles", ""),
//                            kind = DatasourceKind.BOOL,
//                            childs = emptyList(),
//                            values = emptyList(),
//                            value = dict["roster_showGroups"] ?: true
//                        )
//                    ),
//                    values = emptyList(),
//                    value = ""
//                )
//            ),
//            values = emptyList(),
//            value = false
//        )
//        notificationSettings = Datasource(
//            key = "notification",
//            label = localizeString("account_settings_notificaions", ""),
//            kind = DatasourceKind.TITLE,
//            childs = listOf(
//                Datasource(
//                    key = "notification_in_app",
//                    label = localizeString("account_settings_in_app_notifications", ""),
//                    kind = DatasourceKind.GROUP,
//                    childs = listOf(
//                        Datasource(
//                            key = "notification_in_app_alert_last_chats",
//                            label = localizeString("account_settings_chat_message_preview", ""),
//                            kind = DatasourceKind.BOOL,
//                            childs = emptyList(),
//                            values = emptyList(),
//                            value = dict["notification_in_app_alert_last_chats"] ?: false
//                        ),
//                        Datasource(
//                            key = "notification_in_app_sound",
//                            label = localizeString("account_settings_in_app_sounds", ""),
//                            kind = DatasourceKind.BOOL,
//                            childs = emptyList(),
//                            values = emptyList(),
//                            value = dict["notification_in_app_sound"] ?: true
//                        )
//                    ),
//                    values = emptyList(),
//                    value = localizeString("account_settings_notification_application_settings", "")
//                )
//            ),
//            values = emptyList(),
//            value = false
//        )
//        privacySettings = Datasource(
//            key = "privacy",
//            label = localizeString("account_settings_privacy", ""),
//            kind = DatasourceKind.TITLE,
//            childs = listOf(
//                Datasource(
//                    key = "privacy",
//                    label = localizeString("account_settings_privacy_settings", ""),
//                    kind = DatasourceKind.GROUP,
//                    childs = listOf(
//                        Datasource(
//                            key = "privacy_textInputNotify",
//                            label = localizeString("account_settings_typing_notification", ""),
//                            kind = DatasourceKind.BOOL,
//                            childs = emptyList(),
//                            values = emptyList(),
//                            value = dict["privacy_textInputNotify"] ?: true
//                        )
//                    ),
//                    values = emptyList(),
//                    value = ""
//                )
//            ),
//            values = emptyList(),
//            value = false
//        )
//        languageSettings = Datasource(
//            key = "languages",
//            label = localizeString("settings_choose_language", ""),
//            kind = DatasourceKind.GROUP,
//            childs = listOf(
//                Datasource(
//                    key = "system_language",
//                    label = "Default",
//                    kind = DatasourceKind.SELECTOR,
//                    childs = emptyList(),
//                    values = listOf("Default") + TranslationsManager.languages.map { it.rawValue },
//                    value = TranslationsManager.currentLang ?: "Default"
//                )
//            ),
//            values = emptyList(),
//            value = ""
//        )
//        developerSettings = Datasource(
//            key = "developer",
//            label = localizeString("account_settings_developer", ""),
//            kind = DatasourceKind.TITLE,
//            childs = listOf(
//                Datasource(
//                    key = "developer",
//                    label = localizeString("account_settings_developer_mode", ""),
//                    kind = DatasourceKind.GROUP,
//                    childs = listOf(
//                        Datasource(
//                            key = "developer_logEnabled",
//                            label = localizeString("account_settings_write_log", ""),
//                            kind = DatasourceKind.BOOL,
//                            childs = emptyList(),
//                            values = emptyList(),
//                            value = dict["developer_logEnabled"] ?: true
//                        )
//                    ),
//                    values = emptyList(),
//                    value = ""
//                )
//            ),
//            values = emptyList(),
//            value = false
//        )
//    }

//    fun writeDefault() {
//        if (sharedPreferences.contains("default_settingsCached")) {
//            return
//        }
//
//        val defaultBackground = if (CommonConfigManager.config.lockedBackground.isNotEmpty()) {
//            CommonConfigManager.config.lockedBackground
//        } else {
//            "Flowers"
//        }
//
//        val defaults = mapOf(
//            "default_settingsCached" to true,
//            "chat_fontSize" to "regular",
//            "chat_showStatus" to true,
//            "chat_showBackground" to true,
//            "chat_sendByEnter" to false,
//            "chat_chooseBackground" to defaultBackground,
//            "chat_chooseBackgroundColor" to "purple",
//            "roster_sorting" to "by_status",
//            "roster_showAvatars" to true,
//            "roster_showOfflineContacts" to true,
//            "roster_showGroups" to true,
//            "roster_divideByAccounts" to true,
//            "roster_showEmptyGroups" to true,
//            "notification_in_app_alert_last_chats" to false,
//            "notification_in_app_sound" to true,
//            "notification_chat_sound" to true,
//            "notification_chat_vibration" to true,
//            "notification_chat_badge" to "full",
//            "notification_groupchat_sound" to true,
//            "notification_groupchat_vibration" to true,
//            "notification_groupchat_badge" to "full",
//            "privacy_textInputNotify" to true,
//            "privacy_checkServerCertificate" to true,
//            "developer_logEnabled" to false,
//            "avatar_masks_current_avatar_mask_" to "rounded",
//            "burn_messages_enabled" to true,
//            "burn_messages_timer" to 0
////            "privacy_level" to CommonConfigManager.config.defaultPrivacyLevel,
////            PrivacySettings.TYPING_NOTIFICATION.rawValue to true
//        )
//
//        with(sharedPreferences.edit()) {
//            defaults.forEach { (key, value) ->
//                when (value) {
//                    is Boolean -> putBoolean(key, value)
//                    is String -> putString(key, value)
//                    is Int -> putInt(key, value)
//                }
//            }
//            apply()
//        }
//    }

//    fun updateValue(key: String, value: Any) {
//        fun updateItem(settings: Datasource): Boolean {
//            for ((index, item) in settings.childs.withIndex()) {
//                if (item.key == key) {
//                    settings.childs[index].value = value
//                    return true
//                }
//                for ((childIndex, childItem) in item.childs.withIndex()) {
//                    if (childItem.key == key) {
//                        settings.childs[index].childs[childIndex].value = value
//                        return true
//                    }
//                }
//            }
//            return false
//        }
//
//        when {
//            updateItem(chatSettings) -> return
//            updateItem(rosterSettings) -> return
//            updateItem(notificationSettings) -> return
//            updateItem(languageSettings) -> return
//            updateItem(privacySettings) -> return
//            updateItem(developerSettings) -> return
//        }
//    }
//
//    fun getDatasource(key: String): Datasource? {
//        return when (key) {
//            "chat" -> chatSettings
//            "roster" -> rosterSettings
//            "languages" -> languageSettings
//            "notification" -> notificationSettings
//            "privacy" -> privacySettings
//            "developer" -> developerSettings
//            else -> null
//        }
//    }

//    fun saveItem(key: String, value: Int) {
//        sharedPreferences.edit().putInt(key, value).apply()
//        if (chatSettings.childs.isEmpty()) {
//            loadSettings()
//        } else {
//            updateValue(key, value)
//        }
//    }
//
//    fun saveItem(key: String, bool: Boolean) {
//        sharedPreferences.edit().putBoolean(key, bool).apply()
//        if (chatSettings.childs.isEmpty()) {
//            loadSettings()
//        } else {
//            updateValue(key, bool)
//        }
//    }
//
//    fun saveItem(key: String, string: String) {
//        sharedPreferences.edit().putString(key, string).apply()
//        if (chatSettings.childs.isEmpty()) {
//            loadSettings()
//        } else {
//            updateValue(key, string)
//        }
//    }

    fun removeItem(jid: String, scope: KeyScope, key: String) {
        val computedKey = listOf(scope.rawValue, key, jid).prp()
        sharedPreferences.edit().remove(computedKey).apply()
    }

//    fun saveItem(jid: String, scope: KeyScope, key: String, value: Int) {
//        val computedKey = listOf(scope.rawValue, key, jid).prp()
//        saveItem(computedKey, value)
//    }
//
//    fun saveItem(jid: String, scope: KeyScope, key: String, value: String) {
//        val computedKey = listOf(scope.rawValue, key, jid).prp()
//        saveItem(computedKey, value)
//    }
//
//    fun saveItem(jid: String, scope: KeyScope, key: String, value: Boolean) {
//        val computedKey = listOf(scope.rawValue, key, jid).prp()
//        saveItem(computedKey, value)
//    }

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

    // Placeholder for localization (replace with actual string resource access)
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
            "account_settings_privacy" -> XabberApplication.applicationContext().getString(R.string.account_settings_privacy) ?: defaultValue
            "account_settings_privacy_settings" -> XabberApplication.applicationContext().getString(R.string.account_settings_privacy_settings) ?: defaultValue
            "account_settings_typing_notification" -> XabberApplication.applicationContext().getString(R.string.account_settings_typing_notification) ?: defaultValue
            "settings_choose_language" -> XabberApplication.applicationContext().getString(R.string.settings_choose_language) ?: defaultValue
            "account_settings_developer" -> XabberApplication.applicationContext().getString(R.string.account_settings_developer) ?: defaultValue
            "account_settings_developer_mode" -> XabberApplication.applicationContext().getString(R.string.account_settings_developer_mode) ?: defaultValue
            "account_settings_write_log" -> XabberApplication.applicationContext().getString(R.string.account_settings_write_log) ?: defaultValue
            else -> defaultValue
        }
    }
}


