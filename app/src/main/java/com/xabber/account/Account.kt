package com.xabber.account

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.xabber.presentation.XabberApplication
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.xabber.stream.Stream
import com.xabber.stream.StreamState
import com.xabber.stream.serializers.XMPPIQ
import com.xabber.stream.delegates.XMPPStreamDelegate
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.utils.custom.NickGenerator
import com.xabber.utils.getArchivedMessageContainer
import com.xabber.utils.getCarbonCopyMessageContainer
import com.xabber.utils.getCarbonForwardedMessageContainer
import com.xabber.utils.getForwardedMessage
import com.xabber.utils.getQueryId
import com.xabber.utils.isArchivedMessage
import com.xabber.utils.isCarbonCopy
import com.xabber.utils.isCarbonForwarded
import com.xabber.utils.isForwardedMessage
import com.xabber.utils.parseTimestamp
import com.xabber.xmpp.XEP_0CCC.ClientSynchronizationManager
import com.xabber.xmpp.auth.DevicesOCRA
import com.xabber.xmpp.device.DeviceStorageItem
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.message_archive.MessageArchiveManager
import com.xabber.xmpp.messages.messages_manager.ChatMarkersManager
import com.xabber.xmpp.messages.messages_manager.MessageCommonReceiver
import com.xabber.xmpp.messages.messages_manager.MessageManager
import com.xabber.xmpp.presence.PresenceManager
import com.xabber.xmpp.roster.RosterManager
import io.ktor.network.sockets.isClosed
import io.reactivex.subjects.BehaviorSubject
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.w3c.dom.Node
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.util.Date
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

@RequiresApi(Build.VERSION_CODES.O)
class Account : XMPPStreamDelegate {
    constructor()

    constructor(jid: String) {
        this.jid = jid
    }

