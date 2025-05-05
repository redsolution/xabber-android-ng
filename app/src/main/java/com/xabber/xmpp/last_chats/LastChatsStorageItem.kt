package com.xabber.xmpp.last_chats
//
//import com.xabber.utils.prp
//import com.xabber.xmpp.messages.MessageStorageItem
//import com.xabber.xmpp.roster.RosterStorageItem
//import io.realm.kotlin.ext.realmListOf
//import io.realm.kotlin.types.RealmList
//import io.realm.kotlin.types.RealmObject
//import io.realm.kotlin.types.annotations.Ignore
//import io.realm.kotlin.types.annotations.Index
//import io.realm.kotlin.types.annotations.PrimaryKey
//import java.util.Date
//
//
//open class LastChatsStorageItem : RealmObject {
//    companion object {
//
//        fun genPrimary(jid: String, owner: String, conversationType: ConversationType): String {
//            return listOf(jid, owner, conversationType.rawValue).prp()
//        }
//    }
//   @PrimaryKey
//   var primary: String = ""
//    var owner: String = ""
//    @Ignore var jid: String = ""
//    @Ignore
//    var messageDate: Date = Date(0)
//    @Ignore var lastReadMessageDate: Date = Date()
//    var rosterItem: RosterStorageItem? = null
//    var lastMessage: MessageStorageItem? = null
//    var lastMessageId: String = ""
//    var isSynced: Boolean = true
//    var isInitialArchiveLoaded: Boolean = false
//    var isHistoryGapFixedForSession: Boolean = false
//    var isArchived: Boolean = false
//    var fullArchiveLoaded: Boolean = false
//    var retractVersion: String? = null
//    var mentionId: String? = null
//    var lastReadId: String? = null
//    var displayedId: String? = null
//    var deliveredId: String? = null
//    var lastLoadedMessageHistoryId: String? = null
//    var unread: Int = 0
//    var isBlocked: Boolean = false // TODO: Deprecate as per Swift
//    var draftMessage: String? = null
//    var groupchatMyId: String? = null
//    var isPrereaded: Boolean = false
//    var pinnedPosition: Double = 0.0
//    var isPinned: Boolean = false
//    var muteExpired: Double = -1.0
//    var afterburnInterval: Double = -1.0
//    var afterburnIntervalLastUpdate: Double = -1.0
//    var isAllHistoryLoaded: Boolean = false
//    var isFreshNotEmptyEncryptedChat: Boolean = false
//    var hasErrorInChat: Boolean = false
//    var updateTS: Double = 0.0
//    var lastChatOffset: Float = 0f
//
//    private var conversationTypeRaw: String = ConversationType.OMEMO.rawValue
//    private var chatStateRaw: Int = 0
//
//    var chatMarkersSupport: Boolean = false
//
//
//    var conversationType: ConversationType
//        get() = ConversationType.fromRaw(conversationTypeRaw)
//        set(value) {
//            conversationTypeRaw = value.rawValue
//        }
//
//
//    var chatState: ComposingType
//        get() = ComposingType.fromRaw(chatStateRaw)
//        set(value) {
//            chatStateRaw = value.rawValue
//        }
//
//
//    val isAfterburnEnabled: Boolean
//        get() = afterburnInterval > 0
//
//
//    val isMuted: Boolean
//        get() = (System.currentTimeMillis() / 1000.0) < muteExpired
//
//
////    fun setPrimary(owner: String) {
////        this.primary = genPrimary(jid, owner, conversationType)
////        this.owner = owner
////    }
//}
//
//
//enum class ConversationType(val rawValue: String) {
//    OMEMO("omemo"),
//    REGULAR("regular");
//
//    companion object {
//        fun fromRaw(raw: String): ConversationType =
//            values().find { it.rawValue == raw } ?: REGULAR
//    }
//}
//
//
//enum class ComposingType(val rawValue: Int) {
//    NONE(0),
//    TYPING(1),
//    VOICE(2),
//    VIDEO(3),
//    UPLOAD_FILE(4),
//    UPLOAD_IMAGE(5),
//    UPLOAD_AUDIO(6);
//
//    companion object {
//        fun fromRaw(raw: Int): ComposingType =
//            values().find { it.rawValue == raw } ?: NONE
//    }
//}
//
