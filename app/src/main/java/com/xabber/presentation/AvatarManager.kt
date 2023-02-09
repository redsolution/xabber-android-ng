package com.xabber.presentation

import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.xmpp.avatar.AvatarStorageItem
import io.realm.kotlin.Realm

object AvatarManager {
    val realm = Realm.open(defaultRealmConfig())

    fun getAvatar(id: String): AvatarStorageItem? {
        var avatar: AvatarStorageItem? = null
        realm.writeBlocking {
           val item = this.query(AvatarStorageItem::class, "primary = '$id'").first().find()
            avatar = item
        }
        return avatar
    }

}
