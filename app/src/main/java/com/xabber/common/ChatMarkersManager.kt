package com.xabber.xmpp.messages.messages_manager

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.account.AccountManager
import com.xabber.stream.Stream
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.XMLElement
import com.xabber.xmpp.messages.message.MessageStanzaStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.TimeZone

@RequiresApi(Build.VERSION_CODES.O)
class ChatMarkersManager(private val owner: String, withoutAfterburnTimer: Boolean = false) {
    private val realm: Realm by lazy { Realm.open(defaultRealmConfig()) }
    private var afterburnTimer: Timer? = null
    private var TAG = "ChatMarkersManager"

    enum class BurnMessagesTimerValues(val value: Int) {
        OFF(0),
        S5(5),
        S10(10),
        S15(15),
        S30(30),
        M1(60),
        M5(300),
        M10(600),
        M15(900);

        companion object {
            fun verbose(value: BurnMessagesTimerValues): String = when (value) {
                OFF -> "Off"
                S5 -> "5 seconds"
                S10 -> "10 seconds"
                S15 -> "15 seconds"
                S30 -> "30 seconds"
                M1 -> "1 minute"
                M5 -> "5 minutes"
                M10 -> "10 minutes"
                M15 -> "15 minutes"
            }

            fun values(): List<BurnMessagesTimerValues> = listOf(OFF, S5, S10, S15, S30, M1, M5, M10, M15)

            fun allVerboseValues(): List<String> = values().map { verbose(it) }
        }
    }

    var checkMarkersAvailabilityCallback: ((String) -> Boolean)? = null

    private fun isAvailable(jid: String): Boolean = true // Always return true as per iOS

    fun namespaces(): List<String> = listOf("urn:xmpp:chat-markers:0")

    fun getPrimaryNamespace(): String = namespaces().first()

    val child: XMLElement
        get() = XMLElement(
            name = "markable",
            namespace = "urn:xmpp:chat-markers:0",
            raw = "<markable xmlns='urn:xmpp:chat-markers:0'/>",
            attributes = emptyMap(),
            children = emptyList()
        )

    init {
        if (!withoutAfterburnTimer) {
            runBlocking {
                updateDeleteEphemeralMessagesTimer()
            }
        }
        Log.w(TAG, "CHAT MARKERS MANAGER START")

    }

