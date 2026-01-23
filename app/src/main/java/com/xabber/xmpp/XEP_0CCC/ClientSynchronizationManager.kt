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

    private suspend fun readConversationMetadata(query: Element, owner: String) = withContext(Dispatchers.IO) {
        val conversations = query.getElementsByTagName("conversation")
        val stamp = query.getAttribute("stamp")?.toLongOrNull() ?: 0L

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
            val existingChats = query<LastChatsStorageItem>("owner = $0", owner).find()

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
                    val conversationStamp = conversation.getAttribute("stamp")?.toLongOrNull() ?: 0L

                    val conversationType = ConversationType.values().firstOrNull { it.rawValue == type }

                    val metadataList = conversation.getElementsByTagName("metadata")
                    var unreadCount = 0L
                    var unreadAfter: Long? = null
                    var lastMessage: MessageStorageItem? = null
                    var messageDate = conversationStamp
                    var lastMessageId = ""

                    for (j in 0 until metadataList.length) {
                        val metadata = metadataList.item(j) as Element
                        val node = metadata.getAttribute("node")?.takeIf { it.isNotBlank() }
                        if (node != "https://xabber.com/protocol/synchronization") {
                            continue
                        }

                        // Обработка <unread after='...' count='...'/>
                        val unread = metadata.getElementsByTagName("unread").item(0) as? Element
                        if (unread != null) {
                            unreadCount = unread.getAttribute("count")?.toLongOrNull() ?: 0L
                            unreadAfter = unread.getAttribute("after")?.toLongOrNull()
//                            Log.w("UNREAD", "check unreadafter $unreadAfter unreadcount $unreadCount unread $unread")
                        }

                        val lastMessageElement = metadata.getElementsByTagName("last-message").item(0) as? Element
                        lastMessageElement?.let { messageElement ->
                            val message = messageElement.getElementsByTagName("message").item(0) as? Element
                            message?.let {
                                val messageId = it.getAttribute("id")?.takeIf { it.isNotBlank() } ?: ""
                                if (messageId.isEmpty()) {
                                    return@let
                                }
                                val fromJid = it.getAttribute("from")?.let { XMPPJID(it).bare() } ?: jid
                                val toJid   = it.getAttribute("to")?.let { XMPPJID(it).bare() } ?: owner
                                val body = it.getElementsByTagName("body").item(0)?.textContent?.trim() ?: ""
                                if (body.isEmpty()) return@let
                                val isOutgoing = fromJid == owner
                                val opponent   = if (isOutgoing) toJid else fromJid
                                val rawStamp = conversation.getAttribute("stamp")?.takeIf { it.isNotBlank() }
                                val timestamp = rawStamp?.toLongOrNull()

                                val messagePrimary = MessageStorageItem.genPrimary(messageId, owner)
                                val existingMessage = query<MessageStorageItem>("primary = $0", messagePrimary).first().find()
                                lastMessage = if (existingMessage == null) {
                                    copyToRealm(MessageStorageItem().apply {
                                        primary = messagePrimary
                                        this.messageId = messageId
                                        this.owner = owner
                                        this.opponent = opponent
                                        this.body = body
                                        this.date = timestamp!! / 1000
                                        this.sentDate = timestamp / 1000
                                        this.editDate = 0L
                                        this.outgoing = isOutgoing
                                        this.conversationType_ = type
                                        this.isRead = false
                                        this.state = if (isOutgoing) MessageSendingState.Sent else MessageSendingState.Deliver
                                        updatePrimary()
                                    }, UpdatePolicy.ALL)
                                } else {
                                    existingMessage
                                }
                                messageDate = timestamp!!
                                lastMessageId = messageId
                                Log.d("ClientSyncManager", "Set lastMessage for jid=$jid, messageId=$messageId, outgoing=${lastMessage!!.outgoing}, from=$fromJid, to=$toJid")
                            }
                        }
                    }

                    if (lastMessage == null || messageDate == 0L || lastMessageId.isEmpty()) {
                        return@forEach
                    }

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

                    val chatPrimary = LastChatsStorageItem.genPrimary(jid, owner, conversationType!!)
                    if (chatPrimary.isEmpty()) {
                        return@forEach
                    }

                    // Получаем все сообщения в этом чате
                    val allMessagesInChat = query<MessageStorageItem>(
                        "owner = $0 AND opponent = $1 AND conversationType_ = $2",
                        owner, jid, type
                    ).find()

                    // ПРАВИЛЬНАЯ логика обработки unreadAfter:
                    // unreadAfter - временная метка ПОСЛЕ которой все сообщения считаются непрочитанными
                    // То есть: date <= unreadAfter -> прочитаны, date > unreadAfter -> непрочитаны

                    if (unreadCount == 0L) {
                        // Нет непрочитанных сообщений
                        // Это может означать два варианта:
                        // 1. Все сообщения прочитаны (в том числе последнее)
                        // 2. Нет вообще сообщений в чате

                        allMessagesInChat.forEach { msg ->
                            if (msg.outgoing) {
                                // Исходящие: должны быть Read
                                msg.state = MessageSendingState.Read
                            } else {
                                // Входящие: должны быть прочитаны
                                msg.isRead = true
                                msg.state = MessageSendingState.Read
                            }
                        }
                        Log.d("ClientSyncManager", "No unread messages for jid=$jid, marking all as read")
                    } else if (unreadAfter != null && unreadAfter > 0) {
                        // Есть непрочитанные сообщения и есть граница unreadAfter
                        val unreadAfterMs = unreadAfter / 1_000 // Конвертируем в миллисекунды

                        allMessagesInChat.forEach { msg ->
                            if (msg.outgoing) {
                                // Исходящие сообщения
                                msg.state = if (msg.date <= unreadAfterMs) {
                                    MessageSendingState.Read // Прочитаны собеседником
                                } else {
                                    MessageSendingState.Deliver // Доставлены, но не прочитаны
                                }
                            } else {
                                // Входящие сообщения
                                msg.isRead = msg.date <= unreadAfterMs
                                msg.state = if (msg.date <= unreadAfterMs) {
                                    MessageSendingState.Read // Мы прочитали
                                } else {
                                    MessageSendingState.Sent // Не прочитаны нами
                                }
                            }
                        }
                        Log.d("ClientSyncManager", "Messages updated based on unreadAfter=$unreadAfterMs for jid=$jid, unreadCount=$unreadCount")
                    } else {
                        // Есть непрочитанные, но нет unreadAfter - это аномальная ситуация
                        // Помечаем все сообщения как непрочитанные для безопасности
                        allMessagesInChat.forEach { msg ->
                            if (msg.outgoing) {
                                msg.state = MessageSendingState.Deliver
                            } else {
                                msg.isRead = false
                                msg.state = MessageSendingState.Sent
                            }
                        }
                        Log.w("ClientSyncManager", "Unread messages but no unreadAfter for jid=$jid, marking all as unread")
                    }

                    // Пересчитываем фактическое количество непрочитанных
                    val actualUnread = allMessagesInChat.count { !it.outgoing && !it.isRead }

                    // Проверяем соответствие с unreadCount (для отладки)
                    if (actualUnread != unreadCount.toInt()) {
                        Log.w("ClientSyncManager", "Mismatch in unread count for jid=$jid: calculated=$actualUnread, server=$unreadCount")
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
                            this.messageDate = messageDate
                            this.lastMessageId = lastMessageId
                            this.pinnedPosition = pinned
                            this.muteExpired = -1
                            this.rosterItem = rosterItem
                            this.lastMessage = lastMessage

                            // Устанавливаем lastReadMessageDate на основе unreadAfter
                            if (unreadAfter != null && unreadAfter > 0) {
                                this.lastReadMessageDate = unreadAfter / 1_000 // Конвертируем в миллисекунды
                                Log.d("ClientSyncManager", "Set lastReadMessageDate to ${unreadAfter / 1_000} for jid=$jid")
                            } else if (unreadCount == 0L) {
                                // Все сообщения прочитаны - устанавливаем на дату последнего сообщения
                                this.lastReadMessageDate = messageDate
                                Log.d("ClientSyncManager", "Set lastReadMessageDate to messageDate=$messageDate for jid=$jid (all read)")
                            }
                        }, UpdatePolicy.ALL)
                        Log.d("ClientSyncManager", "Created LastChatsStorageItem: primary=$chatPrimary, lastMessageId=$lastMessageId, unreadCount=$actualUnread, unreadAfter=$unreadAfter")
                    } else {
                        findLatest(existingChat)?.apply {
                            if (messageDate > this.messageDate) {
                                this.isArchived = status == "archived"
                                this.unread = actualUnread
                                this.messageDate = messageDate
                                this.lastMessageId = lastMessageId
                                this.pinnedPosition = pinned
                                this.rosterItem = rosterItem
                                this.lastMessage = lastMessage

                                // Обновляем lastReadMessageDate на основе unreadAfter
                                if (unreadAfter != null && unreadAfter > 0) {
                                    this.lastReadMessageDate = unreadAfter / 1_000 // Конвертируем в миллисекунды
                                    Log.d("ClientSyncManager", "Updated lastReadMessageDate to ${unreadAfter / 1_000} for jid=$jid")
                                } else if (unreadCount == 0L) {
                                    // Все сообщения прочитаны - устанавливаем на дату последнего сообщения
                                    this.lastReadMessageDate = messageDate
                                    Log.d("ClientSyncManager", "Updated lastReadMessageDate to messageDate=$messageDate for jid=$jid (all read)")
                                }
                            }
                            Log.d("ClientSyncManager", "Updated LastChatsStorageItem: primary=$chatPrimary, lastMessageId=$lastMessageId, unreadCount=$actualUnread, unreadAfter=$unreadAfter")
                        }
                    }
                }
            }
            val updatedChats = query<LastChatsStorageItem>("owner = $0", owner).find()
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