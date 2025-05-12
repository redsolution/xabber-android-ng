package com.xabber.common

import android.content.Context
import android.content.Intent
import android.util.Log
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.avatar.AvatarStorageItem
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.data_base.models.*
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.BlockStorageItem
import com.xabber.data_base.models.roster.RosterGroupStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.presentation.onboarding.activity.OnBoardingActivity
import com.xabber.presentation.onboarding.fragments.signin.SigninViewModel
import com.xabber.xmpp.device.DeviceStorageItem
import com.xabber.xmpp.groupchat.GroupChatStorageItem
import com.xabber.xmpp.groupchat.GroupchatInvitesStorageItem
import com.xabber.xmpp.messages.message.MessageForwardsInlineStorageItem
import com.xabber.xmpp.messages.message.MessageStanzaStorageItem
import com.xabber.xmpp.messages.message.TemporaryMessageStanzaStorageItem
import com.xabber.xmpp.notifications.NotificationStorageItem
import com.xabber.xmpp.notifications.XMPPNotificationsManagerStorageItem
import com.xabber.xmpp.voip.voIPManager.CallMetadataStorageItem
import com.xabber.xmpp.x509.X509StorageItem
import io.realm.kotlin.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

object AccountManager {
    private val realm = Realm.open(defaultRealmConfig())
    var users: MutableList<Account> = mutableListOf()
    private var isLoggingOut: Boolean = false // Prevent multiple simultaneous logouts

