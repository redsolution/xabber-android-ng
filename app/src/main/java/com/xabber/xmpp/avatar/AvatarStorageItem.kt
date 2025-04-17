package com.xabber.xmpp.avatar

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import android.net.Uri
import android.util.Log
import com.xabber.utils.toMap
import com.xabber.xmpp.notifications.toMap
import org.json.JSONObject


open class AvatarStorageItem : RealmObject {
    companion object {
        private const val TAG = "AvatarStorageItem"

        fun genPrimary(jid: String, owner: String): String {
            return listOf(jid, owner).prp()
        }
    }

    @PrimaryKey
    var primary: String = ""

    @Index
    var jid: String = ""

    @Index
    var owner: String = ""

    var imageHash: String? = null
    private var imageMetadataRaw: String? = null
    private var kindRaw: String = Kind.NONE.rawValue
    var uploadUrl: String? = null
    var image32: String? = null
    var image48: String? = null
    var image64: String? = null
    var image96: String? = null
    var image128: String? = null
    var image192: String? = null
    var image256: String? = null
    var image384: String? = null
    var image512: String? = null
    var imageOriginal: String? = null
    var isPrepared: Boolean = false


    var kind: Kind
        get() = Kind.fromRaw(kindRaw)
        set(value) {
            kindRaw = value.rawValue
        }


    var imageMetadata: Map<String, Any>?
        get() = imageMetadataRaw?.let { raw ->
            try {
                JSONObject(raw).toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot parse avatar metadata for $primary: ${e.message}")
                null
            }
        }
        set(value) {
            imageMetadataRaw = value?.let { map ->
                try {
                    JSONObject(map).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot encode avatar metadata for $primary: ${e.message}")
                    null
                }
            }
        }

    val uri: Uri?
        get() = imageOriginal?.let { Uri.parse(it) }
}


enum class Kind(val rawValue: String) {
    NONE("none"),
    VCARD("vcard"),
    PEP("pep"),
    XABBER("xabber");

    companion object {

        fun fromRaw(raw: String): Kind =
            values().find { it.rawValue == raw } ?: NONE
    }
}

