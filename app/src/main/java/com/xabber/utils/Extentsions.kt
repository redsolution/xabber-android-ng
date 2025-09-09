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
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.presentation.onboarding.fragments.signup.emoji.EmojiTypeDto
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.XMLElement
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import nl.adaptivity.xmlutil.core.impl.multiplatform.StringReader
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.time.DateTimeException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

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

// Mapping
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
        lastMessageDate = if (lastMessage == null || draftMessage != null) messageDate else lastMessage!!.date,
        lastMessageState = if (lastMessage?.state_ == 5 || lastMessage == null) MessageSendingState.None else MessageSendingState.Read,
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
        isGroup = conversationType_ == ConversationType.Group.rawValue
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

/**
 * Parses a timestamp from an XMPP message, prioritizing the inner <time> or <delay> elements for MAM messages.
 * Returns the timestamp as milliseconds since epoch (Long) or null if no valid timestamp is found.
 * @param message The XMPPMessage to parse.
 * @param tag A logging tag for identifying the source of the parse call.
 * @return Long? The parsed timestamp in milliseconds, or null if parsing fails or the message is a chat state.
 */
fun parseTimestamp(message: XMPPMessage, tag: String = "TimestampParser"): Long? {
    fun tryParse(stamp: String, messageId: String?, source: String): Long? {
        try {
            val formatter = DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .optionalStart()
                .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                .optionalEnd()
                .appendOffsetId()
                .toFormatter()
            val zdt = ZonedDateTime.parse(stamp, formatter)
            val epochMilli = zdt.toInstant().toEpochMilli()
            if (epochMilli > System.currentTimeMillis() + 86400000) { // Flag future timestamps > 1 day ahead as invalid
                Log.w(tag, "Invalid future timestamp ($source) for messageId=$messageId: $stamp -> $epochMilli")
                return null
            }
            return epochMilli.also {
                Log.d(tag, "Parsed timestamp ($source) for messageId=$messageId: $stamp -> $it")
            }
        } catch (e: DateTimeException) {
            Log.w(tag, "Failed to parse flexible ISO ($source) for messageId=$messageId: $stamp, error=${e.message}")
        }
        try {
            val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            val zdt = ZonedDateTime.parse(stamp, formatter)
            val epochMilli = zdt.toInstant().toEpochMilli()
            if (epochMilli > System.currentTimeMillis() + 86400000) {
                Log.w(tag, "Invalid future timestamp ($source) for messageId=$messageId: $stamp -> $epochMilli")
                return null
            }
            return epochMilli.also {
                Log.d(tag, "Parsed ISO_OFFSET_DATE_TIME ($source) for messageId=$messageId: $stamp -> $it")
            }
        } catch (e: DateTimeException) {
            Log.w(tag, "Failed to parse ISO_OFFSET_DATE_TIME ($source) for messageId=$messageId: $stamp, error=${e.message}")
        }
        try {
            val formatter = DateTimeFormatter.ISO_INSTANT
            val instant = Instant.parse(stamp)
            val epochMilli = instant.toEpochMilli()
            if (epochMilli > System.currentTimeMillis() + 86400000) {
                Log.w(tag, "Invalid future timestamp ($source) for messageId=$messageId: $stamp -> $epochMilli")
                return null
            }
            return epochMilli.also {
                Log.d(tag, "Parsed ISO_INSTANT ($source) for messageId=$messageId: $stamp -> $it")
            }
        } catch (e: DateTimeException) {
            Log.w(tag, "Failed to parse ISO_INSTANT ($source) for messageId=$messageId: $stamp, error=${e.message}")
        }
        Log.w(tag, "All parsers failed ($source) for messageId=$messageId: $stamp")
        return null
    }

    val messageId = message.element("origin-id", namespace = "urn:xmpp:sid:0")?.getAttribute("id") ?: message.id ?: "unknown"

    // Check MAM forwarded message
    val resultElement = message.element("result", namespace = "urn:xmpp:mam:2")
    if (resultElement != null) {
        val forwarded = resultElement.element("forwarded", namespace = "urn:xmpp:forward:0")
        if (forwarded != null) {
            val innerMessage = forwarded.element("message", namespace = "jabber:client")
            if (innerMessage != null) {
                val innerTime = innerMessage.element("time", namespace = "https://xabber.com/protocol/delivery")?.getAttribute("stamp")
                if (innerTime != null) {
                    return tryParse(innerTime, messageId, "inner <time>")
                }
                val innerDelay = innerMessage.element("delay", namespace = "urn:xmpp:delay")?.getAttribute("stamp")
                if (innerDelay != null) {
                    return tryParse(innerDelay, messageId, "inner <delay>")
                }
                val forwardedDelay = forwarded.element("delay", namespace = "urn:xmpp:delay")?.getAttribute("stamp")
                if (forwardedDelay != null) {
                    return tryParse(forwardedDelay, messageId, "forwarded <delay>")
                }
                Log.d(tag, "No <time> or <delay> found in inner message for messageId=$messageId")
            } else {
                Log.w(tag, "No <forwarded> found in MAM <result> for messageId=$messageId")
            }
        }
    }

    // Fallback to outer message
    val outerTime = message.element("time", namespace = "https://xabber.com/protocol/delivery")?.getAttribute("stamp")
    if (outerTime != null) {
        return tryParse(outerTime, messageId, "outer <time>")
    }
    val outerDelay = message.element("delay", namespace = "urn:xmpp:delay")?.getAttribute("stamp")
    if (outerDelay != null) {
        return tryParse(outerDelay, messageId, "outer <delay>")
    }

    // Parse raw XML as fallback
    try {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(message.raw))
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name
                val namespace = parser.namespace
                if (tagName == "time" && namespace == "https://xabber.com/protocol/delivery") {
                    val stamp = parser.getAttributeValue(null, "stamp")
                    if (stamp != null) {
                        return tryParse(stamp, messageId, "raw <time>")
                    }
                } else if (tagName == "delay" && namespace == "urn:xmpp:delay") {
                    val stamp = parser.getAttributeValue(null, "stamp")
                    if (stamp != null) {
                        return tryParse(stamp, messageId, "raw <delay>")
                    }
                }
            }
            eventType = parser.next()
        }
    } catch (e: Exception) {
        Log.e(tag, "Error parsing raw XML for messageId=$messageId: ${e.message}", e)
    }

    val isChatState = message.element("active", namespace = "http://jabber.org/protocol/chatstates") != null ||
            message.element("composing", namespace = "http://jabber.org/protocol/chatstates") != null ||
            message.element("inactive", namespace = "http://jabber.org/protocol/chatstates") != null ||
            message.element("received", namespace = "urn:xmpp:chat-markers:0") != null ||
            message.element("displayed", namespace = "urn:xmpp:chat-markers:0") != null

    if (isChatState) {
        Log.d(tag, "Chat state message detected, no timestamp required: messageId=$messageId")
        return null
    }

    Log.w(tag, "No valid timestamp found for messageId=$messageId. Message details: " +
            "from=${message.from?.bare()}, to=${message.to?.bare()}, body=${message.body?.take(100)}, " +
            "raw=${message.raw.substring(0, minOf(message.raw.length, 200))}...")
    return null
}

