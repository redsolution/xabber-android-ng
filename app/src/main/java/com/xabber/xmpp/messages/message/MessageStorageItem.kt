package com.xabber.xmpp.messages

import android.content.Context
import android.util.Log
import com.xabber.R
import com.xabber.utils.prp
import com.xabber.xmpp.last_chats.LastChatsStorageItem
import com.xabber.xmpp.roster.Ask
import com.xabber.xmpp.roster.RosterGroupStorageItem
import com.xabber.xmpp.roster.RosterStorageItem
import com.xabber.xmpp.roster.Subscription
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import org.json.JSONObject
import java.util.Date


open class MessageStorageItem : RealmObject {
    companion object {
        private const val TAG = "MessageStorageItem"

        const val ADD_CONTACT_LOCAL_ARCHIVED_ID = "add-contact-local-archived-id"


        fun messageIdForAuthRequest(jid: String): String {
            return listOf("subscribtion", jid).prp()
        }


        fun messageIdForContact(owner: String, jid: String, ts: String): String {
            return listOf("contact", ts, jid, owner).prp()
        }


        fun messageIdForVoIPCall(owner: String, jid: String, callId: String): String {
            return listOf("voip", owner, jid, callId).prp()
        }


        fun messageIdForInitial(jid: String, conversationType: ConversationType): String {
            return listOf(jid, conversationType.rawValue, "initial_message").prp()
        }


        fun genPrimary(messageId: String, owner: String): String {
            return "${messageId}_$owner"
        }
    }

    @PrimaryKey
    var primary: String = ""

    @Index
    var owner: String = ""

    @Index
    var opponent: String = ""

    @Index
    var body: String = ""

    var legacyBody: String = ""

    @Index
    var date: Date = Date()

    var sentDate: Date = Date()
    var editDate: Date? = null
    var outgoing: Boolean = false
    var isRead: Boolean = false

    @Index
    private var messageType: Int = MessageDisplayType.TEXT.rawValue

    @Index
    var messageId: String = ""

    var trustedSource: Boolean = false
    var previousId: String? = null
    var queryIds: String? = null

    @Index
    var archivedId: String = ""

    var isDeleted: Boolean = false
    private var stateRaw: Int = 0
    var groupchatCard: GroupchatUserStorageItem? = null
    var envelopeContainer: String? = null
    var afterburnInterval: Double = -1.0
    var burnDate: Double = -1.0
    var readDate: Double = -1.0
    private var errorMetadataRaw: String? = null
    private var systemMetadataRaw: String? = null
    var messageError: String? = null
    var messageErrorCode: String? = null
    private var conversationTypeRaw: String = ConversationType.REGULAR.rawValue
    var references: RealmList<MessageReferenceStorageItem> = realmListOf()
    var inlineForwards: RealmList<MessageForwardsInlineStorageItem> = realmListOf()

    @Ignore
    var forceUnreadState: Boolean? = null

    @Ignore
    var isInvite: Boolean = false

    @Ignore
    var originalStanza: XMPPMessage? = null


    var displayAs: MessageDisplayType
        get() = MessageDisplayType.fromRaw(messageType)
        set(value) {
            messageType = value.rawValue
        }


    var state: MessageSendingState
        get() = if (displayAs == MessageDisplayType.SYSTEM) {
            MessageSendingState.NONE
        } else {
            MessageSendingState.fromRaw(stateRaw)
        }
        set(value) {
            stateRaw = value.rawValue
        }


    var conversationType: ConversationType
        get() = ConversationType.fromRaw(conversationTypeRaw)
        set(value) {
            conversationTypeRaw = value.rawValue
        }


    val isHasAttachedMessages: Boolean
        get() = false


    val groupchatMetadata: Map<String, Any>?
        get() = references.find { it.kind == ReferenceKind.GROUPCHAT }?.metadata


    val groupchatAuthorId: String?
        get() = if (displayAs == MessageDisplayType.SYSTEM) {
            null
        } else {
            groupchatCard?.userId ?: groupchatMetadata?.get("id") as? String
        }


    val groupchatAuthorNickname: String?
        get() = if (displayAs == MessageDisplayType.SYSTEM) {
            null
        } else {
            groupchatCard?.nickname ?: (groupchatMetadata?.get("nickname") as? String
                ?: groupchatMetadata?.get("jid") as? String)
        }


