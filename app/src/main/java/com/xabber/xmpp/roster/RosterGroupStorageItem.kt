package com.xabber.xmpp.roster
//
//import android.content.Context
//import com.xabber.R
//import com.xabber.utils.prp
//import io.realm.kotlin.ext.realmListOf
//import io.realm.kotlin.types.RealmList
//import io.realm.kotlin.types.RealmObject
//import io.realm.kotlin.types.annotations.PrimaryKey
//
//
//open class RosterGroupStorageItem : RealmObject {
//    companion object {
//        const val SYSTEM_GROUP_NAME = "com.xabber.system.roster.group"
//        const val NOT_IN_ROSTER_GROUP_NAME = "com.xabber.system.roster.noInRoster"
//
//        fun genPrimary(name: String, owner: String): String {
//            return listOf(name, owner).prp()
//        }
//    }
//
//    @PrimaryKey
//    var primary: String = ""
//    var owner: String = ""
//    var name: String = ""
//    var isSystemGroup: Boolean = false
//    var isCollapsed: Boolean = false
//    var order: Int = 0
//    var contacts: RealmList<RosterStorageItem> = realmListOf()
//
//
//
//}
//
