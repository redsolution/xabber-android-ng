package com.xabber.xmpp.avatar


import android.util.Log
import com.xabber.utils.prp
import com.xabber.utils.toMap
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.json.JSONObject

class AvatarStorageItem : RealmObject {
    companion object {
        private const val TAG = "AvatarStorageItem"

        fun genPrimary(jid: String, owner: String): String {
            return listOf(jid, owner).prp()
        }
    }

    @PrimaryKey
    var primary: String = ""  // jid + owner
    var jid: String = ""
    var owner: String = ""
    var imageHash: String = ""
    var fileUri: String = ""
    private var imageMetadataRaw: String? = ""
    var kind_: String = AvatarKind.None.rawValue
    var uploadUrl: String? = null
    var image96: String? = null
    var image128: String? = null
    var image192: String? = null
    var image384: String? = null
    var image512: String? = null
    var kind: AvatarKind
        get() = AvatarKind.values().firstOrNull { it.rawValue == kind_ } ?: AvatarKind.None
        set(newValue: AvatarKind) {
            kind_ = newValue.rawValue
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
}

