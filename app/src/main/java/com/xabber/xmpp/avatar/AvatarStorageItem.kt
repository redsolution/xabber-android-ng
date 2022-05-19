package com.xabber.xmpp.avatar

import com.xabber.utils.storage.prp
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

class AvatarStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""

    var jid: String = ""
    var owner: String = ""
    var imageHash: String = ""
    var fileUri: String = ""
    var imageMetadata_: String = ""
    var kind_: String = AvatarKind.None.rawValue
    var uploadUrl: String? = null
    var image96: String? = null
    var image128: String? = null
    var image192: String? = null
    var image384: String? = null
    var image512: String? = null

    var kind: AvatarKind
        get() = AvatarKind.values().firstOrNull { it.rawValue == kind_ } ?: AvatarKind.None
        set(newValue: AvatarKind) { kind_ = newValue.rawValue }

    companion object {
        fun genPrimary(jid: String, owner: String): String {
            return prp(strArray = arrayOf(jid, owner))
        }
    }
}