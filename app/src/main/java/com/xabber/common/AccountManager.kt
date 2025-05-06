package com.xabber.common

import android.content.Context
import android.content.Intent
import android.util.Log
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.avatar.AvatarStorageItem
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.presentation.onboarding.activity.OnBoardingActivity
import io.realm.kotlin.Realm


object AccountManager {
    private val realm = Realm.open(defaultRealmConfig())
    var users: MutableList<Account> = mutableListOf()
    //    @SuppressLint("SuspiciousIndentation")
//    fun getAvatar(): AvatarDto? {
//        var avatarDto: AvatarDto? = null
//            realm.writeBlocking {
//                val id = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "order = 0").first().find()?.jid
//                if (id != null) {
//                    val realmAvatar =
//                        this.query(com.xabber.data_base.models.avatar.AvatarStorageItem::class, "primary = '$id'").first().find()
//                    if (realmAvatar != null) avatarDto = AvatarDto(
//                        realmAvatar.primary,
//                        jid = realmAvatar.jid,
//                        owner = realmAvatar.owner,
//                        uploadUrl = realmAvatar.uploadUrl,
//                        fileUri = realmAvatar.fileUri,
//                        image96 = realmAvatar.image96,
//                        image128 = realmAvatar.image128,
//                        image192 = realmAvatar.image192,
//                        image384 = realmAvatar.image384,
//                        image512 = realmAvatar.image512
//                    )
//                }
//        }
//        return avatarDto
//    }
//
//    fun getHaveAvatar(): Boolean {
//        var hasAvatar = false
//        realm.writeBlocking {
//            val primaryAccount = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "order = 0").first().find()
//            hasAvatar = primaryAccount?.hasAvatar ?: false
//        }
//        return hasAvatar
//    }
//
//    fun getInitials(): String {
//        var initials = ""
//        realm.writeBlocking {
//            val primaryAccount = this.query(com.xabber.data_base.models.account.AccountStorageItem::class, "order = 0").first().find()
//          val name = primaryAccount?.username
//            initials =
//               name?.split(' ')?.mapNotNull { it.firstOrNull()?.toString() }?.reduce { acc, s -> acc + s }
//                    ?: ""
//          if (initials.length > 2)  initials = initials.substring(0, 2)
//        }
//        return initials
//    }




    fun createAccount(
        jid: String,
        username: String,
        order: Int = 0,
    ): Boolean {
        return try {
            realm.writeBlocking {
                // Check if account with same JID already exists
                val existingAccount = this.query(AccountStorageItem::class, "jid = '$jid'").first().find()
                if (existingAccount != null) {
                    return@writeBlocking false // Account already exists
                }
                // Create new account
                val newAccount = AccountStorageItem().apply {
                    this.jid = jid
                    this.username = username
                    this.order = order
                }
                // Write to Realm
                this.copyToRealm(newAccount)
                // Create Account object for users list
                val newUserAccount = Account().apply {
                    this.jid = jid
                    this.username = username
                }
                // Add to users list
                users.add(newUserAccount)
                true
            }
        } catch (e: Exception) {
            Log.e("AccountManager", "Failed to add account: ${e.message}")
            false
        }
    }

    fun find(jid: String): Account? {
        return users.firstOrNull { it.jid == jid }
    }

    fun deleteAccount(realm: Realm, jid: String): Boolean {
        return try {
            realm.writeBlocking {
                // Find the account with the given jid
                val account = this.query(AccountStorageItem::class, "jid = $0", jid).first().find()
                if (account != null) {
                    // Delete the account from Realm
                    delete(account)
                    true
                } else {
                    // Account not found
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("AccountManager", "Failed to delete account: ${e.message}")
            false
        }
    }

    suspend fun setColorKey(primary: String, colorKey: String) {
        realm.write {
            val item = this.query(
                AccountStorageItem::class,
                "primary = '$primary'"
            ).first().find()
            if (item != null) findLatest(item)?.colorKey = colorKey
        }
    }

    fun getAccount(primary: String): AccountStorageItem? =
        realm.query(
            AccountStorageItem::class,
            "primary = '$primary'"
        ).first().find()

    suspend fun setEnabled(primary: String, isChecked: Boolean) {
        realm.write {
            val account =
                this.query(
                    AccountStorageItem::class,
                    "primary = '$primary'"
                ).first().find()
            if (account != null) account.enabled = isChecked
        }
    }

    fun getMainAccountPrimary(): String? {
        var accountPrimary: String? = null
        realm.writeBlocking {
            val item =
                this.query(AccountStorageItem::class, "order = 0")
                    .first().find()
            accountPrimary = item?.jid
        }
        return accountPrimary
    }

    fun loadFirstAccount(): Account? {
        var account: Account? = null
        realm.writeBlocking {
            val accountStorageItem = this.query(AccountStorageItem::class).first().find()
            if (accountStorageItem != null) {
                account = Account().apply {
                    jid = accountStorageItem.jid
                    loadAccount()
                }
            }
        }
        return account
    }

    fun logout(): Boolean {
        return try {
            realm.writeBlocking {
                // Delete all AccountStorageItem entries
                val accounts = this.query(AccountStorageItem::class).find()
                delete(accounts)
                // Delete all AvatarStorageItem entries (if applicable)
                val avatars = this.query(AvatarStorageItem::class).find()
                delete(avatars)
                // Delete all ResourceStorageItem entries (if applicable)
                val resources = this.query(ResourceStorageItem::class).find()
                delete(resources)
            }
            users.clear()
            true
        } catch (e: Exception) {
            Log.e("AccountManager", "Failed to logout: ${e.message}")
            false
        }
    }
}
