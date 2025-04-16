package com.xabber.xmpp.messages.message

import com.xabber.utils.prp
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.util.Size
import java.util.Base64
import android.text.format.Formatter
import com.xabber.xmpp.messages.toMap
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList

/**
 * Represents a message reference storage item, tracking metadata for message attachments.
 * Persisted using Realm for database storage.
 */
open class MessageReferenceStorageItem : RealmObject {
    data class Model(
        val primary: String,
        val messageId: String,
        val owner: String,
        val jid: String,
        val kindRaw: String,
        val mimeType: String,
        val begin: Int,
        val end: Int,
        val metadataRaw: String,
        val isDownloaded: Boolean,
        val isOriginalMissed: Boolean
    ) {
        val kind: Kind
            get() = Kind.fromRaw(kindRaw)

        val range: IntRange
            get() = begin until end

        val metadata: Map<String, Any>?
            get() = metadataRaw.toByteArray(Charsets.UTF_8).let { data ->
                try {
                    JSONObject(String(data, Charsets.UTF_8)).toMap()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot create JSON object from reference metadata with id: $messageId")
                    null
                }
            }

        val sizeInBytesRaw: Int
            get() = metadata?.get("size") as? Int ?: 0

        val sizeInBytes: String?
            get() {
                val size = metadata?.get("size") as? Int ?: return null
                return Formatter.formatShortFileSize(null, size.toLong())
                    .replace(",", ".")
                    .replace("MB", "MiB")
                    .replace("KB", "KiB")
            }

        val sizeInPx: Size?
            get() {
                val height = metadata?.get("height") as? Int
                val width = metadata?.get("width") as? Int
                return if (height != null && width != null) Size(width, height) else null
            }

        /*
        val sizeInPxThumb: Size?
            get() {
                val height = metadata?.get("height_thumb") as? Int
                val width = metadata?.get("width_thumb") as? Int
                return if (height != null && width != null) Size(width, height) else null
            }
        */

        val meteringLevels: List<Float>?
            get() {
                val metersString = metadata?.get("meters") as? String
                return metersString?.split(" ")?.mapNotNull { it.toFloatOrNull() }
            }

        val uploadUrl: Uri?
            get() {
                val uri = metadata?.get("putUri") as? String
                return uri?.let { Uri.parse(it.encodeUri()) }
            }

        val localFileUrl: Uri?
            get() {
                val uri = metadata?.get("localFileUri") as? String
                return uri?.let { Uri.parse(it.encodeUri()) }
            }

        val downloadUrl: Uri?
            get() {
                val uri = metadata?.get("uri") as? String
                return uri?.let { Uri.parse(it.encodeUri()) }
            }

        val videoPreviewKey: String?
            get() = metadata?.get("thumbnail") as? String

        val videoOrientation: String?
            get() = metadata?.get("orientation") as? String

        val audioDuration: Float?
            get() = metadata?.get("duration") as? Float

        val date: String?
            get() = metadata?.get("date") as? String

        val senderName: String?
            get() = metadata?.get("sender_name") as? String

        val duration: String?
            get() = metadata?.get("video_duration") as? String
    }

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
        NONE("");

