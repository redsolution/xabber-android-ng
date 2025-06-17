package com.xabber.common

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.xmpp.device.DeviceStorageItem
import com.xabber.xmpp.dns.DNSResolver
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.annotations.PrimaryKey
import io.viascom.nanoid.NanoId
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import io.ktor.network.sockets.isClosed
import io.realm.kotlin.RealmConfiguration
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

enum class StreamState {
    NOT_CONNECTING,
    STREAM_OPEN,
    START_TLS,
    PROCEED,
    START_AUTH,
    PROCESS_AUTH,
    PROCESS_OCRA_CHALLENGE,
    PROCESS_OCRA_RESPONSE,
    AUTH_SUCCESS,
    AUTH_FAILED,
    DEVICE_REGISTRATION,
    BINDING,
    CONNECTED
}

@RequiresApi(Build.VERSION_CODES.O)
class Stream {
    @PrimaryKey
    var jid: String = ""
    var host: String = ""
    var port: Int = 5222
    var remoteAddress: String = ""
    private var socket: Socket? = null
    private val connectionLock = Any()
    private var isConnecting = false
    private val messageCallbackChannel = Channel<String>(Channel.UNLIMITED)
    private var state: StreamState = StreamState.NOT_CONNECTING
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
                StreamState.PROCESS_OCRA_CHALLENGE -> runBlocking(Dispatchers.IO) { onProcessOcraChallenge() }
                StreamState.PROCESS_OCRA_RESPONSE -> runBlocking(Dispatchers.IO) { onProcessOcraResponse() }
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
    private val realm: Realm by lazy {
        val config = RealmConfiguration.Builder(setOf(DeviceStorageItem::class)).build()
        Realm.open(config)
    }
    private var clientOcraSuit: String = "OCRA-1:HOTP-SHA256-8:QA10"
    private var clientChallengeQuestion: String? = null
    private var deviceId: String? = null
    private var secret: String? = null
    private var validationKey: String? = null
    private var authCounter: Long = 0

    init {
        checkExistingDevice()
    }

    constructor(jid: String, port: Int? = null) {
        this.jid = jid
        this.port = port ?: 5222
        this.host = extractHostFromJid(jid)
        this.state = StreamState.NOT_CONNECTING
    }

    private fun checkExistingDevice() {
        realm.query<DeviceStorageItem>("owner = $0", jid).first().find()?.let { device ->
            if (device.expire > System.currentTimeMillis().toDouble() / 1000) {
                isDeviceRegistered = true
                deviceId = device.uid
                secret = device.secret
                validationKey = device.validationKey
                authCounter = device.authDate.toLong()
                Log.d(TAG, "Found valid existing device for JID: $jid, uid: ${device.uid}")
            } else {
                Log.d(TAG, "Existing device expired for JID: $jid, uid: ${device.uid}")
            }
        } ?: Log.d(TAG, "No existing device found for JID: $jid")
    }

    private fun extractHostFromJid(jid: String): String {
        try {
            val parts = jid.split("@")
            if (parts.size > 1) {
                return parts[1].split("/").first()
            }
            Log.w(TAG, "Invalid JID format: $jid, using JID as host")
            return jid
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting host from JID: ${e.message}", e)
            return jid
        }
    }

