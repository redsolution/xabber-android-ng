package com.xabber.xmpp.core.transport

import com.xabber.xmpp.core.model.XmppEndpoint
import kotlinx.coroutines.flow.Flow

sealed interface TransportEvent {
    data class RawChunkReceived(val chunk: String) : TransportEvent
    data class Failed(val reason: String) : TransportEvent
    data object Closed : TransportEvent
}

interface XmppTransport {
    val events: Flow<TransportEvent>

    suspend fun connect(endpoint: XmppEndpoint, alternateEndpoints: List<XmppEndpoint> = emptyList()): Boolean

    suspend fun sendRaw(xml: String): Boolean

    suspend fun initiateStartTls(): Boolean

    suspend fun upgradeToTls(): Boolean

    suspend fun initiateStream(domain: String, jid: String): Boolean

    fun setDomain(domain: String)

    fun close()
}
