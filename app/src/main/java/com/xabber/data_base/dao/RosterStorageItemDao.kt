package com.xabber.data_base.dao

import com.xabber.models.xmpp.roster.RosterStorageItem
import io.realm.kotlin.Realm

class RosterStorageItemDao(private val realm: Realm) {

    suspend fun setBlocked(primary: String, isBlocked: Boolean) {
        realm.write {
            val rosterStorageItem =
                this.query(RosterStorageItem::class, "primary = '$primary'").first().find()
            if (rosterStorageItem != null) rosterStorageItem.isBlocked = isBlocked
        }
    }


}