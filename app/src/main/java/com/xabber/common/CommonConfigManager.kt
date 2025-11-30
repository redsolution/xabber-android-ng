package com.xabber.common

import android.graphics.Typeface
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.InputStreamReader
import android.content.res.AssetManager
import com.xabber.presentation.XabberApplication

object CommonConfigManager {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class CommonConfig(
        val locked_conversation_type: String = "regular",
        val allowed_hosts: List<String> = emptyList(),
        val avatar_masks: List<String> = emptyList(),
        val locked_avatar_mask: String = "",
        val supports_multiaccounts: Boolean = true,
        val required_touch_id_or_password: Boolean = false,
        val onboarding_subtitle_text: String = "",
        val app_name: String = "Xabber",
        val bundle_id: String = "",
        val push_bundle_id: String = "",
        val domain: String = "",
        val allow_registration: Boolean = true,
        val locked_host: String = "",
        val support_calls: Boolean = true,
        val support_groupchats: Boolean = true,
        val allow_conversations_from_all_hosts: Boolean = true,
        val application_color: String = "#5E45A8",
        val launch_screen_color: String = "#5E45DY",
        val required_time_signature_for_messages: Boolean = false,
        val time_signature_for_messages_period: Int = 0,
        val motivating: Boolean = false,
        val use_file_enryption_by_default: Boolean = true,
        val support_jid: String = "",
        val should_block_application_when_subscribtion_end: Boolean = false,
        val use_yubikey: Boolean = false,
        val afterburn_at_default: Boolean = false,
        val afterburn_default_interval: Int = 0,
        val server_registration_url: String = "",
        val show_server_features: Boolean = true,
        val blur_screen_when_enter_background: Boolean = true,
        val support_subscribtions: Boolean = true,
        val show_text_logo: Boolean = true,
        val default_privacy_level: String = "server",
        val auto_delete_messages_interval: Int = 0,
        val locked_account_color: String = "",
        val locked_background: String = "",
        val skip_vcard_nickname_onboarding_step: Boolean = false,
        val interface_type: String = "split",
        val symbol_weight: String = "regular",
        val chat_avatar_size: Int = 40,
        val use_large_title: Boolean = true
    )

    enum class InterfaceType { TABS, SPLIT }

    enum class SymbolWeight(val weight: Int) {
        ULTRA_LIGHT(Typeface.BOLD), // подбираем ближайшие
        THIN(Typeface.NORMAL),
        LIGHT(Typeface.NORMAL),
        REGULAR(Typeface.NORMAL),
        MEDIUM(Typeface.BOLD),
        SEMIBOLD(Typeface.BOLD),
        BOLD(Typeface.BOLD),
        HEAVY(Typeface.BOLD),
        BLACK(Typeface.BOLD);

        companion object {
            fun from(raw: String): SymbolWeight =
                values().find { it.name.equals(raw, ignoreCase = true) } ?: REGULAR
        }
    }
//
//    val config: CommonConfig
//    val messageStyleConfig: MessageStyleConfig

//    val interfaceType: InterfaceType
//        get() = InterfaceType.valueOf(config.interface_type.uppercase())
//
//    val symbolWeight: SymbolWeight
//        get() = SymbolWeight.from(config.symbol_weight)

    init {
//        config = loadCommonConfig()
//        messageStyleConfig = loadMessageStyleConfig()
    }

    private fun loadCommonConfig(): CommonConfig {
        return try {
            val assets: AssetManager = XabberApplication.applicationContext().assets
            assets.open("common_config.plist").use { inputStream ->
                val reader = InputStreamReader(inputStream)
                val plistContent = reader.readText()
                // Простой парсинг XML Plist → JSON-подобный map (можно улучшить с XmlPullParser)
                val map = parsePlistToMap(plistContent)
                // Конвертируем в объект через рефлексию или вручную
                // Для простоты — используем JSON-аналог, если plist в формате JSON-like
                // Лучше перевести common_config.plist в JSON и читать через kotlinx.serialization
                // Пока — заглушка с дефолтами + ручной парсинг
                CommonConfig() // Замени на настоящий парсер
            }
        } catch (e: Exception) {
            e.printStackTrace()
            CommonConfig() // fallback
        }
    }

    private fun loadMessageStyleConfig(): MessageStyleConfig {
        return try {
            val assets = XabberApplication.applicationContext().assets
            assets.open("bubblecorners.json").use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString<MessageStyleConfig>(jsonString)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MessageStyleConfig() // fallback
        }
    }

