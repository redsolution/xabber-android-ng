package com.xabber.xmpp.core.module

import com.xabber.xmpp.core.model.IqStanza
import com.xabber.stream.serializers.XMPPIQ
import org.junit.Assert.assertEquals
import org.junit.Test

class XmppModuleRegistryTest {

    @Test
    fun `dispatch prefers higher priority iq module`() {
        val registry = XmppModuleRegistry()
        val calls = mutableListOf<String>()

        registry.registerIqModule(priority = 10) { _, _ ->
            calls += "low"
            false
        }
        registry.registerIqModule(priority = 100) { _, _ ->
            calls += "high"
            true
        }

        val handled = kotlinx.coroutines.runBlocking {
            registry.dispatch(
                IqStanza(
                    XMPPIQ(
                        raw = "<iq type='result' id='1'/>",
                        type = "result",
                        id = "1",
                        from = null,
                        to = null,
                        error = null,
                        queryNamespace = null,
                        queryContent = null,
                    )
                ),
                XmppModuleContext(stream = dummyStream()),
            )
        }

        assertEquals(true, handled)
        assertEquals(listOf("high"), calls)
    }

    private fun dummyStream() = com.xabber.stream.Stream("user@example.com")
}
