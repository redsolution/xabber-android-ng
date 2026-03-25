package com.xabber.account

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.xmpp.avatar.AvatarStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageForwardsInlineStorageItem
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.data_base.models.roster.BlockStorageItem
import com.xabber.data_base.models.roster.RosterGroupStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.presentation.XabberApplication.Companion.applicationContext as appContext
import com.xabber.presentation.application.fragments.chat.viewmodel.ChatViewModel
import com.xabber.presentation.onboarding.util.PasswordStorageHelper
import com.xabber.stream.ProcessedMessageId
import com.xabber.xmpp.device.DeviceStorageItem
import com.xabber.xmpp.global_index.GroupChatIndexStorageItem
import com.xabber.xmpp.groupchat.GroupChatStorageItem
import com.xabber.xmpp.groupchat.GroupchatInvitesStorageItem
import com.xabber.xmpp.messages.message.MessageStanzaStorageItem
import com.xabber.xmpp.messages.message.TemporaryMessageStanzaStorageItem
import com.xabber.xmpp.messages.messages_manager.MessageCommonSender
import com.xabber.xmpp.notifications.NotificationStorageItem
import com.xabber.xmpp.notifications.XMPPNotificationsManagerStorageItem
import com.xabber.xmpp.roster.RosterDisplayNameStorageItem
import com.xabber.xmpp.voip.voIPManager.CallMetadataStorageItem
import com.xabber.xmpp.x509.X509StorageItem
import com.xabber.common.SettingManager
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.reflect.KClass
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@RequiresApi(Build.VERSION_CODES.O)
object AccountManager {

    private val realm = Realm.Companion.open(defaultRealmConfig())
    var users: MutableList<Account> = mutableListOf()

    /**
     * Emits `true` when any account starts reconnecting, `false` when it reconnects successfully.
     * Observed by [ApplicationActivity] via lifecycleScope to drive the reconnect snackbar.
     */
    private val _reconnectingState = MutableStateFlow(false)
    val reconnectingState: StateFlow<Boolean> = _reconnectingState.asStateFlow()

    fun setReconnecting(isReconnecting: Boolean) {
        _reconnectingState.value = isReconnecting
    }

