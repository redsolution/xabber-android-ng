package com.xabber.presentation.application.manage

import android.util.Log
import com.xabber.data_base.defaultRealmConfig
import com.xabber.dto.AvatarDto
import io.realm.kotlin.Realm


object AccountManager {
    private val realm = Realm.open(defaultRealmConfig())

    fun getAvatar(): AvatarDto? {
        var avatarDto: AvatarDto? = null
            realm.writeBlocking {
                val id = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "order = 0").first().find()?.jid
                if (id != null) {
                    val realmAvatar =
                        this.query(com.xabber.data_base.models.avatar.AvatarStorageItem::class, "primary = '$id'").first().find()
                    if (realmAvatar != null) avatarDto = AvatarDto(
                        realmAvatar.primary,
                        jid = realmAvatar.jid,
                        owner = realmAvatar.owner,
                        uploadUrl = realmAvatar.uploadUrl,
                        fileUri = realmAvatar.fileUri,
                        image96 = realmAvatar.image96,
                        image128 = realmAvatar.image128,
                        image192 = realmAvatar.image192,
                        image384 = realmAvatar.image384,
                        image512 = realmAvatar.image512
                    )
                }
        }
        return avatarDto
    }

    fun getHaveAvatar(): Boolean {
        var hasAvatar = false
        realm.writeBlocking {
            val primaryAccount = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "order = 0").first().find()
            hasAvatar = primaryAccount?.hasAvatar ?: false
        }
        return hasAvatar
    }

    fun getInitials(): String {
        var initials = ""
        realm.writeBlocking {
            val primaryAccount = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "order = 0").first().find()
          val name = primaryAccount?.username
            initials =
               name?.split(' ')?.mapNotNull { it.firstOrNull()?.toString() }?.reduce { acc, s -> acc + s }
                    ?: ""
          if (initials.length > 2)  initials = initials.substring(0, 2)
        }
        return initials
    }

}
