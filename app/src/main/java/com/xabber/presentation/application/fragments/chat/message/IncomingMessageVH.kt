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
    private val messageContainer: LinearLayout? = itemView.findViewById(R.id.message_container)

    override fun bind(message: MessageStorageItem, vhExtraData: MessageVhExtraData) {
        super.bind(message, vhExtraData)
        if (tvName != null) {
            // Re-attach tvName as first child since removeAllViews() detaches it
            if (tvName.parent == null) {
                messageContainer?.addView(tvName, 0)
            }
            tvName.isVisible = vhExtraData.isNeedName && vhExtraData.isGroup
            if (tvName.isVisible) {
                val nickname = message.groupchatDisplayedNickname ?: ""
                tvName.text = nickname
                if (nickname.isNotEmpty()) {
                    val colorRes = if (vhExtraData.nicknameColorResId != 0) {
                        vhExtraData.nicknameColorResId
                    } else {
                        ColorManager.colorForGroupchatUser(nickname)
                    }
                    tvName.setTextColor(ContextCompat.getColor(itemView.context, colorRes))
                }
            }
        }
    }

}