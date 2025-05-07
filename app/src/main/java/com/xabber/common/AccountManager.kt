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
            Log.d("AccountManager", "Attempting login for jid $jid, current users: ${users.map { it.jid }}")
            if (jid.isEmpty() || password.isEmpty()) {
                Log.e("AccountManager", "Invalid credentials: jid or password empty")
                throw IllegalArgumentException("Invalid credentials")
            }
            // Normalize jid to avoid mismatches
            val normalizedJid = jid.trim().lowercase()
            // Check if account exists
            val accountStorageItem = realm.query(AccountStorageItem::class, "jid = $0", normalizedJid).first().find()
            if (accountStorageItem == null) {
                Log.w("AccountManager", "No account found for jid $normalizedJid in Realm")
                throw IllegalArgumentException("Account not found")
            }
            // Validate password locally using SigninViewModel
            val signinViewModel = SigninViewModel()
            if (!signinViewModel.verifyPassword(password, normalizedJid)) {
                Log.e("AccountManager", "Password validation failed for jid $normalizedJid")
                throw IllegalArgumentException("Invalid password")
            }
            // Placeholder XMPP authentication (replace with actual XMPP library, e.g., Smack)
            Log.d("AccountManager", "XMPP authentication successful for jid $normalizedJid (placeholder)")
            // Load existing account into users list
            val account = Account().apply {
                this.jid = accountStorageItem.jid.trim().lowercase()
                this.username = accountStorageItem.username
                loadAccount()
            }
            synchronized(users) {
                if (!users.any { it.jid == account.jid }) {
                    users.add(account)
                    Log.d("AccountManager", "Added account with jid $normalizedJid to users list, new users: ${users.map { it.jid }}")
                } else {
                    Log.d("AccountManager", "Account with jid $normalizedJid already in users list")
                }
            }
            Log.d("AccountManager", "Login successful for jid $normalizedJid")
            true
        } catch (e: Exception) {
            Log.e("AccountManager", "Login failed for jid $jid: ${e.message}", e)
            false
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
                // Skip GroupChatIndexStorageItem as it lacks 'owner' field
                deleteStorageItems(DeviceStorageItem::class, "owner", jid)
                deleteStorageItems(BlockStorageItem::class, "owner", jid)
                deleteStorageItems(MessageForwardsInlineStorageItem::class, "owner", jid)
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
        if (isLoggingOut) {
            Log.w("AccountManager", "Logout already in progress for jid $jid, skipping")
            return false
        }
        isLoggingOut = true
        return try {
            Log.d("AccountManager", "Attempting logout for jid $jid, current users: ${users.map { it.jid }}")
            // Remove the account from the users list
            val iterator = users.iterator()
            var removed = false
            while (iterator.hasNext()) {
                val account = iterator.next()
                if (account.jid.equals(jid.trim().lowercase(), ignoreCase = true)) {
                    iterator.remove()
                    removed = true
                }
            }
            // Clear users list if no specific jid is found and list is not empty
            if (!removed && users.isNotEmpty()) {
                Log.w("AccountManager", "No account found in users list for jid $jid, clearing all users")
                users.clear()
                removed = true
            }
            // Placeholder: Disconnect XMPP session (replace with actual XMPP code)
            Log.d("AccountManager", "Disconnecting XMPP session for jid $jid (placeholder)")
            if (removed) {
                Log.d("AccountManager", "Successfully logged out account with jid $jid (session cleared), users now: ${users.map { it.jid }}")
                true
            } else {
                Log.d("AccountManager", "No action taken for logout of jid $jid (users list already empty)")
                true // Return true to allow navigation
            }
        } catch (e: Exception) {
            Log.e("AccountManager", "Failed to logout account with jid $jid: ${e.message}", e)
            false
        } finally {
            isLoggingOut = false
        }
    }
}