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


        fun createOrUpdate(
            jid: String,
            owner: String,
            displayName: String,
            realm: Realm,
            commitTransaction: Boolean
        ) {
            val primary = genPrimary(jid, owner)
            val existing = realm.query<RosterDisplayNameStorageItem>("primary = $0", primary)
                .first().find()

            fun operation() {
                if (existing != null) {
                    existing.displayName = displayName
                } else {
                    val instance = RosterDisplayNameStorageItem().apply {
                        this.primary = primary
                        this.owner = owner
                        this.displayName = displayName
                    }
                    realm.copyToRealm(instance, updatePolicy = UpdatePolicy.MODIFIED)
                }
            }

            if (commitTransaction) {
                realm.writeBlocking { operation() }
            } else {
                operation()
            }
        }
    }

    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var displayName: String = ""
}