    suspend fun login(jid: String, username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("AccountManager", "Attempting to create account (login) for jid $jid, current users: ${users.map { it.jid }}")
            if (jid.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Log.e("AccountManager", "Invalid credentials: jid, username, or password empty")
                throw IllegalArgumentException("Invalid credentials")
            }
            // Normalize jid to avoid mismatches
            val normalizedJid = jid.trim().lowercase()
            // Check if account already exists
            val existingAccount = realm.query(AccountStorageItem::class, "jid = $0", normalizedJid).first().find()
            if (existingAccount != null) {
                Log.w("AccountManager", "Account with jid $normalizedJid already exists in Realm")
                throw IllegalArgumentException("Account already exists")
            }
            // Placeholder XMPP authentication (replace with actual XMPP library, e.g., Smack)
            Log.d("AccountManager", "XMPP authentication successful for jid $normalizedJid (placeholder)")
            // Create new account
            realm.write {
                // Check for stale ResourceStorageItem
//                val existingResources = query(ResourceStorageItem::class, "jid = $0", normalizedJid).find()
//                if (existingResources.isNotEmpty()) {
//                    Log.w("AccountManager", "Found ${existingResources.size} stale ResourceStorageItem for jid $normalizedJid, deleting")
//                    delete(existingResources)
//                }
                // Create new account
                val newAccount = copyToRealm(AccountStorageItem().apply {
                    this.jid = normalizedJid
                    this.username = username
                    this.order = 0
                    this.primary = normalizedJid
                    this.enabled = true
                })
                Log.d("AccountManager", "Created AccountStorageItem for jid $normalizedJid")
            }
            // Create Account object for users list
            val newUserAccount = Account().apply {
                this.jid = normalizedJid
                this.username = username
                loadAccount()
            }
            // Add to users list
            synchronized(users) {
                users.add(newUserAccount)
                Log.d("AccountManager", "Added account with jid $normalizedJid to users list, new users: ${users.map { it.jid }}")
            }
            Log.d("AccountManager", "Account creation (login) successful for jid $normalizedJid")
            true
        } catch (e: Exception) {
            Log.e("AccountManager", "Account creation (login) failed for jid $jid: ${e.message}", e)
            throw e
        }
    }

    fun createAccount(
        jid: String,
        username: String,
        order: Int = 0,
    ): Boolean {
        return try {
            realm.writeBlocking {
                // Check for stale accounts
                val existingAccount = this.query(AccountStorageItem::class, "jid = '$jid'").first().find()
                if (existingAccount != null) {
                    Log.w("AccountManager", "Account with jid $jid already exists in Realm")
                    return@writeBlocking false // Account already exists
                }
                // Check for stale ResourceStorageItem
                val existingResources = this.query(ResourceStorageItem::class, "jid = $0", jid).find()
                if (existingResources.isNotEmpty()) {
                    Log.w("AccountManager", "Found ${existingResources.size} stale ResourceStorageItem for jid $jid, deleting")
                    delete(existingResources)
                }
                // Create new account
                val newAccount = AccountStorageItem().apply {
                    this.jid = jid
                    this.username = username
                    this.order = order
                    this.primary = jid
                }
                // Write to Realm
                this.copyToRealm(newAccount)
                // Create Account object for users list
                val newUserAccount = Account().apply {
                    this.jid = jid
                    this.username = username
                }
                // Add to users list
                synchronized(users) {
                    users.add(newUserAccount)
                }
                Log.d("AccountManager", "Successfully created account with jid $jid, username $username")
                true
            }
        } catch (e: Exception) {
            Log.e("AccountManager", "Failed to create account with jid $jid: ${e.message}", e)
            false
        }
    }

    fun find(jid: String): Account? {
        return users.firstOrNull { it.jid == jid }
    }

    fun deleteAccount(jid: String): Boolean {
        return try {
            realm.writeBlocking {
                // Delete AccountStorageItem
                val account = this.query(AccountStorageItem::class, "jid = $0", jid).first().find()
                if (account == null) {
                    Log.w("AccountManager", "Account with jid $jid not found")
                    return@writeBlocking false // Account not found
                }
                delete(account)

                // Helper function to delete storage items safely
                fun <T : io.realm.kotlin.types.RealmObject> deleteStorageItems(
                    clazz: KClass<T>,
                    queryField: String,
                    queryValue: String
                ) {
                    try {
                        val items = this.query(clazz, "$queryField = $0", queryValue).find()
                        delete(items)
                        Log.d("AccountManager", "Deleted ${items.size} ${clazz.simpleName} for $queryField = $queryValue")
                    } catch (e: Exception) {
                        Log.e("AccountManager", "Failed to delete ${clazz.simpleName} for $queryField = $queryValue: ${e.message}")
                    }
                }

                // Delete storage items with appropriate fields
                deleteStorageItems(AvatarStorageItem::class, "primary", jid)
                deleteStorageItems(ResourceStorageItem::class, "jid", jid)
                deleteStorageItems(X509StorageItem::class, "owner", jid)
                deleteStorageItems(CallMetadataStorageItem::class, "owner", jid)
                deleteStorageItems(NotificationStorageItem::class, "owner", jid)
                deleteStorageItems(XMPPNotificationsManagerStorageItem::class, "owner", jid)
                deleteStorageItems(TemporaryMessageStanzaStorageItem::class, "owner", jid)
                deleteStorageItems(MessageStanzaStorageItem::class, "owner", jid)
                deleteStorageItems(GroupchatInvitesStorageItem::class, "owner", jid)
                deleteStorageItems(GroupChatStorageItem::class, "owner", jid)
                // Skip MessageForwardsInlineStorageItem due to schema issue
                deleteStorageItems(DeviceStorageItem::class, "owner", jid)
                deleteStorageItems(BlockStorageItem::class, "owner", jid)
                deleteStorageItems(MessageReferenceStorageItem::class, "owner", jid)
                deleteStorageItems(RosterGroupStorageItem::class, "owner", jid)
                deleteStorageItems(MessageStorageItem::class, "owner", jid)
                deleteStorageItems(RosterStorageItem::class, "owner", jid)
                deleteStorageItems(LastChatsStorageItem::class, "owner", jid)

                true
            }
        } catch (e: Exception) {
            Log.e("AccountManager", "Failed to delete account with jid $jid: ${e.message}", e)
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
                Log.d("AccountManager", "Loaded first account with jid ${accountStorageItem.jid}")
            } else {
                Log.w("AccountManager", "No accounts found in loadFirstAccount")
            }
        }
        return account
    }

    fun logout(jid: String): Boolean {
        return try {
            // Delete the specific account and its associated data
            val deleted = deleteAccount(jid)
            if (deleted) {
                // Remove the account from the users list using iterator
                val iterator = users.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().jid == jid) {
                        iterator.remove()
                    }
                }
                Log.d("AccountManager", "Successfully logged out account with jid $jid")
                true
            } else {
                Log.w("AccountManager", "Failed to delete account with jid $jid in logout")
                false
            }
        } catch (e: Exception) {
            Log.e("AccountManager", "Failed to logout account with jid $jid: ${e.message}", e)
            false
        }
    }
}