//fun observeMessages(
//    owner: String,
//    opponent: String,
//    conversationType: ConversationType
//): Flow<List<MessageDto>> {
//    return callbackFlow {
//        val realm = Realm.open(defaultRealmConfig())
//        val query = realm.query<MessageStorageItem>(
//            "owner = $0 AND opponent = $1 AND isDeleted = false AND conversationType_ = $2",
//            owner, opponent, conversationType.rawValue
//        ).sort("date", io.realm.kotlin.query.Sort.ASCENDING)
//        val results = query.find()
//
//        // Listen for changes and emit them
//        val listener: (ResultsChange<MessageStorageItem>) -> Unit = { change ->
//            val messages = change.list.mapNotNull { item ->
//                MessageDto(
//                    primary = item.primary,
//                    isOutgoing = item.outgoing,
//                    owner = item.owner,
//                    opponentJid = item.opponent,
//                    messageBody = item.body,
//                    messageSendingState = when {
//                        item.isRead -> MessageSendingState.Read
//                        item.outgoing -> MessageSendingState.Deliver
//                        else -> MessageSendingState.Sent
//                    },
//                    sentTimestamp = item.sentDate,
//                    editTimestamp = item.editDate,
//                    displayType = com.xabber.data_base.models.messages.MessageDisplayType.Text,
//                    canEditMessage = item.outgoing,
//                    canDeleteMessage = item.outgoing,
//                    urlAvatar = null,
//                    isGroup = item.conversationType_ == "https://xabber.com/protocol/groups",
//                    kind = null,
//                    isSelected = false,
//                    references = item.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
//                    isUnread = !item.isRead,
//                    isChecked = false,
//                    archivedId = item.archivedId
//                ).also {
//                    Log.d("observeMessages", "Emitted message: primary=${it.primary}, sentTimestamp=${it.sentTimestamp}, body=${it.messageBody.take(50)}, isUnread=${it.isUnread}")
//                }
//            }
//            trySend(messages).isSuccess // Emit the mapped list
//        }
//
//        results.asFlow().collect { change ->
//            listener(change)
//        }
//
//        // Ensure Realm is closed when the Flow is cancelled
//        awaitClose {
//            realm.close()
//            Log.d("observeMessages", "Realm closed for owner=$owner, opponent=$opponent")
//        }
//    }
//}
fun observeMessages(
    owner: String,
    opponent: String,
    conversationType: ConversationType
): Flow<List<MessageDto>> {
    return callbackFlow {
        val realm = Realm.open(defaultRealmConfig())
        val query = realm.query<MessageStorageItem>(
            "owner = $0 AND opponent = $1 AND isDeleted = false AND conversationType_ = $2",
            owner, opponent, conversationType.rawValue
        ).sort("date", io.realm.kotlin.query.Sort.ASCENDING)
        val results = query.find()

        val listener: (ResultsChange<MessageStorageItem>) -> Unit = { change ->
            val messages = change.list.mapNotNull { item ->
                MessageDto(
                    primary = item.primary,
                    isOutgoing = item.outgoing,
                    owner = item.owner,
                    opponentJid = item.opponent,
                    messageBody = item.body,
                    messageSendingState = when {
                        item.isRead -> MessageSendingState.Read
                        item.outgoing -> MessageSendingState.Deliver
                        else -> MessageSendingState.Sent
                    },
                    sentTimestamp = item.sentDate,
                    editTimestamp = item.editDate,
                    displayType = when (item.displayAs) {
                        "system" -> MessageDisplayType.System
                        else -> MessageDisplayType.Text
                    },
                    canEditMessage = item.outgoing,
                    canDeleteMessage = item.outgoing,
                    urlAvatar = null,
                    isGroup = item.conversationType_ == "https://xabber.com/protocol/groups",
                    kind = null,
                    isSelected = false,
                    references = item.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                    isUnread = !item.isRead,
                    isChecked = false,
                    archivedId = item.archivedId
                ).also {
                    Log.d("observeMessages", "Emitted message: primary=${it.primary}, sentTimestamp=${it.sentTimestamp}, body=${it.messageBody.take(50)}, isUnread=${it.isUnread}")
                }
            }
            trySend(messages).isSuccess
        }

        results.asFlow().collect { change ->
            listener(change)
        }

        awaitClose {
            realm.close()
            Log.d("observeMessages", "Realm closed for owner=$owner, opponent=$opponent")
        }
    }
}