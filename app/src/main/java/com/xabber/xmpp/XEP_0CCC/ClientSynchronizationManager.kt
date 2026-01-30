package com.xabber.xmpp.XEP_0CCC

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.account.AccountManager
import com.xabber.common.SettingManager
import com.xabber.stream.Stream
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.utils.parseTimestamp
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMPPMessage
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

@RequiresApi(Build.VERSION_CODES.O)
class ClientSynchronizationManager(owner: String) {
    private val realm = Realm.open(defaultRealmConfig())
    private var version: String = SettingManager.getKey(owner, SettingManager.KeyScope.CLIENT_SYNCHRONIZATION, "version") ?: "0"
    var isAvailable: Boolean = true // Force true for testing
    private val owner: String = owner.takeIf { it.isNotBlank() } ?: run {
        val account = realm.query<AccountStorageItem>().find().firstOrNull()
        account?.jid?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("No valid account found for ClientSynchronizationManager")
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncBuffer = Channel<SyncItem>(capacity = 1) // Buffer for sync operations
    private val bufferMutex = Mutex()
    private var processingJob: Job? = null

    data class SyncItem(
        val stream: Stream,
        val customVer: String? = null,
        val after: String? = null
    )

    init {
        if (version.isEmpty()) {
            SettingManager.saveClientSynchronizationVersion(owner, "0")
            version = "0"
        }
        Log.d("ClientSyncManager", "Initialized for owner: $owner, version: $version")
        if (owner.isBlank()) {
        }
        scope.launch {
            startSyncProcessing()
            checkLastChats()
        }
    }

    suspend fun startSyncProcessing() {
//        performSync(stream!!, "0", null)
        processingJob?.cancelAndJoin()
        processingJob = scope.launch {
            syncBuffer.consumeAsFlow().collect { item ->
                performSync(item.stream, item.customVer, item.after)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun sync(stream: Stream, customVer: String? = null, after: String? = null): Boolean {
        syncBuffer.send(SyncItem(stream, customVer, after))

        return true
    }

    suspend fun performSync(stream: Stream, customVer: String?, after: String?) {
        val syncId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
        val query = buildString {
            append("<query xmlns='https://xabber.com/protocol/synchronization'")
            if (customVer?.isNotEmpty() == true) {
                append(" stamp='$customVer'")
            } else if (version.isNotEmpty()) {
                append(" stamp='$version'")
            }
            append(">")
            append("<set xmlns='http://jabber.org/protocol/rsm'>")
            append("<max>40</max>") // Reduced page size from 60 to 20
            if (after != null) {
                append("<after>$after</after>")
            }
            append("</set>")
            append("</query>")
        }
        val iq = """
            <iq type='get' id='SYNC: $syncId'>$query</iq>
        """.trimIndent()
        val success = stream.socket?.write(iq) == true
        Log.d("ClientSyncManager", "Sent sync request for $owner with id $syncId, version: ${customVer ?: version}, after: $after, success: $success")
    }

    fun getChat(jid: String, type: ConversationType): LastChatsStorageItem? {
        if (owner.isBlank()) {
            return null
        }
        var chat: LastChatsStorageItem? = null
        realm.writeBlocking {
            chat = this.query<LastChatsStorageItem>("jid = $0 AND owner = $1 AND conversationType_ = $2", jid, owner, type.rawValue).first().find()
        }
        return chat
    }

    suspend fun pinChat(stream: Stream, chatId: String, type: ConversationType) {
        if (owner.isBlank()) {
            Log.e("ClientSyncManager", "Cannot pin chat: invalid owner")
            return
        }
        val stanza = "<iq type='set' id='pin_${chatId}' from='$owner'><query xmlns='https://xabber.com/protocol/synchronization'><conversation jid='$chatId' type='${type.rawValue}' pinned='1'/></query></iq>"
        Log.d("ClientSyncManager", "Sending pin request for chat $chatId: $stanza")
        try {
            if (stream.socket?.write(stanza) == true) {
                Log.d("ClientSyncManager", "Pin request sent successfully for chat $chatId")
            } else {
                Log.e("ClientSyncManager", "Failed to send pin request for chat $chatId")
            }
        } catch (e: Exception) {
            Log.e("ClientSyncManager", "Error sending pin request for chat $chatId: ${e.message}")
        }
    }

    suspend fun update(stream: Stream, chatId: String, type: ConversationType, status: String? = null, mute: Double? = null) {
        if (owner.isBlank()) {
            Log.e("ClientSyncManager", "Cannot update chat: invalid owner")
            return
        }
        val statusAttr = if (status != null) "status='$status'" else ""
        val muteAttr = if (mute != null) "mute='$mute'" else ""
        val stanza = "<iq type='set' id='update_${chatId}' from='$owner'><query xmlns='https://xabber.com/protocol/synchronization'><conversation jid='$chatId' type='${type.rawValue}' $statusAttr $muteAttr/></query></iq>"
        Log.d("ClientSyncManager", "Sending update request for chat $chatId: $stanza")
        try {
            if (stream.socket?.write(stanza) == true) {
                Log.d("ClientSyncManager", "Update request sent successfully for chat $chatId")
            } else {
                Log.e("ClientSyncManager", "Failed to send update request for chat $chatId")
            }
        } catch (e: Exception) {
            Log.e("ClientSyncManager", "Error sending update request for chat $chatId: ${e.message}")
        }
    }

    private fun normalizeTimestampForSync(timestamp: Long): Long {
        return when {
            // Microseconds (16 digits) - convert to milliseconds
            timestamp.toString().length == 16 -> timestamp / 1000L

            // Milliseconds (13 digits) - use as-is
            timestamp.toString().length == 13 -> timestamp

            // Seconds (10 digits) - convert to milliseconds
            timestamp.toString().length == 10 -> timestamp * 1000L

            // Default - assume milliseconds
            else -> timestamp
        }
    }

    private suspend fun readConversationMetadata(query: Element, owner: String) = withContext(Dispatchers.IO) {
        val conversations = query.getElementsByTagName("conversation")

        val excludedJidsForRoster = setOf(
            "favorites.redsolution.com",
            "redmine@redsolution.com",
            "xabber@xmppdev01.xabber.com"
        )
        val excludedTypesForRoster = setOf(
            "urn:xabber:xen:0",
            "urn:xabber:favorites:0",
            "https://xabber.com/protocol/groups"
        )

        realm.write {
            val batchSize = 10
            (0 until conversations.length step batchSize).forEach { start ->
                val batch = (start until minOf(start + batchSize, conversations.length)).map { idx ->
                    conversations.item(idx) as Element
                }
                batch.forEach { conversation ->
                    val jid = conversation.getAttribute("jid")?.let { XMPPJID(it).bare() }?.takeIf { it.isNotBlank() } ?: return@forEach
                    val type = conversation.getAttribute("type")?.takeIf { it.isNotBlank() } ?: return@forEach

                    if (jid == owner || type == "urn:xabber:xen:0" || jid == owner.substringAfter("@")) {
                        return@forEach
                    }

                    val status = conversation.getAttribute("status")?.takeIf { it.isNotBlank() } ?: "active"
                    val pinned = conversation.getAttribute("pinned")?.toLongOrNull() ?: 0L
                    val conversationStampUs = conversation.getAttribute("stamp")?.toLongOrNull() ?: 0L

                    val conversationType = ConversationType.values().firstOrNull { it.rawValue == type } ?: return@forEach

                    val metadataList = conversation.getElementsByTagName("metadata")
                    var unreadCount = 0L
                    var unreadAfterUs: Long? = null
                    var displayedId: String? = null
                    var deliveredId: String? = null
                    var lastMessage: MessageStorageItem? = null
                    var messageDateUs = conversationStampUs
                    var lastMessageId = ""

                    for (j in 0 until metadataList.length) {
                        val metadata = metadataList.item(j) as Element
                        val node = metadata.getAttribute("node")?.takeIf { it.isNotBlank() }
                        if (node != "https://xabber.com/protocol/synchronization") continue

                        val unread = metadata.getElementsByTagName("unread").item(0) as? Element
                        if (unread != null) {
                            unreadCount = unread.getAttribute("count")?.toLongOrNull() ?: 0L
                            unreadAfterUs = unread.getAttribute("after")?.toLongOrNull()

                            Log.d("ClientSyncManager",
                                "Found unread data for jid=$jid: " +
                                        "unreadAfterUs=${unreadAfterUs ?: "null"} (${(unreadAfterUs ?: 0) / 1000L} ms), " +
                                        "unreadCount=$unreadCount")
                        }

                        val displayed = metadata.getElementsByTagName("displayed").item(0) as? Element
                        displayedId = displayed?.getAttribute("id")?.takeIf { it != "0" && it.isNotEmpty() }

                        val delivered = metadata.getElementsByTagName("delivered").item(0) as? Element
                        deliveredId = delivered?.getAttribute("id")?.takeIf { it != "0" && it.isNotEmpty() }

                        Log.d("ClientSyncManager",
                            "Found markers for jid=$jid: " +
                                    "displayedId=$displayedId, " +
                                    "deliveredId=$deliveredId")

                        val lastMessageElement = metadata.getElementsByTagName("last-message").item(0) as? Element
                        lastMessageElement?.let { messageElement ->
                            val message = messageElement.getElementsByTagName("message").item(0) as? Element
                            message?.let {
                                val messageId = it.getAttribute("id")?.takeIf { it.isNotBlank() } ?: return@let
                                val fromJid = it.getAttribute("from")?.let { XMPPJID(it).bare() } ?: jid
                                val toJid = it.getAttribute("to")?.let { XMPPJID(it).bare() } ?: owner
                                val body = it.getElementsByTagName("body").item(0)?.textContent?.trim() ?: ""
                                if (body.isEmpty()) return@let

                                val isOutgoing = fromJid == owner
                                val opponent = if (isOutgoing) toJid else fromJid

                                var timestampUs = 0L
                                val timeElement = it.getElementsByTagName("time").item(0) as? Element
                                if (timeElement != null) {
                                    val stampStr = timeElement.getAttribute("stamp")
                                    if (stampStr != null) {
                                        try {
                                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                                            sdf.timeZone = TimeZone.getTimeZone("UTC")
                                            val ms = sdf.parse(stampStr)?.time ?: 0L
                                            timestampUs = ms * 1000L
                                        } catch (e: Exception) {
                                            Log.w("ClientSyncManager", "Failed to parse message time stamp: $stampStr", e)
                                        }
                                    }
                                }

                                if (timestampUs == 0L) {
                                    timestampUs = conversationStampUs
                                }

                                val messagePrimary = MessageStorageItem.genPrimary(messageId, owner)
                                val existingMessage = query<MessageStorageItem>("primary = $0", messagePrimary).first().find()
                                lastMessage = if (existingMessage == null) {
                                    copyToRealm(MessageStorageItem().apply {
                                        primary = messagePrimary
                                        this.messageId = messageId
                                        this.owner = owner
                                        this.opponent = opponent
                                        this.body = body
                                        this.date = timestampUs / 1000L  // микросекунды → миллисекунды
                                        this.sentDate = timestampUs / 1000L
                                        this.editDate = 0L
                                        this.outgoing = isOutgoing
                                        this.conversationType_ = type

                                        // Определяем состояние на основе timestamp'а сообщения
                                        val messageTimestampUs = timestampUs
                                        val displayedIdUs = displayedId?.toLongOrNull()
                                        val deliveredIdUs = deliveredId?.toLongOrNull()

                                        if (isOutgoing) {
                                            // Исходящие сообщения
                                            this.state = when {
                                                displayedIdUs != null && messageTimestampUs <= displayedIdUs -> {
//                                                    Log.d("ClientSyncManager", "Outgoing message marked as Read: timestampUs=$messageTimestampUs <= displayedIdUs=$displayedIdUs")
                                                    MessageSendingState.Read
                                                }
                                                deliveredIdUs != null && messageTimestampUs <= deliveredIdUs -> {
//                                                    Log.d("ClientSyncManager", "Outgoing message marked as Deliver: timestampUs=$messageTimestampUs <= deliveredIdUs=$deliveredIdUs")
                                                    MessageSendingState.Deliver
                                                }
                                                else -> {
//                                                    Log.d("ClientSyncManager", "Outgoing message marked as Sent: timestampUs=$messageTimestampUs")
                                                    MessageSendingState.Sent
                                                }
                                            }
                                            this.isRead = true  // Исходящие всегда считаются прочитанными
                                        } else {
                                            // Входящие сообщения
                                            val isRead = unreadAfterUs != null && messageTimestampUs <= unreadAfterUs
                                            this.isRead = isRead
                                            this.state = if (isRead) {
//                                                Log.d("ClientSyncManager", "Incoming message marked as Read: timestampUs=$messageTimestampUs <= unreadAfterUs=$unreadAfterUs")
                                                MessageSendingState.Read
                                            } else {
//                                                Log.d("ClientSyncManager", "Incoming message marked as Deliver: timestampUs=$messageTimestampUs > unreadAfterUs=$unreadAfterUs")
                                                MessageSendingState.Deliver
                                            }
                                        }
                                        updatePrimary()
                                    }, UpdatePolicy.ALL)
                                } else {
                                    // Обновляем состояние существующего сообщения
                                    val messageTimestampUs = existingMessage.sentDate * 1000L  // миллисекунды → микросекунды
                                    val displayedIdUs = displayedId?.toLongOrNull()
                                    val deliveredIdUs = deliveredId?.toLongOrNull()

                                    if (existingMessage.outgoing) {
                                        // Исходящие
                                        existingMessage.state = when {
                                            displayedIdUs != null && messageTimestampUs <= displayedIdUs -> {
//                                                Log.d("ClientSyncManager", "Updated outgoing message to Read: timestampUs=$messageTimestampUs <= displayedIdUs=$displayedIdUs")
                                                MessageSendingState.Read
                                            }
                                            deliveredIdUs != null && messageTimestampUs <= deliveredIdUs -> {
//                                                Log.d("ClientSyncManager", "Updated outgoing message to Deliver: timestampUs=$messageTimestampUs <= deliveredIdUs=$deliveredIdUs")
                                                MessageSendingState.Deliver
                                            }
                                            else -> {
                                                if (existingMessage.state != MessageSendingState.Sent) {
//                                                    Log.d("ClientSyncManager", "Updated outgoing message to Sent: timestampUs=$messageTimestampUs")
                                                    MessageSendingState.Sent
                                                } else {
                                                    existingMessage.state
                                                }
                                            }
                                        }
                                        existingMessage.isRead = true
                                    } else {
                                        // Входящие
                                        val isRead = unreadAfterUs != null && messageTimestampUs <= unreadAfterUs
                                        existingMessage.isRead = isRead
                                        existingMessage.state = if (isRead) {
//                                            Log.d("ClientSyncManager", "Updated incoming message to Read: timestampUs=$messageTimestampUs <= unreadAfterUs=$unreadAfterUs")
                                            MessageSendingState.Read
                                        } else {
//                                            Log.d("ClientSyncManager", "Updated incoming message to Deliver: timestampUs=$messageTimestampUs > unreadAfterUs=$unreadAfterUs")
                                            MessageSendingState.Deliver
                                        }
                                    }
                                    existingMessage
                                }

                                messageDateUs = timestampUs
                                lastMessageId = messageId
                            }
                        }
                    }

                    if (lastMessage == null || messageDateUs == 0L || lastMessageId.isEmpty()) return@forEach

                    val isExcludedForRoster = excludedJidsForRoster.contains(jid) || excludedTypesForRoster.contains(type) || jid == owner
                    var rosterItem: RosterStorageItem? = null
                    if (!isExcludedForRoster) {
                        rosterItem = query<RosterStorageItem>("jid = $0 AND owner = $1", jid, owner).first().find()
                            ?: copyToRealm(RosterStorageItem().apply {
                                primary = RosterStorageItem.genPrimary(jid, owner)
                                this.jid = jid
                                this.owner = owner
                                this.customNickname = jid
                            }, UpdatePolicy.ALL)
                    }

                    val chatPrimary = LastChatsStorageItem.genPrimary(jid, owner, conversationType)
                    if (chatPrimary.isEmpty()) return@forEach

                    // Применяем unread after ко всем существующим сообщениям
                    val allMessagesInChat = query<MessageStorageItem>(
                        "owner = $0 AND opponent = $1 AND conversationType_ = $2",
                        owner, jid, type
                    ).find()

                    // Конвертируем в микросекунды для сравнения
                    val displayedIdUs = displayedId?.toLongOrNull()
                    val deliveredIdUs = deliveredId?.toLongOrNull()
                    val unreadAfterMs = if (unreadAfterUs != null) unreadAfterUs / 1000L else 0L

                    // Calculate actual unread based on unreadAfter
                    var actualUnread = 0

                    allMessagesInChat.forEach { msg ->
                        val messageTimestampUs = msg.sentDate * 1000L  // миллисекунды → микросекунды

                        if (msg.outgoing) {
                            // Исходящие сообщения
                            msg.state = when {
                                displayedIdUs != null && messageTimestampUs <= displayedIdUs -> {
                                    MessageSendingState.Read
                                }
                                deliveredIdUs != null && messageTimestampUs <= deliveredIdUs -> {
                                    MessageSendingState.Deliver
                                }
                                else -> {
                                    if (msg.state_ < MessageSendingState.Sent.rawValue) {
                                        MessageSendingState.Sent
                                    } else {
                                        msg.state
                                    }
                                }
                            }
                            msg.isRead = true
                        } else {
                            // Входящие сообщения - используем unread after
                            if (unreadCount == 0L) {
                                // Все прочитаны
                                msg.isRead = true
                                msg.state = MessageSendingState.Read
                            } else if (unreadAfterMs > 0) {
                                // Используем порог unread after
                                val isRead = msg.sentDate <= unreadAfterMs
                                msg.isRead = isRead
                                msg.state = if (isRead) MessageSendingState.Read else MessageSendingState.Deliver

                                if (!isRead) {
                                    actualUnread++
                                }
                            } else {
                                // Нет порога, считаем все непрочитанными
                                msg.isRead = false
                                msg.state = MessageSendingState.Deliver
                                actualUnread++
                            }
                        }
                    }

                    // Если unreadCount от сервера не совпадает с нашим расчетом, используем серверное значение
                    if (unreadCount > 0 && actualUnread != unreadCount.toInt()) {
//                        Log.w("ClientSyncManager", "Unread mismatch: calculated=$actualUnread, server=$unreadCount for jid=$jid, using server value")
                        actualUnread = unreadCount.toInt()
                    }

                    val existingChat = query<LastChatsStorageItem>("jid = $0 AND owner = $1 AND conversationType_ = $2", jid, owner, type).first().find()
                    if (existingChat == null) {
                        copyToRealm(LastChatsStorageItem().apply {
                            primary = chatPrimary
                            this.jid = jid
                            this.owner = owner
                            this.conversationType_ = type
                            this.isArchived = status == "archived"
                            this.unread = actualUnread
                            this.messageDate = messageDateUs / 1000L
                            this.lastMessageId = lastMessageId
                            this.pinnedPosition = pinned
                            this.muteExpired = -1
                            this.rosterItem = rosterItem
                            this.lastMessage = lastMessage

                            // Сохраняем displayedId и deliveredId
                            this.displayedId = displayedId
                            this.deliveredId = deliveredId

                            // Сохраняем lastReadMessageDate из unread after
                            this.lastReadMessageDate = when {
                                unreadAfterMs > 0 -> unreadAfterMs
                                unreadCount == 0L -> messageDateUs / 1000L
                                else -> 0L
                            }

//                            Log.d("ClientSyncManager",
//                                "Created new chat for $jid: " +
//                                        "unread=$actualUnread (server=$unreadCount), " +
//                                        "lastReadMessageDate=$lastReadMessageDate, " +
//                                        "displayedId=$displayedId, " +
//                                        "deliveredId=$deliveredId")
                        }, UpdatePolicy.ALL)
                    } else {
                        findLatest(existingChat)?.apply {
                            if (messageDateUs / 1000L > this.messageDate) {
                                this.isArchived = status == "archived"
                                this.unread = actualUnread
                                this.messageDate = messageDateUs / 1000L
                                this.lastMessageId = lastMessageId
                                this.pinnedPosition = pinned
                                this.rosterItem = rosterItem
                                this.lastMessage = lastMessage

                                // Обновляем displayedId и deliveredId если они есть
                                if (displayedId != null) {
                                    val currentDisplayedIdUs = this.displayedId?.toLongOrNull() ?: 0L
                                    val newDisplayedIdUs = displayedId.toLongOrNull() ?: 0L
                                    if (newDisplayedIdUs > currentDisplayedIdUs) {
                                        this.displayedId = displayedId
                                    }
                                }

                                if (deliveredId != null) {
                                    val currentDeliveredIdUs = this.deliveredId?.toLongOrNull() ?: 0L
                                    val newDeliveredIdUs = deliveredId.toLongOrNull() ?: 0L
                                    if (newDeliveredIdUs > currentDeliveredIdUs) {
                                        this.deliveredId = deliveredId
                                    }
                                }

                                // Обновляем lastReadMessageDate из unread after
                                val newLastReadMessageDate = when {
                                    unreadAfterMs > 0 -> unreadAfterMs
                                    unreadCount == 0L -> messageDateUs / 1000L
                                    else -> this.lastReadMessageDate
                                }

                                if (newLastReadMessageDate > this.lastReadMessageDate) {
                                    this.lastReadMessageDate = newLastReadMessageDate
                                }

//                                Log.d("ClientSyncManager",
//                                    "Updated chat for $jid: " +
//                                            "unread=$actualUnread (server=$unreadCount), " +
//                                            "lastReadMessageDate=$lastReadMessageDate, " +
//                                            "displayedId=${this.displayedId}, " +
//                                            "deliveredId=${this.deliveredId}")
                            }
                        }
                    }

//                    Log.d("ClientSyncManager",
//                        "Chat processing complete: jid=$jid, " +
//                                "unread=$actualUnread (server=$unreadCount), " +
//                                "lastReadMessageDate=${existingChat?.lastReadMessageDate ?: "new"}")
                }
            }
        }
    }

    // Helper function for normalizing timestamps in Realm write context
    private fun normalizeTimestampInRealm(timestamp: Long): Long {
        return when {
            timestamp.toString().length == 16 -> timestamp / 1000L
            timestamp.toString().length == 13 -> timestamp
            timestamp.toString().length == 10 -> timestamp * 1000L
            else -> timestamp
        }
    }

    private suspend fun readSnapshot(query: Element) = withContext(Dispatchers.IO) {
        val stamp = query.getAttribute("stamp")?.toLongOrNull()?.toString() ?: "0"
        readConversationMetadata(query, owner)
        val countElement = query.getElementsByTagNameNS("http://jabber.org/protocol/rsm", "count").item(0) as? Element
        val count = countElement?.textContent?.toIntOrNull() ?: 0
        if (count < 20) { // Updated to match new page size
            version = stamp
            SettingManager.saveClientSynchronizationVersion(owner, version)
            Log.d("ClientSyncManager", "Sync completed, updated version to $version, count: $count")
        } else {
            // Pagination: Trigger another sync with the last conversation's stamp
            val conversations = query.getElementsByTagName("conversation")
            val lastStamp = if (conversations.length > 0) {
                (conversations.item(conversations.length - 1) as Element).getAttribute("stamp")?.toLongOrNull()?.toString() ?: stamp
            } else {
                stamp
            }
            val stream = AccountManager.find(owner)?.stream
            if (stream != null) {
                scope.launch {
                    sync(stream, version, after = lastStamp)
                }
            } else {
                Log.w("ClientSyncManager", "No stream available for pagination sync for $owner")
            }
        }
    }

    private suspend fun readPush(query: Element) = withContext(Dispatchers.IO) {
        val stamp = query.getAttribute("stamp")?.toLongOrNull()?.toString() ?: "0"
        readConversationMetadata(query, owner)
        version = stamp
        SettingManager.saveClientSynchronizationVersion(owner, version)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun read(iq: String) = withContext(Dispatchers.IO) {
        if (owner.isBlank()) {
            return@withContext
        }
        try {
            val cleanedIq = iq.trim()
            if (!cleanedIq.startsWith("<iq") || !cleanedIq.endsWith("</iq>")) {
                return@withContext
            }
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(cleanedIq.byteInputStream())
            val iqElement = document.documentElement
            val queryElement = iqElement.getElementsByTagNameNS("https://xabber.com/protocol/synchronization", "query").item(0) as? Element
            if (queryElement == null) {
                return@withContext
            }
            if (iqElement.getAttribute("type") == "result") {
                readSnapshot(queryElement)
            } else if (iqElement.getAttribute("type") == "set") {
                readPush(queryElement)
            }
        } catch (e: Exception) {
            Log.e("ClientSyncManager", "Failed to parse IQ stanza: ${e.message}", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun receiveClientSyncRaw(message: String) {
        if (owner.isBlank()) {
            return
        }
        try {
            val cleanedMessage = message.trim()
            if (!cleanedMessage.startsWith("<message") || !cleanedMessage.endsWith("</message>")) {
                return
            }

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(cleanedMessage))

            var eventType = parser.eventType
            var messageId: String? = null
            var from: String? = null
            var to: String? = null
            var type: String? = null
            var body: String? = null
            var timestamp: Long = System.currentTimeMillis()
            var inForwarded = false
            var inInnerMessage = false
            var conversationType: String = "urn:xabber:chat" // Default to regular chat

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        when {
                            tagName == "message" && !inForwarded -> {
                                messageId = parser.getAttributeValue(null, "id")?.takeIf { it.isNotBlank() }
                                from = parser.getAttributeValue(null, "from")?.takeIf { it.isNotBlank() }
                                to = parser.getAttributeValue(null, "to")?.takeIf { it.isNotBlank() } ?: owner
                                type = parser.getAttributeValue(null, "type")?.takeIf { it.isNotBlank() } ?: "chat"
                            }
                            tagName == "forwarded" && parser.getAttributeValue(null, "xmlns") == "urn:xmpp:forward:0" -> {
                                inForwarded = true
                            }
                            tagName == "message" && inForwarded -> {
                                inInnerMessage = true
                                messageId = parser.getAttributeValue(null, "id")?.takeIf { it.isNotBlank() } ?: messageId
                                from = parser.getAttributeValue(null, "from")?.split("/")?.get(0)?.takeIf { it.isNotBlank() } ?: from
                                to = parser.getAttributeValue(null, "to")?.takeIf { it.isNotBlank() } ?: to
                                type = parser.getAttributeValue(null, "type")?.takeIf { it.isNotBlank() } ?: type
                            }
                            tagName == "body" && inInnerMessage -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    body = parser.text.trim()
                                }
                            }
                            tagName == "time" && inInnerMessage && parser.getAttributeValue(null, "xmlns") == "https://xabber.com/protocol/delivery" -> {
                                val stamp = parser.getAttributeValue(null, "stamp")
                                if (stamp != null) {
                                    try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                                        timestamp = sdf.parse(stamp)?.time ?: System.currentTimeMillis()
                                    } catch (e: Exception) {
                                        timestamp = System.currentTimeMillis()
                                    }
                                }
                            }
                            tagName == "x" && parser.getAttributeValue(null, "xmlns") == "https://xabber.com/protocol/groups" -> {
                                conversationType = "https://xabber.com/protocol/groups"
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "forwarded" -> inForwarded = false
                            "message" -> if (inForwarded) inInnerMessage = false
                        }
                    }
                }
                eventType = parser.next()
            }

            if (messageId.isNullOrEmpty() || from.isNullOrEmpty() || to.isNullOrEmpty() || body.isNullOrEmpty()) {
                return
            }

            // Determine conversation type based on JID or type
            if (to == "favorites.redsolution.com") {
                conversationType = "urn:xabber:favorites:0"
            }

            val conversationTypeEnum = ConversationType.values().firstOrNull { it.rawValue == conversationType } ?: ConversationType.Regular
            val chatJid = if (from == owner) to else from // Use destination for outgoing, sender for incoming
            val messagePrimary = MessageStorageItem.genPrimary(messageId, owner)
//            if (messagePrimary.isEmpty()) {
//                Log.w("ClientSyncManager", "Skipping message with invalid primary key for messageId=$messageId, owner=$owner")
//                return
//            }
            val chatPrimary = LastChatsStorageItem.genPrimary(chatJid, owner, conversationTypeEnum)
            if (chatPrimary.isEmpty()) {
                Log.w("ClientSyncManager", "Skipping chat creation due to invalid primary key for jid=$chatJid, owner=$owner, type=$conversationType")
                return
            }

            realm.write {
                val rosterItem = query<RosterStorageItem>("jid = $0 AND owner = $1", chatJid, owner).first().find()
                    ?: copyToRealm(RosterStorageItem().apply {
                        primary = RosterStorageItem.genPrimary(chatJid, owner)
                        this.jid = chatJid
                        this.owner = owner
                        this.customNickname = chatJid
                    }, UpdatePolicy.ALL)
                Log.d("ClientSyncManager", "Created/Used RosterStorageItem for jid=$chatJid")

                val message = copyToRealm(MessageStorageItem().apply {
                    primary = messagePrimary
                    this.messageId = messageId
                    this.owner = owner
                    this.opponent = chatJid // Use chatJid (destination for outgoing, sender for incoming)
                    this.body = body
                    this.date = timestamp/1000
                    this.sentDate = timestamp/1000
                    this.editDate = 0L
                    this.outgoing = from == owner
                    this.conversationType_ = conversationType
                    this.isRead = from == owner // Outgoing messages are read
                    this.state = if (from == owner) MessageSendingState.Deliver else MessageSendingState.Sent
                }, UpdatePolicy.ALL)
                Log.d("ClientSyncManager", "Saved message $messageId for jid=$chatJid in receiveClientSyncRaw, body=${body.take(50)}")

                val chat = query<LastChatsStorageItem>("jid = $0 AND owner = $1 AND conversationType_ = $2", chatJid, owner, conversationType).first().find()
                if (chat == null) {
                    copyToRealm(LastChatsStorageItem().apply {
                        primary = chatPrimary
                        this.jid = chatJid
                        this.owner = owner
                        this.conversationType_ = conversationType
                        this.isArchived = false
                        this.unread = if (from == owner) 0 else 1
                        this.messageDate = timestamp/1000
                        this.lastMessageId = messageId
                        this.pinnedPosition = 0
                        this.muteExpired = -1
                        this.rosterItem = rosterItem
                        this.lastMessage = message
                    }, UpdatePolicy.ALL)
                    Log.d("ClientSyncManager", "Created new LastChatsStorageItem for jid=$chatJid, type=$conversationType, primary=$chatPrimary in receiveClientSyncRaw")
                } else {
                    findLatest(chat)?.apply {
                        this.unread = if (from == owner) this.unread else this.unread + 1
                        this.messageDate = timestamp/1000
                        this.lastMessageId = messageId
                        this.lastMessage = message
                        this.isArchived = false
                    }
                    Log.d("ClientSyncManager", "Updated LastChatsStorageItem for jid=$chatJid, type=$conversationType in receiveClientSyncRaw")
                }
            }
            checkLastChats()
        } catch (e: Exception) {
            Log.e("ClientSyncManager", "Failed to parse message in receiveClientSyncRaw: ${e.message}", e)
        }
    }

    private suspend fun checkLastChats() {
        if (owner.isBlank()) {
            return
        }
        realm.write {
            // Query chats within the transaction to ensure fresh data
            val chats = query<LastChatsStorageItem>("owner = $0", owner).find()
            chats.forEach { chat ->
                // Use a snapshot of properties to avoid accessing invalidated objects
                val jid = chat.jid
                val conversationType = chat.conversationType_
                val chatPrimary = LastChatsStorageItem.genPrimary(jid, owner, ConversationType.fromRaw(conversationType))
                if (chatPrimary.isEmpty()) {
                    findLatest(chat)?.let { delete(it) }
                    return@forEach
                }
            }
        }
    }

    fun reset() {
        scope.coroutineContext[Job]?.cancel()
        realm.close()
        Log.d("ClientSyncManager", "Reset and closed Realm for owner $owner")
    }
}