        companion object {
            fun fromRaw(raw: String): Kind =
                values().find { it.rawValue == raw } ?: NONE
        }
    }

    companion object {
        private const val TAG = "MessageReferenceStorageItem"

        fun prepareVoice(messagePrimary: String, realm: Realm) {
            try {
                val instance = realm.query<MessageStorageItem>("primary = $0", messagePrimary)
                    .first().find()
                instance?.let {
                    it.references.forEach { ref -> ref.prepare() }
                    it.inlineForwards.forEach { forward ->
                        forward.references.forEach { ref -> ref.prepare() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "prepareVoice: ${e.message}")
            }
        }

        fun prepareVoice(inlineMessageId: String, realm: Realm) {
            try {
                realm.query<MessageForwardsInlineStorageItem>("messageId = $0", inlineMessageId)
                    .find().forEach { instance ->
                        instance.references.forEach { ref -> ref.prepare() }
                        instance.subforwards.forEach { sub ->
                            sub.references.forEach { ref -> ref.prepare() }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "prepareVoice(inline): ${e.message}")
            }
        }

        fun prepareVoice(messageId: String, jid: String, metadata: String, realm: Realm) {
            try {
                realm.query<MessageReferenceStorageItem>(
                    "messageId = $0 AND jid = $1 AND metadata_ = $2",
                    messageId, jid, metadata
                ).find().forEach { it.prepare() }
            } catch (e: Exception) {
                Log.e(TAG, "prepareVoice(for): ${e.message}")
            }
        }

        fun prepareVideo(messagePrimary: String, realm: Realm) {
            try {
                val instance = realm.query<MessageStorageItem>("primary = $0", messagePrimary)
                    .first().find()
                instance?.let {
                    it.references.forEach { ref ->
                        if (ref.mimeType == "video") ref.prepare()
                    }
                    it.inlineForwards.forEach { forward ->
                        forward.references.forEach { ref ->
                            if (ref.mimeType == "video") ref.prepare()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "prepareVideo: ${e.message}")
            }
        }

        fun prepareVideo(messageId: String, jid: String, metadata: String, realm: Realm) {
            if (CommonConfigManager.useFileEncryptionByDefault) return
            try {
                realm.query<MessageReferenceStorageItem>(
                    "messageId = $0 AND jid = $1 AND metadata_ = $2",
                    messageId, jid, metadata
                ).find().forEach { it.prepare() }
            } catch (e: Exception) {
                Log.e(TAG, "prepareVideo(for): ${e.message}")
            }
        }
    }

    fun primaryKey(): String = "primary"

    @PrimaryKey
    var primary: String = UUID.randomUUID().toString()

    @Index
    var messageId: String = ""

    @Index
    var sentDate: Date = Date(0)

    @Index
    var owner: String = ""

    var jid: String = ""

    @Index
    var kindRaw: String = ""

    var mimeType: String = ""
    var begin: Int = 0
    var end: Int = 0
    var metadataRaw: String = ""
    var isDownloaded: Boolean = false
    var isUploaded: Boolean = false
    var isMissed: Boolean = false
    var hasError: Boolean = false
    var conversationTypeRaw: String = ConversationType.REGULAR.rawValue
    var url: String? = null

    @Ignore
    var model: Model? = null

    @Ignore
    var temporaryData: ByteArray? = null

    var kind: Kind
        get() = Kind.fromRaw(kindRaw)
        set(value) {
            kindRaw = value.rawValue
        }

    var conversationType: ConversationType
        get() = ConversationType.fromRaw(conversationTypeRaw)
            ?: ConversationType.fromRaw(CommonConfigManager.lockedConversationType)
            ?: ConversationType.REGULAR
        set(value) {
            conversationTypeRaw = value.rawValue
        }

    var range: IntRange
        get() = begin until end
        set(value) {
            begin = value.first
            end = value.last + 1
        }

    var uploadUrl: Uri?
        get() = metadata?.get("putUri")?.let { Uri.parse((it as String).encodeUri()) }
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("putUri", value?.toString() ?: "") }
        }

    var localFileUrl: Uri?
        get() = metadata?.get("localFileUri")?.let { Uri.parse((it as String).encodeUri()) }
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("localFileUri", value?.toString() ?: "") }
        }

    var downloadUrl: Uri?
        get() = url?.let { Uri.parse(it.encodeUri()) }
        set(value) {
            val uri = value?.toString()
            metadata = metadata?.toMutableMap()?.apply { put("uri", uri ?: "") }
            url = uri
        }

    var videoPreviewKey: String?
        get() = metadata?.get("thumbnail") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("thumbnail", value ?: "") }
        }

    var videoOrientation: String?
        get() = metadata?.get("orientation") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("orientation", value ?: "") }
        }

    var date: String?
        get() = metadata?.get("date") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("date", value ?: "") }
        }

    var senderName: String?
        get() = metadata?.get("sender_name") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("sender_name", value ?: "") }
        }

    var videoDuration: String?
        get() = metadata?.get("video_duration") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("video_duration", value ?: "") }
        }

    var isDownloading: Boolean?
        get() = metadata?.get("is_downloading") as? Boolean
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("is_downloading", value ?: false) }
        }

    var name: String?
        get() = metadata?.get("name") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("name", value ?: "") }
        }

    var filename: String?
        get() = metadata?.get("filename") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("filename", value ?: "") }
        }

    var filehash: String?
        get() = metadata?.get("hash") as? String
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("hash", value ?: "") }
        }

    var fileID: Int?
        get() = metadata?.get("fileID") as? Int
        set(value) {
            metadata = metadata?.toMutableMap()?.apply { put("fileID", value ?: 0) }
        }

    fun loadModel(): Model? {
        model = Model(
            primary = primary,
            messageId = messageId,
            owner = owner,
            jid = jid,
            kindRaw = kindRaw,
            mimeType = mimeType,
            begin = begin,
            end = end,
            metadataRaw = metadataRaw,
            isDownloaded = isDownloaded,
            isOriginalMissed = isMissed
        )
        return model
    }

    var metadata: Map<String, Any>?
        get() {
            if (isInvalidated) return null
            return try {
                JSONObject(metadataRaw).toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot create JSON object from reference metadata with id: $messageId")
                null
            }
        }
        set(value) {
            metadataRaw = try {
                value?.let { JSONObject(it).toString() } ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "Cannot encode reference metadata with id: $messageId")
                ""
            }
        }

    val sizeInBytesRaw: Int
        get() = metadata?.get("size") as? Int ?: 0

    val sizeInBytes: String?
        get() {
            val size = metadata?.get("size") as? Int ?: return null
            return Formatter.formatShortFileSize(null, size.toLong())
                .replace(",", ".")
                .replace("MB", "MiB")
                .replace("KB", "KiB")
        }

    val sizeInPx: Size?
        get() {
            val height = metadata?.get("height") as? Int
            val width = metadata?.get("width") as? Int
            return if (height != null && width != null) Size(width, height) else null
        }

    var meteringLevels: List<Float>?
        get() = metadata?.get("meters")?.let { metersString ->
            (metersString as String).split(" ").mapNotNull { it.toFloatOrNull() }
        }
        set(value) {
            metadata = metadata?.toMutableMap()?.apply {
                put("meters", value?.joinToString(" ") { it.toString() } ?: "")
            }
        }

    val callState: VoIPCallState
        get() = metadata?.get("callState")?.let {
            VoIPCallState.fromRaw(it as Int)
        } ?: VoIPCallState.ENDED

    val xmlType: String
        get() = when (kind) {
            Kind.VOICE, Kind.MEDIA -> "mutable"
            else -> "mutable"
        }

    fun prepare() {
        if (isDownloaded) return
        when (kind) {
            Kind.VOICE -> {
                val uri = metadata?.get("uri") as? String
                val url = uri?.let { Uri.parse(it.encodeUri()) } ?: return
                // TODO: Implement OpusAudio equivalent for Android
                val messageId = this.messageId
                val jid = this.jid
                val metadataRaw = this.metadataRaw
                // Placeholder for OpusAudio logic
                // OpusAudio.add(url) { result, meters, duration ->
                //     if (!result) return@add
                //     try {
                //         realm.writeBlocking {
                //             query<MessageReferenceStorageItem>(
                //                 "messageId = $0 AND jid = $1 AND metadata_ = $2 AND isDownloaded = false",
                //                 messageId, jid, metadataRaw, false
                //             ).find().forEach { instance ->
                //                 instance.isDownloaded = true
                //                 instance.metadata = instance.metadata?.toMutableMap()?.apply {
                //                     put("meters", meters.joinToString(" ") { it.toString() })
                //                     put("duration", duration)
                //                 }
                //             }
                //         }
                //     } catch (e: Exception) {
                //         Log.e(TAG, e.message)
                //     }
                // }
            }
            Kind.MEDIA -> {
                if (mimeType == "video") {
                    if (CommonConfigManager.useFileEncryptionByDefault) {
                        val primary = this.primary
                        try {
                            realm.writeBlocking {
                                query<MessageReferenceStorageItem>("primary = $0", primary)
                                    .first().find()?.isDownloading = true
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "prepare: ${e.message}")
                        }
                        try {
                            val url = downloadUrl ?: return
                            val encryptedData = url.toURL().readBytes()
                            val keyb64 = metadata?.get("encryption-key") as? String
                            val ivb64 = metadata?.get("iv") as? String
                            if (keyb64 == null || ivb64 == null) return
                            val encryptionKeyRaw = Base64.getDecoder().decode(keyb64)
                            val ivRaw = Base64.getDecoder().decode(ivb64)
                            val gcmSpec = GCMParameterSpec(128, ivRaw)
                            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encryptionKeyRaw, "AES"), gcmSpec)
                            val decrypted = cipher.doFinal(encryptedData)
                            val appDir = File("/data/data/your.app.package/app_files") // Replace with actual path
                            val resultFile = File(appDir, url.toString().substringAfterLast("/"))
                            resultFile.writeBytes(decrypted)
                            try {
                                realm.writeBlocking {
                                    val instance = query<MessageReferenceStorageItem>("primary = $0", primary)
                                        .first().find()
                                    instance?.isDownloaded = true
                                    instance?.localFileUrl = Uri.fromFile(resultFile)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "prepare: ${e.message}")
                            }
                            val previewKey = videoPreviewKey
                            if (previewKey == null) {
                                val urlStr = downloadUrl?.toString() ?: return
                                val key = listOf(jid, owner, urlStr).prp()
                                val result = extractFrameFromVideo(key)
                                try {
                                    realm.writeBlocking {
                                        val instance = query<MessageReferenceStorageItem>("primary = $0", primary)
                                            .first().find()
                                        instance?.isDownloaded = true
                                        instance?.videoPreviewKey = key
                                        instance?.videoDuration = result.videoDuration ?: ""
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "prepare: ${e.message}")
                                }
                                return
                            }
                            extractFrameFromVideo(previewKey)
                        } catch (e: Exception) {
                            Log.e(TAG, "prepare: ${e.message}")
                        }
                    } else {
                        val previewKey = videoPreviewKey
                        if (previewKey == null) {
                            val url = downloadUrl?.toString() ?: return
                            val key = listOf(jid, owner, url).prp()
                            val result = extractFrameFromVideo(key)
                            try {
                                realm.writeBlocking {
                                    query<MessageReferenceStorageItem>(
                                        "messageId = $0 AND jid = $1 AND metadata_ = $2",
                                        messageId, jid, metadataRaw
                                    ).find().forEach { instance ->
                                        instance.isDownloaded = true
                                        instance.videoPreviewKey = key
                                        instance.videoDuration = result.videoDuration ?: ""
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "prepare: ${e.message}")
                            }
                            return
                        }
                        extractFrameFromVideo(previewKey)
                    }
                }
            }
            else -> {}
        }
    }

    fun extractFrameFromVideo(forKey: String): VideoFrameResult {
        if (!imageCache.isCached(forKey)) {
            var orientation: BitmapOrientation = BitmapOrientation.UP
            videoOrientation?.let { orient ->
                when (Orientations.fromRaw(orient)) {
                    Orientations.PORTRAIT -> orientation = BitmapOrientation.RIGHT
                    Orientations.PORTRAIT_UPSIDE_DOWN -> orientation = BitmapOrientation.LEFT
                    Orientations.LANDSCAPE_RIGHT -> orientation = BitmapOrientation.UP
                    Orientations.LANDSCAPE_LEFT -> orientation = BitmapOrientation.DOWN
                    else -> {}
                }
            }

            val url = localFileUrl ?: downloadUrl ?: return VideoFrameResult(null, null, null)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(url.toString())
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                val timeForFrame = durationMs / 2
                val bitmap = retriever.getFrameAtTime(timeForFrame * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                bitmap?.let {
                    imageCache.put(forKey, it)
                }

                val seconds = (durationMs / 1000.0) % 60
                val minutes = ((durationMs / 1000.0 - seconds) % 60).toInt()
                val durationStr = String.format("%d:%02.0f", minutes, seconds).replace(" ", "0")

                return VideoFrameResult(
                    width = bitmap?.width?.toFloat(),
                    height = bitmap?.height?.toFloat(),
                    videoDuration = durationStr
                )
            } catch (e: Exception) {
                Log.e(TAG, "extractFrameFromVideo: ${e.message}")
                return VideoFrameResult(null, null, null)
            } finally {
                retriever.release()
            }
        }
        return VideoFrameResult(null, null, null)
    }
}

