package com.xabber.common

import kotlinx.serialization.Serializable

@Serializable
data class XMPPIQ(
    val raw: String,  // Full original <iq>...</iq> stanza
    val type: String,  // e.g., "get", "set", "result", "error"
    val id: String?,   // Optional id attribute
    val from: String?, // Optional from attribute
    val to: String?,   // Optional to attribute
    val error: String?, // Full <error>...</error> if type="error", else null
    val queryNamespace: String?,  // xmlns of the first child element (e.g., "jabber:iq:roster")
    val queryContent: String?     // Inner content of <iq> (e.g., "<query>...</query>")
)

