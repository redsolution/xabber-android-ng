package com.xabber.xmpp.messages.messages_manager

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.account.AccountManager
import com.xabber.stream.StreamState
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageForwardsInlineStorageItem
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.utils.toMessageReferenceDto
import com.xabber.utils.toXMPPString
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMPPMessage
import io.ktor.network.sockets.isClosed
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.O)
class MessageCommonSender(private val owner: String) {
    private val queue: String = "com.xabber.messages.sender.$owner.${UUID.randomUUID()}"
    private val messagesQueue = MutableStateFlow<Set<MessageQueueItem>>(HashSet())
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "MessageCommonSender"
    }

    data class MessageQueueItem(
        val message: XMPPMessage,
        val messageId: String,
        val recipientJid: String,
        val conversationType: ConversationType,
        val forwardedMessages: List<ForwardedMessageItem> = emptyList(),
        val references: List<MessageReferenceStorageItem> = emptyList(),
        val isSystem: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MessageQueueItem) return false
            return messageId == other.messageId
        }

        override fun hashCode(): Int = messageId.hashCode()
    }

    data class ForwardedMessageItem(
        val referenceElement: String,
        val body: String,
        val count: Int,
        val date: Date
    )

    init {
        subscribeSender()
    }

    fun subscribeSender() {
        Log.d(TAG, "Subscribing sender for owner $owner")
        scope.launch {
            messagesQueue.collect { items ->
                Log.d(TAG, "Collected ${items.size} items for sending: ${items.map { it.messageId }}")
                processQueue(items)
            }
        }
    }

    fun unsubscribeSender() {
        Log.d(TAG, "Unsubscribing sender for owner $owner")
        messagesQueue.value = HashSet()
    }

    suspend fun sendSimpleMessage(
        body: String,
        recipientJid: String,
        forwarded: List<String> = emptyList(),
        conversationType: ConversationType = ConversationType.Regular
    ): String {
        if (body.isBlank() && forwarded.isEmpty()) {
            Log.w(TAG, "sendSimpleMessage: Empty body and no forwarded messages, skipping")
            return ""
        }
        var messageId = NanoId.generate(8)  // Changed to var
        Log.d(TAG, "sendSimpleMessage: messageId=$messageId, recipientJid=$recipientJid, body=$body, forwarded=$forwarded")
        val forwardedMessages = formForwardedMessages(forwarded)
        val legacyBody = forwardedMessages.joinToString("\n") { it.body } + (if (forwardedMessages.isNotEmpty()) "\n" else "") + body
        val realm = Realm.open(defaultRealmConfig())
        val instance = MessageStorageItem().apply {
            configureOutgoingMessage(
                body = body,
                legacyBody = legacyBody,
                messageId = messageId,
                owner = this@MessageCommonSender.owner,
                opponent = recipientJid,
                references = realmListOf(),
                inlineForwards = prepareForwards(forwarded, primary, owner, recipientJid)
            )
            conversationType_ = conversationType.rawValue
            state = MessageSendingState.Sending
        }
        try {
            realm.writeBlocking {
                val existing = query<MessageStorageItem>("owner = $0 AND messageId = $1", owner, messageId).first().find()
                if (existing != null) {
                    Log.w(TAG, "Duplicate messageId=$messageId found, generating new ID")
                    messageId = UUID.randomUUID().toString()  // Update the local messageId var
                    instance.messageId = messageId
                    instance.updatePrimary()
                }
                copyToRealm(instance, UpdatePolicy.ALL)
                Log.d(TAG, "Saved MessageStorageItem: primary=${instance.primary}, messageId=$messageId, body=$body")
                val chat = query<LastChatsStorageItem>(
                    "primary = $0",
                    LastChatsStorageItem.genPrimary(recipientJid, owner, conversationType)
                ).first().find() ?: copyToRealm(LastChatsStorageItem().apply {
                    primary = LastChatsStorageItem.genPrimary(recipientJid, owner, conversationType)
                    jid = recipientJid
                    this.owner = this@MessageCommonSender.owner
                    conversationType_ = conversationType.rawValue
                    isArchived = false
                    unread = 0
                    messageDate = Date().time  // ← FIXED: Milliseconds (removed /1000)
                    lastMessage = instance
                    lastMessageId = messageId
                }, UpdatePolicy.ALL)
                chat.apply {
                    lastReadId = null
                    draftMessage = null
                    lastMessage = instance
                    messageDate = Date().time  // ← FIXED: Milliseconds (removed /1000)
                }
            }
        } finally {
            realm.close()
        }
        processSender(instance.primary, forwardedMessages)
        return messageId  // Now returns the final (possibly updated) messageId
    }

    private suspend fun processSender(
        primary: String,
        forwardedMessages: List<ForwardedMessageItem> = emptyList(),
        retry: Boolean = false
    ) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val (messageId, opponent, conversationTypeRaw) = realm.query<MessageStorageItem>("primary = $0", primary).first().find()?.let {
                Triple(it.messageId, it.opponent, it.conversationType_)
            } ?: Triple("", "", ConversationType.Regular.rawValue)

            if (messageId.isEmpty()) {
                Log.w(TAG, "processSender: No message found for primary=$primary")
                return
            }

            val conversationType = ConversationType.fromRaw(conversationTypeRaw)

            val resource = realm.query<RosterStorageItem>(
                "jid = $0 AND owner = $1",
                opponent, owner
            ).first().find()?.getPrimaryResource()

            val stanzaBody = realm.query<MessageStorageItem>("primary = $0", primary).first().find()?.legacyBody?.replace("<", "&lt;")?.replace(">", "&gt;") ?: ""  // ← FIXED: Proper XML escaping

            val referencesXml = realm.query<MessageStorageItem>("primary = $0", primary).first().find()?.references?.joinToString("") { createReferenceElement(it) } ?: ""

            val rawStanza = """
                <message type='chat' id='$messageId' from='$owner' to='$opponent${resource?.let { "/$it" } ?: ""}'>
                    <body>$stanzaBody</body>
                    <origin-id xmlns='urn:xmpp:sid:0' id='$messageId'/>
                    ${forwardedMessages.joinToString("") { it.referenceElement }}
                    $referencesXml
                </message>
            """.trimIndent()

            val stanza = XMPPMessage(
                raw = rawStanza,
                type = "chat",
                id = messageId,
                from = XMPPJID(fullJID = owner),
                to = XMPPJID(fullJID = opponent + (resource?.let { "/$it" } ?: "")),
                body = stanzaBody
            )

            realm.writeBlocking {
                val instance = query<MessageStorageItem>("primary = $0", primary).first().find()
                instance?.apply {
                    state = MessageSendingState.Sending
                    trustedSource = query<LastChatsStorageItem>(
                        "primary = $0",
                        LastChatsStorageItem.genPrimary(opponent, owner, conversationType)
                    ).first().find()?.isSynced ?: false
                    val stanzaToSave = stanza.copy()
                    storeStanza(this@writeBlocking)
                }
            }

            scope.launch {
                AccountManager.find(owner)?.action { user, stream ->
                    if (stream.state != StreamState.CONNECTED || stream.socket?.getSocket()?.isClosed == true) {
                        Log.e(TAG, "Cannot send message: primary=$primary, messageId=$messageId, stream not connected or socket closed or self-directed")
                        val localRealm = Realm.open(defaultRealmConfig())
                        try {
                            localRealm.writeBlocking {
                                val msg = query<MessageStorageItem>("primary = $0", primary).first().find()
                                msg?.apply {
                                    state = MessageSendingState.Error
                                    messageError = "Stream not connected"
                                }
                                val chat = query<LastChatsStorageItem>(
                                    "primary = $0",
                                    LastChatsStorageItem.genPrimary(opponent, owner, conversationType)
                                ).first().find()

                                chat?.apply {
                                    lastMessage = msg
                                    lastMessageId = msg?.messageId!!
                                    messageDate = System.currentTimeMillis()  // ← FIXED: Milliseconds
                                    unread = 0
                                }
                            }
                        } finally {
                            localRealm.close()
                        }
                        return@action
                    }
                    if (stream.socket?.write(stanza.raw) == true) {
                        Log.d(TAG, "Sent message: primary=$primary, messageId=$messageId, stanza=${stanza.raw}")
                        val localRealm = Realm.open(defaultRealmConfig())
                        try {
                            localRealm.writeBlocking {
                                val msg = query<MessageStorageItem>("primary = $0", primary).first().find()
                                msg?.apply {
                                    state = MessageSendingState.Deliver
                                }
                                val chat = query<LastChatsStorageItem>(
                                    "primary = $0",
                                    LastChatsStorageItem.genPrimary(opponent, owner, conversationType)
                                ).first().find()
                                chat?.let { liveChat ->
                                    findLatest(liveChat)?.apply {
                                        lastMessage = msg
                                        lastMessageId = msg?.messageId ?: ""
                                        messageDate = msg?.sentDate ?: System.currentTimeMillis()  // ← FIXED: Use ms consistently
                                        if (!msg?.outgoing!! && muteExpired <= 0) {
                                            unread = (unread ?: 0) + 1
                                        }
                                    }
                                }
                            }
                        } finally {
                            localRealm.close()
                        }
                    } else {
                        Log.e(TAG, "Failed to send message: primary=$primary, messageId=$messageId")
                        val localRealm = Realm.open(defaultRealmConfig())
                        try {
                            localRealm.writeBlocking {
                                val msg = query<MessageStorageItem>("primary = $0", primary).first().find()
                                msg?.apply {
                                    state = MessageSendingState.Error
                                    messageError = "Failed to send message"
                                    references.forEach { it.hasError = true }
                                }
                                query<LastChatsStorageItem>(
                                    "primary = $0",
                                    LastChatsStorageItem.genPrimary(msg?.opponent ?: "", msg?.owner ?: "", msg?.conversationType ?: ConversationType.Regular)
                                ).first().find()?.hasErrorInChat = true
                            }
                        } finally {
                            localRealm.close()
                        }
                    }
                } ?: Log.w(TAG, "No account found for owner=$owner")
            }
        } finally {
            realm.close()
        }
    }



    private suspend fun formForwardedMessages(forwarded: List<String>): List<ForwardedMessageItem> {
        val out = mutableListOf<ForwardedMessageItem>()
        val dateFormatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        val timeFormatter = SimpleDateFormat("[HH:mm:ss]", Locale.US)

        val realm = Realm.open(defaultRealmConfig())
        try {
            forwarded.forEach { primary ->
                val instance = realm.query<MessageStorageItem>("primary = $0", primary).first().find()
                if (instance != null) {
                    val body = "> ${dateFormatter.format(Date(instance.date))} ${timeFormatter.format(Date(instance.date))} ${instance.opponent}\n${instance.body}"
                    val refElement = """
                        <reference xmlns='https://xabber.com/protocol/references' type='mutable' begin='${body.length - instance.body.length}' end='${body.length}'>
                            <forwarded xmlns='urn:xmpp:forward:0'>
                                <delay xmlns='urn:xmpp:delay' stamp='${Date(instance.date).toXMPPString()}'/>  // ← FIXED: Use extension
                                <message xmlns='jabber:client' id='${instance.messageId}' from='${instance.opponent}' to='$owner'>
                                    <body>${instance.body.replace("<", "&lt;").replace(">", "&gt;")}</body>  // ← FIXED: Escaping
                                </message>
                            </forwarded>
                        </reference>
                    """.trimIndent()
                    out.add(
                        ForwardedMessageItem(
                            referenceElement = refElement,
                            body = body,
                            count = body.length,
                            date = Date(instance.date)
                        )
                    )
                }
            }
        } finally {
            realm.close()
        }
        return out.sortedByDescending { it.date }
    }

    private suspend fun prepareForwards(
        forwarded: List<String>,
        primary: String,
        owner: String,
        recipientJid: String
    ): RealmList<MessageForwardsInlineStorageItem> {
        val out = realmListOf<MessageForwardsInlineStorageItem>()
        val realm = Realm.open(defaultRealmConfig())
        try {
            realm.writeBlocking {
                forwarded.forEach { forwardedPrimary ->
                    val instance = query<MessageStorageItem>("primary = $0", forwardedPrimary).first().find()
                    if (instance != null) {
                        val item = MessageForwardsInlineStorageItem().apply {
                            this.owner = owner
                            this.jid = recipientJid
                            this.forwardJid = if (instance.outgoing) instance.owner else instance.opponent
                            val rosterPrimary = RosterStorageItem.genPrimary(instance.opponent, instance.owner)
                            this.forwardNickname = query<RosterStorageItem>("primary = $0", rosterPrimary).first().find()?.displayName ?: ""
                            this.rosterItem = query<RosterStorageItem>("primary = $0", rosterPrimary).first().find()
                            this.body = instance.body
                            this.references.addAll(instance.references)
                            this.subforwards.addAll(instance.inlineForwards)
                            this.messageId = instance.primary
                            this.parentId = primary
                            this.originalDate = instance.date
                            this.isOutgoing = instance.outgoing
                        }
                        out.add(copyToRealm(item, UpdatePolicy.ALL))
                    }
                }
            }
        } finally {
            realm.close()
        }
        return out
    }

    private fun createReferenceElement(ref: MessageReferenceStorageItem): String {
        return """
            <reference xmlns='https://xabber.com/protocol/references' uri='${ref.uri}' type='${ref.kind}'/>
        """.trimIndent()
    }

    private fun applyDeliveryManager(stanza: XMPPMessage, retry: Boolean, missRetryElement: Boolean): XMPPMessage {
        // Placeholder for delivery manager logic (e.g., adding chat markers)
        return stanza
    }

    private fun notifyChatViewModel(instance: MessageStorageItem) {
        val conversationType = ConversationType.fromRaw(instance.conversationType_)
        val chatId = LastChatsStorageItem.genPrimary(instance.opponent, instance.owner, conversationType)
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
        AccountManager.getChatViewModel(chatId)?.insertMessagesFromReceiver(listOf(messageDto))
        Log.d(TAG, "Notified ChatViewModel: chatId=$chatId, messageId=${instance.messageId}")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun processQueue(items: Set<MessageQueueItem>) {
        items.forEach { item ->
            Log.d(TAG, "Processing queue item: messageId=${item.messageId}, recipientJid=${item.recipientJid}")
            scope.launch {
                AccountManager.find(owner)?.action { user, stream ->
                    if (stream.state != StreamState.CONNECTED || stream.socket?.getSocket()?.isClosed == true) {
                        Log.e(TAG, "Cannot send queue item: messageId=${item.messageId}, stream not connected")
                        val realm = Realm.open(defaultRealmConfig())
                        try {
                            realm.writeBlocking {
                                val instance = query<MessageStorageItem>("primary = $0", MessageStorageItem.genPrimary(item.messageId, owner)).first().find()
                                if (instance != null) {
                                    findLatest(instance)?.apply {
                                        state = MessageSendingState.Error
                                        messageError = "Stream not connected"
                                    }
                                    query<LastChatsStorageItem>(
                                        "primary = $0",
                                        LastChatsStorageItem.genPrimary(instance.opponent, instance.owner, instance.conversationType)
                                    ).first().find()?.hasErrorInChat = true
                                }
                            }
                        } finally {
                            realm.close()
                        }
                        return@action
                    }
                    if (stream.socket?.write(item.message.raw) == true) {
                        Log.d(TAG, "Sent queue item: messageId=${item.messageId}, stanza=${item.message.raw}")
                        val realm = Realm.open(defaultRealmConfig())
                        try {
                            realm.writeBlocking {
                                val instance = query<MessageStorageItem>("primary = $0", MessageStorageItem.genPrimary(item.messageId, owner)).first().find()
                                if (instance != null) {
                                    findLatest(instance)?.state = MessageSendingState.Deliver
                                    val chat = query<LastChatsStorageItem>(
                                        "primary = $0",
                                        LastChatsStorageItem.genPrimary(instance.opponent, instance.owner, instance.conversationType)
                                    ).first().find()
                                    chat?.apply {
                                        findLatest(this)?.apply {
                                            lastMessage = instance
                                            messageDate = Date().time
                                        }
                                    }
                                    notifyChatViewModel(instance)
                                }
                            }
                        } finally {
                            realm.close()
                        }
                    } else {
                        Log.e(TAG, "Failed to send queue item: messageId=${item.messageId}")
                        val realm = Realm.open(defaultRealmConfig())
                        try {
                            realm.writeBlocking {
                                val instance = query<MessageStorageItem>("primary = $0", MessageStorageItem.genPrimary(item.messageId, owner)).first().find()
                                if (instance != null) {
                                    findLatest(instance)?.apply {
                                        state = MessageSendingState.Error
                                        messageError = "Failed to send message"
                                        references.forEach { it.hasError = true }
                                    }
                                    query<LastChatsStorageItem>(
                                        "primary = $0",
                                        LastChatsStorageItem.genPrimary(instance.opponent, instance.owner, instance.conversationType)
                                    ).first().find()?.hasErrorInChat = true
                                }
                            }
                        } finally {
                            realm.close()
                        }
                    }
                } ?: Log.w(TAG, "No account found for owner=$owner")
            }
            messagesQueue.value = messagesQueue.value.toMutableSet().apply { remove(item) }
        }
    }

    private fun formatXMPPDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(date)
    }
}