package com.xabber.xmpp.auth

internal fun redactSecretForLog(value: String, keep: Int = 4): String {
    if (value.isEmpty()) return "<empty>"
    if (value.length <= keep) return "*".repeat(value.length.coerceAtLeast(1))
    return "${value.take(keep)}***"
}
