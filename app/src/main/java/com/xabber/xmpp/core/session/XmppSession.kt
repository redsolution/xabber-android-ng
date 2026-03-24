package com.xabber.xmpp.core.session

import com.xabber.xmpp.core.model.XmppEndpoint
import com.xabber.xmpp.core.model.XmppParsedEvent
import com.xabber.xmpp.core.transport.TransportEvent
import com.xabber.xmpp.core.transport.XmppTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class XmppSession(
    private val transport: XmppTransport,
    private val parser: com.xabber.xmpp.core.parser.XmppParser,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val _state = MutableStateFlow<XmppSessionState>(XmppSessionState.Idle)
    private val _events = MutableSharedFlow<XmppParsedEvent>(extraBufferCapacity = 128)

    val state = _state.asStateFlow()
    val events = _events.asSharedFlow()

    init {
        scope.launch {
            transport.events.collect { event ->
                when (event) {
                    is TransportEvent.RawChunkReceived -> {
                        parser.parseChunk(event.chunk).forEach { _events.emit(it) }
                    }
                    is TransportEvent.Failed -> _state.value = XmppSessionState.Failed(event.reason)
                    TransportEvent.Closed -> _state.value = XmppSessionState.Idle
                }
            }
        }
    }

    suspend fun connect(endpoint: XmppEndpoint, alternateEndpoints: List<XmppEndpoint> = emptyList()): Boolean {
        _state.value = XmppSessionState.Connecting
        return transport.connect(endpoint, alternateEndpoints)
    }

    suspend fun sendRaw(xml: String): Boolean = transport.sendRaw(xml)

    fun close() {
        _state.value = XmppSessionState.Disconnecting
        transport.close()
    }
}