    val groupchatAuthorBadge: String?
        get() {
            val role = groupchatCard?.role?.localized ?: (groupchatMetadata?.get("role") as? String)
            val badge = groupchatCard?.badge ?: (groupchatMetadata?.get("badge") as? String ?: "")
            return if (role?.lowercase() == "member") {
                badge
            } else {
                if (badge.isNotEmpty()) badge else role?.replaceFirstChar { it.uppercase() }
            }
        }

    val groupchatDisplayedNickname: String?
        get() = groupchatAuthorNickname?.let {
            if (displayAs != MessageDisplayType.SYSTEM) {
                if (outgoing) "You:" else it
            } else null
        }


    val groupchatUserAvatarPath: String?
        get() = (groupchatMetadata?.get("avatar_uri") as? String)?.let {
            listOf(it, opponent).prp()
        }


    val callMetadata: Map<String, Any>?
        get() = references.find { it.kind == ReferenceKind.CALL }?.metadata


    var errorMetadata: Map<String, Any>?
        get() = errorMetadataRaw?.let { raw ->
            try {
                JSONObject(raw).toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot parse error metadata for message $messageId: ${e.message}")
                null
            }
        }
        set(value) {
            errorMetadataRaw = value?.let { map ->
                try {
                    JSONObject(map).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot encode error metadata for message $messageId: ${e.message}")
                    null
                }
            }
        }


    var systemMetadata: Map<String, Any>?
        get() = systemMetadataRaw?.let { raw ->
            try {
                JSONObject(raw).toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Cannot parse system metadata for message $messageId: ${e.message}")
                null
            }
        }
        set(value) {
            systemMetadataRaw = value?.let { map ->
                try {
                    JSONObject(map).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot encode system metadata for message $messageId: ${e.message}")
                    null
                }
            }
        }


    fun displayedBody(context: Context): String {
        return when (displayAs) {
            MessageDisplayType.INITIAL -> ""
            MessageDisplayType.TEXT, MessageDisplayType.QUOTE -> {
                if (inlineForwards.isNotEmpty()) {
                    body
                } else {
                    body.trim()
                }
            }
            MessageDisplayType.FILES -> {
                val count = references.count { it.kind != ReferenceKind.GROUPCHAT }
                val firstRef = references.firstOrNull { it.kind != ReferenceKind.GROUPCHAT }
                if (count == 1 && firstRef?.sizeInBytes != null) {
                    context.getString(R.string.chat_message_file_count, firstRef.sizeInBytes.toString())
                } else {
                    context.getString(R.string.chat_message_file)
                }
            }
            MessageDisplayType.IMAGES -> {
                val count = references.count { it.kind != ReferenceKind.GROUPCHAT }
                val firstRef = references.firstOrNull { it.kind != ReferenceKind.GROUPCHAT }
                if (count == 1 && firstRef?.sizeInBytes != null) {
                    context.getString(R.string.chat_message_image_count, firstRef.sizeInBytes.toString())
                } else {
                    context.getString(R.string.chat_message_image)
                }
            }
            MessageDisplayType.VOICE -> {
                val duration = references.firstOrNull { it.kind == ReferenceKind.VOICE }
                    ?.metadata?.get("duration") as? Double
                duration?.let {
                    val minutes = (it / 60).toInt()
                    val seconds = (it % 60).toInt()
                    context.getString(R.string.chat_message_voice_duration, "$minutes:${seconds.toString().padStart(2, '0')}")
                } ?: context.getString(R.string.chat_message_voice)
            }
            MessageDisplayType.CALL -> {
                val metadata = callMetadata
                val outgoing = metadata?.get("outgoing") as? Boolean
                val state = VoIPCallState.fromRaw(metadata?.get("callState") as? String ?: "none")
                val duration = metadata?.get("duration") as? Double
                if (duration != null && duration > 0 && state == VoIPCallState.MADE) {
                    val minutes = (duration / 60).toInt()
                    val seconds = (duration % 60).toInt()
                    val durationStr = "$minutes:${seconds.toString().padStart(2, '0')}"
                    if (outgoing == true) {
                        context.getString(R.string.chat_message_outgoing_call, durationStr)
                    } else {
                        context.getString(R.string.chat_message_incoming_call, durationStr)
                    }
                } else {
                    when (state) {
                        VoIPCallState.MISSED -> context.getString(R.string.chat_message_missed_call)
                        VoIPCallState.NOANSWER -> context.getString(R.string.chat_message_cancelled_call)
                        VoIPCallState.BUSY -> if (outgoing == true) {
                            context.getString(R.string.chat_message_cancelled_call)
                        } else {
                            context.getString(R.string.chat_message_missing_call)
                        }
                        VoIPCallState.RECEIVED -> context.getString(R.string.chat_message_incoming)
                        else -> if (outgoing == true) {
                            context.getString(R.string.chat_message_outgoing)
                        } else {
                            context.getString(R.string.chat_message_incoming)
                        }
                    }
                }
            }
            MessageDisplayType.SYSTEM -> body.trim()
            MessageDisplayType.STICKER -> context.getString(R.string.chat_message_sticker)
        }
    }


