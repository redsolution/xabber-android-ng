package com.xabber.presentation.application.fragments.chatlist

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatListPreviewFormatterTest {

    @Test
    fun `format strips basic html and decodes common entities`() {
        assertEquals(
            "Hello & welcome\nteam",
            ChatListPreviewFormatter.format("<b>Hello &amp; welcome</b><br>team")
        )
    }
}
