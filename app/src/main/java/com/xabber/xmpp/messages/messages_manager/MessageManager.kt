package com.xabber.xmpp.messages.messages_manager

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.account.AccountManager
import com.xabber.stream.Stream
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageForwardsInlineStorageItem
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.xmpp.groupchat.GroupChatStorageItem
import com.xabber.xmpp.messages.XMLElement
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.message.MessageStanzaStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.O)
class MessageManager(private val owner: String, activeStream: Boolean) {
    private val realm: Realm by lazy { Realm.open(defaultRealmConfig()) }
    private val queue: String = "com.xabber.messages.transmitter.$owner.${UUID.randomUUID()}"
    private var updateSendingMessagesTimer: Timer? = null
    private var receiverJob: Job? = null

    data class ScheduledMessage(val body: String, val to: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ScheduledMessage) return false
            return body == other.body && to == other.to
        }

        override fun hashCode(): Int {
            var result = body.hashCode()
            result = 31 * result + to.hashCode()
            return result
        }
    }

    init {
        if (activeStream) {
            CoroutineScope(Dispatchers.IO).launch {
                try {

                    realm.write {
                        val states = listOf(
                            MessageSendingState.Sending,
                            MessageSendingState.Uploading
                        )
                        val collection = query<MessageStorageItem>(
                            "owner = $0 AND state_ IN $1", owner, states
                        ).find()
                        collection.forEach { message ->
                            message.state = MessageSendingState.Error
                            message.messageError = "Internal error"
                            message.references.forEach { it.hasError = true }
                            query<LastChatsStorageItem>(
                                "primary = $0",
                                LastChatsStorageItem.genPrimary(message.opponent, owner, message.conversationType)
                            ).first().find()?.hasErrorInChat = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MessageManager", "Error initializing with active stream: ${e.message}")
                }
            }

        }
        subscribe(activeStream)
    }

    fun subscribe(activeStream: Boolean) {
        updateSendingMessagesTimer?.cancel()
        updateSendingMessagesTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            realm.write {
                                val sendingMessages = query<MessageStorageItem>(
                                    "owner = $0 AND state_ IN $1",
                                    owner,
                                    listOf(
                                        MessageSendingState.Sending,
                                        MessageSendingState.Uploading
                                    )
                                ).find()
                                val toEdit = mutableSetOf<String>()
                                val toResend = mutableSetOf<String>()
                                sendingMessages.forEach { message ->
                                    if (message.displayAs == "text") {
                                        val timeSince = (System.currentTimeMillis() - message.date) / 1000
                                        if (timeSince > 10) {
                                            toResend.add(message.primary)
                                        } else if (timeSince > 60) {
                                            toEdit.add(message.primary)
                                        }
                                    } else {
                                        val totalAttachesSize = message.references.sumOf { it.fileSize }
                                        val totalSeconds = 60 + (totalAttachesSize / 1024 / 32)
                                        if ((System.currentTimeMillis() - message.date) / 1000 > totalSeconds) {
                                            toEdit.add(message.primary)
                                        }
                                    }
                                }
//                                if (toResend.isNotEmpty()) {
//                                    AccountManager.find(owner)?.action { user, stream ->
//                                        toResend.forEach { primary ->
//                                            user.messages.retrySending(primary)
//                                        }
//                                    }
//                                }
                                if (toEdit.isNotEmpty()) {
                                    val collection = query<MessageStorageItem>("primary IN $0", toEdit.toList()).find()
                                    collection.forEach { message ->
                                        message.state = MessageSendingState.Error
                                        message.messageError = "Stream was disconnected"
                                        query<LastChatsStorageItem>(
                                            "primary = $0",
                                            LastChatsStorageItem.genPrimary(message.opponent, owner, message.conversationType)
                                        ).first().find()?.hasErrorInChat = true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MessageManager", "Error in updateSendingMessagesTimer: ${e.message}")
                        }
                    }
                }
            }, 0, 5000)
        }
    }

    fun unsubscribe() {
        updateSendingMessagesTimer?.cancel()
        updateSendingMessagesTimer = null
        receiverJob?.cancel()
        receiverJob = null
    }

    suspend fun readAllMessages() {
        try {
            realm.write {
                runBlocking {
                query<LastChatsStorageItem>("isArchived = false AND owner = $0", owner).find()
                    .forEach { readLastMessage(it.jid, it.conversationType) }
            }
                }
        } catch (e: Exception) {
            Log.e("MessageManager", "Error reading all messages: ${e.message}")
        }
    }

    suspend fun readLastMessage(jid: String, conversationType: ConversationType) {
        try {
            realm.write {
                val primary = LastChatsStorageItem.genPrimary(jid, owner, conversationType)
                val chat = query<LastChatsStorageItem>("primary = $0", primary).first().find()
                if (chat?.lastMessage?.primary != null) {
                    runBlocking {
                        readMessage(chat.lastMessage!!.primary, true)
                    }

                }
                if (chat != null) {
                    chat.unread = 0
                    chat.lastReadId = null
                    val messageId = chat.lastMessageId
                    AccountManager.find(owner)?.unsafeAction { user, stream ->
                        runBlocking {
                            user.chatMarkers.displayedById(stream, jid, messageId)
                        }

                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MessageManager", "Error reading last message for jid $jid: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun readMessage(primary: String, last: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            realm.write {
                val message = query<MessageStorageItem>("primary = $0", primary).first().find() ?: return@write
                if (message.outgoing) return@write

                val conversationType = message.conversationType_
                val opponent = message.opponent
                val lastChat = query<LastChatsStorageItem>(
                    "jid = $0 AND owner = $1 AND conversationType_ = $2",
                    opponent, owner, conversationType
                ).first().find()

                if (lastChat != null && lastChat.unread > 0) {
                    findLatest(lastChat)?.let {
                        if (last) {
                            it.unread = 0
                            it.lastReadId = null
                        } else {
                            it.unread -= 1
                        }
                    }
                }

                val messagesToMark = query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND conversationType_ = $2 AND date <= $3 AND state_ <= ${MessageSendingState.Error}",
                    owner, opponent, conversationType, message.date
                ).find()

                messagesToMark.forEach { msg ->
                    val latestMsg = findLatest(msg) ?: return@forEach
                    latestMsg.isRead = true
                    latestMsg.state = MessageSendingState.Read
                    if (latestMsg.readDate!! <= 1) {
                        latestMsg.readDate = System.currentTimeMillis() / 1000.0
                    }
                    if (latestMsg.afterburnInterval > 0 && latestMsg.burnDate <= 1) {
                        latestMsg.burnDate = System.currentTimeMillis() / 1000.0 + latestMsg.afterburnInterval
                    }
                }

                findLatest(message)?.let {
                    it.isRead = true
                    it.state = MessageSendingState.Read
                }
            }

            AccountManager.find(owner)?.action { user, stream ->
                user.chatMarkers.displayed(stream, primary)
            }
        } catch (e: Exception) {
            Log.e("MessageManager", "Error reading message $primary: ${e.message}")
        }
    }

    suspend fun fail(message: XMPPMessage) {
        val elementId = message.id ?: return
        val opponent = message.to?.bare()?.takeIf { it != owner } ?: return

        try {
            realm.write {
                val instance = query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND messageId = $2",
                    owner, opponent, elementId
                ).first().find() ?: return@write

                findLatest(instance)?.let {
                    it.messageError = "Connection error"
                    it.state = MessageSendingState.Error
                    it.references.forEach { ref ->
                        ref.hasError = true
                    }
                    query<LastChatsStorageItem>(
                        "primary = $0",
                        LastChatsStorageItem.genPrimary(it.opponent, it.owner, it.conversationType)
                    ).first().find()?.hasErrorInChat = true
                }
            }
        } catch (e: Exception) {
            Log.e("MessageManager", "Error failing message $elementId: ${e.message}")
        }
    }

    suspend fun readError(message: XMPPMessage): Boolean {
        val error = message.element("error") ?: return false
        val errorMessageRaw = error.children.firstOrNull()?.name ?: return false
        val elementId = message.id ?: return false
        val opponent = message.to?.bare()?.takeIf { it != owner } ?: return false

        val errorMessage = when (errorMessageRaw) {
            "gone" -> "Contact unavailable"
            "forbidden" -> "Forbidden"
            "feature-not-implemented" -> "Feature not implemented"
            "conflict" -> "Conflict"
            "bad-request" -> "Bad request"
            "internal-server-error" -> "Internal server error"
            "item-not-found" -> "Item not found"
            "jid-malformed" -> "JID malformed"
            "not-acceptable" -> "Not acceptable"
            "not-allowed" -> "Not allowed"
            "not-authorized" -> "Not authorized"
            "policy-violation" -> "Policy violation"
            "recipient-unavailable" -> "Recipient unavailable"
            "redirect" -> "Redirect"
            "remote-server-not-found" -> "Remote server not found"
            "remote-server-timeout" -> "Remote server timeout"
            "subscription-required" -> "Subscription required"
            "undefined-condition" -> "Internal error"
            else -> "Internal error"
        }

        try {
            realm.write {
                val instance = query<MessageStorageItem>(
                    "owner = $0 AND opponent = $1 AND messageId = $2",
                    owner, opponent, elementId
                ).first().find() ?: return@write

                findLatest(instance)?.let {
                    it.messageError = errorMessage
                    it.state = MessageSendingState.Error
                    it.references.forEach { ref ->
                        ref.hasError = true
                    }
                    query<LastChatsStorageItem>(
                        "primary = $0",
                        LastChatsStorageItem.genPrimary(it.opponent, it.owner, it.conversationType)
                    ).first().find()?.hasErrorInChat = true
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("MessageManager", "Error processing error message $elementId: ${e.message}")
            return false
        }
    }

    companion object {
        suspend fun remove(owner: String, commitTransaction: Boolean = true) {
            try {
                val realm = Realm.open(defaultRealmConfig())
                realm.write {
                    val messages = query<MessageStorageItem>("owner = $0", owner).find()
                    val stanzas = query<MessageStanzaStorageItem>("owner = $0", owner).find()
                    val inlines = query<MessageForwardsInlineStorageItem>("owner = $0", owner).find()
                    val refs = query<MessageReferenceStorageItem>("owner = $0", owner).find()
                    // CallMetadataStorageItem not included as it wasn't provided
                    delete(messages)
                    delete(inlines)
                    delete(refs)
                    delete(stanzas)
                }
                // Remove from SharedPreferences equivalent if needed
            } catch (e: Exception) {
                Log.e("MessageManager", "Cannot remove messages for account $owner: ${e.message}")
            }
        }

        fun getMessageAuthorGroupchatStatic(references: List<XMLElement>, jid: String, owner: String): String? {
            val groupchatRef = references.firstOrNull {
                it.element("user", namespace = "https://xabber.com/protocol/groups") != null
            }
            val user = groupchatRef?.element("user", namespace = "https://xabber.com/protocol/groups")
            return user?.element("jid")?.getAttribute("stringValue") ?: run {
                user?.getAttribute("id")?.let { id ->
                    val realm = Realm.open(defaultRealmConfig())
                    realm.query<GroupChatStorageItem>(
                        "primary = $0",
                        GroupChatStorageItem.genPrimary(jid, owner)
                    ).first().find()?.jid
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun sendDisplayedChatMarker(stream: Stream, opponent: String, messageId: String) {
        val id = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
        val stanza = """
            <message id='$id' to='$opponent'>
                <displayed xmlns='urn:xmpp:chat-markers:0' id='$messageId'/>
            </message>
        """.trimIndent()
        withContext(Dispatchers.IO) {
            if (stream.socket?.write(stanza) == true) {
                Log.d("MessageManager", "Sent displayed chat marker for message $messageId to $opponent")
            } else {
                Log.e("MessageManager", "Failed to send displayed chat marker for message $messageId")
            }
        }
    }
}