    private fun extractUsernameFromJid(jid: String): String {
        try {
            val parts = jid.split("@")
            if (parts.size > 1) {
                return parts[0]
            }
            Log.w(TAG, "Invalid JID format: $jid, using JID as username")
            return jid
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting username from JID: ${e.message}", e)
            return jid
        }
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        synchronized(connectionLock) {
            if (isConnecting) {
                Log.w(TAG, "Connect already in progress for $jid, ignoring")
                return@withContext false
            }
            isConnecting = true
        }
        var attempts = 0
        val maxAttempts = 3
        while (attempts < maxAttempts) {
            attempts++
            Log.d(TAG, "Connection attempt $attempts of $maxAttempts for JID: $jid")
            try {
                state = StreamState.NOT_CONNECTING
                val resolver = DNSResolver()
                val result = resolver.resolveSRV(host)
                if (result == null) {
                    Log.e(TAG, "DNS resolution failed for host $host")
                    return@withContext false
                }
                this@Stream.remoteAddress = result.first
                this@Stream.port = result.second
                Log.d(TAG, "Resolved IP: $remoteAddress, Port: $port")
                socket?.close()
                socket = Socket(remoteAddress, port)
                socket?.setDomain(host)
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
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to $host: ${e.message}", e)
                socket?.close()
                socket = null
                state = StreamState.NOT_CONNECTING
                if (attempts < maxAttempts) {
                    delay(1000)
                    continue
                }
                return@withContext false
            } finally {
                synchronized(connectionLock) {
                    isConnecting = false
                }
            }
        }
        Log.e(TAG, "All connection attempts failed for JID: $jid")
        return@withContext false
    }

    private suspend fun handleIncomingMessage(message: String) {
        try {
            Log.d(TAG, "Handling incoming message: $message")
            when {
                message.contains("<stream:stream") && !message.contains("<stream:features") -> {
                    Log.d(TAG, "Received stream header, awaiting features")
                }
                message.contains("<stream:features>") -> {
                    Log.d(TAG, "Received stream features")
                    val response = socket?.parseStreamResponse(message)
                    if (response == null) {
                        Log.e(TAG, "Failed to parse stream features")
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
                        if (state == StreamState.PROCEED && features.mechanisms?.mechanism?.contains("DEVICES-OCRA") == true) {
                            Log.d(TAG, "DEVICES-OCRA authentication is supported post-TLS")
                            state = StreamState.START_AUTH
                        } else if (!attemptedPreTlsAuth && features.mechanisms?.mechanism?.contains("DEVICES-OCRA") == true) {
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
                            state = StreamState.NOT_CONNECTING
                            reconnect()
                        }
                    }
                }
                message.contains("<challenge") && state == StreamState.PROCESS_AUTH -> {
                    Log.d(TAG, "Received OCRA challenge")
                    state = StreamState.PROCESS_OCRA_CHALLENGE
                }
                message.contains("<success") && (state == StreamState.PROCESS_AUTH || state == StreamState.PROCESS_OCRA_RESPONSE) -> {
                    Log.d(TAG, "Authentication successful (OCRA or PLAIN)")
                    state = StreamState.AUTH_SUCCESS
                }
                message.contains("<failure") && (state == StreamState.PROCESS_AUTH || state == StreamState.PROCESS_OCRA_RESPONSE) -> {
                    Log.e(TAG, "Authentication failed: $message")
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
                            realm.write {
                                val existingDevice = query<DeviceStorageItem>("uid = $0 AND owner = $1", uid, jid).first().find()
                                if (existingDevice != null) {
                                    findLatest(existingDevice)?.apply {
                                        configure(
                                            owner = jid,
                                            uid = uid,
                                            ip = socket?.getSocket()?.remoteAddress?.toString() ?: "",
                                            client = "Xabber-android",
                                            device = "Xabben-android-device",
                                            expire = expire,
                                            authDate = System.currentTimeMillis().toDouble() / 1000,
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
                                            device = "Xabben-android-device",
                                            expire = expire,
                                            authDate = System.currentTimeMillis().toDouble() / 1000,
                                            descr = "Confident Albatross",
                                            secret = secret,
                                            validationKey = validationKey
                                        )
                                    }
                                    copyToRealm(newDevice)
                                    Log.d(TAG, "Created new DeviceStorageItem for uid: $uid, owner: $jid")
                                }
                            }
                            isDeviceRegistered = true
                            deviceId = uid
                            this@Stream.secret = secret
                            this@Stream.validationKey = validationKey
                            state = StreamState.BINDING
                        } else {
                            Log.e(TAG, "Failed to parse device registration response: $message")
                            state = StreamState.NOT_CONNECTING
                            reconnect()
                        }
                    } else {
                        Log.e(TAG, "Device registration failed: $message")
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
                    state = StreamState.NOT_CONNECTING
                    reconnect()
                }
                message.contains("</stream:stream>") -> {
                    Log.w(TAG, "Received stream termination")
                    state = StreamState.NOT_CONNECTING
                    reconnect()
                }
                else -> {
                    Log.w(TAG, "Unhandled message: $message")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}", e)
            state = StreamState.NOT_CONNECTING
            reconnect()
        }
    }

