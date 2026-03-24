package com.xabber.xmpp.core.module

import com.xabber.stream.StreamState
import com.xabber.utils.getArchivedMessageContainer
import com.xabber.utils.getCarbonCopyMessageContainer
import com.xabber.utils.getCarbonForwardedMessageContainer
import com.xabber.utils.isCarbonCopy
import com.xabber.utils.isCarbonForwarded
import com.xabber.xmpp.XEP_0CCC.ClientSynchronizationManager
import com.xabber.xmpp.avatar.XmppAvatarManager
import com.xabber.xmpp.groupchat.GroupchatManager
import com.xabber.xmpp.messages.XMPPMessage
import com.xabber.xmpp.messages.message_archive.MessageArchiveManager
import com.xabber.xmpp.messages.messages_manager.ChatMarkersManager
import com.xabber.xmpp.messages.messages_manager.MessageCommonReceiver
import com.xabber.xmpp.presence.PresenceManager
import com.xabber.xmpp.roster.RosterManager

class MamIqModule(
    owner: String,
    private val messageArchiveManager: MessageArchiveManager,
) : AbstractXmppModule(owner), IqModule {
    override fun namespaces(): List<String> = listOf("urn:xmpp:mam:2")

    override suspend fun handle(stanza: com.xabber.xmpp.core.model.IqStanza, context: XmppModuleContext): Boolean {
        return if (stanza.value.queryNamespace == "urn:xmpp:mam:2") {
            messageArchiveManager.read(stanza.raw, context.stream)
        } else {
            false
        }
    }
}

class RosterIqModule(
    owner: String,
    private val rosterManager: RosterManager,
) : AbstractXmppModule(owner), IqModule {
    override fun namespaces(): List<String> = listOf("jabber:iq:roster")

    override suspend fun handle(stanza: com.xabber.xmpp.core.model.IqStanza, context: XmppModuleContext): Boolean {
        val iq = stanza.value
        return iq.type == "result" && iq.queryNamespace == "jabber:iq:roster" && rosterManager.read(iq)
    }
}

class SyncIqModule(
    owner: String,
    private val jid: String,
    private val ackWriter: suspend (String) -> Boolean,
    private val emitSyncStanza: suspend (String, com.xabber.stream.Stream) -> Unit,
) : AbstractXmppModule(owner), IqModule {
    override fun namespaces(): List<String> = listOf("https://xabber.com/protocol/synchronization")

    override suspend fun handle(stanza: com.xabber.xmpp.core.model.IqStanza, context: XmppModuleContext): Boolean {
        val iq = stanza.value
        if (iq.queryNamespace != "https://xabber.com/protocol/synchronization") return false
        if (iq.type == "set") {
            val ackId = iq.id ?: ""
            val ackFrom = iq.to ?: jid
            val ackTo = iq.from ?: jid
            ackWriter("<iq type='result' id='$ackId' from='$ackFrom' to='$ackTo'/>")
        }
        emitSyncStanza(iq.raw, context.stream)
        return true
    }
}

class GroupchatIqModule(
    owner: String,
    private val jid: String,
    private val groupchatManager: GroupchatManager,
    private val ackWriter: suspend (String) -> Boolean,
) : AbstractXmppModule(owner), IqModule {
    override fun namespaces(): List<String> = listOf("https://xabber.com/protocol/groups")

    override suspend fun handle(stanza: com.xabber.xmpp.core.model.IqStanza, context: XmppModuleContext): Boolean {
        val iq = stanza.value
        if (!iq.raw.contains("https://xabber.com/protocol/groups")) return false
        if (iq.type == "set") {
            val ackId = iq.id ?: ""
            val ackFrom = iq.to ?: jid
            val ackTo = iq.from ?: jid
            ackWriter("<iq type='result' id='$ackId' from='$ackFrom' to='$ackTo'/>")
        }
        return groupchatManager.read(iq.raw)
    }
}

