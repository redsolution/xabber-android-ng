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
import io.realm.kotlin.Realm
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
    private val processedMessageIds = mutableSetOf<String>()
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

        if (processedMessageIds.contains(messageId)) {
            Log.d(TAG, "Skipping duplicate archived message based on messageId=$messageId")
            return
        }

        val primary = MessageStorageItem.genPrimary(messageId, owner)
        val innerTimestamp = parseTimestamp(messageBare, TAG)?.let { Date(it) } ?: parseTimestamp(message, TAG)?.let { Date(it) }

        val queryId = getMAMQueryId(message)
        val existing = realm.query<MessageStorageItem>(
            "primary = $0 OR (archivedId = $1 AND archivedId != '' AND conversationType_ = $2)",
            primary, messageId, conversationTypeByMessage(messageBare).rawValue
        ).first().find()
        if (existing != null) {
            Log.d(TAG, "Skipping duplicate archived message: messageId=$messageId, primary=$primary, existing sentDate=${existing.sentDate}, new sentDate=${innerTimestamp!!.time}, queryId=$queryId")
            return
        }
        Log.d(TAG, "receiveArchived: messageId=$messageId, timestamp=$innerTimestamp, body=${messageBare.body?.take(100)}, queryId=$queryId")
        val queueItem = MessageQueueItem(
            message = messageBare,
            messageId = messageId,
            archivedFrom = messageBare.from?.bare(),
            isRead = messageBare.from?.bare() == owner,
            date = innerTimestamp!!,
            state = MessageSendingState.Deliver,
            queryId = queryId,
            originalFrom = messageBare.from?.bare() ?: "",
            originalOutgoing = messageBare.from?.bare() == owner
        )
        processedMessageIds.add(messageId)
        enqueue(queueItem)
        CoroutineScope(Dispatchers.IO).launch {
            processQueue(messagesQueue.value) { messages ->
                messages?.let { save(it) }
            }
        }
        storeMessagesNow()

        Log.d(TAG, "Processed archived MAM synchronously: messageId=$messageId")
    }

    suspend fun receiveCarbon(message: XMPPMessage) {
        val messageBare = getCarbonCopyMessageContainer(message)
        val messageId = getOriginId(messageBare!!) ?: messageBare.id
        if (processedMessageIds.contains(messageId)) {
            Log.d(TAG, "Skipping duplicate carbon message based on messageId=$messageId")
            return
        }
        val primary = messageId?.let { MessageStorageItem.genPrimary(it, owner) }
        if (primary != null && realm.query<MessageStorageItem>("primary = $0", primary).first().find() != null) {
            Log.d(TAG, "Skipping duplicate carbon message: messageId=$messageId, primary=$primary")
            return
        }
        val deliveryTime = parseTimestamp(messageBare, TAG)?.let { Date(it) }
        Log.d(TAG, "receiveCarbon called for messageId=$messageId, timestamp=$deliveryTime")
        val queueItem = MessageQueueItem(
            message = messageBare,
            messageId = messageId,
            archivedFrom = messageBare.from?.bare(),
            isRead = false,
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
        val messageId = getOriginId(message) ?: message.id ?: run {
            Log.w(TAG, "Skipping carbon forwarded message with no valid ID: raw=${message.raw.substring(0, minOf(message.raw.length, 200))}...")
            return
        }
        if (messageId == "388774f9-3793-4a94-9c11-47ec82345440") {
            Log.d(TAG, "Processing target carbon forwarded message: messageId=$messageId, body=${message.body?.take(100)}")
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
        val from = message.from?.bare()
        val to = message.to?.bare()
        val opponent = if (to != owner) to else from
        if (opponent == owner) {
            Log.w(TAG, "Skipping self-directed message: messageId=$messageId, from=$from, to=$to")
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
        Log.d(TAG, "Enqueued carbon forwarded message: messageId=$messageId, opponent=$opponent, isOutgoing=${from == owner}, timestamp=$deliveryTime")
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
        if (messageId == "388774f9-3793-4a94-9c11-47ec82345440") {
            Log.d(TAG, "Processing target runtime message: messageId=$messageId, body=${message.body?.take(100)}")
        }
        val primary = messageId?.let { MessageStorageItem.genPrimary(it, owner) }
        if (primary != null) {
            val existing = realm.query<MessageStorageItem>("primary = $0", primary).first().find()
            if (existing != null) {
                Log.d(TAG, "Skipping duplicate runtime message: messageId=$messageId, primary=$primary, existing body=${existing.body.take(100)}")
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
        if (opponent == owner) {
            Log.w(TAG, "Skipping self-directed runtime message: messageId=$messageId, from=$from, to=$to")
            return
        }
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

        val messageDtos = mutableListOf<MessageDto>()
        val sortedItems = items.sortedBy { it.date.time }

        sortedItems.forEach { item ->
            if (isVoIPMessage(item.message)) {
                Log.d(TAG, "Skipping VoIP message: messageId=${item.messageId}")
                return@forEach
            }

            val messageId = item.messageId ?: return@forEach.also {
                Log.w(TAG, "Skipping message without ID: body=${item.message.body?.take(100)}")
            }

            var primary = MessageStorageItem.genPrimary(messageId, owner)
            if (primary == "_$owner" || primary.isEmpty()) {
                Log.w(TAG, "Skipping message with invalid primary: messageId=$messageId")
                return@forEach
            }

            // Extract archivedId from MAM stanza
            val archivedId = getOriginId(item.message) ?: item.messageId ?: messageId
            // Check for duplicates in database
            val existing = realm.query<MessageStorageItem>(
                "primary = $0 OR (archivedId = $1 AND archivedId != '' AND conversationType_ = $2)",
                primary, archivedId, conversationTypeByMessage(item.message).rawValue
            ).first().find()

            if (existing != null) {
                if (existing.body != item.message.body || existing.isRead != item.isRead || existing.sentDate != item.date.time) {
                    realm.writeBlocking {
                        findLatest(existing)?.apply {
                            body = item.message.body ?: ""
                            isRead = item.isRead
                            sentDate = item.date.time
                            Log.d(TAG, "Updated existing message: primary=$primary, archivedId=$archivedId, body=${item.message.body?.take(50)}")
                        }
                    }
                } else {
                    Log.d(TAG, "Skipping duplicate message: primary=$primary, archivedId=$archivedId")
                    return@forEach
                }
            } else {
                processedMessageIds.add(messageId)
            }

            val from = item.message.from?.bare() ?: item.archivedFrom ?: item.originalFrom
            val to = item.message.to?.bare() ?: return@forEach.also {
                Log.w(TAG, "Skipping message ${item.messageId}: no valid 'to' JID")
            }
            val opponent = if (to != owner) to else from
            if (opponent == owner) {
                Log.w(TAG, "Skipping self-directed message: messageId=$messageId, from=$from, to=$to")
                return@forEach
            }

            val isOutgoing = item.originalOutgoing || from == owner
            val conversationType = conversationTypeByMessage(item.message)
            val readDate = if (item.isRead) {
                item.readDate ?: prereadedMessages.firstOrNull { it.messageId == messageId }?.date
            } else null
            val isRead = readDate?.let { item.date.time < it.time } ?: (item.state == MessageSendingState.Read || isOutgoing)

            // Process references
            val references = realmListOf<MessageReferenceStorageItem>()
            item.message.children.forEach { child ->
                if (child.name == "reference" && child.namespace == "https://xabber.com/protocol/references") {
                    val forwardedMessage = child.element("forwarded", namespace = "urn:xmpp:forward:0")
                        ?.element("message", namespace = "jabber:client")
                    if (forwardedMessage != null) {
                        val forwardedMessageId = forwardedMessage.attributes["id"] ?: return@forEach
                        if (processedMessageIds.contains(forwardedMessageId)) {
                            Log.d(TAG, "Skipping reference with forwarded messageId=$forwardedMessageId")
                            return@forEach
                        }
                    }
                    val ref = realm.write {
                        copyToRealm(MessageReferenceStorageItem().apply {
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
                    }
                    references.add(ref)
                }
            }

            // Create MessageDto
            val messageDto = MessageDto(
                primary = primary,
                isOutgoing = isOutgoing,
                owner = owner,
                opponentJid = opponent,
                messageBody = item.message.body ?: "",
                messageSendingState = item.state,
                sentTimestamp = item.date.time,
                editTimestamp = 0,
                displayType = if (parseSystemMessageMetadata(item.message) != null) MessageDisplayType.System else MessageDisplayType.Text,
                canEditMessage = isOutgoing,
                canDeleteMessage = isOutgoing,
                urlAvatar = null,
                isGroup = conversationType == ConversationType.Group,
                kind = null,
                isSelected = false,
                references = references.mapNotNull { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                isUnread = !isRead,
                isChecked = false,
                archivedId = archivedId
            )

            messageDtos.add(messageDto)

            // Immediate notification to ChatViewModel
            save(listOf(messageDto)) // Immediate DB save!
            val chatId = LastChatsStorageItem.genPrimary(opponent, owner, conversationType)
            val chatViewModel = AccountManager.getChatViewModel(chatId)
            chatViewModel?.insertMessagesFromReceiver(listOf(messageDto))
        }

        // Save to database
        save(messageDtos)
        callback(messageDtos)
        items.forEach { clearQueue(it) }

        // Clear processedMessageIds periodically
        if (processedMessageIds.size > 1000) {
            processedMessageIds.clear()
            Log.d(TAG, "Cleared processedMessageIds to prevent memory growth")
        }
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
            realm.write {
                messages.forEach { messageDto ->
                    val existing = query<MessageStorageItem>("archivedId = $0 AND archivedId != ''", messageDto.archivedId).first().find()
                    if (existing != null) {
                        Log.d(TAG, "Skipping duplicate MessageStorageItem: primary=$existing.primary, messageId=${messageDto.archivedId}, body=${existing.body.take(100)}")
                        return@forEach
                    }
                    val rreferences = realmListOf<MessageReferenceStorageItem>()
                    messageDto.references.forEach { ref ->
                        val refItem = copyToRealm(MessageReferenceStorageItem().apply {
                            primary = "${ref.id}_${System.currentTimeMillis()}"
                            uri = ref.uri
                            mimeType = ref.mimeType
                            isGeo = ref.isGeo
                            latitude = ref.latitude
                            longitude = ref.longitude
                            isAudioMessage = ref.isVoiceMessage
                            fileName = ref.fileName
                            fileSize = ref.size
                        })
                        rreferences.add(refItem)
                    }
                    val bareOpponentJid = messageDto.opponentJid.removeSuffix("/${messageDto.opponentJid.substringAfterLast("/")}")
                    val message = copyToRealm(MessageStorageItem().apply {
                        primary = messageDto.primary
                        owner = messageDto.owner
                        opponent = bareOpponentJid
                        body = messageDto.messageBody
                        date = messageDto.sentTimestamp
                        sentDate = messageDto.sentTimestamp
                        editDate = messageDto.editTimestamp
                        outgoing = messageDto.isOutgoing
                        isRead = !messageDto.isUnread
                        references = rreferences
                        conversationType_ = if (messageDto.isGroup) "https://xabber.com/protocol/groups" else "urn:xabber:chat"
                        archivedId = messageDto.archivedId
                    })
                    val chatId = LastChatsStorageItem.genPrimary(messageDto.opponentJid, messageDto.owner, ConversationType.fromRaw(if (messageDto.isGroup) "https://xabber.com/protocol/groups" else "urn:xabber:chat"))
                    val chat = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                    if (chat != null) {
                        findLatest(chat)?.apply {
                            lastMessage = message
                            messageDate = message.sentDate
                            if (!messageDto.isOutgoing && muteExpired <= 0) {
                                isArchived = false
                                unread = (unread ?: 0) + if (messageDto.isUnread) 1 else 0
                            }
                        }
                        Log.d(TAG, "Updated LastChatsStorageItem for chatId=$chatId: lastMessageId=${message.primary}, unread=${chat.unread}")
                    }
                    Log.d(TAG, "Saved MessageStorageItem: primary=${message.primary}, messageId=${messageDto.archivedId}, owner=${message.owner}, opponent=${message.opponent}, body=${message.body.take(50)}, state=${messageDto.messageSendingState}, isRead=${message.isRead}, archivedId=${message.archivedId}")
                }
            }
            messages.forEach { message ->
                if (message.archivedId == "388774f9-3793-4a94-9c11-47ec82345440") {
                    Log.d(TAG, "Notifying ChatViewModel for target message: primary=${message.primary}, archivedId=${message.archivedId}, body=${message.messageBody.take(50)}")
                }
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
        Log.d(TAG, "unsafeSave called with ${messages.size} messages")
        try {
            realm.write {
                messages.forEach { message ->
                    if (message.save(this, false)) {
                        message.storeStanza(this)
                        Log.d(
                            TAG,
                            "Unsafe Saved MessageStorageItem: primary=${message.primary}, messageId=${message.messageId}, owner=${message.owner}, opponent=${message.opponent}, body=${message.body.take(50)}, state=${message.state}, isRead=${message.isRead}, date=${message.date}, readDate=${message.readDate}, burnDate=${message.burnDate}, afterburnInterval=${message.afterburnInterval}, isDeleted=${message.isDeleted}, conversationType=${message.conversationType}, referencesCount=${message.references.size}"
                        )
                        message.references.forEach { reference ->
                            reference.prepare()
                            Log.d(
                                TAG,
                                "  Reference for message ${message.primary}: primary=${reference.primary}, messageId=${reference.messageId}, kind=${reference.kind}, mimeType=${reference.mimeType}, isDownloaded=${reference.isDownloaded}, isUploaded=${reference.isUploaded}, isMissed=${reference.isMissed}, hasError=${reference.hasError}, fileName=${reference.fileName}, fileSize=${reference.fileSize}, uri=${reference.uri}, metadata=${reference.metadata}"
                            )
                        }
                    } else {
                        Log.w(TAG, "Failed to unsafe save MessageStorageItem: primary=${message.primary}, messageId=${message.messageId}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot unsafe save messages collection: ${e.message}", e)
        }
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


    fun deleteSelfChats() {
        realm.writeBlocking {
            val selfChats = query<LastChatsStorageItem>("owner = $0 AND jid = $0", owner).find()
            delete(selfChats)
            Log.d(TAG, "Deleted ${selfChats.size} self-chats for owner=$owner")
        }
    }
}