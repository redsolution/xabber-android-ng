package com.xabber.xmpp.core.transport

import com.xabber.stream.Socket
import com.xabber.xmpp.core.model.XmppEndpoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class KtorXmppTransport(
    private val socket: Socket,
) : XmppTransport {
    private val _events = MutableSharedFlow<TransportEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val events: Flow<TransportEvent> = _events.asSharedFlow()

    init {
        socket.setMessageCallback { chunk ->
            _events.tryEmit(TransportEvent.RawChunkReceived(chunk))
        }
        socket.setOnReadLoopError {
            _events.tryEmit(TransportEvent.Failed("Socket read loop failed"))
        }
    }

    override suspend fun connect(endpoint: XmppEndpoint, alternateEndpoints: List<XmppEndpoint>): Boolean {
        return socket.connect(
            endpoint.host,
            endpoint.port,
            alternateEndpoints = alternateEndpoints.map { it.host to it.port },
        )
    }

    override suspend fun sendRaw(xml: String): Boolean = socket.write(xml)

    override suspend fun initiateStartTls(): Boolean = socket.initiateStartTls()

    override suspend fun upgradeToTls(): Boolean = socket.upgradeToTls()

    override suspend fun initiateStream(domain: String, jid: String): Boolean {
        socket.initiateXmppStream(socket, domain, jid)
        return true
    }

    override fun setDomain(domain: String) {
        socket.setDomain(domain)
    }

    override fun close() {
        socket.scope.launch {
            socket.close()
        }
        _events.tryEmit(TransportEvent.Closed)
    }
}
