package com.xabber.presentation

import com.xabber.data_base.defaultRealmConfig

import io.realm.kotlin.Realm

object AvatarManager {
    val realm = Realm.open(defaultRealmConfig())

    fun getAvatar(id: String): com.xabber.data_base.models.avatar.AvatarStorageItem? {
        var avatar: com.xabber.data_base.models.avatar.AvatarStorageItem? = null
        realm.writeBlocking {
           val item = this.query(com.xabber.data_base.models.avatar.AvatarStorageItem::class, "primary = '$id'").first().find()
            avatar = item
        }
        return avatar
    }

}
