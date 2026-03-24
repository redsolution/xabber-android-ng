package com.xabber.xmpp.core.model

data class XmppStreamFeatures(
    val mechanisms: List<String> = emptyList(),
    val startTlsSupported: Boolean = false,
    val startTlsRequired: Boolean = false,
    val proxySupported: Boolean = false,
    val devicesSupported: Boolean = false,
    val bindSupported: Boolean = false,
    val synchronizationSupported: Boolean = false,
)
