package com.xabber.xmpp.messages.messages_manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xabber.account.AccountManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.presentation.XabberApplication.Companion.applicationContext as appContext
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.presentation.application.activity.ApplicationActivity
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

    // In-memory cache: groupJid -> myMemberId (avoids repeated Realm queries per message)
    private val myMemberIdCache = mutableMapOf<String, String>()

    private val messagesQueue = MutableStateFlow(mutableSetOf<MessageQueueItem>())

    companion object {
        private const val TAG = "MessageCommonReceiver"
        private const val NOTIFICATION_CHANNEL_ID = "messages_channel"
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
        Log.w(TAG, "COMMON RECEIVER START")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Messages"
            val descriptionText = "Notifications for new incoming messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = appContext().getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }


    private fun updateChatNotification(lastChat: LastChatsStorageItem) {
        createNotificationChannel()

        val chatPrimary = lastChat.primary
        val notificationId = chatPrimary.hashCode()

        Log.d(TAG, "updateChatNotification called for chatPrimary=$chatPrimary, unread=${lastChat.unread}")

        // Use isChatOpen instead of getChatViewModel to avoid creating ViewModel
        val isChatOpen = AccountManager.isChatOpen(chatPrimary)
        if (isChatOpen || lastChat.unread <= 0) {
            Log.d(TAG, "Cancelling notification: chatOpen=$isChatOpen, unread=${lastChat.unread}")
            NotificationManagerCompat.from(appContext()).cancel(notificationId)
            return
        }

        val isMuted = lastChat.muteExpired > System.currentTimeMillis()
        if (isMuted) {
            Log.d(TAG, "Chat is muted until ${lastChat.muteExpired}, skipping notification")
            return
        }

        
        val displayName = if (lastChat.rosterItem?.customNickname != "") {
            lastChat.rosterItem?.customNickname
        } else {
            lastChat.jid
        }
        val title = displayName
        val text = lastChat.lastMessage?.body ?: "New message"

        val intent = Intent(appContext(), ApplicationActivity::class.java).apply {
            action = "android.intent.action.VIEW"
            putExtra("open_chat_primary", chatPrimary)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext(),
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext(), NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(com.xabber.R.drawable.ic_lightbulb) // Ensure this icon exists
            .setContentTitle(title)
            .setContentText(text)
            .setNumber(lastChat.unread)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setGroup("xabber_messages")

        builder.setDefaults(NotificationCompat.DEFAULT_ALL)

        Log.d(TAG, "Showing notification: title=$title, text=$text")
        NotificationManagerCompat.from(appContext()).notify(notificationId, builder.build())
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
//        Log.w("CHECK", "check it RECEIVER SYNC $queueItem, ${message.body}, id:${message.id}, from=${message.from}, to=${message.to}")

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
        val messageId = getOriginId(messageBare) ?: messageBare.id ?: return
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
//        Log.w("CHECK", "check it RECEIVER MAM $queueItem, ${message.body}, id:${message.id}, from=${message.from}, to=${message.to}")

        enqueue(queueItem)
        storeMessagesNow()
    }

    suspend fun receiveCarbon(message: XMPPMessage) {
        val isSentCarbon = message.element("sent", "urn:xmpp:carbons:2") != null
        val forwarded = message.element("forwarded", "urn:xmpp:forward:0") ?: return
        val innerMessage = forwarded.element("message", "jabber:client") ?: return
        val bareMessage = XMPPMessage(innerMessage.raw)

        // Skip carbons for group chat conversations — the group headline message
        // will arrive separately with proper <x xmlns='...groups'> metadata.
        // Carbons lack group metadata, creating a broken Regular-type message.
        val carbonOpponent = if (isSentCarbon) bareMessage.to?.bare() else bareMessage.from?.bare()
        if (carbonOpponent != null) {
            val isGroupChat = realm.query<LastChatsStorageItem>(
                "owner = $0 AND jid = $1 AND (conversationType_ = $2 OR conversationType_ = $3 OR conversationType_ = $4)",
                owner, carbonOpponent,
                ConversationType.Group.rawValue,
                ConversationType.Incognito.rawValue,
                ConversationType.Private.rawValue
            ).first().find() != null
                || realm.query<GroupChatStorageItem>(
                "owner = $0 AND jid = $1",
                owner, carbonOpponent
            ).first().find() != null
            if (isGroupChat) {
                Log.d(TAG, "Skipping carbon for group chat: opponent=$carbonOpponent, messageId=${bareMessage.id}")
                return
            }
        }

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
//        Log.w("CHECK", "check it RECEIVER Carbon $queueItem, ${message.body}, id:${message.id}, from=${message.from}, to=${message.to}")

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

        // Skip carbons for group chat conversations — group headlines handle these properly
        val isGroupChat = realm.query<LastChatsStorageItem>(
            "owner = $0 AND jid = $1 AND (conversationType_ = $2 OR conversationType_ = $3 OR conversationType_ = $4)",
            owner, opponent,
            ConversationType.Group.rawValue,
            ConversationType.Incognito.rawValue,
            ConversationType.Private.rawValue
        ).first().find() != null
            || realm.query<GroupChatStorageItem>(
            "owner = $0 AND jid = $1",
            owner, opponent
        ).first().find() != null
        if (isGroupChat) {
            Log.d(TAG, "Skipping carbon forwarded for group chat: opponent=$opponent, messageId=$messageId")
            return
        }

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
        val xEl = message.element("x", "https://xabber.com/protocol/groups")
        val fwd = xEl?.element("forwarded", "urn:xmpp:forward:0")
        val innerMsg = fwd?.element("message")
        val innerBody = innerMsg?.element("body")?.textContent
        Log.w(TAG, "receiveRuntime: message.body=${message.body}, xEl=${xEl != null}, fwd=${fwd != null}, innerMsg=${innerMsg != null}, innerBody=$innerBody, children=${message.children.map { "${it.name}(${it.namespace})" }}")
        if (xEl != null) {
            Log.w(TAG, "receiveRuntime: xEl.children=${xEl.children.map { "${it.name}(${it.namespace})" }}")
        }
        val body = message.body ?: innerBody
        if (body.isNullOrBlank()) {
            Log.w(TAG, "receiveRuntime: body is null or blank, returning")
            return
        }
        // For group headline messages, origin-id and id are inside the inner forwarded message
        val messageId = getOriginId(message) ?: message.id
            ?: innerMsg?.element("origin-id", "urn:xmpp:sid:0")?.getAttribute("id")
            ?: innerMsg?.getAttribute("id")
            ?: return
        val from = message.from?.bare() ?: return
        val to = message.to?.bare() ?: return
        val isGroupMessage = xEl != null
        // For group headlines, inner <x> with <user> is inside <forwarded>/<message>
        val innerXEl = innerMsg?.element("x", "https://xabber.com/protocol/groups")
        val isOutgoing = if (isGroupMessage) {
            // User element can be in outer <x>, inner <x>, or nested inside <reference>
            val userElement = xEl!!.element("user", "https://xabber.com/protocol/groups")
                ?: xEl.element("user")
                ?: innerXEl?.element("user", "https://xabber.com/protocol/groups")
                ?: innerXEl?.element("user")
                ?: xEl.elements("reference")
                    ?.firstNotNullOfOrNull { ref -> ref.element("user", "https://xabber.com/protocol/groups") ?: ref.element("user") }
            val userJid = userElement?.getAttribute("jid")
                ?: userElement?.element("jid")?.textContent
            val userId = userElement?.getAttribute("id")
            Log.w(TAG, "receiveRuntime: group msg userJid=$userJid, userId=$userId, owner=$owner")
            // Check by JID match (works for public groups)
            val jidMatch = userJid != null && userJid == owner
            // Check by member ID match (works for all groups including incognito)
            val idMatch = if (!jidMatch && userId != null) {
                val myMemberId = getCachedMyMemberId(from)
                !myMemberId.isNullOrEmpty() && userId == myMemberId
            } else false
            jidMatch || idMatch
        } else {
            from == owner
        }
        // For group messages, opponent is the group JID.
        // Outer headline: from=group, to=user. Use 'from' as opponent.
        val opponent = if (isGroupMessage) from else if (isOutgoing) to else from
        Log.w(TAG, "receiveRuntime: from=$from, to=$to, opponent=$opponent, isOutgoing=$isOutgoing, isGroupMessage=$isGroupMessage, messageId=$messageId")
        if (opponent == owner) {
            Log.w(TAG, "receiveRuntime: opponent == owner, skipping")
            return
        }

        val queueItem = MessageQueueItem(
            message = message,
            messageId = messageId,
            archivedFrom = from,
            isRead = isOutgoing,
            date = Date(message.date ?: System.currentTimeMillis()),
            state = MessageSendingState.Deliver,
            originalFrom = from,
            originalOutgoing = isOutgoing
        )
        Log.w(TAG, "receiveRuntime: enqueuing message body='$body', date=${queueItem.date}")

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
        val newUnreadChatPrimaries = mutableSetOf<String>()
        // Collect member IDs learned from JID matches — write to Realm once after the loop
        val learnedMemberIds = mutableMapOf<String, String>() // groupJid -> memberId
        val messagesToSave = mutableListOf<MessageStorageItem>()

        for (item in sorted) {
            if (isVoIPMessage(item.message)) continue

            val from = item.message.from?.bare() ?: item.archivedFrom ?: item.originalFrom
            val to   = item.message.to?.bare()   ?: continue

            if (from.isBlank() || to.isBlank()) continue

            val isGroupMessage = item.message.hasElement("x", "https://xabber.com/protocol/groups")
            val isOutgoing = if (isGroupMessage) {
                val xElement = item.message.element("x", "https://xabber.com/protocol/groups")
                // For group headlines, inner <x> with <user> is inside <forwarded>/<message>
                val innerFwd = xElement?.element("forwarded", "urn:xmpp:forward:0")
                val innerMsgEl = innerFwd?.element("message")
                val innerXElement = innerMsgEl?.element("x", "https://xabber.com/protocol/groups")
                // User element can be in outer <x>, inner <x>, or nested inside <reference>
                val userElement = xElement?.element("user", "https://xabber.com/protocol/groups")
                    ?: xElement?.element("user")
                    ?: innerXElement?.element("user", "https://xabber.com/protocol/groups")
                    ?: innerXElement?.element("user")
                    ?: xElement?.elements("reference")
                        ?.firstNotNullOfOrNull { ref -> ref.element("user", "https://xabber.com/protocol/groups") ?: ref.element("user") }
                val userJid = userElement?.getAttribute("jid")
                    ?: userElement?.element("jid")?.textContent
                val userId = userElement?.getAttribute("id")
                // Check by JID match (works for public groups)
                val jidMatch = userJid != null && userJid == owner
                // Check by member ID match (works for all groups including incognito)
                val idMatch = if (!jidMatch && userId != null) {
                    val myMemberId = getCachedMyMemberId(from)
                    !myMemberId.isNullOrEmpty() && userId == myMemberId
                } else false
                // Remember member ID learned from JID match (will write to Realm after the loop)
                if (jidMatch && userId != null && !learnedMemberIds.containsKey(from)) {
                    learnedMemberIds[from] = userId
                    myMemberIdCache[from] = userId
                }
                jidMatch || idMatch || item.originalOutgoing
            } else {
                from == owner
            }
            val opponent = if (isGroupMessage) from else if (isOutgoing) to else from

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
            val groupchatRef = createGroupchatReference(item.message, opponent, owner)
            if (groupchatRef != null) {
                // Add to a temporary list that will be assigned to messageItem later
                // We'll add it directly to messageItem.references after creation
            }


            // Конвертируем дату сообщения в микросекунды для сравнения
            val messageTimestampUs = item.date.time * 1000L  // миллисекунды → микросекунды

//            Log.d(TAG, "Processing message for $opponent: " +
//                    "displayedId=$displayedId µs, deliveredId=$deliveredId µs, " +
//                    "messageTimestamp=${item.date.time} ms ($messageTimestampUs µs), outgoing=$isOutgoing")

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
            val chatPrimary = LastChatsStorageItem.genPrimary(opponent, owner, conversationType)

            if (!isOutgoing && !finalIsRead) {
                newUnreadChatPrimaries.add(chatPrimary)
                Log.d(TAG, "Added to newUnreadChatPrimaries: $chatPrimary")
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
                    item.message.hasElement("x", "https://xabber.com/protocol/groups#system-message") ||
                    item.message.element("x", "https://xabber.com/protocol/groups")?.element("system-message") != null
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
            groupchatRef?.let { messageItem.references.add(it) }
            Log.w(TAG, "processQueue: adding message to save: primary=${messageItem.primary}, body='${messageItem.body}', opponent=${messageItem.opponent}, convType=${messageItem.conversationType}, outgoing=${messageItem.outgoing}, isRead=${messageItem.isRead}")
            messagesToSave.add(messageItem)
        }

        // Batch-save all messages in a single realm.write transaction
        if (messagesToSave.isNotEmpty()) {
            Log.w(TAG, "processQueue: saving ${messagesToSave.size} messages to Realm")
            try {
                realm.write {
                    for (msg in messagesToSave) {
                        msg.saveInTransaction(this)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error batch-saving ${messagesToSave.size} messages: ${e.message}")
            }
        }

        if (newUnreadChatPrimaries.isNotEmpty()) {
            Log.d(TAG, "Processing ${newUnreadChatPrimaries.size} chats with new unread messages")
            scope.launch(Dispatchers.IO) {
                val tempRealm = Realm.open(defaultRealmConfig())
                try {
                    for (primary in newUnreadChatPrimaries) {
                        val lastChat = tempRealm.query<LastChatsStorageItem>("primary == $0", primary).first().find()
                        lastChat?.let { updateChatNotification(it) }
                    }
                } finally {
                    tempRealm.close()
                }
            }
        }

        // Batch-write learned member IDs to Realm (once per processQueue call, not per message)
        if (learnedMemberIds.isNotEmpty()) {
            try {
                realm.write {
                    for ((groupJid, memberId) in learnedMemberIds) {
                        val groupPrimary = GroupChatStorageItem.genPrimary(groupJid, owner)
                        val group = query<GroupChatStorageItem>("primary = $0", groupPrimary).first().find()
                        if (group != null && group.myMemberId.isEmpty()) {
                            findLatest(group)?.myMemberId = memberId
                        }
                    }
                }
            } catch (_: Exception) { }
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

    private fun createGroupchatReference(message: XMPPMessage, opponent: String, owner: String): MessageReferenceStorageItem? {
        val groupElement = message.element("x", namespace = "https://xabber.com/protocol/groups") ?: return null
        // For group headlines, inner <x> with <user> is inside <forwarded>/<message>
        val innerGroupElement = groupElement.element("forwarded", "urn:xmpp:forward:0")
            ?.element("message")
            ?.element("x", "https://xabber.com/protocol/groups")
        // <user> can be in outer <x>, inner <x>, or nested inside <reference> within <x>
        val user = groupElement.element("user", "https://xabber.com/protocol/groups")
            ?: groupElement.element("user")
            ?: innerGroupElement?.element("user", "https://xabber.com/protocol/groups")
            ?: innerGroupElement?.element("user")
            ?: groupElement.elements("reference")
                .firstNotNullOfOrNull { ref -> ref.element("user", "https://xabber.com/protocol/groups") ?: ref.element("user") }
            ?: return null
        val metadata = mutableMapOf<String, Any>()
        // "id" is an attribute on <user>
        user.getAttribute("id")?.let { metadata["id"] = it }
        // nickname, badge, jid, role are child ELEMENTS of <user>, not attributes
        user.element("nickname")?.textContent?.let { metadata["nickname"] = it }
        user.element("badge")?.textContent?.let { metadata["badge"] = it }
        user.element("jid")?.textContent?.let { metadata["jid"] = it }
        user.element("role")?.textContent?.let { metadata["role"] = it }
        // Avatar info
        user.element("avatar")?.element("info")?.let { info ->
            info.getAttribute("url")?.let { metadata["avatar_uri"] = it }
        }

        return MessageReferenceStorageItem().apply {
            this.owner = owner
            this.jid = opponent
            this.kind_ = "groupchat"
            this.metadata = metadata
        }
    }


    // MARK: - Helpers

    private fun getCachedMyMemberId(groupJid: String): String? {
        myMemberIdCache[groupJid]?.let { if (it.isNotEmpty()) return it }
        val groupPrimary = GroupChatStorageItem.genPrimary(groupJid, owner)
        val memberId = realm.query<GroupChatStorageItem>("primary = $0", groupPrimary)
            .first().find()?.myMemberId ?: ""
        myMemberIdCache[groupJid] = memberId
        return memberId.ifEmpty { null }
    }

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
            message.element("x", "https://xabber.com/protocol/groups") != null -> {
                // Check XEP-TYPE entity type for group subtypes
                val entityType = message.element("x", "https://xabber.com/protocol/entity-type")?.textContent
                when (entityType) {
                    "incognito" -> ConversationType.Incognito
                    "private" -> ConversationType.Private
                    else -> ConversationType.Group
                }
            }
            message.element("channel", "https://xabber.com/protocol/channels") != null -> ConversationType.Channel
            message.element("omemo", "urn:xmpp:omemo:2") != null -> ConversationType.Omemo
            message.element("omemo", "urn:xmpp:omemo:1") != null -> ConversationType.Omemo1
            message.element("axolotl", "eu.siacs.conversations.axolotl") != null -> ConversationType.Axolotl
            message.element("notification", "urn:xabber:xen:0") != null -> ConversationType.Notifications
            else -> ConversationType.Regular
        }
    }

    fun deleteSelfChats() {
        CoroutineScope(Dispatchers.IO).launch {
            realm.write {
                val selfChats = query<LastChatsStorageItem>("owner = $0 AND jid = $0", owner).find()
                delete(selfChats)
            }
        }
    }
}