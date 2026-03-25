package com.xabber.data.sync.mapper

import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.domain.sync.model.ConversationWrite
import com.xabber.domain.sync.model.SyncMessage

object SyncMessageMapper {
    // Returns a new MessageStorageItem (not yet persisted). Call inside realm.write { }.
    fun toStorageItem(
        msg: SyncMessage,
        state: ConversationWrite.MessageUpdate,
        owner: String,
        opponent: String,
        conversationType: String,
    ): MessageStorageItem {
        val primary = MessageStorageItem.genPrimary(msg.id, owner)
        return MessageStorageItem().apply {
            this.primary = primary
            this.messageId = msg.id
            this.owner = owner
            this.opponent = opponent
            this.body = msg.body
            val dateMs = msg.timestampUs / 1000L
            this.date = dateMs
            this.sentDate = dateMs
            this.outgoing = msg.isOutgoing
            this.state = state.state.state
            this.isRead = state.state.isRead
            this.conversationType_ = conversationType

            if (msg.groupNickname != null && !msg.isOutgoing) {
                val ref = MessageReferenceStorageItem().apply {
                    this.kind_ = MessageReferenceStorageItem.Kind.GROUPCHAT.rawValue
                    this.metadata_ = """{"nickname":"${msg.groupNickname.replace("\"", "\\\"")}","id":"${msg.groupNickname.replace("\"", "\\\"")}"}"""
                    this.messagePrimary = primary
                    this.owner = owner
                }
                this.references.add(ref)
            }
        }
    }
}
