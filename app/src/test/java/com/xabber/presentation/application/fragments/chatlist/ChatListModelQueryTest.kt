package com.xabber.presentation.application.fragments.chatlist

import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.dto.ChatListDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatListModelQueryTest {

    @Test
    fun `buildPresenceQuery returns null when there are no direct chats`() {
        val query = buildPresenceQueryForChats(
            listOf(
                chat(isGroup = true, owner = "a@example.com", opponent = "group@example.com")
            )
        )

        assertNull(query)
    }

    @Test
    fun `buildPresenceQuery scopes presences to unique direct chat pairs`() {
        val query = buildPresenceQueryForChats(
            listOf(
                chat(owner = "a@example.com", opponent = "one@example.com"),
                chat(owner = "a@example.com", opponent = "one@example.com"),
                chat(owner = "b@example.com", opponent = "two@example.com"),
                chat(isGroup = true, owner = "a@example.com", opponent = "group@example.com"),
            )
        )

        assertEquals(
            "((owner = 'a@example.com' AND jid = 'one@example.com') OR (owner = 'b@example.com' AND jid = 'two@example.com'))",
            query
        )
    }

    private fun chat(
        owner: String,
        opponent: String,
        isGroup: Boolean = false,
    ): ChatListDto {
        return ChatListDto(
            id = "$owner|$opponent",
            owner = owner,
            opponentJid = opponent,
            opponentNickname = opponent,
            lastMessageState = MessageSendingState.None,
            status = ResourceStatus.OFFLINE,
            entity = RosterItemEntity.CONTACT,
            drawableId = 0,
            colorKey = "blue",
            isGroup = isGroup,
        )
    }
}
