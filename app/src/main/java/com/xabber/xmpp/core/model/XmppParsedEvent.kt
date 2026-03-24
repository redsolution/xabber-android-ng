package com.xabber.xmpp.core.model

import com.xabber.stream.serializers.XMPPIQ
import com.xabber.xmpp.messages.XMPPMessage

sealed interface XmppParsedEvent {
    val raw: String
}

data class StreamOpenEvent(
    override val raw: String,
) : XmppParsedEvent

data class StreamFeaturesEvent(
    override val raw: String,
    val features: XmppStreamFeatures,
) : XmppParsedEvent

data class StreamErrorEvent(
    override val raw: String,
) : XmppParsedEvent

data class StreamClosedEvent(
    override val raw: String = "</stream:stream>",
) : XmppParsedEvent

data class IqStanza(
    val value: XMPPIQ,
) : XmppParsedEvent {
    override val raw: String = value.raw
}

data class MessageStanza(
    val value: XMPPMessage,
) : XmppParsedEvent {
    override val raw: String = value.raw
}

data class PresenceStanza(
    override val raw: String,
) : XmppParsedEvent

data class SaslChallengeEvent(
    override val raw: String,
) : XmppParsedEvent

data class SaslSuccessEvent(
    override val raw: String,
) : XmppParsedEvent

data class SaslFailureEvent(
    override val raw: String,
) : XmppParsedEvent

data class ProceedTlsEvent(
    override val raw: String,
) : XmppParsedEvent
