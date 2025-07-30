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
import kotlinx.coroutines.launch
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

    init {
        if (version.isEmpty()) {
            SettingManager.saveClientSynchronizationVersion(owner, "0")
            version = "0"
        }
        Log.d("ClientSyncManager", "Initialized for owner: $owner, version: $version")
        if (owner.isBlank()) {
            Log.e("ClientSyncManager", "Invalid owner: empty or null")
        }
        CoroutineScope(Dispatchers.IO).launch {
            checkLastChats()
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun sync(stream: Stream, customVer: String? = null, after: String? = null): Boolean {
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
            append("<max>60</max>")
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
        return success
    }

    fun getChat(jid: String, type: ConversationType): LastChatsStorageItem? {
        if (owner.isBlank()) {
            Log.e("ClientSyncManager", "Cannot get chat: invalid owner")
            return null
        }
        var chat: LastChatsStorageItem? = null
        realm.writeBlocking {
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

    private suspend fun readConversationMetadata(query: Element, owner: String) {
        val conversations = query.getElementsByTagName("conversation")
        val stamp = query.getAttribute("stamp")?.toLongOrNull() ?: 0L
        Log.d("ClientSyncManager", "Processing ${conversations.length} conversations with stamp $stamp for owner $owner")

        // Define excluded patterns for roster (contacts), but allow in chats
        val excludedJidsForRoster = setOf(
            "favorites.redsolution.com",
            "redmine@redsolution.com",
            "xabber@xmppdev01.xabber.com"
        )
        val excludedTypesForRoster = setOf(
            "urn:xabber:xen:0",  // Notifications
            "urn:xabber:favorites:0",
            "https://xabber.com/protocol/groups"  // Groups
        )

        realm.write {
            val existingChats = query<LastChatsStorageItem>("owner = $0", owner).find()
            Log.d("ClientSyncManager", "Initial LastChatsStorageItem count for owner $owner: ${existingChats.size}")

            for (i in 0 until conversations.length) {
                val conversation = conversations.item(i) as Element
                val jid = conversation.getAttribute("jid") ?: continue
                val type = conversation.getAttribute("type") ?: continue

                // Skip self JID
                if (jid == owner) {
                    Log.d("ClientSyncManager", "Skipping self conversation with jid=$jid")
                    continue
                }

                // Existing skips (keep for both chats and roster)
                if (type == "urn:xabber:xen:0") {
                    Log.d("ClientSyncManager", "Skipping notification conversation with jid=$jid, type=$type")
                    continue
                }
                if (jid == owner.substringAfter("@")) {
                    Log.d("ClientSyncManager", "Skipping server JID conversation for jid=$jid")
                    continue
                }

                val status = conversation.getAttribute("status") ?: "active"
                val pinned = conversation.getAttribute("pinned")?.toLongOrNull() ?: 0L
                val conversationStamp = conversation.getAttribute("stamp")?.toLongOrNull() ?: 0L

                val conversationType = ConversationType.values().firstOrNull { it.rawValue == type } ?: run {
                    Log.w("ClientSyncManager", "Unknown conversation type $type for jid $jid, treating as urn:xabber:chat")
                    ConversationType.Regular
                }

                val metadataList = conversation.getElementsByTagName("metadata")
                var unreadCount = 0L
                var lastMessage: MessageStorageItem? = null
                var messageDate = 0L
                var lastMessageId = ""

                for (j in 0 until metadataList.length) {
                    val metadata = metadataList.item(j) as Element
                    if (metadata.getAttribute("node") == "https://xabber.com/protocol/synchronization") {
                        val unread = metadata.getElementsByTagName("unread").item(0) as? Element
                        unreadCount = unread?.getAttribute("count")?.toLongOrNull() ?: 0L
                        val lastMessageElement = metadata.getElementsByTagName("last-message").item(0) as? Element
                        lastMessageElement?.let { messageElement ->
                            val message = messageElement.getElementsByTagName("message").item(0) as? Element
                            message?.let {
                                val messageId = it.getAttribute("id") ?: ""
                                val from = it.getAttribute("from") ?: jid
                                val to = it.getAttribute("to") ?: owner
                                val body = it.getElementsByTagName("body").item(0)?.textContent ?: ""
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
                                val existingMessage = query<MessageStorageItem>("primary = $0", messagePrimary).first().find()
                                lastMessage = if (existingMessage == null) {
                                    copyToRealm(MessageStorageItem().apply {
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
                                    }, UpdatePolicy.ALL)
                                } else {
                                    existingMessage
                                }
                                messageDate = timestamp
                                lastMessageId = messageId
                                Log.d("ClientSyncManager", "Saved or used existing message $messageId for jid $jid")
                            }
                        }
                    }
                }

                // Create rosterItem only if not excluded
                var rosterItem: RosterStorageItem? = null
                val isExcludedForRoster = excludedJidsForRoster.contains(jid) || excludedTypesForRoster.contains(type) || jid == owner
                if (!isExcludedForRoster) {
                    rosterItem = query<RosterStorageItem>("jid = $0 AND owner = $1", jid, owner).first().find()
                        ?: copyToRealm(RosterStorageItem().apply {
                            primary = RosterStorageItem.genPrimary(jid, owner)
                            this.jid = jid
                            this.owner = owner
                            this.customNickname = jid
                        }, UpdatePolicy.ALL)
                    Log.d("ClientSyncManager", "Created/Used RosterStorageItem for jid $jid")
                } else {
                    Log.d("ClientSyncManager", "Skipping RosterStorageItem creation for excluded jid=$jid, type=$type")
                }

                val existingChat = query<LastChatsStorageItem>("jid = $0 AND owner = $1 AND conversationType_ = $2", jid, owner, type).first().find()
                if (existingChat == null) {
                    copyToRealm(LastChatsStorageItem().apply {
                        primary = LastChatsStorageItem.genPrimary(jid, owner, conversationType)
                        this.jid = jid
                        this.owner = owner
                        this.conversationType_ = type
                        this.isArchived = status == "archived"
                        this.unread = unreadCount.toInt()
                        this.messageDate = messageDate
                        this.lastMessageId = lastMessageId
                        this.pinnedPosition = pinned
                        this.muteExpired = -1
                        this.rosterItem = rosterItem  // Null for excluded
                        this.lastMessage = lastMessage
                    }, UpdatePolicy.ALL)
                    Log.d("ClientSyncManager", "Created new LastChatsStorageItem for jid $jid, type $type, owner $owner")
                } else {
                    findLatest(existingChat)?.apply {
                        this.isArchived = status == "archived"
                        this.unread = unreadCount.toInt()
                        this.messageDate = messageDate
                        this.lastMessageId = lastMessageId
                        this.pinnedPosition = pinned
                        this.rosterItem = rosterItem  // Update to null if excluded, but since existing might have one, decide if to nullify
                        this.lastMessage = lastMessage
                    }
                    Log.d("ClientSyncManager", "Updated existing LastChatsStorageItem for jid $jid, type $type, owner $owner")
                }
            }

            val updatedChats = query<LastChatsStorageItem>("owner = $0", owner).find()
            Log.d("ClientSyncManager", "LastChatsStorageItem count after readConversationMetadata for owner $owner: ${updatedChats.size}")
            // Removed redundant per-chat logs to reduce repetition; enable if needed for debugging
        }
    }

    private suspend fun readSnapshot(query: Element) {
        Log.d("ClientSyncManager", "Processing snapshot query")
        val stamp = query.getAttribute("stamp")?.toLongOrNull()?.toString() ?: "0"
        readConversationMetadata(query, owner)
        val countElement = query.getElementsByTagNameNS("http://jabber.org/protocol/rsm", "count").item(0) as? Element
        val count = countElement?.textContent?.toIntOrNull() ?: 0
        if (count < 60) {
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
                CoroutineScope(Dispatchers.IO).launch {
                    sync(stream, version, after = lastStamp)
                    Log.d("ClientSyncManager", "Triggered next sync for owner $owner with version $version, after $lastStamp")
                }
            } else {
                Log.w("ClientSyncManager", "No stream available for pagination sync for $owner")
            }
        }
    }

    private suspend fun readPush(query: Element) {
        Log.d("ClientSyncManager", "Processing push query")
        val stamp = query.getAttribute("stamp")?.toLongOrNull()?.toString() ?: "0"
        readConversationMetadata(query, owner)
        version = stamp
        SettingManager.saveClientSynchronizationVersion(owner, version)
        Log.d("ClientSyncManager", "Updated version to $stamp for push")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun read(iq: String) {
        if (owner.isBlank()) {
            Log.e("ClientSyncManager", "Cannot read IQ: invalid owner")
            return
        }
        try {
            Log.d("ClientSyncManager", "Processing sync IQ stanza: ${iq.substring(0, minOf(iq.length, 200))}...")
            val cleanedIq = iq.trim()
            if (!cleanedIq.startsWith("<iq") || !cleanedIq.endsWith("</iq>")) {
                Log.e("ClientSyncManager", "Invalid IQ stanza: does not start with <iq> or end with </iq>")
                return
            }
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(cleanedIq.byteInputStream())
            val iqElement = document.documentElement
            val queryElement = iqElement.getElementsByTagNameNS("https://xabber.com/protocol/synchronization", "query").item(0) as? Element
            if (queryElement == null) {
                Log.w("ClientSyncManager", "Ignoring IQ stanza without synchronization query")
                return
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
                                messageId = parser.getAttributeValue(null, "id") ?: ""
                                from = parser.getAttributeValue(null, "from") ?: ""
                                to = parser.getAttributeValue(null, "to") ?: owner
                                type = parser.getAttributeValue(null, "type") ?: "chat"
                                Log.d("ClientSyncManager", "Message attributes: id=$messageId, from=$from, to=$to, type=$type")
                            }
                            tagName == "forwarded" && parser.getAttributeValue(null, "xmlns") == "urn:xmpp:forward:0" -> {
                                inForwarded = true
                            }
                            tagName == "message" && inForwarded -> {
                                inInnerMessage = true
                                messageId = parser.getAttributeValue(null, "id") ?: messageId
                                from = parser.getAttributeValue(null, "from")?.split("/")?.get(0) ?: from
                                to = parser.getAttributeValue(null, "to") ?: to
                                type = parser.getAttributeValue(null, "type") ?: type
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

            if (messageId.isNullOrEmpty() || from.isNullOrEmpty() || to.isNullOrEmpty()) {
                Log.e("ClientSyncManager", "Invalid message: missing id, from, or to attribute")
                return
            }

            // Determine conversation type based on JID or type
            if (to == "favorites.redsolution.com") {
                conversationType = "urn:xabber:favorites:0"
            }

            realm.write {
                val rosterItem = query<RosterStorageItem>("jid = $0 AND owner = $1", from, owner).first().find()
                    ?: copyToRealm(RosterStorageItem().apply {
                        primary = RosterStorageItem.genPrimary(from, owner)
                        this.jid = from
                        this.owner = owner
                        this.customNickname = from
                    }, UpdatePolicy.ALL)
                Log.d("ClientSyncManager", "Created/Used RosterStorageItem for jid=$from")

                val chat = query<LastChatsStorageItem>("jid = $0 AND owner = $1 AND conversationType_ = $2", to, owner, conversationType).first().find()
                val message = copyToRealm(MessageStorageItem().apply {
                    primary = MessageStorageItem.genPrimary(messageId, owner)
                    this.messageId = messageId
                    this.owner = owner // Set owner explicitly
                    this.opponent = to // Use 'to' for carbon messages (destination chat)
                    this.body = body ?: "" // Ensure body is set
                    this.date = timestamp
                    this.sentDate = timestamp
                    this.editDate = 0L
                    this.outgoing = from == owner // Set outgoing based on from
                    this.conversationType_ = conversationType
                    this.isRead = from == owner // Outgoing messages are read
                    this.state = if (from == owner) MessageSendingState.Deliver else MessageSendingState.Sent
                }, UpdatePolicy.ALL)
                Log.d("ClientSyncManager", "Saved message $messageId for jid=$to in receiveClientSyncRaw")

                if (chat == null) {
                    copyToRealm(LastChatsStorageItem().apply {
                        primary = LastChatsStorageItem.genPrimary(to, owner, ConversationType.fromRaw(conversationType))
                        this.jid = to
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
                    Log.d("ClientSyncManager", "Created new LastChatsStorageItem for jid=$to in receiveClientSyncRaw")
                } else {
                    findLatest(chat)?.apply {
                        this.unread = if (from == owner) this.unread else this.unread + 1
                        this.messageDate = timestamp
                        this.lastMessageId = messageId
                        this.lastMessage = message
                        this.isArchived = false
                    }
                    Log.d("ClientSyncManager", "Updated LastChatsStorageItem for jid=$to in receiveClientSyncRaw")
                }
                // Log MessageStorageItem entries
                query<MessageStorageItem>("messageId = $0", messageId).find().forEach { item ->
                    Log.d(
                        "ClientSyncManager",
                        "MessageStorageItem: primary=${item.primary}, messageId=${item.messageId}, owner=${item.owner}, " +
                                "opponent=${item.opponent}, body=${item.body}, date=${item.date}, sentDate=${item.sentDate}, " +
                                "editDate=${item.editDate}, outgoing=${item.outgoing}, conversationType_=${item.conversationType_}, " +
                                "isRead=${item.isRead}, state=${item.state}"
                    )
                }
                // Log LastChatsStorageItem
                query<LastChatsStorageItem>("jid = $0 AND owner = $1", to, owner).find().forEach { item ->
                    Log.d(
                        "ClientSyncManager",
                        "LastChatsStorageItem: primary=${item.primary}, jid=${item.jid}, owner=${item.owner}, " +
                                "conversationType_=${item.conversationType_}, isArchived=${item.isArchived}, unread=${item.unread}, " +
                                "messageDate=${item.messageDate}, lastMessageId=${item.lastMessageId}"
                    )
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
            val chats = query<LastChatsStorageItem>("owner = $0", owner).find()
            Log.d("ClientSyncManager", "LastChatsStorageItem count for owner $owner: ${chats.size}")
            chats.forEach { chat ->
                Log.d("ClientSyncManager", "Chat: jid=${chat.jid}, type=${chat.conversationType_}, isArchived=${chat.isArchived}, unread=${chat.unread}, messageDate=${chat.messageDate}, lastMessageId=${chat.lastMessageId}")
            }
        }
    }

    fun reset() {
        realm.close()
        Log.d("ClientSyncManager", "Reset and closed Realm for owner $owner")
    }
}