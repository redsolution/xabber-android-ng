package com.xabber.data_base.models.messages

import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.annotation.RequiresApi
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.utils.prp
import com.xabber.utils.toMap
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.UUID
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.xabber.data_base.defaultRealmConfig
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.annotations.Ignore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

open class MessageReferenceStorageItem : RealmObject {
    enum class Kind(val rawValue: String) {
        MEDIA("media"),
        VOICE("voice"),
        FORWARD("forward"),
        MARKUP("markup"),
        MENTION("mention"),
        QUOTE("quote"),
        GROUPCHAT("groupchat"),
        CALL("call"),
        SYSTEM_MESSAGE("system-message"),
        NONE("")
    }

    @PrimaryKey
    var primary: String = UUID.randomUUID().toString()
    var messagePrimary: String = ""
    var sentDate: Double = 0.0
    var owner: String = ""
    var jid: String = ""
    var kind_: String = Kind.NONE.rawValue
    var fileName: String = ""
    var fileSize: Long = 0L
    var mimeType: String = ""
    var begin: Int = 0
    var end: Int = 0
    var metadata_: String = ""
    var isDownloaded: Boolean = false
    var isUploaded: Boolean = false
    var isMissed: Boolean = false
    var hasError: Boolean = false
    var uri: String? = null
    var isGeo: Boolean = false
    var isVideo: Boolean = false
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var isAudioMessage: Boolean = false
    var messageId: String = ""
    var conversationType_: String = ConversationType.Regular.rawValue

    // Cache parsed metadata to avoid repeated JSON parsing during scroll
    @Ignore
    private var _cachedMetadata: Map<String, Any>? = null
    @Ignore
    private var _cachedMetadataRaw: String? = null

    var kind: Kind
        get() = Kind.entries.firstOrNull { it.rawValue == kind_ } ?: Kind.NONE
        set(value) { kind_ = value.rawValue }

    var conversationType: ConversationType
        get() = ConversationType.values().firstOrNull { it.rawValue == conversationType_ } ?: ConversationType.Regular
        set(value) { conversationType_ = value.rawValue }

    var metadata: Map<String, Any>?
        get() {
            val raw = metadata_
            if (raw.isEmpty()) return null
            // Return cached if metadata_ hasn't changed
            if (raw == _cachedMetadataRaw && _cachedMetadata != null) return _cachedMetadata
            return try {
                val parsed = JSONObject(raw).toMap()
                _cachedMetadata = parsed
                _cachedMetadataRaw = raw
                parsed
            } catch (e: Exception) {
                Log.e("MessageReferenceStorageItem", "Cannot parse metadata for message $messagePrimary: ${e.message}")
                null
            }
        }
        set(value) {
            _cachedMetadata = null
            _cachedMetadataRaw = null
            metadata_ = value?.let { map ->
                try {
                    JSONObject(map).toString()
                } catch (e: Exception) {
                    Log.e("MessageReferenceStorageItem", "Cannot encode metadata for message $messagePrimary: ${e.message}")
                    ""
                }
            } ?: ""
        }

    val range: IntRange
        get() = begin until end

