package com.xabber.xmpp.favorites

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey


const val imageName: String = "bookmark.fill"

open class XMPPFavoritesManagerStorageItem: RealmObject {
    fun primaryKey(): String? = "primary"
    companion object{

        fun genPrimary(jid: String, owner: String): String {
            return listOf(jid, owner).prp()
        }
    }
    @PrimaryKey
    var primary: String = ""

    var owner:String=""
    var node:String=""


}