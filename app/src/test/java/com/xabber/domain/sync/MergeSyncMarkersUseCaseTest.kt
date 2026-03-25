package com.xabber.domain.sync

import com.xabber.domain.sync.model.SyncMarkers
import com.xabber.domain.sync.usecase.MergeSyncMarkersUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeSyncMarkersUseCaseTest {

    private val useCase = MergeSyncMarkersUseCase()

    @Test
    fun `incoming markers win when they are newer`() {
        val result = useCase.execute(
            current = SyncMarkers(3, null, "100", "120"),
            incoming = SyncMarkers(0, null, "140", "150"),
            lastMsgTimestampUs = null,
        )
        assertEquals("140", result.displayedId)
        assertEquals("150", result.deliveredId)
        assertEquals(0L, result.unreadCount)
    }

    @Test
    fun `current markers kept when incoming are older`() {
        val result = useCase.execute(
            current = SyncMarkers(2, 7_000_000L, "250", "260"),
            incoming = SyncMarkers(1, null, "200", null),
            lastMsgTimestampUs = null,
        )
        assertEquals("250", result.displayedId)
        assertEquals("260", result.deliveredId)
    }

    @Test
    fun `null current markers treated as empty`() {
        val result = useCase.execute(
            current = null,
            incoming = SyncMarkers(2, null, "100", "120"),
            lastMsgTimestampUs = null,
        )
        assertEquals("100", result.displayedId)
        assertEquals("120", result.deliveredId)
    }

    @Test
    fun `shouldRequestGapFill true when server message is newer by more than tolerance`() {
        assertTrue(useCase.shouldRequestGapFill(
            localLastMessageDateMs = 1_000L,
            serverLastMessageDateMs = 5_000L,
            isGapFixedForSession = false,
        ))
    }

    @Test
    fun `shouldRequestGapFill false when localLastMessageDateMs is zero`() {
        assertFalse(useCase.shouldRequestGapFill(
            localLastMessageDateMs = 0L,
            serverLastMessageDateMs = 5_000L,
            isGapFixedForSession = false,
        ))
    }

    @Test
    fun `shouldRequestGapFill false when difference within tolerance`() {
        assertFalse(useCase.shouldRequestGapFill(
            localLastMessageDateMs = 4_500L,
            serverLastMessageDateMs = 5_000L,
            isGapFixedForSession = false,
        ))
    }

    @Test
    fun `shouldRequestGapFill false when session already fixed`() {
        assertFalse(useCase.shouldRequestGapFill(
            localLastMessageDateMs = 1_000L,
            serverLastMessageDateMs = 5_000L,
            isGapFixedForSession = true,
        ))
    }
}
