package com.xabber.presentation.application.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApplicationUnreadQueryBuilderTest {

    @Test
    fun `build returns null when account list is empty`() {
        assertNull(ApplicationUnreadQueryBuilder.build(emptyList()))
        assertNull(ApplicationUnreadQueryBuilder.build(listOf("", "   ")))
    }

    @Test
    fun `build creates unread query for distinct non blank account ids`() {
        val query = ApplicationUnreadQueryBuilder.build(
            listOf(" owner-1 ", "owner-2", "owner-1", "")
        )

        assertEquals(
            "owner IN {'owner-1','owner-2'} && isArchived = false && muteExpired <= 0 && unread > 0",
            query
        )
    }
}
