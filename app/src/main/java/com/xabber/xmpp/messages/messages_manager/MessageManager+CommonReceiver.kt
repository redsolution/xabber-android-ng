package com.xabber.xmpp.messages.messages_manager

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.account.AccountManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.utils.parseTimestamp
import com.xabber.utils.toMessageReferenceDto
import com.xabber.xmpp.groupchat.GroupChatStorageItem
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.XMLElement
import com.xabber.xmpp.messages.message.TemporaryMessageStanzaStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.UUID
import kotlin.collections.HashSet

@RequiresApi(Build.VERSION_CODES.O)
class MessageCommonReceiver(private val owner: String) {

    private val realm: Realm by lazy { Realm.open(defaultRealmConfig()) }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processMutex = Mutex()
    private val processedMessageIds = mutableSetOf<String>()
    private val messageQueryIds = mutableSetOf<String>()
    val seenQueryIds = mutableSetOf<String>()
    private val prereadedMessages = mutableSetOf<PrereadedMessagesItem>()
    private val prereadedConversation = mutableSetOf<PrereadedConversationItem>()

    private val messagesQueue = MutableStateFlow(mutableSetOf<MessageQueueItem>())

    companion object {
        private const val TAG = "MessageCommonReceiver"
    }

    data class MessageQueueItem(
        val message: XMPPMessage,
        val messageId: String?,
        val archivedFrom: String?,
        var isRead: Boolean,
        val date: Date,
        val state: MessageSendingState,
        val forceUnreadState: Boolean? = null,
        val clientSyncMessage: Boolean = false,
        val queryId: String? = null,
        val groupchatUserCard: String? = null,
        val readDate: Date? = null,
        var originalFrom: String = "",
        var originalOutgoing: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean =
            other is MessageQueueItem &&
                    message.raw == other.message.raw &&  // ← Critical: use raw XML
                    date.time == other.date.time

        override fun hashCode(): Int {
            var result = message.raw.hashCode()
            result = 31 * result + date.time.hashCode()
            return result
        }
    }

    data class PrereadedMessagesItem(
        val messageId: String,
        val stanzaId: String,
        val date: Date,
        val jid: String
    ) {
        override fun equals(other: Any?): Boolean = this === other || (other is PrereadedMessagesItem && messageId == other.messageId && jid == other.jid)
        override fun hashCode(): Int = messageId.hashCode() * 31 + jid.hashCode()
    }

    data class PrereadedConversationItem(
        val conversationType: ConversationType,
        val date: Date,
        val jid: String
    ) {
        override fun equals(other: Any?): Boolean = this === other || (other is PrereadedConversationItem && jid == other.jid && conversationType == other.conversationType)
        override fun hashCode(): Int = jid.hashCode() * 31 + conversationType.hashCode()
    }

    init {
        subscribeReceiver()
    }


    suspend fun receiveClientSyncRaw(
        message: XMPPMessage,
    ) {

        val messageId = getOriginId(message) ?: message.id
        val messageBare = getArchivedMessageContainer(message)
        Log.w(TAG, "receiveClientSyncRaw working!")
        val queueItem = MessageQueueItem(
            message = message,
            messageId = messageId,
            archivedFrom = message.from?.bare(),
            isRead = message.from?.bare() == owner,
            date = Date(messageBare?.date ?: System.currentTimeMillis()),
            state = MessageSendingState.Sent,
            forceUnreadState = message.from?.bare() == owner,
            clientSyncMessage = true,
            queryId = getMAMQueryId(message)
        )
        enqueue(queueItem)
        storeMessagesNow()
        Log.w("CHECK", "check it RECEIVER SYNC $queueItem, ${message.body}, id:${message.id}, from=${message.from}, to=${message.to}")

    }

    fun receiveTemporary(message: XMPPMessage): MessageQueueItem? {
        val date = getDelayedDate(message) ?: return null
        val messageBare = getArchivedMessageContainer(message) ?: return null
        val messageId = getOriginId(messageBare) ?: messageBare.id
        return MessageQueueItem(
            message = messageBare,
            messageId = messageId,
            archivedFrom = message.from?.bare(),
            isRead = message.from?.bare() == owner,
            date = Date(messageBare.date ?: System.currentTimeMillis()),
            state = MessageSendingState.Sent,
            clientSyncMessage = true,
            queryId = getMAMQueryId(message)
        )
    }

