package com.xabber.domain.sync

import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.domain.sync.model.SyncMarkers
import com.xabber.domain.sync.model.SyncMessage
import com.xabber.domain.sync.usecase.DetermineMessageStateUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetermineMessageStateUseCaseTest {

    private val useCase = DetermineMessageStateUseCase()

    private fun msg(
        timestampUs: Long,
        isOutgoing: Boolean,
        currentState: MessageSendingState? = null,
    ) = SyncMessage(
        id = "1", fromJid = "a@b.com", body = "hi",
        timestampUs = timestampUs, isOutgoing = isOutgoing,
        groupNickname = null, currentState = currentState,
    )

    private fun markers(
        unread: Long = 0,
        unreadAfterUs: Long? = null,
        displayedId: String? = null,
        deliveredId: String? = null,
    ) = SyncMarkers(unread, unreadAfterUs, displayedId, deliveredId)

    @Test
    fun `outgoing message marked Read when timestamp before displayedId`() {
        val result = useCase.execute(msg(100L, isOutgoing = true), markers(displayedId = "200"))
        assertEquals(MessageSendingState.Read, result.state)
        assertTrue(result.isRead)
    }

    @Test
    fun `outgoing message marked Deliver when timestamp before deliveredId only`() {
        val result = useCase.execute(msg(100L, isOutgoing = true), markers(deliveredId = "200"))
        assertEquals(MessageSendingState.Deliver, result.state)
        assertTrue(result.isRead)
    }

    @Test
    fun `outgoing message keeps existing Sent state when no marker covers it`() {
        val result = useCase.execute(
            msg(500L, isOutgoing = true, currentState = MessageSendingState.Sent),
            markers()
        )
        assertEquals(MessageSendingState.Sent, result.state)
    }

    @Test
    fun `outgoing message defaults to Sent when currentState is null and no markers`() {
        val result = useCase.execute(msg(500L, isOutgoing = true, currentState = null), markers())
        assertEquals(MessageSendingState.Sent, result.state)
    }

    @Test
    fun `incoming message read when unreadCount is zero`() {
        val result = useCase.execute(msg(100L, isOutgoing = false), markers(unread = 0))
        assertEquals(MessageSendingState.Read, result.state)
        assertTrue(result.isRead)
    }

    @Test
    fun `incoming message read when timestamp before unreadAfterUs`() {
        val result = useCase.execute(
            msg(5_000L, isOutgoing = false),
            markers(unread = 2, unreadAfterUs = 10_000L)
        )
        assertTrue(result.isRead)
    }

    @Test
    fun `incoming message unread when timestamp after unreadAfterUs`() {
        val result = useCase.execute(
            msg(15_000L, isOutgoing = false),
            markers(unread = 2, unreadAfterUs = 10_000L)
        )
        assertEquals(false, result.isRead)
        assertEquals(MessageSendingState.Deliver, result.state)
    }
}
