package com.xabber.xmpp.favorites

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey


const val imageName: String = "bookmark.fill"

open class XMPPFavoritesManagerStorageItem: RealmObject {

    companion object{
        fun primaryKey(): String? = "primary"
        fun genPrimary(jid: String, owner: String): String {
            return listOf(jid, owner).prp()
        }
    }
    @PrimaryKey
    val primary: String = ""
    @Index
    val owner:String=""
    @Index
    val node:String=""


}