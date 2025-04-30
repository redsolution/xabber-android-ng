package com.xabber.xmpp.x509

import com.xabber.utils.prp
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date


open class X509StorageItem : RealmObject {
    companion object {

        fun genPrimary(owner: String, jid: String): String {
            return listOf(owner, jid).prp()
        }
    }

    fun primaryKey(): String? = "primary"


    @PrimaryKey
    var primary: String = ""

    var owner: String = ""
    var jid: String = ""
    var certData: ByteArray? = null
    var stamp: Double = 0.0
    @Ignore
    var dateChanged: Date? = null
}