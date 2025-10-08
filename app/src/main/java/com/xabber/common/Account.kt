package com.xabber.common

import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.utils.custom.NickGenerator
import com.xabber.xmpp.XEP_0CCC.ClientSynchronizationManager
import com.xabber.xmpp.auth.DevicesOCRA
import com.xabber.xmpp.device.DeviceStorageItem
import com.xabber.xmpp.presence.PresenceManager
import com.xabber.xmpp.roster.RosterManager
import com.xabber.xmpp.messages.messages_manager.MessageManager
import com.xabber.xmpp.messages.messages_manager.ChatMarkersManager
import com.xabber.xmpp.messages.messages_manager.MessageCommonReceiver
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.MessageDto
import com.xabber.utils.parseTimestamp
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.message.TemporaryMessageStanzaStorageItem
import com.xabber.xmpp.messages.message_archive.MessageArchiveManager
import io.ktor.network.sockets.isClosed
import io.reactivex.subjects.BehaviorSubject
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.cancel
import nl.adaptivity.xmlutil.core.impl.multiplatform.StringReader
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringWriter
import java.util.Date
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@RequiresApi(Build.VERSION_CODES.O)
class Account : XMPPStreamDelegate {
    constructor()

    constructor(jid: String) {
        this.jid = jid
    }

    companion object {
        private const val TAG = "Account"
    }

    private val presenceStanzas = mutableListOf<XMPPPresence>()
    var jid: String = ""
    var host: String = ""
    var port: Int = 5222
    var username: String = ""
    var supportTokens: Boolean = false
    var tokenUid: String = ""
    var savePassword: Boolean = true
    var useSecureConnection: Boolean = false
    var manuallySetHost: Boolean = false
    var resource: String = ""
    var priority: Int = 0
    var deviceName: String = ""
    var statusMessage: BehaviorSubject<String> = BehaviorSubject.createDefault("Offline")
    var stream: Stream? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private val realm: Realm by lazy { Realm.open(defaultRealmConfig()) }
    private val rosterManager: RosterManager by lazy { RosterManager(jid, realm) }
    private val syncManager: ClientSynchronizationManager by lazy { ClientSynchronizationManager(jid) }
    private val messageArchiveManager: MessageArchiveManager by lazy { MessageArchiveManager(jid) }
    private var presenceManager: PresenceManager? = null
    private val deviceModel = Build.MODEL
    private var isDeviceRegistered = false
    private var ocraAuth: DevicesOCRA? = null
    private var attemptedPreTlsAuth = false
    private var boundJid: String? = null
    private val rosterStanzaBuffer = StringBuilder()
    private val syncStanzaBuffer = StringBuilder()
    private val syncCompletionChannel = Channel<Unit>(1)
    private var supportedFeatures: String = ""
    private var rosterRequested = false
    val chatMarkers: ChatMarkersManager by lazy { ChatMarkersManager(jid) }
    val messages: MessageManager by lazy { MessageManager(jid, activeStream = stream != null) }
    val messageReceiver: MessageCommonReceiver by lazy { MessageCommonReceiver(jid) }

