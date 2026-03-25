package com.xabber.xmpp.presence

import com.xabber.data_base.models.presences.ResourceStorageItem
import com.xabber.data_base.models.roster.RosterStorageItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceManagerBatchKeysTest {

    @Test
    fun `buildRosterPrimaryKeysForBatch deduplicates contact bare jids`() {
        val keys = PresenceManager.buildRosterPrimaryKeysForBatch(
            owner = "owner@example.com",
            presences = listOf(
                ParsedPresence(type = null, from = "one@example.com/mobile", show = null, status = null, priority = null),
                ParsedPresence(type = "unavailable", from = "one@example.com/web", show = null, status = null, priority = null),
                ParsedPresence(type = null, from = "two@example.com", show = null, status = null, priority = null),
            )
        )

        assertEquals(
            setOf(
                RosterStorageItem.genPrimary("one@example.com", "owner@example.com"),
                RosterStorageItem.genPrimary("two@example.com", "owner@example.com"),
            ),
            keys
        )
    }

    @Test
    fun `buildResourcePrimaryKeysForBatch keeps distinct full resources`() {
        val keys = PresenceManager.buildResourcePrimaryKeysForBatch(
            owner = "owner@example.com",
            presences = listOf(
                ParsedPresence(type = null, from = "one@example.com/mobile", show = null, status = null, priority = null),
                ParsedPresence(type = null, from = "one@example.com/web", show = null, status = null, priority = null),
                ParsedPresence(type = null, from = "two@example.com", show = null, status = null, priority = null),
            )
        )

        assertEquals(
            setOf(
                ResourceStorageItem.genPrimary("one@example.com", "owner@example.com", "mobile"),
                ResourceStorageItem.genPrimary("one@example.com", "owner@example.com", "web"),
                ResourceStorageItem.genPrimary("two@example.com", "owner@example.com", ""),
            ),
            keys
        )
    }
}
