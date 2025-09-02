package com.xabber.xmpp.messages.messages_manager

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.common.AccountManager
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
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import kotlin.collections.HashSet

@RequiresApi(Build.VERSION_CODES.O)
class MessageCommonReceiver(private val owner: String) {
    private val realm: Realm by lazy { Realm.open(defaultRealmConfig()) }
    private val queue: String = "com.xabber.messages.transmitter.$owner.${UUID.randomUUID()}"
    private val messagesQueue = MutableStateFlow<Set<MessageQueueItem>>(HashSet())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processedMessageIds = mutableSetOf<String>() // Track processed message IDs
    companion object {
        private const val TAG = "MessageCommonReceiver"
    }

    init {
        subscribeReceiver()
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

    fun receiveClientSyncRaw(
        message: XMPPMessage,
        groupchatUserCard: String? = null,
        isRead: Boolean,
        state: MessageSendingState,
        date: Date,
        readDate: Date? = null
    ): MessageQueueItem? {
        val messageId = getOriginId(message) ?: message.id
        Log.d(
            TAG,
            "receiveClientSyncRaw called: messageId=$messageId, from=${message.from?.bare()}, to=${message.to?.bare()}, body=${message.body?.take(100)}, isRead=$isRead, state=$state, date=$date"
        )
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
        Log.d(TAG, "Created MessageQueueItem: messageId=${queueItem.messageId}, clientSyncMessage=${queueItem.clientSyncMessage}")
        return queueItem
    }

    suspend fun receiveClientSync(message: XMPPMessage, isRead: Boolean, state: MessageSendingState, date: Date) {
        val messageId = getOriginId(message) ?: message.id
        Log.d(TAG, "receiveClientSync called for messageId=$messageId")
        receiveClientSyncRaw(message, null, isRead, state, date)?.let { enqueue(it) }
    }

    fun receiveTemporary(message: XMPPMessage): MessageQueueItem? {
        val date = getDelayedDate(message) ?: return null.also {
            Log.w(TAG, "receiveTemporary failed: no delayed date for messageId=${getOriginId(message) ?: message.id}")
        }
        val messageBare = getArchivedMessageContainer(message) ?: return null.also {
            Log.w(TAG, "receiveTemporary failed: no archived message container for messageId=${getOriginId(message) ?: message.id}")
        }
        val messageId = getOriginId(messageBare) ?: messageBare.id
        Log.d(TAG, "receiveTemporary called for messageId=$messageId")
        return MessageQueueItem(
            message = messageBare,
            messageId = messageId,
            archivedFrom = message.from?.bare(),
            isRead = (message.from?.bare() == owner),
            date = getDeliveryTime(messageBare, owner) ?: date,
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
        val messageBare = getArchivedMessageContainer(message) ?: return.also {
            Log.w(TAG, "receiveArchived failed: no archived message container for messageId=${getOriginId(message) ?: message.id}")
        }
        val messageId = getOriginId(messageBare) ?: messageBare.id ?: run {
            Log.w(TAG, "Skipping archived message with no valid ID: raw=${message.raw.substring(0, minOf(message.raw.length, 200))}...")
            return
        }

        // Check for duplicates based on messageId
        if (processedMessageIds.contains(messageId)) {
            Log.d(TAG, "Skipping duplicate archived message based on messageId=$messageId")
            return
        }

        // Check for nested forwarded messages in references
        val forwardedReferences = messageBare.children.filter {
            it.name == "reference" && it.namespace == "https://xabber.com/protocol/references" &&
                    it.element("forwarded", namespace = "urn:xmpp:forward:0") != null
        }
        if (forwardedReferences.isNotEmpty()) {
            forwardedReferences.forEach { reference ->
                val forwardedMessage = reference.element("forwarded", namespace = "urn:xmpp:forward:0")
                    ?.element("message", namespace = "jabber:client")
                if (forwardedMessage != null) {
                    val forwardedMessageId = forwardedMessage.attributes["id"] ?: return@forEach
                    if (processedMessageIds.contains(forwardedMessageId)) {
                        Log.d(TAG, "Skipping nested forwarded message with messageId=$forwardedMessageId as it was already processed")
                        return
                    }
                }
            }
        }

        val primary = MessageStorageItem.genPrimary(messageId, owner)
        val innerTimestamp = parseTimestamp(messageBare, TAG)?.let { Date(it) } ?: parseTimestamp(message, TAG)?.let { Date(it) } ?: run {
            Log.e(TAG, "No valid timestamp for messageId=$messageId, using current time as fallback")
            Date()
        }
        val queryId = getMAMQueryId(message)
        val existing = realm.query<MessageStorageItem>("primary = $0", primary).first().find()
        if (existing != null && existing.sentDate >= innerTimestamp.time && queryId?.let {
                existing.queryIds?.contains(
                    it
                )
            } == true) {
            Log.d(TAG, "Skipping duplicate archived message: messageId=$messageId, primary=$primary, existing sentDate=${existing.sentDate}, new sentDate=${innerTimestamp.time}, body=${existing.body.take(100)}, queryId=$queryId")
            return
        }
        Log.d(TAG, "receiveArchived: messageId=$messageId, timestamp=$innerTimestamp, body=${messageBare.body?.take(100)}, queryId=$queryId")
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
        processedMessageIds.add(messageId) // Mark message as processed
        enqueue(queueItem)
    }

    suspend fun receiveCarbon(message: XMPPMessage) {
        val messageBare = getCarbonCopyMessageContainer(message) ?: return.also {
            Log.w(TAG, "receiveCarbon failed: no carbon copy message container for messageId=${getOriginId(message) ?: message.id}")
        }
        val messageId = getOriginId(messageBare) ?: messageBare.id
        if (processedMessageIds.contains(messageId)) {
            Log.d(TAG, "Skipping duplicate carbon message based on messageId=$messageId")
            return
        }
        val primary = messageId?.let { MessageStorageItem.genPrimary(it, owner) }
        if (primary != null && realm.query<MessageStorageItem>("primary = $0", primary).first().find() != null) {
            Log.d(TAG, "Skipping duplicate carbon message: messageId=$messageId, primary=$primary")
            return
        }
        val deliveryTime = parseTimestamp(messageBare, TAG)?.let { Date(it) } ?: return.also {
            Log.w(TAG, "receiveCarbon failed: no valid timestamp for messageId=$messageId")
        }
        Log.d(TAG, "receiveCarbon called for messageId=$messageId, timestamp=$deliveryTime")
        val queueItem = MessageQueueItem(
            message = messageBare,
            messageId = messageId,
            archivedFrom = messageBare.from?.bare(),
            isRead = false,
            date = deliveryTime,
            state = MessageSendingState.Sent,
            queryId = getMAMQueryId(message),
            originalFrom = messageBare.from?.bare() ?: "",
            originalOutgoing = messageBare.from?.bare() == owner
        )
        if (messageId != null) {
            processedMessageIds.add(messageId)
        } // Mark message as processed
        enqueue(queueItem)
    }

    suspend fun receiveCarbonForwarded(message: XMPPMessage) {
        val messageId = getOriginId(message) ?: message.id ?: run {
            Log.w(TAG, "Skipping carbon forwarded message with no valid ID: raw=${message.raw.substring(0, minOf(message.raw.length, 200))}...")
            return
        }
        if (processedMessageIds.contains(messageId)) {
            Log.d(TAG, "Skipping duplicate carbon forwarded message based on messageId=$messageId")
            return
        }
        if (message.body.isNullOrEmpty()) {
            Log.d(TAG, "Skipping carbon forwarded message with no body: messageId=$messageId")
            return
        }
        val primary = MessageStorageItem.genPrimary(messageId, owner)
        if (realm.query<MessageStorageItem>("primary = $0", primary).first().find() != null) {
            Log.d(TAG, "Skipping duplicate carbon forwarded message: messageId=$messageId, primary=$primary")
            return
        }
        val from = message.from?.bare() ?: return.also {
            Log.w(TAG, "receiveCarbonForwarded failed: no from JID for messageId=$messageId")
        }
        val to = message.to?.bare() ?: return.also {
            Log.w(TAG, "receiveCarbonForwarded failed: no to JID for messageId=$messageId")
        }
        val opponent = if (to != owner) to else from
        val deliveryTime = parseTimestamp(message, TAG)?.let { Date(it) } ?: return.also {
            Log.w(TAG, "receiveCarbonForwarded failed: no valid timestamp for messageId=$messageId")
        }
        Log.d(TAG, "receiveCarbonForwarded called for messageId=$messageId, timestamp=$deliveryTime")
        val queueItem = MessageQueueItem(
            message = message,
            messageId = messageId,
            archivedFrom = from,
            isRead = from == owner,
            date = deliveryTime,
            state = if (from == owner) MessageSendingState.Deliver else MessageSendingState.Sent,
            queryId = getMAMQueryId(message),
            originalFrom = from,
            originalOutgoing = from == owner
        )
        processedMessageIds.add(messageId) // Mark message as processed
        enqueue(queueItem)
    }

    private fun isValidMessage(message: XMPPMessage): Boolean {
        return try {
            message.raw.contains("</message>").also {
                if (!it) Log.w(TAG, "Invalid message (incomplete): messageId=${message.id}, raw=${message.raw.take(200)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate message: ${e.message}")
            false
        }
    }

    suspend fun receiveRuntime(message: XMPPMessage) {
        if (!isValidMessage(message)) {
            Log.w(TAG, "Skipping incomplete runtime message: messageId=${message.id}")
            return
        }
        if (message.element("result", namespace = "urn:xmpp:mam:2") != null || message.raw.contains("urn:xmpp:mam:2")) {
            Log.d(TAG, "Detected MAM message in receiveRuntime, redirecting to receiveArchived: messageId=${getOriginId(message) ?: message.id}")
            receiveArchived(message)
            return
        }
        val messageId = getOriginId(message) ?: message.id
        if (processedMessageIds.contains(messageId)) {
            Log.d(TAG, "Skipping duplicate runtime message based on messageId=$messageId")
            return
        }
        Log.d(TAG, "receiveRuntime called for messageId=$messageId, body=${message.body?.take(100)}")
        val primary = messageId?.let { MessageStorageItem.genPrimary(it, owner) }
        if (primary != null) {
            val deliveryTime = parseTimestamp(message, TAG)?.let { Date(it) } ?: Date()
            val existing = realm.query<MessageStorageItem>("primary = $0", primary).first().find()
            if (existing != null && existing.sentDate >= deliveryTime.time) {
                Log.d(TAG, "Skipping duplicate runtime message: messageId=$messageId, primary=$primary, existing sentDate=${existing.sentDate}, new sentDate=${deliveryTime.time}, body=${existing.body.take(100)}")
                return
            }
        } else {
            Log.w(TAG, "Skipping message without ID: body=${message.body?.take(100)}")
            return
        }
        val from = message.from?.bare()
        val to = message.to?.bare()
        if (from == null || to == null) {
            Log.w(TAG, "Skipping runtime message with missing from/to: messageId=$messageId, from=$from, to=$to, body=${message.body?.take(100)}")
            return
        }
        val opponent = if (to != owner) to else from
        val isOutgoing = from == owner
        val deliveryTime = parseTimestamp(message, TAG)?.let { Date(it) } ?: Date()
        val queueItem = MessageQueueItem(
            message = message,
            messageId = messageId,
            archivedFrom = from,
            isRead = isOutgoing,
            date = deliveryTime,
            state = if (isOutgoing) MessageSendingState.Deliver else MessageSendingState.Sent,
            queryId = getMAMQueryId(message),
            originalFrom = from,
            originalOutgoing = isOutgoing
        )
        processedMessageIds.add(messageId) // Mark message as processed
        enqueue(queueItem)
        Log.d(TAG, "Enqueued runtime message: messageId=$messageId, opponent=$opponent, isOutgoing=$isOutgoing, timestamp=$deliveryTime")
    }

    fun updateReadDate(messageId: String, stanzaId: String, jid: String, date: Date) {
        Log.d(TAG, "updateReadDate called: messageId=$messageId, stanzaId=$stanzaId, jid=$jid, date=$date")
        prereadedMessages.add(PrereadedMessagesItem(messageId, stanzaId, date, jid))
    }

    private val prereadedMessages = mutableSetOf<PrereadedMessagesItem>()
    private val prereadedConversation = mutableSetOf<PrereadedConversationItem>()

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
        processedMessageIds.clear() // Clear processed message IDs on queue clear
    }

    fun subscribeReceiver() {
        Log.d(TAG, "Subscribing receiver for owner $owner")
        scope.launch {
            messagesQueue.collect { results ->
                Log.d(TAG, "Collected ${results.size} items: ${results.map { it.messageId }}")
                processQueue(results) { messages ->
                    messages?.let { save(it) }
                }
                AccountManager.find(owner)?.chatMarkers?.deleteEphemeralMessages()
            }
        }
    }

    fun unsubscribeReceiver() {
        Log.d(TAG, "Unsubscribing receiver for owner $owner")
        clearQueue()
    }

    suspend fun processQueue(items: Set<MessageQueueItem>, callback: suspend (List<MessageDto>?) -> Unit) {
        if (items.isEmpty()) {
            Log.d(TAG, "No items in queue to process")
            callback(null)
            return
        }
        Log.d(TAG, "Processing queue with ${items.size} items: ${items.map { it.messageId }}")
        items.forEach { item ->
            Log.d(
                TAG,
                "Queue item: messageId=${item.messageId}, from=${item.message.from?.bare()}, to=${item.message.to?.bare()}, body=${item.message.body?.take(100)}, state=${item.state}, isRead=${item.isRead}, date=${Date(item.date.time)}, queryId=${item.queryId}"
            )
        }
        val messageQueryIds = mutableSetOf<String>()
        val out = mutableListOf<MessageDto>()
        val sortedItems = items.sortedBy { it.date.time } // Ascending for oldest first

        realm.write {
            sortedItems.forEach { item ->
                val messageId = item.messageId ?: return@forEach
                var primary = MessageStorageItem.genPrimary(messageId, owner)
                val existing = query<MessageStorageItem>("primary = $0", primary).first().find()
                if (existing != null && existing.sentDate >= item.date.time && item.queryId?.let {
                        existing.queryIds?.contains(
                            it
                        )
                    } == true) {
                    Log.d(TAG, "Skipping duplicate in processQueue: messageId=$messageId, primary=$primary, sentDate=${existing.sentDate}, body=${existing.body.take(100)}, queryId=${item.queryId}")
                    return@forEach
                }
                val references = realmListOf<MessageReferenceStorageItem>()
                item.message.children.forEach { child ->
                    if (child.name == "reference" && child.namespace == "https://xabber.com/protocol/references") {
                        val forwardedMessage = child.element("forwarded", namespace = "urn:xmpp:forward:0")
                            ?.element("message", namespace = "jabber:client")
                        if (forwardedMessage != null) {
                            val forwardedMessageId = forwardedMessage.attributes["id"] ?: return@forEach
                            if (processedMessageIds.contains(forwardedMessageId)) {
                                Log.d(TAG, "Skipping reference with forwarded messageId=$forwardedMessageId as it was already processed")
                                return@forEach
                            }
                        }
                        val ref = copyToRealm(MessageReferenceStorageItem().apply {
                            primary = "${child.attributes["id"]}_${System.currentTimeMillis()}"
                            uri = child.attributes["uri"]
                            mimeType = child.attributes["type"].toString()
                            isGeo = child.attributes["type"] == "geo"
                            latitude = child.attributes["latitude"]?.toDoubleOrNull() ?: 0.0
                            longitude = child.attributes["longitude"]?.toDoubleOrNull() ?: 0.0
                            isAudioMessage = child.attributes["type"]?.contains("audio") == true
                            fileName = child.attributes["name"].toString()
                            fileSize = child.attributes["size"]?.toLongOrNull() ?: 0L
                        })
                        references.add(ref)
                    }
                }
                val message = copyToRealm(MessageStorageItem().apply {
                    this.primary = primary
                    owner = this@MessageCommonReceiver.owner
                    opponent = item.originalFrom.removeSuffix("/${item.message.from?.resource}")
                    body = item.message.body ?: ""
                    date = item.date.time
                    sentDate = item.date.time
                    outgoing = item.originalOutgoing
                    isRead = item.isRead
                    conversationType_ = if (item.groupchatUserCard != null) "https://xabber.com/protocol/groups" else "urn:xabber:chat"
                    this.references = references
                    queryIds = item.queryId
                    archivedId = messageId
                })
                out.add(
                    MessageDto(
                        primary = message.primary,
                        isOutgoing = message.outgoing,
                        owner = message.owner,
                        opponentJid = message.opponent,
                        messageBody = message.body,
                        messageSendingState = item.state,
                        sentTimestamp = message.sentDate,
                        editTimestamp = message.editDate,
                        displayType = MessageDisplayType.Text,
                        canEditMessage = message.outgoing,
                        canDeleteMessage = message.outgoing,
                        urlAvatar = null,
                        isGroup = message.conversationType_ == "https://xabber.com/protocol/groups",
                        kind = null,
                        isSelected = false,
                        references = message.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                        isUnread = !message.isRead,
                        isChecked = false,
                        archivedId = messageId
                    )
                )
                Log.d(TAG, "Processed queue item to MessageDto: primary=$primary, sentTimestamp=${message.sentDate}, body=${message.body.take(50)}, queryId=${item.queryId}, opponent=${message.opponent}")
            }
        }

        Log.d(TAG, "Prepared ${out.size} MessageDTOs for saving")
        callback(out.sortedBy { it.sentTimestamp }) // Ascending for oldest first
        items.forEach { clearQueue(it) }
    }

    private suspend fun enqueue(item: MessageQueueItem) {
        Log.d(TAG, "Enqueuing item: messageId=${item.messageId}")
        val current = messagesQueue.value.toMutableSet()
        current.add(item)
        messagesQueue.value = current
        Log.d(TAG, "Queue updated: size=${messagesQueue.value.size}, items=${messagesQueue.value.map { it.messageId }}")
    }

    private suspend fun save(messages: List<MessageDto>) {
        Log.d(TAG, "save called with ${messages.size} messages")
        try {
            messages.forEach { message ->
                Log.d(TAG, "Saving MessageDto: primary=${message.primary}, sentTimestamp=${message.sentTimestamp}, body=${message.messageBody.take(50)}, opponentJid=${message.opponentJid}")
                val conversationType = ConversationType.fromRaw(if (message.isGroup) "https://xabber.com/protocol/groups" else "urn:xabber:chat")
                val chatId = LastChatsStorageItem.genPrimary(message.opponentJid, message.owner, conversationType)
                val chatViewModel = AccountManager.getChatViewModel(chatId)
                if (chatViewModel != null) {
                    chatViewModel.insertMessagesFromReceiver(listOf(message))
                    Log.d(TAG, "Notified ChatViewModel for chatId=$chatId with message ${message.primary}")
                } else {
                    Log.w(TAG, "ChatViewModel not found for chatId=$chatId, messageId=${message.primary}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving messages: ${e.message}", e)
        }
    }

    suspend fun unsafeSave(messages: List<MessageStorageItem>) {
        realm.write {
            messages.forEach { message ->
                copyToRealm(message, UpdatePolicy.ALL)
            }
        }
        Log.d(TAG, "Unsafe saved ${messages.size} MessageStorageItems")
    }

    fun storeMessagesNow() {
        Log.d(TAG, "storeMessagesNow called for owner $owner")
        val results = messagesQueue.value
        messagesQueue.value = HashSet()
        Log.d(TAG, "storeMessagesNow: Processing ${results.size} queued items: ${results.map { it.messageId }}")
        CoroutineScope(Dispatchers.IO).launch {
            processQueue(results) { messages ->
                messages?.let { save(it) }
            }
            AccountManager.find(owner)?.chatMarkers?.deleteEphemeralMessages()
        }
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

    private fun getCarbonForwardedMessageContainer(message: XMPPMessage): XMPPMessage? {
        return getCarbonCopyMessageContainer(message)
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
        }.also {
            Log.d(TAG, "Determined conversationType=${it.rawValue} for messageId=${message.id}, to=$to")
        }
    }

    private fun getPreviousId(message: XMPPMessage): String? {
        return message.element("previous", namespace = "some_namespace")?.getAttribute("id")
    }

    private fun getMessageAuthorGroupchat(references: List<XMLElement>, jid: String): String? {
        val groupchatRef = references.firstOrNull {
            it.element("user", namespace = "https://xabber.com/protocol/groups") != null
        }
        val user = groupchatRef?.element("user", namespace = "https://xabber.com/protocol/groups")
        return user?.element("jid")?.getAttribute("stringValue") ?: run {
            user?.getAttribute("id")?.let { id ->
                realm.query<GroupChatStorageItem>("primary = $0", GroupChatStorageItem.genPrimary(jid, owner))
                    .first().find()?.jid
            }
        }
    }

    fun deleteSelfChats() {
        realm.writeBlocking {
            val selfChats = query<LastChatsStorageItem>("owner = $0 AND jid = $0", owner).find()
            delete(selfChats)
            Log.d(TAG, "Deleted ${selfChats.size} self-chats for owner=$owner")
        }
    }
}