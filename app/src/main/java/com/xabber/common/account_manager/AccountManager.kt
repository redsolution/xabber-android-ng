package com.xabber.common.account_manager

import com.xabber.xmpp.account.Account
import com.xabber.xmpp.account.AccountStorageItem
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.query
import io.realm.query.find

class AccountManager {
    companion object {
        val shared = AccountManager()
    }

    var users: ArrayList<Account> = ArrayList<Account>()

    fun isAccountListEmpty(): Boolean {
        val realmConfig = RealmConfiguration
            .Builder(setOf(AccountStorageItem::class))
            .build()
        val realm = Realm.open(configuration = realmConfig)
        val accounts = realm.query<AccountStorageItem>().find()
        realm.close()
        return accounts.isEmpty()
    }

    fun load(autoConnect: Boolean) {
        val realmConfig = RealmConfiguration
            .Builder(setOf(AccountStorageItem::class))
            .build()
        val realm = Realm.open(configuration = realmConfig)
        val accounts = realm
            .query<AccountStorageItem>("enabled == true")
            .find()
            .map { return  }

    }

}