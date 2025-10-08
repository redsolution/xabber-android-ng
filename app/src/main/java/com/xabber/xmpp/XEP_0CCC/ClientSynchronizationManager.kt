package com.xabber.xmpp.XEP_0CCC

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.common.AccountManager
import com.xabber.common.SettingManager
import com.xabber.common.Stream
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())
    private val syncBuffer = Channel<SyncItem>(capacity = 50) // Buffer for sync operations
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
            Log.e("ClientSyncManager", "Invalid owner: empty or null")
        }
        scope.launch {
            startSyncProcessing()
            checkLastChats()
        }
    }

    private suspend fun startSyncProcessing() {
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
        Log.d("ClientSyncManager", "Buffered sync request for $owner, version: ${customVer ?: version}, after: $after")
        return true
    }

    private suspend fun performSync(stream: Stream, customVer: String?, after: String?) {
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
            append("<max>20</max>") // Reduced page size from 60 to 20
            if (after != null) {
                append("<after>$after</after>")
            }
            append("</set>")
            append("</query>")
        }
        val iq = """
            <iq type='get' id='$syncId' from='$owner' to='$owner'>$query</iq>
        """.trimIndent()
        val success = stream.socket?.write(iq) == true
        Log.d("ClientSyncManager", "Sent sync request for $owner with id $syncId, version: ${customVer ?: version}, after: $after, success: $success")
    }

    suspend fun getChat(jid: String, type: ConversationType): LastChatsStorageItem? {
        if (owner.isBlank()) {
            Log.e("ClientSyncManager", "Cannot get chat: invalid owner")
            return null
        }
        var chat: LastChatsStorageItem? = null
        realm.write {
            chat = this.query<LastChatsStorageItem>("jid = $0 AND owner = $1 AND conversationType_ = $2", jid, owner, type.rawValue).first().find()
        }
        Log.d("ClientSyncManager", "getChat for jid=$jid, type=$type, owner=$owner: ${if (chat != null) "Found" else "Not found"}")
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
        Log.d("ClientSyncManager", "Processing ${conversations.length} conversations with stamp $stamp for owner $owner")

        val excludedJidsForRoster = setOf("favorites.redsolution.com", "redmine@redsolution.com", "xabber@xmppdev01.xabber.com")
        val excludedTypesForRoster = setOf("urn:xabber:xen:0", "urn:xabber:favorites:0", "https://xabber.com/protocol/groups")
        val validConversationTypes = setOf("urn:xabber:chat", "https://xabber.com/protocol/groups", "urn:xabber:favorites:0")

        realm.write {
            val existingChats = query<LastChatsStorageItem>("owner = $0", owner).find().associateBy { it.primary }.toMutableMap()
            val existingRosterItems = query<RosterStorageItem>("owner = $0", owner).find().associateBy { it.primary }.toMutableMap()

            val batchSize = 50
            val newChats = mutableListOf<LastChatsStorageItem>()
            val updatedChats = mutableListOf<LastChatsStorageItem>()
            val newMessages = mutableListOf<MessageStorageItem>()
            val newRosterItems = mutableListOf<RosterStorageItem>()

            (0 until conversations.length).forEach { idx ->
                val conversation = conversations.item(idx) as Element
                val jid = conversation.getAttribute("jid")?.takeIf { it.isNotBlank() } ?: return@forEach
                val type = conversation.getAttribute("type")?.takeIf { it.isNotBlank() } ?: return@forEach

                if (jid == owner || type == "urn:xabber:xen:0" || jid == owner.substringAfter("@")) {
                    Log.d("ClientSyncManager", "Skipping conversation: jid=$jid, type=$type")
                    return@forEach
                }

                if (type !in validConversationTypes) {
                    Log.w("ClientSyncManager", "Invalid conversation type $type for jid $jid, skipping")
                    return@forEach
                }

                val status = conversation.getAttribute("status")?.takeIf { it.isNotBlank() } ?: "active"
                val pinned = conversation.getAttribute("pinned")?.toLongOrNull() ?: 0L
                val conversationStamp = conversation.getAttribute("stamp")?.toLongOrNull() ?: 0L
                val conversationType = ConversationType.values().firstOrNull { it.rawValue == type } ?: ConversationType.Regular

                val metadataList = conversation.getElementsByTagName("metadata")
                var unreadCount = 0L
                var lastMessage: MessageStorageItem? = null
                var messageDate = 0L
                var lastMessageId = ""

                for (j in 0 until metadataList.length) {
                    val metadata = metadataList.item(j) as Element
                    if (metadata.getAttribute("node") != "https://xabber.com/protocol/synchronization") continue
                    val unread = metadata.getElementsByTagName("unread").item(0) as? Element
                    unreadCount = unread?.getAttribute("count")?.toLongOrNull() ?: 0L
                    val lastMessageElement = metadata.getElementsByTagName("last-message").item(0) as? Element
                    lastMessageElement?.let { messageElement ->
                        val message = messageElement.getElementsByTagName("message").item(0) as? Element
                        message?.let {
                            val messageId = it.getAttribute("id")?.takeIf { it.isNotBlank() } ?: return@let
                            val from = it.getAttribute("from")?.takeIf { it.isNotBlank() } ?: jid
                            val to = it.getAttribute("to")?.takeIf { it.isNotBlank() } ?: owner
                            val body = it.getElementsByTagName("body").item(0)?.textContent?.trim() ?: ""
                            if (body.isEmpty()) return@let
                            val timeElement = it.getElementsByTagName("time").item(0) as? Element
                            val timestamp = timeElement?.getAttribute("stamp")?.let { stamp ->
                                try {
                                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                                    sdf.parse(stamp)?.time ?: 0L
                                } catch (e: Exception) {
                                    Log.e("ClientSyncManager", "Failed to parse timestamp $stamp: ${e.message}")
                                    0L
                                }
                            } ?: 0L

                            val messagePrimary = MessageStorageItem.genPrimary(messageId, owner)
                            if (messagePrimary.isEmpty()) return@let
                            val existingMessage = query<MessageStorageItem>("primary = $0", messagePrimary).first().find()
                            lastMessage = if (existingMessage == null) {
                                val newMessage = MessageStorageItem().apply {
                                    primary = messagePrimary
                                    this.messageId = messageId
                                    this.owner = owner
                                    this.opponent = from
                                    this.body = body
                                    this.date = timestamp
                                    this.sentDate = timestamp
                                    this.editDate = 0L
                                    this.outgoing = to == owner
                                    this.conversationType_ = type
                                    this.isRead = unreadCount == 0L
                                    this.state = MessageSendingState.Sent
                                }
                                newMessages.add(newMessage)
                                newMessage
                            } else {
                                existingMessage
                            }
                            messageDate = timestamp
                            lastMessageId = messageId
                        }
                    }
                }

                if (lastMessage == null || messageDate == 0L || lastMessageId.isEmpty()) {
                    Log.d("ClientSyncManager", "Skipping conversation with no valid message for jid=$jid, type=$type")
                    return@forEach
                }

                val chatPrimary = LastChatsStorageItem.genPrimary(jid, owner, conversationType)
                if (chatPrimary.isEmpty()) return@forEach
                var rosterItem: RosterStorageItem? = null
                if (!excludedJidsForRoster.contains(jid) && !excludedTypesForRoster.contains(type) && jid != owner) {
                    rosterItem = existingRosterItems[RosterStorageItem.genPrimary(jid, owner)]
                        ?: RosterStorageItem().apply {
                            primary = RosterStorageItem.genPrimary(jid, owner)
                            this.jid = jid
                            this.owner = owner
                            this.customNickname = jid
                        }.also { newRosterItems.add(it) }
                }

                val existingChat = existingChats[chatPrimary]
                if (existingChat == null) {
                    val newChat = LastChatsStorageItem().apply {
                        primary = chatPrimary
                        this.jid = jid
                        this.owner = owner
                        this.conversationType_ = type
                        this.isArchived = status == "archived"
                        this.unread = unreadCount.toInt()
                        this.messageDate = messageDate
                        this.lastMessageId = lastMessageId
                        this.pinnedPosition = pinned
                        this.muteExpired = -1
                        this.rosterItem = rosterItem
                        this.lastMessage = lastMessage
                    }
                    newChats.add(newChat)
                } else {
                    findLatest(existingChat)?.apply {
                        if (messageDate > this.messageDate) {
                            this.isArchived = status == "archived"
                            this.unread = unreadCount.toInt()
                            this.messageDate = messageDate
                            this.lastMessageId = lastMessageId
                            this.pinnedPosition = pinned
                            this.rosterItem = rosterItem
                            this.lastMessage = lastMessage
                        }
                    }
                    updatedChats.add(existingChat)
                }
            }

            newMessages.forEach { copyToRealm(it, UpdatePolicy.ALL) }
            newRosterItems.forEach { copyToRealm(it, UpdatePolicy.ALL) }
            newChats.forEach { copyToRealm(it, UpdatePolicy.ALL) }
            Log.d("ClientSyncManager", "Processed ${conversations.length} conversations: ${newChats.size} created, ${updatedChats.size} updated")
        }
    }

    private suspend fun readSnapshot(query: Element) = withContext(Dispatchers.IO) {
        Log.d("ClientSyncManager", "Processing snapshot query")
        val stamp = query.getAttribute("stamp")?.toLongOrNull()?.toString() ?: "0"
        readConversationMetadata(query, owner)
        val countElement = query.getElementsByTagNameNS("http://jabber.org/protocol/rsm", "count").item(0) as? Element
        val count = countElement?.textContent?.toIntOrNull() ?: 0
        if (count < 20) {
            version = stamp
            SettingManager.saveClientSynchronizationVersion(owner, version)
            Log.d("ClientSyncManager", "Sync completed, updated version to $version, count: $count")
        } else {
            val conversations = query.getElementsByTagName("conversation")
            val lastStamp = if (conversations.length > 0) {
                (conversations.item(conversations.length - 1) as Element).getAttribute("stamp")?.toLongOrNull()?.toString() ?: stamp
            } else {
                stamp
            }
            val stream = AccountManager.find(owner)?.stream
            if (stream != null) {
                scope.launch {
                    delay(1000L) // Exponential backoff
                    sync(stream, version, after = lastStamp)
                    Log.d("ClientSyncManager", "Triggered next sync for owner $owner with version $version, after $lastStamp")
                }
            } else {
                Log.w("ClientSyncManager", "No stream available for pagination sync for owner $owner")
            }
        }
    }

    private suspend fun readPush(query: Element) = withContext(Dispatchers.IO) {
        Log.d("ClientSyncManager", "Processing push query")
        val stamp = query.getAttribute("stamp")?.toLongOrNull()?.toString() ?: "0"
        readConversationMetadata(query, owner)
        version = stamp
        SettingManager.saveClientSynchronizationVersion(owner, version)
        Log.d("ClientSyncManager", "Updated version to $stamp for push")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun read(iq: String) = withContext(Dispatchers.IO) {
        if (owner.isBlank()) {
            Log.e("ClientSyncManager", "Cannot read IQ: invalid owner")
            return@withContext
        }
        try {
            Log.d("ClientSyncManager", "Processing sync IQ stanza: ${iq.substring(0, minOf(iq.length, 200))}...")
            val cleanedIq = iq.trim()
            if (!cleanedIq.startsWith("<iq") || !cleanedIq.endsWith("</iq>")) {
                Log.e("ClientSyncManager", "Invalid IQ stanza: does not start with <iq> or end with </iq>")
                return@withContext
            }
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(cleanedIq.byteInputStream())
            val iqElement = document.documentElement
            val queryElement = iqElement.getElementsByTagNameNS("https://xabber.com/protocol/synchronization", "query").item(0) as? Element
            if (queryElement == null) {
                Log.w("ClientSyncManager", "Ignoring IQ stanza without synchronization query")
                return@withContext
            }
            if (iqElement.getAttribute("type") == "result") {
                readSnapshot(queryElement)
            } else if (iqElement.getAttribute("type") == "set") {
                readPush(queryElement)
            } else {
                Log.w("ClientSyncManager", "Ignoring sync IQ with type: ${iqElement.getAttribute("type")}")
            }
        } catch (e: Exception) {
            Log.e("ClientSyncManager", "Failed to parse IQ stanza: ${e.message}", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun receiveClientSyncRaw(message: String) {
        if (owner.isBlank()) {
            Log.e("ClientSyncManager", "Cannot process message: invalid owner")
            return
        }
        try {
            Log.d("ClientSyncManager", "Processing message stanza: ${message.substring(0, minOf(message.length, 200))}...")
            val cleanedMessage = message.trim()
            if (!cleanedMessage.startsWith("<message") || !cleanedMessage.endsWith("</message>")) {
                Log.e("ClientSyncManager", "Invalid message stanza: does not start with <message> or end with </message>")
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
                                Log.d("ClientSyncManager", "Message attributes: id=$messageId, from=$from, to=$to, type=$type")
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
                                Log.d("ClientSyncManager", "Inner message attributes: id=$messageId, from=$from, to=$to, type=$type")
                            }
                            tagName == "body" && inInnerMessage -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    body = parser.text.trim()
                                    Log.d("ClientSyncManager", "Body: $body")
                                }
                            }
                            tagName == "time" && inInnerMessage && parser.getAttributeValue(null, "xmlns") == "https://xabber.com/protocol/delivery" -> {
                                val stamp = parser.getAttributeValue(null, "stamp")
                                if (stamp != null) {
                                    try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                                        timestamp = sdf.parse(stamp)?.time ?: System.currentTimeMillis()
                                        Log.d("ClientSyncManager", "Parsed timestamp: $timestamp")
                                    } catch (e: Exception) {
                                        Log.e("ClientSyncManager", "Failed to parse message timestamp: ${e.message}")
                                        timestamp = System.currentTimeMillis()
                                    }
                                }
                            }
                            tagName == "x" && parser.getAttributeValue(null, "xmlns") == "https://xabber.com/protocol/groups" -> {
                                conversationType = "https://xabber.com/protocol/groups"
                                Log.d("ClientSyncManager", "Detected group chat: conversationType=$conversationType")
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
                Log.e("ClientSyncManager", "Invalid message: missing id=$messageId, from=$from, to=$to, or body=$body")
                return
            }

            // Determine conversation type based on JID or type
            if (to == "favorites.redsolution.com") {
                conversationType = "urn:xabber:favorites:0"
            }

            val conversationTypeEnum = ConversationType.values().firstOrNull { it.rawValue == conversationType } ?: ConversationType.Regular
            val chatJid = if (from == owner) to else from // Use destination for outgoing, sender for incoming
            val messagePrimary = MessageStorageItem.genPrimary(messageId, owner)
            if (messagePrimary.isEmpty()) {
                Log.w("ClientSyncManager", "Skipping message with invalid primary key for messageId=$messageId, owner=$owner")
                return
            }
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
                    this.date = timestamp
                    this.sentDate = timestamp
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
                        this.messageDate = timestamp
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
                        this.messageDate = timestamp
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
            Log.e("ClientSyncManager", "Cannot check last chats: invalid owner")
            return
        }
        realm.write {
            // Query chats within the transaction to ensure fresh data
            val chats = query<LastChatsStorageItem>("owner = $0", owner).find()
            Log.d("ClientSyncManager", "Checking LastChatsStorageItem count for owner $owner: ${chats.size}")
            chats.forEach { chat ->
                // Use a snapshot of properties to avoid accessing invalidated objects
                val jid = chat.jid
                val conversationType = chat.conversationType_
                val chatPrimary = LastChatsStorageItem.genPrimary(jid, owner, ConversationType.fromRaw(conversationType))
                if (chatPrimary.isEmpty()) {
                    Log.w("ClientSyncManager", "Found invalid primary key for chat jid=$jid, type=$conversationType, deleting")
                    findLatest(chat)?.let { delete(it) }
                    Log.d("ClientSyncManager", "Deleted LastChatsStorageItem with invalid primary for jid=$jid, type=$conversationType")
                    return@forEach
                }
                Log.d("ClientSyncManager", "Chat: jid=$jid, type=$conversationType, primary=$chatPrimary, isArchived=${chat.isArchived}, unread=${chat.unread}, messageDate=${chat.messageDate}, lastMessageId=${chat.lastMessageId}")
            }
        }
    }

    fun reset() {
        scope.coroutineContext[Job]?.cancel()
        realm.close()
        Log.d("ClientSyncManager", "Reset and closed Realm for owner $owner")
    }
}