//package com.xabber.common
//
//import android.os.Build
//import android.util.Log
//import androidx.annotation.RequiresApi
//import com.xabber.data_base.defaultRealmConfig
//import com.xabber.xmpp.roster.RosterManager
//import io.reactivex.subjects.BehaviorSubject
//import io.realm.kotlin.Realm
//import io.realm.kotlin.ext.query
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.runBlocking
//import kotlinx.coroutines.withContext
//import nl.adaptivity.xmlutil.serialization.XML
//import nl.adaptivity.xmlutil.serialization.XmlSerialName
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.decodeFromString
//import java.util.concurrent.ConcurrentHashMap
//
////// Placeholder for XML element parsing (based on Swift's DDXMLElement)
////@Serializable
////@XmlSerialName("error", "", "")
////data class XMPPError(
////    val xmlns: String? = null,
////    val name: String? = null,
////    @XmlSerialName("text", "", "")
////    val text: String? = null
////)
////
////// Placeholder for parsed IQ stanza
////@Serializable
////@XmlSerialName("iq", "jabber:client", "")
////data class XMPPIQ(
////    val type: String? = null,
////    val id: String? = null,
////    val from: String? = null,
////    @XmlSerialName("ping", "urn:xmpp:ping", "")
////    val ping: Ping? = null
////) {
////    @Serializable
////    data class Ping(val present: Boolean = true)
////}
////
////@RequiresApi(Build.VERSION_CODES.O)
////fun Account.restore() {
////    // Commented-out Swift logic for checking authentication/connection status not implemented
////    // as Stream.kt does not expose isAuthenticated/isConnected properties
////    runBlocking(Dispatchers.IO) {
////        closeStream() // Equivalent to disconnect(hard: true) and resetStream()
////        connectStream() // Equivalent to asyncConnect()
////    }
////}
////
////@RequiresApi(Build.VERSION_CODES.O)
////suspend fun Account.didAuthenticate() = withContext(Dispatchers.IO) {
////    try {
////        // Placeholder for registerRegularPushForAccount()
////        Log.d("Account", "Registering regular push for account $jid (placeholder)")
////
////        configureBase() // Assuming this is defined elsewhere in Account
////        XMPPUIActionManager.open(owner = jid, force = true)
////
////        // Placeholder for syncManager and sm.didResume
////        if (SyncManager.didResume) {
////            AccountManager.find(jid)?.markAsConnected(jid)
////            // presence() // Uncomment if presence() is implemented
////            withContext(Dispatchers.Main) {
////                ToastPresenter.presentSuccess("SM did resume")
////            }
////            SyncManager.sync(stream)
////        } else {
////            withContext(Dispatchers.Main) {
////                ToastPresenter.present("Synchronization", "cloud")
////            }
////            configureExtensions() // Assuming this is defined elsewhere in Account
////            discoConfigure() // Placeholder for disco.configure
////            if (rosterVersion != null && SyncManager.isAvailable) {
////                statusMessage.onNext("Synchronization")
////            }
////            RosterManager(owner = jid).request(stream ?: return@withContext)
////        }
////
////        // Simulate queue.asyncAfter with coroutine delay
////        delay(1000)
////        SyncManager.sync(stream)
////        Devices.requestList(stream)
////    } catch (e: Exception) {
////        Log.e("Account", "Error in didAuthenticate for $jid: ${e.message}", e)
////    }
////}
////
////suspend fun Account.didReceivePing(iq: String): Boolean = withContext(Dispatchers.IO) {
////    try {
////        val xml = XML {
////            indent = 2
////            autoPolymorphic = false
////            defaultPolicy { ignoreUnknownChildren(); pedantic = false }
////        }
////        val parsedIq = xml.decodeFromString<XMPPIQ>(iq)
////        if (parsedIq.xmlns == "jabber:client" && parsedIq.type == "get" && parsedIq.ping?.present == true) {
////            val response = """
////                <iq type='result' id='${parsedIq.id}' to='${parsedIq.from}'/>
////            """.trimIndent()
////            stream?.getSocket()?.write(response)?.also { success ->
////                if (success) {
////                    Log.d("Account", "Sent ping response for IQ id: ${parsedIq.id}")
////                } else {
////                    Log.e("Account", "Failed to send ping response for IQ id: ${parsedIq.id}")
////                }
////            } ?: run {
////                Log.e("Account", "Cannot send ping response: Socket is null")
////                return@withContext false
////            }
////            return@withContext true
////        }
////        return@withContext false
////    } catch (e: Exception) {
////        Log.e("Account", "Error parsing ping IQ: ${e.message}, IQ: $iq", e)
////        return@withContext false
////    }
////}
////
////@RequiresApi(Build.VERSION_CODES.O)
////suspend fun Account.didReceiveError(error: String) = withContext(Dispatchers.IO) {
////    val xml = XML {
////        indent = 2
////        autoPolymorphic = false
////        defaultPolicy { ignoreUnknownChildren(); pedantic = false }
////    }
////    val parsedError = try {
////        xml.decodeFromString<XMPPError>(error)
////    } catch (e: Exception) {
////        Log.e("Account", "Failed to parse error XML: ${e.message}, error: $error", e)
////        return@withContext
////    }
////
////    suspend fun failToConnect(errorName: String) {
////        reconnectAutoReconnect = false // Placeholder for reconnect.autoReconnect
////        closeStream() // Equivalent to disconnect(hard: true)
////        when (errorName) {
////            "conflict" -> {
////                if (Devices.isAvailable) {
////                    tokenWasInvalidated()
////                } else {
////                    updateResource("$resource${(0..16379).random()}")
////                }
////            }
////            "credentials-expired" -> tokenWasInvalidated()
////            "policy-violation" -> {
////                if (CommonConfigManager.shouldBlockApplicationWhenSubscriptionEnd) {
////                    val result = SubscribtionsManager.checkXMPPAccountState(jid)
////                    if (result) {
////                        statusMessage.onNext(parsedError.text ?: "Offline")
////                    } else {
////                        statusMessage.onNext("Subscription expired")
////                        withContext(Dispatchers.Main) {
////                            SubscribtionsPresenter.present(animated = true)
////                        }
////                    }
////                } else {
////                    statusMessage.onNext(parsedError.text ?: "Offline")
////                }
////            }
////            "not-authorized" -> {
////                if (parsedError.text == "Device was revoked") {
////                    tokenWasInvalidated()
////                    return
////                }
////                statusMessage.onNext("Incorrect username or password")
////                if (jid != AccountManager.newAccountJid) {
////                    tokenShouldUpdate()
////                }
////                AccountManager.changeNewUserState(jid, NewUserState.Failure(statusMessage.value))
////            }
////            else -> {
////                statusMessage.onNext("Offline")
////                AccountManager.changeNewUserState(jid, NewUserState.Failure(parsedError.text ?: "Unknown error"))
////            }
////        }
////    }
////
////    suspend fun tryToReconnect(errorName: String) {
////        reconnectAutoReconnect = true // Placeholder for reconnect.autoReconnect
////        delay(1000)
////        reconnectManualStart() // Placeholder for reconnect.manualStart
////    }
////
////    val errorNames = listOf("conflict", "credentials-expired", "policy-violation", "not-authorized")
////    parsedError.xmlns?.let { xmlns ->
////        when (xmlns) {
////            "urn:ietf:params:xml:ns:xmpp-streams", "urn:ietf:params:xml:ns:xmpp-sasl" -> {
////                parsedError.name?.let { errorName ->
////                    if (errorName in errorNames) {
////                        failToConnect(errorName)
////                    } else {
////                        tryToReconnect(errorName)
////                    }
////                }
////            }
////        }
////    }
////
////    CredentialsManager.getItem(jid).release(error = true)
////    resetConfigs() // Placeholder for resetConfigs
////}
////
////@RequiresApi(Build.VERSION_CODES.O)
////suspend fun Account.tokenShouldUpdate() = withContext(Dispatchers.IO) {
////    reconnectAutoReconnect = false // Placeholder for reconnect.autoReconnect
////    closeStream()
////    withContext(Dispatchers.Main) {
////        CredentialsExpiredPresenter(jid).present(animated = true)
////    }
////}
////
////fun Account.tokenWasInvalidated() {
////    // Placeholder for NotificationCenter.default.post
////    Log.d("Account", "Token invalidated for jid: $jid (posting notification)")
////    ApplicationStateManager.postTokenExpired(jid)
////}
////
//@RequiresApi(Build.VERSION_CODES.O)
//suspend fun Account.didReceiveRoster() = withContext(Dispatchers.IO) {
//    delay(1000)
//    if (!SyncManager.didResume) {
//        presence()
//    }
//    delay(1000)
//    updateExtensions() // Placeholder for updateExtensions
//
//    if (SyncManager.isAvailable) {
//        statusMessage.onNext("Synchronization")
//    }
//    if (!isSynced && !SyncManager.isAvailable) {
//        isSynced = true
//        delay(2000)
//        AccountManager.changeNewUserState(jid, NewUserState.DataLoaded)
//        if (AccountManager.users.size == 1) {
//            XMPPUIActionManager.performRequest(owner = jid, action = { stream, session ->
//                session.retract?.enable(stream)
//            }, fail = {
//                msgDeleteManager.enable(stream)
//            })
//        } else {
//            msgDeleteManager.enable(stream)
//        }
//        if (myPresence == null) {
//            requestInitialMAM()
//        }
//    }
//    notificationsUpdate() // Placeholder for notifications.update
//    favoritesUpdate() // Placeholder for favorites.update
//}
////
////// Placeholder properties and methods for Account
////var Account.reconnectAutoReconnect: Boolean
////    get() = false // Placeholder
////    set(value) {
////        Log.d("Account", "Setting reconnectAutoReconnect to $value for $jid (placeholder)")
////    }
////
////var Account.isSynced: Boolean
////    get() = false // Placeholder
////    set(value) {
////        Log.d("Account", "Setting isSynced to $value for $jid (placeholder)")
////    }
////
////var Account.myPresence: String?
////    get() = null // Placeholder
////    set(value) {
////        Log.d("Account", "Setting myPresence to $value for $jid (placeholder)")
////    }
////
////fun Account.configureBase() {
////    Log.d("Account", "Configuring base for $jid (placeholder)")
////}
////
////fun Account.configureExtensions() {
////    Log.d("Account", "Configuring extensions for $jid (placeholder)")
////}
////
////fun Account.discoConfigure() {
////    Log.d("Account", "Configuring disco for $jid (placeholder)")
////}
////
////fun Account.rosterVersion(): String? = null // Placeholder
////
////fun Account.updateResource(resource: String) {
////    Log.d("Account", "Updating resource to $resource for $jid (placeholder)")
////    this.resource = resource
////}
////
////fun Account.reconnectManualStart() {
////    Log.d("Account", "Manual reconnect start for $jid (placeholder)")
////}
////
////fun Account.resetConfigs() {
////    Log.d("Account", "Resetting configs for $jid (placeholder)")
////}
////
////fun Account.requestInitialMAM() {
////    Log.d("Account", "Requesting initial MAM for $jid (placeholder)")
////}
////
////fun Account.notificationsUpdate() {
////    Log.d("Account", "Updating notifications for $jid (placeholder)")
////}
////
////fun Account.favoritesUpdate() {
////    Log.d("Account", "Updating favorites for $jid (placeholder)")
////}
////
////// Placeholder for SyncManager
//object SyncManager {
//    val didResume: Boolean
//        get() = false // Placeholder
//    val isAvailable: Boolean
//        get() = false // Placeholder
//    fun sync(stream: Stream?) {
//        Log.d("SyncManager", "Syncing with stream (placeholder)")
//    }
//}
////
////// Placeholder for Devices
////object Devices {
////    val isAvailable: Boolean
////        get() = false // Placeholder
////    fun requestList(stream: Stream?) {
////        Log.d("Devices", "Requesting device list (placeholder)")
////    }
////}
////
////// Placeholder for XMPPUIActionManager
////object XMPPUIActionManager {
////    fun open(owner: String, force: Boolean) {
////        Log.d("XMPPUIActionManager", "Opening UI for $owner, force=$force (placeholder)")
////    }
////
////    fun performRequest(owner: String, action: (Stream?, Any?) -> Unit, fail: () -> Unit) {
////        Log.d("XMPPUIActionManager", "Performing request for $owner (placeholder)")
////        fail() // Default to fail for placeholder
////    }
////}
////
////// Placeholder for ToastPresenter
////object ToastPresenter {
////    fun presentSuccess(message: String) {
////        Log.d("ToastPresenter", "Presenting success toast: $message (placeholder)")
////    }
////
////    fun present(message: String, image: String) {
////        Log.d("ToastPresenter", "Presenting toast: $message, image=$image (placeholder)")
////    }
////}
////
////// Placeholder for SubscribtionsManager
////object SubscribtionsManager {
////    fun checkXMPPAccountState(jid: String): Boolean {
////        Log.d("SubscribtionsManager", "Checking XMPP account state for $jid (placeholder)")
////        return false // Placeholder
////    }
////}
////
////// Placeholder for SubscribtionsPresenter
////object SubscribtionsPresenter {
////    fun present(animated: Boolean) {
////        Log.d("SubscribtionsPresenter", "Presenting subscriptions UI, animated=$animated (placeholder)")
////    }
////}
////
////// Placeholder for CredentialsManager
////object CredentialsManager {
////    fun getItem(jid: String): CredentialsItem {
////        Log.d("CredentialsManager", "Getting credentials for $jid (placeholder)")
////        return CredentialsItem()
////    }
////}
////
////class CredentialsItem {
////    fun release(error: Boolean) {
////        Log.d("CredentialsItem", "Releasing credentials, error=$error (placeholder)")
////    }
////}
////
////// Placeholder for CredentialsExpiredPresenter
////class CredentialsExpiredPresenter(val jid: String) {
////    fun present(animated: Boolean) {
////        Log.d("CredentialsExpiredPresenter", "Presenting credentials expired UI for $jid, animated=$animated (placeholder)")
////    }
////}
////
////// Placeholder for ApplicationStateManager
////object ApplicationStateManager {
////    fun postTokenExpired(jid: String) {
////        Log.d("ApplicationStateManager", "Posting token expired notification for $jid (placeholder)")
////    }
////}
////
////// Placeholder for CommonConfigManager
////object CommonConfigManager {
////    val shouldBlockApplicationWhenSubscriptionEnd: Boolean
////        get() = false // Placeholder
////}
////
////
////// Placeholder for NewUserState
////sealed class NewUserState {
////    data class Failure(val message: String) : NewUserState()
////    object DataLoaded : NewUserState()
////}
////
////// Placeholder for msgDeleteManager
////object msgDeleteManager {
////    fun enable(stream: Stream?) {
////        Log.d("msgDeleteManager", "Enabling message delete manager (placeholder)")
////    }
////}
////
////// Placeholder for session.retract
////object session {
////    val retract: Retract?
////        get() = null // Placeholder
////}
////
////object Retract {
////    fun enable(stream: Stream?) {
////        Log.d("Retract", "Enabling retract (placeholder)")
////    }
////}