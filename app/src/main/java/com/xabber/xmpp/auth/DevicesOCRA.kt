package com.xabber.xmpp.auth

import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.stream.Stream
import com.xabber.stream.StreamFeatures
import com.xabber.stream.StreamState
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
    private var secret: String,
    private var validationKey: String,
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
            Log.e(TAG, "Missing OCRA data input: deviceId=${redactSecretForLog(deviceId)}, hasSecret=${secret.isNotEmpty()}, hasValidationKey=${validationKey.isNotEmpty()}, authCounter=$authCounter")
            return@withContext false
        }
        realm.query<DeviceStorageItem>("uid = $0 AND owner = $1", deviceId, stream.jid).first().find()?.let { device ->
            Log.d(TAG, "DeviceStorageItem: uid=${redactSecretForLog(device.uid)}, secretBase64Valid=${try { Base64.decode(device.secret, Base64.DEFAULT); true } catch (e: Exception) { false }}, authDate=${device.authDate}, authCounter=${device.authCounter}, owner=${device.owner}")
            if (!try { Base64.decode(device.secret, Base64.DEFAULT); true } catch (e: Exception) { false }) {
                Log.w(TAG, "Invalid secret, triggering re-registration")

                return@withContext false
            }
        } ?: run {
            Log.e(TAG, "No DeviceStorageItem found for deviceId=${redactSecretForLog(deviceId)}, owner=${stream.jid}, triggering re-registration")
            stream.state = StreamState.DEVICE_REGISTRATION
            return@withContext false
        }
        Log.d(TAG, "Starting OCRA authentication with authCounter=$authCounter")
        val base64 = clientInitialResponse()
        val authMessage = """
            <auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='$MECHANISM_NAME'>$base64</auth>
        """.trimIndent()
        state = OCRAAuthState.CHALLENGE
        if (stream.socket?.write(authMessage) == true) {
            Log.d(TAG, "Sent OCRA auth request for deviceId=${redactSecretForLog(deviceId)}")
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
        Log.d(TAG, "Prepared client initial response for user=$username, deviceId=${redactSecretForLog(deviceId)}, payloadBytes=${messageBytes.size}")
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
        Log.d(TAG, "Handling OCRA challenge")
        if (!message.contains("<challenge")) {
            Log.e(TAG, "Expected challenge element, received non-challenge payload")
            state = OCRAAuthState.FAILED
            return@withContext false
        }
        val startTag = "<challenge"
        val endTag = "</challenge>"
        val startIndex = message.indexOf('>', message.indexOf(startTag)) + 1
        val endIndex = message.indexOf(endTag)
        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            Log.e(TAG, "Invalid challenge tag format")
            state = OCRAAuthState.FAILED
            return@withContext false
        }
        val base64Data = message.substring(startIndex, endIndex).trim()
        try {
            val decodedData = Base64.decode(base64Data, Base64.DEFAULT)
            val decodedString = String(decodedData, Charsets.UTF_8)
            val parts = decodedString.split(NULL_BYTE)
            Log.d(TAG, "Decoded OCRA challenge with ${parts.size} parts")
            if (parts.size != 3) {
                Log.e(TAG, "Expected 3 challenge parts, found ${parts.size}")
                state = OCRAAuthState.FAILED
                return@withContext false
            }
            return@withContext processChallengeParts(parts)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Base64 decoding failed for challenge payload: ${e.message}", e)
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
        var srvChallengeQuestion = parts[2]
        try {
            Log.d(TAG, "Server challenge received: suit=$srvOCRASuit, challengeLength=${srvChallengeQuestion.length}")
            val clientChallengeValid = verifyClientChallenge(srvResponse)
            if (!clientChallengeValid) {
                Log.e(TAG, "Client challenge verification failed with clientOCRASuit=$clientOCRASuit, triggering re-registration")

                return false
            }
//            srvChallengeQuestion = "EZM4JiZkv8"
//            this.authCounter = 356
//            this.validationKey = "CzbqHNItUt7VDGz0wI4z7Mp5XQBQofFw1v/PBmuSaz+JYkuKq+k5E8jaknOHdPXHOPOwS862cUDBwtK/2vX87Q=="
//            this.secret = "FhkwG5xOJIJ4gMDZIBIJQdoz24iVzeGZhrFBFQ86FgwoG/ROjN5itRSKz521PUTAdhtxa0awqrZUeTIevmoXog=="

            val response = generateServerResponse(srvOCRASuit, srvChallengeQuestion)
            Log.d(TAG, "Sending OCRA response payload")
            val responseMessage = """
                <response xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>$response</response>
            """.trimIndent()
            state = OCRAAuthState.END
            if (stream.socket?.write(responseMessage) == true) {
                Log.d(TAG, "Sent OCRA response")
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
        Log.d(TAG, "Verifying client challenge response")
        val clAlgorithm = getCryptoAlgorithm(clientOCRASuit)
        val clHashLength = getHashLength(clientOCRASuit)
        val secretBytes = try {
            Base64.decode(secret, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid secret format, error: ${e.message}")
            return false
        }
        val srvResponseBytes = try {
            Base64.decode(srvResponse, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Base64 decoding failed for server response: ${e.message}")
            return false
        }
        val srvResponseDecoded = String(srvResponseBytes, Charsets.UTF_8)
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
        val hashString = Base64.encodeToString(clHash, Base64.NO_WRAP)
        val clHotpLength = getHotpLength(clientOCRASuit)
        if (clHotpLength == 0) {
            val isValid = hashString == srvResponse
            Log.d(TAG, "Verification result: $isValid")
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
            val isValid = payload == srvResponseDecoded
            Log.d(TAG, "Verification result: $isValid")
            return isValid
        }
    }

    private fun generateServerResponse(srvOCRASuit: String, srvChallengeQuestion: String): String {
        val algorithm = getCryptoAlgorithm(srvOCRASuit)
        val hashLength = getHashLength(srvOCRASuit)

        val secretBytes = try {
            Base64.decode(secret, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid secret format in generateServerResponse: ${e.message}")
            return ""
        }
        Log.d(TAG, "Generating server response for deviceId=${redactSecretForLog(deviceId)}, authCounter=$authCounter")
        val dataInput = buildServerChallengeData(srvOCRASuit, srvChallengeQuestion)
        val hash = computeHmac(algorithm, secretBytes, dataInput)
        val clHotpLength = getHotpLength(srvOCRASuit)
        if (clHotpLength == 0) {
            val base64Hash = Base64.encodeToString(hash, Base64.NO_WRAP)
            val doubleBase64Hash = Base64.encodeToString(base64Hash.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
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
            val base64Payload = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val doubleBase64Payload = Base64.encodeToString(base64Payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return doubleBase64Payload
        }
    }

    private fun buildServerChallengeData(ocraSuit: String, challengeQuestion: String): ByteArray {
        val challengeData = ByteArray(164)
        val suitBytes = ocraSuit.toByteArray(Charsets.UTF_8)
        val challengeBytes = challengeQuestion.toByteArray(Charsets.UTF_8)

        // Inserted fragment for ChallengeBytesCounter
        val challengeBytesCounter = ByteArray(128)
        System.arraycopy(challengeBytes, 0, challengeBytesCounter, 0, challengeBytes.size)
        val padLengthCounter = 128 - challengeBytes.size
        if (padLengthCounter > 0) {
            System.arraycopy(ByteArray(padLengthCounter), 0, challengeBytesCounter, challengeBytes.size, padLengthCounter)
        }

        val counterBytes = ByteBuffer.allocate(8).putLong(authCounter).array()
        var offset = 0
        System.arraycopy(suitBytes, 0, challengeData, offset, suitBytes.size)
        offset += suitBytes.size
        challengeData[offset++] = 0
        System.arraycopy(counterBytes, 0, challengeData, offset, counterBytes.size)
        offset += counterBytes.size
        System.arraycopy(challengeBytesCounter, 0, challengeData, offset, challengeBytesCounter.size)
        offset += challengeBytesCounter.size
        val padLength = 164 - offset
        if (padLength > 0) {
            System.arraycopy(ByteArray(padLength), 0, challengeData, offset, padLength)
        }
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
        Log.d(TAG, "Handling OCRA auth response")
        if (message.contains("<success")) {
            Log.d(TAG, "OCRA authentication successful")
            state = OCRAAuthState.END
            realm.write {
                val device = query<DeviceStorageItem>("uid = $0 AND owner = $1", deviceId, stream.jid).first().find()
                if (device != null) {
                    findLatest(device)?.apply {
                        authDate = System.currentTimeMillis()
                        authCounter = this@DevicesOCRA.authCounter + 1
                    }
                    Log.d(TAG, "Updated DeviceStorageItem: authDate=${device?.authDate}, authCounter=${device?.authCounter} for deviceId=${redactSecretForLog(deviceId)}")
                }
            }
            true
        } else {
            Log.e(TAG, "OCRA authentication failed")
            // Извлечение текста ошибки
            val errorTextMatch = Regex("""<text[^>]*>([^<]+)</text>""").find(message)
            val errorText = errorTextMatch?.groupValues?.get(1) ?: "Unknown OCRA authentication error"
            Log.e(TAG, "OCRA error details: $errorText")
            // Уведомление через callback в Stream
            state = OCRAAuthState.FAILED
            return@withContext false
        }
    }

    fun getState(): OCRAAuthState = state
}
