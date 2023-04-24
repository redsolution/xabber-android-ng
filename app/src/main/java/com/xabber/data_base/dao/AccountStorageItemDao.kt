package com.xabber.data_base.dao

import com.xabber.models.xmpp.account.AccountStorageItem
import io.realm.kotlin.Realm

class AccountStorageItemDao(private val realm: Realm) {

    fun getAccount(primary: String): AccountStorageItem? =
        realm.query(AccountStorageItem::class, "primary = '$primary'").first().find()

    fun getMainAccountPrimary(): String? {
        var accountPrimary: String? = null
        realm.writeBlocking {
            val item = this.query(AccountStorageItem::class, "order = 0").first().find()
            accountPrimary = item?.jid
        }
        return accountPrimary
    }

     fun createAccount(
        accountJid: String,
        accountName: String,
        accountColor: String?,
        accountHasAvatar: Boolean = false
    ) {
        val accountOrder = defineAccountOrder()
        realm.writeBlocking {
            this.copyToRealm(AccountStorageItem().apply {
                primary = accountJid
                jid = accountJid
                order = accountOrder
                colorKey = accountColor ?: "blue"
                enabled = true
                hasAvatar = accountHasAvatar
                username = accountName
            })
        }
    }

    private fun defineAccountOrder(): Int {
        var order = 0
        realm.writeBlocking {
            val accountList = this.query(AccountStorageItem::class).find()
            if (accountList.isNotEmpty()) order = accountList.size
        }
        return order
    }

    suspend fun setEnabled(primary: String, isChecked: Boolean) {
        realm.write {
            val account =
                this.query(AccountStorageItem::class, "primary = '$primary'").first().find()
            if (account != null) account.enabled = isChecked
        }
    }

    suspend fun setColorKey(primary: String, colorKey: String) {
        realm.write {
            val item = this.query(AccountStorageItem::class, "primary = '$primary'").first().find()
            if (item != null) findLatest(item)?.colorKey = colorKey
        }
    }

}
