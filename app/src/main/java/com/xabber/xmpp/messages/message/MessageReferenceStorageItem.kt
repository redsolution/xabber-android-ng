package com.xabber.xmpp.messages.message
//
//import com.xabber.utils.prp
//import io.realm.kotlin.Realm
//import io.realm.kotlin.ext.query
//import io.realm.kotlin.types.RealmObject
//import io.realm.kotlin.types.annotations.Ignore
//import io.realm.kotlin.types.annotations.Index
//import io.realm.kotlin.types.annotations.PrimaryKey
//import android.net.Uri
//import android.util.Log
//import org.json.JSONObject
//import java.io.File
//import java.util.Date
//import java.util.UUID
//import javax.crypto.Cipher
//import javax.crypto.spec.GCMParameterSpec
//import javax.crypto.spec.SecretKeySpec
//import android.graphics.Bitmap
//import android.media.MediaMetadataRetriever
//import android.util.LruCache
//import android.util.Size
//import java.util.Base64
//import android.text.format.Formatter
//import com.xabber.utils.toMap
//import com.xabber.xmpp.messages.MessageStorageItem
//import io.realm.kotlin.RealmConfiguration
//import io.realm.kotlin.UpdatePolicy
//import io.realm.kotlin.ext.realmListOf
//import io.realm.kotlin.types.RealmList
//
///**
// * Represents a message reference storage item, tracking metadata for message attachments.
// * Persisted using Realm for database storage.
// */
//private const val TAG = "MessageReferenceStorageItem"
//
//open class MessageReferenceStorageItem : RealmObject {
//    data class Model(
//        val primary: String,
//        val messageId: String,
//        val owner: String,
//        val jid: String,
//        val kind_: String,
//        val mimeType: String,
//        val begin: Int,
//        val end: Int,
//        val metadata_: String,
//        val isDownloaded: Boolean,
//        val isOriginalMissed: Boolean
//    ) {
//        val kind: Kind
//            get() = Kind.fromRaw(kind_)
//
//        val range: IntRange
//            get() = begin until end
//
//        val metadata: Map<String, Any>?
//            get() = metadata_.toByteArray(Charsets.UTF_8).let { data ->
//                try {
//                    JSONObject(String(data, Charsets.UTF_8)).toMap()
//                } catch (e: Exception) {
//                    Log.e(
//                        TAG,
//                        "Cannot create JSON object from reference metadata with id: $messageId"
//                    )
//                    null
//                }
//            }
//
//        val sizeInBytesRaw: Int
//            get() = metadata?.get("size") as? Int ?: 0
//
//        val sizeInBytes: String?
//            get() {
//                val size = metadata?.get("size") as? Int ?: return null
//                return Formatter.formatShortFileSize(null, size.toLong())
//                    .replace(",", ".")
//                    .replace("MB", "MiB")
//                    .replace("KB", "KiB")
//            }
//
//        val sizeInPx: Size?
//            get() {
//                val height = metadata?.get("height") as? Int
//                val width = metadata?.get("width") as? Int
//                return if (height != null && width != null) Size(width, height) else null
//            }
//
//
//        val meteringLevels: List<Float>?
//            get() {
//                val metersString = metadata?.get("meters") as? String
//                return metersString?.split(" ")?.mapNotNull { it.toFloatOrNull() }
//            }
//
//        val uploadUrl: Uri?
//            get() {
//                val uri = metadata?.get("putUri") as? String
//                return uri?.let { Uri.parse(it.encodeUri()) }
//            }
//
//        val localFileUrl: Uri?
//            get() {
//                val uri = metadata?.get("localFileUri") as? String
//                return uri?.let { Uri.parse(it.encodeUri()) }
//            }
//
//        val downloadUrl: Uri?
//            get() {
//                val uri = metadata?.get("uri") as? String
//                return uri?.let { Uri.parse(it.encodeUri()) }
//            }
//
//        val videoPreviewKey: String?
//            get() = metadata?.get("thumbnail") as? String
//
//        val videoOrientation: String?
//            get() = metadata?.get("orientation") as? String
//
//        val audioDuration: Float?
//            get() = metadata?.get("duration") as? Float
//
//        val date: String?
//            get() = metadata?.get("date") as? String
//
//        val senderName: String?
//            get() = metadata?.get("sender_name") as? String
//
//        val duration: String?
//            get() = metadata?.get("video_duration") as? String
//    }
//
//    enum class Kind(val rawValue: String) {
//        MEDIA("media"),
//        VOICE("voice"),
//        FORWARD("forward"),
//        MARKUP("markup"),
//        MENTION("mention"),
//        QUOTE("quote"),
//        GROUPCHAT("groupchat"),
//        CALL("call"),
//        SYSTEM_MESSAGE("system-message"),
//        NONE("");
//
//        companion object {
//            fun fromRaw(raw: String): Kind =
//                values().find { it.rawValue == raw } ?: NONE
//        }
//    }
//
//
//    fun primaryKey(): String = "primary"
//
//    @PrimaryKey
//    var primary: String = UUID.randomUUID().toString()
//    var messageId: String = ""
//    @Ignore var sentDate: Date = Date(0)
//    var owner: String = ""
//    var jid: String = ""
//    var kind_: String = ""
//    var mimeType: String = ""
//    var begin: Int = 0
//    var end: Int = 0
//    var metadata_: String = ""
//    var isDownloaded: Boolean = false
//    var isUploaded: Boolean = false
//    var isMissed: Boolean = false
//    var hasError: Boolean = false
//    var conversationTypeRaw: String = ConversationType.REGULAR.rawValue
//    var url: String? = null
//
//    @Ignore
//    var model: Model? = null
//
//    @Ignore
//    var temporaryData: ByteArray? = null
//
//    var kind: Kind
//        get() = Kind.fromRaw(kind_)
//        set(value) {
//            kind_ = value.rawValue
//        }
//
//    var conversationType: ConversationType
//        get() = ConversationType.fromRaw(conversationTypeRaw)
//            ?: ConversationType.fromRaw(CommonConfigManager.lockedConversationType)
//            ?: ConversationType.REGULAR
//        set(value) {
//            conversationTypeRaw = value.rawValue
//        }
//
//    var range: IntRange
//        get() = begin until end
//        set(value) {
//            begin = value.first
//            end = value.last + 1
//        }
//
//    var uploadUrl: Uri?
//        get() = metadata?.get("putUri")?.let { Uri.parse((it as String).encodeUri()) }
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply { put("putUri", value?.toString() ?: "") }
//        }
//
//    var localFileUrl: Uri?
//        get() = metadata?.get("localFileUri")?.let { Uri.parse((it as String).encodeUri()) }
//        set(value) {
//            metadata =
//                metadata?.toMutableMap()?.apply { put("localFileUri", value?.toString() ?: "") }
//        }
//
//    var downloadUrl: Uri?
//        get() = url?.let { Uri.parse(it.encodeUri()) }
//        set(value) {
//            val uri = value?.toString()
//            metadata = metadata?.toMutableMap()?.apply { put("uri", uri ?: "") }
//            url = uri
//        }
//
//    var videoPreviewKey: String?
//        get() = metadata?.get("thumbnail") as? String
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply { put("thumbnail", value ?: "") }
//        }
//
//    var videoOrientation: String?
//        get() = metadata?.get("orientation") as? String
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply { put("orientation", value ?: "") }
//        }
//
//    var date: String?
//        get() = metadata?.get("date") as? String
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply { put("date", value ?: "") }
//        }
//
//    var senderName: String?
//        get() = metadata?.get("sender_name") as? String
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply { put("sender_name", value ?: "") }
//        }
//
//    var videoDuration: String?
//        get() = metadata?.get("video_duration") as? String
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply { put("video_duration", value ?: "") }
//        }
//
//    var isDownloading: Boolean?
//        get() = metadata?.get("is_downloading") as? Boolean
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply { put("is_downloading", value ?: false) }
//        }
//
//    var name: String?
//        get() = metadata?.get("name") as? String
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply { put("name", value ?: "") }
//        }
//
//    var filename: String?
//        get() = metadata?.get("filename") as? String
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply { put("filename", value ?: "") }
//        }
//
//    var filehash: String?
//        get() = metadata?.get("hash") as? String
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply { put("hash", value ?: "") }
//        }
//
//    var fileID: Int?
//        get() = metadata?.get("fileID") as? Int
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply { put("fileID", value ?: 0) }
//        }
//
//    fun loadModel(): Model? {
//        model = Model(
//            primary = primary,
//            messageId = messageId,
//            owner = owner,
//            jid = jid,
//            kind_ = kind_,
//            mimeType = mimeType,
//            begin = begin,
//            end = end,
//            metadata_ = metadata_,
//            isDownloaded = isDownloaded,
//            isOriginalMissed = isMissed
//        )
//        return model
//    }
//
//    var metadata: Map<String, Any>?
//        get() {
//            if (metadata_.isEmpty()) return null
//            return try {
//                JSONObject(metadata_).toMap()
//            } catch (e: Exception) {
//                Log.e(TAG, "Cannot create JSON object from reference metadata with id: $messageId")
//                null
//            }
//        }
//        set(value) {
//            metadata_ = try {
//                value?.let { JSONObject(it).toString() } ?: ""
//            } catch (e: Exception) {
//                Log.e(TAG, "Cannot encode reference metadata with id: $messageId")
//                ""
//            }
//        }
//
//    val sizeInBytes_: Int
//        get() = metadata?.get("size") as? Int ?: 0
//
//    val sizeInBytes: String?
//        get() {
//            val size = metadata?.get("size") as? Int ?: return null
//            return Formatter.formatShortFileSize(null, size.toLong())
//                .replace(",", ".")
//                .replace("MB", "MiB")
//                .replace("KB", "KiB")
//        }
//
//    val sizeInPx: Size?
//        get() {
//            val height = metadata?.get("height") as? Int
//            val width = metadata?.get("width") as? Int
//            return if (height != null && width != null) Size(width, height) else null
//        }
//
//    var meteringLevels: List<Float>?
//        get() = metadata?.get("meters")?.let { metersString ->
//            (metersString as String).split(" ").mapNotNull { it.toFloatOrNull() }
//        }
//        set(value) {
//            metadata = metadata?.toMutableMap()?.apply {
//                put("meters", value?.joinToString(" ") { it.toString() } ?: "")
//            }
//        }
//
//    val callState: VoIPCallState
//        get() = metadata?.get("callState")?.let {
//            VoIPCallState.fromRaw(it as Int)
//        } ?: VoIPCallState.ENDED
//
//    val xmlType: String
//        get() = when (kind) {
//            Kind.VOICE, Kind.MEDIA -> "mutable"
//            else -> "mutable"
//        }
//
//
//}
//
//
//// Stubs for dependencies
//enum class ConversationType(val rawValue: String) {
//    REGULAR("regular"),
//    OMEMO("omemo"),
//    OMEMO1("omemo1"),
//    AXOLOTL("axolotl"),
//    GROUP("group");
//
//    companion object {
//        fun fromRaw(raw: String): ConversationType? =
//            values().find { it.rawValue == raw }
//    }
//}
//
//enum class VoIPCallState(val rawValue: Int) {
//    ENDED(0); // Simplified stub
//
//    companion object {
//        fun fromRaw(raw: Int): VoIPCallState = ENDED
//    }
//}
//
//
//object CommonConfigManager {
//    val useFileEncryptionByDefault: Boolean = false
//    val lockedConversationType: String = "regular"
//}
//
//enum class Orientations(val rawValue: String) {
//    PORTRAIT("portrait"),
//    PORTRAIT_UPSIDE_DOWN("portraitUpsideDown"),
//    LANDSCAPE_RIGHT("landscapeRight"),
//    LANDSCAPE_LEFT("landscapeLeft"),
//    UNKNOWN("unknown");
//
//    companion object {
//        fun fromRaw(raw: String): Orientations =
//            values().find { it.rawValue == raw } ?: UNKNOWN
//    }
//}
//
//data class VideoFrameResult(
//    val width: Float?,
//    val height: Float?,
//    val videoDuration: String?
//)
//
//enum class BitmapOrientation {
//    UP, RIGHT, DOWN, LEFT
//}
//
//
//
//// Utility extensions
//fun String.encodeUri(): String = Uri.encode(this)
//
//fun Uri.toURL(): java.net.URL = java.net.URL(this.toString())
//
//val realmConfiguration = RealmConfiguration.create(
//    schema = setOf(
//        MessageReferenceStorageItem::class,
//        MessageStorageItem::class,
//        MessageForwardsInlineStorageItem::class
//    )
//)