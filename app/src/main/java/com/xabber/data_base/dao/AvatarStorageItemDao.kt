package com.xabber.data_base.dao

import io.realm.kotlin.Realm

class AvatarStorageItemDao(private val realm: Realm) {

    fun getAvatar(primary: String): com.xabber.data_base.models.avatar.AvatarStorageItem? =
        realm.query(
            com.xabber.data_base.models.avatar.AvatarStorageItem::class,
            "primary = '$primary'"
        ).first().find()

    fun createAvatar(primary: String, jid: String, owner: String, uri: String) {
        realm.writeBlocking {
            this.copyToRealm(com.xabber.data_base.models.avatar.AvatarStorageItem().apply {
                this.primary = primary
                this.jid = jid
                this.owner = owner
                this.fileUri = uri
            })
        }
    }

}