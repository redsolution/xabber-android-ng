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

    suspend fun receiveClientSync(message: XMPPMessage, isRead: Boolean, state: MessageSendingState, date: Date) {
        val messageId = getOriginId(message) ?: message.id
        receiveClientSyncRaw(message, null, isRead, state, date)?.let { enqueue(it) }
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
        CoroutineScope(Dispatchers.IO).launch {
            processQueue(messagesQueue.value) { messages ->
                messages?.let { save(it) }
            }
        }
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
        if (!isValidMessage(message)) {
            return
        }
        if (message.element("result", namespace = "urn:xmpp:mam:2") != null || message.raw.contains("urn:xmpp:mam:2")) {
            receiveArchived(message)
            return
        }
        val messageId = getOriginId(message) ?: message.id
        val primary = messageId?.let { MessageStorageItem.genPrimary(it, owner) }
        if (primary != null) {
            val existing = realm.query<MessageStorageItem>("primary = $0", primary).first().find()
            if (existing != null) {
                return
            }
        } else {
            return
        }
        val from = message.from?.bare()
        val to = message.to?.bare()
        if (from == null || to == null) {
            return
        }
        val opponent = if (to != owner) to else from
        if (opponent == owner) {
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
    }

    fun updateReadDate(messageId: String, stanzaId: String, jid: String, date: Date) {
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
        val current = messagesQueue.value.toMutableSet()
        current.remove(item)
        messagesQueue.value = current
    }

    private fun clearQueue() {
        messagesQueue.value = HashSet()
    }

    fun subscribeReceiver() {
        scope.launch {
            messagesQueue.collect { results ->
                processQueue(results) { messages ->
                    messages?.let { save(it) }
                }
                AccountManager.find(owner)?.chatMarkers?.deleteEphemeralMessages()
            }
        }
    }

    fun unsubscribeReceiver() {
        clearQueue()
    }

    suspend fun processQueue(items: Set<MessageQueueItem>, callback: suspend (List<MessageDto>?) -> Unit) {
        if (items.isEmpty()) {
            callback(null)
            return
        }

        val messageDtos = mutableListOf<MessageDto>()
        val sortedItems = items.sortedBy { it.date.time }

        sortedItems.forEach { item ->
            if (isVoIPMessage(item.message)) {
                return@forEach
            }

            val messageId = item.messageId ?: return@forEach

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
                        }
                    }
                } else {
                    return@forEach
                }
            } else {
                processedMessageIds.add(messageId)
            }

            val from = item.message.from?.bare() ?: item.archivedFrom ?: item.originalFrom
            val to = item.message.to?.bare() ?: return@forEach
            val opponent = if (to != owner) to else from
            if (opponent == owner) {
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
        }
    }

    private suspend fun enqueue(item: MessageQueueItem) {
        val current = messagesQueue.value.toMutableSet()
        current.add(item)
        messagesQueue.value = current
    }

    private suspend fun save(messages: List<MessageDto>) {
        try {
            realm.write {
                messages.forEach { messageDto ->
                    val existing = query<MessageStorageItem>("archivedId = $0 AND archivedId != ''", messageDto.archivedId).first().find()
                    if (existing != null) {
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
                    }
                }
            }
            messages.forEach { message ->
                val conversationType = ConversationType.fromRaw(if (message.isGroup) "https://xabber.com/protocol/groups" else "urn:xabber:chat")
                val chatId = LastChatsStorageItem.genPrimary(message.opponentJid, message.owner, conversationType)
                val chatViewModel = AccountManager.getChatViewModel(chatId)
                if (chatViewModel != null) {
                    chatViewModel.insertMessagesFromReceiver(listOf(message))
                } else {
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

                        message.references.forEach { reference ->
                            reference.prepare()

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
        val results = messagesQueue.value
        messagesQueue.value = HashSet()
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
        }
    }


    fun deleteSelfChats() {
        realm.writeBlocking {
            val selfChats = query<LastChatsStorageItem>("owner = $0 AND jid = $0", owner).find()
            delete(selfChats)
        }
    }
}