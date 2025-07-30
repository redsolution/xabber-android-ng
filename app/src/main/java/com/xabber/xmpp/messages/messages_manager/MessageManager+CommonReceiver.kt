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
import com.xabber.xmpp.messages.XMLElement
import com.xabber.xmpp.messages.XMPPMessage
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
        val primary = MessageStorageItem.genPrimary(messageId, owner)
        val existing = realm.query<MessageStorageItem>("primary = $0", primary).first().find()
        if (existing != null) {
            Log.d(TAG, "Skipping duplicate archived message: messageId=$messageId, primary=$primary, body=${existing.body.take(100)}")
            return
        }
        val innerTimestamp = parseTimestamp(messageBare, TAG)?.let { Date(it) } ?: parseTimestamp(message, TAG)?.let { Date(it) } ?: run {
            Log.e(TAG, "No valid timestamp for messageId=$messageId, using current time as fallback")
            Date()
        }
        Log.d(TAG, "receiveArchived: messageId=$messageId, timestamp=$innerTimestamp, body=${messageBare.body?.take(100)}")
        enqueue(
            MessageQueueItem(
                message = messageBare,
                messageId = messageId,
                archivedFrom = messageBare.from?.bare(),
                isRead = messageBare.from?.bare() == owner,
                date = innerTimestamp,
                state = MessageSendingState.Deliver,
                queryId = getMAMQueryId(message),
                originalFrom = messageBare.from?.bare() ?: "",
                originalOutgoing = messageBare.from?.bare() == owner
            )
        )
    }

    suspend fun receiveCarbon(message: XMPPMessage) {
        val messageBare = getCarbonCopyMessageContainer(message) ?: return.also {
            Log.w(TAG, "receiveCarbon failed: no carbon copy message container for messageId=${getOriginId(message) ?: message.id}")
        }
        val messageId = getOriginId(messageBare) ?: messageBare.id
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
        enqueue(queueItem)
    }

    suspend fun receiveCarbonForwarded(message: XMPPMessage) {
        val messageId = getOriginId(message) ?: message.id ?: run {
            Log.w(TAG, "Skipping carbon forwarded message with no valid ID: raw=${message.raw.substring(0, minOf(message.raw.length, 200))}...")
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
        if (opponent == owner) {
            Log.w(TAG, "Skipping self-directed message: messageId=$messageId, from=$from, to=$to")
            return
        }
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
        // Check if the message is a MAM archived message
        if (message.element("result", namespace = "urn:xmpp:mam:2") != null || message.raw.contains("urn:xmpp:mam:2")) {
            Log.d(TAG, "Detected MAM message in receiveRuntime, redirecting to receiveArchived: messageId=${getOriginId(message) ?: message.id}")
            receiveArchived(message)
            return
        }
        val messageId = getOriginId(message) ?: message.id
        Log.d(TAG, "receiveRuntime called for messageId=$messageId, body=${message.body?.take(100)}")
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
        enqueue(
            MessageQueueItem(
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
        )
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
        items.forEach { item ->
            Log.d(
                TAG,
                "Queue item: messageId=${item.messageId}, from=${item.message.from?.bare()}, to=${item.message.to?.bare()}, body=${item.message.body?.take(100)}, state=${item.state}, isRead=${item.isRead}, date=${Date(item.date.time)}, queryId=${item.queryId}"
            )
        }
        val messageQueryIds = mutableSetOf<String>()
        val out = mutableListOf<MessageDto>()
        val sortedItems = items.sortedBy { it.date.time }

        sortedItems.forEach { item ->
            if (isVoIPMessage(item.message)) {
                Log.d(TAG, "Skipping VoIP message: messageId=${item.messageId}")
                return@forEach
            }

            val primary = item.messageId?.let { MessageStorageItem.genPrimary(it, owner) } ?: return@forEach.also {
                Log.w(TAG, "Skipping message without ID: body=${item.message.body?.take(100)}")
            }
            val existing = realm.query<MessageStorageItem>("primary = $0", primary).first().find()
            if (existing != null) {
                Log.d(TAG, "Skipping duplicate message already in DB: messageId=${item.messageId}, primary=$primary")
                return@forEach
            }

            val instance = MessageStorageItem()
            val from = item.message.from?.bare() ?: item.archivedFrom ?: item.originalFrom
            val to = item.message.to?.bare() ?: return@forEach.also {
                Log.w(TAG, "Skipping message ${item.messageId}: no valid 'to' JID")
            }
            val opponent = if (to != owner) to else from
            if (opponent == owner) {
                Log.w(TAG, "Skipping self-directed message in processQueue: messageId=${item.messageId}, from=$from, to=$to")
                return@forEach
            }
            var isEncryptedMessage = false
            val isOutgoing = item.originalOutgoing || from == owner
            instance.configureIncomingMessage(
                message = item.message,
                owner = owner,
                opponent = opponent,
                outgoing = isOutgoing,
                isRead = if (isOutgoing) true else item.isRead,
                date = item.date,
                isEncrypted = isEncryptedMessage
            )
            Log.d(TAG, "Configured message ${item.messageId}: outgoing=$isOutgoing, isRead=${instance.isRead}, state=${instance.state}")

            val conversationType = conversationTypeByMessage(item.message)
            instance.conversationType_ = conversationType.rawValue
            Log.d(TAG, "Set conversationType=${conversationType.rawValue} for messageId=${item.messageId}")

            // Initialize ChatViewModel proactively
            AccountManager.createChatViewModel(owner, opponent, conversationType)

            var errorMetadata: Map<String, Any> = emptyMap()
            val afterburnInterval = item.message.element("ephemeral", namespace = "urn:xmpp:ephemeral:0")
                ?.getAttribute("timer")?.toDoubleOrNull() ?: 0.0

            var hasSignElement = false
            var envelopeContainer: String? = null
            if (item.message.hasElement("time-signature", namespace = "some_signature_namespace")) {
                hasSignElement = true
                envelopeContainer = item.message.element("time-signature", namespace = "some_signature_namespace")?.raw
                Log.d(TAG, "Message ${item.messageId} has time-signature: envelopeContainer=${envelopeContainer?.take(100)}")
            }

            item.message.element("x", namespace = "https://xabber.com/protocol/groups")?.let { groupElement ->
                groupElement.element("reference")?.element("user", namespace = "https://xabber.com/protocol/groups")?.let { userElement ->
                    val userId = userElement.getAttribute("id")
                    if (userId != null) {
                        if (item.groupchatUserCard != null && item.groupchatUserCard.contains("id=\"$userId\"")) {
                            item.originalOutgoing = true
                            Log.d(TAG, "Message ${item.messageId} marked as outgoing (groupchatUserCard match)")
                        } else {
                            realm.query<GroupChatStorageItem>("primary = $0", GroupChatStorageItem.genPrimary(opponent, owner))
                                .first().find()?.let { groupChat ->
                                    item.originalOutgoing = groupChat.contacts.contains(userId)
                                    Log.d(TAG, "Message ${item.messageId} outgoing=${item.originalOutgoing} (groupChat.contacts check)")
                                }
                        }
                    }
                } ?: run {
                    getMessageAuthorGroupchat(groupElement.elements("reference"), opponent)?.let { groupchatAuthor ->
                        item.originalOutgoing = groupchatAuthor == owner
                        Log.d(TAG, "Message ${item.messageId} outgoing=${item.originalOutgoing} (groupchatAuthor check)")
                    }
                }
            } ?: run {
                item.originalOutgoing = from == owner
                Log.d(TAG, "Message ${item.messageId} outgoing=${item.originalOutgoing} (default from check)")
            }

            val readDate = if (item.isRead) {
                item.readDate ?: prereadedMessages.firstOrNull { it.messageId == item.messageId }?.date
            } else null
            if (readDate != null && item.date.time < readDate.time) {
                item.isRead = true
                Log.d(TAG, "Message ${item.messageId} marked as read due to readDate=$readDate")
            } else {
                item.isRead = item.state == MessageSendingState.Read
                Log.d(TAG, "Message ${item.messageId} isRead=${item.isRead} based on state=${item.state}")
            }

            if (parseSystemMessageMetadata(item.message) != null) {
                instance.configureSystemMessage(item.message, owner, opponent, item.date)
                instance.state = MessageSendingState.None
                instance.isRead = item.forceUnreadState ?: item.isRead
                Log.d(TAG, "Configured system message ${item.messageId}: state=${instance.state}, isRead=${instance.isRead}")
            } else {
                instance.configureIncomingMessage(
                    message = item.message,
                    owner = owner,
                    opponent = opponent,
                    outgoing = item.originalOutgoing,
                    isRead = item.forceUnreadState ?: item.isRead,
                    date = item.date,
                    isEncrypted = isEncryptedMessage
                )
                instance.forceUnreadState = item.forceUnreadState
                instance.state = item.state
                Log.d(TAG, "Configured incoming message ${item.messageId}: outgoing=${item.originalOutgoing}, isRead=${instance.isRead}, state=${instance.state}")
            }
            instance.envelopeContainer = envelopeContainer
            instance.updatePrimary()
            instance.afterburnInterval = afterburnInterval

            if (hasSignElement) {
                instance.errorMetadata = errorMetadata
                Log.d(TAG, "Message ${item.messageId} has errorMetadata=$errorMetadata, raw errorMetadata_=${instance.errorMetadata_}")
            }

            instance.trustedSource = if (item.clientSyncMessage) {
                false
            } else {
                item.queryId?.let { queryId ->
                    messageQueryIds.contains(queryId).also { if (!it) messageQueryIds.add(queryId) }
                } ?: false
            }
            Log.d(TAG, "Message ${item.messageId} trustedSource=${instance.trustedSource}")

            instance.previousId = getPreviousId(item.message)

            if (isEncryptedMessage && errorMetadata.isNotEmpty()) {
                instance.messageError = "cert_error"
                Log.d(TAG, "Message ${item.messageId} has cert_error")
            }

            if (afterburnInterval > 0) {
                if (isEncryptedMessage && errorMetadata.isNotEmpty()) {
                    instance.isDeleted = true
                    Log.d(TAG, "Message ${item.messageId} marked as deleted due to encryption error")
                }
            }

            if (readDate != null && afterburnInterval > 0) {
                instance.isRead = true
                if (!item.originalOutgoing) {
                    instance.state = MessageSendingState.Read
                }
                instance.readDate = (readDate.time / 1000).toDouble()
                instance.burnDate = (readDate.time / 1000).toDouble() + afterburnInterval
                Log.d(TAG, "Message ${item.messageId} updated: readDate=${instance.readDate}, burnDate=${instance.burnDate}")

                val existingConversation = prereadedConversation.firstOrNull { it.jid == opponent && it.conversationType == conversationType }
                if (existingConversation != null) {
                    if (existingConversation.date.time < readDate.time) {
                        prereadedConversation.remove(existingConversation)
                        prereadedConversation.add(PrereadedConversationItem(conversationType, readDate, opponent))
                        Log.d(TAG, "Updated PrereadedConversationItem for jid=$opponent, conversationType=${conversationType.rawValue}")
                    }
                } else {
                    prereadedConversation.add(PrereadedConversationItem(conversationType, readDate, opponent))
                    Log.d(TAG, "Added PrereadedConversationItem for jid=$opponent, conversationType=${conversationType.rawValue}")
                }

                if (instance.burnDate <= System.currentTimeMillis() / 1000) {
                    instance.isDeleted = true
                    instance.body = ""
                    instance.legacyBody = ""
                    Log.d(TAG, "Message ${item.messageId} marked as deleted due to burnDate expiration")
                }
            }

            // Convert to MessageDto
            val messageDto = MessageDto(
                primary = instance.primary,
                isOutgoing = instance.outgoing,
                owner = instance.owner,
                opponentJid = instance.opponent,
                messageBody = instance.body,
                messageSendingState = when {
                    instance.isRead -> MessageSendingState.Read
                    instance.outgoing -> MessageSendingState.Deliver
                    else -> MessageSendingState.Sent
                },
                sentTimestamp = instance.sentDate,
                editTimestamp = instance.editDate,
                displayType = MessageDisplayType.Text,
                canEditMessage = instance.outgoing,
                canDeleteMessage = instance.outgoing,
                urlAvatar = null,
                isGroup = instance.conversationType_ == "https://xabber.com/protocol/groups",
                kind = null,
                isSelected = false,
                references = instance.references.map { it.toMessageReferenceDto() } as ArrayList<MessageReferenceDto>,
                isUnread = !instance.isRead,
                isChecked = false
            )
            out.add(messageDto)
        }

        Log.d(TAG, "Prepared ${out.size} MessageDTOs for saving")
        callback(out.sortedBy { it.sentTimestamp })
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
            realm.write {
                messages.forEach { messageDto ->
                    var primary = messageDto.primary
                    val existing = query<MessageStorageItem>("primary = $0", primary).first().find()
                    if (existing != null) {
                        Log.d(TAG, "Skipping duplicate MessageStorageItem: primary=$primary, messageId=${messageDto.primary.split("_")[0]}")
                        return@forEach
                    }
                    val rreferences = realmListOf<MessageReferenceStorageItem>()
                    for (i in 0 until messageDto.references.size) {
                        val ref = copyToRealm(MessageReferenceStorageItem().apply {
                            primary = messageDto.references[i].id + "${System.currentTimeMillis()}"
                            uri = messageDto.references[i].uri
                            mimeType = messageDto.references[i].mimeType
                            isGeo = messageDto.references[i].isGeo
                            latitude = messageDto.references[i].latitude
                            longitude = messageDto.references[i].longitude
                            isAudioMessage = messageDto.references[i].isVoiceMessage
                            fileName = messageDto.references[i].fileName
                            fileSize = messageDto.references[i].size
                        })
                        rreferences.add(ref)
                    }
                    val message = copyToRealm(MessageStorageItem().apply {
                        primary = messageDto.primary
                        owner = messageDto.owner
                        opponent = messageDto.opponentJid
                        body = messageDto.messageBody
                        date = messageDto.sentTimestamp
                        sentDate = messageDto.sentTimestamp
                        editDate = messageDto.editTimestamp
                        outgoing = messageDto.isOutgoing
                        isRead = !messageDto.isUnread
                        references = rreferences
                        conversationType_ = if (messageDto.isGroup) "https://xabber.com/protocol/groups" else "urn:xabber:chat"
                    })
                    Log.d(TAG, "Saved MessageStorageItem: primary=${message.primary}, messageId=${message.messageId}, owner=${message.owner}, opponent=${message.opponent}, body=${message.body}, state=${message.state}, isRead=${message.isRead}")
                }
            }
            messages.forEach { message ->
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
                            "Unsafe Saved MessageStorageItem: " +
                                    "primary=${message.primary}, " +
                                    "messageId=${message.messageId}, " +
                                    "owner=${message.owner}, " +
                                    "opponent=${message.opponent}, " +
                                    "body=${message.body}, " +
                                    "state=${message.state}, " +
                                    "isRead=${message.isRead}, " +
                                    "date=${message.date}, " +
                                    "readDate=${message.readDate}, " +
                                    "burnDate=${message.burnDate}, " +
                                    "afterburnInterval=${message.afterburnInterval}, " +
                                    "isDeleted=${message.isDeleted}, " +
                                    "conversationType=${message.conversationType}, " +
                                    "referencesCount=${message.references.size}, " +
                                    "errorMetadata=${message.errorMetadata}, " +
                                    "messageError=${message.messageError}, " +
                                    "trustedSource=${message.trustedSource}, " +
                                    "previousId=${message.previousId}, " +
                                    "envelopeContainer=${message.envelopeContainer?.take(100) ?: "null"}"
                        )
                        message.references.forEach { reference ->
                            reference.prepare()
                            Log.d(
                                TAG,
                                "  Reference for message ${message.primary}: " +
                                        "primary=${reference.primary}, " +
                                        "messageId=${reference.messageId}, " +
                                        "kind=${reference.kind}, " +
                                        "mimeType=${reference.mimeType}, " +
                                        "isDownloaded=${reference.isDownloaded}, " +
                                        "isUploaded=${reference.isUploaded}, " +
                                        "isMissed=${reference.isMissed}, " +
                                        "hasError=${reference.hasError}, " +
                                        "fileName=${reference.fileName}, " +
                                        "fileSize=${reference.fileSize}, " +
                                        "uri=${reference.uri}, " +
                                        "metadata=${reference.metadata}"
                            )
                        }
                    } else {
                        Log.w(TAG, "Failed to unsafe save MessageStorageItem: primary=${message.primary}, messageId=${message.messageId}")
                    }
                }
            }
            realm.write {
                val savedMessages = query<MessageStorageItem>("primary IN $0", messages.map { it.primary }.toList()).find()
                if (savedMessages.isNotEmpty()) {
                    Log.d(TAG, "Verified ${savedMessages.size} messages unsafe saved for owner $owner")
                    savedMessages.forEach { savedMessage ->
                        Log.d(
                            TAG,
                            "Verified Unsafe Saved MessageStorageItem: " +
                                    "primary=${savedMessage.primary}, " +
                                    "messageId=${savedMessage.messageId}, " +
                                    "owner=${savedMessage.owner}, " +
                                    "opponent=${savedMessage.opponent}, " +
                                    "body=${savedMessage.body}, " +
                                    "state=${savedMessage.state}"
                        )
                    }
                } else {
                    Log.w(TAG, "No messages found after unsafe saving for owner $owner")
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