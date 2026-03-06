package com.xabber.xmpp.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xabber.account.AccountManager
import com.xabber.common.SettingManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.presentation.XabberApplication
import com.xabber.utils.prp
import com.xabber.xmpp.ake.VerificationSessionStorageItem
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.messages_manager.MessageCommonSender
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Utility to convert JSONObject to Map
fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    val keys = this.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = this.get(key)
    }
    return map
}

@RequiresApi(Build.VERSION_CODES.O)
enum class NotifyType {
    NEW_MESSAGE,
    SUBSCRIPTION,
    NEW_RESOURCE_DETECT,
    CONTACT_GO_ONLINE,
    CONTACT_GO_OFFLINE,
    VERIFICATION
}

@RequiresApi(Build.VERSION_CODES.O)
data class NotifyItem(
    var owner: String? = null,
    var from: String,
    var to: String,
    var message: String,
    var timestamp: Long,
    var showed: Boolean = false,
    var id: String = "",
    var displayName: String = "",
    var archived: Boolean = false,
    var username: String? = null,
    var imageUrl: String? = null,
    var conversationType: String
) {
    constructor(from: String, to: String, message: String, date: Date, conversationType: String) : this(
        from = from,
        to = to,
        message = message,
        timestamp = date.time,
        conversationType = conversationType
    )
}

@RequiresApi(Build.VERSION_CODES.O)
class NotifyManagerStorage : RealmObject {
    companion object {
        fun primaryKey(): String = "id"
    }

    @PrimaryKey
    var id: Int = 0
    var unread: Int = 0
    var lastOpenDate: Long = Date(1000L).time
    var showMessageNotify: Boolean = true
    var showSubscriptionNotify: Boolean = true
    var showNewResourceNotify: Boolean = true
    var showContactOnlineNotify: Boolean = true
    var showContactOfflineNotify: Boolean = true
}

@RequiresApi(Build.VERSION_CODES.O)
class NotifyPersonalStorageItem : RealmObject {
    companion object {
        fun primaryKey(): String = "owner"
    }

    @PrimaryKey
    var owner: String = ""
    var muteAll: Boolean = false
    var muteList: RealmList<String> = realmListOf()
}

@RequiresApi(Build.VERSION_CODES.O)
data class MuteItem(
    val to: String,
    val from: String,
    var manually: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MuteItem) return false
        return from == other.from && to == other.to
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        return result
    }
}

@RequiresApi(Build.VERSION_CODES.O)
data class UnreadItem(
    var owner: String = "",
    var opponent: String = "",
    var count: Int = 1
) {
    fun up() {
        count += 1
    }

    fun primary(): String {
        return listOf(opponent, owner).prp()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnreadItem) return false
        return owner == other.owner && opponent == other.opponent
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + opponent.hashCode()
        return result
    }
}

@RequiresApi(Build.VERSION_CODES.O)
class NotifyManager private constructor() {
    companion object {
        val shared: NotifyManager by lazy { NotifyManager() }

        const val NOTIFICATION_MESSAGE_CATEGORY = "com.xabber.android.message"
        const val NOTIFICATION_PUSH_MESSAGE_CATEGORY = "com.xabber.android.message.push"
        const val NOTIFICATION_SUBSCRIPTION_CATEGORY = "com.xabber.android.subscription"
        const val NOTIFICATION_INVITE_CATEGORY = "com.xabber.android.invite"
        const val NOTIFICATION_VERIFICATION_CATEGORY = "com.xabber.android.verification"
        const val NOTIFICATION_MESSAGE_ACTION_REPLY = "$NOTIFICATION_MESSAGE_CATEGORY.reply"
        const val NOTIFICATION_MESSAGE_ACTION_MARK_AS_READ = "$NOTIFICATION_MESSAGE_CATEGORY.read"
        const val NOTIFICATION_MESSAGE_ACTION_SET_MUTE = "$NOTIFICATION_MESSAGE_CATEGORY.mute"
        const val NOTIFICATION_MESSAGE_ACTION_SUBSCRIBE = "$NOTIFICATION_SUBSCRIPTION_CATEGORY.accept"
        const val NOTIFICATION_MESSAGE_ACTION_UNSUBSCRIBE = "$NOTIFICATION_SUBSCRIPTION_CATEGORY.decline"
        const val NOTIFICATION_MESSAGE_ACTION_BLOCK = "$NOTIFICATION_SUBSCRIPTION_CATEGORY.block"
        const val NOTIFICATION_MESSAGE_ACTION_JOIN_GROUP = "$NOTIFICATION_INVITE_CATEGORY.join"
        const val NOTIFICATION_MESSAGE_ACTION_DECLINE_GROUP = "$NOTIFICATION_INVITE_CATEGORY.decline"

        val NOTIFICATION_CATEGORIES = listOf(
            NOTIFICATION_MESSAGE_CATEGORY,
            NOTIFICATION_PUSH_MESSAGE_CATEGORY
        )

        private const val TAG = "NotifyManager"
    }

    private val realm = Realm.open(defaultRealmConfig())
    private val writeScope = CoroutineScope(Dispatchers.IO)

