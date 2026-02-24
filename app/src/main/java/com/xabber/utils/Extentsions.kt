package com.xabber.utils

import android.app.Activity
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.AccountDto
import com.xabber.dto.AvatarDto
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiTypeDto
import com.xabber.xmpp.groupchat.GroupChatStorageItem
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.XMLElement
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.ResultsChange
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import nl.adaptivity.xmlutil.core.impl.multiplatform.StringReader
import nl.adaptivity.xmlutil.serialization.structure.PolymorphicMode
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.DateTimeException
import java.time.Instant
import java.time.Year
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Existing Fragment and Activity extensions (unchanged)
fun Fragment.showToast(message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun Fragment.showToast(message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun Fragment.isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        activity as AppCompatActivity,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}

fun Fragment.askUserForOpeningAppSettings() {
    val appSettingsIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", requireActivity().packageName, null)
    )
    if (requireActivity().packageManager.resolveActivity(
            appSettingsIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        ) != null
    ) {
        val dialog = AlertDialog.Builder(requireContext())
        dialog.setTitle(R.string.dialog_title_permission_denied)
            .setMessage(R.string.offer_to_open_settings)
            .setPositiveButton(R.string.dialog_button_open) { _, _ ->
                startActivity(appSettingsIntent)
            }
            .setNegativeButton(R.string.dialog_button_cancel) { dialogInterface: DialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()
            .show()
    }
}

fun Fragment.setFragmentResultListener(
    requestKey: String,
    listener: ((resultKey: String, bundle: Bundle) -> Unit)
) {
    parentFragmentManager.setFragmentResultListener(requestKey, this, listener)
}

fun Fragment.setFragmentResult(
    requestKey: String,
    result: Bundle
) = parentFragmentManager.setFragmentResult(requestKey, result)

fun List<EmojiTypeDto>.toMap(): Map<String, List<List<String>>> {
    return associate { it.name to it.list }
}

fun AppCompatActivity.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun AppCompatActivity.showToast(message: Int) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Activity.lockScreenRotation(isLock: Boolean) {
    requestedOrientation =
        if (isLock) {
            val display: Display? = if (SDK_INT >= Build.VERSION_CODES.R) {
                this.display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            var rotation = 0
            when (display?.rotation) {
                Surface.ROTATION_0 -> rotation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                Surface.ROTATION_90 -> rotation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                Surface.ROTATION_180 -> rotation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                Surface.ROTATION_270 -> rotation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            }
            rotation
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
}

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

val Int.px: Float
    get() = ((this - 0.5f) / Resources.getSystem().displayMetrics.density)

fun AppCompatActivity.hideSoftKeyboard(view: View) {
    val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

fun Drawable.getBitmap(): Bitmap {
    val bitmap: Bitmap = Bitmap.createBitmap(
        intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

fun spToPxFloat(sp: Float, context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        sp,
        context.resources.displayMetrics
    )
}

fun RecyclerView.partSmoothScrollToPosition(targetItem: Int) {
    layoutManager?.apply {
        val maxScroll = 6
        when (this) {
            is LinearLayoutManager -> {
                val topItem = findFirstVisibleItemPosition()
                val distance = topItem - targetItem
                val anchorItem = when {
                    distance > maxScroll -> targetItem + maxScroll
                    distance < maxScroll -> targetItem - maxScroll
                    else -> topItem
                }
                if (anchorItem != topItem) scrollToPosition(anchorItem)
                post {
                    smoothScrollToPosition(targetItem)
                }
            }
            else -> smoothScrollToPosition(targetItem)
        }
    }
}

inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelable(key) as? T
}

// Existing Mapping Extensions (unchanged)
fun LastChatsStorageItem.toChatListDto(): ChatListDto =
    ChatListDto(
        id = primary,
        owner = owner,
        opponentJid = jid,
        opponentNickname = "",
        customNickname = rosterItem?.customNickname ?: "",
        lastMessageBody = when {
            lastMessage == null -> ""
            lastMessage!!.body.isNotEmpty() -> lastMessage!!.body
            lastMessage!!.references.isNotEmpty() -> when {
                lastMessage!!.references[0].isAudioMessage -> "Voice message"
                lastMessage!!.references[0].isGeo -> "Location"
                else -> "${lastMessage!!.references[0].fileName}"
            }
            else -> ""
        },
        lastMessageDate = if (lastMessage == null || draftMessage != null) messageDate else lastMessage!!.sentDate,  // ← Both ms now
        lastMessageState = lastMessage?.state ?: MessageSendingState.None,
        isArchived = isArchived,
        isSynced = isSynced,
        draftMessage = draftMessage,
        hasAttachment = false,
        isSystemMessage = false,
        isMentioned = false,
        muteExpired = muteExpired,
        pinnedDate = pinnedPosition,
        status = ResourceStatus.ONLINE,
        entity = RosterItemEntity.CONTACT,
        unread = if (unread <= 0) "" else unread.toString(),
        lastPosition = lastPosition,
        drawableId = avatar,
        isHide = false,
        lastMessageIsOutgoing = lastMessage?.outgoing ?: false,
        isGroup = conversationType_ == ConversationType.Group.rawValue ||
                conversationType_ == ConversationType.Incognito.rawValue ||
                conversationType_ == ConversationType.Private.rawValue
    )

fun com.xabber.data_base.models.account.AccountStorageItem.toAccountDto() =
    AccountDto(
        id = primary,
        jid = jid,
        order = order,
        nickname = username,
        enabled = enabled,
        colorKey = colorKey,
        hasAvatar = hasAvatar
    )

fun com.xabber.data_base.models.avatar.AvatarStorageItem.toAvatarDto() =
    AvatarDto(
        id = primary,
        owner = owner,
        jid = jid,
        uploadUrl = uploadUrl,
        fileUri = fileUri,
        image96 = image96,
        image128 = image128,
        image192 = image192,
        image384 = image384,
        image512 = image512
    )

fun MessageReferenceStorageItem.toMessageReferenceDto() =
    MessageReferenceDto(
        id = primary,
        uri = uri,
        mimeType = mimeType,
        size = fileSize,
        fileName = fileName,
        isGeo = isGeo,
        latitude = latitude,
        longitude = longitude,
        isVoiceMessage = isAudioMessage
    )

fun List<String>.prp(): String {
    return joinToString(separator = "_")
}

fun Array<String>.prp(): String {
    return joinToString(separator = "_")
}

fun <T> List<T>.chunked(size: Int): List<List<T>> {
    require(size > 0) { "Chunk size must be positive, was $size" }
    return (0 until this.size step size).map { start ->
        subList(start, minOf(start + size, this.size))
    }
}

fun <T> Array<T>.chunked(size: Int): List<Array<T>> {
    require(size > 0) { "Chunk size must be positive, was $size" }
    return (0 until this.size step size).map { start ->
        sliceArray(start until minOf(start + size, this.size))
    }
}

fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    for (key in keys()) {
        map[key] = get(key)
    }
    return map
}

@RequiresApi(Build.VERSION_CODES.O)
fun parseTimestamp(message: XMPPMessage, owner: String, tag: String = "TimestampParser"): Long {
    // 1. Runtime / Carbons — ищем <time by=owner> прямо в сообщении
//    Log.w(tag, "ANALYSYS OF TIMESTAMP OF MESSAGE $message")
    message.element("time", "https://xabber.com/protocol/delivery")
        ?.takeIf { it.getAttribute("by") == owner }
        ?.getAttribute("stamp")
        ?.let { stamp ->
            stamp.parseXMPPDateToMillis()?.let { return it }
        }

    // 2. MAM — ищем внутри <result><forwarded><message><time by=owner>
    message.element("result", "urn:xmpp:mam:2")
        ?.element("forwarded", "urn:xmpp:forward:0")
        ?.element("message", "jabber:client")
        ?.element("time", "https://xabber.com/protocol/delivery")
        ?.takeIf { it.getAttribute("by") == owner }
        ?.getAttribute("stamp")
        ?.let { stamp ->
            stamp.parseXMPPDateToMillis()?.let { return it }
        }

    // 3. Fallback: <delay> (внутри forwarded или снаружи)
    val delayStamp = message.element("result")
        ?.element("forwarded")
        ?.element("delay", "urn:xmpp:delay")
        ?.getAttribute("stamp")
        ?: message.element("delay", "urn:xmpp:delay")?.getAttribute("stamp")

    if (delayStamp != null) {
        delayStamp.parseXMPPDateToMillis()?.let { return it }
    }

//    Log.w(tag, "No timestamp found in message, using current time as fallback")
    return System.currentTimeMillis()
}

@RequiresApi(Build.VERSION_CODES.O)
private fun tryParseStamp(stampStr: String, tag: String, source: String): Date {
    return try {
        val normalized = stampStr.replace(Regex("""\.(\d{3})\d+Z"""), ".$1Z")  // .551777Z → .551Z
        Log.d(tag, "Normalize $source: $stampStr → $normalized")
        val instant = Instant.parse(normalized)
        Date.from(instant)
    } catch (e: DateTimeParseException) {
        Log.w(tag, "Parse failed for $source ($stampStr): ${e.message}, fallback now")
        Date()  // Current time as last resort
    }
}

fun Date.toXMPPString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(this)
}

@RequiresApi(Build.VERSION_CODES.O)
fun String.parseXMPPDate(): Date? {
    return try {
        val normalized = this.replace(Regex("""\.(\d{3})\d+Z"""), ".$1Z")  // Truncate sub-ms
        val instant = Instant.parse(normalized)
        Date.from(instant)
    } catch (e: DateTimeParseException) {
        Log.w("DateParser", "Failed to parse XMPP date: $this, error=${e.message}")
        null
    }
}

// New XMPPMessage Extensions (converted from Swift)
fun XMPPMessage.getStanzaId(owner: String): String {
    val isGroupchat = this.element("x", namespace = "https://xabber.com/protocol/groups") != null
    var resultId: String? = null

    if (!isGroupchat) {
        val received = this.element("received", namespace = "https://xabber.com/protocol/delivery")
        if (received != null) {
            val stanzaIds = received.elements("stanza-id")
            if (stanzaIds.size == 1) {
                resultId = stanzaIds.first().getAttribute("id")
            } else {
                stanzaIds.forEach { stanzaId ->
                    val by = stanzaId.getAttribute("by")
                    if (by == owner) {
                        resultId = stanzaId.getAttribute("id")
                    }
                }
            }
            if (resultId?.isNotEmpty() == true) {
                return resultId!!
            }
        }
    }

    var ids = this.elements("stanza-id")
    if (ids.isEmpty()) {
        ids = this.elements("archived")
    }
    if (ids.size == 1) {
        resultId = ids.first().getAttribute("id")
    } else if (ids.size > 1) {
        ids.forEach { element ->
            if (isGroupchat) {
                val from = this.from?.bare()
                val by = element.getAttribute("by")
                if (from != null && by == from) {
                    resultId = element.getAttribute("id")
                }
            } else {
                val by = element.getAttribute("by")
                if (by == owner) {
                    resultId = element.getAttribute("id")
                }
            }
        }
    }
    return resultId ?: ""
}

fun XMPPMessage.getOriginId(): String? {
    return this.element("origin-id")?.getAttribute("id") ?: this.id
}

fun XMPPMessage.getStanzaIdAuthor(): String? {
    return this.element("stanza-id")?.getAttribute("by")
}

fun XMPPMessage.getMAMQueryId(): String? {
    return this.element("result")?.getAttribute("queryid")
}

fun XMPPMessage.getPreviousId(): String? {
    return this.element("previous-id", namespace = "http://xabber.com/protocol/previous")?.getAttribute("id")
}

fun XMPPMessage.getUniqueMessageId(owner: String): String {
    var id = this.getStanzaId(owner)
    this.id?.let { id = it }
    this.getOriginId()?.let { id = it }
    return id
}

fun XMPPMessage.getArchivedMessageContainer(): XMPPMessage? {
    val container = this.element("result")?.element("forwarded")?.element("message")
    return container?.let { XMPPMessage(raw = it.raw, children = this.children) }
}

fun XMPPMessage.getCarbonCopyMessageContainer(): XMPPMessage? {
    val from = this.from?.bare() ?: return null
    val to = this.to?.bare() ?: return null
    if (from != to) return null
    val container = this.element("sent")?.element("forwarded")?.element("message")
    return container?.let { XMPPMessage(raw = it.raw, children = this.children) }
}

fun XMPPMessage.getCarbonForwardedMessageContainer(): XMPPMessage? {
    val from = this.from?.bare() ?: return null
    val to = this.to?.bare() ?: return null
    if (from != to) return null
    val container = this.element("received")?.element("forwarded")?.element("message")
    return container?.let { XMPPMessage(raw = it.raw, children = this.children) }
}

fun XMPPMessage.getForwardedMessage(): XMPPMessage? {
    val container = this.element("forwarded")?.element("message")
    return container?.let { XMPPMessage(raw = it.raw, children = this.children) }
}

fun XMPPMessage.isArchivedMessage(): Boolean {
    val namespace = this.element("result")?.namespace
    return namespace in listOf("urn:xmpp:mam:0", "urn:xmpp:mam:1", "urn:xmpp:mam:2", "urn:xmpp:mam:3")
}

fun XMPPMessage.isCarbonCopy(): Boolean {
    val namespace = this.element("sent")?.namespace
    return namespace in listOf("urn:xmpp:carbons:0", "urn:xmpp:carbons:1", "urn:xmpp:carbons:2")
}

fun XMPPMessage.isCarbonForwarded(): Boolean {
    val namespace = this.element("received")?.namespace
    return namespace in listOf("urn:xmpp:carbons:0", "urn:xmpp:carbons:1", "urn:xmpp:carbons:2")
}

fun XMPPMessage.isVoIPMessage(): Boolean {
    return this.element("propose") != null ||
            this.element("accept") != null ||
            this.element("reject") != null
}

fun XMPPMessage.isForwardedMessage(): Boolean {
    val forwarded = this.element("forwarded")?.namespace
    return forwarded == "urn:xmpp:forward:0"
}

fun XMPPMessage.isForwardedMessageOld(): Boolean {
    return this.elements("reference").any { it.getAttribute("type") == "forward" }
}

fun XMPPMessage.isModernForwardedMessage(): Boolean {
    return this.elements("reference").any { it.getAttribute("type") == "forward" }
}

fun XMPPMessage.getQueryId(): String? {
    return this.element("result")?.getAttribute("queryid")
}

@RequiresApi(Build.VERSION_CODES.O)
fun XMPPMessage.getDeliveryTime(owner: String): Date? {
    val timeElement = this.elements("time").firstOrNull {
        it.namespace == "https://xabber.com/protocol/delivery" && it.getAttribute("by") == owner
    }
    val dateString = timeElement?.getAttribute("stamp") ?: return null
    return tryParseDate(dateString, this.getOriginId() ?: this.id ?: "unknown", "delivery <time>")
}

@RequiresApi(Build.VERSION_CODES.O)
fun XMPPMessage.getDelayedDate(): Date? {
    var date: Date? = null
    val resultElement = this.element("result")
    if (resultElement != null) {
        val forwarded = resultElement.element("forwarded")
        if (forwarded != null) {
            val time = forwarded.element("time")?.getAttribute("stamp")
            if (time != null) {
                date = tryParseDate(time, this.getOriginId() ?: this.id ?: "unknown", "result forwarded <time>")
            }
            if (date == null) {
                val delay = forwarded.element("delay")?.getAttribute("stamp")
                if (delay != null) {
                    date = tryParseDate(delay, this.getOriginId() ?: this.id ?: "unknown", "result forwarded <delay>")
                }
            }
        }
    }
    if (date == null) {
        val delay = this.element("delay")?.getAttribute("stamp")
        if (delay != null) {
            date = tryParseDate(delay, this.getOriginId() ?: this.id ?: "unknown", "outer <delay>")
        }
    }
    return date
}

@RequiresApi(Build.VERSION_CODES.O)
fun XMPPMessage.getDeliveryDate(): Date? {
    var date: Date? = null
    val time = this.element("time")?.getAttribute("stamp")
    if (time != null) {
        date = tryParseDate(time, this.getOriginId() ?: this.id ?: "unknown", "outer <time>")
    }
    if (date == null) {
        val delay = this.element("delay")?.getAttribute("stamp")
        if (delay != null) {
            date = tryParseDate(delay, this.getOriginId() ?: this.id ?: "unknown", "outer <delay>")
        }
    }
    return date
}

@RequiresApi(Build.VERSION_CODES.O)
fun XMLElement.getDateFrom(): Date? {
    val dateString = this.element("delay")?.getAttribute("stamp") ?: return null
    return tryParseDate(dateString, null, "element <delay>")
}


fun XMPPMessage.conversationTypeByMessage(): ConversationType {
    if (this.element("x", namespace = "https://xabber.com/protocol/groups") != null ||
        this.element("x", namespace = "https://xabber.com/protocol/groups#system-message") != null) {
        return ConversationType.Group
    }
    val encrypted = this.element("encrypted")?.namespace
    if (encrypted != null) {
        return ConversationType.fromRaw(encrypted) ?: ConversationType.Regular // Assume CommonConfigManager equivalent exists
    }
    val owner = this.from?.bare()
    val to = this.to?.bare()
    if (owner != null && to == "favorites.redsolution.com") {
        return ConversationType.Favorites
    }
    return ConversationType.Regular
}

@RequiresApi(Build.VERSION_CODES.O)
private fun tryParseDate(stamp: String, messageId: String?, source: String): Date? {
    return stamp.parseXMPPDate().also {
        if (it == null) {
            Log.w("DateParser", "Failed to parse date ($source) for messageId=$messageId: $stamp")
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
fun String.parseXMPPDateToMillis(): Long? = try {
    // Нормализуем разные форматы времени
    val normalized = when {
        // Формат с Z на конце: 2025-09-18T07:54:08.153013Z
        endsWith("Z") -> this.replace(Regex("""\.(\d{3})\d{0,6}Z$"""), ".$1Z")

        // Формат с offset: 2024-11-28T06:06:25.626+00:00
        contains(Regex("""[+-]\d{2}:\d{2}$""")) -> {
            // Убираем миллисекунды после 3 цифр
            val withoutNanos = this.replace(Regex("""\.(\d{3})\d+([+-]\d{2}:\d{2})$"""), ".$1$2")
            // Если offset +00:00, заменяем на Z
            if (withoutNanos.endsWith("+00:00")) {
                withoutNanos.replace("+00:00", "Z")
            } else {
                withoutNanos
            }
        }

        // Формат без offset (предполагаем UTC)
        else -> this.replace(Regex("""\.(\d{3})\d+$"""), ".$1") + "Z"
    }

    Instant.parse(normalized).toEpochMilli()
} catch (e: Exception) {
    Log.w("DateParser", "Failed to parse timestamp: $this", e)
    null
}

fun List<ChatListDto>.applyAccountColors(): List<ChatListDto> {
    val realm = Realm.open(defaultRealmConfig())
    try {
        val accountMap = realm.query<com.xabber.data_base.models.account.AccountStorageItem>("enabled = true")
            .find()
            .associate { it.primary to it.colorKey }

        this.forEach { dto ->
            accountMap[dto.owner]?.let { dto.colorKey = it }
        }
    } finally {
        realm.close()
    }
    return this
}