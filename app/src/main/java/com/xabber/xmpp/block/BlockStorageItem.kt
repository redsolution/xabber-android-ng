package com.xabber.xmpp.block

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date


open class BlockStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""

    var jid: String = ""
    var owner: String = ""
    var timestamp: Date = Date()
    var isGroupchatInvitation: Boolean = false


    fun set(jid: String, owner: String) {
        this.primary = listOf(jid, owner).prp()
        this.jid = jid
        this.owner = owner
        this.timestamp = Date()
        val resource = jid.split("/").getOrNull(1)
        this.isGroupchatInvitation = resource?.toDoubleOrNull() != null
    }
}