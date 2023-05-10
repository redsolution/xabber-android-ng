package com.xabber.data_base.models.avatar


import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class AvatarStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""  // jid + owner
    var jid: String = ""
    var owner: String = ""
    var imageHash: String = ""
    var fileUri: String = ""
    var imageMetadata_: String = ""
    var kind_: String = com.xabber.data_base.models.avatar.AvatarKind.None.rawValue
    var uploadUrl: String? = null
    var image96: String? = null
    var image128: String? = null
    var image192: String? = null
    var image384: String? = null
    var image512: String? = null
    var kind: com.xabber.data_base.models.avatar.AvatarKind
        get() = com.xabber.data_base.models.avatar.AvatarKind.values().firstOrNull { it.rawValue == kind_ } ?: com.xabber.data_base.models.avatar.AvatarKind.None
        set(newValue: com.xabber.data_base.models.avatar.AvatarKind) {
            kind_ = newValue.rawValue
        }

}
