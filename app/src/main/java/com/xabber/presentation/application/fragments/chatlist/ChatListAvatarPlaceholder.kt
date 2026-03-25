package com.xabber.presentation.application.fragments.chatlist

object ChatListAvatarPlaceholder {
    private val palette = intArrayOf(
        0xFF3F8AE0.toInt(),
        0xFF45B7D1.toInt(),
        0xFF5BB974.toInt(),
        0xFFF29F05.toInt(),
        0xFFE56B6F.toInt()
    )

    fun initialsForJid(jid: String): String {
        val seed = jid.trim()
        if (seed.isEmpty()) return "?"
        return seed.first().uppercase()
    }

    fun backgroundColorForJid(jid: String): Int {
        val seed = jid.trim()
        if (seed.isEmpty()) return palette.first()
        val index = (seed.hashCode() and Int.MAX_VALUE) % palette.size
        return palette[index]
    }
}