    companion object {
        private const val TAG = "Account"
        private val sharedFactory by lazy { DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true } }
    }
    private var bindingCompleted = false
    private var bindingRequestId: String? = null
    private var isConnecting = false
    private val streamMutex = Mutex()
    private val presenceStanzas = mutableListOf<String>() // Class-level buffer for presence stanzas
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

    val realm: Realm by lazy { Realm.Companion.open(defaultRealmConfig()) }
    private var rosterManager: RosterManager? = null
    private var syncManager: ClientSynchronizationManager? = null
    var messageArchiveManager: MessageArchiveManager? = null
    var chatMarkers: ChatMarkersManager? = null
    var messages: MessageManager? = null
    var messageReceiver: MessageCommonReceiver? = null
    var presenceManager: PresenceManager? = null


    private val deviceModel = Build.MODEL
    private var isDeviceRegistered = false
    private var ocraAuth: DevicesOCRA? = null
    private var attemptedPreTlsAuth = false
    private var boundJid: String? = null
    private var supportedFeatures: String = ""
    private var rosterRequested = false

    private val rosterStanzaBuffer = StringBuilder()
    private val syncStanzaBuffer = StringBuilder()
    private val syncCompletionChannel = Channel<Unit>(1)

    private var reconnectJob: Job? = null
    private var reconnectDelayMs = 3000L          // Initial delay
    private val MAX_RECONNECT_DELAY = 60000L // Cap at 60 seconds
    private val RECONNECT_BACKOFF_MULTIPLIER = 2L
    private val MAX_RECONNECT_ATTEMPTS = 10 // Optional hard limit
    private var reconnectAttempts = 0
    // New: Buffer for post-registration stanzas (roster, sync, presence)
    private val stanzaBuffer = MutableSharedFlow<StanzaItem>(replay = 0, extraBufferCapacity = 1000)
    private var stanzaProcessingScope =
        CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())

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
        stanzaProcessingScope.launch {
            stanzaBuffer.collect { item ->
                when (item.type) {
                    StanzaItem.StanzaType.ROSTER -> processRosterStanza(item.content, item.stream)
                    StanzaItem.StanzaType.SYNC -> processSyncStanza(item.content, item.stream)
                    StanzaItem.StanzaType.PRESENCE -> presenceManager!!.processPresence(item.content)
                    StanzaItem.StanzaType.OTHER -> Log.d(TAG, "Skipping OTHER")
                }
            }
        }
    }

    private fun restartStanzaProcessing() {
        // Отменяем старый скоуп
        stanzaProcessingScope.cancel()
        // Создаём новый
        stanzaProcessingScope = CoroutineScope(Dispatchers.IO.limitedParallelism(2) + SupervisorJob())
        stanzaProcessingScope.launch {
            stanzaBuffer.collect { item ->
                when (item.type) {
                    StanzaItem.StanzaType.ROSTER -> processRosterStanza(item.content, item.stream)
                    StanzaItem.StanzaType.SYNC -> processSyncStanza(item.content, item.stream)
                    StanzaItem.StanzaType.PRESENCE -> presenceManager?.processPresence(item.content)
                    StanzaItem.StanzaType.OTHER -> Log.d(TAG, "Skipping OTHER")
                }
            }
        }
        Log.d(TAG, "Stanza processing restarted")
    }

    suspend fun performReconnect() {
        // Отменяем старую попытку переподключения, если она ещё идёт
        ApplicationActivity.currentActivity?.showReconnectingSnackbar()
        reconnectJob?.cancel()
        reconnectJob = null

        // Полностью закрываем текущий stream
        closeStream()
        rosterRequested = false
        attemptedPreTlsAuth = false
        bindingCompleted = false
        boundJid = null

        // Сбрасываем состояние аккаунта
        statusMessage.onNext("Offline")
        resetReconnectState()
        delay(200)
        // Запускаем переподключение с экспоненциальной задержкой
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            reconnectAttempts = 0
            while (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++
                if (!isNetworkAvailable()) {
                    delay(5000)
                    reconnectAttempts-- // не считаем попыткой
                    continue
                }

                delay(reconnectDelayMs)

                if (connectStream()) {
                    Log.d(TAG, "Reconnect successful for $jid")
                    resetReconnectState()
                    return@launch
                }

                reconnectDelayMs = (reconnectDelayMs * RECONNECT_BACKOFF_MULTIPLIER)
                    .coerceAtMost(MAX_RECONNECT_DELAY)
            }
            showPermanentErrorDialog()
        }
    }

    private suspend fun processRosterStanza(stanza: String, stream: Stream) {
        val batchSize = 10 // Process up to 10 roster stanzas at once
        val rosterStanzas = mutableListOf<String>()
        synchronized(rosterStanzaBuffer) {
            rosterStanzaBuffer.append(stanza)
            val bufferedContent = rosterStanzaBuffer.toString()
            if (bufferedContent.trim().startsWith("<iq") && bufferedContent.contains("</iq>")) {
                rosterStanzas.add(bufferedContent)
                rosterStanzaBuffer.clear()
            } else {
                return
            }
        }

        // Batch process roster stanzas
        rosterStanzas.chunked(batchSize).forEach { batch ->
            try {
                batch.forEach { completeStanza ->
                    val iq = parseIQ(completeStanza) // Assume parseIQ is defined elsewhere
                    if (iq != null) {
                        rosterManager?.read(
                            XMPPIQ(
                                raw = completeStanza,
                                type = iq.type,
                                id = iq.id,
                                from = iq.from,
                                to = iq.to,
                                error = iq.error,
                                queryNamespace = iq.queryNamespace,
                                queryContent = iq.queryContent
                            )
                        )
                    } else {
                        Log.w(TAG, "Failed to parse roster IQ stanza: ${completeStanza.take(200)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing roster stanza batch: ${e.message}", e)
            }
        }
    }

    private suspend fun processSyncStanza(stanza: String, stream: Stream) {
        val batchSize = 10 // Process up to 10 sync stanzas at once
        val syncStanzas = mutableListOf<String>()
        synchronized(syncStanzaBuffer) {
            syncStanzaBuffer.append(stanza)
            val bufferedContent = syncStanzaBuffer.toString()
            if (bufferedContent.contains("<query") && bufferedContent.contains("https://xabber.com/protocol/synchronization") && bufferedContent.contains(
                    "</query>"
                )
            ) {
                val cleaned = bufferedContent.replace(
                    Regex("""<iq[^>]*type='result'[^>]*id='ping1'[^>]*/>"""),
                    ""
                ).replace(Regex("r\\.boldin='modify'"), "")
                val iqStart = cleaned.indexOf("<iq")
                val iqEnd = cleaned.lastIndexOf("</iq>") + 5
                if (iqStart != -1 && iqEnd != -1 && iqEnd > iqStart) {
                    syncStanzas.add(cleaned.substring(iqStart, iqEnd))
                    syncStanzaBuffer.clear()
                } else {
                    Log.e(TAG, "Failed to extract complete sync <iq> stanza: ${cleaned.take(200)}")
                    return
                }
            } else {
                return
            }
        }
        syncStanzas.chunked(batchSize).forEach { batch ->
            try {
                batch.forEach { completeStanza ->
                    syncManager?.read(completeStanza)
                    syncCompletionChannel.trySend(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing sync stanza batch: ${e.message}", e)
            }
        }

    }

//    private suspend fun processPresenceStanza(stanza: String, stream: Stream) {
//        synchronized(presenceStanzas) { presenceStanzas.add(stanza) }
//        if (presenceStanzas.size >= 50) {
//            val batch = synchronized(presenceStanzas) {
//                presenceStanzas.take(50).also { presenceStanzas.removeAll(it) }
//            }
//            launch { batch.forEach { presenceManager?.processPresence(it) } }
//        }
//    }

    private fun parseIQ(stanza: String): XMPPIQ? {
        try {
            val typeMatch = Regex("""type=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1) ?: return null
            val idMatch = Regex("""id=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
            val fromMatch = Regex("""from=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
            val toMatch = Regex("""to=['"]([^'"]+)['"]""").find(stanza)?.groupValues?.get(1)
            val iqStart = stanza.indexOf("<iq")
            val headerEnd = stanza.indexOf(">", iqStart)
            val iqEnd = stanza.lastIndexOf("</iq>")
            val content = if (headerEnd != -1 && iqEnd > headerEnd + 1)
                stanza.substring(headerEnd + 1, iqEnd).trim() else ""
            val queryNamespace = Regex("""xmlns=['"]([^'"]+)['"]""")
                .find(content)?.groupValues?.get(1)
            return XMPPIQ(stanza, typeMatch, idMatch, fromMatch, toMatch, null, queryNamespace, content)
        } catch (e: Exception) { return null }
    }

    fun setOnErrorCallback(callback: (String) -> Unit) {
        onErrorCallback = callback
        stream?.setOnErrorCallback { error ->
            onErrorCallback?.invoke(error)
            statusMessage.onNext("Offline")
            launchReconnect()
        }
    }

    private fun launchReconnect() {
        reconnectJob?.cancel() // Cancel any previous attempt

        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            reconnectAttempts = 0
            while (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++
                Log.d(TAG, "Reconnect attempt $reconnectAttempts after ${reconnectDelayMs}ms delay")

                delay(reconnectDelayMs)

                // Attempt reconnection
                val success = connectStream()
                if (success) {
                    Log.d(TAG, "Reconnection successful")
                    resetReconnectState()
                    return@launch
                }

                // Increase delay (exponential backoff, capped)
                reconnectDelayMs = (reconnectDelayMs * RECONNECT_BACKOFF_MULTIPLIER)
                    .coerceAtMost(MAX_RECONNECT_DELAY)
                Log.w(TAG, "Reconnect failed – next attempt in ${reconnectDelayMs}ms")
            }

            // All attempts failed → show dialog on main thread
            withContext(Dispatchers.Main) {
                showPermanentErrorDialog()
            }
        }
    }

    private fun resetReconnectState() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        reconnectDelayMs = 3000L
    }

    private fun showPermanentErrorDialog() {
        val activity = ApplicationActivity.currentActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        AlertDialog.Builder(activity)
            .setTitle("Соединение потеряно")
            .setMessage("Не удалось восстановить соединение после нескольких попыток. Проверьте интернет и попробуйте позже.")
            .setPositiveButton("Повторить") { _, _ ->
                launchReconnect()
            }
            .setNegativeButton("Отмена", null)
            .setCancelable(false)
            .show()
    }

    suspend fun loadAccount() = withContext(Dispatchers.IO) {
        try {
            val realm = Realm.Companion.open(defaultRealmConfig())
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
            val realm = Realm.Companion.open(defaultRealmConfig())
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
            val realm = Realm.Companion.open(defaultRealmConfig())
            val exists = realm.query<AccountStorageItem>("jid = $0", jid).first().find() != null
            realm.close()
            return exists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking account existence for $jid: ${e.message}", e)
            return false
        }
    }

    private fun checkExistingDevice() {
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

    private suspend fun initializeStream(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (jid.isEmpty()) {
                Log.w(TAG, "Cannot initialize Stream: JID is empty")
                onErrorCallback?.invoke("Cannot initialize connection: Invalid JID")
                return@withContext false
            }

            stream?.close()
            stream = Stream(jid, port).apply {
                onErrorCallback?.let { setOnErrorCallback(it) }
                delegate = this@Account
            }
            Log.d(TAG, "Stream initialized for $jid with port $port")

            // Создаём менеджеры ТОЛЬКО после успешного создания стрима
            rosterManager = RosterManager(jid, realm)
            syncManager = ClientSynchronizationManager(jid)
            messageArchiveManager = MessageArchiveManager(jid)
            chatMarkers = ChatMarkersManager(jid)
            messages = MessageManager(jid, activeStream = true)
            messageReceiver = MessageCommonReceiver(jid)
            messageReceiver?.subscribeReceiver()

            restartStanzaProcessing()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Stream for $jid: ${e.message}", e)
            onErrorCallback?.invoke("Error initializing connection: ${e.message}")
            return@withContext false
        }
    }

    fun isConnected(): Boolean {
        val socket = stream?.socket?.getSocket()
        return socket != null && !socket.isClosed
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun connectStream(): Boolean = withContext(Dispatchers.IO) {
        if (isConnecting) {
            Log.w(TAG, "Connection already in progress for $jid, ignoring")
            return@withContext false
        }
        isConnecting = true
        try {
            closeStream()

            if (!initializeStream()) {
                Log.e(TAG, "Failed to initialize stream for $jid")
                return@withContext false
            }
            val currentStream = stream ?: error("Stream is null after initialization")
            // 👇 теперь stream гарантированно не null
            currentStream.setOnSocketReadLoopError {
                Log.e(TAG, "Read loop error - connection lost")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        XabberApplication.applicationContext(),
                        "Соединение потеряно. Попытка переподключения...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                CoroutineScope(Dispatchers.IO).launch {
                    performReconnect()
                }
                statusMessage.onNext("Offline")
            }

            val connectError = stream!!.connect()
            if (connectError == null) {
                presenceManager = PresenceManager(jid, stream!!.socket!!)
                statusMessage.onNext("Online")
                resetReconnectState()
                Log.d(TAG, "Stream connected for $jid")
                ApplicationActivity.currentActivity?.hideReconnectingSnackbar()
                return@withContext true
            } else {
                statusMessage.onNext("Offline")
                Log.e(TAG, "Stream connection failed for $jid: $connectError")
                onErrorCallback?.invoke(connectError)
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error connecting Stream for $jid: ${e.message}", e)
            onErrorCallback?.invoke("Connection error: ${e.message}")
            statusMessage.onNext("Offline")
            return@withContext false
        } finally {
            isConnecting = false
        }

    }

    private suspend fun syncAllChats(stream: Stream) = withContext(Dispatchers.IO) {
        val realm = Realm.Companion.open(defaultRealmConfig())
        try {
            val chats = realm.writeBlocking {
                query<LastChatsStorageItem>("owner = $0", jid).find()
            }
            Log.d(TAG, "Found ${chats.size} chats to sync for $jid")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing chats for $jid: ${e.message}", e)
        } finally {
            realm.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun closeStream() = withContext(Dispatchers.IO) {
        streamMutex.withLock {
            stream?.close()
            stream = null
            presenceManager = null
            resetReconnectState()

            rosterManager?.close()
            rosterManager = null
            syncManager = null
            messageArchiveManager = null
            chatMarkers = null
            messages = null
            messageReceiver?.unsubscribeReceiver()
            messageReceiver = null

            statusMessage.onNext("Offline")
            rosterRequested = false
            attemptedPreTlsAuth = false
            bindingCompleted = false
            boundJid = null

            // Отменяем скоуп – коллектор остановится
            stanzaProcessingScope.cancel()
            Log.d(TAG, "Stream fully closed and cleaned for $jid")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun didReceiveIQ(iq: XMPPIQ, stream: Stream): Boolean {

        try {
            if (iq.queryNamespace == "urn:xmpp:mam:2") {
                return messageArchiveManager!!.read(iq.raw, stream)
            }
            // Buffer roster and sync IQ stanzas post-registration
            if (stream.state == StreamState.CONNECTED || stream.state == StreamState.BINDING) {
                if (iq.type == "result" && iq.queryNamespace == "jabber:iq:roster") {
                    Log.w(TAG, "ROSTER IQ: $iq")
                    return rosterManager!!.read(iq)
                }
                if (iq.queryNamespace == "https://xabber.com/protocol/synchronization") {
                    stanzaBuffer.emit(StanzaItem(StanzaItem.StanzaType.SYNC, iq.raw, stream))
                    return true
                }
            }

            // Handle ping and disco#info synchronously to maintain responsiveness
            if (iq.type == "get" && iq.queryNamespace == "urn:xmpp:ping" && stream.state == StreamState.CONNECTED) {
                val pingId = iq.id ?: return false
                val fromJid = iq.from ?: return false
                val response = """
                    <iq type='result' id='$pingId' to='$fromJid'/>
                """.trimIndent()
                return withContext(Dispatchers.IO) {
                    if (stream.socket?.write(response) == true) {
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
                val discoId = iq.id ?: return false
                val fromJid = iq.from ?: return false
                val toJid = iq.to ?: jid
                val response = buildDiscoInfoResponse(discoId, fromJid, toJid)
                return withContext(Dispatchers.IO) {
                    if (stream.socket?.write(response) == true) {
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
                        var expireDuration = expireMatch.groupValues[1].toLongOrNull() ?: 0L
                        if (expireDuration <= 0) {
                            Log.w(TAG, "Invalid expire duration from server: $expireDuration - defaulting to 3600s")
                            expireDuration = 3600
                        }
                        val secret = secretMatch.groupValues[1]
                        val currentTime = System.currentTimeMillis()
                        val expire = currentTime + expireDuration
                        var existingDevice: DeviceStorageItem? = null
                        existingDevice = realm.query<DeviceStorageItem>("uid = $0 AND owner = $1", uid, jid).first().find()
                        realm.write {
                            if (existingDevice != null) {
                                findLatest(existingDevice)?.apply {
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

            // *** ADD THIS NEW HANDLER ***
            if (iq.type == "result" && iq.queryContent.isNullOrEmpty() && iq.queryNamespace.isNullOrEmpty()) {
                return true
            }

            // Handle other empty results that might have just a namespace but no content
            if (iq.type == "result" && (iq.queryContent.isNullOrEmpty() || iq.queryContent?.trim() == "")) {
                return true
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error handling IQ: ${e.message}", e)
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

    override suspend fun didReceivePresence(presence: String, stream: Stream): Boolean {
        if (stream.state == StreamState.CONNECTED || stream.state == StreamState.BINDING) {
            stanzaBuffer.emit(StanzaItem(StanzaItem.StanzaType.PRESENCE, presence, stream))
            return true
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

    override fun didReceiveStreamFeatures(features: String, stream: Stream): Boolean {
        try {
            Log.d(TAG, "Received stream features: $features")
            supportedFeatures = features
            realm.writeBlocking {
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
                if (stream.state == StreamState.PROCEED && DevicesOCRA.Companion.isSupported(feat)) {
                    Log.d(TAG, "DEVICES-OCRA authentication is supported post-TLS")
                    stream.state = StreamState.START_AUTH
                } else if (!attemptedPreTlsAuth && DevicesOCRA.Companion.isSupported(feat)) {
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

    override suspend fun didReceiveMessage(message: XMPPMessage, stream: Stream) {
        // 1. Always process chat markers first — they can come in any message
        chatMarkers!!.read(message)

        // ────────────────────────────────────────────────────────────────
        //  Important: we determine the *nature* of the message
        //             based on the **outer** container
        // ────────────────────────────────────────────────────────────────

        val isMamResult = message.hasElement("result", "urn:xmpp:mam:2") ||
                message.raw.contains("""<result\b[^>]*xmlns\s*=\s*["']urn:xmpp:mam:2["']""".toRegex(RegexOption.IGNORE_CASE))

        val isMamTmp = message.hasElement("archived", "urn:xmpp:mam:tmp") ||
                message.raw.contains("""<archived\b[^>]*xmlns\s*=\s*["']urn:xmpp:mam:tmp["']""".toRegex(RegexOption.IGNORE_CASE))
        val isCarbon         = message.isCarbonCopy() || message.isCarbonForwarded()
        val isClientSyncLast = message.hasElement("last-message", "https://xabber.com/protocol/synchronization")

        // Extract "real payload" depending on container type
        val payload = when {
            isMamResult      -> message.getArchivedMessageContainer() ?: message
            isMamTmp         -> message  // tmp variant usually doesn't wrap again
            isCarbon         -> message.getCarbonCopyMessageContainer()
                ?: message.getCarbonForwardedMessageContainer()
                ?: message
            else             -> message
        }

        // Very important: skip empty / service messages early
        if (payload.body.isNullOrBlank() && payload.children.isEmpty()) {
            return
        }

        // ────────────────────────────────────────────────────────────────
        //                    Routing based on ORIGIN
        // ────────────────────────────────────────────────────────────────
        when {
            isMamResult || isMamTmp -> {
                // All history — classic MAM + your temporary archived variant
//                Log.w(TAG, "messageReceiver.receiveArchived")
                messageReceiver!!.receiveArchived(payload)
            }

            isCarbon -> {
                Log.w(TAG, "messageReceiver.receiveCarbon")

                messageReceiver!!.receiveCarbon(payload)
            }

            isClientSyncLast -> {
                Log.w(TAG, "messageReceiver.receiveClientSyncRaw")

                messageReceiver!!.receiveClientSyncRaw(payload)
            }

            // Only real live messages should fall here
            else -> {
                Log.w(TAG, "messageReceiver.receiveRuntime")

                messageReceiver!!.receiveRuntime(payload)
            }
        }
    }

        override suspend fun streamDidConnect(stream: Stream): Boolean {
            CoroutineScope(Dispatchers.IO).launch {
                presenceManager?.sendInitialPresence()

                delay(200)

                if (!rosterRequested) {
                    rosterManager?.request(stream)
                    rosterRequested = true
                }
                streamCarbonsSend(stream)
                streamSyncRequest(stream)
            }
            return true
        }

    override suspend fun streamBinding(stream: Stream): Boolean {
        val bindId = NanoId.generateOptimized(9, "-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
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
            val response = stream.socket?.parseStreamResponse(
                stream.messageCallbackChannel.tryReceive().getOrNull() ?: ""
            )
            val features = response?.features
            if (DevicesOCRA.Companion.isSupported(features)) {
                Log.d(TAG, "Initiating DEVICES-OCRA authentication for JID: $jid")
                var device: DeviceStorageItem? = null
                device = realm.query<DeviceStorageItem>("owner = $0", jid).first().find()
                device?.let {
                    if (it.secret.isNotEmpty() && it.validationKey.isNotEmpty() && it.uid.isNotEmpty()) {
                        Log.d(
                            TAG,
                            "Using DeviceStorageItem for OCRA: uid=${it.uid}, authCounter=${it.authCounter}"
                        )
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
            delay(100)
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
            this.syncManager!!.sync(this.stream!!)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending sync request for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Sync request error: ${e.message}")
            stream.state = StreamState.NOT_CONNECTING
            return false
        }
    }

    override suspend fun streamCarbonsSend(stream: Stream): Boolean = withContext(Dispatchers.IO) {

        val id = NanoId.generateOptimized(
            9,
            "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            63,
            16
        )
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

//        if (stream.state != StreamState.CONNECTED) {
//            Log.w(
//                TAG,
//                "Cannot send carbons enable: Stream is not in CONNECTED state, current state: ${stream.state}"
//            )
//            onErrorCallback?.invoke("Cannot send carbons enable: Not connected")
//            return@withContext false
//        }
//        if (stream.socket == null || stream.socket?.getSocket()?.isClosed == true) {
//            Log.e(TAG, "Cannot send carbons enable: Socket is null or closed")
//            onErrorCallback?.invoke("Cannot send carbons enable: Connection closed")
//            stream.state = StreamState.NOT_CONNECTING
//            return@withContext false
//        }
//        try {
//        } catch (e: Exception) {
//            Log.e(TAG, "Error sending carbons enable for JID: $jid: ${e.message}", e)
//            onErrorCallback?.invoke("Carbons enable error: ${e.message}")
//            return@withContext false
//        }
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
        withContext(Dispatchers.IO) {
            action(this@Account, stream!!)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = XabberApplication.applicationContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false

        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }


    private fun MessageArchiveManager.getQueryIds(): Map<String, MessageArchiveManager.CallbackQueueItem> {
        return queryIds
    }
}