    fun updatePrimary(system: Boolean = false, auth: Boolean = false) {
        if (primary.isNotEmpty()) return
        primary = genPrimary(messageId = messageId, owner = owner)
        if (system) primary += "_sys"
        if (auth) primary += "_auth"
        if (isInvite) primary += "_invite"
    }


    fun storeStanza(realm: Realm) {
        if (originalStanza == null || primary.isEmpty()) return
        val instance = MessageStanzaStorageItem().apply {
            set(messageId, owner, originalStanza?.xmlString ?: "", date, primary)
        }
        try {
            realm.writeBlocking {
                copyToRealm(instance, updatePolicy = UpdatePolicy.MODIFIED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot store stanza for message $messageId: ${e.message}")
        }
    }


    fun saveStanza(message: XMPPMessage, date: Date, realm: Realm) {
        if (primary.isEmpty()) return
        val stanza = MessageStanzaStorageItem().apply {
            set(messageId, owner, message.xmlString, date, primary)
        }
        try {
            realm.writeBlocking {
                if (query<MessageStanzaStorageItem>("primary = $0", stanza.primary).first().find() == null) {
                    copyToRealm(stanza, updatePolicy = UpdatePolicy.MODIFIED)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot save stanza for message $messageId: ${e.message}")
        }
    }


    fun configureSystemMessage(messageContainer: XMPPMessage, owner: String, opponent: String, date: Date) {
        references.addAll(parseReferences(messageContainer, opponent, owner))
        legacyBody = messageContainer.body ?: ""
        body = messageContainer.body ?: ""
        systemMetadata = parseSystemMessageMetadata(messageContainer)
        this.owner = owner
        this.opponent = opponent
        displayAs = MessageDisplayType.SYSTEM
        messageId = getUniqueMessageId(messageContainer, owner)
        archivedId = getStanzaId(messageContainer, owner)
        previousId = getPreviousId(messageContainer)
        this.date = date
        sentDate = date
        outgoing = false
        conversationType = ConversationType.GROUP
        updatePrimary()
    }

    fun editMessage(messageContainer: XMPPMessage, editDate: Date) {
        references.clear()
        references.addAll(parseReferences(messageContainer, opponent, owner))
        val groupchatRef = messageContainer.element("x", xmlns = "https://xabber.com/protocol/groups")
            ?.element("reference", xmlns = "https://xabber.com/protocol/references")
        body = messageContainer.body
            ?.xmlEscape(reverse = false)
            ?.excludeFromBody(messageContainer.elements("reference"), groupchatRef)
            ?.trim() ?: ""
        if (messageContainer.from == null) {
            messageContainer.addAttribute("from", outgoing ? owner : opponent)
        }
        inlineForwards.clear()
        inlineForwards.addAll(parseInlineMessages(messageContainer, primary, opponent, owner))
        updateDisplayMode()
        this.editDate = editDate
        messageError = "Edit"
        if (archivedId.isEmpty()) {
            archivedId = getStanzaId(messageContainer, owner)
        }
        originalStanza = messageContainer
    }


    fun configureIncomingMessage(
        messageContainer: XMPPMessage,
        owner: String,
        opponent: String,
        outgoing: Boolean,
        isRead: Boolean,
        date: Date,
        isEncrypted: Boolean = false
    ) {
        references.addAll(parseReferences(messageContainer, opponent, owner))
        val groupchatRef = messageContainer.element("x", xmlns = "https://xabber.com/protocol/groups")
            ?.element("reference", xmlns = "https://xabber.com/protocol/references")
        body = messageContainer.body
            ?.xmlEscape(reverse = false)
            ?.excludeFromBody(messageContainer.elements("reference"), groupchatRef)
            ?.trim() ?: ""
        if (messageContainer.from == null) {
            messageContainer.addAttribute("from", outgoing ? owner : opponent)
        }
        messageContainer.element("replaced")?.attributeStringValue("stamp")?.toXmppDate()?.let {
            editDate = it
            messageError = "Edit"
        }
        legacyBody = messageContainer.body ?: ""
        this.opponent = opponent
        this.owner = owner
        this.outgoing = outgoing
        this.isRead = isRead
        this.date = date
        sentDate = date
        messageId = getUniqueMessageId(messageContainer, owner)
        archivedId = getStanzaId(messageContainer, owner)
        previousId = getPreviousId(messageContainer)
        originalStanza = messageContainer
        conversationType = conversationTypeByMessage(messageContainer)
        updatePrimary()
        inlineForwards.addAll(parseInlineMessages(messageContainer, primary, opponent, owner))
        updateDisplayMode()
        references.forEach { it.messageId = primary }
        if (!outgoing) {
            try {
                val realm = Realm.open(realmConfiguration)
                groupchatCard = realm.query<GroupchatUserStorageItem>(
                    "groupchatId = $0 AND isMe = true",
                    listOf(opponent, owner).prp()
                ).first().find()
            } catch (e: Exception) {
                Log.e(TAG, "configureIncomingMessage: ${e.message}")
            }
        }
    }


    fun configureOutgoingMessage(
        body: String,
        legacy: String,
        messageId: String,
        owner: String,
        opponent: String,
        references: List<MessageReferenceStorageItem>,
        inlineForwards: List<MessageForwardsInlineStorageItem>
    ) {
        this.inlineForwards.addAll(inlineForwards)
        this.references.addAll(references)
        this.body = body
        this.legacyBody = legacy
        this.owner = owner
        this.opponent = opponent
        this.outgoing = true
        this.isRead = true
        this.messageId = messageId
        state = MessageSendingState.NOT_SENDED
        queryIds = "runtime_send"
        updatePrimary()
        updateDisplayMode()
        this.references.forEach { ref ->
            ref.messageId = primary
            ref.sentDate = Date()
            // TODO: Implement file encryption if needed
            // if (useFileEncryptionByDefault && ref.conversationType.isEncrypted) { ... }
        }
        try {
            val realm = Realm.open(realmConfiguration)
            groupchatCard = realm.query<GroupchatUserStorageItem>(
                "groupchatId = $0 AND isMe = true",
                listOf(opponent, owner).prp()
            ).first().find()
        } catch (e: Exception) {
            Log.e(TAG, "configureOutgoingMessage: ${e.message}")
        }
    }


    fun configureAuthRequestMessage(body: String, opponent: String, owner: String) {
        this.body = body
        this.opponent = opponent
        this.owner = owner
        this.isRead = true
        this.date = Date()
        conversationType = ConversationType.REGULAR // Replace with config.locked_conversation_type
        messageId = messageIdForAuthRequest(jid = opponent)
        displayAs = MessageDisplayType.SYSTEM
        sentDate = date
        outgoing = false
        state = MessageSendingState.NONE
        systemMetadata = mapOf("auth_message" to true)
        updatePrimary()
    }

    /**
     * Configures a contact-related message.
     */
    fun configureContactMessage(body: String, opponent: String, owner: String) {
        this.body = body
        this.opponent = opponent
        this.owner = owner
        this.isRead = true
        conversationType = ConversationType.REGULAR // Replace with config.locked_conversation_type
        this.date = Date()
        messageId = messageIdForContact(
            owner = owner,
            jid = opponent,
            ts = (date.time / 1000.0).toString()
        )
        displayAs = MessageDisplayType.SYSTEM
        primary = messageId
        // archivedId = ADD_CONTACT_LOCAL_ARCHIVED_ID
        sentDate = date
        outgoing = false
        state = MessageSendingState.NONE
    }


    fun configureVoIPCallMessage(
        opponent: String,
        owner: String,
        date: Date,
        isRead: Boolean,
        callId: String,
        archivedId: String?,
        outgoing: Boolean,
        duration: Double,
        callState: VoIPCallState
    ) {
        this.opponent = opponent
        this.owner = owner
        conversationType = ConversationType.REGULAR // Replace with config.locked_conversation_type
        messageId = messageIdForVoIPCall(owner, opponent, callId)
        primary = messageId
        this.date = date
        sentDate = date
        state = MessageSendingState.NONE
        this.isRead = isRead
        this.outgoing = outgoing
        displayAs = MessageDisplayType.CALL
        archivedId = archivedId ?: ""
        val reference = MessageReferenceStorageItem().apply {
            this.messageId = this@MessageStorageItem.messageId
            primary = listOf(owner, callId).prp()
            this.owner = owner
            kind = ReferenceKind.CALL
            metadata = mapOf(
                "duration" to duration,
                "outgoing" to outgoing,
                "callState" to callState.rawValue,
                "date" to (date.time / 1000.0)
            )
        }
        references.clear()
        try {
            val realm = Realm.open(realmConfiguration)
            val existingRef = realm.query<MessageReferenceStorageItem>("primary = $0", reference.primary).first().find()
            references.add(existingRef ?: reference)
        } catch (e: Exception) {
            Log.e(TAG, "configureVoIPCallMessage: ${e.message}")
        }
        trustedSource = true
    }


    fun isInStorage(realm: Realm): Boolean {
        return try {
            realm.query<MessageStorageItem>("primary = $0", primary).first().find() != null
        } catch (e: Exception) {
            Log.e(TAG, "isInStorage: ${e.message}")
            false
        }
    }


    fun save(realm: Realm, commitTransaction: Boolean, silentNotifications: Boolean = false): Boolean {
        if (opponent.isEmpty()) return false
        // TODO: Implement auto-delete logic if needed
        // if (autoDeleteInterval > 0 && displayAs != .INITIAL && date < threshold) return false

        // Handle groupchat user card updates if needed
        // TODO: Implement groupchatCard update logic
        updatePrimary()

        try {
            fun transaction(block: () -> Unit) {
                if (commitTransaction) {
                    realm.writeBlocking { block() }
                } else {
                    block()
                }
            }

            val existing = realm.query<MessageStorageItem>("primary = $0", primary).first().find()
            if (existing != null) {
                if (trustedSource && !existing.trustedSource) {
                    transaction {
                        if (archivedId.isNotEmpty()) existing.archivedId = archivedId
                        existing.trustedSource = true
                        existing.previousId = previousId
                    }
                }
                transaction {
                    queryIds?.let { newIds ->
                        if (newIds.contains("history")) {
                            existing.queryIds = existing.queryIds?.let { "$it,$newIds" } ?: newIds
                        }
                    }
                }
                return false
            }

            var notify = false
            val lastChat = realm.query<LastChatsStorageItem>(
                "primary = $0",
                LastChatsStorageItem.genPrimary(jid = opponent, owner = owner, conversationType = conversationType)
            ).first().find()

            if (lastChat != null) {
                transaction {
                    references.firstOrNull()?.metadata?.get("ephemeral-timer")?.let { timer ->
                        if (lastChat.afterburnIntervalLastUpdate < date.time / 1000.0) {
                            lastChat.afterburnIntervalLastUpdate = date.time / 1000.0
                            lastChat.afterburnInterval = (timer as? Int)?.toDouble() ?: -1.0
                        }
                    }
                    if (lastChat.isFreshNotEmptyEncryptedChat) {
                        lastChat.isFreshNotEmptyEncryptedChat = false
                    }
                }

                if ((lastChat.lastMessage?.date ?: Date(0)) > date) {
                    isRead = true
                    if (outgoing && archivedId.isNotEmpty()) {
                        archivedId.toDoubleOrNull()?.let { time ->
                            lastChat.deliveredId?.toDoubleOrNull()?.let { delivered ->
                                if (delivered > time) state = MessageSendingState.DELIVER
                            }
                            lastChat.displayedId?.toDoubleOrNull()?.let { displayed ->
                                if (displayed > time) state = MessageSendingState.READ
                            }
                        }
                    }
                    transaction {
                        realm.copyToRealm(this@MessageStorageItem, updatePolicy = UpdatePolicy.MODIFIED)
                        lastChat.rosterItem = realm.query<RosterStorageItem>(
                            "primary = $0",
                            RosterStorageItem.genPrimary(jid = opponent, owner = owner)
                        ).first().find()
                    }
                } else {
                    notify = true
                    transaction {
                        if (lastChat.isArchived && !lastChat.isMuted) {
                            lastChat.isArchived = false
                        }
                        lastChat.messageDate = sentDate
                        if (!isDeleted) lastChat.lastMessage = this@MessageStorageItem
                        lastChat.lastMessageId = messageId
                        references.firstOrNull()?.metadata?.get("ephemeral-timer")?.let { timer ->
                            lastChat.afterburnIntervalLastUpdate = date.time / 1000.0
                            lastChat.afterburnInterval = (timer as? Int)?.toDouble() ?: -1.0
                        } ?: run {
                            if (afterburnInterval > -1 && lastChat.afterburnIntervalLastUpdate < date.time / 1000.0) {
                                lastChat.afterburnIntervalLastUpdate = date.time / 1000.0
                                lastChat.afterburnInterval = afterburnInterval
                            }
                        }
                        if (isInvite && !isRead) {
                            if (lastChat.rosterItem?.subscription != Subscription.BOTH) {
                                lastChat.rosterItem?.ask = Ask.IN
                            }
                        }
                        if (!isRead && !outgoing && forceUnreadState == null) {
                            lastChat.unread += 1
                        } else if (outgoing) {
                            lastChat.unread = 0
                        }
                    }
                }
            } else {
                val instance = LastChatsStorageItem().apply {
                    jid = this@MessageStorageItem.opponent
                    conversationType = this@MessageStorageItem.conversationType
                    setPrimary(withOwner = owner)
                    messageDate = sentDate
                    lastMessage = if (!this@MessageStorageItem.isDeleted) this@MessageStorageItem else null
                    isSynced = conversationType in listOf(ConversationType.OMEMO, ConversationType.OMEMO1, ConversationType.AXOLOTL)
                    lastMessageId = this@MessageStorageItem.messageId
                    references.firstOrNull()?.metadata?.get("ephemeral-timer")?.let { timer ->
                        afterburnIntervalLastUpdate = date.time / 1000.0
                        afterburnInterval = (timer as? Int)?.toDouble() ?: -1.0
                    } ?: run {
                        afterburnIntervalLastUpdate = date.time / 1000.0
                        afterburnInterval = this@MessageStorageItem.afterburnInterval
                    }
                    if (displayAs == MessageDisplayType.INITIAL && conversationType.isEncrypted) {
                        isFreshNotEmptyEncryptedChat = true
                    }
                }
                transaction {
                    if (!instance.isInvalidated) {
                        realm.copyToRealm(instance, updatePolicy = UpdatePolicy.MODIFIED)
                        instance.rosterItem = realm.query<RosterStorageItem>(
                            "primary = $0",
                            RosterStorageItem.genPrimary(jid = opponent, owner = owner)
                        ).first().find() ?: run {
                            val rosterItem = RosterStorageItem().apply {
                                this.owner = this@MessageStorageItem.owner
                                jid = this@MessageStorageItem.opponent
                                subscription = Subscription.UNDEFINED
                                primary = RosterStorageItem.genPrimary(jid = opponent, owner = this.owner)
                            }
                            val group = realm.query<RosterGroupStorageItem>(
                                "primary = $0",
                                RosterGroupStorageItem.genPrimary(
                                    name = RosterGroupStorageItem.NOT_IN_ROSTER_GROUP_NAME,
                                    owner = owner
                                )
                            ).first().find() ?: RosterGroupStorageItem().apply {
                                isSystemGroup = true
                                name = RosterGroupStorageItem.NOT_IN_ROSTER_GROUP_NAME
                                this.owner = this@MessageStorageItem.owner
                                primary = RosterGroupStorageItem.genPrimary(
                                    name = RosterGroupStorageItem.NOT_IN_ROSTER_GROUP_NAME,
                                    owner = owner
                                )
                            }
                            if (!group.contacts.contains(rosterItem)) {
                                group.contacts.add(rosterItem)
                            }
                            realm.copyToRealm(group, updatePolicy = UpdatePolicy.MODIFIED)
                            rosterItem.associatedLastChat = instance
                            realm.copyToRealm(rosterItem, updatePolicy = UpdatePolicy.MODIFIED)
                            rosterItem
                        }
                    }
                }
                // TODO: Implement DefaultAvatarManager if needed
            }

            // TODO: Implement NotifyManager for notifications if needed
            return true
        } catch (e: Exception) {
            Log.e(TAG, "save: ${e.message}")
            return false
        }
    }

    /**
     * Updates the display mode based on references.
     */
    fun updateDisplayMode() {
        when {
            references.any { it.kind == ReferenceKind.CALL } -> {
                displayAs = MessageDisplayType.CALL
            }
            references.any { it.kind == ReferenceKind.VOICE } -> {
                if (body.trim().isEmpty()) {
                    displayAs = if (references.count { it.kind in listOf(ReferenceKind.VOICE, ReferenceKind.MEDIA) } == 1) {
                        MessageDisplayType.VOICE
                    } else {
                        MessageDisplayType.FILES
                    }
                } else {
                    displayAs = MessageDisplayType.TEXT
                }
            }
            references.any { it.mimeType in listOf(
                MimeIconType.FILE.rawValue,
                MimeIconType.ARCHIVE.rawValue,
                MimeIconType.DOCUMENT.rawValue,
                MimeIconType.PDF.rawValue,
                MimeIconType.PRESENTATION.rawValue,
                MimeIconType.VIDEO.rawValue,
                MimeIconType.AUDIO.rawValue
            ) } -> {
                displayAs = if (body.trim().isEmpty()) MessageDisplayType.FILES else MessageDisplayType.TEXT
            }
            references.any { it.mimeType == MimeIconType.IMAGE.rawValue } -> {
                val nonGroupRefs = references.filter { it.kind != ReferenceKind.GROUPCHAT }
                if (nonGroupRefs.size == 1 && nonGroupRefs.first().metadata?.get("name") == "Memoji") {
                    displayAs = MessageDisplayType.STICKER
                } else {
                    displayAs = if (body.trim().isEmpty()) MessageDisplayType.IMAGES else MessageDisplayType.TEXT
                }
            }
            references.any { it.mimeType == MimeIconType.FILE.rawValue } -> {
                displayAs = if (references.none { it.kind == ReferenceKind.QUOTE }) {
                    MessageDisplayType.TEXT
                } else {
                    MessageDisplayType.QUOTE
                }
            }
            references.any { it.kind == ReferenceKind.QUOTE } -> {
                displayAs = MessageDisplayType.QUOTE
            }
            references.any { it.kind == ReferenceKind.SYSTEM_MESSAGE } -> {
                displayAs = MessageDisplayType.SYSTEM
            }
            else -> {
                // Default case
            }
        }
    }
}

/**
 * Display type for messages.
 */
enum class MessageDisplayType(val rawValue: Int) {
    TEXT(0),
    FILES(1),
    IMAGES(2),
    VOICE(3),
    CALL(4),
    SYSTEM(5),
    STICKER(6),
    QUOTE(7),
    INITIAL(8);

    companion object {
        fun fromRaw(raw: Int): MessageDisplayType =
            values().find { it.rawValue == raw } ?: TEXT
    }
}

/**
 * Sending state for messages.
 */
enum class MessageSendingState(val rawValue: Int) {
    SENDED(0),
    DELIVER(1),
    READ(2),
    ERROR(3),
    NONE(4),
    NOT_SENDED(5),
    SENDING(6),
    UPLOADING(7);

    companion object {
        fun fromRaw(raw: Int): MessageSendingState =
            values().find { it.rawValue == raw } ?: NONE
    }
}

/**
 * VoIP call state.
 */
enum class VoIPCallState(val rawValue: String) {
    MISSED("missed"),
    NOANSWER("noanswer"),
    MADE("made"),
    BUSY("busy"),
    RECEIVED("received"),
    NONE("none");

    companion object {
        fun fromRaw(raw: String): VoIPCallState =
            values().find { it.rawValue == raw } ?: NONE
    }
}

/**
 * Conversation type (stubbed).
 */
enum class ConversationType(val rawValue: String) {
    REGULAR("regular"),
    OMEMO("omemo"),
    OMEMO1("omemo1"),
    AXOLOTL("axolotl"),
    GROUP("group");

    companion object {
        fun fromRaw(raw: String): ConversationType =
            values().find { it.rawValue == raw } ?: REGULAR
    }
}

/**
 * Reference kind (stubbed).
 */
enum class ReferenceKind(val xmlType: String) {
    MEDIA("media"),
    VOICE("voice"),
    CALL("call"),
    QUOTE("quote"),
    GROUPCHAT("groupchat"),
    SYSTEM_MESSAGE("systemMessage"),
    FORWARD("forward"),
    MARKUP("markup"),
    MENTION("mention"),
    NONE("none");

    companion object {
        fun fromXmlType(type: String): ReferenceKind =
            values().find { it.xmlType == type } ?: NONE
    }
}

/**
 * MIME type (stubbed).
 */
enum class MimeIconType(val rawValue: String) {
    FILE("file"),
    ARCHIVE("archive"),
    DOCUMENT("document"),
    PDF("pdf"),
    PRESENTATION("presentation"),
    VIDEO("video"),
    AUDIO("audio"),
    IMAGE("image");

    companion object {
        fun fromRaw(raw: String): MimeIconType =
            values().find { it.rawValue == raw } ?: FILE
    }
}

// Stubs for related classes and functions
open class GroupchatUserStorageItem : RealmObject {
    var groupchatId: String = ""
    var userId: String = ""
    var nickname: String? = null
    var role: String? = null
    var badge: String? = null
    var isMe: Boolean = false
    val localized: String? get() = role // Simplified
}

open class MessageReferenceStorageItem : RealmObject {
    var messageId: String = ""
    var primary: String = ""
    var owner: String = ""
    var kind: ReferenceKind = ReferenceKind.NONE
    var metadata: Map<String, Any>? = null
    var mimeType: String? = null
    var begin: Int = 0
    var end: Int = 0
    var sentDate: Date? = null
    var sizeInBytes: Int? = null
    val xmlType: String get() = kind.xmlType
    val range: IntRange get() = begin until end
}

open class MessageForwardsInlineStorageItem : RealmObject {
    var primary: String = ""
}

open class MessageStanzaStorageItem : RealmObject {
    var primary: String = ""
    fun set(messageId: String, owner: String, xmlString: String, date: Date, primary: String) {
        this.primary = primary
    }
}

data class XMPPMessage(val body: String? = null) {
    var from: String? = null
    var xmlString: String = ""
    fun element(name: String, xmlns: String? = null): DDXMLElement? = null
    fun elements(name: String): List<DDXMLElement> = emptyList()
    fun addAttribute(name: String, stringValue: String) {}
}

data class DDXMLElement(val name: String, val stringValue: String? = null) {
    var xmlns: String? = null
    fun element(name: String, xmlns: String? = null): DDXMLElement? = null
    fun attributeStringValue(name: String): String? = null
}

// Placeholder functions
fun parseReferences(message: XMPPMessage, jid: String, owner: String): List<MessageReferenceStorageItem> = emptyList()
fun parseSystemMessageMetadata(message: XMPPMessage): Map<String, Any> = emptyMap()
fun getUniqueMessageId(message: XMPPMessage, owner: String): String = ""
fun getStanzaId(message: XMPPMessage, owner: String): String = ""
fun getPreviousId(message: XMPPMessage): String? = null
fun parseInlineMessages(message: XMPPMessage, parentId: String, jid: String, owner: String): List<MessageForwardsInlineStorageItem> = emptyList()
fun conversationTypeByMessage(message: XMPPMessage): ConversationType = ConversationType.REGULAR
fun String.xmlEscape(reverse: Boolean): String = this
fun String.excludeFromBody(references: List<DDXMLElement>, groupchatRef: DDXMLElement?): String = this
fun String.toXmppDate(): Date? = null
fun JSONObject.toMap(): Map<String, Any> = emptyMap() // Simplified; use kotlinx.serialization if needed
val realmConfiguration = RealmConfiguration.create(
    schema = setOf(
        MessageStorageItem::class,
        GroupchatUserStorageItem::class,
        MessageReferenceStorageItem::class,
        MessageForwardsInlineStorageItem::class,
        MessageStanzaStorageItem::class,
        RosterStorageItem::class,
        LastChatsStorageItem::class,
        RosterGroupStorageItem::class
    )
)