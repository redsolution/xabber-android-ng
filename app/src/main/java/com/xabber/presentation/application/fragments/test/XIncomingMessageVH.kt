package com.xabber.presentation.application.fragments.test

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.view.Gravity
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.StyleRes
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.models.dto.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.MessageChanger
import com.xabber.presentation.application.fragments.chat.message.XMessageVH
import com.xabber.utils.MaskManager
import com.xabber.utils.StringUtils
import com.xabber.utils.dipToPx
import com.xabber.utils.dp
import java.util.*

class XIncomingMessageVH internal constructor(
    private val listener: MessageAdapter.Listener?,
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

        if (messageRealmObject.isChecked) itemView.setBackgroundResource(R.color.selected) else itemView.setBackgroundResource(
            R.color.transparent
        )
        statusIcon.isVisible = false
      //  bottomStatusIcon.isVisible = false
//        val avatar = itemView.findViewById<ImageView>(R.id.avatar)
//        avatar.isVisible = false
        // text & appearance
        messageTextTv.text = messageRealmObject.messageBody

        // time
        val date = Date(messageRealmObject.sentTimestamp)
        val time = StringUtils.getTimeText(messageTime.context, date)
        messageTime.text = time

        // setup BACKGROUND
//        val balloonDrawable = ContextCompat.getDrawable(
//            context, if (needTail) MessageChanger.tail else MessageChanger.simple
//        )
//
//        shadowDrawable?.setColorFilter(
//            ContextCompat.getColor(context, R.color.black),
//            PorterDuff.Mode.MULTIPLY
//        )

//        balloonDrawable?.setColorFilter(
//            itemView.resources.getColor(
//                R.color.blue_100,
//                itemView.context.theme
//            ), PorterDuff.Mode.MULTIPLY
//        )
      //  messageBalloon.background = balloonDrawable
        //    messageShadow.background = shadowDrawable

        // setup BALLOON margins
      //  val im = itemView.findViewById<ImageView>(R.id.im)

        val shadowDrawable = ContextCompat.getDrawable(
            context,
            if (needTail) MessageChanger.tail else MessageChanger.simple
        )
    //    im.background = shadowDrawable
            // im.scaleX = -1f
  //      im.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(im.context, R.color.blue_100))

//        val layoutParams = messageBalloon.layoutParams as ConstraintLayout.LayoutParams
//        layoutParams.setMargins(
//              4.dp,
//            2.dp,
//            0,
//            2.dp
//
//        )
        shadowDrawable?.setColorFilter(
            itemView.resources.getColor(
                R.color.blue_100,
                itemView.context.theme
            ), PorterDuff.Mode.MULTIPLY
        )

     messageBalloon.background = shadowDrawable
    //    im.scaleX = -1f


        // setup MESSAGE padding
        messageBalloon.setPadding(
           16.dp,
           2.dp,
            8.dp,
            2.dp
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
                listener?.checkItem(!messageRealmObject.isChecked, messageRealmObject.primary)
            } else {
                val popup = PopupMenu(it.context, it, Gravity.CENTER)
                popup.setForceShowIcon(true)
                popup.inflate(R.menu.popup_menu_message_incoming)


                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.copy -> {
                            val text = messageTextTv.text.toString()
                            listener?.copyText(text)
                        }
                        R.id.pin -> {
                            listener?.pinMessage(messageRealmObject)
                        }
                        R.id.forward -> {
                            listener?.forwardMessage(messageRealmObject)
                        }
                        R.id.reply -> {
                            listener?.replyMessage(messageRealmObject)
                        }
                        R.id.delete_message -> {
                            listener?.deleteMessage(messageRealmObject.primary)
                        }
                    }
                    true
                }
                popup.show()
            }
        }

        itemView.setOnClickListener {
            if (Check.getSelectedMode()) {
                listener?.checkItem(!messageRealmObject.isChecked, messageRealmObject.primary)
            } else {
                val popup = PopupMenu(messageTextTv.context, messageTextTv, Gravity.CENTER)
                popup.setForceShowIcon(true)
                popup.inflate(R.menu.popup_menu_message_incoming)


                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.copy -> {
                            val text = messageTextTv.text.toString()
                            listener?.copyText(text)
                        }
                        R.id.pin -> {
                            listener?.pinMessage(messageRealmObject)
                        }
                        R.id.forward -> {
                            listener?.forwardMessage(messageRealmObject)
                        }
                        R.id.reply -> {
                            listener?.replyMessage(messageRealmObject)
                        }
                        R.id.delete_message -> {
                            listener?.deleteMessage(messageRealmObject.primary)
                        }
                    }
                    true
                }
                popup.show()
            }
        }

        itemView.setOnLongClickListener {
            if (!Check.getSelectedMode()) listener?.onLongClick(messageRealmObject.primary)
            else {
                listener?.checkItem(!messageRealmObject.isChecked, messageRealmObject.primary)
            }
            true
        }
    }


}