    // Временный парсер Plist → Map (можно заменить на полноценный Plist парсер)
    private fun parsePlistToMap(plist: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        // Очень упрощённый парсер — лучше использовать библиотеку или конвертировать в JSON
        // Пока возвращаем пустую карту → все значения будут дефолтными
        return result
    }
}

// =============================================
// MARK: - MessageStyleConfig (полный аналог Swift)
// =============================================

@Serializable
data class MessageStyleConfig(
    val message_bubbles: MessageBubbleContainer = MessageBubbleContainer(),
    val containers: Containers = Containers()
) {
    @Serializable
    data class Radius(val items: List<Float> = listOf(0f, 0f, 0f, 0f)) {
        companion object {
            fun fromInts(ints: List<Int>): Radius =
                Radius(ints.map { it.toFloat() })
        }
        val leftUpper: Float get() = items.getOrNull(0) ?: 0f
        val rightUpper: Float get() = items.getOrNull(1) ?: 0f
        val leftBottom: Float get() = items.getOrNull(2) ?: 0f
        val rightBottom: Float get() = items.getOrNull(3) ?: 0f
    }

    @Serializable
    data class MessageRadius(
        val r0: List<Int> = emptyList(),
        val r1: List<Int> = emptyList(),
        val r2: List<Int> = emptyList(),
        val r3: List<Int> = emptyList(),
        val r4: List<Int> = emptyList(),
        val r5: List<Int> = emptyList(),
        val r6: List<Int> = emptyList(),
        val r7: List<Int> = emptyList(),
        val r8: List<Int> = emptyList(),
        val r9: List<Int> = emptyList(),
        val r10: List<Int> = emptyList(),
        val r11: List<Int> = emptyList(),
        val r12: List<Int> = emptyList(),
        val r13: List<Int> = emptyList(),
        val r14: List<Int> = emptyList(),
        val r15: List<Int> = emptyList(),
        val r16: List<Int> = emptyList()
    ) {
        fun getRadiusFor(index: String): Radius = when (index) {
            "0" -> Radius.fromInts(r0)
            "1" -> Radius.fromInts(r1)
            "2" -> Radius.fromInts(r2)
            "3" -> Radius.fromInts(r3)
            "4" -> Radius.fromInts(r4)
            "5" -> Radius.fromInts(r5)
            "6" -> Radius.fromInts(r6)
            "7" -> Radius.fromInts(r7)
            "8" -> Radius.fromInts(r8)
            "9" -> Radius.fromInts(r9)
            "10" -> Radius.fromInts(r10)
            "11" -> Radius.fromInts(r11)
            "12" -> Radius.fromInts(r12)
            "13" -> Radius.fromInts(r13)
            "14" -> Radius.fromInts(r14)
            "15" -> Radius.fromInts(r15)
            "16" -> Radius.fromInts(r16)
            else -> Radius.fromInts(r0)
        }
    }

    @Serializable
    data class MessageBubble(
        val image: ImageRadius = ImageRadius(),
        val message: TextRadius = TextRadius()
    ) {
        @Serializable data class ImageRadius(
            val bubble: MessageRadius = MessageRadius(),
            val image: MessageRadius = MessageRadius(),
            val timestamp: MessageRadius = MessageRadius()
        )
        @Serializable data class TextRadius(
            val bubble: MessageRadius = MessageRadius()
        )
    }

    @Serializable
    data class MessageBubbleContainer(
        val no_tail: MessageBubble = MessageBubble(),
        val smooth: MessageBubble = MessageBubble(),
        val bubble: MessageBubble = MessageBubble(),
        val bubbles: MessageBubble = MessageBubble(),
        val curvy: MessageBubble = MessageBubble(),
        val stripes: MessageBubble = MessageBubble(),
        val transparent: MessageBubble = MessageBubble(),
        val wedge: MessageBubble = MessageBubble()
    ) {
        companion object {
            fun verboseNames() = listOf(
                "No tail", "Smooth", "Bubble", "Bubbles",
                "Curvy", "Stripes", "Transparent", "Wedge"
            )

            fun keyFromVerbose(name: String): String = when (name) {
                "No tail" -> "no_tail"
                "Smooth" -> "smooth"
                "Bubble" -> "bubble"
                "Bubbles" -> "bubbles"
                "Curvy" -> "curvy"
                "Stripes" -> "stripes"
                "Transparent" -> "transparent"
                "Wedge" -> "wedge"
                else -> "no_tail"
            }
        }
    }

    @Serializable
    data class Containers(
        val container_l1: Container = Container(),
        val container_l2: Container = Container(),
        val container_l3: Container = Container()
    ) {
        @Serializable
        data class Container(
            val border: MessageRadius = MessageRadius(),
            val inner: MessageRadius = MessageRadius()
        )
    }
}