    var uploadUrl: URL?
        get() = metadata?.get("putUri")?.let { uri ->
            try {
                URL(uri.toString().encodeURL())
            } catch (e: Exception) {
                Log.e("MessageReferenceStorageItem", "Invalid putUri: $uri")
                null
            }
        }
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("putUri", value?.toString() ?: "") } ?: mapOf("putUri" to (value?.toString() ?: ""))
        }

    var localFileUrl: URL?
        get() = metadata?.get("localFileUri")?.let { uri ->
            try {
                URL(uri.toString().encodeURL())
            } catch (e: Exception) {
                Log.e("MessageReferenceStorageItem", "Invalid localFileUri: $uri")
                null
            }
        }
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("localFileUri", value?.toString() ?: "") } ?: mapOf("localFileUri" to (value?.toString() ?: ""))
        }

    var downloadUrl: URL?
        get() = uri?.let { uri ->
            try {
                URL(uri.encodeURL())
            } catch (e: Exception) {
                Log.e("MessageReferenceStorageItem", "Invalid uri: $uri")
                null
            }
        }
        set(value) {
            val uri = value?.toString()
            metadata = metadata?.toMutableMap()?.apply { put("uri", uri ?: "") } ?: mapOf("uri" to (uri ?: ""))
            this.uri = uri
        }

    var decodedUrl: URL?
        get() = metadata?.get("decodedUrl")?.let { uri ->
            try {
                URL(uri.toString().encodeURL())
            } catch (e: Exception) {
                Log.e("MessageReferenceStorageItem", "Invalid decodedUrl: $uri")
                null
            }
        }
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("decodedUrl", value?.toString() ?: "") } ?: mapOf("decodedUrl" to (value?.toString() ?: ""))
        }

    var videoPreviewKey: String?
        get() = metadata?.get("thumbnail") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("thumbnail", value ?: "") } ?: mapOf("thumbnail" to (value ?: ""))
        }

    var videoPreviewUrl: URL?
        get() = metadata?.get("thumbnail")?.let { key ->
            try {
                URL(key.toString())
            } catch (e: Exception) {
                Log.e("MessageReferenceStorageItem", "Invalid thumbnail URL: $key")
                null
            }
        }
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("thumbnail", value?.toString() ?: "") } ?: mapOf("thumbnail" to (value?.toString() ?: ""))
        }

    var videoOrientation: String?
        get() = metadata?.get("orientation") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("orientation", value ?: "") } ?: mapOf("orientation" to (value ?: ""))
        }

    var date: String?
        get() = metadata?.get("date") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("date", value ?: "") } ?: mapOf("date" to (value ?: ""))
        }

    var senderName: String?
        get() = metadata?.get("sender_name") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("sender_name", value ?: "") } ?: mapOf("sender_name" to (value ?: ""))
        }

    var duration: Int?
        get() = metadata?.get("duration") as? Int
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("duration", value ?: 0) } ?: mapOf("duration" to (value ?: 0))
        }

    var videoDuration: String?
        get() = metadata?.get("video_duration") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("video_duration", value ?: "") } ?: mapOf("video_duration" to (value ?: ""))
        }

    var isDownloading: Boolean?
        get() = metadata?.get("is_downloading") as? Boolean
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("is_downloading", value ?: false) } ?: mapOf("is_downloading" to (value ?: false))
        }

    var name: String?
        get() = metadata?.get("name") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("name", value ?: "") } ?: mapOf("name" to (value ?: ""))
        }

    var filename: String?
        get() = metadata?.get("filename") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("filename", value ?: "") } ?: mapOf("filename" to (value ?: ""))
        }

    var filehash: String?
        get() = metadata?.get("hash") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("hash", value ?: "") } ?: mapOf("hash" to (value ?: ""))
        }

    var fileID: Int?
        get() = metadata?.get("fileID") as? Int
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("fileID", value ?: 0) } ?: mapOf("fileID" to (value ?: 0))
        }

    val sizeInBytesRaw: Long
        get() = metadata?.get("size") as? Long ?: 0L

    val sizeInBytes: String?
        get() {
            val size = metadata?.get("size") as? Long ?: return null
            val units = arrayOf("B", "KiB", "MiB", "GiB")
            var value = size.toDouble()
            var unitIndex = 0
            while (value >= 1024 && unitIndex < units.size - 1) {
                value /= 1024
                unitIndex++
            }
            return DecimalFormat("#.##").format(value) + " ${units[unitIndex]}"
        }

    val sizeInPx: Pair<Int, Int>?
        get() {
            val width = metadata?.get("width") as? Int
            val height = metadata?.get("height") as? Int
            return if (width != null && height != null) Pair(width, height) else null
        }

    var meteringLevels: List<Float>?
        get() = metadata?.get("pcm")?.toString()?.split(" ")?.mapNotNull { it.toFloatOrNull() }
        set(value) {
            metadata = metadata?.toMutableMap()?.apply {
                put("pcm", value?.joinToString(" ") { it.toString() } ?: "")
            } ?: mapOf("pcm" to (value?.joinToString(" ") { it.toString() } ?: ""))
        }

    val callState: VoIPCallState?
        get() = metadata?.get("callState")?.let { state ->
            VoIPCallState.entries.firstOrNull { it.value == state as? Int } ?: VoIPCallState.ENDED
        }

    val xmlType: String
        get() = when (kind) {
            Kind.VOICE, Kind.MEDIA -> "mutable"
            else -> "mutable"
        }

    companion object {
        @RequiresApi(Build.VERSION_CODES.O)
        suspend fun prepareVideo(messagePrimary: String, realm: Realm) {
            try {
                realm.write {
                    val instance = query<MessageStorageItem>("primary = $0", messagePrimary).first().find()
                    instance?.references?.forEach { ref ->
                        if (ref.mimeType == "video") {
                            CoroutineScope(Dispatchers.IO).launch {
                                ref.prepare()
                            }
                        }
                    }
                    instance?.inlineForwards?.forEach { forward ->
                        forward.references.forEach { ref ->
                            if (ref.mimeType == "video") {
                                CoroutineScope(Dispatchers.IO).launch {
                                    ref.prepare()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MessageReferenceStorageItem", "Error in prepareVideo for message $messagePrimary: ${e.message}")
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        suspend fun prepareVideo(messageId: String, jid: String, metadata: String, realm: Realm) {
            // Assuming CommonConfigManager.shared.config.use_file_encryption_by_default is false for now
            try {
                realm.write {
                    query<MessageReferenceStorageItem>(
                        "messageId = $0 AND jid = $1 AND metadata_ = $2",
                        messageId, jid, metadata
                    ).find().forEach { ref ->
                        CoroutineScope(Dispatchers.IO).launch {
                            ref.prepare()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MessageReferenceStorageItem", "Error in prepareVideo for messageId $messageId, jid $jid: ${e.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
     fun prepare() {
        if (isDownloaded) return

        when (kind) {
            Kind.VOICE -> {
                // Voice handling omitted as it depends on AudioMessageReceiver and OpusAudio
                // Placeholder for future implementation if needed
            }
            Kind.MEDIA -> {
                if (mimeType == "video") {
                    // Assuming CommonConfigManager.shared.config.use_file_encryption_by_default is false
                    CoroutineScope(Dispatchers.IO).launch {
                        val key = videoPreviewKey ?: run {
                            val url = downloadUrl?.toString() ?: return@launch
                            listOf(jid, owner, url).prp()
                        }
                        val result = extractFrameFromVideo(key)
                        val localRealm = Realm.open(defaultRealmConfig())
                        try {
                            localRealm.write {
                                val instances = query<MessageReferenceStorageItem>(
                                    "messageId = $0 AND jid = $1 AND metadata_ = $2",
                                    messageId, jid, metadata_
                                ).find()
                                instances.forEach { instance ->
                                    instance.isDownloaded = true
                                    instance.videoPreviewKey = key
                                    instance.videoDuration = result.videoDuration ?: ""
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MessageReferenceStorageItem", "Error updating video metadata: ${e.message}")
                        } finally {
                            localRealm.close()
                        }
                    }

                }
            }
            else -> Unit
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun extractFrameFromVideo(key: String): VideoFrameResult {
//        val context = // Obtain Android Context here (e.g., from Application or Activity)
//            android.content.ContextHolder.context // Placeholder; replace with actual Context access
//        val imageLoader = context.imageLoader
//        if (imageLoader.memoryCache?.get(key) == null) {
//            val url = localFileUrl ?: downloadUrl ?: return VideoFrameResult(null, null, null)
//            val retriever = MediaMetadataRetriever()
//            try {
//                withContext(Dispatchers.IO) {
//                    retriever.setDataSource(url.toString())
//                    val orientation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
//                    val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST) // 1-second mark
//                    bitmap?.let {
//                        val request = ImageRequest.Builder(context)
//                            .data(it)
//                            .memoryCacheKey(key)
//                            .build()
//                        val result = imageLoader.execute(request)
//                        if (result is SuccessResult) {
//                            Log.d("MessageReferenceStorageItem", "Cached video frame for key: $key")
//                        }
//                    }
//
//                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
//                    val seconds = (durationMs / 1000) % 60
//                    val minutes = (durationMs / 1000 / 60) % 60
//                    val videoDuration = String.format("%02d:%02d", minutes, seconds)
//
//                    return VideoFrameResult(
//                        width = bitmap?.width?.toFloat(),
//                        height = bitmap?.height?.toFloat(),
//                        videoDuration = videoDuration
//                    )
//                }
//            } catch (e: Exception) {
//                Log.e("MessageReferenceStorageItem", "Error extracting video frame for key $key: ${e.message}")
//                return VideoFrameResult(null, null, null)
//            } finally {
//                retriever.release()
//            }
//        }
        return VideoFrameResult(null, null, null)
    }

    data class VideoFrameResult(
        val width: Float?,
        val height: Float?,
        val videoDuration: String?
    )

    private fun String.encodeURL(): String = java.net.URLEncoder.encode(this, "UTF-8")
}

enum class VoIPCallState(val value: Int) {
    ENDED(0),
    // Add other states as needed (e.g., RINGING, CONNECTED, etc.)
}