package com.xabber.data_base.models.roster

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class BlockStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""
    var jid: String = ""
    var owner: String = ""
    var timestamp: Long = 0
    var isGroupchatInvitation: Boolean = false
}
