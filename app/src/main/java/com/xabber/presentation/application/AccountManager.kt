package com.xabber.presentation.application

import android.util.Log
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.AvatarDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.avatar.AvatarStorageItem
import io.realm.kotlin.Realm


object AccountManager {
    private val realm = Realm.open(defaultRealmConfig())
    private const val DEFAULT_COLOR = "blue"

    fun getColorKey(): String {
        var colorKey = DEFAULT_COLOR
        realm.writeBlocking {
            val primaryAccount = this.query(AccountStorageItem::class, "order = 0").first().find()
            colorKey = primaryAccount?.colorKey ?: DEFAULT_COLOR
        }
        return colorKey
    }

    fun getAvatar(): AvatarDto? {
        var avatarDto: AvatarDto? = null
            realm.writeBlocking {
                val id = this.query(AccountStorageItem::class, "order = 0").first().find()?.jid
                if (id != null) {
                    val realmAvatar =
                        this.query(AvatarStorageItem::class, "primary = '$id'").first().find()
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
            val primaryAccount = this.query(AccountStorageItem::class, "order = 0").first().find()
            hasAvatar = primaryAccount?.hasAvatar ?: false
        }
        return hasAvatar
    }

    fun getInitials(): String {
        var initials = ""
        realm.writeBlocking {
            val primaryAccount = this.query(AccountStorageItem::class, "order = 0").first().find()
          val name = primaryAccount?.nickname
            initials =
               name?.split(' ')?.mapNotNull { it.firstOrNull()?.toString() }?.reduce { acc, s -> acc + s }
                    ?: ""
          if (initials.length > 2)  initials = initials.substring(0, 2)
            Log.d("eee", "initials = $initials")
        }
        return initials
    }

}
