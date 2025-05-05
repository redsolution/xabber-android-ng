package com.xabber.data_base.models.roster

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date

class BlockStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""
    var jid: String = ""
    var owner: String = ""
    var timestamp: Long = 0
    var isGroupchatInvitation: Boolean = false

    fun set(jid: String, owner: String) {
        this.primary = listOf(jid, owner).prp()
        this.jid = jid
        this.owner = owner
        this.timestamp = 0
        val resource = jid.split("/").getOrNull(1)
        this.isGroupchatInvitation = resource?.toDoubleOrNull() != null
    }
}
