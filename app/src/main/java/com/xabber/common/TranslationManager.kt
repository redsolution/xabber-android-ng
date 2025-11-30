package com.xabber.common

import android.content.Context
import androidx.core.os.bundleOf
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.xabber.R
import com.xabber.presentation.XabberApplication
import java.util.*

object TranslationManager {

    // ========================================================================
    // MARK: - Singleton
    // ========================================================================
    // В Kotlin object и так синглтон — идеально

    // ========================================================================
    // MARK: - Current Language
    // ========================================================================
    var currentLang: String? = null
        private set

    // ========================================================================
    // MARK: - All supported languages (аналог enum Languages)
    // ========================================================================
    enum class Languages(val displayName: String, val code: String) {
        EN("English", "en"),
        SQ("Shqip", "sq"),
        AR("عربي", "ar"),
        HY("Հայերէն", "hy"),
        AZ("Azərbaycan dili", "az"),
        BE("Беларуская мова", "be"),
        BS("Bosanski", "bs"),
        BG("Български", "bg"),
        CA("Català", "ca"),
        ZH("中國人", "zh"),
        ZH_HANS("简体中文", "zh-Hans"),
        HR("Hrvatski", "hr"),
        CS("Čeština", "cs"),
        DA("Dansk", "da"),
        NL("Nederlands", "nl"),
        ET("Eesti keel", "et"),
        FIL("Filipino", "fil"),
        FI("Suomi", "fi"),
        FR("Français", "fr"),
        KA("ქართული ენა", "ka"),
        DE("Deutsch", "de"),
        EL("ελληνικά", "el"),
        HI("हिन्दी", "hi"),
        HE("העברעאיש", "he"),
        HU("Magyar", "hu"),
        IS("Íslenska", "is"),
        GA("Gaeilge", "ga"),
        IT("Italiano", "it"),
        ID("Bahasa Indonesia", "id"),
        JA("日本語", "ja"),
        KO("한국어", "ko"),
        KU("کوردی", "ku"),
        TLH("Klingon", "tlh"),
        KY("Кыргыз тили", "ky"),
        LA("Lingua Latina", "la"),
        LT("Lietuvių kalba", "lt"),
        LB("Lëtzebuergesch", "lb"),
        MK("Македонски", "mk"),
        MS("മലയാളം", "ms"),
        MR("मराठी", "mr"),
        MN("Монгол", "mn"),
        NE("नेपाली", "ne"),
        NB("Bokmål", "nb"),
        NB_NO("Norsk", "nb_NO"),
        OC("Occitan", "oc"),
        FA("فارسی", "fa"),
        PL("Polski", "pl"),
        PT("Português", "pt"),
        PT_BR("Português do Brasil", "pt_BR"),
        PA("ਪੰਜਾਬੀ", "pa"),
        RO("Limba română", "ro"),
        RU("Русский язык", "ru"),
        SAT("ᱥᱟᱱᱛᱟᱲᱤ", "sat"),
        SCO("Scots Leid", "sco"),
        SR("Cрпски / srpski", "sr"),
        SI("සිංහල", "si"),
        SK("Slovenčina", "sk"),
        SL("Slovenščina", "sl"),
        ES("Español", "es"),
        SW("Kiswahili", "sw"),
        SV("Svenska", "sv"),
        TG("Тоҷикӣ", "tg"),
        TA("தமிழ்", "ta"),
        TE("తెలుగు", "te"),
        TR("Türkçe", "tr"),
        TK("Türkmen dili", "tk"),
        UK("Українська мова", "uk"),
        UZ("Ўзбек тили", "uz"),
        VI("Tiếng Việt", "vi"),
        CY("Cymraeg", "cy"),
        YO("Èdè Yorùbá", "yo"),
        ZU("isiZulu", "zu");

        companion object {
            fun fromCode(code: String): Languages? =
                values().find { it.code.equals(code, ignoreCase = true) }

            fun fromDisplayName(name: String): Languages? =
                values().find { it.displayName == name }
        }
    }

    // ========================================================================
    // MARK: - Init & Prepare
    // ========================================================================
    init {
        prepare()
    }

    fun prepare() {
        val saved = SettingManager.getKey("", SettingManager.KeyScope.LANGUAGES, "current_language")
        currentLang = if (saved == "Default" || saved.isNullOrBlank()) {
            "en"
        } else {
            saved
        }
//        applyLanguage()
    }

    // ========================================================================
    // MARK: - Save Language
    // ========================================================================
//    fun save(language: String) {
//        SettingManager.saveItem("", SettingManager.KeyScope.LANGUAGES, "current_language", language)
//        prepare() // перечитываем и применяем
//        NotificationCenter.default.post(name = NotificationName.NewLanguageSelected, object = null)
//    }

    // ========================================================================
    // MARK: - Apply to App (Android 13+ и ниже)
    // ========================================================================
    private fun applyLanguage() {
        val locale = when (currentLang) {
            "zh-Hans" -> Locale.SIMPLIFIED_CHINESE
            "zh" -> Locale.TRADITIONAL_CHINESE
            "pt_BR" -> Locale("pt", "BR")
            "nb_NO" -> Locale("nb", "NO")
            else -> Locale(currentLang ?: "en")
        }

        // Современный способ (Android 13+)
        val localeList = LocaleListCompat.create(locale)
        AppCompatDelegate.setApplicationLocales(localeList)

        // Для старых версий (на всякий случай)
        val config = XabberApplication.applicationContext().resources.configuration
        config.setLocale(locale)
        XabberApplication.applicationContext().createConfigurationContext(config)
    }

    // ========================================================================
    // MARK: - Helper: Convert display name → code
    // ========================================================================
    fun codeFromDisplayName(displayName: String): String {
        return Languages.fromDisplayName(displayName)?.code ?: "en"
    }

    // ========================================================================
    // MARK: - List for UI
    // ========================================================================
//    val allLanguages: List<String>
//        get() = listOf(XabberApplication.applicationContext().getString(R.string.default_language)) +
//                Languages.values().map { it.displayName }

    val allLanguageCodes: List<String>
        get() = listOf("Default") + Languages.values().map { it.code }
}

// ========================================================================
// MARK: - Notification (аналог Notification.Name)
// ========================================================================
object NotificationName {
    const val NewLanguageSelected = "NewLanguageSelected"
}

// ========================================================================
// MARK: - NotificationCenter (мини-версия)
// ========================================================================
object NotificationCenter {
    private val observers = mutableMapOf<String, MutableList<(Any?) -> Unit>>()

    fun addObserver(name: String, action: (Any?) -> Unit) {
        observers.getOrPut(name) { mutableListOf() }.add(action)
    }

    fun removeObserver(name: String, action: (Any?) -> Unit) {
        observers[name]?.remove(action)
    }

    fun post(name: String, objectData: Any? = null) {
        observers[name]?.forEach { it(objectData) }
    }

    // Удобный extension
    fun post(name: String) = post(name, null)
}