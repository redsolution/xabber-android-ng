package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.model.dto.MessageDto
import com.xabber.model.dto.MessageVhExtraData
import io.realm.RealmResults

internal class ForwardedAdapter(private val realmResults: RealmResults<MessageRealmObject?>,
                                private val vhExtraData: MessageVhExtraData
) : RealmRecyclerViewAdapter<MessageDto?, ForwardedVH?>(realmResults, true, true),
    MessageVH.MessageClickListener, MessageVH.MessageLongClickListener {

    private val appearanceStyle = SettingsManager.chatsAppearanceStyle()
    private val listener: MessageVH.FileListener? = vhExtraData.listener
    private val fwdListener: ForwardListener? = vhExtraData.fwdListener

    interface ForwardListener {
        fun onForwardClick(messageId: String?)
    }

    override fun getItemCount() =
        if (realmResults.isValid && realmResults.isLoaded) realmResults.size else 0

    fun getMessageItem(position: Int): MessageDto =
        if (position < realmResults.size && position != RecyclerView.NO_POSITION) {
            realmResults[position]
        } else {
            null
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForwardedVH {
        return ForwardedVH(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_message, parent, false
            ),
            this, this, listener, appearanceStyle
        )
    }

    override fun onBindViewHolder(holder: ForwardedVH, position: Int) {
        val messageRealmObject = getMessageItem(position)
        if (messageRealmObject == null) {
            LogManager.w(this, "onBindViewHolder Null message item. Position: $position")
            return
        }

        // setup message uniqueId
        holder.messageId = messageRealmObject.primaryKey

        val extraData = MessageVhExtraData(
            null,
            null,
            colors = vhExtraData.colors,
            groupMember = messageRealmObject.groupchatUserId?.let {
                getGroupMemberById(messageRealmObject.account, messageRealmObject.user, it)
            },
            mainMessageTimestamp = vhExtraData.mainMessageTimestamp,
            isUnread = false,
            isChecked = false,
            isNeedTail = false,
            isNeedDate = true,
            isNeedName = true
        )
        holder.bind(messageRealmObject, extraData)
    }

    override fun onMessageClick(caller: View, position: Int) {
        getItem(position)?.let { message ->
            if (message.hasForwardedMessages()) {
                fwdListener?.onForwardClick(message.primaryKey)
            }
        }
    }

    override fun onLongMessageClick(position: Int) {}

}