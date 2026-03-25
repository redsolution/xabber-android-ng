package com.xabber.presentation.application.fragments.chatlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListAvatarPlaceholderTest {

    @Test
    fun `initialsForJid uses first non blank character`() {
        assertEquals("A", ChatListAvatarPlaceholder.initialsForJid("aleksey@example.com"))
        assertEquals("?", ChatListAvatarPlaceholder.initialsForJid("   "))
    }

    @Test
    fun `backgroundColorForJid is deterministic`() {
        val first = ChatListAvatarPlaceholder.backgroundColorForJid("aleksey@example.com")
        val second = ChatListAvatarPlaceholder.backgroundColorForJid("aleksey@example.com")
        assertEquals(first, second)
        assertTrue(first != 0)
    }
}