    suspend fun receiveArchived(message: XMPPMessage) {
//        if (!message.hasElement("result", "urn:xmpp:mam:2") &&
//            !message.hasElement("archived", "urn:xmpp:mam:tmp")) {
//            return
//        }
        val date = getDelayedDate(message) ?: Date()
        val messageBare = getArchivedMessageContainer(message) ?: return
        val messageId = getOriginId(messageBare) ?: messageBare.id!!
        if (processedMessageIds.contains(messageId)) return
        processedMessageIds.add(messageId)
        val primary = MessageStorageItem.genPrimary(messageId, owner)
        if (realm.query<MessageStorageItem>("primary == $0", primary).first().find() != null) {
            return
        }
        val queueItem = MessageQueueItem(
            message = messageBare,
            messageId = messageId,
            archivedFrom = message.from?.bare(),
            isRead = true,
            date = Date(message.date ?: System.currentTimeMillis()),
            state = MessageSendingState.Sent,
            queryId = getMAMQueryId(message)
        )
        Log.w("CHECK", "check it RECEIVER MAM $queueItem, ${message.body}, id:${message.id}, from=${message.from}, to=${message.to}")

        enqueue(queueItem)
        storeMessagesNow()
    }

    suspend fun receiveCarbon(message: XMPPMessage) {
        val isSentCarbon = message.element("sent", "urn:xmpp:carbons:2") != null
        val forwarded = message.element("forwarded", "urn:xmpp:forward:0") ?: return
        val innerMessage = forwarded.element("message", "jabber:client") ?: return
        val bareMessage = XMPPMessage(innerMessage.raw)

        val messageId = getOriginId(bareMessage) ?: bareMessage.id ?: return
        if (processedMessageIds.contains(messageId)) return
        processedMessageIds.add(messageId)

        val isRead = isSentCarbon  // Only own sent carbons are read
        val state = MessageSendingState.Sent

        val queueItem = MessageQueueItem(
            message = bareMessage,
            messageId = messageId,
            archivedFrom = bareMessage.from?.bare(),
            isRead = isRead,
            date = Date(message.date ?: System.currentTimeMillis()),
            state = state,
            queryId = getMAMQueryId(message),
            originalOutgoing = isSentCarbon
        )
        Log.w("CHECK", "check it RECEIVER Carbon $queueItem, ${message.body}, id:${message.id}, from=${message.from}, to=${message.to}")

        enqueue(queueItem)
        storeMessagesNow()
    }

    suspend fun receiveCarbonForwarded(message: XMPPMessage) {
        val messageId = getOriginId(message) ?: message.id ?: return
        if (message.body.isNullOrBlank()) return

        val primary = MessageStorageItem.genPrimary(messageId, owner)
        if (realm.query<MessageStorageItem>("primary == $0", primary).first().find() != null) return

        val from = message.from?.bare() ?: return
        val to = message.to?.bare() ?: return
        val opponent = if (to != owner) to else from
        if (opponent == owner) return

        val queueItem = MessageQueueItem(
            message = message,
            messageId = messageId,
            archivedFrom = from,
            isRead = from == owner,
            date = Date(parseTimestamp(message, owner, TAG)),
            state = MessageSendingState.Sent,
            originalFrom = from,
            originalOutgoing = from == owner
        )
        enqueue(queueItem)
    }

    suspend fun receiveRuntime(message: XMPPMessage) {
        if (message.body.isNullOrBlank()) return
        val messageId = getOriginId(message) ?: message.id ?: return
        val from = message.from?.bare() ?: return
        val to = message.to?.bare() ?: return
        val isOutgoing = if (message.hasElement("x", "https://xabber.com/protocol/groups")) {
            // групповой чат — особый случай
            message.element("x", "https://xabber.com/protocol/groups")
                ?.element("reference")
                ?.element("user", "https://xabber.com/protocol/groups")
                ?.getAttribute("id") == owner
        } else {
            from == owner   // обычный чат — исходящее, если from == наш аккаунт
        }
        val opponent = if (isOutgoing) to else from
        if (opponent == owner) return

        val queueItem = MessageQueueItem(
            message = message,
            messageId = messageId,
            archivedFrom = from,
            isRead = from == owner,
            date = Date(message.date ?: System.currentTimeMillis()),
            state = MessageSendingState.Deliver,
            originalFrom = from,
            originalOutgoing = from == owner
        )
//        Log.w("CHECK", "check it RECEIVER runtime $queueItem, ${message.body}, id:${message.id}, from=${message.from}, to=${message.to}")

        enqueue(queueItem)
        storeMessagesNow()
    }

    fun updateReadDate(messageId: String, stanzaId: String, jid: String, date: Date) {
        prereadedMessages.add(PrereadedMessagesItem(messageId, stanzaId, date, jid))
    }


