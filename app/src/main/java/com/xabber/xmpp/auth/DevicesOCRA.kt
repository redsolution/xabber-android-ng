package com.xabber.xmpp.auth

import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.common.Stream
import com.xabber.common.StreamFeatures
import com.xabber.common.StreamState
import com.xabber.xmpp.device.DeviceStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

enum class OCRAAuthState {
    START,
    CHALLENGE,
    RESPONSE,
    END,
    FAILED
}

class DevicesOCRA(
    private val stream: Stream,
    private val deviceId: String,
    private val secret: String,
    private val validationKey: String,
    private var authCounter: Long,
    private val realm: Realm,
) {
    companion object {
        private const val TAG = "DevicesOCRA"
        const val MECHANISM_NAME = "DEVICES-OCRA"
        private const val CLIENT_OCRA_SUIT = "OCRA-1:HOTP-SHA256-8:QA10"
        private const val NULL_BYTE = "\u0000"

        fun isSupported(features: StreamFeatures?): Boolean {
            return features?.mechanisms?.mechanism?.contains(MECHANISM_NAME) == true
        }
    }

    private var state: OCRAAuthState = OCRAAuthState.START
    private var clientChallengeQuestion: String? = null
    private val clientOCRASuit: String = CLIENT_OCRA_SUIT

    private fun generateClientChallenge(): String {
        val length = 10
        val letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { letters.random() }
            .joinToString("")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (deviceId.isEmpty() || secret.isEmpty() || validationKey.isEmpty() || authCounter == 0L) {
            Log.e(TAG, "Missing OCRA DataInput: deviceId=$deviceId, secret=$secret, validationKey=$validationKey, authCounter=$authCounter")
            return@withContext false
        }
        realm.query<DeviceStorageItem>("uid = $0 AND owner = $1", deviceId, stream.jid).first().find()?.let { device ->
            Log.d(TAG, "DeviceStorageItem: uid=${device.uid}, secret=${device.secret}, secretBase64Valid=${try { Base64.decode(device.secret, Base64.DEFAULT); true } catch (e: Exception) { false }}, validationKey=${device.validationKey}, authDate=${device.authDate}, authCounter=${device.authCounter}, owner=${device.owner}")
            if (!try { Base64.decode(device.secret, Base64.DEFAULT); true } catch (e: Exception) { false }) {
                Log.w(TAG, "Invalid secret, triggering re-registration")
                stream.state = StreamState.DEVICE_REGISTRATION
                return@withContext false
            }
            if (device.secret != secret || device.validationKey != validationKey || device.authCounter != authCounter) {
                Log.w(TAG, "Mismatch: stored_secret=${device.secret}, input_secret=$secret, stored_validationKey=${device.validationKey}, input_validationKey=$validationKey, stored_authCounter=${device.authCounter}, input_authCounter=$authCounter, triggering re-registration")
                stream.state = StreamState.DEVICE_REGISTRATION
                return@withContext false
            }
        } ?: run {
            Log.e(TAG, "No DeviceStorageItem found for deviceId=$deviceId, owner=${stream.jid}, triggering re-registration")
            stream.state = StreamState.DEVICE_REGISTRATION
            return@withContext false
        }
        Log.d(TAG, "Starting OCRA authentication with authCounter=$authCounter")
        val base64 = clientInitialResponse()
        val authMessage = """
            <auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='$MECHANISM_NAME'>$base64</auth>
        """.trimIndent()
        state = OCRAAuthState.CHALLENGE
        if (stream.getSocket()?.write(authMessage) == true) {
            Log.d(TAG, "Sent OCRA auth request: $authMessage")
            true
        } else {
            Log.e(TAG, "Failed to send OCRA auth request")
            state = OCRAAuthState.FAILED
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun clientInitialResponse(): String {
        clientChallengeQuestion = generateClientChallenge()
        val username = stream.extractUsernameFromJid(stream.jid)
        val message = "n,,${NULL_BYTE}$username${NULL_BYTE}$deviceId${NULL_BYTE}$clientOCRASuit${NULL_BYTE}$clientChallengeQuestion${NULL_BYTE}$validationKey"
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        Log.d(TAG, "Client initial response message: ${message.replace(NULL_BYTE, "|")}")
        Log.d(TAG, "Client initial response bytes: ${messageBytes.joinToString(", ") { it.toUByte().toString(16).padStart(2, '0') }}")
        return Base64.encodeToString(messageBytes, Base64.NO_WRAP)
    }

    private fun getCryptoAlgorithm(ocraSuit: String): String {
        try {
            val cryptoFunction = ocraSuit.split(":")[1]
            val algo = cryptoFunction.split("-")[1]
            return when (algo) {
                "SHA1" -> "HmacSHA1"
                "SHA256" -> "HmacSHA256"
                "SHA512" -> "HmacSHA512"
                else -> {
                    Log.e(TAG, "Unknown algorithm in ocraSuit: $ocraSuit")
                    "HmacSHA256"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse crypto algorithm from ocraSuit: $ocraSuit, error: ${e.message}")
            return "HmacSHA256"
        }
    }

    private fun getHashLength(ocraSuit: String): Int {
        val algo = getCryptoAlgorithm(ocraSuit)
        return when (algo) {
            "HmacSHA1" -> 20
            "HmacSHA256" -> 32
            "HmacSHA512" -> 64
            else -> 32
        }
    }

    private fun getHotpLength(ocraSuit: String): Int {
        try {
            val parts = ocraSuit.split(":")
            if (parts.size < 2) {
                Log.e(TAG, "Invalid ocraSuit format: $ocraSuit")
                return 0
            }
            val cryptoFunction = parts[1]
            val hotpParts = cryptoFunction.split("-")
            if (hotpParts.size < 3) {
                Log.e(TAG, "Invalid cryptoFunction format: $cryptoFunction")
                return 0
            }
            val hotpLength = hotpParts[2].toIntOrNull() ?: 0
            Log.d(TAG, "Parsed HOTP length from ocraSuit: $ocraSuit, length: $hotpLength")
            return hotpLength
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse HOTP length from ocraSuit: $ocraSuit, error: ${e.message}")
            return 0
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun handleAuthChallenge(message: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Handling OCRA challenge: $message")
        if (!message.contains("<challenge")) {
            Log.e(TAG, "Expected challenge element, received: $message")
            state = OCRAAuthState.FAILED
            return@withContext false
        }
        val startTag = "<challenge"
        val endTag = "</challenge>"
        val startIndex = message.indexOf('>', message.indexOf(startTag)) + 1
        val endIndex = message.indexOf(endTag)
        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            Log.e(TAG, "Invalid challenge tag format in message: $message")
            state = OCRAAuthState.FAILED
            return@withContext false
        }
        val base64Data = message.substring(startIndex, endIndex).trim()
        Log.d(TAG, "Raw Base64 challenge data: $base64Data")
        try {
            val decodedData = Base64.decode(base64Data, Base64.DEFAULT)
            val decodedString = String(decodedData, Charsets.UTF_8)
            Log.d(TAG, "Decoded challenge string: ${decodedString.replace(NULL_BYTE, "|")}")
            val parts = decodedString.split(NULL_BYTE)
            Log.d(TAG, "Challenge parts after splitting on null byte: ${parts.joinToString("|")}")
            if (parts.size != 3) {
                Log.e(TAG, "Expected 3 challenge parts, found ${parts.size}")
                state = OCRAAuthState.FAILED
                return@withContext false
            }
            return@withContext processChallengeParts(parts)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Base64 decoding failed for challenge: $base64Data, error: ${e.message}", e)
            state = OCRAAuthState.FAILED
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error processing challenge: ${e.message}", e)
            state = OCRAAuthState.FAILED
            return@withContext false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun processChallengeParts(parts: List<String>): Boolean {
        if (parts.size != 3) {
            Log.e(TAG, "Invalid number of challenge parts: ${parts.size}")
            state = OCRAAuthState.FAILED
            return false
        }
        val srvResponse = parts[0]
        val srvOCRASuit = parts[1]
        val srvChallengeQuestion = parts[2]
        try {
            Log.d(TAG, "Server challenge: srvResponse=$srvResponse, srvOCRASuit=$srvOCRASuit, srvChallengeQuestion=$srvChallengeQuestion")
            val clientChallengeValid = verifyClientChallenge(srvResponse)
            if (!clientChallengeValid) {
                Log.e(TAG, "Client challenge verification failed with clientOCRASuit=$clientOCRASuit, triggering re-registration")
                stream.state = StreamState.DEVICE_REGISTRATION
                return false
            }
            val response = generateServerResponse(srvOCRASuit, srvChallengeQuestion)
            Log.d(TAG, "Sending OCRA response payload: $response")
            val responseMessage = """
                <response xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>$response</response>
            """.trimIndent()
            state = OCRAAuthState.END
            if (stream.getSocket()?.write(responseMessage) == true) {
                Log.d(TAG, "Sent OCRA response: $responseMessage")
                return true
            } else {
                Log.e(TAG, "Failed to send OCRA response")
                state = OCRAAuthState.FAILED
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing challenge parts: ${e.message}", e)
            state = OCRAAuthState.FAILED
            return false
        }
    }

    private fun verifyClientChallenge(srvResponse: String): Boolean {
        Log.d(TAG, "Verifying client challenge with srvResponse=$srvResponse")
        val clAlgorithm = getCryptoAlgorithm(clientOCRASuit)
        val clHashLength = getHashLength(clientOCRASuit)
        val secretBytes = try {
            Base64.decode(secret, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid secret format: $secret, error: ${e.message}")
            return false
        }
        Log.d(TAG, "Verification secret: ${Base64.encodeToString(secretBytes, Base64.NO_WRAP)}")
        val srvResponseBytes = try {
            Base64.decode(srvResponse, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Base64 decoding failed for srvResponse: $srvResponse, error: ${e.message}")
            return false
        }
        val srvResponseDecoded = String(srvResponseBytes, Charsets.UTF_8)
        Log.d(TAG, "srvResponseDecoded=$srvResponseDecoded")
        val clientChallenge = clientChallengeQuestion ?: run {
            Log.e(TAG, "Client challenge question is null")
            return false
        }
        val challengeBytes = clientChallenge.toByteArray(Charsets.UTF_8)
        val clientChallengeData = ByteArray(128)
        System.arraycopy(challengeBytes, 0, clientChallengeData, 0, challengeBytes.size)
        val padLength = 128 - challengeBytes.size
        if (padLength > 0) {
            System.arraycopy(ByteArray(padLength), 0, clientChallengeData, challengeBytes.size, padLength)
        }
        val suitBytes = clientOCRASuit.toByteArray(Charsets.UTF_8)
        val nullByteArray = byteArrayOf(0)
        val clDataInput = suitBytes + nullByteArray + clientChallengeData
        val clHash = computeHmac(clAlgorithm, secretBytes, clDataInput)
        if (clHash.isEmpty()) {
            Log.e(TAG, "HMAC computation failed")
            return false
        }
        Log.d(TAG, "HMAC inputs: algorithm=$clAlgorithm, secret=${Base64.encodeToString(secretBytes, Base64.NO_WRAP).take(8)}..., challengeData=${clDataInput.joinToString(", ") { it.toUByte().toString(16).padStart(2, '0') }}, challengeQuestion=$clientChallenge")
        val hashString = Base64.encodeToString(clHash, Base64.NO_WRAP)
        Log.d(TAG, "Computed hash: $hashString")
        val clHotpLength = getHotpLength(clientOCRASuit)
        if (clHotpLength == 0) {
            val isValid = hashString == srvResponse
            Log.d(TAG, "Verification result: $isValid (hashString=$hashString, srvResponse=$srvResponse)")
            return isValid
        } else {
            val offset = (clHash[clHash.size - 1].toInt() and 0x0F)
            val binary = ((clHash[offset].toInt() and 0x7F) shl 24) or
                    ((clHash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((clHash[offset + 2].toInt() and 0xFF) shl 8) or
                    (clHash[offset + 3].toInt() and 0xFF)
            val pinValue = (binary and 0x7FFFFFFF.toInt()).toLong() % 10.0.pow(clHotpLength).toLong()
            val payload = when (clHotpLength) {
                4 -> String.format("%04d", pinValue)
                6 -> String.format("%06d", pinValue)
                8 -> String.format("%08d", pinValue)
                else -> pinValue.toString()
            }
            Log.d(TAG, "Computed payload: $payload, expected srvResponseDecoded: $srvResponseDecoded")
            val isValid = payload == srvResponseDecoded
            Log.d(TAG, "Verification result: $isValid (payload=$payload, srvResponseDecoded=$srvResponseDecoded)")
            return isValid
        }
    }

    private fun generateServerResponse(srvOCRASuit: String, srvChallengeQuestion: String): String {
        val algorithm = getCryptoAlgorithm(srvOCRASuit)
        val hashLength = getHashLength(srvOCRASuit)
        val secretBytes = try {
            Base64.decode(secret, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid secret format in generateServerResponse: $secret, error: ${e.message}")
            return ""
        }
        Log.d(TAG, "Generating server response with secret=${Base64.encodeToString(secretBytes, Base64.NO_WRAP)}, authCounter=$authCounter")
        val dataInput = buildServerChallengeData(srvOCRASuit, srvChallengeQuestion)
        val hash = computeHmac(algorithm, secretBytes, dataInput)
        Log.d(TAG, "Server response HMAC: algorithm=$algorithm, challengeData=${dataInput.joinToString(", ") { it.toUByte().toString(16).padStart(2, '0') }}, hash=${Base64.encodeToString(hash, Base64.NO_WRAP)}")
        val clHotpLength = getHotpLength(srvOCRASuit)
        if (clHotpLength == 0) {
            val base64Hash = Base64.encodeToString(hash, Base64.NO_WRAP)
            Log.d(TAG, "Server response base64 hash: $base64Hash")
            val doubleBase64Hash = Base64.encodeToString(base64Hash.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            Log.d(TAG, "Server response double base64 hash: $doubleBase64Hash")
            return doubleBase64Hash
        } else {
            val offset = (hash[hash.size - 1].toInt() and 0x0F)
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)
            val pinValue = (binary and 0x7FFFFFFF.toInt()).toLong() % 10.0.pow(clHotpLength).toLong()
            val payload = when (clHotpLength) {
                4 -> String.format("%04d", pinValue)
                6 -> String.format("%06d", pinValue)
                8 -> String.format("%08d", pinValue)
                else -> pinValue.toString()
            }
            Log.d(TAG, "Server response HOTP payload: $payload")
            val base64Payload = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            Log.d(TAG, "Server response base64 HOTP payload: $base64Payload")
            val doubleBase64Payload = Base64.encodeToString(base64Payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            Log.d(TAG, "Server response double base64 HOTP payload: $doubleBase64Payload")
            return doubleBase64Payload
        }
    }

    private fun buildServerChallengeData(ocraSuit: String, challengeQuestion: String): ByteArray {
        val challengeData = ByteArray(128)
        val suitBytes = ocraSuit.toByteArray(Charsets.UTF_8)
        val challengeBytes = challengeQuestion.toByteArray(Charsets.UTF_8)
        val counterBytes = ByteBuffer.allocate(8).putLong(authCounter).array()
        var offset = 0
        System.arraycopy(suitBytes, 0, challengeData, offset, suitBytes.size)
        offset += suitBytes.size
        challengeData[offset++] = 0
        System.arraycopy(counterBytes, 0, challengeData, offset, counterBytes.size)
        offset += counterBytes.size
        System.arraycopy(challengeBytes, 0, challengeData, offset, challengeBytes.size)
        offset += challengeBytes.size
        val padLength = 128 - offset
        if (padLength > 0) {
            System.arraycopy(ByteArray(padLength), 0, challengeData, offset, padLength)
        }
        Log.d(TAG, "Server challenge data: ${challengeData.joinToString(", ") { it.toUByte().toString(16).padStart(2, '0') }}")
        return challengeData
    }

    private fun computeHmac(algorithm: String, key: ByteArray, data: ByteArray): ByteArray {
        try {
            val mac = Mac.getInstance(algorithm)
            mac.init(SecretKeySpec(key, algorithm))
            return mac.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "HMAC computation failed: algorithm=$algorithm, error: ${e.message}", e)
            return ByteArray(0)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun handleAuthResponse(message: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Handling OCRA auth response: $message")
        if (message.contains("<success")) {
            Log.d(TAG, "OCRA authentication successful")
            state = OCRAAuthState.END
            realm.write {
                val device = query<DeviceStorageItem>("uid = $0 AND owner = $1", deviceId, stream.jid).first().find()
                if (device != null) {
                    findLatest(device)?.apply {
                        authDate = System.currentTimeMillis().toDouble() / 1000
                        authCounter = this@DevicesOCRA.authCounter + 1
                    }
                    Log.d(TAG, "Updated DeviceStorageItem: authDate=${device?.authDate}, authCounter=${device?.authCounter} for deviceId=$deviceId")
                }
            }
            true
        } else {
            Log.e(TAG, "OCRA authentication failed: $message")
            // Извлечение текста ошибки
            val errorTextMatch = Regex("""<text[^>]*>([^<]+)</text>""").find(message)
            val errorText = errorTextMatch?.groupValues?.get(1) ?: "Unknown OCRA authentication error"
            Log.e(TAG, "OCRA error details: $errorText")
            // Уведомление через callback в Stream
            state = OCRAAuthState.FAILED
            stream.state = StreamState.DEVICE_REGISTRATION
            return@withContext false
        }
    }

    fun getState(): OCRAAuthState = state
}