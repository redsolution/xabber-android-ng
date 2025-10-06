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
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope
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

    init {
        if (deviceName.isEmpty()) {
            deviceName = NickGenerator.genRandomNick()
        }
    }

    override fun toString(): String {
        return "Account $jid"
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
            Log.e("Account", "Can't load user $jid from db", e)
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
            Log.d("Account", "Can't update push info for user ${this.jid}", e)
        }
    }

    fun isExist(jid: String): Boolean {
        try {
            val realm = Realm.open(defaultRealmConfig())
            val exists = realm.query<AccountStorageItem>("jid = $0", jid).first().find() != null
            realm.close()
            return exists
        } catch (e: Exception) {
            Log.e("Account", "Error checking account existence for $jid: ${e.message}", e)
            return false
        }
    }

    private suspend fun checkExistingDevice() {
        try {
            val devices = realm.query<DeviceStorageItem>("owner = $0", jid).find()
            Log.d("Account", "Found ${devices.size} devices for JID: $jid")
            devices.forEach { device ->
                Log.d("Account", "Device: uid=${device.uid}, expire=${device.expire}, authCounter=${device.authCounter}, secret=${device.secret.substring(0, 8)}..., validationKey=${device.validationKey.substring(0, 8)}...")
            }
            val validDevice = devices.firstOrNull { it.expire > System.currentTimeMillis().toDouble() / 1000 }
            isDeviceRegistered = validDevice != null
            if (isDeviceRegistered) {
                Log.d("Account", "Valid device found for JID: $jid, uid: ${validDevice?.uid}")
            } else if (devices.isNotEmpty()) {
                Log.w("Account", "Devices found but all expired/invalid for JID: $jid")
            } else {
                Log.d("Account", "No devices found for JID: $jid")
            }
        } catch (e: Exception) {
            Log.e("Account", "Error checking existing devices for JID: $jid: ${e.message}", e)
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
                Log.d("Account", "Stream initialized for $jid with port $port")
                messageReceiver.subscribeReceiver()
            } else {
                Log.w("Account", "Cannot initialize Stream: JID is empty")
                onErrorCallback?.invoke("Cannot initialize connection: Invalid JID")
            }
        } catch (e: Exception) {
            Log.e("Account", "Error initializing Stream for $jid: ${e.message}", e)
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
                    Log.d("Account", "Stream connected for $jid")
                    syncAllChats(it)
                    true
                } else {
                    statusMessage.onNext("Offline")
                    Log.e("Account", "Stream connection failed for $jid: $connectError")
                    onErrorCallback?.invoke(connectError)
                    false
                }
            } ?: run {
                Log.w("Account", "No Stream initialized for $jid")
                onErrorCallback?.invoke("No connection initialized")
                false
            }
        } catch (e: Exception) {
            Log.e("Account", "Error connecting Stream for $jid: ${e.message}", e)
            onErrorCallback?.invoke("Connection error: ${e.message}")
            statusMessage.onNext("Offline")
            false
        }
    }

    private suspend fun syncAllChats(stream: Stream) = withContext(Dispatchers.IO) {
        val realm = Realm.open(defaultRealmConfig())
        try {
            val chats = realm.writeBlocking {
                query<LastChatsStorageItem>("owner = $0", jid).find()
            }
            Log.d("Account", "Found ${chats.size} chats to sync for $jid")
            chats.forEach { chat ->
                launch {
                    val conversationType = ConversationType.fromRaw(chat.conversationType_)
                    try {
                        messageArchiveManager.syncChat(
                            stream = stream,
                            jid = chat.jid,
                            conversationType = conversationType,
                            callback = {
                                Log.d("Account", "Chat history sync completed for jid=${chat.jid}, type=${chat.conversationType_}")
                            }
                        )
                        Log.d("Account", "Initiated sync for chat jid=${chat.jid}, type=${chat.conversationType_}")
                    } catch (e: Exception) {
                        Log.e("Account", "Failed to sync chat jid=${chat.jid}, type=${chat.conversationType_}: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Account", "Error syncing chats for $jid: ${e.message}", e)
        } finally {
            realm.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun closeStream() = withContext(Dispatchers.IO) {
        stream?.close()
        stream = null
        presenceManager = null
        messageReceiver.unsubscribeReceiver()
        statusMessage.onNext("Offline")
        rosterRequested = false
        Log.d("Account", "Stream closed for $jid")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun didReceiveIQ(iq: XMPPIQ, stream: Stream): Boolean {
        try {
            if (iq.type == "get" && iq.queryNamespace == "urn:xmpp:ping" && stream.state == StreamState.CONNECTED) {
                Log.d("Account", "Received server ping request")
                val pingId = iq.id ?: return false
                val fromJid = iq.from ?: return false
                val response = """
                    <iq type='result' id='$pingId' to='$fromJid'/>
                """.trimIndent()
                if (runBlocking { stream.socket?.write(response) } == true) {
                    Log.d("Account", "Sent ping response: $response")
                    return true
                } else {
                    Log.e("Account", "Failed to send ping response")
                    onErrorCallback?.invoke("Failed to send ping response")
                    stream.state = StreamState.NOT_CONNECTING
                    return false
                }
            }

            if (iq.type == "get" && iq.queryNamespace == "http://jabber.org/protocol/disco#info") {
                Log.d("Account", "Received disco#info query from ${iq.from}")
                val discoId = iq.id ?: return false
                val fromJid = iq.from ?: return false
                val toJid = iq.to ?: jid
                val response = buildDiscoInfoResponse(discoId, fromJid, toJid)
                if (runBlocking { stream.socket?.write(response) } == true) {
                    Log.d("Account", "Sent disco#info response to $fromJid")
                    return true
                } else {
                    Log.e("Account", "Failed to send disco#info response")
                    return false
                }
            }

            if (iq.queryNamespace == "jabber:iq:roster" || iq.queryContent?.contains("<item") == true || iq.queryContent?.contains("<group>") == true) {
                var completeStanza: String? = null
                synchronized(rosterStanzaBuffer) {
                    rosterStanzaBuffer.append(iq.raw)
                    Log.d("Account", "Appended to roster stanza buffer: ${iq.raw.substring(0, minOf(iq.raw.length, 200))}...")
                    val bufferedContent = rosterStanzaBuffer.toString()
                    if (!bufferedContent.trim().startsWith("<iq")) {
                        Log.d("Account", "Waiting for IQ start, current buffer: ${bufferedContent.substring(0, minOf(bufferedContent.length, 200))}...")
                        return false
                    }
                    if (!bufferedContent.contains("</iq>")) {
                        Log.d("Account", "Incomplete roster IQ stanza, waiting for more data: ${bufferedContent.substring(0, minOf(bufferedContent.length, 200))}...")
                        return false
                    }
                    completeStanza = bufferedContent
                    rosterStanzaBuffer.clear()
                    Log.d("Account", "Cleared roster stanza buffer after copying complete stanza")
                }
                completeStanza?.let {
                    Log.d("Account", "Processing complete roster IQ stanza: ${it.substring(0, minOf(it.length, 200))}...")
                    rosterManager.read(XMPPIQ(raw = it, type = iq.type, id = iq.id, from = iq.from, to = iq.to, error = iq.error, queryNamespace = iq.queryNamespace, queryContent = iq.queryContent))
                    return true
                }
                return false
            }

            if (iq.queryNamespace == "https://xabber.com/protocol/synchronization") {
                var completeStanza: String? = null
                synchronized(syncStanzaBuffer) {
                    syncStanzaBuffer.append(iq.raw)
                    Log.d("Account", "Appended to sync stanza buffer: ${iq.raw.substring(0, minOf(iq.raw.length, 200))}...")
                    val bufferedContent = syncStanzaBuffer.toString()
                    if (!bufferedContent.contains("<query") || !bufferedContent.contains("https://xabber.com/protocol/synchronization")) {
                        Log.d("Account", "Waiting for query start, current buffer: ${bufferedContent.substring(0, minOf(bufferedContent.length, 200))}...")
                        return false
                    }
                    if (!bufferedContent.contains("</query>")) {
                        Log.d("Account", "Incomplete sync query stanza, waiting for more data: ${bufferedContent.substring(0, minOf(bufferedContent.length, 200))}...")
                        return false
                    }
                    completeStanza = bufferedContent
                    syncStanzaBuffer.clear()
                    Log.d("Account", "Cleared sync stanza buffer after copying complete stanza")
                }
                completeStanza?.let {
                    Log.d("Account", "Processing complete sync query stanza: ${it.substring(0, minOf(it.length, 200))}...")
                    val cleaned = it.replace(Regex("""<iq[^>]*type='result'[^>]*id='ping1'[^>]*/>"""), "").replace(Regex("r\\.boldin='modify'"), "")
                    val iqStart = cleaned.indexOf("<iq")
                    val iqEnd = cleaned.lastIndexOf("</iq>") + 5
                    if (iqStart != -1 && iqEnd != -1 && iqEnd > iqStart) {
                        val iqStanza = cleaned.substring(iqStart, iqEnd)
                        try {
                            syncManager.read(iqStanza)
                            Log.d("Account", "Processed sync response with ClientSynchronizationManager for JID: $jid")
                            syncCompletionChannel.trySend(Unit)
                            return true
                        } catch (e: Exception) {
                            Log.e("Account", "Error parsing sync IQ stanza: ${e.message}", e)
                            return false
                        }
                    } else {
                        Log.e("Account", "Failed to extract complete <iq> stanza from: ${cleaned.substring(0, minOf(cleaned.length, 200))}...")
                        return false
                    }
                }
                return false
            }

            if (iq.queryNamespace == "urn:xmpp:mam:2") {
                Log.d("Account", "Received MAM IQ response: ${iq.raw}...")
                return messageArchiveManager.read(iq.raw, stream)
            }

            if (stream.state == StreamState.DEVICE_REGISTRATION) {
                Log.d("Account", "Received IQ response for device registration")
                if (iq.type == "result") {
                    Log.d("Account", "Device registration successful")
                    val uidMatch = Regex("""device id=['"]([^'"]+)['"]""").find(iq.queryContent ?: "")
                    val validationKeyMatch = Regex("""<validation-key>([^<]+)</validation-key>""").find(iq.queryContent ?: "")
                    val expireMatch = Regex("""<expire>([^<]+)</expire>""").find(iq.queryContent ?: "")
                    val secretMatch = Regex("""<secret>([^<]+)</secret>""").find(iq.queryContent ?: "")
                    if (uidMatch != null && validationKeyMatch != null && expireMatch != null && secretMatch != null) {
                        val uid = uidMatch.groupValues[1]
                        val validationKey = validationKeyMatch.groupValues[1]
                        var expireDuration = expireMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                        if (expireDuration <= 0) {
                            Log.w("Account", "Invalid expire duration from server: $expireDuration - defaulting to 3600s")
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
                                Log.d("Account", "Updated existing DeviceStorageItem for uid: $uid, owner: $jid")
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
                                Log.d("Account", "Created new DeviceStorageItem for uid: $uid, owner: $jid")
                            }
                        }
                        val savedDevice = realm.query<DeviceStorageItem>("uid = $0 AND owner = $1", uid, jid).first().find()
                        if (savedDevice != null) {
                            Log.d("Account", "Confirmed DeviceStorageItem saved: uid=$uid, expire=${savedDevice.expire}, authCounter=${savedDevice.authCounter}")
                            isDeviceRegistered = true
                            stream.state = StreamState.BINDING
                        } else {
                            Log.e("Account", "Failed to confirm DeviceStorageItem save for uid=$uid, owner=$jid - forcing re-registration")
                            isDeviceRegistered = false
                            stream.state = StreamState.DEVICE_REGISTRATION
                            return false
                        }
                        realm.write {
                            val device = query<DeviceStorageItem>("uid = $0 AND owner = $1", uid, jid).first().find()
                            if (device != null) {
                                findLatest(device)?.apply {
                                    authCounter++
                                    Log.d("Account", "Incremented authCounter to 2 for uid: $uid, owner: $jid")
                                }
                            } else {
                                Log.e("Account", "Failed to find device for incrementing authCounter: uid=$uid, owner=$jid")
                            }
                        }
                        isDeviceRegistered = true
                        stream.state = StreamState.BINDING
                        return true
                    } else {
                        Log.e("Account", "Failed to parse device registration response: ${iq.raw}")
                        onErrorCallback?.invoke("Failed to parse device registration response")
                        stream.state = StreamState.NOT_CONNECTING
                        return false
                    }
                } else {
                    Log.e("Account", "Device registration failed: ${iq.raw}")
                    onErrorCallback?.invoke("Device registration failed")
                    stream.state = StreamState.NOT_CONNECTING
                    return false
                }
            }

            if (stream.state == StreamState.BINDING) {
                Log.d("Account", "Received IQ response for binding")
                val jidMatch = Regex("""<jid>([^<]+)</jid>""").find(iq.queryContent ?: "")
                if (jidMatch != null) {
                    boundJid = jidMatch.groupValues[1]
                    Log.d("Account", "Resource binding successful, bound JID: $boundJid")
                    stream.state = StreamState.CONNECTED
                    return true
                } else {
                    Log.e("Account", "Binding failed, no JID in response: ${iq.raw}")
                    onErrorCallback?.invoke("Resource binding failed")
                    stream.state = StreamState.NOT_CONNECTING
                    return false
                }
            }

            Log.w("Account", "Unhandled IQ: ${iq.raw}")
            return false
        } catch (e: Exception) {
            Log.e("Account", "Error handling IQ: ${e.message}", e)
            onErrorCallback?.invoke("Error processing IQ: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
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

    override fun didReceivePresence(presence: String, stream: Stream): Boolean {
        Log.d("Account", "Received presence stanza")
        return presenceManager?.processPresence(presence) ?: run {
            Log.w("Account", "PresenceManager not initialized, skipping presence processing")
            false
        }
    }

    override fun didReceiveStreamHeader(header: String, stream: Stream): Boolean {
        Log.d("Account", "Received stream header, awaiting features")
        return true
    }

    override fun didReceiveStreamFeatures(features: String, stream: Stream): Boolean {
        try {
            Log.d("Account", "Received stream features: $features")
            supportedFeatures = features
            realm.writeBlocking {
                val account = query<AccountStorageItem>("jid = $0", jid).first().find()
                if (account != null && account.clientSyncSupport != true) {
                    findLatest(account)?.clientSyncSupport = true
                    Log.d("Account", "Updated AccountStorageItem clientSyncSupport to true for JID: $jid")
                }
            }
            val response = stream.socket?.parseStreamResponse(features)
            if (response == null) {
                Log.e("Account", "Failed to parse stream features")
                onErrorCallback?.invoke("Failed to parse stream features")
                stream.state = StreamState.NOT_CONNECTING
                return false
            }
            if (stream.state == StreamState.NOT_CONNECTING || stream.state == StreamState.PROCEED || stream.state == StreamState.AUTH_SUCCESS) {
                Log.d("Account", "Initial stream response received")
                stream.state = StreamState.STREAM_OPEN
            }
            response.features?.let { feat ->
                Log.d("Account", "Stream features: $feat")
                if (stream.state == StreamState.PROCEED && DevicesOCRA.isSupported(feat)) {
                    Log.d("Account", "DEVICES-OCRA authentication is supported post-TLS")
                    stream.state = StreamState.START_AUTH
                } else if (!attemptedPreTlsAuth && DevicesOCRA.isSupported(feat)) {
                    Log.d("Account", "DEVICES-OCRA authentication is supported pre-TLS")
                    attemptedPreTlsAuth = true
                    stream.state = StreamState.START_AUTH
                } else if (stream.state == StreamState.PROCEED && feat.mechanisms?.mechanism?.contains("PLAIN") == true) {
                    Log.d("Account", "PLAIN authentication is supported post-TLS")
                    stream.state = StreamState.START_AUTH
                } else if (!attemptedPreTlsAuth && feat.mechanisms?.mechanism?.contains("PLAIN") == true) {
                    Log.d("Account", "PLAIN authentication is supported pre-TLS")
                    attemptedPreTlsAuth = true
                    stream.state = StreamState.START_AUTH
                } else if (feat.starttls?.present == true) {
                    val isTlsRequired = features.contains("<required/>")
                    Log.d("Account", "STARTTLS is supported${if (isTlsRequired) " and required" else ""}")
                    if (isTlsRequired && attemptedPreTlsAuth) {
                        Log.w("Account", "TLS required after failed pre-TLS auth attempt")
                        attemptedPreTlsAuth = false
                    }
                    stream.state = StreamState.START_TLS
                } else if (stream.state == StreamState.STREAM_OPEN && feat.devices?.present == true) {
                    Log.d("Account", "Device registration is supported")
                    if (isDeviceRegistered) {
                        Log.d("Account", "Skipping device registration, already registered for JID: $jid")
                        stream.state = StreamState.BINDING
                    } else {
                        stream.state = StreamState.DEVICE_REGISTRATION
                    }
                } else {
                    Log.w("Account", "No supported features found")
                    onErrorCallback?.invoke("No supported authentication features found")
                    stream.state = StreamState.NOT_CONNECTING
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("Account", "Error handling stream features: ${e.message}", e)
            onErrorCallback?.invoke("Error processing stream features: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun didReceiveChallenge(challenge: String, stream: Stream): Boolean {
        try {
            Log.d("Account", "Received OCRA challenge")
            if (stream.state == StreamState.PROCESS_AUTH && ocraAuth != null) {
                val success = ocraAuth!!.handleAuthChallenge(challenge)
                if (!success) {
                    Log.e("Account", "OCRA challenge handling failed")
                    onErrorCallback?.invoke("OCRA authentication challenge failed")
                    stream.state = StreamState.AUTH_FAILED
                    return false
                }
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e("Account", "Error handling challenge: ${e.message}", e)
            onErrorCallback?.invoke("Error processing challenge: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun didReceiveSuccess(success: String, stream: Stream): Boolean {
        try {
            Log.d("Account", "Authentication successful")
            if (stream.state == StreamState.PROCESS_AUTH) {
                if (ocraAuth != null) {
                    val succ = ocraAuth!!.handleAuthResponse(success)
                    if (!succ) {
                        Log.e("Account", "OCRA authentication response handling failed")
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
            Log.e("Account", "Error handling success: ${e.message}", e)
            onErrorCallback?.invoke("Error processing success: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override fun didReceiveFailure(failure: String, stream: Stream): Boolean {
        try {
            Log.e("Account", "Authentication failed: $failure")
            if (stream.state == StreamState.PROCESS_AUTH) {
                val errorTextMatch = Regex("""<text[^>]*>([^<]+)</text>""").find(failure)
                val errorText = errorTextMatch?.groupValues?.get(1) ?: "Unknown authentication error"
                val errorTypeMatch = Regex("""<([a-z\-]+)\/>""").find(failure)
                val errorType = errorTypeMatch?.groupValues?.get(1) ?: "unknown"
                val userMessage = when (errorType) {
                    "not-authorized" -> "Authentication failed: $errorText"
                    else -> "Authentication failed: $errorText ($errorType)"
                }
                Log.e("Account", userMessage)
                onErrorCallback?.invoke(userMessage)
                stream.state = StreamState.AUTH_FAILED
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e("Account", "Error handling failure: ${e.message}", e)
            onErrorCallback?.invoke("Error processing failure: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override fun didReceiveProceed(proceed: String, stream: Stream): Boolean {
        Log.d("Account", "Received proceed for STARTTLS")
        return true
    }

    override suspend fun didReceiveMessage(message: String, stream: Stream): Boolean {
        Log.d(TAG, "Received message stanza: $message")
        try {
            val xmppMessage = XMPPMessage(message)
            var messageId = xmppMessage.id
            var isChatState = false
            var innerMessageId: String? = null
            var innerFrom: String? = null
            var innerTo: String? = null
            var innerBody: String? = null
            var innerType: String? = null
            var innerLang: String? = null
            var inForwarded = false
            val innerRaw = StringBuilder()

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(message))
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        val namespace = parser.namespace
                        if (tagName == "message" && (namespace == "jabber:client" || namespace.isEmpty())) {
                            if (!inForwarded) {
                                messageId = parser.getAttributeValue(null, "id") ?: "unknown_${System.currentTimeMillis()}"
                                innerRaw.append("<message")
                                for (i in 0 until parser.attributeCount) {
                                    innerRaw.append(" ${parser.getAttributeName(i)}='${parser.getAttributeValue(i)}'")
                                }
                                innerRaw.append(">")
                            } else {
                                innerMessageId = parser.getAttributeValue(null, "id") ?: "unknown_${System.currentTimeMillis()}"
                                innerFrom = parser.getAttributeValue(null, "from")?.trim()
                                innerTo = parser.getAttributeValue(null, "to")?.trim()
                                innerType = parser.getAttributeValue(null, "type")
                                innerLang = parser.getAttributeValue(null, "xml:lang")
                                Log.d(TAG, "Inner message attributes: id=$innerMessageId, from=$innerFrom, to=$innerTo, type=$innerType, lang=$innerLang")
                                innerRaw.append("<message")
                                for (i in 0 until parser.attributeCount) {
                                    innerRaw.append(" ${parser.getAttributeName(i)}='${parser.getAttributeValue(i)}'")
                                }
                                innerRaw.append(">")
                            }
                        } else if (tagName == "forwarded" && namespace == "urn:xmpp:forward:0") {
                            inForwarded = true
                        } else if (tagName in listOf("active", "composing", "inactive", "received", "displayed") && (namespace == "http://jabber.org/protocol/chatstates" || namespace == "urn:xmpp:chat-markers:0")) {
                            isChatState = true
                            innerRaw.append("<$tagName xmlns='$namespace'/>")
                        } else if (tagName == "body" && inForwarded) {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) {
                                innerBody = parser.text.trim()
                                innerRaw.append("<body>${parser.text}</body>")
                            }
                        } else if (inForwarded && namespace != "jabber:client") {
                            innerRaw.append("<${tagName} xmlns='${namespace}'")
                            for (i in 0 until parser.attributeCount) {
                                innerRaw.append(" ${parser.getAttributeName(i)}='${parser.getAttributeValue(i)}'")
                            }
                            innerRaw.append("/>")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name
                        if (tagName == "forwarded" && parser.namespace == "urn:xmpp:forward:0") {
                            inForwarded = false
                        } else if (inForwarded && tagName == "message" && (parser.namespace == "jabber:client" || parser.namespace.isEmpty())) {
                            innerRaw.append("</message>")
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inForwarded) {
                            innerRaw.append(parser.text)
                        }
                    }
                }
                eventType = parser.next()
            }

            messageId = innerMessageId ?: messageId ?: "unknown_${System.currentTimeMillis()}"
            Log.d(TAG, "Processing message: id=$messageId, isChatState=$isChatState, innerFrom=$innerFrom, innerTo=$innerTo, innerBody=$innerBody")

            if (isChatState && innerBody.isNullOrEmpty()) {
                Log.d(TAG, "Skipping chat state notification: id=$messageId")
                return true
            }

            val fromJid = innerFrom ?: xmppMessage.from?.bare()
            val toJid = innerTo ?: xmppMessage.to?.bare()
            val body = innerBody ?: xmppMessage.body
            if (fromJid == null || toJid == null || body == null) {
                Log.w(TAG, "Skipping message with missing attributes: id=$messageId, innerFrom=$innerFrom, innerTo=$innerTo, innerBody=$innerBody, from=${xmppMessage.from?.bare()}, to=${xmppMessage.to?.bare()}, body=${xmppMessage.body}")
                return false
            }

            val opponent = if (toJid != jid) toJid else fromJid
            if (opponent == jid) {
                Log.w(TAG, "Skipping self-directed message: id=$messageId, from=$fromJid, to=$toJid, stanza=$message")
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
                        this.date = parseTimestamp(xmppMessage) ?: System.currentTimeMillis()
                        this.stanza = message
                    }
                    copyToRealm(newTempStanza, UpdatePolicy.ALL)
                }
            }

            val isOutgoing = fromJid == jid
            val state = if (isOutgoing) MessageSendingState.Deliver else MessageSendingState.Sent

            var containerType: String? = null
            var innerMessage: XMPPMessage? = xmppMessage
            if (inForwarded && xmppMessage.element("sent", namespace = "urn:xmpp:carbons:2") != null) {
                containerType = "forwarded"
                Log.d(TAG, "Detected forwarded carbon message for messageId=$messageId")
                innerMessage = XMPPMessage(
                    raw = innerRaw.toString(),
                    type = innerType,
                    id = innerMessageId,
                    from = innerFrom?.let { XMPPJID(fullJID = it) },
                    to = innerTo?.let { XMPPJID(fullJID = it) },
                    lang = innerLang,
                    body = innerBody
                )
                Log.d(TAG, "Extracted inner message for forwarded container: id=${innerMessage.id}, raw=$innerRaw")
            } else if (xmppMessage.element("last-message") != null) {
                containerType = "last-message"
                Log.d(TAG, "Detected last-message container for messageId=$messageId")
            } else if (xmppMessage.element("archived", namespace = "urn:xmpp:mam:tmp") != null) {
                containerType = "archived"
                Log.d(TAG, "Detected archived container for messageId=$messageId")
            } else {
                containerType = "runtime"
                Log.d(TAG, "No specific container found, treating as runtime for messageId=$messageId")
            }

            when (containerType) {
                "archived" -> {
                    Log.d(TAG, "Directing archived message to MessageArchiveManager: id=$messageId")
                    messageArchiveManager.readMessage(message)
                }
                "forwarded" -> {
                    Log.d(TAG, "Directing forwarded (carbon) message to MessageCommonReceiver")
                    messageReceiver.receiveCarbon(xmppMessage)
                }
                "last-message" -> {
                    // Handle last-message if needed
                }
                "runtime" -> {
                    Log.d(TAG, "Directing runtime message to MessageCommonReceiver")
                    messageReceiver.receiveRuntime(xmppMessage)
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
            Log.e(TAG, "Error handling message: ${e.message}, stanza=$message", e)
            stream.state = StreamState.NOT_CONNECTING
            return false
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


    override suspend fun streamDidConnect(stream: Stream): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
            presenceManager?.sendInitialPresence()
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
            if (runBlocking { stream.socket?.write(bindRequest) } == true) {
                Log.d("Account", "Sent bind request for JID: $jid")
                return true
            } else {
                Log.e("Account", "Failed to send bind request for JID: $jid")
                onErrorCallback?.invoke("Failed to send resource binding request")
                stream.state = StreamState.NOT_CONNECTING
                return false
            }
        } catch (e: Exception) {
            Log.e("Account", "Error during resource binding for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Resource binding error: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun streamDeviceRegistration(stream: Stream): Boolean {
        try {
            if (isDeviceRegistered) {
                Log.d("Account", "Device already registered for JID: $jid, skipping registration")
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
            if (runBlocking { stream.socket?.write(deviceRequest) } == true) {
                Log.d("Account", "Sent device registration request for JID: $jid")
                return true
            } else {
                Log.e("Account", "Failed to send device registration request for JID: $jid")
                onErrorCallback?.invoke("Failed to send device registration request")
                stream.state = StreamState.NOT_CONNECTING
                return false
            }
        } catch (e: Exception) {
            Log.e("Account", "Error during device registration for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Device registration error: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun streamAuthFailed(stream: Stream): Boolean {
        Log.e("Account", "Authentication failed for JID: $jid")
        onErrorCallback?.invoke("Authentication failed")
        if (!attemptedPreTlsAuth || stream.state == StreamState.PROCEED) {
            Log.e("Account", "Closing connection due to auth failure")
            runBlocking { closeStream() }
        } else {
            Log.d("Account", "Pre-TLS auth failed, falling back to START_TLS")
            stream.state = StreamState.START_TLS
        }
        return true
    }

    override suspend fun streamAuthSuccess(stream: Stream): Boolean {
        Log.d("Account", "Authentication successful for JID: $jid, initiating new stream")
        try {
            stream.socket?.initiateXmppStream(stream.socket!!, host, jid)
            Log.d("Account", "New stream initiated after auth success for JID: $jid")
            return true
        } catch (e: Exception) {
            Log.e("Account", "Error initiating new stream after auth success: ${e.message}", e)
            onErrorCallback?.invoke("Error initiating stream after authentication")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun streamPlainAuth(stream: Stream): Boolean {
        try {
            Log.d("Account", "Initiating SASL PLAIN authentication for JID: $jid")
            val username = stream.extractUsernameFromJid(jid)
            Log.d("Account", "Extracted username: $username")
            val authData = stream.socket!!.saslPlainAuth(jid, username)
            Log.d("Account", "Generated SASL PLAIN auth data")
            val authMessage = """
                <auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>$authData</auth>
            """.trimIndent()
            if (runBlocking { stream.socket?.write(authMessage) } == true) {
                Log.d("Account", "Sent SASL PLAIN auth request for JID: $jid")
                stream.state = StreamState.PROCESS_AUTH
                return true
            } else {
                Log.e("Account", "Failed to send SASL PLAIN auth request for JID: $jid")
                onErrorCallback?.invoke("Failed to send PLAIN authentication request")
                stream.state = StreamState.AUTH_FAILED
                return false
            }
        } catch (e: IllegalStateException) {
            Log.e("Account", "SASL PLAIN authentication failed for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("PLAIN authentication failed: ${e.message}")
            stream.state = StreamState.AUTH_FAILED
            return false
        }
    }

    override suspend fun streamOCRAAuth(stream: Stream): Boolean = withContext(Dispatchers.IO) {
        Log.d("Account", "Entering streamOCRAAuth for JID: $jid")
        if (stream.socket == null || stream.socket?.getSocket()?.isClosed == true) {
            Log.e("Account", "Cannot initiate authentication: Socket is null or closed")
            onErrorCallback?.invoke("Cannot initiate authentication: Connection closed")
            stream.state = StreamState.AUTH_FAILED
            return@withContext false
        }
        try {
            val response = stream.socket?.parseStreamResponse(stream.messageCallbackChannel.tryReceive().getOrNull() ?: "")
            val features = response?.features
            if (DevicesOCRA.isSupported(features)) {
                Log.d("Account", "Initiating DEVICES-OCRA authentication for JID: $jid")
                var device: DeviceStorageItem? = null
                device = realm.query<DeviceStorageItem>("owner = $0", jid).first().find()
                device?.let {
                    if (it.secret.isNotEmpty() && it.validationKey.isNotEmpty() && it.uid.isNotEmpty()) {
                        Log.d("Account", "Using DeviceStorageItem for OCRA: uid=${it.uid}, authCounter=${it.authCounter}")
                        ocraAuth = DevicesOCRA(
                            stream = stream,
                            deviceId = it.uid,
                            secret = it.secret,
                            validationKey = it.validationKey,
                            authCounter = it.authCounter,
                            realm = realm
                        )
                        if (ocraAuth?.start() == true) {
                            Log.d("Account", "DEVICES-OCRA authentication started for JID: $jid")
                            stream.state = StreamState.PROCESS_AUTH
                            return@withContext true
                        } else {
                            Log.e("Account", "Failed to start DEVICES-OCRA authentication for JID: $jid")
                            onErrorCallback?.invoke("Failed to start DEVICES-OCRA authentication")
                            stream.state = StreamState.AUTH_FAILED
                            return@withContext false
                        }
                    } else {
                        Log.e("Account", "DeviceStorageItem missing required fields for OCRA")
                        onErrorCallback?.invoke("Invalid device data for OCRA authentication")
                        stream.state = StreamState.AUTH_FAILED
                        return@withContext false
                    }
                } ?: run {
                    Log.e("Account", "No valid device found for OCRA authentication for JID: $jid")
                    if (features?.mechanisms?.mechanism?.contains("PLAIN") == true) {
                        Log.d("Account", "Falling back to PLAIN authentication")
                        streamPlainAuth(stream)
                        return@withContext true
                    } else {
                        Log.e("Account", "No supported authentication mechanisms")
                        onErrorCallback?.invoke("No supported authentication mechanisms")
                        stream.state = StreamState.AUTH_FAILED
                        return@withContext false
                    }
                }
            } else if (features?.mechanisms?.mechanism?.contains("PLAIN") == true) {
                Log.d("Account", "DEVICES-OCRA not supported, using PLAIN authentication")
                streamPlainAuth(stream)
                return@withContext true
            } else {
                Log.e("Account", "No supported authentication mechanisms found")
                onErrorCallback?.invoke("No supported authentication mechanisms")
                stream.state = StreamState.AUTH_FAILED
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("Account", "Unexpected error during authentication for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Authentication error: ${e.message}")
            stream.state = StreamState.AUTH_FAILED
            return@withContext false
        }
    }

    override suspend fun streamStartTLS(stream: Stream): Boolean = withContext(Dispatchers.IO) {
        if (stream.socket == null || stream.socket?.getSocket()?.isClosed == true) {
            Log.e("Account", "Cannot initiate STARTTLS: Socket is null or closed")
            onErrorCallback?.invoke("Cannot initiate STARTTLS: Connection closed")
            stream.state = StreamState.NOT_CONNECTING
            return@withContext false
        }
        try {
            Log.d("Account", "Initiating STARTTLS negotiation")
            if (!stream.socket!!.initiateStartTls()) {
                Log.e("Account", "Failed to initiate STARTTLS")
                onErrorCallback?.invoke("Failed to initiate STARTTLS")
                stream.state = StreamState.NOT_CONNECTING
                return@withContext false
            }
            Log.d("Account", "STARTTLS negotiation successful, preparing for TLS upgrade")
            delay(1000)
            Log.d("Account", "Upgrading to TLS")
            if (stream.socket?.upgradeToTls() == true) {
                Log.d("Account", "TLS upgrade successful, initiating new stream")
                stream.state = StreamState.PROCEED
                stream.socket?.initiateXmppStream(stream.socket!!, host, jid)
                Log.d("Account", "New stream initiated over TLS, awaiting response")
                return@withContext true
            } else {
                Log.e("Account", "Failed to upgrade to TLS")
                onErrorCallback?.invoke("Failed to upgrade to TLS")
                stream.state = StreamState.NOT_CONNECTING
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("Account", "Error during STARTTLS process: ${e.message}", e)
            onErrorCallback?.invoke("STARTTLS error: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return@withContext false
        }
    }

    override suspend fun streamSyncRequest(stream: Stream): Boolean {
        try {
            if (stream.state != StreamState.CONNECTED) {
                Log.w("Account", "Cannot send sync request: Stream is not in CONNECTED state, current state: ${stream.state}")
                onErrorCallback?.invoke("Cannot send sync request: Not connected")
                return false
            }
            if (stream.socket == null || stream.socket?.getSocket()?.isClosed == true) {
                Log.e("Account", "Cannot send sync request: Socket is null or closed")
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
            if (stream.socket?.write(syncRequest) == true) {
                Log.d("Account", "Sent sync request for JID: $jid with id: $syncId")
                return true
            } else {
                Log.e("Account", "Failed to send sync request for JID: $jid")
                onErrorCallback?.invoke("Failed to send sync request")
                stream.state = StreamState.NOT_CONNECTING
                return false
            }
        } catch (e: Exception) {
            Log.e("Account", "Error sending sync request for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Sync request error: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun streamCarbonsSend(stream: Stream): Boolean = withContext(Dispatchers.IO) {
        if (stream.state != StreamState.CONNECTED) {
            Log.w("Account", "Cannot send carbons enable: Stream is not in CONNECTED state, current state: ${stream.state}")
            onErrorCallback?.invoke("Cannot send carbons enable: Not connected")
            return@withContext false
        }
        if (stream.socket == null || stream.socket?.getSocket()?.isClosed == true) {
            Log.e("Account", "Cannot send carbons enable: Socket is null or closed")
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
                Log.d("Account", "Sent carbons enable stanza for JID: $jid with id: $id")
                return@withContext true
            } else {
                Log.e("Account", "Failed to send carbons enable stanza for JID: $jid")
                onErrorCallback?.invoke("Failed to send carbons enable stanza")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("Account", "Error sending carbons enable for JID: $jid: ${e.message}", e)
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
            Log.w("Account", "Invalid JID format: $jid")
            return jid
        } catch (e: Exception) {
            Log.e("Account", "Error extracting host from JID: ${e.message}", e)
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
                Log.w("Account", "Cannot execute action: Stream is not connected or socket is closed")
            }
        } ?: Log.w("Account", "Cannot execute action: Stream is null")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun logMessageStorageItems(owner: String, messageIds: List<String>? = null) {
        Log.d("Account", "Logging MessageStorageItem entries for owner: $owner")
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
                    "Account",
                    "MessageStorageItem: primary=${item.primary}, messageId=${item.messageId}, owner=${item.owner}, " +
                            "opponent=${item.opponent}, body=${item.body}, date=${item.date}, sentDate=${item.sentDate}, " +
                            "editDate=${item.editDate}, outgoing=${item.outgoing}, conversationType_=${item.conversationType_}, " +
                            "isRead=${item.isRead}, state=${item.state}"
                )
            }
            Log.d("Account", "Finished logging MessageStorageItem entries")
        } catch (e: Exception) {
            Log.e("Account", "Error querying MessageStorageItem: ${e.message}", e)
        }
    }
}