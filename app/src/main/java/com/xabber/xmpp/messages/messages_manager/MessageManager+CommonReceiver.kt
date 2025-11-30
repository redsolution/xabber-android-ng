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
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
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
import java.util.Date
import java.util.UUID
import kotlin.collections.HashSet

@RequiresApi(Build.VERSION_CODES.O)
class MessageCommonReceiver(private val owner: String) {

    private val realm: Realm by lazy { Realm.open(defaultRealmConfig()) }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val processedMessageIds = mutableSetOf<String>()
    private val messageQueryIds = mutableSetOf<String>()

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
        override fun equals(other: Any?): Boolean = (other as? MessageQueueItem)?.messageId == messageId
        override fun hashCode(): Int = messageId?.hashCode() ?: 0
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

    // MARK: - Public API

    fun receiveClientSyncRaw(
        message: XMPPMessage,
        groupchatUserCard: String? = null,
        isRead: Boolean,
        state: MessageSendingState,
        date: Date,
        readDate: Date? = null
    ): MessageQueueItem {
        val messageId = getOriginId(message) ?: message.id
        return MessageQueueItem(
            message = message,
            messageId = messageId,
            archivedFrom = message.from?.bare(),
            isRead = isRead,
            date = date,
            state = state,
            forceUnreadState = isRead,
            clientSyncMessage = true,
            queryId = getMAMQueryId(message),
            groupchatUserCard = groupchatUserCard,
            readDate = readDate
        )
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
            date = getDeliveryTime(messageBare, owner) ?: date,
            state = MessageSendingState.Deliver,
            clientSyncMessage = true,
            queryId = getMAMQueryId(message)
        )
    }

    suspend fun receiveArchived(message: XMPPMessage) {
        if (!isValidMessage(message)) return
        val messageBare = getArchivedMessageContainer(message) ?: return
        val messageId = getOriginId(messageBare) ?: messageBare.id ?: return

        if (processedMessageIds.contains(messageId)) return
        processedMessageIds.add(messageId)

        val primary = MessageStorageItem.genPrimary(messageId, owner)
        val existing = realm.query<MessageStorageItem>("primary == $0", primary).first().find()
        if (existing != null) return

        val queueItem = MessageQueueItem(
            message = messageBare,
            messageId = messageId,
            archivedFrom = messageBare.from?.bare(),
            isRead = messageBare.from?.bare() == owner,
            date = getDeliveryTime(messageBare, owner) ?: Date(),
            state = MessageSendingState.Deliver,
            queryId = getMAMQueryId(message),
            originalFrom = messageBare.from?.bare() ?: "",
            originalOutgoing = messageBare.from?.bare() == owner
        )
        enqueue(queueItem)
    }

    suspend fun receiveCarbon(message: XMPPMessage) {
        val messageBare = getCarbonCopyMessageContainer(message) ?: return
        val messageId = getOriginId(messageBare) ?: messageBare.id ?: return
        if (processedMessageIds.contains(messageId)) return

        val primary = MessageStorageItem.genPrimary(messageId, owner)
        if (realm.query<MessageStorageItem>("primary == $0", primary).first().find() != null) return

        val queueItem = MessageQueueItem(
            message = messageBare,
            messageId = messageId,
            archivedFrom = messageBare.from?.bare(),
            isRead = true,
            date = getDeliveryTime(messageBare, owner) ?: Date(),
            state = MessageSendingState.Sent,
            queryId = getMAMQueryId(message),
            originalFrom = messageBare.from?.bare() ?: "",
            originalOutgoing = messageBare.from?.bare() == owner
        )
        processedMessageIds.add(messageId)
        enqueue(queueItem)
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
            date = getDeliveryTime(message, owner) ?: Date(),
            state = if (from == owner) MessageSendingState.Deliver else MessageSendingState.Sent,
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
        val opponent = if (to != owner) to else from
        if (opponent == owner) return

        val queueItem = MessageQueueItem(
            message = message,
            messageId = messageId,
            archivedFrom = from,
            isRead = from == owner,
            date = getDeliveryTime(message, owner) ?: Date(),
            state = if (from == owner) MessageSendingState.Deliver else MessageSendingState.Sent,
            originalFrom = from,
            originalOutgoing = from == owner
        )
        enqueue(queueItem)
    }

    fun updateReadDate(messageId: String, stanzaId: String, jid: String, date: Date) {
        prereadedMessages.add(PrereadedMessagesItem(messageId, stanzaId, date, jid))
    }

    // MARK: - Queue Processing

    @OptIn(FlowPreview::class)
    internal fun subscribeReceiver() {
        scope.launch {
            messagesQueue
                .debounce(1)
                .collect { items ->
                    if (items.isNotEmpty()) {
                        processQueue(items.toList())
                        AccountManager.find(owner)?.chatMarkers?.deleteEphemeralMessages()
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

    private fun processQueue(items: List<MessageQueueItem>) {
        val sorted = items.sortedBy { it.date }
        for (item in sorted) {
            if (isVoIPMessage(item.message)) continue

            val from = item.message.from?.bare() ?: item.archivedFrom ?: item.originalFrom
            val to = item.message.to?.bare() ?: continue
            if (to == owner && from == owner) continue

            val opponent = if (to != owner) to else from

            // Определяем исходящее (точно как в Swift)
            val isOutgoing = if (item.message.hasElement("x", "https://xabber.com/protocol/groups")) {
                item.message.element("x", "https://xabber.com/protocol/groups")
                    ?.element("reference")?.element("user", "https://xabber.com/protocol/groups")
                    ?.getAttribute("id") == owner
            } else {
                from == owner
            }

            val conversationType = conversationTypeByMessage(item.message)

            // preread
            var isRead = item.isRead
            val readDate = item.readDate
                ?: prereadedMessages.firstOrNull { it.messageId == item.messageId }?.date
                ?: prereadedConversation.firstOrNull { it.jid == opponent && it.conversationType == conversationType }?.date

            if (readDate != null && item.date < readDate) {
                isRead = true
            }

            val afterburnInterval = item.message.element("ephemeral", "urn:xmpp:ephemeral:0")
                ?.getAttribute("timer")?.toDoubleOrNull() ?: 0.0

            val messageItem = MessageStorageItem().apply {
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
                        isRead = isRead,
                        date = item.date,
                        isEncrypted = item.message.hasElement("encrypted")
                    )
                }

                state = item.state
                this.afterburnInterval = afterburnInterval.toLong()
                if (afterburnInterval > 0 && readDate != null) {
                    this.readDate = readDate.time / 1000
                    this.burnDate = (readDate.time / 1000) + afterburnInterval.toLong()
                    if (this.burnDate <= System.currentTimeMillis() / 1000) {
                        this.isDeleted = true
                        this.body = ""
                    }
                }

                queryIds = item.queryId
                trustedSource = item.clientSyncMessage || (item.queryId?.let { messageQueryIds.contains(it) } ?: false)
                if (item.queryId != null && !item.clientSyncMessage) {
                    messageQueryIds += item.queryId
                }

                messageId = item.messageId ?: item.message.originId ?: NanoId.generate()
                updatePrimary()
            }

            messageItem.save(realm, commitTransaction = true, silentNotifications = false)
        }
    }

    private fun enqueue(item: MessageQueueItem) {
        val set = messagesQueue.value.toMutableSet()
        set += item
        messagesQueue.value = set
    }

    suspend fun storeMessagesNow() {
        val items = messagesQueue.value.toList()
        messagesQueue.value = mutableSetOf()
        processQueue(items)
        AccountManager.find(owner)?.chatMarkers?.deleteEphemeralMessages()
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
        val forwarded = message.element("forwarded", "urn:xmpp:forward:0") ?: return null
        val inner = forwarded.element("message", "jabber:client") ?: return null
        return XMPPMessage(inner.raw, children = message.children)
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