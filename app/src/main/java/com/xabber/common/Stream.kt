package com.xabber.common

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.xmpp.auth.DevicesOCRA
import com.xabber.xmpp.device.DeviceStorageItem
import com.xabber.xmpp.dns.DNSResolver
import com.xabber.xmpp.roster.RosterManager
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import io.ktor.network.sockets.isClosed
import io.viascom.nanoid.NanoId

enum class StreamState {
    NOT_CONNECTING,
    STREAM_OPEN,
    START_TLS,
    PROCEED,
    START_AUTH,
    PROCESS_AUTH,
    AUTH_SUCCESS,
    AUTH_FAILED,
    DEVICE_REGISTRATION,
    BINDING,
    CONNECTED
}

@RequiresApi(Build.VERSION_CODES.O)
class Stream {
    @io.realm.kotlin.types.annotations.PrimaryKey
    var jid: String = ""
    var host: String = ""
    var port: Int = 5222
    var remoteAddress: String = ""
    private var socket: Socket? = null
    private val connectionLock = Any()
    private var isConnecting = false
    private val messageCallbackChannel = Channel<String>(Channel.UNLIMITED)
    var state: StreamState = StreamState.NOT_CONNECTING
        set(value) {
            field = value
            Log.d(TAG, "Transitioned to state: $value")
            when (value) {
                StreamState.NOT_CONNECTING -> onNotConnecting()
                StreamState.STREAM_OPEN -> runBlocking(Dispatchers.IO) { onStreamOpen() }
                StreamState.START_TLS -> runBlocking(Dispatchers.IO) { onStartTls() }
                StreamState.PROCEED -> runBlocking(Dispatchers.IO) { onProceed() }
                StreamState.START_AUTH -> runBlocking(Dispatchers.IO) { onStartAuth() }
                StreamState.PROCESS_AUTH -> runBlocking(Dispatchers.IO) { onProcessAuth() }
                StreamState.AUTH_SUCCESS -> runBlocking(Dispatchers.IO) { onAuthSuccess() }
                StreamState.AUTH_FAILED -> runBlocking(Dispatchers.IO) { onAuthFailed() }
                StreamState.DEVICE_REGISTRATION -> runBlocking(Dispatchers.IO) { onDeviceRegistration() }
                StreamState.BINDING -> runBlocking(Dispatchers.IO) { onBinding() }
                StreamState.CONNECTED -> runBlocking(Dispatchers.IO) { onConnected() }
            }
        }
    private val TAG = "Stream"
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3
    private var attemptedPreTlsAuth = false
    private var boundJid: String? = null
    private val deviceModel = Build.MODEL
    private var isDeviceRegistered = false
    private var ocraAuth: DevicesOCRA? = null
    private val realm: Realm by lazy {
        Realm.open(defaultRealmConfig())
    }
    private val rosterManager: RosterManager by lazy { RosterManager(jid, realm) }
    private var onErrorCallback: ((String) -> Unit)? = null
    private val rosterStanzaBuffer = StringBuilder()


    init {
        runBlocking(Dispatchers.IO) {
            checkExistingDevice()
        }
    }

    constructor(jid: String, port: Int? = null) {
        this.jid = jid
        this.port = port ?: 5222
        this.host = extractHostFromJid(jid)
        this.state = StreamState.NOT_CONNECTING
    }

    fun setOnErrorCallback(callback: (String) -> Unit) {
        onErrorCallback = callback
    }

    private suspend fun checkExistingDevice() {
        synchronized(connectionLock) {
            realm.query<DeviceStorageItem>("owner = $0", jid).first().find()?.let { device ->
                if (device.expire > System.currentTimeMillis().toDouble() / 1000) {
                    isDeviceRegistered = true
                    Log.d(TAG, "Found valid existing device for JID: $jid, uid: ${device.uid}, authCounter: ${device.authCounter}")
                } else {
                    Log.d(TAG, "Existing device expired for JID: $jid, uid: ${device.uid}")
                    isDeviceRegistered = false
                }
            } ?: run {
                Log.d(TAG, "No existing device found for JID: $jid")
                isDeviceRegistered = false
            }
        }
    }

