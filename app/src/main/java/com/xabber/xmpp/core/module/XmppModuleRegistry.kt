package com.xabber.xmpp.core.module

import com.xabber.stream.Stream
import com.xabber.xmpp.core.model.IqStanza
import com.xabber.xmpp.core.model.MessageStanza
import com.xabber.xmpp.core.model.PresenceStanza

data class XmppModuleContext(
    val stream: Stream,
)

fun interface IqModule {
    suspend fun handle(stanza: IqStanza, context: XmppModuleContext): Boolean
}

fun interface MessageModule {
    suspend fun handle(stanza: MessageStanza, context: XmppModuleContext): Boolean
}

fun interface PresenceModule {
    suspend fun handle(stanza: PresenceStanza, context: XmppModuleContext): Boolean
}

class XmppModuleRegistry {
    private val iqModules = mutableListOf<Pair<Int, IqModule>>()
    private val messageModules = mutableListOf<Pair<Int, MessageModule>>()
    private val presenceModules = mutableListOf<Pair<Int, PresenceModule>>()

    fun registerIqModule(priority: Int = 0, module: IqModule) {
        iqModules += priority to module
        iqModules.sortByDescending { it.first }
    }

    fun registerMessageModule(priority: Int = 0, module: MessageModule) {
        messageModules += priority to module
        messageModules.sortByDescending { it.first }
    }

    fun registerPresenceModule(priority: Int = 0, module: PresenceModule) {
        presenceModules += priority to module
        presenceModules.sortByDescending { it.first }
    }

    suspend fun dispatch(stanza: IqStanza, context: XmppModuleContext): Boolean {
        return iqModules.any { (_, module) -> module.handle(stanza, context) }
    }

    suspend fun dispatch(stanza: MessageStanza, context: XmppModuleContext): Boolean {
        return messageModules.any { (_, module) -> module.handle(stanza, context) }
    }

    suspend fun dispatch(stanza: PresenceStanza, context: XmppModuleContext): Boolean {
        return presenceModules.any { (_, module) -> module.handle(stanza, context) }
    }
}
