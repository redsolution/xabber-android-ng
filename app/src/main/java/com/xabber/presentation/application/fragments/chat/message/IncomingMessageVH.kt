package com.xabber.presentation.application.fragments.chat.message

import android.view.View
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData

class IncomingMessageVH internal constructor(
    itemView: View,
    menuItemListener: MessageAdapter.MenuItemListener?,
    onViewClickListener: MessageAdapter.OnViewClickListener?
) : MessageVH(itemView, menuItemListener, onViewClickListener) {

    override fun bind(message: MessageDto, vhExtraData: MessageVhExtraData) {
        super.bind(message, vhExtraData)
//        val tvName = itemView.findViewById<TextView>(R.id.tv_message_username)
//        tvName.isVisible = vhExtraData.isNeedName && message.isGroup

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

