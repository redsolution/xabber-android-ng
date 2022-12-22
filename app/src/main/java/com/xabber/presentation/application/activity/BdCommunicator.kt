package com.xabber.presentation.application.activity

import com.xabber.model.xmpp.account.AccountStorageItem
import com.xabber.model.xmpp.presences.ResourceStorageItem
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration

class BdCommunicator : BdRepository {

    override fun checkUserIsRegister(): Boolean {
        val config =
            RealmConfiguration.Builder(setOf(AccountStorageItem::class, ResourceStorageItem::class))
                .build()
        val realm = Realm.open(config)
        val accountCollection = realm
            .query(AccountStorageItem::class)
            .find()
        val comparisonResult = accountCollection.size > 0
        realm.close()
        return comparisonResult
    }
}
