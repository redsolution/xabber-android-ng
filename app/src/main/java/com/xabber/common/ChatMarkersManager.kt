package com.xabber.xmpp.messages.messages_manager

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.common.AccountManager
import com.xabber.common.Stream
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

                val jids = collection.mapNotNull { it.opponent }.toSet()
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
        if (message.element("markable", namespace = getPrimaryNamespace()) == null ||
            message.from?.bare() == null ||
            (message.id == null && getOriginId(message) == null)
        ) {
            return false
        }

        val toJid = message.from!!
        val messageId = message.id ?: getOriginId(message)!!
        val elementId = "ChatMarkers_${NanoId.generateOptimized(8, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)}"
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

        return false
    }

    private suspend fun onReceived(message: XMPPMessage): Boolean {
        val received = message.element("received", namespace = getPrimaryNamespace()) ?: return false
        val jid = message.from?.bare() ?: return false
        val messageId = received.getAttribute("id") ?: return false

        try {
            realm.write {
                val instance = query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND messageId = $2 AND state_ < ${MessageStorageItem.MessageSendingState.DELIVERED.value}",
                    owner, jid, messageId
                ).first().find() ?: return@write

                findLatest(instance)?.state = MessageSendingState.Deliver
            }
            return true
        } catch (e: Exception) {
            Log.e("ChatMarkersManager", "Error in onReceived: ${e.message}")
            return false
        }
    }

    private suspend fun onDisplayed(message: XMPPMessage, archivedDate: Date? = null, delayed: Boolean = false): Boolean {
        val displayed = message.element("displayed", namespace = getPrimaryNamespace()) ?: return false
        val jid = if (message.from?.bare() == owner) message.to?.bare() else message.from?.bare() ?: return false
        val messageId = displayed.getAttribute("id") ?: return false

        var date = archivedDate ?: getDelayedDate(message) ?: getDeliveryDate(message) ?: Date()
        val stanzaId = displayed.elements("stanza-id").firstOrNull { it.getAttribute("by") == owner }?.getAttribute("id") ?: "no-stanza-id"

        if (!delayed) {
            if (jid != null) {
                AccountManager.find(owner)?.messageReceiver?.updateReadDate(messageId, stanzaId, jid, date)
            }
            CoroutineScope(Dispatchers.IO).launch {
                delay(200) // Simulate asyncAfter
                onDisplayed(message, date, true)
            }
        }

        try {
            realm.write {
                val instance = query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND messageId = $2",
                    owner, jid, messageId
                ).first().find() ?: return@write

                val collection = query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND date <= $2 AND burnDate < 1 AND state_ != ${MessageStorageItem.MessageSendingState.ERROR.value}",
                    owner, jid, instance.date
                ).find()

                findLatest(instance)?.let { msg ->
                    val chat = query<LastChatsStorageItem>(
                        "primary = $0",
                        jid?.let { LastChatsStorageItem.genPrimary(it, owner, msg.conversationType) }
                    ).first().find()
                    if (chat?.lastMessage?.primary == msg.primary) {
                        chat.unread = 0
                    }

                    if (msg.readDate!! < 1) {
                        msg.readDate = (date.time / 1000.0)
                    }
                    if (msg.afterburnInterval > 0 && msg.burnDate < 1) {
                        msg.burnDate = (date.time / 1000.0) + msg.afterburnInterval
                    }
                    msg.state = MessageSendingState.Read
                    msg.isRead = true
                }

                collection.forEach { msg ->
                    if (msg.readDate!! < 1) {
                        msg.readDate = (date.time / 1000.0)
                    }
                    if (msg.afterburnInterval > 0 && msg.burnDate < 1) {
                        msg.burnDate = (date.time / 1000.0) + msg.afterburnInterval
                    }
                    msg.state = MessageSendingState.Read
                    msg.isRead = true
                }
            }
            deleteEphemeralMessages()
            return true
        } catch (e: Exception) {
            Log.e("ChatMarkersManager", "Error in onDisplayed: ${e.message}")
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

    suspend fun read(message: XMPPMessage): Boolean {
        return when {
            onCarbonsSentDisplayed(message) -> true
            onCarbonsForwardedDisplayed(message) -> true
            onArchivedDisplayed(message) -> true
            onCarbonsSentReceived(message) -> true
            onCarbonsForwardedReceived(message) -> true
            onArchivedReceived(message) -> true
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

    suspend fun displayed(stream: Stream, messagePrimary: String) {
        try {
            realm.write {
                val instance = query<MessageStorageItem>("primary = $0", messagePrimary).first().find() ?: return@write
                if (instance.outgoing) return@write

                val elementId = "ChatMarkers_${NanoId.generateOptimized(8, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)}"
                val displayed = XMLElement(
                    name = "displayed",
                    namespace = getPrimaryNamespace(),
                    raw = "<displayed xmlns='${getPrimaryNamespace()}' id='${instance.messageId}'/>",
                    attributes = mapOf("id" to instance.messageId),
                    children = emptyList()
                )

                val stanzaInstance = query<MessageStanzaStorageItem>(
                    "primary = $0", "${messagePrimary}_stanza"
                ).first().find()
                stanzaInstance?.let { stanza ->
                    // Parse stanza.stanza XML and extract stanza-id elements if needed
                    // For simplicity, assuming no stanza-id elements are added here
                }

                val response = XMPPMessage(
                    raw = "",
                    type = "chat",
                    id = elementId,
                    to = XMPPJID(fullJID = instance.opponent),
                    children = listOf(displayed)
                )
                val conversationType = instance.conversationType
                val conversation = XMLElement(
                    name = "conversation",
                    namespace = "https://xabber.com/protocol/synchronization",
                    raw = "<conversation xmlns='https://xabber.com/protocol/synchronization' type='${conversationType.rawValue}' jid='${instance.opponent}'/>",
                    attributes = mapOf("type" to conversationType.rawValue, "jid" to instance.opponent),
                    children = emptyList()
                )
                response.addElement(conversation)

                runBlocking {
                    stream.socket?.write(response.raw)
                }

                val collection = query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND date < $2 AND burnDate < 0",
                    owner, instance.opponent, instance.date
                ).find()

                findLatest(instance)?.let { msg ->
                    if (msg.readDate!! < 1 && msg.burnDate < 1 && msg.afterburnInterval > 0) {
                        msg.readDate = System.currentTimeMillis() / 1000.0
                        msg.burnDate = System.currentTimeMillis() / 1000.0 + msg.afterburnInterval
                    }
                }

                collection.forEach { msg ->
                    if (msg.readDate!! < 1 && msg.burnDate < 1 && msg.afterburnInterval > 0) {
                        msg.readDate = System.currentTimeMillis() / 1000.0
                        msg.burnDate = System.currentTimeMillis() / 1000.0 + msg.afterburnInterval
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatMarkersManager", "Error in displayed: ${e.message}")
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