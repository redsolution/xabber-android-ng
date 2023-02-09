package com.xabber.presentation.application.fragments.test

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.StyleRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.models.dto.MessageDto
import com.xabber.models.dto.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.message.XMessageVH
import com.xabber.utils.StringUtils
import com.xabber.utils.dipToPx
import java.util.*

class XIncomingMessageVH internal constructor(private val listener: MessageAdapter.Listener,
                                              itemView: View, messageListener: MessageClickListener?,
                                              longClickListener: MessageLongClickListener?, fileListener: FileListener?,
                                              val listen: BindListener?, avatarClickListener: OnMessageAvatarClickListener,
                                              @StyleRes appearance: Int
) : XMessageVH(itemView, messageListener!!, longClickListener!!, fileListener, appearance) {

    interface BindListener {
        fun onBind(message: MessageDto?)
    }

    interface OnMessageAvatarClickListener {
        fun onMessageAvatarClick(position: Int)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun bind(messageRealmObject: MessageDto, extraData: MessageVhExtraData) {
        super.bind(messageRealmObject, extraData)
        val context: Context = itemView.getContext()
        val needTail: Boolean = extraData.isNeedTail

        if (messageRealmObject.isChecked) itemView.setBackgroundResource(R.color.selected) else itemView.setBackgroundResource(R.color.transparent)
        statusIcon.isVisible = false
        bottomStatusIcon.isVisible = false
        val avatar = itemView.findViewById<ImageView>(R.id.avatar)
        avatar.isVisible = false
        // text & appearance
        messageTextTv.text = messageRealmObject.messageBody

        // time
        val date = Date(messageRealmObject.sentTimestamp)
        val time = StringUtils.getTimeText(messageTime.context, date)
        messageTime.text = time

        // setup BACKGROUND
        val balloonDrawable = ContextCompat.getDrawable(
            context,
            if (needTail) R.drawable.msg_in else R.drawable.msg
        )
        val shadowDrawable = ContextCompat.getDrawable(
            context,
            if (needTail) R.drawable.msg_in_shadow else R.drawable.msg_shadow
        )
        shadowDrawable?.setColorFilter(
            ContextCompat.getColor(context, R.color.black),
            PorterDuff.Mode.MULTIPLY
        )

        balloonDrawable?.setColorFilter(
            itemView.resources.getColor(
                R.color.blue_100,
                itemView.context.theme
            ), PorterDuff.Mode.MULTIPLY
        )
        messageBalloon.background = balloonDrawable
        messageShadow.background = shadowDrawable

        // setup BALLOON margins
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(
            dipToPx(if (needTail) 3f else 11f, context),
            dipToPx(3f, context),
            dipToPx(0f, context),
            dipToPx(3f, context)
        )
        messageShadow.layoutParams = layoutParams

        // setup MESSAGE padding
        messageBalloon.setPadding(
            dipToPx(if (needTail) 20f else 12f, context),
            dipToPx(8f, context),
            dipToPx(8f, context),
            dipToPx(8f, context)
        )

        //   setUpAvatar(context, extraData.groupMember, messageRealmObject, needTail)

        // hide empty message
//        if (messageRealmObject.messageBody.trim().isEmpty()
//            && !messageRealmObject.hasForwardedMessages()
//            && !messageRealmObject.hasReferences()
//        ) {
//            getMessageBalloon().setVisibility(View.GONE)
//            getMessageShadow().setVisibility(View.GONE)
//            getMessageTime().setVisibility(View.GONE)
//            getBottomMessageTime().setVisibility(View.GONE)
//            avatar.visibility = View.GONE
//            LogManager.w(this, "Empty message! Hidden, but need to correct")
//        } else {
//            getMessageBalloon().setVisibility(View.VISIBLE)
//            getMessageTime().setVisibility(View.VISIBLE)
//            getBottomMessageTime().setVisibility(View.VISIBLE)
//        }
        itemView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {

            override fun onViewAttachedToWindow(view: View) {
               // Log.d("bibi", "onAttached ${messageRealmObject.messageBody}")
              if (messageRealmObject.isUnread)
                listen?.onBind(messageRealmObject)
            }

            override fun onViewDetachedFromWindow(v: View) {
                unsubscribeAll()
            }
        })
        if (messageTextTv.getText().toString().trim().isEmpty()) {
            messageTextTv.setVisibility(View.GONE)
        }


//    private fun setUpAvatar(
//        context: Context, groupMember: GroupMemberRealmObject?,
//        messageRealmObject: MessageRealmObject, needTail: Boolean
//    ) {
//        var needAvatar: Boolean = SettingsManager.chatsShowAvatars()
//        // for new groupchats (0GGG)
//        if (groupMember != null) {
//            needAvatar = true
//        }
//        if (!needAvatar) {
//            avatar.visibility = View.GONE
//            return
//        }
//        if (!needTail) {
//            avatar.visibility = View.INVISIBLE
//            return
//        }
//        avatar.visibility = View.VISIBLE
//
//        //groupchat avatar
//        if (groupMember != null) {
//            val placeholder: Drawable
//            placeholder = try {
//                val contactJid: ContactJid = ContactJid.from(
//                    messageRealmObject.getUser().getJid().toString()
//                            + "/"
//                            + groupMember.getNickname()
//                )
//                AvatarManager.getInstance().getOccupantAvatar(
//                    contactJid, groupMember.getNickname()
//                )
//            } catch (e: ContactJid.ContactJidCreateException) {
//                AvatarManager.getInstance().generateDefaultAvatar(
//                    groupMember.getNickname(), groupMember.getNickname()
//                )
//            }
//            Glide.with(context)
//                .load(
//                    AvatarManager.getInstance().getGroupMemberAvatar(
//                        groupMember, messageRealmObject.getAccount()
//                    )
//                )
//                .centerCrop()
//                .placeholder(placeholder)
//                .error(placeholder)
//                .into(avatar)
//            return
//        }
//        val user: ContactJid = messageRealmObject.getUser()
//        val resource: Resourcepart = messageRealmObject.getResource()
//        if (resource.equals(Resourcepart.EMPTY)) {
//            avatar.setImageDrawable(AvatarManager.getInstance().getRoomAvatarForContactList(user))
//        } else {
//            val nick: String = resource.toString()
//            val contactJid: ContactJid
//            try {
//                contactJid = ContactJid.from(user.getJid().toString() + "/" + resource.toString())
//                avatar.setImageDrawable(
//                    AvatarManager.getInstance().getOccupantAvatar(contactJid, nick)
//                )
//            } catch (e: ContactJid.ContactJidCreateException) {
//                LogManager.exception(this, e)
//                avatar.setImageDrawable(
//                    AvatarManager.getInstance().generateDefaultAvatar(nick, nick)
//                )
//            }
//        }
//    }
        messageTextTv.setOnClickListener {
            if (Check.getSelectedMode()) {
                listener.checkItem(!messageRealmObject.isChecked, messageRealmObject.primary)
            } else {
                val popup = PopupMenu(it.context, it, Gravity.CENTER)
                popup.setForceShowIcon(true)
                popup.inflate(R.menu.popup_menu_message_incoming)


                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.copy -> {
                            val text = messageTextTv.text.toString()
                            listener.copyText(text)
                        }
                        R.id.pin -> {
                            listener.pinMessage(messageRealmObject)
                        }
                        R.id.forward -> {
                            listener.forwardMessage(messageRealmObject)
                        }
                        R.id.reply -> {
                            listener.replyMessage(messageRealmObject)
                        }
                        R.id.delete_message -> {
                            listener.deleteMessage(messageRealmObject.primary)
                        }
                    }
                    true
                }
                popup.show()
            }
        }

        itemView.setOnClickListener {
            if (Check.getSelectedMode()) {
                listener.checkItem(!messageRealmObject.isChecked, messageRealmObject.primary)
            } else {
                val popup = PopupMenu(messageTextTv.context, messageTextTv, Gravity.CENTER)
                popup.setForceShowIcon(true)
                popup.inflate(R.menu.popup_menu_message_incoming)


                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.copy -> {
                            val text = messageTextTv.text.toString()
                            listener.copyText(text)
                        }
                        R.id.pin -> {
                            listener.pinMessage(messageRealmObject)
                        }
                        R.id.forward -> {
                            listener.forwardMessage(messageRealmObject)
                        }
                        R.id.reply -> {
                            listener.replyMessage(messageRealmObject)
                        }
                        R.id.delete_message -> {
                            listener.deleteMessage(messageRealmObject.primary)
                        }
                    }
                    true
                }
                popup.show()
            }
        }

        itemView.setOnLongClickListener {
            if (!Check.getSelectedMode()) listener.onLongClick(messageRealmObject.primary)
            else {
                listener.checkItem(!messageRealmObject.isChecked, messageRealmObject.primary)
            }
            true
        }
    }





}