    /**
     * JIDs of accounts currently in the middle of connecting (startup or reconnect).
     * While a JID is in this set, [Account.action] and [Account.unsafeAction] skip
     * any socket/Realm work to avoid crashes on a not-yet-ready stream.
     */
    val connectingAccounts: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private var isLoggingOut: Boolean = false
    private var passwordStorageHelper: PasswordStorageHelper? = null
    private val chatViewModels = mutableMapOf<String, ChatViewModel>()
//    private val streams = mutableMapOf<String, Stream>()
    private val messageSenders: MutableMap<String, MessageCommonSender> = mutableMapOf()
    // Initialize PasswordStorageHelper with application context
    fun initialize(context: Context) {
        if (passwordStorageHelper == null) {
            passwordStorageHelper = PasswordStorageHelper(context)
            Log.d("AccountManager", "PasswordStorageHelper initialized")
            startNetworkMonitoring(context.applicationContext)
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** Tracks the currently active network so we can detect wifi↔mobile switches. */
    @Volatile
    private var currentNetworkId: Long = -1L

    /**
     * The last network ID we were successfully *connected* on.
     * Unlike currentNetworkId, this is NOT reset on onLost — it persists so that
     * when a network is restored (same handle), we can recognize it and avoid a
     * force-reconnect. Reset only when we connect on a new network.
     */
    /** Managed scope for all AccountManager async work. Survives for the app lifetime. */
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var lastConnectedNetworkId: Long = -1L

    /**
     * Debounce timestamp to suppress rapid consecutive network events
     * (common on Samsung, Xiaomi MIUI, Huawei EMUI, MediaTek dual-SIM).
     */
    @Volatile
    private var lastNetworkEventMs: Long = 0L
    private val NETWORK_EVENT_DEBOUNCE_MS = 1_000L

    private fun startNetworkMonitoring(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Seed with current active network (if any)
        connectivityManager.activeNetwork?.let {
            currentNetworkId = it.networkHandle
            lastConnectedNetworkId = it.networkHandle
        }

        // We add VALIDATED so that onAvailable only fires once the network is
        // actually routable (past captive-portal check).  This prevents premature
        // reconnect attempts on Samsung/Huawei/MIUI devices that report the network
        // before DNS is usable.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                val newId = network.networkHandle

                // Guard against invalid handles (seen on some MTK / dual-SIM devices)
                if (newId <= 0) {
                    Log.w("AccountManager", "onAvailable: invalid network handle $newId, ignoring")
                    return
                }

                // Debounce: some vendors (MIUI, Huawei) fire rapid onLost+onAvailable pairs
                // for the same physical interface (WiFi reassociation, DHCP renewal).
                val now = System.currentTimeMillis()
                if (now - lastNetworkEventMs < NETWORK_EVENT_DEBOUNCE_MS) {
                    Log.d("AccountManager", "onAvailable: debouncing rapid network event for handle=$newId")
                    // Still update tracking
                    currentNetworkId = newId
                    return
                }
                lastNetworkEventMs = now

                val prevConnected = lastConnectedNetworkId
                currentNetworkId = newId
                lastConnectedNetworkId = newId

                // "Same network" = the network handle we last actually connected on came back.
                // We do NOT use currentNetworkId for this comparison because onLost resets it to -1,
                // making every subsequent onAvailable look like a new network (the original bug).
                val isSameNetwork = (prevConnected == newId && prevConnected > 0)

                if (isSameNetwork) {
                    // Same network restored (brief dropout, DHCP renewal) — only reconnect accounts that dropped
                    Log.d("AccountManager", "Network restored (same=$newId) – checking offline accounts")
                    managerScope.launch {
                        users.forEach { account ->
                            if (!account.isConnected()) {
                                Log.d("AccountManager", "Reconnecting offline ${account.jid}")
                                account.performReconnect()
                            }
                        }
                    }
                } else {
                    // Different network (wifi→mobile or vice versa) — force reconnect ALL accounts
                    Log.d("AccountManager", "Network changed ($prevConnected → $newId) – force reconnecting all accounts")
                    managerScope.launch {
                        users.forEach { account ->
                            Log.d("AccountManager", "Force reconnecting ${account.jid} due to network change")
                            account.performReconnect(force = true, networkChanged = true)
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                val lostId = network.networkHandle
                Log.d("AccountManager", "Network lost ($lostId)")
                // Only clear currentNetworkId — do NOT touch lastConnectedNetworkId.
                // lastConnectedNetworkId persists so the next onAvailable for the same
                // handle is correctly identified as a "same network" restore.
                if (currentNetworkId == lostId) {
                    currentNetworkId = -1L
                }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // Some devices (Oppo ColorOS, some Huawei) fire onAvailable before VALIDATED
                // is granted even when we requested it.  onCapabilitiesChanged is more reliable
                // for detecting the moment the network becomes actually usable.
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return

                val networkId = network.networkHandle
                // When we are offline (currentNetworkId == -1) onAvailable may not have fired
                // (observed on Nokia T20 / MediaTek after svc wifi disable/enable).  Accept any
                // validated network as a reconnect signal in that case.
                val wasOffline = currentNetworkId == -1L
                if (!wasOffline && networkId != currentNetworkId) return

                val now = System.currentTimeMillis()
                if (now - lastNetworkEventMs < NETWORK_EVENT_DEBOUNCE_MS) return
                lastNetworkEventMs = now

                if (wasOffline) {
                    // Treat as if onAvailable fired: update tracking and reconnect.
                    val prevConnected = lastConnectedNetworkId
                    currentNetworkId = networkId
                    lastConnectedNetworkId = networkId
                    val isSameNetwork = prevConnected == networkId && prevConnected > 0
                    Log.d("AccountManager", "Network validated while offline (handle=$networkId, same=$isSameNetwork) – reconnecting offline accounts")
                    managerScope.launch {
                        users.forEach { account ->
                            if (!account.isConnected()) {
                                Log.d("AccountManager", "Network validated (offline recovery), reconnecting ${account.jid} force=${!isSameNetwork}")
                                account.performReconnect(force = !isSameNetwork)
                            }
                        }
                    }
                } else {
                    Log.d("AccountManager", "Network validated (handle=$networkId) – checking offline accounts")
                    managerScope.launch {
                        users.filter { !it.isConnected() }.forEach { account ->
                            Log.d("AccountManager", "Network validated, reconnecting offline ${account.jid}")
                            account.performReconnect()
                        }
                    }
                }
            }
        }
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }

//    fun registerStream(owner: String, stream: Stream) {
//        streams[owner] = stream
//        Log.d("AccountManager", "Registered stream for owner=$owner")
//    }
//
//    fun getStream(owner: String): Stream? {
//        return streams[owner].also {
//            if (it == null) Log.w("AccountManager", "No stream found for owner=$owner")
//        }
//    }

    fun registerMessageSender(owner: String, sender: MessageCommonSender) {
        messageSenders[owner] = sender
    }

    fun isChatOpen(chatId: String): Boolean {
        return chatViewModels.containsKey(chatId)
    }

    fun unregisterMessageSender(owner: String) {
        messageSenders.remove(owner)
    }

    fun createMessageSender(owner: String): MessageCommonSender {
        val sender = messageSenders[owner] ?: MessageCommonSender(owner)
        registerMessageSender(owner, sender)
        return sender
    }

    fun registerChatViewModel(chatId: String, chatViewModel: ChatViewModel) {
        chatViewModels[chatId] = chatViewModel
        Log.d("AccountManager", "Registered ChatViewModel for chatId=$chatId")
        NotificationManagerCompat.from(appContext()).cancel(chatId.hashCode())    }

    fun getChatViewModel(chatId: String): ChatViewModel? {
        var viewModel = chatViewModels[chatId]
        if (viewModel == null) {
            val parts = chatId.split("_")
            if (parts.size == 3) {
                val opponent = parts[0]
                val owner = parts[1]
                val conversationType = ConversationType.Companion.fromRaw(parts[2])
                viewModel = ChatViewModel(chatId, owner, opponent, conversationType)
                chatViewModels[chatId] = viewModel
                Log.d("AccountManager", "Initialized and registered ChatViewModel for chatId=$chatId")
            } else {
                Log.e("AccountManager", "Invalid chatId format: $chatId")
            }
        } else {
            Log.d("AccountManager", "Returning existing ChatViewModel for chatId=$chatId")
        }
        return viewModel
    }


    fun createChatViewModel(owner: String, opponent: String, conversationType: ConversationType) {
        val chatId = LastChatsStorageItem.Companion.genPrimary(opponent, owner, conversationType)
        if (!chatViewModels.containsKey(chatId)) {
            val viewModel = ChatViewModel(chatId, owner, opponent, conversationType)
            chatViewModels[chatId] = viewModel
            Log.d("AccountManager", "Created ChatViewModel for chatId=$chatId, opponent=$opponent, conversationType=${conversationType.rawValue}")
            // Ensure LastChatsStorageItem exists (async to avoid blocking main thread)
            managerScope.launch {
                realm.write {
                    val existingChat = query<LastChatsStorageItem>("primary = $0", chatId).first().find()
                    if (existingChat == null) {
                        copyToRealm(LastChatsStorageItem().apply {
                            primary = chatId
                            this.owner = owner
                            jid = opponent
                            this.conversationType_ = conversationType.rawValue
                        })
                        Log.d("AccountManager", "Created LastChatsStorageItem for chatId=$chatId")
                    }
                }
            }
        } else {
            Log.d("AccountManager", "ChatViewModel already exists for chatId=$chatId")
        }
    }

    fun unregisterChatViewModel(chatId: String) {
        chatViewModels.remove(chatId)
        Log.d("AccountManager", "Unregistered ChatViewModel for chatId=$chatId")

    }


    suspend fun login(jid: String, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Log.d(
                    "AccountManager",
                    "Attempting to create account (login) for jid $jid, current users: ${users.map { it.jid }}"
                )
                if (jid.isEmpty() || username.isEmpty() || password.isEmpty()) {
                    Log.e("AccountManager", "Invalid credentials: jid, username, or password empty")
                    throw IllegalArgumentException("Invalid credentials")
                }
                val normalizedJid = jid.trim().lowercase()
                val existingAccount =
                    realm.query(AccountStorageItem::class, "jid = $0", normalizedJid).first().find()
                if (existingAccount != null) {
                    Log.w(
                        "AccountManager",
                        "Account with jid $normalizedJid already exists in Realm"
                    )
                    throw IllegalArgumentException("Account already exists")
                }

                passwordStorageHelper?.setData(normalizedJid, password.toByteArray())
                    ?: Log.w(
                        "AccountManager",
                        "PasswordStorageHelper not initialized, skipping password storage"
                    )
                Log.d("AccountManager", "Stored password for jid $normalizedJid")

                realm.write {
                    val newAccount = copyToRealm(AccountStorageItem().apply {
                        this.order = query<AccountStorageItem>().find().size
                        this.jid = normalizedJid
                        this.username = username
                        primary = normalizedJid
                        enabled = true
                    })
                    Log.d("AccountManager", "Created AccountStorageItem for jid $normalizedJid , account $newAccount")
                }
                val newUserAccount = Account().apply {
                    this.jid = normalizedJid
                    this.username = username
                    loadAccount()
                }
                val streamConnected = newUserAccount.connectStream()
                if (!streamConnected) {
                    Log.e("AccountManager", "Failed to connect Stream for jid $normalizedJid")
                    realm.write {
                        val account =
                            query(AccountStorageItem::class, "jid = $0", normalizedJid).first()
                                .find()
                        account?.let { delete(it) }
                        passwordStorageHelper?.remove(normalizedJid)
                    }
                    throw IllegalStateException("Stream connection failed")
                }
                synchronized(users) {
                    users.add(newUserAccount)
                    Log.d(
                        "AccountManager",
                        "Added account with jid $normalizedJid to users list, new users: ${users.map { it.jid }}"
                    )
                }

                Log.d(
                    "AccountManager",
                    "Account creation (login) successful for jid $normalizedJid"
                )
                true
            } catch (e: Exception) {
                Log.e(
                    "AccountManager",
                    "Account creation (login) failed for jid $jid: ${e.message}",
                    e
                )
                passwordStorageHelper?.remove(jid)
                throw e
            }
        }
    fun createAccount(jid: String, username: String, order: Int = 0): Boolean {
        return try {
            realm.writeBlocking {
                val existingAccount = this.query(AccountStorageItem::class, "jid = '$jid'").first().find()
                if (existingAccount != null) {
                    Log.w("AccountManager", "Account with jid $jid already exists in Realm")
                    return@writeBlocking false
                }
                val existingResources = this.query(ResourceStorageItem::class, "jid = $0", jid).find()
                if (existingResources.isNotEmpty()) {
                    Log.w("AccountManager", "Found ${existingResources.size} stale ResourceStorageItem for jid $jid, deleting")
                    delete(existingResources)
                }
                val newAccount = AccountStorageItem().apply {
                    this.jid = jid
                    this.username = username
                    this.order = order
                    this.primary = jid
                }
                this.copyToRealm(newAccount)
                val newUserAccount = Account().apply {
                    this.jid = jid
                    this.username = username
                }
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
                val account = this.query(AccountStorageItem::class, "jid = $0", jid).first().find()
                if (account == null) {
                    Log.w("AccountManager", "Account with jid $jid not found")
                    return@writeBlocking false
                }
                delete(account)
                fun <T : RealmObject> deleteStorageItems(
                    clazz: KClass<T>,
                    queryField: String,
                    queryValue: String
                ) {
                    try {
                        val items = this.query(clazz, "$queryField = $0", queryValue).find()
                        Log.d("AccountManager", "Deleting ${items.size} items of ${clazz.simpleName} for $queryField = $queryValue")
                        delete(items)
                    } catch (e: Exception) {
                        Log.e("AccountManager", "Failed to delete ${clazz.simpleName} for $queryField = $queryValue: ${e.message}")
                    }
                }

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
                deleteStorageItems(DeviceStorageItem::class, "owner", jid)
                deleteStorageItems(BlockStorageItem::class, "owner", jid)
                deleteStorageItems(MessageReferenceStorageItem::class, "owner", jid)
                deleteStorageItems(RosterGroupStorageItem::class, "owner", jid)
                deleteStorageItems(MessageStorageItem::class, "owner", jid)
                deleteStorageItems(RosterStorageItem::class, "owner", jid)
                deleteStorageItems(LastChatsStorageItem::class, "owner", jid)
                deleteStorageItems(MessageForwardsInlineStorageItem::class, "owner", jid)
                // GroupChatIndexStorageItem has no owner field — it is a global index keyed by
                // groupchat JID, so delete all entries when any account is removed.
                try {
                    val indexItems = this.query(GroupChatIndexStorageItem::class).find()
                    Log.d("AccountManager", "Deleting ${indexItems.size} items of GroupChatIndexStorageItem (global)")
                    delete(indexItems)
                } catch (e: Exception) {
                    Log.e("AccountManager", "Failed to delete GroupChatIndexStorageItem: ${e.message}")
                }
                deleteStorageItems(RosterDisplayNameStorageItem::class, "owner", jid)
                deleteStorageItems(ProcessedMessageId::class, "owner", jid)
                passwordStorageHelper?.remove(jid)
                    ?: Log.w("AccountManager", "PasswordStorageHelper not initialized, skipping password removal for jid $jid")

                true
            }
        } catch (e: Exception) {
            Log.e("AccountManager", "Failed to delete account with jid $jid: ${e.message}", e)
            false
        }
    }

    suspend fun setColorKey(primary: String, colorKey: String) {
        realm.write {
            val item = this.query(AccountStorageItem::class, "primary = '$primary'").first().find()
            if (item != null) findLatest(item)?.colorKey = colorKey
        }
    }

    fun getAccount(primary: String): AccountStorageItem? =
        realm.query(AccountStorageItem::class, "primary = '$primary'").first().find()

    suspend fun setEnabled(primary: String, isChecked: Boolean) {
        realm.write {
            val account = this.query(AccountStorageItem::class, "primary = '$primary'").first().find()
            if (account != null) account.enabled = isChecked
        }
    }

    fun getMainAccountPrimary(): String? {
        var accountPrimary: String? = null
        realm.writeBlocking {
            val item = this.query(AccountStorageItem::class, "order = 0").first().find()
            accountPrimary = item?.jid
        }
        return accountPrimary
    }

    fun loadFirstAccount(): Account? {
        var account: Account? = null
        runBlocking(Dispatchers.IO) {
            account = loadFirstAccountAsync()
        }
        return account
    }

    suspend fun loadFirstAccountAsync(): Account? = withContext(Dispatchers.IO) {
        var account: Account? = null
        try {
            synchronized(users) {
                if (users.isNotEmpty()) {
                    account = users.first()
                    Log.d("AccountManager", "First account already loaded: ${account?.jid}")
                    return@withContext account
                }
            }

            val accountStorageItem = realm.query(AccountStorageItem::class).first().find()
            if (accountStorageItem == null) {
                Log.w("AccountManager", "No accounts found in loadFirstAccount")
                return@withContext null
            }

            val jid = accountStorageItem.jid
            val username = accountStorageItem.username
            Log.d(
                "AccountManager",
                "Attempting to load and connect first account with jid $jid"
            )

            val newUserAccount = Account().apply {
                this.jid = jid
                this.username = username
                loadAccount()
            }

            Log.d("AccountManager", "connectingAccounts ADD (startup) $jid")
            connectingAccounts.add(jid)
            val streamConnected = try {
                newUserAccount.connectStream()
            } finally {
                connectingAccounts.remove(jid)
                Log.d("AccountManager", "connectingAccounts REMOVE (startup) $jid, set=$connectingAccounts")
            }
            if (!streamConnected) {
                Log.w("AccountManager", "Initial connect failed for $jid, keeping account offline")
            }

            synchronized(users) {
                users.add(newUserAccount)
            }

            account = newUserAccount
            Log.d(
                "AccountManager",
                "Loaded account $jid, connected=$streamConnected"
            )
        } catch (e: Exception) {
            Log.e("AccountManager", "Failed to load and connect first account: ${e.message}", e)
            account?.let {
                synchronized(users) {
                    users.removeIf { user -> user.jid == it.jid }
                    passwordStorageHelper?.remove(it.jid)
                    Log.d(
                        "AccountManager",
                        "Removed account with jid ${it.jid} from users list and password storage due to failure"
                    )
                }
            }
        }
        return@withContext account
    }

    fun logout(jid: String): Boolean {
        return try {
            val account = users.firstOrNull { it.jid == jid }
            account?.let {
                runBlocking(Dispatchers.IO) {
                    it.cleanup()
                }
            }

            // Delete all data from Realm and password storage
            val deleted = deleteAccount(jid)
            if (deleted) {
                // Clear cached settings (sync version, roster version, etc.)
                SettingManager.clear(jid)

                // Remove from users list (already inside deleteAccount, but double-check)
                synchronized(users) {
                    users.removeAll { it.jid == jid }
                }

                // Clear any in-memory ViewModels for this account
                chatViewModels.entries.removeAll { it.key.contains(jid) }

                Log.d("AccountManager", "Successfully logged out account with jid $jid")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("AccountManager", "Failed to logout account with jid $jid: ${e.message}", e)
            false
        }
    }
}