    // New: Buffer for post-registration stanzas (roster, sync, presence)
    private val stanzaBuffer = MutableSharedFlow<StanzaItem>(replay = 0, extraBufferCapacity = 1000)
    private val stanzaProcessingScope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())

    // New: Data class to hold stanza type and content
    private data class StanzaItem(val type: StanzaType, val content: String, val stream: Stream) {
        enum class StanzaType {
            ROSTER, SYNC, PRESENCE, OTHER
        }
    }

    init {
        if (deviceName.isEmpty()) {
            deviceName = NickGenerator.genRandomNick()
        }
        // New: Start processing buffered stanzas asynchronously
        stanzaProcessingScope.launch {
            stanzaBuffer.collect { item ->
                when (item.type) {
                    StanzaItem.StanzaType.ROSTER -> processRosterStanza(item.content as String, item.stream)
                    StanzaItem.StanzaType.SYNC -> processSyncStanza(item.content as String, item.stream)
                    StanzaItem.StanzaType.PRESENCE -> processPresenceStanza(item.content as XMPPPresence, item.stream)
                    StanzaItem.StanzaType.OTHER -> Log.d(TAG, "Skipping non-buffered stanza type: ${item.content.toString().take(200)}")
                }
            }
        }
    }

    fun setOnErrorCallback(callback: (String) -> Unit) {
        onErrorCallback = callback
        stream?.setOnErrorCallback { error ->
            callback(error)
            statusMessage.onNext("Offline")
        }
    }

    suspend fun loadAccount() = withContext(Dispatchers.IO) {
        try {
            val realm = Realm.open(defaultRealmConfig())
            val item = realm.query<AccountStorageItem>("primary == $0", jid).first().find()
            item?.let {
                this@Account.jid = it.jid
                this@Account.host = it.host
                this@Account.port = it.port
                it.resource?.resource?.let { res -> this@Account.resource = res }
                this@Account.username = it.username
                if (this@Account.host.isBlank()) {
                    this@Account.host = extractHostFromJid(this@Account.jid)
                }
            }
            realm.close()
            checkExistingDevice()
            initializeStream()
        } catch (e: Exception) {
            Log.e(TAG, "Can't load user $jid from db", e)
            onErrorCallback?.invoke("Error loading account: ${e.message}")
        }
    }

    suspend fun create() {
        try {
            if (!manuallySetHost) {
                host = extractHostFromJid(jid)
            }
            val realm = Realm.open(defaultRealmConfig())
            realm.writeBlocking {
                val item = AccountStorageItem().apply {
                    order = query<AccountStorageItem>().find().size
                    jid = this@Account.jid
                    host = this@Account.host
                    savePassword = this@Account.savePassword
                    manuallySetHost = this@Account.manuallySetHost
                    port = this@Account.port
                    username = this@Account.username
                    createdAt = System.currentTimeMillis()
                    deviceName = this@Account.deviceName
                }
                copyToRealm(item, updatePolicy = UpdatePolicy.ALL)
            }
            initializeStream()
        } catch (e: Exception) {
            Log.d(TAG, "Can't update push info for user ${this.jid}", e)
        }
    }

    fun isExist(jid: String): Boolean {
        try {
            val realm = Realm.open(defaultRealmConfig())
            val exists = realm.query<AccountStorageItem>("jid = $0", jid).first().find() != null
            realm.close()
            return exists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking account existence for $jid: ${e.message}", e)
            return false
        }
    }

    private suspend fun checkExistingDevice() {
        try {
            val devices = realm.query<DeviceStorageItem>("owner = $0", jid).find()
            Log.d(TAG, "Found ${devices.size} devices for JID: $jid")
            devices.forEach { device ->
                Log.d(TAG, "Device: uid=${device.uid}, expire=${device.expire}, authCounter=${device.authCounter}, secret=${device.secret.substring(0, 8)}..., validationKey=${device.validationKey.substring(0, 8)}...")
            }
            val validDevice = devices.firstOrNull { it.expire > System.currentTimeMillis().toDouble() / 1000 }
            isDeviceRegistered = validDevice != null
            if (isDeviceRegistered) {
                Log.d(TAG, "Valid device found for JID: $jid, uid: ${validDevice?.uid}")
            } else if (devices.isNotEmpty()) {
                Log.w(TAG, "Devices found but all expired/invalid for JID: $jid")
            } else {
                Log.d(TAG, "No devices found for JID: $jid")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking existing devices for JID: $jid: ${e.message}", e)
            isDeviceRegistered = false
        }
    }

    private suspend fun initializeStream() = withContext(Dispatchers.IO) {
        try {
            if (jid.isNotEmpty()) {
                stream?.close()
                stream = Stream(jid, port).apply {
                    onErrorCallback?.let { setOnErrorCallback(it) }
                    delegate = this@Account
                }
                Log.d(TAG, "Stream initialized for $jid with port $port")
                messageReceiver.subscribeReceiver()
            } else {
                Log.w(TAG, "Cannot initialize Stream: JID is empty")
                onErrorCallback?.invoke("Cannot initialize connection: Invalid JID")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Stream for $jid: ${e.message}", e)
            onErrorCallback?.invoke("Error initializing connection: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun connectStream(): Boolean = withContext(Dispatchers.IO) {
        try {
            stream?.let {
                val connectError = it.connect()
                if (connectError == null) {
                    presenceManager = PresenceManager(jid, it.socket!!)
                    statusMessage.onNext("Online")
                    Log.d(TAG, "Stream connected for $jid")
                    syncAllChats(it) // Trigger MAM sync immediately
                    return@withContext true
                } else {
                    statusMessage.onNext("Offline")
                    Log.e(TAG, "Stream connection failed for $jid: $connectError")
                    onErrorCallback?.invoke(connectError)
                    return@withContext false
                }
            } ?: run {
                Log.w(TAG, "No Stream initialized for $jid")
                onErrorCallback?.invoke("No connection initialized")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting Stream for $jid: ${e.message}", e)
            onErrorCallback?.invoke("Connection error: ${e.message}")
            statusMessage.onNext("Offline")
            return@withContext false
        }
    }

    private suspend fun syncAllChats(stream: Stream) = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val chats = realm.write {
                query<LastChatsStorageItem>("owner = $0", jid).find()
            }
            Log.d(TAG, "Found ${chats.size} chats to sync for $jid")
            // Prioritize chats with pinned messages or recent activity
            val prioritizedChats = chats.sortedByDescending { it.pinnedPosition ?: it.messageDate }
            prioritizedChats.forEach { chat ->
                launch(Dispatchers.IO.limitedParallelism(4)) { // Limit concurrent MAM queries
                    val conversationType = ConversationType.fromRaw(chat.conversationType_)
                    try {
                        messageArchiveManager.syncChat(
                            stream = stream,
                            jid = chat.jid,
                            conversationType = conversationType,
                            callback = {
                                Log.d(TAG, "Chat history sync completed for jid=${chat.jid}, type=${chat.conversationType_}")
                            }
                        )
                        Log.d(TAG, "Initiated sync for chat jid=${chat.jid}, type=${chat.conversationType_}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync chat jid=${chat.jid}, type=${chat.conversationType_}: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing chats for $jid: ${e.message}", e)
        } finally {
            realm.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun closeStream() = withContext(Dispatchers.IO) {
        stream?.close()
        stream = null
        presenceManager = null
        rosterManager.close()
        messageReceiver.unsubscribeReceiver()
        statusMessage.onNext("Offline")
        rosterRequested = false
        synchronized(this@Account) {
            presenceStanzas.clear() // Clear buffer on close
        }
        stanzaProcessingScope.cancel()
        Log.d(TAG, "Stream closed for $jid")
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun didReceiveIQ(iq: XMPPIQ, stream: Stream): Boolean {

        try {
            // Buffer roster and sync IQ stanzas post-registration
            if (stream.state == StreamState.CONNECTED || stream.state == StreamState.BINDING) {
                if (iq.queryNamespace == "jabber:iq:roster" || iq.queryContent?.contains("<item") == true || iq.queryContent?.contains("<group>") == true) {
                    stanzaBuffer.emit(StanzaItem(StanzaItem.StanzaType.ROSTER, iq.raw, stream))
                    Log.d(TAG, "Buffered roster IQ stanza: ${iq.raw.take(200)}")
                    return true
                }
                if (iq.queryNamespace == "https://xabber.com/protocol/synchronization") {
                    stanzaBuffer.emit(StanzaItem(StanzaItem.StanzaType.SYNC, iq.raw, stream))
                    Log.d(TAG, "Buffered sync IQ stanza: ${iq.raw.take(200)}")
                    return true
                }
            }

            // Handle ping and disco#info synchronously to maintain responsiveness
            if (iq.type == "get" && iq.queryNamespace == "urn:xmpp:ping" && stream.state == StreamState.CONNECTED) {
                Log.d(TAG, "Received server ping request")
                val pingId = iq.id ?: return false
                val fromJid = iq.from ?: return false
                val response = """
                    <iq type='result' id='$pingId' to='$fromJid'/>
                """.trimIndent()
                return withContext(Dispatchers.IO) {
                    if (stream.socket?.write(response) == true) {
                        Log.d(TAG, "Sent ping response: $response")
                        true
                    } else {
                        Log.e(TAG, "Failed to send ping response")
                        onErrorCallback?.invoke("Failed to send ping response")
                        stream.state = StreamState.NOT_CONNECTING
                        false
                    }
                }
            }

            if (iq.type == "get" && iq.queryNamespace == "http://jabber.org/protocol/disco#info") {
                Log.d(TAG, "Received disco#info query from ${iq.from}")
                val discoId = iq.id ?: return false
                val fromJid = iq.from ?: return false
                val toJid = iq.to ?: jid
                val response = buildDiscoInfoResponse(discoId, fromJid, toJid)
                return withContext(Dispatchers.IO) {
                    if (stream.socket?.write(response) == true) {
                        Log.d(TAG, "Sent disco#info response to $fromJid")
                        true
                    } else {
                        Log.e(TAG, "Failed to send disco#info response")
                        false
                    }
                }
            }

            // Handle device registration and binding synchronously
            if (stream.state == StreamState.DEVICE_REGISTRATION) {
                Log.d(TAG, "Received IQ response for device registration")
                if (iq.type == "result") {
                    Log.d(TAG, "Device registration successful")
                    val uidMatch = Regex("""device id=['"]([^'"]+)['"]""").find(iq.queryContent ?: "")
                    val validationKeyMatch = Regex("""<validation-key>([^<]+)</validation-key>""").find(iq.queryContent ?: "")
                    val expireMatch = Regex("""<expire>([^<]+)</expire>""").find(iq.queryContent ?: "")
                    val secretMatch = Regex("""<secret>([^<]+)</secret>""").find(iq.queryContent ?: "")
                    if (uidMatch != null && validationKeyMatch != null && expireMatch != null && secretMatch != null) {
                        val uid = uidMatch.groupValues[1]
                        val validationKey = validationKeyMatch.groupValues[1]
                        var expireDuration = expireMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                        if (expireDuration <= 0) {
                            Log.w(TAG, "Invalid expire duration from server: $expireDuration - defaulting to 3600s")
                            expireDuration = 3600.0
                        }
                        val secret = secretMatch.groupValues[1]
                        val currentTime = System.currentTimeMillis().toDouble() / 1000
                        val expire = currentTime + expireDuration
                        var existingDevice: DeviceStorageItem? = null
                        existingDevice = realm.query<DeviceStorageItem>("uid = $0 AND owner = $1", uid, jid).first().find()
                        realm.write {
                            if (existingDevice != null) {
                                findLatest(existingDevice!!)?.apply {
                                    configure(
                                        owner = jid,
                                        uid = uid,
                                        ip = stream.socket?.getSocket()?.remoteAddress?.toString() ?: "",
                                        client = "Xabber-android-device",
                                        device = deviceModel,
                                        expire = expire,
                                        authDate = currentTime,
                                        authCounter = 1,
                                        descr = "Confident Albatross",
                                        secret = secret,
                                        validationKey = validationKey
                                    )
                                }
                                Log.d(TAG, "Updated existing DeviceStorageItem for uid: $uid, owner: $jid")
                            } else {
                                val newDevice = DeviceStorageItem().apply {
                                    configure(
                                        owner = jid,
                                        uid = uid,
                                        ip = stream.socket?.getSocket()?.remoteAddress?.toString() ?: "",
                                        client = "Xabber-android",
                                        device = deviceModel,
                                        expire = expire,
                                        authDate = currentTime,
                                        authCounter = 1,
                                        descr = "Confident Albatross",
                                        secret = secret,
                                        validationKey = validationKey
                                    )
                                }
                                copyToRealm(newDevice, UpdatePolicy.ALL)
                                Log.d(TAG, "Created new DeviceStorageItem for uid: $uid, owner: $jid")
                            }
                        }
                        val savedDevice = realm.query<DeviceStorageItem>("uid = $0 AND owner = $1", uid, jid).first().find()
                        if (savedDevice != null) {
                            Log.d(TAG, "Confirmed DeviceStorageItem saved: uid=$uid, expire=${savedDevice.expire}, authCounter=${savedDevice.authCounter}")
                            isDeviceRegistered = true
                            stream.state = StreamState.BINDING
                        } else {
                            Log.e(TAG, "Failed to confirm DeviceStorageItem save for uid=$uid, owner=$jid - forcing re-registration")
                            isDeviceRegistered = false
                            stream.state = StreamState.DEVICE_REGISTRATION
                            return false
                        }
                        realm.write {
                            val device = query<DeviceStorageItem>("uid = $0 AND owner = $1", uid, jid).first().find()
                            if (device != null) {
                                findLatest(device)?.apply {
                                    authCounter++
                                    Log.d(TAG, "Incremented authCounter to 2 for uid: $uid, owner: $jid")
                                }
                            } else {
                                Log.e(TAG, "Failed to find device for incrementing authCounter: uid=$uid, owner=$jid")
                            }
                        }
                        isDeviceRegistered = true
                        stream.state = StreamState.BINDING
                        return true
                    } else {
                        Log.e(TAG, "Failed to parse device registration response: ${iq.raw}")
                        onErrorCallback?.invoke("Failed to parse device registration response")
                        stream.state = StreamState.NOT_CONNECTING
                        return false
                    }
                } else {
                    Log.e(TAG, "Device registration failed: ${iq.raw}")
                    onErrorCallback?.invoke("Device registration failed")
                    stream.state = StreamState.NOT_CONNECTING
                    return false
                }
            }

            if (stream.state == StreamState.BINDING) {
                Log.d(TAG, "Received IQ response for binding")
                val jidMatch = Regex("""<jid>([^<]+)</jid>""").find(iq.queryContent ?: "")
                if (jidMatch != null) {
                    boundJid = jidMatch.groupValues[1]
                    Log.d(TAG, "Resource binding successful, bound JID: $boundJid")
                    stream.state = StreamState.CONNECTED
                    return true
                } else {
                    Log.e(TAG, "Binding failed, no JID in response: ${iq.raw}")
                    onErrorCallback?.invoke("Resource binding failed")
                    stream.state = StreamState.NOT_CONNECTING
                    return false
                }
            }

            Log.w(TAG, "Unhandled IQ: ${iq.raw}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling IQ: ${e.message}", e)
            onErrorCallback?.invoke("Error processing IQ: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    // New: Async processing for roster stanzas
    private suspend fun processRosterStanza(stanza: String, stream: Stream) {
        val batchSize = 10 // Process up to 10 roster stanzas at once
        val rosterStanzas = mutableListOf<String>()

        synchronized(rosterStanzaBuffer) {
            rosterStanzaBuffer.append(stanza)
            val bufferedContent = rosterStanzaBuffer.toString()
            if (bufferedContent.trim().startsWith("<iq") && bufferedContent.contains("</iq>")) {
                rosterStanzas.add(bufferedContent)
                rosterStanzaBuffer.clear()
                Log.d(TAG, "Collected complete roster stanza for processing: ${bufferedContent.take(200)}")
            } else {
                Log.d(TAG, "Incomplete roster stanza, buffering: ${bufferedContent.take(200)}")
                return
            }
        }

        // Batch process roster stanzas
        rosterStanzas.chunked(batchSize).forEach { batch ->
            try {
                batch.forEach { completeStanza ->
                    val iq = parseIQ(completeStanza) // Assume parseIQ is defined elsewhere
                    if (iq != null) {
                        rosterManager.read(XMPPIQ(raw = completeStanza, type = iq.type, id = iq.id, from = iq.from, to = iq.to, error = iq.error, queryNamespace = iq.queryNamespace, queryContent = iq.queryContent))
                        Log.d(TAG, "Processed roster IQ stanza: ${completeStanza.take(200)}")
                    } else {
                        Log.w(TAG, "Failed to parse roster IQ stanza: ${completeStanza.take(200)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing roster stanza batch: ${e.message}", e)
            }
        }
    }

    // New: Async processing for sync stanzas
    private suspend fun processSyncStanza(stanza: String, stream: Stream) {
        val batchSize = 10 // Process up to 10 sync stanzas at once
        val syncStanzas = mutableListOf<String>()
        synchronized(syncStanzaBuffer) {
            syncStanzaBuffer.append(stanza)
            val bufferedContent = syncStanzaBuffer.toString()
            if (bufferedContent.contains("<query") && bufferedContent.contains("https://xabber.com/protocol/synchronization") && bufferedContent.contains("</query>")) {
                val cleaned = bufferedContent.replace(Regex("""<iq[^>]*type='result'[^>]*id='ping1'[^>]*/>"""), "").replace(Regex("r\\.boldin='modify'"), "")
                val iqStart = cleaned.indexOf("<iq")
                val iqEnd = cleaned.lastIndexOf("</iq>") + 5
                if (iqStart != -1 && iqEnd != -1 && iqEnd > iqStart) {
                    syncStanzas.add(cleaned.substring(iqStart, iqEnd))
                    syncStanzaBuffer.clear()
                    Log.d(TAG, "Collected complete sync stanza for processing: ${cleaned.take(200)}")
                } else {
                    Log.e(TAG, "Failed to extract complete sync <iq> stanza: ${cleaned.take(200)}")
                    return
                }
            } else {
                Log.d(TAG, "Incomplete sync stanza, buffering: ${bufferedContent.take(200)}")
                return
            }
        }

        // Batch process sync stanzas
        syncStanzas.chunked(batchSize).forEach { batch ->
            try {
                batch.forEach { completeStanza ->
                    syncManager.read(completeStanza)
                    Log.d(TAG, "Processed sync query stanza: ${completeStanza.take(200)}")
                    syncCompletionChannel.trySend(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing sync stanza batch: ${e.message}", e)
            }
        }
    }

    // New: Async processing for presence stanzas
    private suspend fun processPresenceStanza(presence: XMPPPresence, stream: Stream) {
        val batchSize = 20 // Reduced from 50 to 20 for faster processing
        val maxWaitTime = 10L // Reduced from 50ms to 10ms

        val batchToProcess = synchronized(this) {
            presenceStanzas.add(presence)
            if (presenceStanzas.size >= batchSize) {
                val batch = presenceStanzas.take(batchSize).toMutableList()
                presenceStanzas.removeAll(batch)
                batch
            } else {
                emptyList()
            }
        }

        if (batchToProcess.isNotEmpty()) {
            processPresenceBatch(batchToProcess, stream)
        } else {
            stanzaProcessingScope.launch {
                delay(maxWaitTime)
                val delayedBatch = synchronized(this@Account) {
                    if (presenceStanzas.isNotEmpty()) {
                        val batch = presenceStanzas.take(batchSize).toMutableList()
                        presenceStanzas.removeAll(batch)
                        batch
                    } else {
                        emptyList()
                    }
                }
                if (delayedBatch.isNotEmpty()) {
                    processPresenceBatch(delayedBatch, stream)
                }
            }
        }
    }

    private suspend fun processPresenceBatch(presences: List<XMPPPresence>, stream: Stream) {
        if (presences.isEmpty()) return
        val startTime = System.currentTimeMillis()
        try {
            presences.chunked(20).forEach { chunk ->
                chunk.forEach { presence ->
                    if (presence.from?.contains("/Group") == true) {
                        return@forEach // Skip group chat presence early
                    }
                    stanzaProcessingScope.launch {
                        try {
                            presenceManager?.processPresence(presence)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing individual presence stanza: ${e.message}, id=${presence.id}, from=${presence.from}", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in processPresenceBatch: ${e.message}")
        }
        Log.d(TAG, "Processed batch of ${presences.size} presence stanzas in ${System.currentTimeMillis() - startTime}ms")
    }

    private fun buildDiscoInfoResponse(id: String, to: String, from: String): String {
        val features = listOf(
            "http://jabber.org/protocol/disco#info",
            "urn:xmpp:ping",
            "jabber:iq:roster",
            "urn:xmpp:carbons:2",
            "https://xabber.com/protocol/synchronization",
            "urn:ietf:params:xml:ns:xmpp-bind",
            "urn:ietf:params:xml:ns:xmpp-sasl",
            "urn:ietf:params:xml:ns:xmpp-tls",
            "urn:xmpp:mam:2"
        )
        val featureVars = features.joinToString("") { "<feature var='$it'/>" }
        return """
            <iq type='result' id='$id' to='$to' from='$from'>
                <query xmlns='http://jabber.org/protocol/disco#info'>
                    <identity category='client' type='mobile' name='Xabber Android'/>
                    $featureVars
                </query>
            </iq>
        """.trimIndent()
    }

    override suspend fun didReceivePresence(presence: XMPPPresence, stream: Stream): Boolean {
        if (stream.state == StreamState.CONNECTED || stream.state == StreamState.BINDING) {
            stanzaBuffer.emit(StanzaItem(StanzaItem.StanzaType.PRESENCE, presence.raw, stream))
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Buffered presence stanza: id=${presence.id}, from=${presence.from}, to=${presence.to}")
            }
            return true
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Received presence stanza: id=${presence.id}, from=${presence.from}, to=${presence.to}")
        }
        return presenceManager?.processPresence(presence) ?: run {
            Log.w(TAG, "PresenceManager not initialized, skipping presence processing")
            false
        }
    }


    override fun didReceiveStreamHeader(header: String, stream: Stream): Boolean {
        Log.d(TAG, "Received stream header, awaiting features")
        return true
    }

    override suspend fun didReceiveStreamFeatures(features: String, stream: Stream): Boolean {
        try {
            Log.d(TAG, "Received stream features: $features")
            supportedFeatures = features
            realm.write {
                val account = query<AccountStorageItem>("jid = $0", jid).first().find()
                if (account != null && account.clientSyncSupport != true) {
                    findLatest(account)?.clientSyncSupport = true
                    Log.d(TAG, "Updated AccountStorageItem clientSyncSupport to true for JID: $jid")
                }
            }
            val response = stream.socket?.parseStreamResponse(features)
            if (response == null) {
                Log.e(TAG, "Failed to parse stream features")
                onErrorCallback?.invoke("Failed to parse stream features")
                stream.state = StreamState.NOT_CONNECTING
                return false
            }
            if (stream.state == StreamState.NOT_CONNECTING || stream.state == StreamState.PROCEED || stream.state == StreamState.AUTH_SUCCESS) {
                Log.d(TAG, "Initial stream response received")
                stream.state = StreamState.STREAM_OPEN
            }
            response.features?.let { feat ->
                Log.d(TAG, "Stream features: $feat")
                if (stream.state == StreamState.PROCEED && DevicesOCRA.isSupported(feat)) {
                    Log.d(TAG, "DEVICES-OCRA authentication is supported post-TLS")
                    stream.state = StreamState.START_AUTH
                } else if (!attemptedPreTlsAuth && DevicesOCRA.isSupported(feat)) {
                    Log.d(TAG, "DEVICES-OCRA authentication is supported pre-TLS")
                    attemptedPreTlsAuth = true
                    stream.state = StreamState.START_AUTH
                } else if (stream.state == StreamState.PROCEED && feat.mechanisms?.mechanism?.contains("PLAIN") == true) {
                    Log.d(TAG, "PLAIN authentication is supported post-TLS")
                    stream.state = StreamState.START_AUTH
                } else if (!attemptedPreTlsAuth && feat.mechanisms?.mechanism?.contains("PLAIN") == true) {
                    Log.d(TAG, "PLAIN authentication is supported pre-TLS")
                    attemptedPreTlsAuth = true
                    stream.state = StreamState.START_AUTH
                } else if (feat.starttls?.present == true) {
                    val isTlsRequired = features.contains("<required/>")
                    Log.d(TAG, "STARTTLS is supported${if (isTlsRequired) " and required" else ""}")
                    if (isTlsRequired && attemptedPreTlsAuth) {
                        Log.w(TAG, "TLS required after failed pre-TLS auth attempt")
                        attemptedPreTlsAuth = false
                    }
                    stream.state = StreamState.START_TLS
                } else if (stream.state == StreamState.STREAM_OPEN && feat.devices?.present == true) {
                    Log.d(TAG, "Device registration is supported")
                    if (isDeviceRegistered) {
                        Log.d(TAG, "Skipping device registration, already registered for JID: $jid")
                        stream.state = StreamState.BINDING
                    } else {
                        stream.state = StreamState.DEVICE_REGISTRATION
                    }
                } else {
                    Log.w(TAG, "No supported features found")
                    onErrorCallback?.invoke("No supported authentication features found")
                    stream.state = StreamState.NOT_CONNECTING
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error handling stream features: ${e.message}", e)
            onErrorCallback?.invoke("Error processing stream features: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun didReceiveChallenge(challenge: String, stream: Stream): Boolean {
        try {
            Log.d(TAG, "Received OCRA challenge")
            if (stream.state == StreamState.PROCESS_AUTH && ocraAuth != null) {
                val success = ocraAuth!!.handleAuthChallenge(challenge)
                if (!success) {
                    Log.e(TAG, "OCRA challenge handling failed")
                    onErrorCallback?.invoke("OCRA authentication challenge failed")
                    stream.state = StreamState.AUTH_FAILED
                    return false
                }
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling challenge: ${e.message}", e)
            onErrorCallback?.invoke("Error processing challenge: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun didReceiveSuccess(success: String, stream: Stream): Boolean {
        try {
            Log.d(TAG, "Authentication successful")
            if (stream.state == StreamState.PROCESS_AUTH) {
                if (ocraAuth != null) {
                    val succ = ocraAuth!!.handleAuthResponse(success)
                    if (!succ) {
                        Log.e(TAG, "OCRA authentication response handling failed")
                        onErrorCallback?.invoke("OCRA authentication response handling failed")
                        stream.state = StreamState.AUTH_FAILED
                        return false
                    }
                }
                stream.state = StreamState.AUTH_SUCCESS
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling success: ${e.message}", e)
            onErrorCallback?.invoke("Error processing success: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override fun didReceiveFailure(failure: String, stream: Stream): Boolean {
        try {
            Log.e(TAG, "Authentication failed: $failure")
            if (stream.state == StreamState.PROCESS_AUTH) {
                val errorTextMatch = Regex("""<text[^>]*>([^<]+)</text>""").find(failure)
                val errorText = errorTextMatch?.groupValues?.get(1) ?: "Unknown authentication error"
                val errorTypeMatch = Regex("""<([a-z\-]+)\/>""").find(failure)
                val errorType = errorTypeMatch?.groupValues?.get(1) ?: "unknown"
                val userMessage = when (errorType) {
                    "not-authorized" -> "Authentication failed: $errorText"
                    else -> "Authentication failed: $errorText ($errorType)"
                }
                Log.e(TAG, userMessage)
                onErrorCallback?.invoke(userMessage)
                stream.state = StreamState.AUTH_FAILED
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling failure: ${e.message}", e)
            onErrorCallback?.invoke("Error processing failure: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override fun didReceiveProceed(proceed: String, stream: Stream): Boolean {
        Log.d(TAG, "Received proceed for STARTTLS")
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun didReceiveMessage(message: XMPPMessage, stream: Stream): Boolean {
        Log.d(TAG, "Received message stanza: id=${message.id}, from=${message.from?.bare()}, to=${message.to?.bare()}")
        try {
            // Check if the message is a chat state notification
            val isChatState = message.hasElement("active", "http://jabber.org/protocol/chatstates") ||
                    message.hasElement("composing", "http://jabber.org/protocol/chatstates") ||
                    message.hasElement("inactive", "http://jabber.org/protocol/chatstates") ||
                    message.hasElement("received", "urn:xmpp:chat-markers:0") ||
                    message.hasElement("displayed", "urn:xmpp:chat-markers:0")

            val messageId = message.id ?: "unknown_${System.currentTimeMillis()}"
            val fromJid = message.from?.bare()
            val toJid = message.to?.bare()
            val body = message.body

            if (isChatState && body.isNullOrEmpty()) {
                Log.d(TAG, "Skipping chat state notification: id=$messageId")
                return true
            }

            if (fromJid == null || toJid == null || body == null) {
                Log.w(TAG, "Skipping message with missing attributes: id=$messageId, from=$fromJid, to=$toJid, body=$body")
                return false
            }

            val opponent = if (toJid != jid) toJid else fromJid
            if (opponent == jid) {
                Log.w(TAG, "Skipping self-directed message: id=$messageId, from=$fromJid, to=$toJid")
                return false
            }

            val realm = Realm.open(defaultRealmConfig())
            val primary = "${messageId}_$jid"
            val existingMessage = realm.query<MessageStorageItem>("primary = $0", primary).first().find()
            if (existingMessage != null) {
                Log.d(TAG, "Skipping duplicate message: id=$messageId, primary=$primary")
                realm.close()
                return true
            }

            val tempStanza = realm.query<TemporaryMessageStanzaStorageItem>(
                "primary = $0 AND isProcessed = false", TemporaryMessageStanzaStorageItem.genPrimary(messageId, jid)
            ).first().find()

            if (tempStanza == null && !isChatState) {
                Log.d(TAG, "Creating new TemporaryMessageStanzaStorageItem for messageId=$messageId, primary=$primary")
                realm.write {
                    val newTempStanza = TemporaryMessageStanzaStorageItem().apply {
                        this.messageId = messageId
                        this.primary = primary
                        this.owner = jid
                        this.jid = opponent
                        this.isProcessed = false
                        this.date = parseTimestamp(message) ?: System.currentTimeMillis()
                        this.stanza = message.raw
                    }
                    copyToRealm(newTempStanza, UpdatePolicy.ALL)
                }
            }

            val timestamp = tempStanza?.date?.takeIf { it > 0 } ?: parseTimestamp(message) ?: System.currentTimeMillis()
            val date = Date(timestamp)
            val isOutgoing = fromJid == jid
            val state = if (isOutgoing) MessageSendingState.Deliver else MessageSendingState.Sent

            var containerType: String? = null
            var innerMessage: XMPPMessage? = message
            if (message.hasElement("sent", "urn:xmpp:carbons:2")) {
                containerType = "forwarded"
                Log.d(TAG, "Detected forwarded carbon message for messageId=$messageId")
                // For carbon messages, use the inner message data already parsed in Stream
                innerMessage = message
            } else if (message.hasElement("last-message")) {
                containerType = "last-message"
                Log.d(TAG, "Detected last-message container for messageId=$messageId")
            } else if (message.hasElement("archived", "urn:xmpp:mam:tmp")) {
                containerType = "archived"
                Log.d(TAG, "Detected archived container for messageId=$messageId")
            } else {
                containerType = "runtime"
                Log.d(TAG, "No specific container found, treating as runtime for messageId=$messageId")
            }

            when (containerType) {
                "archived" -> {
                    Log.d(TAG, "Directing archived message to MessageArchiveManager: id=$messageId")
                    messageArchiveManager.readMessage(message.raw)
                }
                "forwarded" -> {
                    Log.d(TAG, "Directing forwarded (carbon) message to MessageCommonReceiver")
                    messageReceiver.receiveCarbon(message)
                }
                "last-message" -> {
                    // Handle last-message if needed
                }
                "runtime" -> {
                    Log.d(TAG, "Directing runtime message to MessageCommonReceiver")
                    messageReceiver.receiveRuntime(message)
                }
            }

            if (tempStanza != null) {
                realm.write {
                    val latest = findLatest(tempStanza)
                    if (latest != null) {
                        latest.isProcessed = true
                        Log.d(TAG, "Marked TemporaryMessageStanzaStorageItem as processed: id=$messageId, primary=$primary")
                    }
                }
            }
            realm.close()

            Log.d(TAG, "Processed message successfully: id=$messageId, container=$containerType")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}", e)
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    private fun parseTimestamp(message: XMPPMessage): Long? {
        val timeElement = message.element("time", namespace = "https://xabber.com/protocol/delivery")
        val stamp = timeElement?.getAttribute("stamp")
        return stamp?.let {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(it)?.time
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse timestamp: ${e.message}")
                null
            }
        }
    }

    private fun parserToDom(parser: XmlPullParser): Node {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val document = builder.newDocument()
        val stack = mutableListOf<Node>(document)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val element = document.createElementNS(parser.namespace, parser.name)
                    for (i in 0 until parser.attributeCount) {
                        element.setAttribute(parser.getAttributeName(i), parser.getAttributeValue(i))
                    }
                    stack.last().appendChild(element)
                    stack.add(element)
                }
                XmlPullParser.END_TAG -> {
                    stack.removeLast()
                }
                XmlPullParser.TEXT -> {
                    stack.last().appendChild(document.createTextNode(parser.text))
                }
            }
            eventType = parser.next()
        }
        return stack.first().firstChild ?: document
    }

    private fun getDeliveryTime(message: XMPPMessage): Date? {
        val time = message.element("time", namespace = "https://xabber.com/protocol/delivery")?.getAttribute("stamp")
        return time?.let {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse delivery timestamp: ${e.message}")
                null
            }
        }
    }

    override suspend fun streamDidConnect(stream: Stream): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
//            presenceManager?.sendInitialPresence()
            if (!rosterRequested) {
                rosterManager.request(stream)
                rosterRequested = true
            }
            streamCarbonsSend(stream)
            streamSyncRequest(stream)
        }
        return true
    }

    override suspend fun streamBinding(stream: Stream): Boolean {
        try {
            val bindId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
            val resourceId = NanoId.generateOptimized(8, "0123456789ABC Chaz6", 63, 16)
            val bindRequest = """
                <iq type='set' id='$bindId'>
                    <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>
                        <resource>xabber-android-$resourceId</resource>
                    </bind>
                </iq>
            """.trimIndent()
            return withContext(Dispatchers.IO) {
                if (stream.socket?.write(bindRequest) == true) {
                    Log.d(TAG, "Sent bind request for JID: $jid")
                    true
                } else {
                    Log.e(TAG, "Failed to send bind request for JID: $jid")
                    onErrorCallback?.invoke("Failed to send resource binding request")
                    stream.state = StreamState.NOT_CONNECTING
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during resource binding for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Resource binding error: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun streamDeviceRegistration(stream: Stream): Boolean {
        try {
            if (isDeviceRegistered) {
                Log.d(TAG, "Device already registered for JID: $jid, skipping registration")
                stream.state = StreamState.BINDING
                return true
            }
            val deviceId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
            val deviceRequest = """
                <iq type='set' id='$deviceId'>
                    <register xmlns='https://xabber.com/protocol/devices'>
                        <device xmlns='https://xabber.com/protocol/devices'>
                            <info>$deviceModel</info>
                            <client>Xabber-android</client>
                            <expire>3600000</expire>
                            <public-label>Confident Albatross</public-label>
                            <type>android</type>
                        </device>
                    </register>
                </iq>
            """.trimIndent()
            return withContext(Dispatchers.IO) {
                if (stream.socket?.write(deviceRequest) == true) {
                    Log.d(TAG, "Sent device registration request for JID: $jid")
                    true
                } else {
                    Log.e(TAG, "Failed to send device registration request for JID: $jid")
                    onErrorCallback?.invoke("Failed to send device registration request")
                    stream.state = StreamState.NOT_CONNECTING
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during device registration for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Device registration error: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun streamAuthFailed(stream: Stream): Boolean {
        Log.e(TAG, "Authentication failed for JID: $jid")
        onErrorCallback?.invoke("Authentication failed")
        if (!attemptedPreTlsAuth || stream.state == StreamState.PROCEED) {
            Log.e(TAG, "Closing connection due to auth failure")
            closeStream()
        } else {
            Log.d(TAG, "Pre-TLS auth failed, falling back to START_TLS")
            stream.state = StreamState.START_TLS
        }
        return true
    }

    override suspend fun streamAuthSuccess(stream: Stream): Boolean {
        try {
            Log.d(TAG, "Authentication successful for JID: $jid, initiating new stream")
            stream.socket?.initiateXmppStream(stream.socket!!, host, jid)
            Log.d(TAG, "New stream initiated after auth success for JID: $jid")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating new stream after auth success: ${e.message}", e)
            onErrorCallback?.invoke("Error initiating stream after authentication")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun streamPlainAuth(stream: Stream): Boolean {
        try {
            Log.d(TAG, "Initiating SASL PLAIN authentication for JID: $jid")
            val username = stream.extractUsernameFromJid(jid)
            Log.d(TAG, "Extracted username: $username")
            val authData = stream.socket!!.saslPlainAuth(jid, username)
            Log.d(TAG, "Generated SASL PLAIN auth data")
            val authMessage = """
                <auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>$authData</auth>
            """.trimIndent()
            return withContext(Dispatchers.IO) {
                if (stream.socket?.write(authMessage) == true) {
                    Log.d(TAG, "Sent SASL PLAIN auth request for JID: $jid")
                    stream.state = StreamState.PROCESS_AUTH
                    true
                } else {
                    Log.e(TAG, "Failed to send SASL PLAIN auth request for JID: $jid")
                    onErrorCallback?.invoke("Failed to send PLAIN authentication request")
                    stream.state = StreamState.AUTH_FAILED
                    false
                }
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "SASL PLAIN authentication failed for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("PLAIN authentication failed: ${e.message}")
            stream.state = StreamState.AUTH_FAILED
            return false
        }
    }

    override suspend fun streamOCRAAuth(stream: Stream): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Entering streamOCRAAuth for JID: $jid")
        if (stream.socket == null || stream.socket?.getSocket()?.isClosed == true) {
            Log.e(TAG, "Cannot initiate authentication: Socket is null or closed")
            onErrorCallback?.invoke("Cannot initiate authentication: Connection closed")
            stream.state = StreamState.AUTH_FAILED
            return@withContext false
        }
        try {
            val response = stream.socket?.parseStreamResponse(stream.messageCallbackChannel.tryReceive().getOrNull() ?: "")
            val features = response?.features
            if (DevicesOCRA.isSupported(features)) {
                Log.d(TAG, "Initiating DEVICES-OCRA authentication for JID: $jid")
                var device: DeviceStorageItem? = null
                device = realm.query<DeviceStorageItem>("owner = $0", jid).first().find()
                device?.let {
                    if (it.secret.isNotEmpty() && it.validationKey.isNotEmpty() && it.uid.isNotEmpty()) {
                        Log.d(TAG, "Using DeviceStorageItem for OCRA: uid=${it.uid}, authCounter=${it.authCounter}")
                        ocraAuth = DevicesOCRA(
                            stream = stream,
                            deviceId = it.uid,
                            secret = it.secret,
                            validationKey = it.validationKey,
                            authCounter = it.authCounter,
                            realm = realm
                        )
                        if (ocraAuth?.start() == true) {
                            Log.d(TAG, "DEVICES-OCRA authentication started for JID: $jid")
                            stream.state = StreamState.PROCESS_AUTH
                            return@withContext true
                        } else {
                            Log.e(TAG, "Failed to start DEVICES-OCRA authentication for JID: $jid")
                            onErrorCallback?.invoke("Failed to start DEVICES-OCRA authentication")
                            stream.state = StreamState.AUTH_FAILED
                            return@withContext false
                        }
                    } else {
                        Log.e(TAG, "DeviceStorageItem missing required fields for OCRA")
                        onErrorCallback?.invoke("Invalid device data for OCRA authentication")
                        stream.state = StreamState.AUTH_FAILED
                        return@withContext false
                    }
                } ?: run {
                    Log.e(TAG, "No valid device found for OCRA authentication for JID: $jid")
                    if (features?.mechanisms?.mechanism?.contains("PLAIN") == true) {
                        Log.d(TAG, "Falling back to PLAIN authentication")
                        streamPlainAuth(stream)
                        return@withContext true
                    } else {
                        Log.e(TAG, "No supported authentication mechanisms")
                        onErrorCallback?.invoke("No supported authentication mechanisms")
                        stream.state = StreamState.AUTH_FAILED
                        return@withContext false
                    }
                }
            } else if (features?.mechanisms?.mechanism?.contains("PLAIN") == true) {
                Log.d(TAG, "DEVICES-OCRA not supported, using PLAIN authentication")
                streamPlainAuth(stream)
                return@withContext true
            } else {
                Log.e(TAG, "No supported authentication mechanisms found")
                onErrorCallback?.invoke("No supported authentication mechanisms")
                stream.state = StreamState.AUTH_FAILED
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during authentication for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Authentication error: ${e.message}")
            stream.state = StreamState.AUTH_FAILED
            return@withContext false
        }
    }

    override suspend fun streamStartTLS(stream: Stream): Boolean = withContext(Dispatchers.IO) {
        if (stream.socket == null || stream.socket?.getSocket()?.isClosed == true) {
            Log.e(TAG, "Cannot initiate STARTTLS: Socket is null or closed")
            onErrorCallback?.invoke("Cannot initiate STARTTLS: Connection closed")
            stream.state = StreamState.NOT_CONNECTING
            return@withContext false
        }
        try {
            Log.d(TAG, "Initiating STARTTLS negotiation")
            if (!stream.socket!!.initiateStartTls()) {
                Log.e(TAG, "Failed to initiate STARTTLS")
                onErrorCallback?.invoke("Failed to initiate STARTTLS")
                stream.state = StreamState.NOT_CONNECTING
                return@withContext false
            }
            Log.d(TAG, "STARTTLS negotiation successful, preparing for TLS upgrade")
            delay(1000)
            Log.d(TAG, "Upgrading to TLS")
            if (stream.socket?.upgradeToTls() == true) {
                Log.d(TAG, "TLS upgrade successful, initiating new stream")
                stream.state = StreamState.PROCEED
                stream.socket?.initiateXmppStream(stream.socket!!, host, jid)
                Log.d(TAG, "New stream initiated over TLS, awaiting response")
                return@withContext true
            } else {
                Log.e(TAG, "Failed to upgrade to TLS")
                onErrorCallback?.invoke("Failed to upgrade to TLS")
                stream.state = StreamState.NOT_CONNECTING
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during STARTTLS process: ${e.message}", e)
            onErrorCallback?.invoke("STARTTLS error: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return@withContext false
        }
    }

    override suspend fun streamSyncRequest(stream: Stream): Boolean {
        try {
            if (stream.state != StreamState.CONNECTED) {
                Log.w(TAG, "Cannot send sync request: Stream is not in CONNECTED state, current state: ${stream.state}")
                onErrorCallback?.invoke("Cannot send sync request: Not connected")
                return false
            }
            if (stream.socket == null || stream.socket?.getSocket()?.isClosed == true) {
                Log.e(TAG, "Cannot send sync request: Socket is null or closed")
                onErrorCallback?.invoke("Cannot send sync request: Connection closed")
                stream.state = StreamState.NOT_CONNECTING
                return false
            }
            val syncId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
            val syncRequest = """
                <iq type='get' id='$syncId' from='$jid' to='$jid'>
                    <query xmlns='https://xabber.com/protocol/synchronization'/>
                </iq>
            """.trimIndent()
            return withContext(Dispatchers.IO) {
                if (stream.socket?.write(syncRequest) == true) {
                    Log.d(TAG, "Sent sync request for JID: $jid with id: $syncId")
                    true
                } else {
                    Log.e(TAG, "Failed to send sync request for JID: $jid")
                    onErrorCallback?.invoke("Failed to send sync request")
                    stream.state = StreamState.NOT_CONNECTING
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending sync request for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Sync request error: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun streamCarbonsSend(stream: Stream): Boolean = withContext(Dispatchers.IO) {
        if (stream.state != StreamState.CONNECTED) {
            Log.w(TAG, "Cannot send carbons enable: Stream is not in CONNECTED state, current state: ${stream.state}")
            onErrorCallback?.invoke("Cannot send carbons enable: Not connected")
            return@withContext false
        }
        if (stream.socket == null || stream.socket?.getSocket()?.isClosed == true) {
            Log.e(TAG, "Cannot send carbons enable: Socket is null or closed")
            onErrorCallback?.invoke("Cannot send carbons enable: Connection closed")
            stream.state = StreamState.NOT_CONNECTING
            return@withContext false
        }
        try {
            val id = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
            val carbonsStanza = """
                <iq type="set" to="$jid" id="$id">
                    <enable xmlns="urn:xmpp:carbons:2"/>
                </iq>
            """.trimIndent()
            if (stream.socket?.write(carbonsStanza) == true) {
                Log.d(TAG, "Sent carbons enable stanza for JID: $jid with id: $id")
                return@withContext true
            } else {
                Log.e(TAG, "Failed to send carbons enable stanza for JID: $jid")
                onErrorCallback?.invoke("Failed to send carbons enable stanza")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending carbons enable for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Carbons enable error: ${e.message}")
            return@withContext false
        }
    }

    fun extractHostFromJid(jid: String): String {
        try {
            val parts = jid.split("@")
            if (parts.size > 1) {
                return parts[1].split("/").first()
            }
            Log.w(TAG, "Invalid JID format: $jid")
            return jid
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting host from JID: ${e.message}", e)
            return jid
        }
    }

    fun unsafeAction(action: (Account, Stream) -> Unit) {
        action(this, stream!!)
    }

    suspend fun action(action: suspend (Account, Stream) -> Unit) {
        stream?.let { stream ->
            if (stream.state == StreamState.CONNECTED && stream.socket?.getSocket()?.isClosed == false) {
                action(this, stream)
            } else {
                Log.w(TAG, "Cannot execute action: Stream is not connected or socket is closed")
            }
        } ?: Log.w(TAG, "Cannot execute action: Stream is null")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun logMessageStorageItems(owner: String, messageIds: List<String>? = null) {
        Log.d(TAG, "Logging MessageStorageItem entries for owner: $owner")
        try {
            realm.query<MessageStorageItem>(
                query = if (messageIds.isNullOrEmpty()) {
                    "owner = $0"
                } else {
                    "owner = $0 AND messageId IN $1"
                },
                owner, messageIds
            ).find().forEach { item ->
                Log.d(
                    TAG,
                    "MessageStorageItem: primary=${item.primary}, messageId=${item.messageId}, owner=${item.owner}, " +
                            "opponent=${item.opponent}, body=${item.body}, date=${item.date}, sentDate=${item.sentDate}, " +
                            "editDate=${item.editDate}, outgoing=${item.outgoing}, conversationType_=${item.conversationType_}, " +
                            "isRead=${item.isRead}, state=${item.state}"
                )
            }
            Log.d(TAG, "Finished logging MessageStorageItem entries")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MessageStorageItem: ${e.message}", e)
        }
    }

    // New: Helper function to parse IQ stanzas (assumed to exist or added for completeness)
    private fun parseIQ(stanza: String): XMPPIQ? {
        try {
            val typeMatch = Regex("""type=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1) ?: return null
            val idMatch = Regex("""id=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
            val fromMatch = Regex("""from=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
            val toMatch = Regex("""to=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
            val error = if (typeMatch == "error") {
                val errorStart = stanza.indexOf("<error")
                if (errorStart != -1) {
                    val errorEnd = stanza.indexOf("</error>", errorStart) + 8
                    stanza.substring(errorStart, errorEnd)
                } else null
            } else null
            val iqStart = stanza.indexOf("<iq")
            val headerEnd = stanza.indexOf(">", iqStart)
            val iqEnd = stanza.lastIndexOf("</iq>")
            val content = if (headerEnd != -1 && iqEnd > headerEnd + 1) stanza.substring(headerEnd + 1, iqEnd).trim() else ""
            val queryNamespace = if (content.isNotEmpty()) {
                val childStart = content.indexOf("<")
                if (childStart != -1) {
                    val childHeaderEnd = content.indexOf(">", childStart)
                    Regex("""xmlns=['"]([^'"]+)['"]""").find(content.substring(childStart, childHeaderEnd + 1))?.groupValues?.get(1)
                } else null
            } else null
            return XMPPIQ(stanza, typeMatch, idMatch, fromMatch, toMatch, error, queryNamespace, content)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing IQ: ${e.message}, stanza=$stanza", e)
            return null
        }
    }
}