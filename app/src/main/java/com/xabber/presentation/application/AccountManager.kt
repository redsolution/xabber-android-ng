package com.xabber.presentation.application

import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.models.xmpp.avatar.AvatarStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query

object AccountManager {
   private val realm = Realm.open(defaultRealmConfig())
    private const val DEFAULT_COLOR = "blue"

   fun getColorKey(): String {
       var colorKey = DEFAULT_COLOR
       realm.writeBlocking {
          val primaryAccount = this.query(AccountStorageItem::class, "order = 0").first().find()
           colorKey = primaryAccount?.colorKey?: DEFAULT_COLOR
       }
       return colorKey
   }

//    fun getAvatar(): AvatarDto {
//        realm.writeBlocking {
//            val avatar = this.query(AvatarStorageItem::class)
//        }
//    }

   // private fun
}