    @OptIn(FlowPreview::class)
    internal fun subscribeReceiver() {
        scope.launch {
            messagesQueue
                .debounce(1)
                .collect { items ->
                    processMutex.withLock {
                        if (items.isNotEmpty()) {
                            processQueue(items.toList())
                            AccountManager.find(owner)?.chatMarkers?.deleteEphemeralMessages()
                        }
                    }
                }
        }
    }

    internal fun unsubscribeReceiver() {
        // queueJob?.cancel() — если был
        clearQueue()
    }

    private fun clearQueue(item: MessageQueueItem) {
        Log.d(TAG, "Clearing queue item: messageId=${item.messageId}")
        val current = messagesQueue.value.toMutableSet()
        current.remove(item)
        messagesQueue.value = current
        Log.d(TAG, "Queue state after clear: size=${messagesQueue.value.size}, items=${messagesQueue.value.map { it.messageId }}")
    }
    private fun clearQueue() {
        Log.d(TAG, "Clearing entire message queue")
        messagesQueue.value = HashSet()
        Log.d(TAG, "Queue cleared: size=${messagesQueue.value.size}")
    }

    private suspend fun processQueue(items: List<MessageQueueItem>) {
        val sorted = items.sortedBy { it.date }

        for (item in sorted) {
            if (isVoIPMessage(item.message)) continue

            val from = item.message.from?.bare() ?: item.archivedFrom ?: item.originalFrom
            val to   = item.message.to?.bare()   ?: continue

            if (from.isBlank() || to.isBlank()) continue

            val isOutgoing = from == owner
            val opponent   = if (isOutgoing) to else from

            if (opponent == owner) {
                Log.w(TAG, "Skipping self-message: from=$from, to=$to")
                continue
            }
            val conversationType = conversationTypeByMessage(item.message)

            // Получаем информацию о чате
            val chat = realm.query<LastChatsStorageItem>(
                "owner = $0 AND jid = $1 AND conversationType_ = $2",
                owner, opponent, conversationType.rawValue
            ).first().find()

            val displayedId = chat?.displayedId?.toLongOrNull()
            val deliveredId = chat?.deliveredId?.toLongOrNull()
            val lastReadMessageDate = chat?.lastReadMessageDate ?: 0L

            // Конвертируем дату сообщения в микросекунды для сравнения
            val messageTimestampUs = item.date.time * 1000L  // миллисекунды → микросекунды

            Log.d(TAG, "Processing message for $opponent: " +
                    "displayedId=$displayedId µs, deliveredId=$deliveredId µs, " +
                    "messageTimestamp=${item.date.time} ms ($messageTimestampUs µs), outgoing=$isOutgoing")

            // Определяем состояние на основе timestamp'а
            val finalState = if (isOutgoing) {
                when {
                    displayedId != null && messageTimestampUs <= displayedId -> {
                        Log.d(TAG, "Outgoing message marked as Read: messageTimestampUs=$messageTimestampUs <= displayedId=$displayedId µs")
                        MessageSendingState.Read
                    }
                    deliveredId != null && messageTimestampUs <= deliveredId -> {
                        Log.d(TAG, "Outgoing message marked as Deliver: messageTimestampUs=$messageTimestampUs <= deliveredId=$deliveredId µs")
                        MessageSendingState.Deliver
                    }
                    else -> {
                        Log.d(TAG, "Outgoing message marked as Sent: messageTimestampUs=$messageTimestampUs")
                        MessageSendingState.Sent
                    }
                }
            } else {
                // Для входящих сообщений используем unread after
                val messageDateMs = item.date.time
                val lastReadMessageDateMs = normalizeTimestamp(lastReadMessageDate)

                val isReadByThreshold = if (lastReadMessageDateMs > 0) {
                    messageDateMs <= lastReadMessageDateMs
                } else {
                    item.isRead
                }

                if (isReadByThreshold) {
                    Log.d(TAG, "Incoming message marked as Read: messageDate=$messageDateMs <= lastReadMessageDate=$lastReadMessageDateMs")
                    MessageSendingState.Read
                } else {
                    Log.d(TAG, "Incoming message marked as Deliver: messageDate=$messageDateMs > lastReadMessageDate=$lastReadMessageDateMs")
                    MessageSendingState.Deliver
                }
            }

            val finalIsRead = if (isOutgoing) {
                // Исходящие сообщения считаются прочитанными если они Read или Deliver
                finalState == MessageSendingState.Read || finalState == MessageSendingState.Deliver
            } else {
                // Входящие сообщения прочитаны если состояние Read
                finalState == MessageSendingState.Read
            }

            val afterburnInterval = item.message.element("ephemeral", "urn:xmpp:ephemeral:0")
                ?.getAttribute("timer")?.toDoubleOrNull() ?: 0.0

            val messageItem = MessageStorageItem().apply {
                owner = this@MessageCommonReceiver.owner
                this.opponent = opponent
                outgoing = isOutgoing
                this.isRead = finalIsRead
                date = item.date.time
                sentDate = item.date.time

                if (item.message.hasElement("system", "urn:xmpp:system") ||
                    item.message.hasElement("x", "https://xabber.com/protocol/groups#system-message")
                ) {
                    configureSystemMessage(item.message, owner, opponent, item.date)
                } else {
                    configureIncomingMessage(
                        message = item.message,
                        owner = owner,
                        opponent = opponent,
                        outgoing = isOutgoing,
                        isRead = finalIsRead,
                        date = item.date,
                        isEncrypted = item.message.hasElement("encrypted")
                    )
                }

                state = finalState
                this.afterburnInterval = afterburnInterval.toLong()

                messageId = item.messageId ?: item.message.originId ?: NanoId.generate()
                updatePrimary()

                queryIds = item.queryId
                trustedSource = item.clientSyncMessage || (item.queryId?.let { messageQueryIds.contains(it) } ?: false)
                if (item.queryId != null && !item.clientSyncMessage) {
                    messageQueryIds += item.queryId
                }

                archivedId = item.message.archivedId
                    ?: item.message.element("result", "urn:xmpp:mam:2")?.getAttribute("id")
                            ?: item.message.element("archived", "urn:xmpp:mam:tmp")?.getAttribute("id")
                            ?: ""

            }

            messageItem.save(silentNotifications = true, realm = realm)
        }

        AccountManager.find(owner)?.chatMarkers?.deleteEphemeralMessages()
    }

