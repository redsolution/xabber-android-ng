package com.xabber.models.xmpp.roster

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class RosterGroupStorageItem: RealmObject {
    @PrimaryKey
    var primary: String = ""
    var owner: String = ""
    var name: String = ""
    var isSystemGroup: Boolean = false      // general
    var isCollapsed: Boolean = false
    var order: Int = 0
    var contacts: RealmList<RosterStorageItem> = realmListOf()
}