    private suspend fun reconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(TAG, "Max reconnect attempts ($maxReconnectAttempts) reached for JID: $jid")
            state = StreamState.NOT_CONNECTING
            return
        }
        reconnectAttempts++
        Log.d(TAG, "Attempting to reconnect for JID: $jid (attempt $reconnectAttempts/$maxReconnectAttempts)")
        socket?.close()
        socket = null
        delay(1000)
        if (connect()) {
            Log.d(TAG, "Reconnection successful for JID: $jid")
        } else {
            Log.e(TAG, "Reconnection failed for JID: $jid")
            state = StreamState.NOT_CONNECTING
            reconnect()
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        synchronized(connectionLock) {
//            socket?.close()
            socket = null
            realm.close()
            state = StreamState.NOT_CONNECTING
            messageCallbackChannel.close()
            reconnectAttempts = 0
            attemptedPreTlsAuth = false
            boundJid = null
            isDeviceRegistered = false
            clientChallengeQuestion = null
            deviceId = null
            secret = null
            validationKey = null
            authCounter = 0
            Log.d(TAG, "Stream closed for $jid")
        }
    }

    fun logout(jid: String) {
        if (this.jid == jid) {
            runBlocking(Dispatchers.IO) {
                close()
            }
        }
    }

    fun getSocket(): Socket? {
        return socket
    }

    open fun onNotConnecting() {}

    open suspend fun onStreamOpen() {}

    open suspend fun onStartTls() {
        if (socket == null || socket?.getSocket()?.isClosed == true) {
            Log.e(TAG, "Cannot initiate STARTTLS: Socket is null or closed")
            state = StreamState.NOT_CONNECTING
            reconnect()
            return
        }
        try {
            Log.d(TAG, "Initiating STARTTLS negotiation")
            if (!socket!!.initiateStartTls()) {
                Log.e(TAG, "Failed to initiate STARTTLS")
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
                state = StreamState.NOT_CONNECTING
                reconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during STARTTLS process: ${e.message}", e)
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
            state = StreamState.AUTH_FAILED
            reconnect()
            return
        }
        try {
            // Fetch the latest stream features
            val latestMessage = withTimeoutOrNull(5000) {
                messageCallbackChannel.receive()
            } ?: run {
                Log.e(TAG, "No stream features received within timeout")
                state = StreamState.AUTH_FAILED
                reconnect()
                return
            }
            val response = socket?.parseStreamResponse(latestMessage)
            if (response?.features == null) {
                Log.e(TAG, "Failed to parse stream features for authentication")
                state = StreamState.AUTH_FAILED
                reconnect()
                return
            }

            // Prioritize DEVICES-OCRA if supported and device is registered
            if (response.features.mechanisms?.mechanism?.contains("DEVICES-OCRA") == true && isDeviceRegistered) {
                Log.d(TAG, "Initializing DEVICES-OCRA authentication for JID: $jid")
                // Validate OCRA data
                if (deviceId.isNullOrEmpty() || secret.isNullOrEmpty() || validationKey.isNullOrEmpty()) {
                    Log.e(TAG, "Missing OCRA data: deviceId=$deviceId, secret=$secret, validationKey=$validationKey")
                    state = StreamState.AUTH_FAILED
                    reconnect()
                    return
                }
                // Generate client challenge
                clientChallengeQuestion = generateClientChallenge()
                // Construct OCRA initial message
                val username = extractUsernameFromJid(jid)
                val message = "n,,\u0000$username\u0000$deviceId\u0000$clientOcraSuit\u0000$clientChallengeQuestion\u0000$validationKey"
                val base64 = Base64.getEncoder().encodeToString(message.toByteArray(StandardCharsets.UTF_8))
                val authMessage = """
                    <auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='DEVICES-OCRA'>$base64</auth>
                """.trimIndent()
                if (socket?.write(authMessage) == true) {
                    Log.d(TAG, "Sent DEVICES-OCRA auth request for JID: $jid: $authMessage")
                    state = StreamState.PROCESS_AUTH
                } else {
                    Log.e(TAG, "Failed to send DEVICES-OCRA auth request for JID: $jid")
                    state = StreamState.AUTH_FAILED
                    reconnect()
                }
            } else if (response.features.mechanisms?.mechanism?.contains("PLAIN") == true) {
                // Fallback to PLAIN authentication
                Log.d(TAG, "Falling back to SASL PLAIN authentication for JID: $jid")
                val username = extractUsernameFromJid(jid)
                val authData = socket!!.saslPlainAuth(jid, username)
                val authMessage = """
                    <auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>$authData</auth>
                """.trimIndent()
                if (socket?.write(authMessage) == true) {
                    Log.d(TAG, "Sent SASL PLAIN auth request for JID: $jid: $authMessage")
                    state = StreamState.PROCESS_AUTH
                } else {
                    Log.e(TAG, "Failed to send SASL PLAIN auth request for JID: $jid")
                    state = StreamState.AUTH_FAILED
                    reconnect()
                }
            } else {
                Log.e(TAG, "No supported authentication mechanisms found")
                state = StreamState.AUTH_FAILED
                reconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during authentication initialization for JID: $jid: ${e.message}", e)
            state = StreamState.AUTH_FAILED
            reconnect()
        }
    }

    open suspend fun onProcessAuth() {
        Log.d(TAG, "Awaiting authentication response for JID: $jid")
    }

    open suspend fun onProcessOcraChallenge() {
        Log.d(TAG, "Processing OCRA challenge for JID: $jid")
        try {
            val message = messageCallbackChannel.receive()
            if (!message.contains("<challenge")) {
                Log.e(TAG, "Expected challenge, received: $message")
                state = StreamState.AUTH_FAILED
                reconnect()
                return
            }
            val base64Data = message.substringAfter(">").substringBefore("</challenge>")
            val decodedData = Base64.getDecoder().decode(base64Data)
            val serverChallenge = String(decodedData, StandardCharsets.UTF_8).split("\u0000")
            if (serverChallenge.size != 3) {
                Log.e(TAG, "Invalid OCRA challenge format: ${serverChallenge.joinToString()}")
                state = StreamState.AUTH_FAILED
                reconnect()
                return
            }
            val srvResponse = serverChallenge[0]
            val srvOcraSuit = serverChallenge[1]
            val srvChallengeQuestion = serverChallenge[2]
            val srvResponseDecoded = String(Base64.getDecoder().decode(srvResponse), StandardCharsets.UTF_8)

            Log.d(TAG, "Challenge srvResponse: $srvResponse")
            Log.d(TAG, "Challenge srvResponseDecoded: $srvResponseDecoded")
            Log.d(TAG, "Challenge srvOcraSuit: $srvOcraSuit")
            Log.d(TAG, "Challenge srvChallengeQuestion: $srvChallengeQuestion")

            // Verify client challenge
            val clHash = computeHmac(clientOcraSuit, secret!!, clientChallengeQuestion!!)
            val clHotpLength = getHotpLength(clientOcraSuit)
            val isValid = if (clHotpLength == 0) {
                val hashString = Base64.getEncoder().encodeToString(clHash)
                hashString == srvResponse
            } else {
                val pinValue = truncateHash(clHash, clHotpLength)
                val payload = String.format("%0${clHotpLength}d", pinValue)
                payload == srvResponseDecoded
            }

            if (isValid) {
                // Compute server response
                val hash = computeHmac(srvOcraSuit, secret!!, srvChallengeQuestion, authCounter)
                val hotpLength = getHotpLength(srvOcraSuit)
                val response = if (hotpLength == 0) {
                    val base64 = Base64.getEncoder().encodeToString(hash)
                    Base64.getEncoder().encodeToString(base64.toByteArray(StandardCharsets.UTF_8))
                } else {
                    val pinValue = truncateHash(hash, hotpLength)
                    val payload = String.format("%0${hotpLength}d", pinValue)
                    Base64.getEncoder().encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
                }
                val responseMessage = """
                    <response xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>$response</response>
                """.trimIndent()
                if (socket?.write(responseMessage) == true) {
                    Log.d(TAG, "Sent OCRA response for JID: $jid: $responseMessage")
                    state = StreamState.PROCESS_OCRA_RESPONSE
                } else {
                    Log.e(TAG, "Failed to send OCRA response for JID: $jid")
                    state = StreamState.AUTH_FAILED
                    reconnect()
                }
            } else {
                Log.e(TAG, "Client challenge verification failed")
                state = StreamState.AUTH_FAILED
                reconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing OCRA challenge: ${e.message}", e)
            state = StreamState.AUTH_FAILED
            reconnect()
        }
    }

    open suspend fun onProcessOcraResponse() {
        Log.d(TAG, "Awaiting OCRA response for JID: $jid")
    }

    open suspend fun onAuthSuccess() {
        Log.d(TAG, "Authentication successful for JID: $jid, initiating new stream")
        try {
            socket?.initiateXmppStream(socket!!, host, jid)
            Log.d(TAG, "New stream initiated after auth success for JID: $jid, awaiting response")
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating new stream after auth success for JID: $jid: ${e.message}", e)
            state = StreamState.NOT_CONNECTING
            reconnect()
        }
    }

    open suspend fun onAuthFailed() {
        Log.e(TAG, "Authentication failed for JID: $jid")
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
            state = StreamState.NOT_CONNECTING
            reconnect()
            return
        }
        try {
            Log.w(TAG, "D E V I C E $deviceModel")
            val deviceId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
            val deviceRequest = """
                <iq type='set' id='$deviceId'>
                    <register xmlns='https://xabber.com/protocol/devices'>
                        <device xmlns='https://xabber.com/protocol/devices'>
                            <info>$deviceModel</info>
                            <client>Xabber-android</client>
                            <public-label>Confident Albatross</public-label>
                            <type>android</type>
                        </device>
                    </register>
                </iq>
            """.trimIndent()
            if (socket?.write(deviceRequest) == true) {
                Log.d(TAG, "Sent device registration request for JID: $jid: $deviceRequest")
            } else {
                Log.e(TAG, "Failed to send device registration request for JID: $jid")
                state = StreamState.NOT_CONNECTING
                reconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during device registration for JID: $jid: ${e.message}", e)
            state = StreamState.NOT_CONNECTING
            reconnect()
        }
    }

    open suspend fun onBinding() {
        Log.d(TAG, "Entering onBinding for JID: $jid")
        if (socket == null || socket?.getSocket()?.isClosed == true) {
            Log.e(TAG, "Cannot perform resource binding: Socket is null or closed")
            state = StreamState.NOT_CONNECTING
            reconnect()
            return
        }
        try {
            val bindId = NanoId.generateOptimized(9, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)
            val resourceId = NanoId.generateOptimized(8, "0123456789ABCDEF", 63, 16)
            val bindRequest = """
                <iq type='set' id='$bindId'>
                    <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>
                        <resource>xabber-android-$resourceId</resource>
                    </bind>
                </iq>
            """.trimIndent()
            if (socket?.write(bindRequest) == true) {
                Log.d(TAG, "Sent bind request for JID: $jid: $bindRequest")
            } else {
                Log.e(TAG, "Failed to send bind request for JID: $jid")
                state = StreamState.NOT_CONNECTING
                reconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during resource binding for JID: $jid: ${e.message}", e)
            state = StreamState.NOT_CONNECTING
            reconnect()
        }
    }

    open suspend fun onConnected() {
        socket?.scope?.launch {
            while (state == StreamState.CONNECTED && socket?.getSocket()?.isClosed == false) {
                try {
                    val pingJid = boundJid ?: jid
                    if (socket?.sendPing(pingJid) == true) {
                        Log.d(TAG, "Sent ping to $pingJid")
                    } else {
                        Log.w(TAG, "Failed to send ping for JID: $jid")
                        state = StreamState.NOT_CONNECTING
                        reconnect()
                        break
                    }
                    delay(30000)
                } catch (e: Exception) {
                    Log.e(TAG, "Ping error for JID: $jid: ${e.message}", e)
                    state = StreamState.NOT_CONNECTING
                    reconnect()
                    break
                }
            }
            Log.d(TAG, "Ping loop terminated for $jid")
        }
    }

    private fun generateClientChallenge(): String {
        val len = 10
        val letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..len).map { letters.random() }.joinToString("")
    }

    private fun getCryptoAlgorithm(ocraSuit: String): String {
        val cryptoFunction = ocraSuit.split(":")[1]
        val algo = cryptoFunction.split("-")[1]
        return when (algo) {
            "SHA1" -> "HmacSHA1"
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> "HmacSHA1"
        }
    }

    private fun getHashLength(ocraSuit: String): Int {
        val cryptoFunction = ocraSuit.split(":")[1]
        val algo = cryptoFunction.split("-")[1]
        return when (algo) {
            "SHA1" -> 20
            "SHA256" -> 32
            "SHA512" -> 64
            else -> 20
        }
    }

    private fun getHotpLength(ocraSuit: String): Int {
        val cryptoFunction = ocraSuit.split(":")[1]
        val hotpLength = cryptoFunction.split("-")[2]
        return hotpLength.toIntOrNull() ?: 0
    }

    private fun computeHmac(ocraSuit: String, secret: String, challengeQuestion: String, counter: Long = 0): ByteArray {
        val algorithm = getCryptoAlgorithm(ocraSuit)
        val secretBytes = Base64.getDecoder().decode(secret)
        val keySpec = SecretKeySpec(secretBytes, algorithm)
        val mac = Mac.getInstance(algorithm)
        mac.init(keySpec)

        val challengeData = challengeQuestion.toByteArray(StandardCharsets.UTF_8)
        val paddedChallenge = ByteArray(128)
        System.arraycopy(challengeData, 0, paddedChallenge, 0, challengeData.size)

        val dataInput = mutableListOf<Byte>()
        dataInput.addAll(ocraSuit.toByteArray(StandardCharsets.UTF_8).toList())
        dataInput.add(0)
        if (counter > 0) {
            val counterBytes = ByteArray(8)
            for (i in 0 until 8) {
                counterBytes[7 - i] = (counter shr (i * 8)).toByte()
            }
            dataInput.addAll(counterBytes.toList())
        }
        dataInput.addAll(paddedChallenge.toList())

        return mac.doFinal(dataInput.toByteArray())
    }

    private fun truncateHash(hash: ByteArray, hotpLength: Int): Long {
        val offset = (hash[hash.size - 1].toInt() and 0x0f)
        val truncatedHash = ByteArray(4)
        System.arraycopy(hash, offset, truncatedHash, 0, 4)
        var value = 0
        for (i in 0 until 4) {
            value = (value shl 8) or (truncatedHash[i].toInt() and 0xff)
        }
        value = value and 0x7fffffff
        return (value % 10.0.pow(hotpLength).toLong()).toLong()
    }
}