    private fun extractHostFromJid(jid: String): String {
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

    fun extractUsernameFromJid(jid: String): String {
        try {
            val parts = jid.split("@")
            return if (parts.size > 1) parts[0] else jid
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting username from JID: ${e.message}", e)
            return jid
        }
    }

    suspend fun connect(): String? = withContext(Dispatchers.IO) {
        synchronized(connectionLock) {
            if (isConnecting) {
                Log.w(TAG, "Connect already in progress for $jid, ignoring")
                return@withContext "Connect already in progress"
            }
            isConnecting = true
        }
        var attempts = 0
        val maxAttempts = 3
        try {
            while (attempts < maxAttempts) {
                attempts++
                Log.d(TAG, "Connection attempt $attempts of $maxAttempts for JID: $jid")
                try {
                    state = StreamState.NOT_CONNECTING
                    val resolver = DNSResolver()
                    val result = resolver.resolveSRV(host)
                    if (result == null) {
                        Log.e(TAG, "DNS resolution failed for host $host")
                        return@withContext "DNS resolution failed"
                    }
                    this@Stream.remoteAddress = result.first
                    this@Stream.port = result.second
                    Log.d(TAG, "Resolved IP: $remoteAddress, Port: $port")
                    socket?.close()
                    socket = Socket(remoteAddress, port)
                    socket?.setMessageCallback { message ->
                        CoroutineScope(Dispatchers.IO).launch {
                            Log.d(TAG, "Received message via callback: $message")
                            messageCallbackChannel.send(message)
                            handleIncomingMessage(message)
                        }
                    }
                    if (socket?.connect(remoteAddress, port) != true) {
                        Log.e(TAG, "Socket connection failed for $remoteAddress:$port")
                        socket?.close()
                        socket = null
                        delay(1000)
                        continue
                    }
                    Log.d(TAG, "Socket connected successfully for $remoteAddress:$port")
                    socket?.initiateXmppStream(socket!!, host, jid)
                    Log.d(TAG, "XMPP stream initiation started, waiting for server response")
                    reconnectAttempts = 0
                    attemptedPreTlsAuth = false
                    return@withContext null
                } catch (e: Exception) {
                    Log.e(TAG, "Error connecting to $host: ${e.message}", e)
                    socket?.close()
                    socket = null
                    state = StreamState.NOT_CONNECTING
                    if (attempts < maxAttempts) {
                        delay(1000)
                        continue
                    }
                    return@withContext "All connection attempts failed: ${e.message}"
                }
            }
            Log.e(TAG, "All connection attempts failed for JID: $jid")
            return@withContext "All connection attempts failed"
        } finally {
            synchronized(connectionLock) {
                isConnecting = false
            }
        }
    }

    private suspend fun handleIncomingMessage(message: String) {
        try {
            Log.d(TAG, "Handling incoming message: $message")
            // Check if the message is roster-related
            if (message.contains("jabber:iq:roster") || message.contains("<item") || message.contains("<group>")) {
                var completeStanza: String? = null
                synchronized(rosterStanzaBuffer) {
                    rosterStanzaBuffer.append(message)
                    Log.d(TAG, "Appended to roster stanza buffer: $message")

                    // Check if the buffer contains a complete IQ stanza
                    val bufferedContent = rosterStanzaBuffer.toString()
                    if (!bufferedContent.trim().startsWith("<iq")) {
                        Log.d(TAG, "Waiting for IQ start, current buffer: $bufferedContent")
                        return
                    }
                    if (!bufferedContent.contains("</iq>")) {
                        Log.d(TAG, "Incomplete roster IQ stanza, waiting for more data. Current buffer: $bufferedContent")
                        return
                    }

                    // Complete IQ stanza received, copy and clear buffer
                    completeStanza = bufferedContent
                    rosterStanzaBuffer.clear()
                    Log.d(TAG, "Cleared roster stanza buffer after copying complete stanza")
                }

                // Process the complete stanza outside the synchronized block
                completeStanza?.let {
                    Log.d(TAG, "Processing complete roster IQ stanza: $it")
                    rosterManager.read(it)
                }
                return
            }

            // Handle non-roster messages as before
            when {
                message.contains("<stream:stream") && !message.contains("<stream:features") -> {
                    Log.d(TAG, "Received stream header, awaiting features")
                }
                message.contains("<stream:features>") -> {
                    Log.d(TAG, "Received stream features")
                    val response = socket?.parseStreamResponse(message)
                    if (response == null) {
                        Log.e(TAG, "Failed to parse stream features")
                        onErrorCallback?.invoke("Failed to parse stream features")
                        state = StreamState.NOT_CONNECTING
                        reconnect()
                        return
                    }
                    if (state == StreamState.NOT_CONNECTING || state == StreamState.PROCEED || state == StreamState.AUTH_SUCCESS) {
                        Log.d(TAG, "Initial stream response received")
                        state = StreamState.STREAM_OPEN
                    }
                    response.features?.let { features ->
                        Log.d(TAG, "Stream features: $features")
                        if (state == StreamState.PROCEED && DevicesOCRA.isSupported(features)) {
                            Log.d(TAG, "DEVICES-OCRA authentication is supported post-TLS")
                            state = StreamState.START_AUTH
                        } else if (!attemptedPreTlsAuth && DevicesOCRA.isSupported(features)) {
                            Log.d(TAG, "DEVICES-OCRA authentication is supported pre-TLS")
                            attemptedPreTlsAuth = true
                            state = StreamState.START_AUTH
                        } else if (state == StreamState.PROCEED && features.mechanisms?.mechanism?.contains("PLAIN") == true) {
                            Log.d(TAG, "PLAIN authentication is supported post-TLS")
                            state = StreamState.START_AUTH
                        } else if (!attemptedPreTlsAuth && features.mechanisms?.mechanism?.contains("PLAIN") == true) {
                            Log.d(TAG, "PLAIN authentication is supported pre-TLS")
                            attemptedPreTlsAuth = true
                            state = StreamState.START_AUTH
                        } else if (features.starttls?.present == true) {
                            val isTlsRequired = message.contains("<required/>")
                            Log.d(TAG, "STARTTLS is supported${if (isTlsRequired) " and required" else ""}")
                            if (isTlsRequired && attemptedPreTlsAuth) {
                                Log.w(TAG, "TLS required after failed pre-TLS auth attempt")
                                attemptedPreTlsAuth = false
                            }
                            state = StreamState.START_TLS
                        } else if (state == StreamState.STREAM_OPEN && features.devices?.present == true) {
                            Log.d(TAG, "Device registration is supported")
                            if (isDeviceRegistered) {
                                Log.d(TAG, "Skipping device registration, already registered for JID: $jid")
                                state = StreamState.BINDING
                            } else {
                                state = StreamState.DEVICE_REGISTRATION
                            }
                        } else {
                            Log.w(TAG, "No supported features found")
                            onErrorCallback?.invoke("No supported authentication features found")
                            state = StreamState.NOT_CONNECTING
                            reconnect()
                        }
                    }
                }
                message.contains("<challenge") && state == StreamState.PROCESS_AUTH && ocraAuth != null -> {
                    Log.d(TAG, "Received OCRA challenge")
                    val success = ocraAuth!!.handleAuthChallenge(message)
                    if (!success) {
                        Log.e(TAG, "OCRA challenge handling failed")
                        onErrorCallback?.invoke("OCRA authentication challenge failed")
                        state = StreamState.AUTH_FAILED
                    }
                }
                message.contains("<success") && state == StreamState.PROCESS_AUTH -> {
                    Log.d(TAG, "Authentication successful")
                    if (ocraAuth != null) {
                        val success = ocraAuth!!.handleAuthResponse(message)
                        if (!success) {
                            Log.e(TAG, "OCRA authentication response handling failed")
                            onErrorCallback?.invoke("OCRA authentication response handling failed")
                            state = StreamState.AUTH_FAILED
                            return
                        }
                    }
                    state = StreamState.AUTH_SUCCESS
                }
                message.contains("<failure") && state == StreamState.PROCESS_AUTH -> {
                    Log.e(TAG, "Authentication failed: $message")
                    val errorTextMatch = Regex("""<text[^>]*>([^<]+)</text>""").find(message)
                    val errorText = errorTextMatch?.groupValues?.get(1) ?: "Unknown authentication error"
                    val errorTypeMatch = Regex("""<([a-z\-]+)\/>""").find(message)
                    val errorType = errorTypeMatch?.groupValues?.get(1) ?: "unknown"
                    val userMessage = when (errorType) {
                        "not-authorized" -> "Authentication failed: $errorText"
                        else -> "Authentication failed: $errorText ($errorType)"
                    }
                    Log.e(TAG, userMessage)
                    onErrorCallback?.invoke(userMessage)
                    state = StreamState.AUTH_FAILED
                }
                message.contains("<proceed") -> {
                    Log.d(TAG, "Received proceed for STARTTLS")
                }
                message.contains("<iq") && state == StreamState.DEVICE_REGISTRATION -> {
                    Log.d(TAG, "Received IQ response for device registration")
                    if (message.contains("type='result'")) {
                        Log.d(TAG, "Device registration successful")
                        val uidMatch = Regex("""device id=['"]([^'"]+)['"]""").find(message)
                        val validationKeyMatch = Regex("""<validation-key>([^<]+)</validation-key>""").find(message)
                        val expireMatch = Regex("""<expire>([^<]+)</expire>""").find(message)
                        val secretMatch = Regex("""<secret>([^<]+)</secret>""").find(message)
                        if (uidMatch != null && validationKeyMatch != null && expireMatch != null && secretMatch != null) {
                            val uid = uidMatch.groupValues[1]
                            val validationKey = validationKeyMatch.groupValues[1]
                            val expire = expireMatch.groupValues[1].toDoubleOrNull() ?: 1.0
                            val secret = secretMatch.groupValues[1]
                            var existingDevice: DeviceStorageItem? = null
                            synchronized(connectionLock) {
                                existingDevice = realm.query<DeviceStorageItem>("uid = $0 AND owner = $1", uid, jid).first().find()
                            }
                            realm.write {
                                if (existingDevice != null) {
                                    findLatest(existingDevice!!)?.apply {
                                        configure(
                                            owner = jid,
                                            uid = uid,
                                            ip = socket?.getSocket()?.remoteAddress?.toString() ?: "",
                                            client = "Xabber-android-device",
                                            device = deviceModel,
                                            expire = expire,
                                            authDate = System.currentTimeMillis().toDouble() / 1000,
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
                                            ip = socket?.getSocket()?.remoteAddress?.toString() ?: "",
                                            client = "Xabber-android",
                                            device = deviceModel,
                                            expire = expire,
                                            authDate = System.currentTimeMillis().toDouble() / 1000,
                                            authCounter = 1,
                                            descr = "Confident Albatross",
                                            secret = secret,
                                            validationKey = validationKey
                                        )
                                    }
                                    copyToRealm(newDevice)
                                    Log.d(TAG, "Created new DeviceStorageItem for uid: $uid, owner: $jid")
                                }
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
                            synchronized(connectionLock) {
                                isDeviceRegistered = true
                            }
                            state = StreamState.BINDING
                        } else {
                            Log.e(TAG, "Failed to parse device registration response: $message")
                            onErrorCallback?.invoke("Failed to parse device registration response")
                            state = StreamState.NOT_CONNECTING
                            reconnect()
                        }
                    } else {
                        Log.e(TAG, "Device registration failed: $message")
                        onErrorCallback?.invoke("Device registration failed")
                        state = StreamState.NOT_CONNECTING
                        reconnect()
                    }
                }
                message.contains("<iq") && state == StreamState.BINDING -> {
                    Log.d(TAG, "Received IQ stanza for binding")
                    val jidMatch = Regex("""<jid>([^<]+)</jid>""").find(message)
                    if (jidMatch != null) {
                        boundJid = jidMatch.groupValues[1]
                        Log.d(TAG, "Resource binding successful, bound JID: $boundJid")
                        state = StreamState.CONNECTED
                    } else {
                        Log.e(TAG, "Binding failed, no JID in response: $message")
                        onErrorCallback?.invoke("Resource binding failed")
                        state = StreamState.NOT_CONNECTING
                        reconnect()
                    }
                }
                message.contains("<iq") && message.contains("type='get'") && message.contains("urn:xmpp:ping") && state == StreamState.CONNECTED -> {
                    Log.d(TAG, "Received server ping request")
                    val idMatch = Regex("""id=['"]([^'"]+)['"]""").find(message)
                    val fromMatch = Regex("""from=['"]([^'"]+)['"]""").find(message)
                    if (idMatch != null && fromMatch != null) {
                        val pingId = idMatch.groupValues[1]
                        val fromJid = fromMatch.groupValues[1]
                        val response = """
                        <iq type='result' id='$pingId' to='$fromJid'/>
                    """.trimIndent()
                        if (socket?.write(response) == true) {
                            Log.d(TAG, "Sent ping response: $response")
                        } else {
                            Log.e(TAG, "Failed to send ping response")
                            onErrorCallback?.invoke("Failed to send ping response")
                            state = StreamState.NOT_CONNECTING
                            reconnect()
                        }
                    } else {
                        Log.w(TAG, "Invalid ping stanza, missing id or from: $message")
                    }
                }
                message.contains("<message") -> {
                    Log.d(TAG, "Received message stanza")
                }
                message.contains("<presence") -> {
                    Log.d(TAG, "Received presence stanza")
                }
                message.contains("<stream:error") -> {
                    Log.e(TAG, "Received stream error: $message")
                    onErrorCallback?.invoke("Stream error occurred")
                    state = StreamState.NOT_CONNECTING
                    reconnect()
                }
                message.contains("</stream:stream>") -> {
                    Log.w(TAG, "Received stream termination")
                    onErrorCallback?.invoke("Connection closed by server")
                    state = StreamState.NOT_CONNECTING
                    reconnect()
                }
                else -> {
                    Log.w(TAG, "Unhandled message: $message")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}", e)
            onErrorCallback?.invoke("Error processing server response: ${e.message}")
            state = StreamState.NOT_CONNECTING
            reconnect()
        }
    }

    private suspend fun reconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(TAG, "Max reconnect attempts ($maxReconnectAttempts) reached for JID: $jid")
            onErrorCallback?.invoke("Maximum reconnection attempts reached")
            state = StreamState.NOT_CONNECTING
            return
        }
        reconnectAttempts++
        Log.d(TAG, "Attempting to reconnect for JID: $jid (attempt $reconnectAttempts/$maxReconnectAttempts)")
        socket?.close()
        socket = null
        rosterStanzaBuffer.clear() // Clear roster buffer on reconnect
        delay(1000)
        val connectError = connect()
        if (connectError == null) {
            Log.d(TAG, "Reconnection successful for JID: $jid")
        } else {
            Log.e(TAG, "Reconnection failed for JID: $jid: $connectError")
            onErrorCallback?.invoke("Reconnection failed: $connectError")
            state = StreamState.NOT_CONNECTING
            reconnect()
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        synchronized(connectionLock) {
            socket = null
            realm.close()
            state = StreamState.NOT_CONNECTING
            messageCallbackChannel.close()
            rosterStanzaBuffer.clear() // Clear roster buffer on close
            reconnectAttempts = 0
            attemptedPreTlsAuth = false
            boundJid = null
            isDeviceRegistered = false
            ocraAuth = null
            Log.d(TAG, "Stream closed for $jid")
        }
        socket?.close()
    }

    fun logout(jid: String) {
        if (this.jid == jid) {
            runBlocking(Dispatchers.IO) {
                close()
            }
        }
    }

    fun getSocket(): Socket? = socket

    open fun onNotConnecting() {}

    open suspend fun onStreamOpen() {}

    open suspend fun onStartTls() {
        if (socket == null || socket?.getSocket()?.isClosed == true) {
            Log.e(TAG, "Cannot initiate STARTTLS: Socket is null or closed")
            onErrorCallback?.invoke("Cannot initiate STARTTLS: Connection closed")
            state = StreamState.NOT_CONNECTING
            reconnect()
            return
        }
        try {
            Log.d(TAG, "Initiating STARTTLS negotiation")
            if (!socket!!.initiateStartTls()) {
                Log.e(TAG, "Failed to initiate STARTTLS")
                onErrorCallback?.invoke("Failed to initiate STARTTLS")
                state = StreamState.NOT_CONNECTING
                reconnect()
                return
            }
            Log.d(TAG, "STARTTLS negotiation successful, preparing for TLS upgrade")
            delay(1000)
            Log.d(TAG, "Upgrading to TLS")
            if (socket?.upgradeToTls() == true) {
                Log.d(TAG, "TLS upgrade successful, initiating new stream")
                state = StreamState.PROCEED
                socket?.initiateXmppStream(socket!!, host, jid)
                Log.d(TAG, "New stream initiated over TLS, awaiting response")
            } else {
                Log.e(TAG, "Failed to upgrade to TLS")
                onErrorCallback?.invoke("Failed to upgrade to TLS")
                state = StreamState.NOT_CONNECTING
                reconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during STARTTLS process: ${e.message}", e)
            onErrorCallback?.invoke("STARTTLS error: ${e.message}")
            state = StreamState.NOT_CONNECTING
            reconnect()
        }
    }

    open suspend fun onProceed() {
        Log.d(TAG, "Awaiting stream features after TLS upgrade for JID: $jid")
    }

    open suspend fun onStartAuth() {
        Log.d(TAG, "Entering onStartAuth for JID: $jid")
        if (socket == null || socket?.getSocket()?.isClosed == true) {
            Log.e(TAG, "Cannot initiate authentication: Socket is null or closed")
            onErrorCallback?.invoke("Cannot initiate authentication: Connection closed")
            state = StreamState.AUTH_FAILED
            reconnect()
            return
        }
        try {
            val response = socket?.parseStreamResponse(messageCallbackChannel.tryReceive().getOrNull() ?: "")
            val features = response?.features
            if (DevicesOCRA.isSupported(features)) {
                Log.d(TAG, "Initiating DEVICES-OCRA authentication for JID: $jid")
                var device: DeviceStorageItem? = null
                synchronized(connectionLock) {
                    device = realm.query<DeviceStorageItem>("owner = $0", jid).first().find()
                }
                device?.let {
                    if (it.secret.isNotEmpty() && it.validationKey.isNotEmpty() && it.uid.isNotEmpty()) {
                        Log.d(TAG, "Using DeviceStorageItem for OCRA: uid=${it.uid}, authCounter=${it.authCounter}")
                        ocraAuth = DevicesOCRA(
                            stream = this,
                            deviceId = it.uid,
                            secret = it.secret,
                            validationKey = it.validationKey,
                            authCounter = it.authCounter,
                            realm = realm
                        )
                        if (ocraAuth?.start() == true) {
                            Log.d(TAG, "DEVICES-OCRA authentication started for JID: $jid")
                            state = StreamState.PROCESS_AUTH
                        } else {
                            Log.e(TAG, "Failed to start DEVICES-OCRA authentication for JID: $jid")
                            onErrorCallback?.invoke("Failed to start DEVICES-OCRA authentication")
                            state = StreamState.AUTH_FAILED
                            reconnect()
                        }
                    } else {
                        Log.e(TAG, "DeviceStorageItem missing required fields for OCRA")
                        onErrorCallback?.invoke("Invalid device data for OCRA authentication")
                        state = StreamState.AUTH_FAILED
                        reconnect()
                    }
                } ?: run {
                    Log.e(TAG, "No valid device found for OCRA authentication for JID: $jid")
                    if (features?.mechanisms?.mechanism?.contains("PLAIN") == true) {
                        Log.d(TAG, "Falling back to PLAIN authentication")
                        startPlainAuth()
                    } else {
                        Log.e(TAG, "No supported authentication mechanisms")
                        onErrorCallback?.invoke("No supported authentication mechanisms")
                        state = StreamState.AUTH_FAILED
                        reconnect()
                    }
                }
            } else if (features?.mechanisms?.mechanism?.contains("PLAIN") == true) {
                Log.d(TAG, "DEVICES-OCRA not supported, using PLAIN authentication")
                startPlainAuth()
            } else {
                Log.e(TAG, "No supported authentication mechanisms found")
                onErrorCallback?.invoke("No supported authentication mechanisms")
                state = StreamState.AUTH_FAILED
                reconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during authentication for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Authentication error: ${e.message}")
            state = StreamState.AUTH_FAILED
            reconnect()
        }
    }

    private suspend fun startPlainAuth() {
        try {
            Log.d(TAG, "Initiating SASL PLAIN authentication for JID: $jid")
            val username = extractUsernameFromJid(jid)
            Log.d(TAG, "Extracted username: $username")
            val authData = socket!!.saslPlainAuth(jid, username)
            Log.d(TAG, "Generated SASL PLAIN auth data")
            val authMessage = """
                <auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>$authData</auth>
            """.trimIndent()
            if (socket?.write(authMessage) == true) {
                Log.d(TAG, "Sent SASL PLAIN auth request for JID: $jid")
                state = StreamState.PROCESS_AUTH
            } else {
                Log.e(TAG, "Failed to send SASL PLAIN auth request for JID: $jid")
                onErrorCallback?.invoke("Failed to send PLAIN authentication request")
                state = StreamState.AUTH_FAILED
                reconnect()
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "SASL PLAIN authentication failed for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("PLAIN authentication failed: ${e.message}")
            state = StreamState.AUTH_FAILED
            reconnect()
        }
    }

    open suspend fun onProcessAuth() {
        Log.d(TAG, "Awaiting authentication response for JID: $jid")
    }

    open suspend fun onAuthSuccess() {
        Log.d(TAG, "Authentication successful for JID: $jid, initiating new stream")
        try {
            socket?.initiateXmppStream(socket!!, host, jid)
            Log.d(TAG, "New stream initiated after auth success for JID: $jid")
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating new stream after auth success: ${e.message}", e)
            onErrorCallback?.invoke("Error initiating stream after authentication")
            state = StreamState.NOT_CONNECTING
            reconnect()
        }
    }

    open suspend fun onAuthFailed() {
        Log.e(TAG, "Authentication failed for JID: $jid")
        onErrorCallback?.invoke("Authentication failed")
        if (!attemptedPreTlsAuth || state == StreamState.PROCEED) {
            Log.e(TAG, "Closing connection due to auth failure")
            close()
        } else {
            Log.d(TAG, "Pre-TLS auth failed, falling back to START_TLS")
            state = StreamState.START_TLS
        }
    }

    open suspend fun onDeviceRegistration() {
        Log.d(TAG, "Entering onDeviceRegistration for JID: $jid")
        if (socket == null || socket?.getSocket()?.isClosed == true) {
            Log.e(TAG, "Cannot perform device registration: Socket is null or closed")
            onErrorCallback?.invoke("Cannot perform device registration: Connection closed")
            state = StreamState.NOT_CONNECTING
            reconnect()
            return
        }
        synchronized(connectionLock) {
            if (isDeviceRegistered) {
                Log.d(TAG, "Device already registered for JID: $jid, skipping registration")
                state = StreamState.BINDING
                return
            }
        }
        try {
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
            if (socket?.write(deviceRequest) == true) {
                Log.d(TAG, "Sent device registration request for JID: $jid")
            } else {
                Log.e(TAG, "Failed to send device registration request for JID: $jid")
                onErrorCallback?.invoke("Failed to send device registration request")
                state = StreamState.NOT_CONNECTING
                reconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during device registration for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Device registration error: ${e.message}")
            state = StreamState.NOT_CONNECTING
            reconnect()
        }
    }

    open suspend fun onBinding() {
        Log.d(TAG, "Entering onBinding for JID: $jid")
        if (socket == null || socket?.getSocket()?.isClosed == true) {
            Log.e(TAG, "Cannot perform resource binding: Socket is null or closed")
            onErrorCallback?.invoke("Cannot perform resource binding: Connection closed")
            state = StreamState.NOT_CONNECTING
            reconnect()
            return
        }
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
            if (socket?.write(bindRequest) == true) {
                Log.d(TAG, "Sent bind request for JID: $jid")
            } else {
                Log.e(TAG, "Failed to send bind request for JID: $jid")
                onErrorCallback?.invoke("Failed to send resource binding request")
                state = StreamState.NOT_CONNECTING
                reconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during resource binding for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Resource binding error: ${e.message}")
            state = StreamState.NOT_CONNECTING
            reconnect()
        }
    }

    open suspend fun onConnected() {
        Log.d(TAG, "Stream connected for JID: $jid, initiating roster request")
        try {
            rosterManager.request(this)
            Log.d(TAG, "Roster request sent for JID: $jid")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending roster request for JID: $jid: ${e.message}", e)
            onErrorCallback?.invoke("Error sending roster request: ${e.message}")
        }
        socket?.scope?.launch {
            while (state == StreamState.CONNECTED && socket?.getSocket()?.isClosed == false) {
                try {
                    val pingJid = boundJid ?: jid
                    if (socket?.sendPing(pingJid) == true) {
                        Log.d(TAG, "Sent ping to $pingJid")
                    } else {
                        Log.w(TAG, "Failed to send ping for JID: $jid")
                        onErrorCallback?.invoke("Failed to send ping")
                        state = StreamState.NOT_CONNECTING
                        reconnect()
                        break
                    }
                    delay(30000)
                } catch (e: Exception) {
                    Log.e(TAG, "Ping error for JID: $jid: ${e.message}", e)
                    onErrorCallback?.invoke("Ping error: ${e.message}")
                    state = StreamState.NOT_CONNECTING
                    reconnect()
                    break
                }
            }
            Log.d(TAG, "Ping loop terminated for $jid")
        }
    }
}