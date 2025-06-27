package com.xabber.xmpp.roster

import android.util.Log
import com.xabber.data_base.defaultRealmConfig
import com.xabber.utils.prp
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class RosterDisplayNameStorageItem : RealmObject {
    @PrimaryKey
    var primary: String = ""
    var owner: String = ""
    var displayName: String = ""

    companion object {
        fun genPrimary(jid: String, owner: String): String {
            return listOf(jid, owner).prp()
        }

        suspend fun createOrUpdate(jid: String, owner: String, displayName: String, commitTransaction: Boolean) {
            withContext(Dispatchers.IO) {
                val realm = Realm.open(defaultRealmConfig())
                try {
                    val primary = genPrimary(jid, owner)
                    realm.write {
                        val instance = query<RosterDisplayNameStorageItem>("primary = $0", primary).first().find()
                        if (instance != null) {
                            copyToRealm(findLatest(instance)!!.apply {
                                this.displayName = displayName
                            }, UpdatePolicy.ALL)
                        } else {
                            copyToRealm(RosterDisplayNameStorageItem().apply {
                                this.primary = primary
                                this.owner = owner
                                this.displayName = displayName
                            }, UpdatePolicy.ALL)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RosterDisplayNameStorageItem", "Error in createOrUpdate: ${e.message}", e)
                } finally {
                    realm.close()
                }
            }
        }
    }
}

fun List<String>.prp(): String {
    return joinToString(separator = "_")
}

fun Array<String>.prp(): String {
    return joinToString(separator = "_")
}