package com.xabber.data_base.dao

import com.xabber.models.xmpp.avatar.AvatarStorageItem
import io.realm.kotlin.Realm

class AvatarStorageItemDao(private val realm: Realm) {

    fun getAvatar(primary: String): AvatarStorageItem? =
        realm.query(AvatarStorageItem::class, "primary = '$primary'").first().find()

    fun createAvatar(primary: String, jid: String, owner: String, uri: String) {
        realm.writeBlocking {
            this.copyToRealm(AvatarStorageItem().apply {
                this.primary = primary
                this.jid = jid
                this.owner = owner
                this.fileUri = uri
            })
        }
    }

}