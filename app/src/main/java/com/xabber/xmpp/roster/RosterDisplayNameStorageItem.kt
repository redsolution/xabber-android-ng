package com.xabber.xmpp.roster

import com.xabber.utils.prp
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey


open class RosterDisplayNameStorageItem : RealmObject {
    companion object {

        fun genPrimary(jid: String, owner: String): String {
            return listOf(jid, owner).prp()
        }

    }

    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var displayName: String = ""
}