    // Helper function to normalize timestamps to milliseconds
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

    private suspend fun enqueue(item: MessageQueueItem) {
        processMutex.withLock {
            val set = messagesQueue.value.toMutableSet()
            if (set.contains(item)) return@withLock
            set += item
            messagesQueue.value = set
        }
    }
    suspend fun storeMessagesNow() {
        processMutex.withLock {
            val items = messagesQueue.value.toList()
            messagesQueue.value = mutableSetOf()
            if (items.isNotEmpty()) {
                processQueue(items)
                AccountManager.find(owner)?.chatMarkers?.deleteEphemeralMessages()
            }
        }
    }


    // MARK: - Helpers

    private fun getOriginId(message: XMPPMessage): String? =
        message.element("origin-id", "urn:xmpp:sid:0")?.getAttribute("id")

    private fun getMAMQueryId(message: XMPPMessage): String? =
        message.element("result", "urn:xmpp:mam:2")?.getAttribute("queryid")

    private fun getDelayedDate(message: XMPPMessage): Date? =
        parseTimestamp(message, TAG)?.let { Date(it) }

    private fun getDeliveryTime(message: XMPPMessage, owner: String): Date? =
        parseTimestamp(message, TAG)?.let { Date(it) }

    private fun getArchivedMessageContainer(message: XMPPMessage): XMPPMessage? {
        return message
    }

    private fun getCarbonCopyMessageContainer(message: XMPPMessage): XMPPMessage? {
        val sent = message.element("sent", "urn:xmpp:carbons:2") ?: return null
        val forwarded = sent.element("forwarded", "urn:xmpp:forward:0") ?: return null
        val inner = forwarded.element("message", "jabber:client") ?: return null
        return XMPPMessage(inner.raw, children = message.children)
    }

    private fun isVoIPMessage(message: XMPPMessage): Boolean =
        message.element("call", "urn:xmpp:jingle:1") != null

    private fun isValidMessage(message: XMPPMessage): Boolean =
        message.raw.contains("</message>")

    fun conversationTypeByMessage(message: XMPPMessage): ConversationType {
        val to = message.to?.bare()
        return when {
            to == "favorites.redsolution.com" -> ConversationType.Favorites
            message.element("x", "https://xabber.com/protocol/groups") != null -> ConversationType.Group
            message.element("channel", "https://xabber.com/protocol/channels") != null -> ConversationType.Channel
            message.element("omemo", "urn:xmpp:omemo:2") != null -> ConversationType.Omemo
            message.element("omemo", "urn:xmpp:omemo:1") != null -> ConversationType.Omemo1
            message.element("axolotl", "eu.siacs.conversations.axolotl") != null -> ConversationType.Axolotl
            message.element("xen", "urn:xabber:xen:0") != null -> ConversationType.Notifications
            else -> ConversationType.Regular
        }
    }

    fun deleteSelfChats() {
        realm.writeBlocking {
            val selfChats = query<LastChatsStorageItem>("owner = $0 AND jid = $0", owner).find()
            delete(selfChats)
        }
    }
}