package com.xabber.common

import kotlinx.serialization.Serializable

@Serializable
data class XMPPPresence(
    val raw: String,
    val type: String? = null,
    val from: String? = null,
    val to: String? = null,
    val id: String? = null, // Added to store presence ID
    val show: String? = null,  // "away", "chat", etc.
    val status: String? = null,
    val priority: Int? = null,
    val deviceId: String? = null,
    val timestamp: Long = 0
)
