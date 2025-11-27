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
    companion object {
        private const val TAG = "MessageCommonReceiver"
    }
    private var queueJob: Job? = null  // ← Это и есть наш "DisposeBag"!
    private val messagesQueue = MutableStateFlow(mutableSetOf<MessageQueueItem>())
    private val chatMarkers = ChatMarkersManager(owner)
    private val messageQueryIds = mutableSetOf<String>()

    data class PrereadedMessagesItem(
        val messageId: String,
        val stanzaId: String,
        val date: Date,
        val jid: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PrereadedMessagesItem) return false
            return messageId == other.messageId && jid == other.jid && stanzaId == other.stanzaId
        }

        override fun hashCode(): Int {
            var result = messageId.hashCode()
            result = 31 * result + stanzaId.hashCode()
            result = 31 * result + jid.hashCode()
            return result
        }
    }

    data class PrereadedConversationItem(
        val conversationType: ConversationType,
        val date: Date,
        val jid: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PrereadedConversationItem) return false
            return conversationType == other.conversationType && jid == other.jid
        }

        override fun hashCode(): Int {
            var result = conversationType.rawValue.hashCode()
            result = 31 * result + jid.hashCode()
            return result
        }
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
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MessageQueueItem) return false
            return messageId == other.messageId
        }

        override fun hashCode(): Int {
            return messageId?.hashCode() ?: 0
        }
    }

    init {
        subscribeReceiver()
    }

    fun receiveClientSyncRaw(
        message: XMPPMessage,
        groupchatUserCard: String? = null,
        isRead: Boolean,
        state: MessageSendingState,
        date: Date,
        readDate: Date? = null
    ): MessageQueueItem {
        val messageId = getOriginId(message) ?: message.id

        val queueItem = MessageQueueItem(
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
        return queueItem
    }



    fun receiveTemporary(message: XMPPMessage): MessageQueueItem {
        val date = getDelayedDate(message)
        val messageBare = getArchivedMessageContainer(message)
        val messageId = getOriginId(messageBare!!) ?: messageBare.id
        return MessageQueueItem(
            message = messageBare,
            messageId = messageId,
            archivedFrom = message.from?.bare(),
            isRead = (message.from?.bare() == owner),
            date = getDeliveryTime(messageBare, owner)!!,
            state = MessageSendingState.Deliver,
            clientSyncMessage = true,
            queryId = getMAMQueryId(message)
        )
    }

    suspend fun receiveArchived(message: XMPPMessage) {
        if (!isValidMessage(message)) {
            Log.w(TAG, "Skipping incomplete archived message: messageId=${message.id}")
            return
        }
        val messageBare = getArchivedMessageContainer(message)
        val messageId = getOriginId(messageBare!!) ?: messageBare.id

        if (processedMessageIds.contains(messageId)) {
            return
        }

        val primary = MessageStorageItem.genPrimary(messageId!!, owner)
        val innerTimestamp = Date(parseTimestamp(messageBare, TAG)!!)

        val queryId = getMAMQueryId(message)
        val existing = realm.query<MessageStorageItem>(
            "primary = $0 OR (archivedId = $1 AND archivedId != '' AND conversationType_ = $2)",
            primary, messageId, conversationTypeByMessage(messageBare).rawValue
        ).first().find()
        if (existing != null) {
            return
        }
        val queueItem = MessageQueueItem(
            message = messageBare,
            messageId = messageId,
            archivedFrom = messageBare.from?.bare(),
            isRead = messageBare.from?.bare() == owner,
            date = innerTimestamp,
            state = MessageSendingState.Deliver,
            queryId = queryId,
            originalFrom = messageBare.from?.bare() ?: "",
            originalOutgoing = messageBare.from?.bare() == owner
        )
        processedMessageIds.add(messageId)
        enqueue(queueItem)

        storeMessagesNow()

    }

    suspend fun receiveCarbon(message: XMPPMessage) {
        val messageBare = getCarbonCopyMessageContainer(message)
        val messageId = getOriginId(messageBare!!) ?: messageBare.id
        if (processedMessageIds.contains(messageId)) {
            return
        }
        val primary = messageId?.let { MessageStorageItem.genPrimary(it, owner) }
        if (primary != null && realm.query<MessageStorageItem>("primary = $0", primary).first().find() != null) {
            return
        }
        val deliveryTime = parseTimestamp(messageBare, TAG)?.let { Date(it) }
        val queueItem = MessageQueueItem(
            message = messageBare,
            messageId = messageId,
            archivedFrom = messageBare.from?.bare(),
            isRead = true,
            date = deliveryTime!!,
            state = MessageSendingState.Sent,
            queryId = getMAMQueryId(message),
            originalFrom = messageBare.from?.bare() ?: "",
            originalOutgoing = messageBare.from?.bare() == owner
        )
        if (messageId != null) {
            processedMessageIds.add(messageId)
        }
        enqueue(queueItem)
    }

    suspend fun receiveCarbonForwarded(message: XMPPMessage) {
        val messageId = getOriginId(message) ?: message.id
        if (messageId == "388774f9-3793-4a94-9c11-47ec82345440") {
        }
        if (message.body.isNullOrEmpty()) {
            return
        }
        val primary = MessageStorageItem.genPrimary(messageId!!, owner)
        if (realm.query<MessageStorageItem>("primary = $0", primary).first().find() != null) {
            return
        }
        val from = message.from?.bare()
        val to = message.to?.bare()
        val opponent = if (to != owner) to else from
        if (opponent == owner) {
            return
        }
        val deliveryTime = parseTimestamp(message, TAG)?.let { Date(it) }
        val queueItem = MessageQueueItem(
            message = message,
            messageId = messageId,
            archivedFrom = from,
            isRead = from == owner,
            date = deliveryTime!!,
            state = if (from == owner) MessageSendingState.Deliver else MessageSendingState.Sent,
            queryId = getMAMQueryId(message),
            originalFrom = from!!,
            originalOutgoing = from == owner
        )
        enqueue(queueItem)
    }

    private fun isValidMessage(message: XMPPMessage): Boolean {
        return try {
            message.raw.contains("</message>")
        } catch (e: Exception) {
            false
        }
    }

    suspend fun receiveRuntime(message: XMPPMessage) {
        if (message.body.isNullOrBlank()) {
            Log.d(TAG, "Skipping runtime message without body")
            return
        }

        val messageId = getOriginId(message) ?: message.id ?: return
        val from = message.from?.bare() ?: return
        val to = message.to?.bare() ?: return
        val opponent = if (to != owner) to else from
        if (opponent == owner) return

        val isOutgoing = from == owner
        val deliveryTime = message.date?.let { Date(it) } ?: Date()

        val queueItem = MessageQueueItem(
            message = message,
            messageId = messageId,
            archivedFrom = from,
            isRead = isOutgoing,
            date = deliveryTime,
            state = if (isOutgoing) MessageSendingState.Deliver else MessageSendingState.Sent,
            originalFrom = from,
            originalOutgoing = isOutgoing
        )
        enqueue(queueItem)
    }

    fun updateReadDate(messageId: String, stanzaId: String, jid: String, date: Date) {
        prereadedMessages.add(PrereadedMessagesItem(messageId, stanzaId, date, jid))
    }

    private val prereadedMessages = mutableSetOf<PrereadedMessagesItem>()
    private val prereadedConversation = mutableSetOf<PrereadedConversationItem>()



    @OptIn(FlowPreview::class)
    fun subscribeReceiver() {
        queueJob?.cancel()
        queueJob = scope.launch {
            messagesQueue
                .debounce(1) // ← 1 мс — как в Swift, почти мгновенно, но без спама
                .collect { results ->
                    if (results.isNotEmpty()) {
                        val copy = results.toSet()
                        processQueue(copy) { saved ->
                            saved?.let {
                                scope.launch {
                                    save(it)
                                }
                            }
                        }
                        AccountManager.find(owner)?.chatMarkers?.deleteEphemeralMessages()
                    }
                }
        }
    }

    fun unsubscribeReceiver() {
        queueJob?.cancel()
        queueJob = null
        clearQueue()
    }

    private fun processQueue(items: Set<MessageQueueItem>, callback: (List<MessageStorageItem>?) -> Unit) {
        if (items.isEmpty()) return callback(null)

        val sorted = items.sortedBy { it.date }
        val result = mutableListOf<MessageStorageItem>()

        for (item in sorted) {
            if (isVoIPMessage(item.message)) continue

            val instance = MessageStorageItem()
            val from = item.message.from?.bare() ?: item.archivedFrom ?: item.originalFrom
            val to = item.message.to?.bare() ?: continue
            if (to == owner && from == owner) continue

            val opponent = if (to != owner) to else from

            // Определяем originalOutgoing (точно как в Swift)
            item.originalOutgoing = if (item.message.hasElement("x", "https://xabber.com/protocol/groups")) {
                val userId = item.message.element("x", "https://xabber.com/protocol/groups")
                    ?.element("reference")?.element("user", "https://xabber.com/protocol/groups")
                    ?.getAttribute("id")
                userId == owner
            } else {
                from == owner
            }

            val conversationType = conversationTypeByMessage(item.message)

            // isRead с учётом prereaded
            var finalIsRead = item.isRead
            val readDate = item.readDate
                ?: prereadedMessages.firstOrNull { it.messageId == item.messageId }?.date
                ?: prereadedConversation.firstOrNull { it.jid == opponent && it.conversationType == conversationType }?.date

            if (readDate != null && item.date < readDate) {
                finalIsRead = true
            }

            // Afterburn
            val afterburnInterval = item.message.element("ephemeral", "urn:xmpp:ephemeral:0")
                ?.getAttribute("timer")?.toDoubleOrNull() ?: 0.0

            if (item.message.hasElement("system", "urn:xmpp:system") ||
                item.message.hasElement("x", "https://xabber.com/protocol/groups#system-message")) {
                instance.configureSystemMessage(item.message, owner, opponent, item.date)
            } else {
                instance.configureIncomingMessage(
                    message = item.message,
                    owner = owner,
                    opponent = opponent,
                    outgoing = item.originalOutgoing,
                    isRead = finalIsRead,
                    date = item.date,
                    isEncrypted = item.message.hasElement("encrypted")
                )
            }

            instance.state = item.state
            instance.afterburnInterval = afterburnInterval.toLong()
            if (afterburnInterval > 0 && readDate != null) {
                instance.readDate = readDate.time / 1000
                instance.burnDate = (readDate.time / 1000) + afterburnInterval.toLong()
                if (instance.burnDate <= System.currentTimeMillis() / 1000) {
                    instance.isDeleted = true
                    instance.body = ""
                }
            }

            instance.queryIds = item.queryId
            instance.trustedSource = item.clientSyncMessage || (item.queryId?.let { messageQueryIds.contains(it) } ?: false)
            if (item.queryId != null && !item.clientSyncMessage) {
                messageQueryIds += item.queryId
            }

            instance.messageId = item.messageId ?: item.message.originId ?: NanoId.generate()
            instance.updatePrimary()

            result += instance
        }

        callback(result)
        items.forEach { clearQueue(it) }
    }

    private fun isChatMarker(message: XMPPMessage): Boolean {
        val forwarded = getCarbonCopyMessageContainer(message) ?: getArchivedMessageContainer(message) ?: return false
        return forwarded.element("received", namespace = "urn:xmpp:chat-markers:0") != null ||
                forwarded.element("displayed", namespace = "urn:xmpp:chat-markers:0") != null ||
                forwarded.element("acknowledged", namespace = "urn:xmpp:chat-markers:0") != null
    }

    private fun extractMarkerId(message: XMPPMessage): String? {
        val forwarded = getCarbonCopyMessageContainer(message) ?: getArchivedMessageContainer(message) ?: return null
        return forwarded.element("received", namespace = "urn:xmpp:chat-markers:0")?.getAttribute("id")
            ?: forwarded.element("displayed", namespace = "urn:xmpp:chat-markers:0")?.getAttribute("id")
            ?: forwarded.element("acknowledged", namespace = "urn:xmpp:chat-markers:0")?.getAttribute("id")
    }

    private fun enqueue(item: MessageQueueItem) {
        val set = messagesQueue.value.toMutableSet()
        set += item
        messagesQueue.value = set
    }

    private fun enqueue(collection: List<MessageQueueItem>) {
        val set = messagesQueue.value.toMutableSet()
        set += collection
        messagesQueue.value = set
    }

    private fun clearQueue(item: MessageQueueItem) {
        val set = messagesQueue.value.toMutableSet()
        set -= item
        messagesQueue.value = set
    }

    private fun clearQueue() {
        messagesQueue.value = mutableSetOf()
    }

    private suspend fun save(messages: List<MessageStorageItem>, silentNotifications: Boolean = false) {
        if (messages.isEmpty()) return

        try {
            realm.writeBlocking {
                messages.forEach { msg ->
                    // Дедупликация по archivedId (если пришло из MAM)
                    if (!msg.archivedId.isNullOrBlank()) {
                        val existingByArchived = query<MessageStorageItem>(
                            "archivedId = $0 AND archivedId != '' AND owner = $1",
                            msg.archivedId, owner
                        ).first().find()

                        if (existingByArchived != null) {
                            Log.d(TAG, "Message already exists by archivedId=${msg.archivedId}, skipping save")
                            return@forEach
                        }
                    }

                    // Сохраняем сам объект
                    val savedMessage = copyToRealm(msg, UpdatePolicy.ALL)

                    // Сохраняем станзу (если нужно)
                    savedMessage.storeStanza(this)

                    // Обновляем LastChatsStorageItem
                    val conversationType = ConversationType.fromRaw(savedMessage.conversationType_)
                    val chatPrimary = LastChatsStorageItem.genPrimary(savedMessage.opponent, owner, conversationType)
                    val chat = query<LastChatsStorageItem>("primary = $0", chatPrimary).first().find()
                    if (chat != null) {
                        findLatest(chat)?.apply {
                            val currentLastDate = lastMessage?.sentDate ?: 0L

                            // Обновляем lastMessage и messageDate ТОЛЬКО если новое сообщение новее
                            if (savedMessage.sentDate > currentLastDate) {
                                lastMessage = savedMessage
                                lastMessageId = savedMessage.archivedId.takeIf { it.isNotBlank() } ?: savedMessage.messageId
                                messageDate = savedMessage.sentDate  // теперь можно без проверки, т.к. уже > current
                            }
                        }
                    }
                }
            }


            // Удаляем сгоревшие сообщения (afterburn)
            AccountManager.find(owner)?.chatMarkers?.deleteEphemeralMessages()

        } catch (e: Exception) {
            Log.e(TAG, "Error saving messages: ${e.message}", e)
        }
    }

    suspend fun storeMessagesNow() {
        val copy = messagesQueue.value.toSet()
        clearQueue()
        processQueue(copy) { saved ->
            saved?.let {
                scope.launch {
                    save(it)
                }
            }
        }
        AccountManager.find(owner)?.chatMarkers?.deleteEphemeralMessages()
    }


    private fun getOriginId(message: XMPPMessage): String? {
        return message.element("origin-id", namespace = "urn:xmpp:sid:0")?.getAttribute("id")
    }

    private fun getMAMQueryId(message: XMPPMessage): String? {
        return message.element("result", namespace = "urn:xmpp:mam:2")?.getAttribute("queryid")
    }

    private fun getDelayedDate(message: XMPPMessage): Date? {
        return parseTimestamp(message, TAG)?.let { Date(it) }
    }

    private fun getDeliveryTime(message: XMPPMessage, owner: String): Date? {
        return parseTimestamp(message, TAG)?.let { Date(it) }
    }

    private fun getArchivedMessageContainer(message: XMPPMessage): XMPPMessage? {
        val forwarded = message.element("forwarded", namespace = "urn:xmpp:forward:0")
        return forwarded?.element("message", namespace = "jabber:client")?.let { XMPPMessage(it.raw, children = message.children) }
    }

    private fun getCarbonCopyMessageContainer(message: XMPPMessage): XMPPMessage? {
        val sent = message.element("sent", namespace = "urn:xmpp:carbons:2")
        return sent?.element("forwarded", namespace = "urn:xmpp:forward:0")?.element("message", namespace = "jabber:client")?.let { XMPPMessage(it.raw, children = message.children) }
    }

    private fun isVoIPMessage(message: XMPPMessage): Boolean {
        return message.element("call", namespace = "urn:xmpp:jingle:1") != null
    }

    private fun parseSystemMessageMetadata(message: XMPPMessage): Map<String, Any>? {
        val system = message.element("system", namespace = "some_system_namespace")
        return system?.let { mapOf("system" to it.raw) }
    }

    fun conversationTypeByMessage(message: XMPPMessage): ConversationType {
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
        }
    }


    fun deleteSelfChats() {
        realm.writeBlocking {
            val selfChats = query<LastChatsStorageItem>("owner = $0 AND jid = $0", owner).find()
            delete(selfChats)
        }
    }
}