// Stubs for dependencies
enum class ConversationType(val rawValue: String) {
    REGULAR("regular"),
    OMEMO("omemo"),
    OMEMO1("omemo1"),
    AXOLOTL("axolotl"),
    GROUP("group");

    companion object {
        fun fromRaw(raw: String): ConversationType? =
            values().find { it.rawValue == raw }
    }
}

enum class VoIPCallState(val rawValue: Int) {
    ENDED(0); // Simplified stub

    companion object {
        fun fromRaw(raw: Int): VoIPCallState = ENDED
    }
}

open class MessageStorageItem : RealmObject {
    var primary: String = ""
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()
    var inlineForwards: RealmList<MessageForwardsInlineStorageItem> = realmListOf()
}

open class MessageForwardsInlineStorageItem : RealmObject {
    var messageId: String = ""
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()
    var subforwards: RealmList<MessageForwardsInlineStorageItem> = realmListOf()
}

object CommonConfigManager {
    val useFileEncryptionByDefault: Boolean = false
    val lockedConversationType: String = "regular"
}

enum class Orientations(val rawValue: String) {
    PORTRAIT("portrait"),
    PORTRAIT_UPSIDE_DOWN("portraitUpsideDown"),
    LANDSCAPE_RIGHT("landscapeRight"),
    LANDSCAPE_LEFT("landscapeLeft"),
    UNKNOWN("unknown");

    companion object {
        fun fromRaw(raw: String): Orientations =
            values().find { it.rawValue == raw } ?: UNKNOWN
    }
}

data class VideoFrameResult(
    val width: Float?,
    val height: Float?,
    val videoDuration: String?
)

enum class BitmapOrientation {
    UP, RIGHT, DOWN, LEFT
}

// Placeholder image cache
object ImageCache {
    private val cache = LruCache<String, Bitmap>(100)

    fun isCached(forKey: String): Boolean = cache.get(forKey) != null

    fun store(image: Bitmap, forKey: String) {
        cache.put(forKey, image)
    }
}

// Utility extensions
fun String.encodeUri(): String = Uri.encode(this)

fun Uri.toURL(): java.net.URL = java.net.URL(this.toString())

val realmConfiguration = RealmConfiguration.create(
    schema = setOf(
        MessageReferenceStorageItem::class,
        MessageStorageItem::class,
        MessageForwardsInlineStorageItem::class
    )
)