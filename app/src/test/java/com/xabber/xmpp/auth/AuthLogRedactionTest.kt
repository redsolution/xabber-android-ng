package com.xabber.xmpp.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthLogRedactionTest {

    @Test
    fun `redactSecretForLog hides most of long values`() {
        assertEquals("abcd***", redactSecretForLog("abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun `redactSecretForLog handles empty and short values`() {
        assertEquals("<empty>", redactSecretForLog(""))
        assertEquals("***", redactSecretForLog("abc"))
    }
}
