package com.xabber.presentation.application.activity

import com.xabber.data.xmpp.account.AccountStorageItem
import com.xabber.data.xmpp.presences.ResourceStorageItem
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.query

class BdCommunicator : BdRepository {

    override fun checkUserIsRegister(): Boolean {
        val config =
            RealmConfiguration.Builder(setOf(AccountStorageItem::class, ResourceStorageItem::class))
                .build()
        val realm = Realm.open(config)
        val accountCollection = realm
            .query<AccountStorageItem>()
            .find()
        val comparisonResult = accountCollection.size > 0
        realm.close()
        return comparisonResult
    }
}