class AvatarIqModule(
    owner: String,
    private val avatarManager: XmppAvatarManager,
) : AbstractXmppModule(owner), IqModule {
    override fun namespaces(): List<String> = listOf("urn:xmpp:avatar:data")

    override suspend fun handle(stanza: com.xabber.xmpp.core.model.IqStanza, context: XmppModuleContext): Boolean {
        val iq = stanza.value
        return iq.type == "result" && iq.raw.contains("urn:xmpp:avatar:data") && avatarManager.read(iq.raw)
    }
}

class BufferedPresenceModule(
    owner: String,
    private val emitPresence: suspend (String, com.xabber.stream.Stream) -> Unit,
    private val presenceManager: () -> PresenceManager?,
    private val groupchatManager: () -> GroupchatManager?,
) : AbstractXmppModule(owner), PresenceModule {
    override fun namespaces(): List<String> = listOf("presence")

    override suspend fun handle(stanza: com.xabber.xmpp.core.model.PresenceStanza, context: XmppModuleContext): Boolean {
        if (context.stream.state == StreamState.CONNECTED || context.stream.state == StreamState.BINDING) {
            emitPresence(stanza.raw, context.stream)
            return true
        }
        if (stanza.raw.contains("https://xabber.com/protocol/groups")) {
            return groupchatManager()?.handlePresence(stanza.raw) ?: false
        }
        return presenceManager()?.processPresence(stanza.raw) ?: false
    }
}

class MessageRoutingModule(
    owner: String,
    private val chatMarkersManager: ChatMarkersManager?,
    private val avatarManager: XmppAvatarManager?,
    private val messageReceiver: MessageCommonReceiver?,
    private val groupchatManager: GroupchatManager?,
) : AbstractXmppModule(owner), MessageModule {
    override fun namespaces(): List<String> = listOf("message")

    override suspend fun handle(stanza: com.xabber.xmpp.core.model.MessageStanza, context: XmppModuleContext): Boolean {
        val message = stanza.value
        chatMarkersManager?.read(message)
        if (avatarManager?.readMessage(message) == true) {
            return true
        }

        val isMamResult = message.hasElement("result", "urn:xmpp:mam:2") ||
            message.raw.contains("""<result\b[^>]*xmlns\s*=\s*["']urn:xmpp:mam:2["']""".toRegex(RegexOption.IGNORE_CASE))
        val isGroupChatLive = message.raw.contains("type='headline'") &&
            message.raw.contains("https://xabber.com/protocol/groups")
        val isMamTmp = !isGroupChatLive && (
            message.hasElement("archived", "urn:xmpp:mam:tmp") ||
                message.raw.contains("""<archived\b[^>]*xmlns\s*=\s*["']urn:xmpp:mam:tmp["']""".toRegex(RegexOption.IGNORE_CASE))
            )
        val isCarbon = message.isCarbonCopy() || message.isCarbonForwarded()
        val isClientSyncLast = message.hasElement("last-message", "https://xabber.com/protocol/synchronization")

        val payload: XMPPMessage = when {
            isCarbon -> message.getCarbonCopyMessageContainer()
                ?: message.getCarbonForwardedMessageContainer()
                ?: message
            isMamResult -> message.getArchivedMessageContainer() ?: message
            isMamTmp -> message
            else -> message
        }

        if (payload.body.isNullOrBlank() && payload.children.isEmpty()) {
            return true
        }

        when {
            isCarbon -> messageReceiver?.receiveCarbon(message)
            isMamResult || isMamTmp -> messageReceiver?.receiveArchived(payload)
            isClientSyncLast -> messageReceiver?.receiveClientSyncRaw(payload)
            else -> messageReceiver?.receiveRuntime(payload)
        }

        if (payload.hasElement("x", "https://xabber.com/protocol/groups")) {
            groupchatManager?.handleMessage(payload)
        }
        return true
    }
}
