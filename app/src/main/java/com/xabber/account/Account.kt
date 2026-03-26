package com.xabber.account

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.xabber.presentation.XabberApplication
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import com.xabber.presentation.onboarding.util.PasswordStorageHelper
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
import com.xabber.xmpp.auth.redactSecretForLog
import com.xabber.xmpp.avatar.XmppAvatarManager
import com.xabber.xmpp.device.DeviceStorageItem
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.message_archive.MessageArchiveManager
import com.xabber.xmpp.messages.messages_manager.ChatMarkersManager
import com.xabber.xmpp.messages.messages_manager.MessageCommonReceiver
import com.xabber.xmpp.messages.messages_manager.MessageManager
import com.xabber.xmpp.groupchat.GroupchatManager
import com.xabber.xmpp.presence.PresenceManager
import com.xabber.xmpp.roster.RosterManager
import com.xabber.xmpp.core.model.IqStanza
import com.xabber.xmpp.core.model.MessageStanza
import com.xabber.xmpp.core.model.PresenceStanza
import com.xabber.xmpp.core.auth.AccountIdentity
import com.xabber.xmpp.core.auth.DeviceState
import com.xabber.xmpp.core.auth.DevicesOcraAuthStrategy
import com.xabber.xmpp.core.auth.PlainAuthStrategy
import com.xabber.xmpp.core.module.XmppModuleContext
import com.xabber.xmpp.core.module.XmppModuleRegistry
import com.xabber.xmpp.core.module.MamIqModule
import com.xabber.xmpp.core.module.RosterIqModule
import com.xabber.xmpp.core.module.SyncIqModule
import com.xabber.xmpp.core.module.GroupchatIqModule
import com.xabber.xmpp.core.module.AvatarIqModule
import com.xabber.xmpp.core.module.BufferedPresenceModule
import com.xabber.xmpp.core.module.MessageRoutingModule
import com.xabber.xmpp.core.session.XmppSession
import com.xabber.xmpp.core.session.XmppSessionAction
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.util.concurrent.atomic.AtomicBoolean
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
    private val isConnecting = AtomicBoolean(false)
    private val streamMutex = Mutex()
    private val presenceStanzas = mutableListOf<String>() // Class-level buffer for presence stanzas
    private val progressListeners = mutableListOf<ConnectionProgressListener>()
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
    /** True once the account has connected at least once this session; gates the reconnect snackbar. */
    private var wasOnline = false
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
    var groupchatManager: GroupchatManager? = null
    var avatarManager: XmppAvatarManager? = null
    private var moduleRegistry: XmppModuleRegistry? = null
    private var xmppSession: XmppSession? = null

    private val deviceModel = Build.MODEL
    private var isDeviceRegistered = false
    private var ocraAuth: DevicesOCRA? = null
    private var attemptedPreTlsAuth = false
    private var boundJid: String? = null
    private var supportedFeatures: String = ""
    private var rosterRequested = false

    private val rosterStanzaBuffer = StringBuilder()

    private var reconnectJob: Job? = null
    /** Polls for network while offline; cancelled as soon as any reconnect path fires. */
    private var networkWatcherJob: Job? = null
    private var reconnectDelayMs = 3000L          // Initial delay
    private val MAX_RECONNECT_DELAY = 10000L // Cap at 10 seconds
    private val RECONNECT_BACKOFF_MULTIPLIER = 2L
    private val MAX_RECONNECT_ATTEMPTS = 10 // Optional hard limit
    private var reconnectAttempts = 0
    private val isReconnecting = AtomicBoolean(false)
    /** Stable scope that outlives individual streams and reconnect jobs. */
    private val accountScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rosterMutex = Mutex()
    // New: Buffer for post-registration stanzas (roster, sync, presence)
    private var stanzaBuffer = MutableSharedFlow<StanzaItem>(replay = 0, extraBufferCapacity = 1000)
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
                    StanzaItem.StanzaType.PRESENCE -> {
                        if (item.content.contains("https://xabber.com/protocol/groups")) {
                            groupchatManager?.handlePresence(item.content)
                        } else {
                            presenceManager?.processPresence(item.content)
                        }
                    }
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
                    StanzaItem.StanzaType.PRESENCE -> {
                        if (item.content.contains("https://xabber.com/protocol/groups")) {
                            groupchatManager?.handlePresence(item.content)
                        } else {
                            presenceManager?.processPresence(item.content)
                        }
                    }
                    StanzaItem.StanzaType.OTHER -> Log.d(TAG, "Skipping OTHER")
                }
            }
        }
        Log.d(TAG, "Stanza processing restarted")
    }

    /**
     * @param force When true, cancels any in-progress reconnect and starts fresh.
     *              Used for network-change events where the old connection is stale.
     * @param networkChanged When true, shows a toast indicating the network switch was the cause.
     */
    suspend fun performReconnect(force: Boolean = false, networkChanged: Boolean = false) {
        if (force) {
            // Force: always proceed, cancel whatever is running
            val wasAlready = isReconnecting.getAndSet(true)
            if (wasAlready) {
                Log.w(TAG, "Force-cancelling in-progress reconnect for $jid (network changed)")
                reconnectJob?.cancel()
                reconnectJob = null
                // The cancelled job's finally block may not have run yet, so isConnecting
                // could still be true — reset it explicitly so the new connectStream() can proceed.
                isConnecting.set(false)
            }
        } else {
            // Non-force: bail if already reconnecting
            if (!isReconnecting.compareAndSet(false, true)) {
                Log.w(TAG, "performReconnect already in progress for $jid, ignoring duplicate call")
                return
            }
            // Check network before tearing down the existing connection (non-force only)
            if (!isNetworkAvailable()) {
                Log.w(TAG, "performReconnect: no network available, skipping teardown for $jid")
                // Block any pending action()/unsafeAction() calls while we are offline,
                // then close the dead stream so stream==null guard also triggers.
                AccountManager.connectingAccounts.add(jid)
                Log.d(TAG, "connectingAccounts ADD (no network) $jid, set=${AccountManager.connectingAccounts}")
                isReconnecting.set(false)
                reconnectJob?.cancel()
                closeStream()
                // Fallback watcher: on devices where onAvailable/onCapabilitiesChanged don't
                // fire reliably (e.g. Nokia T20 / MediaTek), poll until the network is back
                // and then kick off a normal reconnect.  The watcher is cancelled automatically
                // once any other reconnect path (onAvailable, onCapabilitiesChanged) fires.
                networkWatcherJob?.cancel()
                networkWatcherJob = accountScope.launch {
                    var poll = 0
                    while (isActive) {
                        delay(3000)
                        poll++
                        if (!isNetworkAvailable()) {
                            Log.d(TAG, "Offline watcher poll=$poll: no network yet for $jid")
                            continue
                        }
                        Log.d(TAG, "Offline watcher poll=$poll: network back for $jid — triggering reconnect")
                        // Launch in a sibling coroutine so the watcher job is not
                        // self-cancelled when performReconnect() cancels reconnectJob.
                        accountScope.launch { performReconnect() }
                        return@launch
                    }
                }
                return
            }
        }

        try {
            // Cancel any offline watcher — a real reconnect attempt is now in flight.
            networkWatcherJob?.cancel()
            networkWatcherJob = null

            reconnectJob?.cancel()
            reconnectJob = null
            // Reset isConnecting in case connectStream() is mid-flight (e.g. stuck in TCP handshake)
            // so the new attempt below is not silently rejected.
            isConnecting.set(false)

            // Neutralise the error callback on the OLD stream before teardown,
            // so any delayed socket error from the old connection cannot trigger
            // another spurious performReconnect() after we've already started one.
            stream?.setOnSocketReadLoopError(null)

            // Полностью закрываем текущий stream
            closeStream()

            // Reset auth/binding flags AFTER the stream is dead
            rosterRequested = false
            attemptedPreTlsAuth = false
            bindingCompleted = false
            boundJid = null

            goOffline()
            // Reset delay/attempt counters without cancelling reconnectJob (it's already null above)
            reconnectAttempts = 0
            reconnectDelayMs = 3000L
            delay(200)

            // Launch reconnect loop under accountScope so it is tracked and bounded
            reconnectJob = accountScope.launch {
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
                AccountManager.connectingAccounts.remove(jid)
                Log.d(TAG, "connectingAccounts REMOVE (max attempts) $jid, set=${AccountManager.connectingAccounts}")
                isReconnecting.set(false)
                AccountManager.setReconnecting(false)
                Handler(Looper.getMainLooper()).post { showPermanentErrorDialog() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in performReconnect: ${e.message}", e)
            AccountManager.connectingAccounts.remove(jid)
            Log.d(TAG, "connectingAccounts REMOVE (exception) $jid, set=${AccountManager.connectingAccounts}")
            isReconnecting.set(false)
        }
    }

    private suspend fun processRosterStanza(stanza: String, stream: Stream) {
        val batchSize = 10 // Process up to 10 roster stanzas at once
        val rosterStanzas = mutableListOf<String>()
        // Use Mutex (not synchronized) so we suspend the coroutine rather than block the thread
        rosterMutex.withLock {
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
                    val iq = parseIQ(completeStanza)
                    if (iq != null) {
                        try {
                            withTimeout(10_000L) {
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
                            }
                        } catch (e: TimeoutCancellationException) {
                            Log.w(TAG, "Roster stanza DB write timed out, skipping: ${completeStanza.take(100)}")
                        }
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
        // XmppParser guarantees each stanza is a complete <iq>...</iq> — no re-buffering needed.
        try {
            withTimeout(10_000L) {
                syncManager?.read(stanza)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Sync stanza DB write timed out, skipping: ${stanza.take(100)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing sync stanza: ${e.message}", e)
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
            goOffline()
            launchReconnect()
        }
    }

    private fun launchReconnect() {
        if (!isReconnecting.compareAndSet(false, true)) {
            Log.w(TAG, "launchReconnect: reconnect already in progress for $jid")
            return
        }
        reconnectJob?.cancel() // Cancel any previous attempt

        reconnectJob = accountScope.launch {
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

            // All attempts failed
            isReconnecting.set(false)
            AccountManager.setReconnecting(false)
            Handler(Looper.getMainLooper()).post { showPermanentErrorDialog() }
        }
    }

    private fun resetReconnectState() {
        networkWatcherJob?.cancel()
        networkWatcherJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        reconnectDelayMs = 3000L
        isReconnecting.set(false)
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

    // ------------------------------------------------------------------ state helpers

    /** Mark account as offline; signal reconnecting state if we were online before. */
    private fun goOffline() {
        statusMessage.onNext("Offline")
        Log.d(TAG, "goOffline: wasOnline=$wasOnline for $jid")
        if (wasOnline) {
            AccountManager.setReconnecting(true)
        }
    }

    /** Mark account as online; clear reconnecting state so the activity hides the snackbar. */
    private fun goOnline() {
        Log.d(TAG, "goOnline: setting wasOnline=true for $jid")
        wasOnline = true
        statusMessage.onNext("Online")
        AccountManager.setReconnecting(false)
    }

    // ------------------------------------------------------------------

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
            realm.write {
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
                Log.d(
                    TAG,
                    "Device: uid=${redactSecretForLog(device.uid)}, expire=${device.expire}, authCounter=${device.authCounter}, hasSecret=${device.secret.isNotBlank()}, hasValidationKey=${device.validationKey.isNotBlank()}"
                )
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
            messageArchiveManager?.temporaryMessageReceiver = messageReceiver
            messageReceiver?.subscribeReceiver()
            groupchatManager = GroupchatManager(jid)
            avatarManager = XmppAvatarManager(jid)
            xmppSession = XmppSession(
                account = AccountIdentity(
                    jid = jid,
                    username = jid.substringBefore("@", jid),
                ),
                authStrategies = listOf(
                    DevicesOcraAuthStrategy(
                        streamProvider = { requireNotNull(stream) { "Stream is not initialized" } },
                        realmProvider = { realm },
                        deviceStateProvider = { ownerJid -> loadDeviceState(ownerJid) },
                    ),
                    PlainAuthStrategy(
                        passwordProvider = { ownerJid ->
                            PasswordStorageHelper(XabberApplication.applicationContext()).getData(ownerJid)
                        },
                    ),
                ),
            )
            configureLegacyModuleRegistry()

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
        if (!isConnecting.compareAndSet(false, true)) {
            Log.w(TAG, "Connection already in progress for $jid, ignoring")
            return@withContext false
        }
        try {
            closeStream()

            if (!initializeStream()) {
                Log.e(TAG, "Failed to initialize stream for $jid")
                return@withContext false
            }
            val currentStream = stream ?: error("Stream is null after initialization")
            // Register read-loop error callback using accountScope so it is tracked.
            // Any stale callback from a previous stream was already nullified in performReconnect().
            currentStream.setOnSocketReadLoopError {
                Log.e(TAG, "Read loop error - connection lost")
                goOffline()
                accountScope.launch(Dispatchers.IO) {
                    performReconnect()
                }
            }

            val connectError = stream!!.connect()
            if (connectError == null) {
                // Clear stale resource entries from previous sessions
                try {
                    val realm = io.realm.kotlin.Realm.open(defaultRealmConfig())
                    realm.write {
                        val staleResources = query<com.xabber.data_base.models.presences.ResourceStorageItem>(
                            "owner = $0", jid
                        ).find()
                        if (staleResources.isNotEmpty()) {
                            Log.d(TAG, "Clearing ${staleResources.size} stale resources for $jid")
                            delete(staleResources)
                        }
                    }
                    realm.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing stale resources: ${e.message}")
                }
                presenceManager = PresenceManager(jid, stream!!.socket!!)
                AccountManager.connectingAccounts.remove(jid)
                Log.d(TAG, "connectingAccounts REMOVE (connectStream success) $jid, set=${AccountManager.connectingAccounts}")
                Log.d(TAG, "Stream connected for $jid")
                resetReconnectState()
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
            isConnecting.set(false)
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun closeStream() = withContext(Dispatchers.IO) {
        streamMutex.withLock {
            stream?.close()
            stream = null
            presenceManager = null
            // Do NOT call resetReconnectState() here — that would cancel the reconnectJob
            // that may have triggered this closeStream() call, killing the reconnect loop.
            reconnectAttempts = 0
            reconnectDelayMs = 3000L

            rosterManager?.close()
            rosterManager = null
            syncManager?.reset()
            syncManager = null
            messageArchiveManager?.reset()
            messageArchiveManager = null
            chatMarkers?.close()
            chatMarkers = null
            messages?.unsubscribe()
            messages = null
            messageReceiver?.unsubscribeReceiver()
            messageReceiver = null
            groupchatManager?.close()
            groupchatManager = null
            avatarManager?.close()
            avatarManager = null
            moduleRegistry = null
            xmppSession?.resetAuthState()
            xmppSession = null

            statusMessage.onNext("Offline")
            rosterRequested = false
            attemptedPreTlsAuth = false
            bindingCompleted = false
            boundJid = null

            // Reset the stanza pipeline — cancel the old scope and create a fresh buffer.
            // Do NOT call restartStanzaProcessing() here: initializeStream() will call it
            // after managers are created. Starting a collector now would be wasteful (it gets
            // cancelled immediately) and could process stanzas with null managers.
            stanzaProcessingScope.cancel()
            stanzaBuffer = MutableSharedFlow(replay = 0, extraBufferCapacity = 1000)

            Log.d(TAG, "Stream fully closed and cleaned for $jid")
        }
    }

    private fun configureLegacyModuleRegistry() {
        moduleRegistry = XmppModuleRegistry().apply {
            messageArchiveManager?.let { registerIqModule(priority = 100, module = MamIqModule(jid, it)) }
            rosterManager?.let { registerIqModule(priority = 90, module = RosterIqModule(jid, it)) }
            registerIqModule(
                priority = 80,
                module = SyncIqModule(
                    owner = jid,
                    jid = jid,
                    ackWriter = { xml -> withContext(Dispatchers.IO) { stream?.socket?.write(xml) == true } },
                    emitSyncStanza = { raw, activeStream ->
                        stanzaBuffer.emit(StanzaItem(StanzaItem.StanzaType.SYNC, raw, activeStream))
                    },
                ),
            )
            groupchatManager?.let {
                registerIqModule(
                    priority = 70,
                    module = GroupchatIqModule(
                        owner = jid,
                        jid = jid,
                        groupchatManager = it,
                        ackWriter = { xml -> withContext(Dispatchers.IO) { stream?.socket?.write(xml) == true } },
                    ),
                )
            }
            avatarManager?.let { registerIqModule(priority = 60, module = AvatarIqModule(jid, it)) }

            registerIqModule(priority = 50) { stanza, context ->
                val iq = stanza.value
                if (iq.type != "get" || iq.queryNamespace != "urn:xmpp:ping" || context.stream.state != StreamState.CONNECTED) {
                    return@registerIqModule false
                }
                val pingId = iq.id ?: return@registerIqModule false
                val fromJid = iq.from ?: return@registerIqModule false
                val response = "<iq type='result' id='$pingId' to='$fromJid'/>"
                withContext(Dispatchers.IO) {
                    if (context.stream.socket?.write(response) == true) {
                        true
                    } else {
                        Log.e(TAG, "Failed to send ping response")
                        onErrorCallback?.invoke("Failed to send ping response")
                        context.stream.state = StreamState.NOT_CONNECTING
                        false
                    }
                }
            }

            registerIqModule(priority = 40) { stanza, context ->
                val iq = stanza.value
                if (iq.type != "get" || iq.queryNamespace != "http://jabber.org/protocol/disco#info") {
                    return@registerIqModule false
                }
                val discoId = iq.id ?: return@registerIqModule false
                val fromJid = iq.from ?: return@registerIqModule false
                val toJid = iq.to ?: jid
                val response = buildDiscoInfoResponse(discoId, fromJid, toJid)
                withContext(Dispatchers.IO) {
                    if (context.stream.socket?.write(response) == true) {
                        true
                    } else {
                        Log.e(TAG, "Failed to send disco#info response")
                        false
                    }
                }
            }

            registerIqModule(priority = 10) { stanza, _ ->
                val iq = stanza.value
                iq.type == "result" && (iq.queryContent.isNullOrBlank())
            }

            registerPresenceModule(
                priority = 100,
                module = BufferedPresenceModule(
                    owner = jid,
                    emitPresence = { raw, activeStream ->
                        stanzaBuffer.emit(StanzaItem(StanzaItem.StanzaType.PRESENCE, raw, activeStream))
                    },
                    presenceManager = { presenceManager },
                    groupchatManager = { groupchatManager },
                ),
            )
            registerMessageModule(
                priority = 80,
                module = MessageRoutingModule(
                    owner = jid,
                    chatMarkersManager = chatMarkers,
                    avatarManager = avatarManager,
                    messageReceiver = messageReceiver,
                    groupchatManager = groupchatManager,
                ),
            )
        }
    }

    private suspend fun loadDeviceState(ownerJid: String): DeviceState? {
        val device = realm.query<DeviceStorageItem>("owner = $0", ownerJid).first().find() ?: return null
        if (device.uid.isBlank() || device.secret.isBlank() || device.validationKey.isBlank()) {
            return null
        }
        return DeviceState(
            deviceId = device.uid,
            secret = device.secret,
            validationKey = device.validationKey,
            authCounter = device.authCounter,
        )
    }

    private suspend fun applySessionAction(action: XmppSessionAction, stream: Stream): Boolean {
        return when (action) {
            XmppSessionAction.NoOp -> false
            XmppSessionAction.RestartStream -> {
                stream.state = StreamState.AUTH_SUCCESS
                true
            }
            is XmppSessionAction.TransitionTo -> {
                stream.state = action.state
                true
            }
            is XmppSessionAction.SendAndTransition -> {
                withContext(Dispatchers.IO) {
                    if (stream.socket?.write(action.xml) == true) {
                        stream.state = action.state
                        true
                    } else {
                        Log.e(TAG, "Failed to write session action stanza")
                        onErrorCallback?.invoke("Failed to write XMPP session stanza")
                        stream.state = StreamState.NOT_CONNECTING
                        false
                    }
                }
            }
            is XmppSessionAction.Fail -> {
                Log.e(TAG, action.message)
                onErrorCallback?.invoke(action.message)
                stream.state = action.state
                true
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun didReceiveIQ(iq: XMPPIQ, stream: Stream): Boolean {

        try {
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

            val registry = moduleRegistry
            if (registry != null && registry.dispatch(IqStanza(iq), XmppModuleContext(stream))) {
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
        val registry = moduleRegistry
        if (registry != null) {
            return registry.dispatch(PresenceStanza(presence), XmppModuleContext(stream))
        }
        if (stream.state == StreamState.CONNECTED || stream.state == StreamState.BINDING) {
            stanzaBuffer.emit(StanzaItem(StanzaItem.StanzaType.PRESENCE, presence, stream))
            return true
        }
        if (presence.contains("https://xabber.com/protocol/groups")) {
            return groupchatManager?.handlePresence(presence) ?: false
        }
        return presenceManager?.processPresence(presence) ?: false
    }

    override fun didReceiveStreamHeader(header: String, stream: Stream): Boolean {
        Log.d(TAG, "Received stream header, awaiting features")
        return true
    }

    override fun didReceiveStreamFeatures(features: String, stream: Stream): Boolean {
        try {
            Log.d(TAG, "Received stream features: $features")
            supportedFeatures = features
            val syncSupported = features.contains("xabber.com/protocol/synchronization")
            stanzaProcessingScope.launch {
                realm.write {
                    val account = query<AccountStorageItem>("jid = $0", jid).first().find()
                    if (account != null && account.clientSyncSupport != syncSupported) {
                        findLatest(account)?.clientSyncSupport = syncSupported
                        Log.d(TAG, "Updated AccountStorageItem clientSyncSupport to $syncSupported for JID: $jid")
                    }
                }
            }
            val response = stream.socket?.parseStreamResponse(features)
            if (response == null) {
                Log.e(TAG, "Failed to parse stream features")
                onErrorCallback?.invoke("Failed to parse stream features")
                stream.state = StreamState.NOT_CONNECTING
                return false
            }
            // Capture whether we are post-TLS BEFORE changing the state to STREAM_OPEN.
            // The PROCEED checks below must use this flag, not stream.state, because
            // state is updated to STREAM_OPEN on the very next line.
            val isPostTls = (stream.state == StreamState.PROCEED)
            if (stream.state == StreamState.NOT_CONNECTING || stream.state == StreamState.PROCEED || stream.state == StreamState.AUTH_SUCCESS) {
                Log.d(TAG, "Initial stream response received")
                stream.state = StreamState.STREAM_OPEN
            }
            response.features?.let { feat ->
                Log.d(TAG, "Stream features: $feat")
            }
            val session = xmppSession ?: return false
            val action = runBlocking(Dispatchers.IO) {
                session.handleStreamFeatures(
                    raw = features,
                    currentState = if (isPostTls) StreamState.PROCEED else stream.state,
                    isDeviceRegistered = isDeviceRegistered,
                    deviceState = loadDeviceState(jid),
                )
            }
            return runBlocking(Dispatchers.IO) { applySessionAction(action, stream) }
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
            if (stream.state == StreamState.PROCESS_AUTH) {
                val session = xmppSession ?: return false
                return applySessionAction(session.handleChallenge(challenge), stream)
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
                val session = xmppSession ?: return false
                return applySessionAction(session.handleSuccess(success), stream)
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
                val session = xmppSession ?: return false
                return runBlocking(Dispatchers.IO) {
                    applySessionAction(session.handleFailure(failure), stream)
                }
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
        moduleRegistry?.dispatch(MessageStanza(message), XmppModuleContext(stream))
    }

        override suspend fun streamDidConnect(stream: Stream): Boolean {
            // Start keepalive now that the stream is fully authenticated and bound.
            // Starting it earlier (at TCP connect) causes pings during handshake → not-authorized.
            stream.socket?.startKeepAlive()

            accountScope.launch {
                // If a reconnect happened while we were waiting, this stream is stale — bail out.
                // Using referential equality so we check the exact Stream object, not just JID.
                if (this@Account.stream !== stream) {
                    Log.w(TAG, "streamDidConnect: stale stream for $jid, ignoring")
                    return@launch
                }

                goOnline()
                presenceManager?.sendInitialPresence()

                delay(200)

                // Re-check after the delay: a reconnect could have replaced the stream.
                if (this@Account.stream !== stream) {
                    Log.w(TAG, "streamDidConnect: stream replaced during delay for $jid, aborting data load")
                    return@launch
                }

                if (!rosterRequested) {
                    rosterManager?.request(stream)
                    rosterRequested = true
                }
                streamCarbonsSend(stream)
                streamSyncRequest(stream)

                // Request own member IDs for groups where we don't know them yet
                delay(500)
                if (this@Account.stream !== stream) return@launch
                groupchatManager?.requestSelfIdsForAllGroups(stream)
            }
            return true
        }

    override suspend fun streamBinding(stream: Stream): Boolean {
        if (bindingCompleted) {
            Log.w(TAG, "Binding already completed for $jid, ignoring duplicate request")
            return true
        }
        bindingCompleted = true
        val bindRequest = xmppSession?.buildBindRequest() ?: return false
        return withContext(Dispatchers.IO) {
            if (stream.socket?.write(bindRequest) == true) {
                Log.d(TAG, "Sent bind request for JID: $jid")
                true
            } else {
                Log.e(TAG, "Failed to send bind request for JID: $jid")
                bindingCompleted = false
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
        if (xmppSession?.shouldFallbackToTlsAfterAuthFailure() == true) {
            Log.d(TAG, "Pre-TLS auth failed, falling back to START_TLS")
            stream.state = StreamState.START_TLS
        } else {
            Log.e(TAG, "Closing connection due to auth failure")
            closeStream()
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
            // Use already-parsed features from didReceiveStreamFeatures(), not the channel
            // (the channel may already be consumed by the stream handler)
            val response = stream.socket?.parseStreamResponse(supportedFeatures)
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
                Log.d(TAG, "TLS upgrade successful, stream header sent by upgradeToTls()")
                stream.state = StreamState.PROCEED
                // upgradeToTls() sends <stream:stream> via write() → wrapAndSendTls() and
                // starts the TLS read loop. The server's response arrives via messageCallback
                // → handleIncomingStanza → didReceiveStreamFeatures asynchronously.
                // Do NOT call initiateXmppStream() here — that would send a duplicate header.
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
            // Bail out if this is a stale stream (a reconnect replaced it).
            // This prevents sending requests on a closed socket and avoids corrupting
            // the new stream's state by accident.
            if (this.stream !== stream) {
                Log.w(TAG, "streamSyncRequest: stale stream for $jid, ignoring")
                return false
            }
            if (stream.state != StreamState.CONNECTED) {
                Log.w(TAG, "Cannot send sync request: not CONNECTED (state=${stream.state})")
                // Do NOT set stream.state = NOT_CONNECTING here — that would mark a live stream
                // as disconnected, causing all subsequent IQs to be silently dropped.
                return false
            }
            if (stream.socket == null || stream.socket?.getSocket()?.isClosed == true) {
                Log.e(TAG, "Cannot send sync request: socket is null or closed")
                return false
            }
            val sm = this.syncManager ?: run {
                Log.e(TAG, "Cannot send sync request: syncManager is null")
                return false
            }
            sm.sync(stream, boundJid = this.boundJid)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending sync request for $jid: ${e.message}", e)
            // Do NOT change stream.state here — let the socket error propagate naturally.
            return false
        }
    }

    suspend fun muteConversation(chatJid: String, type: ConversationType, muteSeconds: Long) {
        val sm = syncManager ?: run {
            Log.e(TAG, "Cannot mute: syncManager is null")
            return
        }
        val st = stream ?: run {
            Log.e(TAG, "Cannot mute: stream is null")
            return
        }
        sm.muteConversation(st, chatJid, type, muteSeconds)
    }

    suspend fun unmuteConversation(chatJid: String, type: ConversationType) {
        val sm = syncManager ?: run {
            Log.e(TAG, "Cannot unmute: syncManager is null")
            return
        }
        val st = stream ?: run {
            Log.e(TAG, "Cannot unmute: stream is null")
            return
        }
        sm.unmuteConversation(st, chatJid, type)
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
        val s = stream
        if (s == null || jid in AccountManager.connectingAccounts) {
            Log.w(TAG, "unsafeAction() skipped for $jid: ${if (s == null) "stream null" else "account connecting"}")
            return
        }
        action(this, s)
    }

    suspend fun action(action: suspend (Account, Stream) -> Unit) {
        val s = stream
        if (s == null || jid in AccountManager.connectingAccounts) {
            Log.w(TAG, "action() skipped for $jid: ${if (s == null) "stream null" else "account connecting"}")
            return
        }
        withContext(Dispatchers.IO) {
            // Re-check after the context switch: a concurrent closeStream() or
            // performReconnect() may have nulled the stream/managers in the meantime.
            if (jid in AccountManager.connectingAccounts || stream == null) {
                Log.w(TAG, "action() aborted after context switch for $jid")
                return@withContext
            }
            action(this@Account, s)
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

    suspend fun cleanup() {
        reconnectJob?.cancel()
        reconnectJob = null
        isReconnecting.set(false)
        AccountManager.connectingAccounts.remove(jid)
        Log.d(TAG, "connectingAccounts REMOVE (cleanup) $jid, set=${AccountManager.connectingAccounts}")
        stanzaProcessingScope.cancel()
        accountScope.cancel()
        closeStream()
        progressListeners.clear()
        syncManager?.clear()  // Reset sync version
    }
}
