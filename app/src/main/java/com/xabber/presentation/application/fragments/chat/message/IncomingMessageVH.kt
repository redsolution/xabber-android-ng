package com.xabber.presentation.application.fragments.chat.message

import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.widget.TextView
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData

class IncomingMessageVH internal constructor(
    itemView: View,
    private val listener: MessageAdapter.MenuItemListener?,
    onViewClickListener: MessageAdapter.OnViewClickListener?,
    val listen: BindListener?, avatarClickListener: OnMessageAvatarClickListener
) : MessageVH(itemView, listener, onViewClickListener) {

    interface BindListener {
        fun onBind(message: MessageDto?)
    }

    interface OnMessageAvatarClickListener {
        fun onMessageAvatarClick(position: Int)
    }

    override fun bind(message: MessageDto, vhExtraData: MessageVhExtraData) {
        super.bind(message, vhExtraData)
        val tvName = itemView.findViewById<TextView>(R.id.tv_message_username)
        tvName.isVisible = vhExtraData.isNeedName

//
//        itemView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
//
//            override fun onViewAttachedToWindow(view: View) {
//                if (message.isUnread)
//                    listen?.onBind(message)
//            }
//
//            override fun onViewDetachedFromWindow(v: View) {
//
//            }
//        })

    }
}

