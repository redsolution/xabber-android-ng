package com.xabber.xmpp.messages.message

import com.xabber.utils.prp
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import androidx.core.content.ContextCompat
import com.xabber.R
import com.xabber.xmpp.messages.MessageStorageItem
import com.xabber.xmpp.roster.RosterStorageItem
import java.util.Date


open class MessageForwardsInlineStorageItem : RealmObject {
    data class Model(
        val messageId: String,
        val parentId: String,
        val owner: String,
        val jid: String,
        val kindRaw: String,
        val body: String,
        val forwardJid: String,
        val forwardNickname: String,
        val isOutgoing: Boolean,
        val originalDate: Date?,
        val subforwards: List<Model>,
        val references: List<MessageReferenceStorageItem.Model>
    ) {
        val kind: Kind
            get() = Kind.fromRaw(kindRaw) ?: Kind.TEXT

        val displayedBody: String
            get() = when (kind) {
                Kind.TEXT, Kind.QUOTE -> body
                Kind.FILES -> {
                    val count = references.count { it.kind != MessageReferenceStorageItem.Kind.GROUPCHAT }
                    if (count == 1) {
                        references.firstOrNull { it.kind != MessageReferenceStorageItem.Kind.GROUPCHAT }?.sizeInBytes?.let {
                            context.getString(R.string.chat_message_file_count, it)
                        } ?: context.getString(R.string.chat_message_file)
                    } else {
                        context.getString(R.string.chat_message_attached_files, count.toString())
                    }
                }
                Kind.IMAGES -> {
                    val count = references.count { it.kind != MessageReferenceStorageItem.Kind.GROUPCHAT }
                    if (count == 1) {
                        references.firstOrNull { it.kind != MessageReferenceStorageItem.Kind.GROUPCHAT }?.sizeInBytes?.let {
                            context.getString(R.string.chat_message_image_count, it)
                        } ?: context.getString(R.string.chat_message_image)
                    } else {
                        context.getString(R.string.chat_message_attached_images, count.toString())
                    }
                }
                Kind.VIDEOS -> {
                    val count = references.count { it.kind != MessageReferenceStorageItem.Kind.GROUPCHAT }
                    if (count == 1) {
                        references.firstOrNull { it.kind != MessageReferenceStorageItem.Kind.GROUPCHAT }?.sizeInBytesRaw?.let {
                            context.getString(R.string.chat_message_video_count, it.toString())
                        } ?: context.getString(R.string.chat_message_video)
                    } else {
                        context.getString(R.string.chat_messages_attached_videos, count.toString())
                    }
                }
                Kind.VOICE -> {
                    references.firstOrNull { it.kind == MessageReferenceStorageItem.Kind.VOICE }?.metadata?.get("duration")?.let { duration ->
                        duration as? Double?.let {
                            val minutes = (it / 60).toInt()
                            val seconds = (it % 60).toInt()
                            context.getString(R.string.chat_message_voice_duration, "$minutes:${seconds.toString().padStart(2, '0')}")
                        }
                    } ?: context.getString(R.string.chat_message_voice)
                }
            }

        val attributedGroupAuthor: SpannableString
            get() {
                val author = groupchatAuthorNickname ?: if (isOutgoing) {
                    AccountManager.find(owner)?.username ?: owner
                } else if (forwardNickname.isNotEmpty()) {
                    forwardNickname
                } else {
                    forwardJid
                }
                return ContactChatMetadataManager.get(
                    author,
                    owner,
                    groupchatAuthorBadge ?: "",
                    groupchatMetadata?.get("role") as? String ?: "member"
                ).getAttributedNickname(mapOf("font" to Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)))
            }

        val attributedAuthor: SpannableString
            get() {
                val author = groupchatAuthorNickname ?: if (isOutgoing) {
                    AccountManager.find(owner)?.username ?: owner
                } else if (forwardNickname.isNotEmpty()) {
                    forwardNickname
                } else {
                    forwardJid
                }
                return ContactChatMetadataManager.get(
                    author,
                    owner,
                    groupchatAuthorBadge ?: "",
                    groupchatMetadata?.get("role") as? String ?: "member"
                ).getAttributedNickname(mapOf("font" to Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)))
            }

        val forwardedBody: SpannableString
            get() {
                val text = if (subforwards.size <= 1) {
                    context.getString(R.string.chat_message_forwarded_message)
                } else {
                    context.getString(R.string.chat_message_some_forwarded_messages, subforwards.size.toString())
                }
                val formattedBody = SpannableString(text)
                val range = IntRange(0, formattedBody.length)
                formattedBody.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.blue_700)),
                    range.first,
                    range.last,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                formattedBody.setSpan(
                    TypefaceSpan(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)),
                    range.first,
                    range.last,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                formattedBody.setSpan(
                    UnderlineSpan(),
                    range.first,
                    range.last,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                return formattedBody
            }

        val attributedBody: SpannableString
            get() = applyReferences(mapOf("font" to Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)))

        val attributedQuotes: List<MessageStorageItem.QuoteBodyItem>
            get() = quoteBody(mapOf("font" to Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)))



        val groupchatMetadata: Map<String, Any>?
            get() = references.firstOrNull { it.kind == MessageReferenceStorageItem.Kind.GROUPCHAT }?.metadata

        val groupchatAuthorJid: String?
            get() = groupchatMetadata?.get("jid") as? String

        val groupchatAuthorNickname: String?
            get() = groupchatMetadata?.get("nickname") as? String ?: groupchatMetadata?.get("jid") as? String

        val groupchatAuthorBadge: String?
            get() = groupchatMetadata?.get("badge") as? String ?: (groupchatMetadata?.get("role") as? String)?.replaceFirstChar { it.uppercase() }

        val groupchatUserAvatarPath: String?
            get() = groupchatMetadata?.get("id")?.let { avatarId ->
                listOf(avatarId as String, jid).prp()
            }

        // Placeholder for context, required for localized strings
        private lateinit var context: Context

        fun setContext(context: Context) {
            this.context = context
        }
    }

    enum class Kind(val rawValue: String) {
        TEXT("text"),
        IMAGES("images"),
        VIDEOS("videos"),
        FILES("files"),
        VOICE("voice"),
        QUOTE("quote");

        companion object {
            fun fromRaw(raw: String): Kind? =
                values().find { it.rawValue == raw }
        }
    }

    companion object {

        fun indexedProperties(): List<String> = listOf("messageId")

        fun ignoredProperties(): List<String> = listOf(
            "canCheckRealmAccessedLinks",
            "model"
        )
    }

    @Ignore
    var model: Model? = null



    @Index
    var messageId: String = ""

    var owner: String = ""
    var jid: String = ""
    var kindRaw: String = Kind.TEXT.rawValue
    var parentId: String = ""
    var body: String = ""
    var forwardJid: String = ""
    var forwardNickname: String = ""
    var isOutgoing: Boolean = false
    var originalDate: Date? = null
    var rosterItem: RosterStorageItem? = null
    var subforwards: RealmList<MessageForwardsInlineStorageItem> = realmListOf()
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()

    @Ignore
    var canCheckRealmAccessedLinks: Boolean = true

    var kind: Kind
        get() = Kind.fromRaw(kindRaw) ?: Kind.TEXT
        set(newValue) {
            kindRaw = newValue.rawValue
        }


}