    suspend fun updateDeleteEphemeralMessagesTimer() {
        deleteEphemeralMessages()
        // Assuming CommonConfigManager.shared.config.afterburn_at_default is true for now
        afterburnTimer?.cancel()
        afterburnTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    CoroutineScope(Dispatchers.IO).launch {
                        deleteEphemeralMessages()
                    }
                }
            }, 0, 1000) // 1-second interval
        }
    }

    suspend fun deleteEphemeralMessages() {
        try {
            realm.write {
                val collection = query<MessageStorageItem>(
                    "owner = $0 AND afterburnInterval > 0 AND isRead = true AND isDeleted = false AND burnDate < $1 AND burnDate > 0",
                    owner, System.currentTimeMillis() / 1000.0
                ).find()

                if (collection.isEmpty()) return@write

                val jids = collection.map { it.opponent }.toSet()
                val chats = query<LastChatsStorageItem>(
                    "owner = $0 AND jid IN $1", owner, jids.toList()
                ).find()

                collection.forEach { message ->
                    message.isDeleted = true
                    message.body = ""
                    message.legacyBody = ""
                }

                chats.forEach { chat ->
                    val lastMessage = query<MessageStorageItem>(
                        "owner = $0 AND opponent = $1 AND isDeleted = false AND conversationType_ = $2",
                        owner, chat.jid, chat.conversationType_
                    ).find().sortedByDescending { it.date }.firstOrNull()
                    findLatest(chat)?.lastMessage = lastMessage
                }
            }
        } catch (e: Exception) {
            Log.e("ChatMarkersManager", "Error in deleteEphemeralMessages: ${e.message}")
        }
    }

    private suspend fun setReceived(message: XMPPMessage): Boolean {
        Log.w("CHECK ENGINE", "CHECK of xmppmessage: ${message.children}")
        if (message.element("markable", namespace = getPrimaryNamespace()) == null ||
            message.from?.bare() == null ||
            (message.id == null && getOriginId(message) == null)
        ) {
            return false
        }

        val toJid = message.from!!
        val messageId = message.id ?: getOriginId(message)!!
        val elementId = "ChatMarkers_${NanoId.generateOptimized(8, "-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)}"
        val received = XMLElement(
            name = "received",
            namespace = getPrimaryNamespace(),
            raw = "<received xmlns='${getPrimaryNamespace()}' id='$messageId'/>",
            attributes = mapOf("id" to messageId),
            children = emptyList()
        )
        val response = XMPPMessage(
            raw = "",
            type = "chat",
            id = elementId,
            to = toJid,
            children = listOf(received)
        )
        val conversationType = conversationTypeByMessage(message)
        val conversation = XMLElement(
            name = "conversation",
            namespace = "https://xabber.com/protocol/synchronization",
            raw = "<conversation xmlns='https://xabber.com/protocol/synchronization' type='${conversationType.rawValue}' jid='${toJid.bare()}'/>",
            attributes = mapOf("type" to conversationType.rawValue, "jid" to toJid.bare()),
            children = emptyList()
        )
        response.addElement(conversation)

        AccountManager.find(owner)?.unsafeAction { _, stream ->
           runBlocking {
               stream.socket?.write(response.raw)
           }
        }

        return true
    }

    private suspend fun onReceived(message: XMPPMessage): Boolean {
//        Log.w("CHECK ENGINE", "CHECK onReceived of xmppmessage: ${message.children}")

        val received = message.element("received", namespace = getPrimaryNamespace()) ?: return false
        val messageId = received.getAttribute("id") ?: return false
        val jid = if (message.from?.bare() == owner) message.to?.bare() else message.from?.bare() ?: return false

        try {
            realm.write {
                // Находим исходящее сообщение и обновляем его состояние до Deliver
                val instance = query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND messageId = $2 AND outgoing = true AND state_ < ${MessageSendingState.Deliver.rawValue}",
                    owner, jid, messageId
                ).first().find()

                if (instance != null) {
                    instance.state = MessageSendingState.Deliver
//                    Log.d("ChatMarkersManager", "Updated outgoing message state to Deliver: messageId=$messageId")
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("ChatMarkersManager", "Error in onReceived: ${e.message}", e)
            return false
        }
    }
    private suspend fun onDisplayed(message: XMPPMessage, archivedDate: Date? = null, delayed: Boolean = false): Boolean {
        val displayed = message.element("displayed", namespace = getPrimaryNamespace()) ?: return false
        val targetMessageId = displayed.getAttribute("id") ?: return false

        val outgoingMarker = message.from?.bare() == owner
        val jid = if (outgoingMarker) message.to?.bare() else message.from?.bare() ?: return false
        val date = archivedDate ?: getDelayedDate(message) ?: getDeliveryDate(message) ?: Date()

        if (!delayed) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(200)
                onDisplayed(message, date, true)
            }
            return true
        }

        try {
            realm.write {
                val chatPrimary = LastChatsStorageItem.genPrimary(jid!!, owner, ConversationType.Regular)
                val chat = query<LastChatsStorageItem>("primary = $0", chatPrimary).first().find() ?: return@write

                // Find the message that was displayed (it can be incoming or outgoing)
                val targetMessage = query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND messageId = $2",
                    owner, jid, targetMessageId
                ).first().find()

                val targetTimestampUs = when {
                    targetMessage != null -> targetMessage.sentDate * 1000L
                    targetMessageId.toLongOrNull() != null -> targetMessageId.toLongOrNull()!! * 1000L
                    else -> date.time * 1000L
                }

                // Update displayedId (for outgoing messages)
                val currentDisplayedId = chat.displayedId?.toLongOrNull() ?: 0L
                if (targetTimestampUs > currentDisplayedId) {
                    chat.displayedId = targetTimestampUs.toString()
                    Log.d("ChatMarkersManager", "Updated displayedId to $targetTimestampUs µs for chat $jid")
                }

                // Update lastReadMessageDate for incoming messages (remote device read)
                val currentLastReadMs = chat.lastReadMessageDate
                val targetTimestampMs = targetTimestampUs / 1000L
                if (targetTimestampMs > currentLastReadMs) {
                    chat.lastReadMessageDate = targetTimestampMs
                    Log.d("ChatMarkersManager", "Updated lastReadMessageDate to $targetTimestampMs ms for chat $jid")
                }

                // Update outgoing messages state
                val updatedDisplayedId = chat.displayedId?.toLongOrNull() ?: 0L
                val updatedDeliveredId = chat.deliveredId?.toLongOrNull() ?: 0L

                val outgoingMessages = query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND outgoing = true",
                    owner, jid
                ).find()

                outgoingMessages.forEach { msg ->
                    val msgTimestampUs = msg.sentDate * 1000L
                    msg.state = when {
                        msgTimestampUs <= updatedDisplayedId -> MessageSendingState.Read
                        msgTimestampUs <= updatedDeliveredId -> MessageSendingState.Deliver
                        else -> if (msg.state_ < MessageSendingState.Sent.rawValue) MessageSendingState.Sent else msg.state
                    }

                    if (msg.state == MessageSendingState.Read && (msg.readDate ?: 0L) < 1) {
                        msg.readDate = date.time / 1000
                    }
                }

                // Recalculate unread count based on lastReadMessageDate
                val incomingMessages = query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND outgoing = false AND isDeleted = false",
                    owner, jid
                ).find()

                val newUnread = incomingMessages.count { msg ->
                    msg.sentDate > chat.lastReadMessageDate
                }.toInt()

                chat.unread = newUnread
                Log.d("ChatMarkersManager", "Recalculated unread count to $newUnread for chat $jid")
            }

            deleteEphemeralMessages()
            return true
        } catch (e: Exception) {
            Log.e("ChatMarkersManager", "Error in onDisplayed: ${e.message}", e)
            return false
        }
    }

    // Helper function to normalize timestamps (add this to ChatMarkersManager class)
    private fun normalizeTimestamp(timestamp: Long): Long {
        return when {
            // If timestamp has 16 digits (microseconds), convert to milliseconds
            timestamp.toString().length == 16 -> timestamp / 1000L

            // If timestamp has 13 digits (milliseconds), use as-is
            timestamp.toString().length == 13 -> timestamp

            // If timestamp has 10 digits (seconds), convert to milliseconds
            timestamp.toString().length == 10 -> timestamp * 1000L

            // Default - assume milliseconds
            else -> timestamp
        }
    }

    private suspend fun onServerDeliveryReceived(message: XMPPMessage): Boolean {
        val delivery = message.element("received", namespace = "https://xabber.com/protocol/delivery") ?: return false
        val originIdElement = delivery.element("origin-id", namespace = "urn:xmpp:sid:0")
        val messageId = originIdElement?.getAttribute("id") ?: return false

        Log.d("ChatMarkers", "Server delivery confirmation for messageId=$messageId")

        try {
            realm.write {
                val targetMessage = query<MessageStorageItem>(
                    "owner = $0 AND messageId = $1 AND outgoing = true AND state_ < ${MessageSendingState.Deliver.rawValue}",
                    owner, messageId
                ).first().find()

                if (targetMessage != null) {
                    targetMessage.state = MessageSendingState.Deliver
                    Log.d("ChatMarkers", "Updated outgoing message to Deliver: messageId=$messageId")
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("ChatMarkers", "Error updating delivery status: ${e.message}", e)
            return false
        }
    }

    private suspend fun onCarbonsSentDisplayed(message: XMPPMessage): Boolean {
        if (!isCarbonCopy(message)) return false
        val bareMessage = getCarbonCopyMessageContainer(message) ?: return false
        val date = getDelayedDate(message)
        return onDisplayed(bareMessage, date)
    }

    private suspend fun onCarbonsForwardedDisplayed(message: XMPPMessage): Boolean {
        if (!isCarbonForwarded(message)) return false
        val bareMessage = getCarbonForwardedMessageContainer(message) ?: return false
        val date = getDelayedDate(message)
        return onDisplayed(bareMessage, date)
    }

    private suspend fun onArchivedDisplayed(message: XMPPMessage): Boolean {
        if (!isArchivedMessage(message)) return false
        val bareMessage = getArchivedMessageContainer(message) ?: return false
        val date = getDelayedDate(message) ?: return false
        return onDisplayed(bareMessage, date)
    }

    private suspend fun onCarbonsSentReceived(message: XMPPMessage): Boolean {
        if (!isCarbonCopy(message)) return false
        val bareMessage = getCarbonCopyMessageContainer(message) ?: return false
        return onReceived(bareMessage)
    }

    private suspend fun onCarbonsForwardedReceived(message: XMPPMessage): Boolean {
        if (!isCarbonForwarded(message)) return false
        val bareMessage = getCarbonForwardedMessageContainer(message) ?: return false
        return onReceived(bareMessage)
    }

    private suspend fun onArchivedReceived(message: XMPPMessage): Boolean {
        if (!isArchivedMessage(message)) return false
        val bareMessage = getArchivedMessageContainer(message) ?: return false
        return onReceived(bareMessage)
    }

    private suspend fun onDelivered(message: XMPPMessage): Boolean {
        val delivered = message.element("delivered", namespace = getPrimaryNamespace()) ?: run {
            return false
        }

        val targetMessageId = delivered.getAttribute("id") ?: return false
        Log.d("ChatMarkers", "Processing delivered marker for messageId=$targetMessageId")

        val outgoing = message.from?.bare() == owner
        val jid = if (outgoing) message.to?.bare() else message.from?.bare() ?: return false

        try {
            realm.write {
                // Получаем чат
                val chatPrimary = LastChatsStorageItem.genPrimary(jid!!, owner, ConversationType.Regular)
                val chat = query<LastChatsStorageItem>("primary = $0", chatPrimary).first().find()

                // Обновляем deliveredId в чате
                val targetMessageIdLong = targetMessageId.toLongOrNull()
                if (targetMessageIdLong != null) {
                    val currentDeliveredId = chat?.deliveredId?.toLongOrNull() ?: 0L
                    if (targetMessageIdLong > currentDeliveredId) {
                        chat?.deliveredId = targetMessageId
                        Log.d("ChatMarkersManager",
                            "Updated deliveredId for chat $jid to $targetMessageId (was ${chat?.deliveredId})")
                    }
                }

                // Обновляем состояние исходящих сообщений на основе нового deliveredId
                val updatedDeliveredId = chat?.deliveredId?.toLongOrNull() ?: 0L

                if (updatedDeliveredId > 0) {
                    // Обновляем все исходящие сообщения
                    val outgoingMessages = query<MessageStorageItem>(
                        "owner = $0 AND opponent = $1 AND outgoing = true",
                        owner, jid
                    ).find()

                    outgoingMessages.forEach { msg ->
                        val messageIdLong = msg.messageId.toLongOrNull()
                        if (messageIdLong != null && messageIdLong <= updatedDeliveredId) {
                            // Сообщение доставлено, но еще не прочитано
                                msg.state = MessageSendingState.Deliver
                                Log.d("ChatMarkersManager",
                                    "Outgoing message marked as Deliver: messageId=${msg.messageId}, deliveredId=$updatedDeliveredId")

                        }
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e("ChatMarkersManager", "Error in onDelivered: ${e.message}", e)
            return false
        }
    }

    suspend fun read(message: XMPPMessage): Boolean {
        return when {
            onServerDeliveryReceived(message) -> true
            onCarbonsSentDisplayed(message) -> true
            onCarbonsForwardedDisplayed(message) -> true
            onArchivedDisplayed(message) -> true
            onCarbonsSentReceived(message) -> true
            onCarbonsForwardedReceived(message) -> true
            onArchivedReceived(message) -> true
            onDelivered(message) -> true  // Добавьте эту строку
            onReceived(message) -> true
            onDisplayed(message) -> true
            setReceived(message) -> true
            else -> false
        }
    }
    suspend fun displayedById(stream: Stream, jid: String, messageId: String) {
        val displayed = XMLElement(
            name = "displayed",
            namespace = getPrimaryNamespace(),
            raw = "<displayed xmlns='${getPrimaryNamespace()}' id='$messageId'/>",
            attributes = mapOf("id" to messageId),
            children = emptyList()
        )
        val elementId = "ChatMarkers_${NanoId.generateOptimized(8, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)}"
        val message = XMPPMessage(
            raw = "",
            type = "chat",
            id = elementId,
            to = XMPPJID(fullJID = jid),
            children = listOf(displayed)
        )
        stream.socket?.write(message.raw)
    }

//    suspend fun displayed(stream: Stream, messagePrimary: String) {
//        try {
//            realm.write {
//                val instance = query<MessageStorageItem>("primary = $0", messagePrimary).first().find() ?: return@write
//                if (instance.outgoing) return@write
//
//                val elementId = "ChatMarkers_${NanoId.generateOptimized(8, "-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)}"
//
//                // Собираем <displayed ...>
//                val displayedChildren = mutableListOf<String>()
//                val stanzaInstance = query<MessageStanzaStorageItem>("primary = $0", "${messagePrimary}_stanza").first().find()
//                stanzaInstance?.stanza?.let { rawStanza ->
//                    val stanzaIdRegex = Regex("<stanza-id[^>]*by=['\"]$owner['\"][^>]*id=['\"]([^'\"]+)['\"][^>]*/?>")
//                    stanzaIdRegex.findAll(rawStanza).forEach { match ->
//                        val id = match.groupValues[1]
//                        displayedChildren.add("<stanza-id xmlns='urn:xmpp:sid:0' by='$owner' id='$id'/>")
//                    }
//                }
//
//                val displayedInner = displayedChildren.joinToString("")
//                val displayedXml = """
//                <displayed xmlns='${getPrimaryNamespace()}' id='${instance.messageId}'>
//                    $displayedInner
//                </displayed>
//            """.trimIndent()
//
//                // Собираем <conversation ...>
//                val conversationType = instance.conversationType
//                val conversationXml = """
//                <conversation xmlns='https://xabber.com/protocol/synchronization' type='${conversationType.rawValue}' jid='${instance.opponent}'/>
//            """.trimIndent()
//
//                // Добавляем encryption + store при шифровании
//                val extraElements = mutableListOf<String>()
//                if (conversationType.isEncrypted) {
//                    extraElements.add("<encryption xmlns='urn:xmpp:eme:0' namespace='${conversationType.rawValue}'/>")
//                    extraElements.add("<store xmlns='urn:xmpp:hints:2'/>")
//                }
//
//                // Финальная станза
//                val finalStanza = """
//                <message type='chat' to='${instance.opponent}' id='$elementId'>
//                    $displayedXml
//                    $conversationXml
//                    ${extraElements.joinToString("")}
//                </message>
//            """.trimIndent()
//
//                Log.d("ChatMarkersManager", "Sending displayed marker:\n$finalStanza")
//
//                CoroutineScope(Dispatchers.IO).launch {
//                    try {
//                        stream.socket?.write(finalStanza)
//                    } catch (e: Exception) {
//                        Log.e("ChatMarkersManager", "Failed to send displayed marker", e)
//                    }
//                }
//                // Обновляем readDate / burnDate (уже правильно)
//                val collection = query<MessageStorageItem>(
//                    "owner = $0 AND opponent = $1 AND date < $2 AND burnDate < 0",
//                    owner, instance.opponent, instance.date
//                ).find()
//
//                findLatest(instance)?.let { msg ->
//                    if ((msg.readDate ?: 0) < 1) msg.readDate = System.currentTimeMillis() / 1000
//                    if (msg.afterburnInterval > 0 && (msg.burnDate ?: 0) < 1) {
//                        msg.burnDate = System.currentTimeMillis() / 1000 + msg.afterburnInterval
//                    }
//                }
//
//                collection.forEach { msg ->
//                    if ((msg.readDate ?: 0) < 1) msg.readDate = System.currentTimeMillis() / 1000
//                    if (msg.afterburnInterval > 0 && (msg.burnDate ?: 0) < 1) {
//                        msg.burnDate = System.currentTimeMillis() / 1000 + msg.afterburnInterval
//                    }
//                }
//
//                Log.w("CHAT MARKERS", "DISPLAYED SENT for primary=$messagePrimary")
//            }
//        } catch (e: Exception) {
//            Log.e("ChatMarkersManager", "Error in displayed: ${e.message}", e)
//        }
//    }
suspend fun displayed(stream: Stream, messagePrimary: String) = withContext(Dispatchers.IO) {
    var opponent: String? = null
    var messageId: String? = null
    var conversationType: ConversationType? = null

    realm.write {
        val msg = query<MessageStorageItem>("primary = $0", messagePrimary).first().find() ?: return@write
        if (msg.outgoing) return@write

        opponent = msg.opponent
        messageId = msg.messageId
        conversationType = msg.conversationType

        // Обновляем readDate / burnDate
        if ((msg.readDate ?: 0) < 1) msg.readDate = System.currentTimeMillis() / 1000
        if (msg.afterburnInterval > 0 && (msg.burnDate ?: 0) < 1) {
            msg.burnDate = System.currentTimeMillis() / 1000 + msg.afterburnInterval
        }
    }

    // Отправляем маркер — полностью асинхронно и неблокирующе
    opponent?.let { opp ->
        messageId?.let { mid ->
            conversationType?.let { type ->
                try {
                    val elementId = "ChatMarkers_${NanoId.generateOptimized(8, "-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)}"

                    val displayedChildren = mutableListOf<String>()
                    val stanzaInstance = realm.query<MessageStanzaStorageItem>(
                        "primary = $0", "${messagePrimary}_stanza"
                    ).first().find()

                    stanzaInstance?.stanza?.let { raw ->
                        val regex = Regex("<stanza-id[^>]*by=['\"]$owner['\"][^>]*id=['\"]([^'\"]+)['\"][^>]*>")
                        regex.findAll(raw).forEach { match ->
                            val id = match.groupValues[1]
                            displayedChildren.add("<stanza-id xmlns='urn:xmpp:sid:0' by='$owner' id='$id'/>")
                        }
                    }

                    val displayedInner = displayedChildren.joinToString("")
                    val displayedXml = "<displayed xmlns='${getPrimaryNamespace()}' id='$mid'>$displayedInner</displayed>"

                    val conversationXml = "<conversation xmlns='https://xabber.com/protocol/synchronization' type='${type.rawValue}' jid='$opp'/>"

                    val extra = if (type.isEncrypted) {
                        "<encryption xmlns='urn:xmpp:eme:0' namespace='${type.rawValue}'/><store xmlns='urn:xmpp:hints:2'/>"
                    } else ""

                    val stanza = """
                        <message type='chat' to='$opp' id='$elementId'>
                            $displayedXml
                            $conversationXml
                            $extra
                        </message>
                    """.trimIndent()

                    stream.socket?.write(stanza) // ← НЕ блокирует UI!
                } catch (e: Exception) {
                    Log.e("ChatMarkersManager", "Failed to send displayed marker", e)
                }
            }
        }
    }
}

    private fun getOriginId(message: XMPPMessage): String? {
        return message.element("origin-id", namespace = "urn:xmpp:sid:0")?.getAttribute("id")
    }

    private fun getDelayedDate(message: XMPPMessage): Date? {
        val delay = message.element("delay", namespace = "urn:xmpp:delay")?.getAttribute("stamp")
        return delay?.let {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(it)
            } catch (e: Exception) {
                Log.e("ChatMarkersManager", "Failed to parse delay timestamp: ${e.message}")
                null
            }
        }
    }

    private fun getDeliveryDate(message: XMPPMessage): Date? {
        val time = message.element("time", namespace = "https://xabber.com/protocol/delivery")?.getAttribute("stamp")
        return time?.let {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(it)
            } catch (e: Exception) {
                Log.e("ChatMarkersManager", "Failed to parse delivery timestamp: ${e.message}")
                null
            }
        }
    }

    private fun isCarbonCopy(message: XMPPMessage): Boolean {
        return message.element("sent", namespace = "urn:xmpp:carbons:2") != null
    }

    private fun isCarbonForwarded(message: XMPPMessage): Boolean {
        return message.element("forwarded", namespace = "urn:xmpp:forward:0") != null
    }

    private fun isArchivedMessage(message: XMPPMessage): Boolean {
        return message.element("archived", namespace = "urn:xmpp:mam:tmp") != null
    }

    private fun getCarbonCopyMessageContainer(message: XMPPMessage): XMPPMessage? {
        val sent = message.element("sent", namespace = "urn:xmpp:carbons:2")
        return sent?.element("forwarded", namespace = "urn:xmpp:forward:0")?.element("message", namespace = "jabber:client")?.let { XMPPMessage(it.raw) }
    }

    private fun getCarbonForwardedMessageContainer(message: XMPPMessage): XMPPMessage? {
        return getCarbonCopyMessageContainer(message)
    }

    private fun getArchivedMessageContainer(message: XMPPMessage): XMPPMessage? {
        val forwarded = message.element("forwarded", namespace = "urn:xmpp:forward:0")
        return forwarded?.element("message", namespace = "jabber:client")?.let { XMPPMessage(it.raw) }
    }

    private fun conversationTypeByMessage(message: XMPPMessage): ConversationType {
        val to = message.to?.bare()
        return when {
            to == "favorites.redsolution.com" -> ConversationType.Favorites
            message.element("x", namespace = "https://xabber.com/protocol/groups") != null -> ConversationType.Group
            message.element("channel", namespace = "https://xabber.com/protocol/channels") != null -> ConversationType.Channel
            message.element("omemo", namespace = "urn:xmpp:omemo:2") != null -> ConversationType.Omemo
            message.element("omemo", namespace = "urn:xmpp:omemo:1") != null -> ConversationType.Omemo1
            message.element("axolotl", namespace = "eu.siacs.conversations.axolotl") != null -> ConversationType.Axolotl
            message.element("xen", namespace = "urn:xabber:xen:0") != null -> ConversationType.Notifications
            else -> ConversationType.Regular
        }.also {
            Log.d("MessageCommonReceiver", "Determined conversationType=${it.rawValue} for messageId=${message.id}, to=$to")
        }
    }
}