package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.presentation.application.fragments.chat.MessageAdapter
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData
import com.xabber.presentation.application.manage.ColorManager

class IncomingMessageVH(
    itemView: View, inflater: LayoutInflater,
    menuItemListener: MessageAdapter.MenuItemListener?,
    onViewClickListener: MessageAdapter.OnViewClickListener?
) : MessageViewHolder(itemView, inflater, menuItemListener, onViewClickListener) {

    private val tvName: TextView? = itemView.findViewById(R.id.tv_message_username)

    override fun bind(message: MessageStorageItem, vhExtraData: MessageVhExtraData) {
        super.bind(message, vhExtraData)
        if (tvName != null) {
            tvName.isVisible = vhExtraData.isNeedName && vhExtraData.isGroup
            if (tvName.isVisible) {
                tvName.text = message.groupchatDisplayedNickname ?: ""
                val authorId = message.groupchatAuthorJid ?: message.groupchatAuthorId ?: ""
                if (authorId.isNotEmpty()) {
                    tvName.setTextColor(ContextCompat.getColor(itemView.context, ColorManager.colorForGroupchatUser(authorId)))
                }
            }
        }
    }

}