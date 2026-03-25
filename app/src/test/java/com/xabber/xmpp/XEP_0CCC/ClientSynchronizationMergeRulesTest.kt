package com.xabber.xmpp.XEP_0CCC

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientSynchronizationMergeRulesTest {

    @Test
    fun `mergeSyncChatMarkers keeps newer marker state even without newer message`() {
        val merged = mergeSyncChatMarkers(
            current = SyncChatMarkersSnapshot(
                unread = 3,
                displayedId = "100",
                deliveredId = "120",
                lastReadMessageDate = 5000L,
            ),
            actualUnread = 0,
            incomingDisplayedId = "140",
            incomingDeliveredId = "150",
            unreadCount = 0L,
            unreadAfterUs = null,
            messageDateMs = 9000L,
        )

        assertEquals(0, merged.unread)
        assertEquals("140", merged.displayedId)
        assertEquals("150", merged.deliveredId)
        assertEquals(9000L, merged.lastReadMessageDate)
    }

    @Test
    fun `mergeSyncChatMarkers keeps existing markers when incoming markers are older or absent`() {
        val merged = mergeSyncChatMarkers(
            current = SyncChatMarkersSnapshot(
                unread = 2,
                displayedId = "250",
                deliveredId = "260",
                lastReadMessageDate = 8000L,
            ),
            actualUnread = 1,
            incomingDisplayedId = "200",
            incomingDeliveredId = null,
            unreadCount = 1L,
            unreadAfterUs = 7_000_000L,
            messageDateMs = 7500L,
        )

        assertEquals(1, merged.unread)
        assertEquals("250", merged.displayedId)
        assertEquals("260", merged.deliveredId)
        assertEquals(8000L, merged.lastReadMessageDate)
    }

    @Test
    fun `shouldRequestGapFill only fires when newer server message exceeds tolerance and session is not fixed`() {
        assertTrue(
            shouldRequestGapFill(
                previousMessageDateMs = 1_000L,
                serverLastMessageDateMs = 5_000L,
                isHistoryGapFixedForSession = false,
            )
        )
        assertFalse(
            shouldRequestGapFill(
                previousMessageDateMs = 0L,
                serverLastMessageDateMs = 5_000L,
                isHistoryGapFixedForSession = false,
            )
        )
        assertFalse(
            shouldRequestGapFill(
                previousMessageDateMs = 4_500L,
                serverLastMessageDateMs = 5_000L,
                isHistoryGapFixedForSession = false,
            )
        )
        assertFalse(
            shouldRequestGapFill(
                previousMessageDateMs = 1_000L,
                serverLastMessageDateMs = 5_000L,
                isHistoryGapFixedForSession = true,
            )
        )
    }
}
