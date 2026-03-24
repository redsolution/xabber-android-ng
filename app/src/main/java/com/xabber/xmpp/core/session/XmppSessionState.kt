package com.xabber.xmpp.core.session

sealed interface XmppSessionState {
    data object Idle : XmppSessionState
    data object Resolving : XmppSessionState
    data object Connecting : XmppSessionState
    data object StreamStarted : XmppSessionState
    data object AwaitingFeatures : XmppSessionState
    data object TlsNegotiating : XmppSessionState
    data object Authenticating : XmppSessionState
    data object Binding : XmppSessionState
    data object Online : XmppSessionState
    data object Disconnecting : XmppSessionState
    data class Failed(val reason: String) : XmppSessionState
}
