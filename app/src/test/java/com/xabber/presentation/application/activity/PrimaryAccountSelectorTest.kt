package com.xabber.presentation.application.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrimaryAccountSelectorTest {

    @Test
    fun `selectPrimaryAccountId returns null when there are no valid accounts`() {
        assertNull(PrimaryAccountSelector.selectPrimaryAccountId(emptyList()))
        assertNull(PrimaryAccountSelector.selectPrimaryAccountId(listOf("" to 1, "   " to 2)))
    }

    @Test
    fun `selectPrimaryAccountId returns account with smallest order`() {
        val selected = PrimaryAccountSelector.selectPrimaryAccountId(
            listOf("third" to 3, "first" to 1, "second" to 2)
        )

        assertEquals("first", selected)
    }
}