    /** Fire-and-forget Realm write on IO thread — prevents blocking the main thread */
    private fun realmWriteAsync(block: io.realm.kotlin.MutableRealm.() -> Unit) {
        writeScope.launch {
            realm.write(block)
        }
    }
    private var currentDialog: String? = null
    private var lastOpen: Long = Date(1L).time
    private val message = NotifyItem("", "", "", Date(lastOpen), "")
    private val subscription = NotifyItem("", "", "", Date(lastOpen), "")
    private val newResource = NotifyItem("", "", "", Date(lastOpen), "")
    private val contactOnline = NotifyItem("", "", "", Date(lastOpen), "")
    private val contactOffline = NotifyItem("", "", "", Date(lastOpen), "")
    private val verification = NotifyItem("", "", "", Date(lastOpen), "")
    private var unreadMessagesCount: Int = 0
    private val muteList = mutableListOf<MuteItem>()
    private val regJidQueue = mutableListOf<String>()
    private var showMessageNotify: Boolean = true
    private var showSubscriptionNotify: Boolean = true
    private var showNewResourceNotify: Boolean = true
    private var showContactOnlineNotify: Boolean = true
    private var showContactOfflineNotify: Boolean = true
    private val unreadItems = ConcurrentHashMap<String, UnreadItem>()
    private var canShowNotify: Boolean = false
    private val activeAccountCountFlow = MutableStateFlow(0)
    private val deliveredNotificationsIds = ConcurrentHashMap.newKeySet<String>()
    private var lastChatsDisplayedState: Boolean = false
    private var openViewControllerPayload: Map<String, String>? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            subscribe()
        }
    }

    fun updateSubscription() {
        // No-op for now, as subscription is handled via coroutines
    }

    @OptIn(FlowPreview::class)
    private fun subscribe() {
        CoroutineScope(Dispatchers.IO).launch {
            realm.query<AccountStorageItem>("enabled == true")
                .asFlow()
                .map { accounts -> accounts.list.mapNotNull { it.jid } }
                .debounce(300)
                .collect { accounts ->
                    if (accounts.size != activeAccountCountFlow.value) {
                        activeAccountCountFlow.value = accounts.size
                        unreadItems.clear()
                        val predicate = if (SettingManager.getString("locked_conversation_type")?.isNotEmpty() == true) {
                            val excludedJids = accounts.mapNotNull { it.substringAfter("@") } +
                                    (SettingManager.getString("support_jid") ?: "")
                            "conversationType_ == '${SettingManager.getString("locked_conversation_type")}' OR jid IN ${excludedJids.joinToString(",", "{", "}")} AND muteExpired < 0 AND isArchived == false AND owner IN ${accounts.joinToString(",", "{", "}")}"
                        } else {
                            "muteExpired < 0 AND isArchived == false AND owner IN ${accounts.joinToString(",", "{", "}")}"
                        }

                        realm.query<LastChatsStorageItem>(predicate)
                            .asFlow()
                            .map { chats ->
                                chats.list.sumOf { chat ->
                                    chat.unread + if (chat.rosterItem?.isThereSubscriptionRequest() == true) 1 else 0
                                }
                            }
                            .debounce(300)
                            .collect { unreadCount ->
                                withContext(Dispatchers.Main) {
                                    if (canShowNotify) {
                                        showNotify(XabberApplication.applicationContext(), NotifyType.NEW_MESSAGE)
                                    }
                                    unreadMessagesCount = unreadCount
                                }
                            }

                        realm.query<RosterStorageItem>("owner IN ${accounts.joinToString(",", "{", "}")} AND ask_ IN {'in', 'both'} AND removed == false AND isHidden == false")
                            .asFlow()
                            .debounce(500)
                            .collect {
                                val unread = realm.query<LastChatsStorageItem>(predicate)
                                    .find()
                                    .sumOf { chat ->
                                        chat.unread + if (chat.rosterItem?.isThereSubscriptionRequest() == true) 1 else 0
                                    }
                                withContext(Dispatchers.Main) {
                                    unreadMessagesCount = unread
                                }
                            }
                    }
                }
        }
    }

    fun unsubscribe() {
        unreadItems.clear()
    }

    fun countUnread(results: List<MessageStorageItem>) {
        unreadItems.clear()
        results.forEach { item ->
            val key = "${item.opponent}_${item.owner}"
            unreadItems.compute(key) { _, existing ->
                existing?.apply { up() } ?: UnreadItem(item.owner, item.opponent)
            }
        }

        realmWriteAsync {
            unreadItems.values.forEach { item ->
                val chatItem = query<LastChatsStorageItem>("primary = '${item.primary()}'").first().find()
                chatItem?.unread = item.count
            }
        }
    }

    fun load(config: NotifyManagerStorage) {
        lastOpen = config.lastOpenDate
        showMessageNotify = config.showMessageNotify
        showSubscriptionNotify = config.showSubscriptionNotify
        showNewResourceNotify = config.showNewResourceNotify
        showContactOnlineNotify = config.showContactOnlineNotify
        showContactOfflineNotify = config.showContactOfflineNotify
    }

    @SuppressLint("MissingPermission")
    fun showInviteNotification(
        context: Context,
        title: String,
        subtitle: String,
        text: String,
        jid: String,
        owner: String
    ) {
        if (lastChatsDisplayedState) return

        // Check if chat is blocked or muted
        val chat = realm.query<LastChatsStorageItem>(
            "primary = '${LastChatsStorageItem.genPrimary(jid, owner, ConversationType.Group)}'"
        ).first().find()
        if (chat?.isBlocked == true || chat?.muteExpired?.let { it > System.currentTimeMillis() } == true || chat?.hasErrorInChat == true) return

        val notifyId = listOf(jid, owner, NOTIFICATION_INVITE_CATEGORY).prp()
        val notificationBuilder = NotificationCompat.Builder(context, createNotificationChannel(context))
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subtitle)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setCategory(NOTIFICATION_INVITE_CATEGORY)
            .setContentIntent(createPendingIntent(context, owner, jid, NOTIFICATION_INVITE_CATEGORY))
            .setExtras(android.os.Bundle().apply {
                putString("owner", owner)
                putString("jid", jid)
                putString("category", NOTIFICATION_INVITE_CATEGORY)
            })

        // Apply notification settings
        val soundEnabled = SettingManager.get("notification_groupchat_sound") as? Boolean ?: true
        val vibrationEnabled = SettingManager.get("notification_groupchat_vibration") as? Boolean ?: true
        if (soundEnabled) notificationBuilder.setSound(getNotificationSound(context))
        if (vibrationEnabled) notificationBuilder.setVibrate(longArrayOf(0, 500, 250, 500))
        if (chat?.isPinned == true) notificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH)

        NotificationManagerCompat.from(context).notify(notifyId.hashCode(), notificationBuilder.build())

        // Store ShowedNotificationRequests
        realmWriteAsync {
            copyToRealm(ShowedNotificationRequests().apply {
                primary = ShowedNotificationRequests.genPrimary(notifyId, owner)
                this.owner = owner
                this.jid = jid
                this.requestId = notifyId
                this.stanzaId = ""
                this.kind = ShowedNotificationRequests.Kind.SYSTEM
            }, UpdatePolicy.ALL)
        }

        // Store NotificationStorageItem
        realmWriteAsync {
            copyToRealm(NotificationStorageItem().apply {
                primary = NotificationStorageItem.genPrimary(owner, jid, notifyId)
                this.owner = owner
                this.jid = jid
                this.uniqueId = notifyId
                this.category = Category.CONTACT
                this.isRead = false
                this.displayedNick = subtitle
                this.text = text
                this.date = Date().time
                this.shouldShow = true
                this.metadata = mapOf("category" to NOTIFICATION_INVITE_CATEGORY)
            }, UpdatePolicy.ALL)
        }
    }

    fun update(
        context: Context,
        message: String,
        messageId: String,
        username: String?,
        opponent: String,
        owner: String,
        date: Date = Date(),
        displayName: String = "",
        imageUrl: String? = null,
        conversationType: ConversationType
    ) {
        if (isMuted(owner, opponent, conversationType)) return

        var primary = ShowedNotificationRequests.genPrimary(messageId, owner)
        if (realm.query<ShowedNotificationRequests>("primary = '$primary'").first().find() != null) return

        // Check if chat is blocked or muted
        val chat = realm.query<LastChatsStorageItem>(
            "primary = '${LastChatsStorageItem.genPrimary(opponent, owner, conversationType)}'"
        ).first().find()
        if (chat?.isBlocked == true || chat?.muteExpired?.let { it > System.currentTimeMillis() } == true || chat?.hasErrorInChat == true) return

        if (date.time > this.message.timestamp && messageId != this.message.id) {
            if (!this.message.showed) {
                showNotify(context, NotifyType.NEW_MESSAGE)
            }
            this.message.apply {
                this.to = opponent
                this.from = owner
                this.message = message
                this.timestamp = date.time
                this.id = messageId
                this.showed = false
                this.displayName = displayName
                this.username = username
                this.imageUrl = imageUrl
                this.conversationType = conversationType.rawValue
            }
            canShowNotify = true

            // Store ShowedNotificationRequests
            realmWriteAsync {
                copyToRealm(ShowedNotificationRequests().apply {
                    this.primary = primary
                    this.owner = owner
                    this.jid = opponent
                    this.requestId = messageId
                    this.stanzaId = messageId
                    this.kind = ShowedNotificationRequests.Kind.MESSAGE
                }, UpdatePolicy.ALL)
            }

            // Store NotificationStorageItem
            realmWriteAsync {
                copyToRealm(NotificationStorageItem().apply {
                    primary = NotificationStorageItem.genPrimary(owner, opponent, messageId)
                    this.owner = owner
                    this.jid = opponent
                    this.uniqueId = messageId
                    this.category = Category.CONTACT
                    this.isRead = false
                    this.displayedNick = displayName
                    this.text = message
                    this.date = date.time
                    this.shouldShow = true
                    this.metadata = mapOf(
                        "owner" to owner,
                        "jid" to opponent,
                        "stanzaId" to messageId,
                        "conversation_type" to conversationType.rawValue
                    )
                }, UpdatePolicy.ALL)
            }
        }
    }

    fun update(
        context: Context,
        type: NotifyType,
        content: String,
        opponent: String,
        owner: String,
        date: Date = Date()
    ) {
        if (isMuted(owner, opponent, ConversationType.Regular)) return

        // Check if chat is blocked or muted
        val chat = realm.query<LastChatsStorageItem>(
            "primary = '${LastChatsStorageItem.genPrimary(opponent, owner, ConversationType.Regular)}'"
        ).first().find()
        if (chat?.isBlocked == true || chat?.muteExpired?.let { it > System.currentTimeMillis() } == true || chat?.hasErrorInChat == true) return

        val notifyItem = when (type) {
            NotifyType.NEW_RESOURCE_DETECT -> {
                if (!showNewResourceNotify) return
                newResource
            }
            NotifyType.CONTACT_GO_ONLINE -> {
                if (!showContactOnlineNotify) return
                contactOnline
            }
            NotifyType.CONTACT_GO_OFFLINE -> {
                if (!showContactOfflineNotify) return
                contactOffline
            }
            else -> return
        }

        if (date.time > this.message.timestamp) {
            notifyItem.apply {
                this.to = opponent
                this.from = owner
                this.message = content
                this.timestamp = date.time
            }
            showNotify(context, type)
        }
    }

    fun update(
        context: Context,
        subscription: String,
        opponent: String,
        owner: String,
        displayName: String,
        date: Date = Date()
    ) {
        if (isMuted(owner, opponent, ConversationType.Regular) || !showSubscriptionNotify) return

        // Check if chat is blocked or muted
        val chat = realm.query<LastChatsStorageItem>(
            "primary = '${LastChatsStorageItem.genPrimary(opponent, owner, ConversationType.Regular)}'"
        ).first().find()
        if (chat?.isBlocked == true || chat?.muteExpired?.let { it > System.currentTimeMillis() } == true || chat?.hasErrorInChat == true) return

        if (date.time > this.message.timestamp) {
            this.subscription.apply {
                this.to = opponent
                this.from = owner
                this.message = subscription
                this.displayName = displayName
                this.timestamp = date.time
            }
            showNotify(context, NotifyType.SUBSCRIPTION)

            // Store ShowedNotificationRequests
            val notifyId = listOf(opponent, owner, NOTIFICATION_SUBSCRIPTION_CATEGORY).prp()
            realmWriteAsync {
                copyToRealm(ShowedNotificationRequests().apply {
                    primary = ShowedNotificationRequests.genPrimary(notifyId, owner)
                    this.owner = owner
                    this.jid = opponent
                    this.requestId = notifyId
                    this.stanzaId = ""
                    this.kind = ShowedNotificationRequests.Kind.SUBSCRIPTION
                }, UpdatePolicy.ALL)
            }

            // Store NotificationStorageItem
            realmWriteAsync {
                copyToRealm(NotificationStorageItem().apply {
                    primary = NotificationStorageItem.genPrimary(owner, opponent, notifyId)
                    this.owner = owner
                    this.jid = opponent
                    this.uniqueId = notifyId
                    this.category = Category.CONTACT
                    this.isRead = false
                    this.displayedNick = displayName
                    this.text = "Contact $opponent wants to add you to contact list"
                    this.date = date.time
                    this.shouldShow = true
                    this.metadata = mapOf(
                        "owner" to owner,
                        "jid" to opponent,
                        "category" to NOTIFICATION_SUBSCRIPTION_CATEGORY
                    )
                }, UpdatePolicy.ALL)
            }
        }
    }

    fun update(
        context: Context,
        verificationMessage: String,
        owner: String,
        displayName: String,
        sid: String,
        timestamp: Long
    ) {
        verification.apply {
            this.message = verificationMessage
            this.displayName = displayName
            this.timestamp = timestamp
            this.id = sid
            this.owner = owner
        }
        showNotify(context, NotifyType.VERIFICATION)

        // Store ShowedNotificationRequests
        realmWriteAsync {
            copyToRealm(ShowedNotificationRequests().apply {
                primary = ShowedNotificationRequests.genPrimary(sid, owner)
                this.owner = owner
                this.jid = ""
                this.requestId = sid
                this.stanzaId = sid
                this.kind = ShowedNotificationRequests.Kind.SYSTEM
            }, UpdatePolicy.ALL)
        }

        // Store NotificationStorageItem
        realmWriteAsync {
            copyToRealm(NotificationStorageItem().apply {
                primary = NotificationStorageItem.genPrimary(owner, "", sid)
                this.owner = owner
                this.jid = ""
                this.uniqueId = sid
                this.category = Category.DEVICE
                this.isRead = false
                this.displayedNick = displayName
                this.text = verificationMessage
                this.date = timestamp
                this.shouldShow = true
                this.metadata = mapOf(
                    "owner" to owner,
                    "sid" to sid
                )
            }, UpdatePolicy.ALL)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotify(context: Context, type: NotifyType) {
        if (!canShowNotify && type != NotifyType.VERIFICATION) return

        val notificationManager = NotificationManagerCompat.from(context)
        val notificationBuilder = NotificationCompat.Builder(context, createNotificationChannel(context))
        var notificationId = ""

        when (type) {
            NotifyType.NEW_MESSAGE -> {
                if (message.showed || !showMessageNotify) return
                if (currentDialog == listOf(message.to, message.from).prp()) return

                // Check if chat is blocked or muted
                val chat = realm.query<LastChatsStorageItem>(
                    "primary = '${LastChatsStorageItem.genPrimary(message.to, message.from, ConversationType.fromRaw(message.conversationType))}'"
                ).first().find()
                if (chat?.isBlocked == true || chat?.muteExpired?.let { it > System.currentTimeMillis() } == true || chat?.hasErrorInChat == true) return

                val isGroupChat = message.conversationType == "https://xabber.com/protocol/groups"
                val soundEnabled = if (isGroupChat) {
                    SettingManager.get("notification_groupchat_sound") as? Boolean ?: true
                } else {
                    SettingManager.get("notification_chat_sound") as? Boolean ?: true
                }
                val vibrationEnabled = if (isGroupChat) {
                    SettingManager.get("notification_groupchat_vibration") as? Boolean ?: true
                } else {
                    SettingManager.get("notification_chat_vibration") as? Boolean ?: true
                }
                val showPreviews = if (isGroupChat) {
                    SettingManager.get("notification_groupchat_showPreviews") as? Boolean ?: true
                } else {
                    SettingManager.get("notification_chat_showPreviews") as? Boolean ?: true
                }

                val unreadCount = chat?.unread ?: 1
                val titleName = message.displayName.ifEmpty { message.to }

                notificationBuilder
                    .setContentTitle(titleName)
                    .setContentText(if (showPreviews) message.message else "New message")
                    .setSubText(message.username)
                    .setNumber(unreadCount)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .apply {
                        if (soundEnabled) setSound(getNotificationSound(context))
                        if (vibrationEnabled) setVibrate(longArrayOf(0, 500, 250, 500))
                        if (chat?.isPinned == true) setPriority(NotificationCompat.PRIORITY_HIGH)
                    }
                    .setCategory(NOTIFICATION_MESSAGE_CATEGORY)
                    .setContentIntent(createPendingIntent(context, message.from, message.to, NOTIFICATION_MESSAGE_CATEGORY))
                    .setAutoCancel(true)
                    .setExtras(android.os.Bundle().apply {
                        putString("owner", message.from)
                        putString("jid", message.to)
                        putString("stanzaId", message.id)
                        putLong("timestamp", Date().time)
                        putString("conversation_type", message.conversationType)
                    })

                message.imageUrl?.let { url ->
                    try {
                        val bitmap = BitmapFactory.decodeStream(URL(url).openStream())
                        notificationBuilder.setLargeIcon(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load image for notification: ${e.message}")
                    }
                }

                notificationId = message.id
                message.showed = true
            }
            NotifyType.SUBSCRIPTION -> {
                if (subscription.showed || !showSubscriptionNotify) return

                // Check if chat is blocked or muted
                val chat = realm.query<LastChatsStorageItem>(
                    "primary = '${LastChatsStorageItem.genPrimary(subscription.to, subscription.from, ConversationType.Regular)}'"
                ).first().find()
                if (chat?.isBlocked == true || chat?.muteExpired?.let { it > System.currentTimeMillis() } == true || chat?.hasErrorInChat == true) return

                val soundEnabled = SettingManager.get("notification_chat_sound") as? Boolean ?: true
                val vibrationEnabled = SettingManager.get("notification_chat_vibration") as? Boolean ?: true
                val showPreviews = SettingManager.get("notification_chat_showPreviews") as? Boolean ?: true

                notificationBuilder
                    .setContentTitle(subscription.displayName)
                    .setContentText(if (showPreviews) "Contact ${subscription.to} wants to add you to contact list" else "New subscription request")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .apply {
                        if (soundEnabled) setSound(getNotificationSound(context))
                        if (vibrationEnabled) setVibrate(longArrayOf(0, 500, 250, 500))
                        if (chat?.isPinned == true) setPriority(NotificationCompat.PRIORITY_HIGH)
                    }
                    .setCategory(NOTIFICATION_SUBSCRIPTION_CATEGORY)
                    .setContentIntent(createPendingIntent(context, subscription.from, subscription.to, NOTIFICATION_SUBSCRIPTION_CATEGORY))
                    .setAutoCancel(true)
                    .setExtras(android.os.Bundle().apply {
                        putString("owner", subscription.from)
                        putString("jid", subscription.to)
                    })

                notificationId = listOf(subscription.to, subscription.from, NOTIFICATION_SUBSCRIPTION_CATEGORY).prp()
                subscription.showed = true
            }
            NotifyType.NEW_RESOURCE_DETECT -> {
                if (newResource.showed || !showNewResourceNotify) return

                // Check if chat is blocked or muted
                val chat = realm.query<LastChatsStorageItem>(
                    "primary = '${LastChatsStorageItem.genPrimary(newResource.to, newResource.from, ConversationType.Regular)}'"
                ).first().find()
                if (chat?.isBlocked == true || chat?.muteExpired?.let { it > System.currentTimeMillis() } == true || chat?.hasErrorInChat == true) return

                val soundEnabled = SettingManager.get("notification_chat_sound") as? Boolean ?: true
                val vibrationEnabled = SettingManager.get("notification_chat_vibration") as? Boolean ?: true
                val showPreviews = SettingManager.get("notification_chat_showPreviews") as? Boolean ?: true

                notificationBuilder
                    .setContentTitle("New Resource Detected")
                    .setContentText(if (showPreviews) newResource.message else "New resource detected")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .apply {
                        if (soundEnabled) setSound(getNotificationSound(context))
                        if (vibrationEnabled) setVibrate(longArrayOf(0, 500, 250, 500))
                        if (chat?.isPinned == true) setPriority(NotificationCompat.PRIORITY_HIGH)
                    }
                    .setCategory(NOTIFICATION_MESSAGE_CATEGORY)
                    .setContentIntent(createPendingIntent(context, newResource.from, newResource.to, NOTIFICATION_MESSAGE_CATEGORY))
                    .setAutoCancel(true)
                    .setExtras(android.os.Bundle().apply {
                        putString("owner", newResource.from)
                        putString("jid", newResource.to)
                    })

                notificationId = listOf(newResource.to, newResource.from, NOTIFICATION_MESSAGE_CATEGORY).prp()
                newResource.showed = true

                // Store NotificationStorageItem
                realmWriteAsync {
                    copyToRealm(NotificationStorageItem().apply {
                        primary = NotificationStorageItem.genPrimary(newResource.from, newResource.to, notificationId)
                        this.owner = newResource.from
                        this.jid = newResource.to
                        this.uniqueId = notificationId
                        this.category = Category.CONTACT
                        this.isRead = false
                        this.text = newResource.message
                        this.date = newResource.timestamp
                        this.shouldShow = true
                        this.metadata = mapOf(
                            "owner" to newResource.from,
                            "jid" to newResource.to,
                            "category" to NOTIFICATION_MESSAGE_CATEGORY
                        )
                    }, UpdatePolicy.ALL)
                }
            }
            NotifyType.CONTACT_GO_ONLINE -> {
                if (contactOnline.showed || !showContactOnlineNotify) return

                // Check if chat is blocked or muted
                val chat = realm.query<LastChatsStorageItem>(
                    "primary = '${LastChatsStorageItem.genPrimary(contactOnline.to, contactOnline.from, ConversationType.Regular)}'"
                ).first().find()
                if (chat?.isBlocked == true || chat?.muteExpired?.let { it > System.currentTimeMillis() } == true || chat?.hasErrorInChat == true) return

                val soundEnabled = SettingManager.get("notification_chat_sound") as? Boolean ?: true
                val vibrationEnabled = SettingManager.get("notification_chat_vibration") as? Boolean ?: true
                val showPreviews = SettingManager.get("notification_chat_showPreviews") as? Boolean ?: true

                notificationBuilder
                    .setContentTitle("Contact Online")
                    .setContentText(if (showPreviews) contactOnline.message else "Contact is online")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .apply {
                        if (soundEnabled) setSound(getNotificationSound(context))
                        if (vibrationEnabled) setVibrate(longArrayOf(0, 500, 250, 500))
                        if (chat?.isPinned == true) setPriority(NotificationCompat.PRIORITY_HIGH)
                    }
                    .setCategory(NOTIFICATION_MESSAGE_CATEGORY)
                    .setContentIntent(createPendingIntent(context, contactOnline.from, contactOnline.to, NOTIFICATION_MESSAGE_CATEGORY))
                    .setAutoCancel(true)
                    .setExtras(android.os.Bundle().apply {
                        putString("owner", contactOnline.from)
                        putString("jid", contactOnline.to)
                    })

                notificationId = listOf(contactOnline.to, contactOnline.from, NOTIFICATION_MESSAGE_CATEGORY).prp()
                contactOnline.showed = true

                // Store NotificationStorageItem
                realmWriteAsync {
                    copyToRealm(NotificationStorageItem().apply {
                        primary = NotificationStorageItem.genPrimary(contactOnline.from, contactOnline.to, notificationId)
                        this.owner = contactOnline.from
                        this.jid = contactOnline.to
                        this.uniqueId = notificationId
                        this.category = Category.CONTACT
                        this.isRead = false
                        this.text = contactOnline.message
                        this.date = contactOnline.timestamp
                        this.shouldShow = true
                        this.metadata = mapOf(
                            "owner" to contactOnline.from,
                            "jid" to contactOnline.to,
                            "category" to NOTIFICATION_MESSAGE_CATEGORY
                        )
                    }, UpdatePolicy.ALL)
                }
            }
            NotifyType.CONTACT_GO_OFFLINE -> {
                if (contactOffline.showed || !showContactOfflineNotify) return

                // Check if chat is blocked or muted
                val chat = realm.query<LastChatsStorageItem>(
                    "primary = '${LastChatsStorageItem.genPrimary(contactOffline.to, contactOffline.from, ConversationType.Regular)}'"
                ).first().find()
                if (chat?.isBlocked == true || chat?.muteExpired?.let { it > System.currentTimeMillis() } == true || chat?.hasErrorInChat == true) return

                val soundEnabled = SettingManager.get("notification_chat_sound") as? Boolean ?: true
                val vibrationEnabled = SettingManager.get("notification_chat_vibration") as? Boolean ?: true
                val showPreviews = SettingManager.get("notification_chat_showPreviews") as? Boolean ?: true

                notificationBuilder
                    .setContentTitle("Contact Offline")
                    .setContentText(if (showPreviews) contactOffline.message else "Contact is offline")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .apply {
                        if (soundEnabled) setSound(getNotificationSound(context))
                        if (vibrationEnabled) setVibrate(longArrayOf(0, 500, 250, 500))
                        if (chat?.isPinned == true) setPriority(NotificationCompat.PRIORITY_HIGH)
                    }
                    .setCategory(NOTIFICATION_MESSAGE_CATEGORY)
                    .setContentIntent(createPendingIntent(context, contactOffline.from, contactOffline.to, NOTIFICATION_MESSAGE_CATEGORY))
                    .setAutoCancel(true)
                    .setExtras(android.os.Bundle().apply {
                        putString("owner", contactOffline.from)
                        putString("jid", contactOffline.to)
                    })

                notificationId = listOf(contactOffline.to, contactOffline.from, NOTIFICATION_MESSAGE_CATEGORY).prp()
                contactOffline.showed = true

                // Store NotificationStorageItem
                realmWriteAsync {
                    copyToRealm(NotificationStorageItem().apply {
                        primary = NotificationStorageItem.genPrimary(contactOffline.from, contactOffline.to, notificationId)
                        this.owner = contactOffline.from
                        this.jid = contactOffline.to
                        this.uniqueId = notificationId
                        this.category = Category.CONTACT
                        this.isRead = false
                        this.text = contactOffline.message
                        this.date = contactOffline.timestamp
                        this.shouldShow = true
                        this.metadata = mapOf(
                            "owner" to contactOffline.from,
                            "jid" to contactOffline.to,
                            "category" to NOTIFICATION_MESSAGE_CATEGORY
                        )
                    }, UpdatePolicy.ALL)
                }
            }
            NotifyType.VERIFICATION -> {
                val soundEnabled = SettingManager.get("notification_chat_sound") as? Boolean ?: true
                val vibrationEnabled = SettingManager.get("notification_chat_vibration") as? Boolean ?: true
                val showPreviews = SettingManager.get("notification_chat_showPreviews") as? Boolean ?: true

                notificationBuilder
                    .setContentTitle(verification.displayName)
                    .setContentText(if (showPreviews) verification.message else "Verification request")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .apply {
                        if (soundEnabled) setSound(getNotificationSound(context))
                        if (vibrationEnabled) setVibrate(longArrayOf(0, 500, 250, 500))
                    }
                    .setCategory(NOTIFICATION_VERIFICATION_CATEGORY)
                    .setContentIntent(createPendingIntent(context, verification.owner ?: "", "", NOTIFICATION_VERIFICATION_CATEGORY))
                    .setAutoCancel(true)
                    .setExtras(android.os.Bundle().apply {
                        putString("owner", verification.owner)
                        putString("sid", verification.id)
                    })

                notificationId = verification.id
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifications = systemNotificationManager.getActiveNotifications()
            val stanzaId = notificationBuilder.build().extras.getString("stanzaId") ?: "none"
            if (!notifications.any { it.notification.extras.getString("stanzaId") == stanzaId } &&
                !deliveredNotificationsIds.contains(stanzaId)) {
                notificationManager.notify(notificationId.hashCode(), notificationBuilder.build())
                deliveredNotificationsIds.add(stanzaId)
            }
        } else {
            // Fallback for pre-API 23: Skip duplicate check and notify directly
            notificationManager.notify(notificationId.hashCode(), notificationBuilder.build())
            deliveredNotificationsIds.add(notificationBuilder.build().extras.getString("stanzaId") ?: "none")
        }

        // Update XMPPNotificationsManagerStorageItem
        if (type == NotifyType.NEW_MESSAGE || type == NotifyType.SUBSCRIPTION || type == NotifyType.VERIFICATION) {
            realmWriteAsync {
                val notificationManagerItem = query<XMPPNotificationsManagerStorageItem>("primary = $0", XMPPNotificationsManagerStorageItem.genPrimary(message.from)).first().find()
                if (notificationManagerItem != null) {
                    notificationManagerItem.lastItemId = notificationId
                    notificationManagerItem.unread = unreadMessagesCount
                } else {
                    copyToRealm(XMPPNotificationsManagerStorageItem().apply {
                        primary = XMPPNotificationsManagerStorageItem.genPrimary(message.from)
                        owner = message.from
                        lastItemId = notificationId
                        unread = unreadMessagesCount
                    }, UpdatePolicy.ALL)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun showSimpleNotify(context: Context, title: String, subtitle: String, body: String) {
        val notificationId = "simpleNotify_${UUID.randomUUID()}"
        val soundEnabled = SettingManager.get("notification_chat_sound") as? Boolean ?: true
        val vibrationEnabled = SettingManager.get("notification_chat_vibration") as? Boolean ?: true
        val showPreviews = SettingManager.get("notification_chat_showPreviews") as? Boolean ?: true

        val notificationBuilder = NotificationCompat.Builder(context, createNotificationChannel(context))
            .setContentTitle(title)
            .setContentText(if (showPreviews) body else "New notification")
            .setSubText(subtitle)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .apply {
                if (soundEnabled) setSound(getNotificationSound(context))
                if (vibrationEnabled) setVibrate(longArrayOf(0, 500, 250, 500))
            }
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(notificationId.hashCode(), notificationBuilder.build())

        // Store ShowedNotificationRequests
        realmWriteAsync {
            copyToRealm(ShowedNotificationRequests().apply {
                primary = ShowedNotificationRequests.genPrimary(notificationId, "")
                this.owner = ""
                this.jid = ""
                this.requestId = notificationId
                this.stanzaId = ""
                this.kind = ShowedNotificationRequests.Kind.SYSTEM
            }, UpdatePolicy.ALL)
        }

        // Store NotificationStorageItem
        realmWriteAsync {
            copyToRealm(NotificationStorageItem().apply {
                primary = NotificationStorageItem.genPrimary("", "", notificationId)
                this.owner = ""
                this.jid = ""
                this.uniqueId = notificationId
                this.category = Category.DEVICE
                this.isRead = false
                this.displayedNick = subtitle
                this.text = body
                this.date = Date().time
                this.shouldShow = true
                this.metadata = mapOf("category" to "simple")
            }, UpdatePolicy.ALL)
        }
    }

    fun isManuallyMuted(owner: String, contact: String, conversationType: ConversationType): Boolean {
        return realm.query<NotifyPersonalStorageItem>("owner = $0", owner).first().find()?.let { settings ->
            if (settings.muteAll) true
            else realm.query<LastChatsStorageItem>(
                "primary = '${LastChatsStorageItem.genPrimary(contact, owner, conversationType)}'"
            ).first().find()?.let { chat ->
                chat.isMuted || chat.muteExpired > System.currentTimeMillis()
            } ?: false
        } ?: false
    }

    fun isMuted(owner: String, contact: String, conversationType: ConversationType): Boolean {
        return realm.query<LastChatsStorageItem>(
            "primary = '${LastChatsStorageItem.genPrimary(contact, owner, conversationType)}'"
        ).first().find()?.let { chat ->
            chat.isMuted || chat.muteExpired > System.currentTimeMillis()
        } ?: false
    }

    fun muteAll(owner: String) {
        realmWriteAsync {
            val settings = query<NotifyPersonalStorageItem>("owner = $0", owner).first().find()
            if (settings != null) {
                settings.muteAll = true
            } else {
                copyToRealm(NotifyPersonalStorageItem().apply {
                    this.owner = owner
                    this.muteAll = true
                })
            }
        }
    }

    fun unmuteAll(owner: String) {
        realmWriteAsync {
            val settings = query<NotifyPersonalStorageItem>("owner = $0", owner).first().find()
            settings?.muteAll = false
        }
    }

    fun isMutedAll(owner: String): Boolean {
        return realm.query<NotifyPersonalStorageItem>("owner = $0", owner).first().find()?.muteAll ?: false
    }

    fun clearNotifications(context: Context, jid: String, owner: String) {
        CoroutineScope(Dispatchers.Main).launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notifications = systemNotificationManager.getActiveNotifications()
                val ids = notifications
                    .filter { it.notification.extras.getString("jid") == jid && it.notification.extras.getString("owner") == owner }
                    .map { it.id }
                ids.forEach { NotificationManagerCompat.from(context).cancel(it) }
            }

            // Update NotificationStorageItem
            realmWriteAsync {
                query<NotificationStorageItem>("owner = $0 AND jid = $1", owner, jid).find().forEach {
                    it.isRead = true
                    it.shouldShow = false
                }
            }
        }
    }

    fun clearAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()

        // Update NotificationStorageItem
        realmWriteAsync {
            query<NotificationStorageItem>().find().forEach {
                it.isRead = true
                it.shouldShow = false
            }
        }
    }

    fun clearNotificationsFor(context: Context, jid: String) {
        CoroutineScope(Dispatchers.Main).launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notifications = systemNotificationManager.getActiveNotifications()
                val ids = notifications
                    .filter { it.notification.extras.getString("owner") == jid }
                    .map { it.id }
                ids.forEach { NotificationManagerCompat.from(context).cancel(it) }
            }
            clearUncategorizedNotifications(context)

            // Update NotificationStorageItem
            realmWriteAsync {
                query<NotificationStorageItem>("owner = $0", jid).find().forEach {
                    it.isRead = true
                    it.shouldShow = false
                }
            }
        }
    }

    fun clearUncategorizedNotifications(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notifications = systemNotificationManager.getActiveNotifications()
                val ids = notifications
                    .filter { !NOTIFICATION_CATEGORIES.contains(it.notification.category) }
                    .map { it.id }
                ids.forEach { NotificationManagerCompat.from(context).cancel(it) }
            }

            // Update NotificationStorageItem
            realmWriteAsync {
                query<NotificationStorageItem>().find().forEach {
                    if (!NOTIFICATION_CATEGORIES.contains(it.metadata?.get("category") as? String)) {
                        it.isRead = true
                        it.shouldShow = false
                    }
                }
            }
        }
    }

    fun clearNotifications(context: Context, timestamp: Long, owner: String, jid: String) {
        CoroutineScope(Dispatchers.Main).launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notifications = systemNotificationManager.getActiveNotifications()
                val ids = notifications
                    .filter { it.notification.extras.getString("jid") == jid && it.notification.extras.getString("owner") == owner }
                    .filter { it.notification.extras.getLong("timestamp", 0) < timestamp }
                    .map { it.id }
                ids.forEach { NotificationManagerCompat.from(context).cancel(it) }
            }

            // Update NotificationStorageItem
            realmWriteAsync {
                query<NotificationStorageItem>("owner = $0 AND jid = $1 AND date < $2", owner, jid, timestamp).find().forEach {
                    it.isRead = true
                    it.shouldShow = false
                }
            }
        }
    }

    fun clearNotifications(context: Context, stanzaIds: List<String>) {
        CoroutineScope(Dispatchers.Main).launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notifications = systemNotificationManager.getActiveNotifications()
                val ids = mutableListOf<Int>()
                stanzaIds.forEach { stanzaId ->
                    val notification = notifications.find { it.notification.extras.getString("stanzaId") == stanzaId }
                    if (notification != null) {
                        val userInfo = notification.notification.extras
                        val timestamp = userInfo.getLong("timestamp", 0)
                        val jid = userInfo.getString("jid")
                        val owner = userInfo.getString("owner")
                        if (jid != null && owner != null) {
                            ids.addAll(notifications
                                .filter { it.notification.extras.getString("jid") == jid && it.notification.extras.getString("owner") == owner }
                                .filter { it.notification.extras.getLong("timestamp", 0) <= timestamp }
                                .map { it.id })
                        }
                    }
                }
                ids.addAll(notifications
                    .filter { !NOTIFICATION_CATEGORIES.contains(it.notification.category) }
                    .map { it.id })
                ids.forEach { NotificationManagerCompat.from(context).cancel(it) }
            }

            // Update NotificationStorageItem
            realmWriteAsync {
                stanzaIds.forEach { stanzaId ->
                    query<NotificationStorageItem>("stanzaId = $0", stanzaId).find().forEach {
                        it.isRead = true
                        it.shouldShow = false
                    }
                }
            }
        }
    }

    suspend fun onMarkAsReadMessageNotification(context: Context, userInfo: Map<String, Any>, completionHandler: (() -> Unit)? = null): Boolean {
        val owner = userInfo["owner"] as? String ?: return false
        val jid = userInfo["jid"] as? String ?: return false
        val stanzaId = userInfo["stanzaId"] as? String ?: return false

        // Check if chat is blocked
        val chat = realm.query<LastChatsStorageItem>(
            "primary = '${LastChatsStorageItem.genPrimary(jid, owner, ConversationType.Regular)}'"
        ).first().find()
        if (chat?.isBlocked == true) return false

        try {
            val message = XMPPMessage(
                raw = """
                    <message type='chat' id='${UUID.randomUUID()}' to='$jid' from='$owner'>
                        <displayed xmlns='urn:xmpp:chat-markers:0' id='$stanzaId'/>
                    </message>
                """.trimIndent(),
                type = "chat",
                id = UUID.randomUUID().toString(),
                to = com.xabber.xmpp.jid.XMPPJID(fullJID = jid),
                from = com.xabber.xmpp.jid.XMPPJID(fullJID = owner)
            )
            MessageCommonSender(owner).sendSimpleMessage("", jid, conversationType = ConversationType.Regular)
            completionHandler?.invoke()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark message as read: ${e.message}")
            completionHandler?.invoke()
            return false
        }
    }

    suspend fun onReplyMessageNotification(context: Context, userInfo: Map<String, Any>, text: String, completionHandler: (() -> Unit)? = null): Boolean {
        val owner = userInfo["owner"] as? String ?: return false
        val jid = userInfo["jid"] as? String ?: return false

        // Check if chat is blocked
        val chat = realm.query<LastChatsStorageItem>(
            "primary = '${LastChatsStorageItem.genPrimary(jid, owner, ConversationType.Regular)}'"
        ).first().find()
        if (chat?.isBlocked == true) return false

        try {
            MessageCommonSender(owner).sendSimpleMessage(text, jid, conversationType = ConversationType.Regular)
            completionHandler?.invoke()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reply to message: ${e.message}")
            completionHandler?.invoke()
            return false
        }
    }

    suspend fun onSubscribeContactNotification(context: Context, userInfo: Map<String, Any>, completionHandler: (() -> Unit)? = null): Boolean {
        val owner = userInfo["owner"] as? String ?: return false
        val jid = userInfo["jid"] as? String ?: return false

        // Check if chat is blocked
        val chat = realm.query<LastChatsStorageItem>(
            "primary = '${LastChatsStorageItem.genPrimary(jid, owner, ConversationType.Regular)}'"
        ).first().find()
        if (chat?.isBlocked == true) return false

        try {
            val stream = AccountManager.find(owner)?.stream ?: return false
            val subscribe = """
                <presence type='subscribe' to='$jid' from='$owner'/>
            """.trimIndent()
            val subscribed = """
                <presence type='subscribed' to='$jid' from='$owner'/>
            """.trimIndent()
            stream.socket?.write(subscribe)
            stream.socket?.write(subscribed)
            completionHandler?.invoke()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe contact: ${e.message}")
            completionHandler?.invoke()
            return false
        }
    }

    suspend fun onUnsubscribeContactNotification(context: Context, userInfo: Map<String, Any>, completionHandler: (() -> Unit)? = null): Boolean {
        val owner = userInfo["owner"] as? String ?: return false
        val jid = userInfo["jid"] as? String ?: return false

        try {
            val stream = AccountManager.find(owner)?.stream ?: return false
            val unsubscribe = """
                <presence type='unsubscribe' to='$jid' from='$owner'/>
            """.trimIndent()
            val unsubscribed = """
                <presence type='unsubscribed' to='$jid' from='$owner'/>
            """.trimIndent()
            val block = """
                <iq type='set' id='${UUID.randomUUID()}'>
                    <block xmlns='urn:xmpp:blocking'>
                        <item jid='$jid'/>
                    </block>
                </iq>
            """.trimIndent()
            stream.socket?.write(unsubscribe)
            stream.socket?.write(unsubscribed)
            stream.socket?.write(block)

            // Update LastChatsStorageItem
            realmWriteAsync {
                val chat = query<LastChatsStorageItem>(
                    "primary = '${LastChatsStorageItem.genPrimary(jid, owner, ConversationType.Regular)}'"
                ).first().find()
                chat?.isBlocked = true
            }

            completionHandler?.invoke()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe contact: ${e.message}")
            completionHandler?.invoke()
            return false
        }
    }

    suspend fun onJoinGroupNotification(context: Context, userInfo: Map<String, Any>, completionHandler: (() -> Unit)? = null): Boolean {
        val owner = userInfo["owner"] as? String ?: return false
        val jid = userInfo["groupchat"] as? String ?: return false

        // Check if chat is blocked
        val chat = realm.query<LastChatsStorageItem>(
            "primary = '${LastChatsStorageItem.genPrimary(jid, owner, ConversationType.Group)}'"
        ).first().find()
        if (chat?.isBlocked == true) return false

        try {
            val stream = AccountManager.find(owner)?.stream ?: return false
            val subscribe = """
                <presence type='subscribe' to='$jid' from='$owner'/>
            """.trimIndent()
            stream.socket?.write(subscribe)
            completionHandler?.invoke()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join group: ${e.message}")
            completionHandler?.invoke()
            return false
        }
    }

    suspend fun onDeclineGroupNotification(context: Context, userInfo: Map<String, Any>, completionHandler: (() -> Unit)? = null): Boolean {
        val owner = userInfo["owner"] as? String ?: return false
        val jid = userInfo["groupchat"] as? String ?: return false

        try {
            val stream = AccountManager.find(owner)?.stream ?: return false
            val unsubscribe = """
                <presence type='unsubscribe' to='$jid' from='$owner'/>
            """.trimIndent()
            val unsubscribed = """
                <presence type='unsubscribed' to='$jid' from='$owner'/>
            """.trimIndent()
            stream.socket?.write(unsubscribe)
            stream.socket?.write(unsubscribed)

            // Update LastChatsStorageItem
            realmWriteAsync {
                val chat = query<LastChatsStorageItem>(
                    "primary = '${LastChatsStorageItem.genPrimary(jid, owner, ConversationType.Group)}'"
                ).first().find()
                chat?.isBlocked = true
            }

            completionHandler?.invoke()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decline group: ${e.message}")
            completionHandler?.invoke()
            return false
        }
    }

    suspend fun onTouchMessageNotification(context: Context, userInfo: Map<String, Any>, atStart: Boolean, completionHandler: (() -> Unit)? = null) {
        val owner = userInfo["owner"] as? String ?: return
        val jid = userInfo["jid"] as? String ?: return
        if (jid == owner) return

        // Check if chat is blocked
        val conversationType = userInfo["conversation_type"]?.let { ConversationType.fromRaw(it as String) } ?: ConversationType.Regular
        val chat = realm.query<LastChatsStorageItem>(
            "primary = '${LastChatsStorageItem.genPrimary(jid, owner, conversationType)}'"
        ).first().find()
        if (chat?.isBlocked == true) return

        val stanzaId = userInfo["stanzaId"] as? String
        if (stanzaId != null) {
            try {
                val message = XMPPMessage(
                    raw = """
                        <message type='chat' id='${UUID.randomUUID()}' to='$jid' from='$owner'>
                            <displayed xmlns='urn:xmpp:chat-markers:0' id='$stanzaId'/>
                        </message>
                    """.trimIndent(),
                    type = "chat",
                    id = UUID.randomUUID().toString(),
                    to = com.xabber.xmpp.jid.XMPPJID(fullJID = jid),
                    from = com.xabber.xmpp.jid.XMPPJID(fullJID = owner)
                )
                MessageCommonSender(owner).sendSimpleMessage("", jid, conversationType = conversationType)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark message as read: ${e.message}")
            }
        }

        // Check for group chat and incognito mode
        var isGroupchat = false
        var isIncognito = false
        realm.query<LastChatsStorageItem>(
            "primary = '${LastChatsStorageItem.genPrimary(jid, owner, ConversationType.Group)}'"
        ).first().find()?.let { groupChat ->
            isGroupchat = true
            isIncognito = (SettingManager.get("privacy_level") as? String) == "incognito"
        }

        if (atStart) {
            openViewControllerPayload = mapOf("owner" to owner, "jid" to jid, "action" to "initialChat")
        } else {
            openViewControllerPayload = mapOf("owner" to owner, "jid" to jid, "action" to "foregroundChat")
        }

        realmWriteAsync {
            val instance = query<LastChatsStorageItem>(
                "primary = '${LastChatsStorageItem.genPrimary(jid, owner, conversationType)}'"
            ).first().find()
            instance?.let {
                if (!it.isSynced) {
                    it.isPrereaded = true
                }
                it.unread = 0
            }

            // Update NotificationStorageItem
            query<NotificationStorageItem>("owner = $0 AND jid = $1", owner, jid).find().forEach {
                it.isRead = true
                it.shouldShow = false
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            completionHandler?.invoke()
        }
    }

    fun onTouchVerificationNotification(context: Context, userInfo: Map<String, Any>, completionHandler: (() -> Unit)? = null) {
        val owner = userInfo["owner"] as? String ?: return
        val sid = userInfo["sid"] as? String ?: return

        realmWriteAsync {
            val instance = query<VerificationSessionStorageItem>(
                "primary = '${VerificationSessionStorageItem.genPrimary(jid = owner, owner = sid)}'"
            ).first().find()
            instance?.let {
                when (it.state) {
                    VerificationSessionStorageItem.VerificationState.RECEIVED_REQUEST -> {
                        sendVerificationBroadcast(context, owner, sid, "showConfirmation")
                    }
                    VerificationSessionStorageItem.VerificationState.RECEIVED_REQUEST_ACCEPT -> {
                        sendVerificationBroadcast(context, owner, sid, "showCodeInput")
                    }
                    VerificationSessionStorageItem.VerificationState.FAILED,
                    VerificationSessionStorageItem.VerificationState.REJECTED,
                    VerificationSessionStorageItem.VerificationState.TRUSTED -> {
                        delete(it)
                        Log.d(TAG, "Deleted verification session for owner=$owner, sid=$sid, state=${it.state}")
                    }
                    else -> {}
                }
            }

            // Update NotificationStorageItem
            query<NotificationStorageItem>("owner = $0 AND uniqueId = $1", owner, sid).find().forEach {
                it.isRead = true
                it.shouldShow = false
            }
        }
        completionHandler?.invoke()
    }

    fun setLastChatsDisplayed(state: Boolean) {
        lastChatsDisplayedState = state
    }

    fun isLastChatsDisplayed(): Boolean {
        return lastChatsDisplayedState
    }

    private fun createNotificationChannel(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "xabber_notifications"
            val channel = NotificationChannel(
                channelId,
                "Xabber Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for Xabber chat events"
            }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
            return channelId
        }
        return ""
    }

    private fun getNotificationSound(context: Context): android.net.Uri? {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun createPendingIntent(context: Context, owner: String, jid: String, category: String): PendingIntent {
        val intent = Intent(context, Class.forName("com.xabber.presentation.ChatActivity")).apply {
            putExtra("owner", owner)
            putExtra("jid", jid)
            putExtra("category", category)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notificationManager(context: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }

    private fun sendVerificationBroadcast(context: Context, owner: String, sid: String, action: String) {
        val intent = Intent("com.xabber.verification.$action").apply {
            putExtra("owner", owner)
            putExtra("sid", sid)
        }
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        Log.d(TAG, "Sent verification broadcast: action=$action, owner=$owner, sid=$sid")
    }
}