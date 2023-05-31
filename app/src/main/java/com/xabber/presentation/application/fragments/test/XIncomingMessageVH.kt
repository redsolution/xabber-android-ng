package com.xabber.presentation.application.fragments.test

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.models.dto.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.MessageChanger
import com.xabber.presentation.application.fragments.chat.message.XMessageVH
import com.xabber.utils.StringUtils
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
    override fun bind(message: MessageDto, extraData: MessageVhExtraData) {
        super.bind(message, extraData)
        val context: Context = itemView.context
        var needTail: Boolean = extraData.isNeedTail

        val balloon = itemView.findViewById<FrameLayout>(R.id.balloon)
        val messageBalloon = itemView.findViewById<LinearLayout>(R.id.message_balloon)
        val tail = itemView.findViewById<FrameLayout>(R.id.tail)

        if (message.hasReferences) needTail = false

        // checked background
        if (message.isChecked) itemView.setBackgroundResource(R.color.selected) else itemView.setBackgroundResource(
            R.color.transparent
        )

        // text
        messageTextTv.text = message.messageBody

        // time
        val date = Date(message.sentTimestamp)
        val time = StringUtils.getTimeText(context, date)
        messageTime.text = if (message.editTimestamp > 0) "edit $time" else time


        // background
        val balloonBackground = ContextCompat.getDrawable(
            context,
            if (needTail) MessageChanger.tail else
                MessageChanger.simple
        )

        val tailBackground = ContextCompat.getDrawable(
            context, MessageChanger.hvost
        )
        tailBackground?.setColorFilter(ContextCompat.getColor(context, R.color.blue_100), PorterDuff.Mode.MULTIPLY)
        balloonBackground?.setColorFilter(ContextCompat.getColor(context, R.color.blue_100), PorterDuff.Mode.MULTIPLY)
        balloon.background = balloonBackground

        tail.background = tailBackground

        if (!MessageChanger.bottom) {
            balloon.scaleY = -1f
            tail.scaleY = -1f
        } else {
            balloon.scaleY = 1f
            tail.scaleY = 1f
        }

        // visible tail
        tail.isInvisible = !needTail || MessageChanger.typeValue == 2


        if (MessageChanger.bottom) {

        } else {
            val layoutParams = tail.layoutParams as RelativeLayout.LayoutParams
            layoutParams.removeRule(RelativeLayout.ALIGN_BOTTOM) // Удаляем правило ALIGN_BOTTOM
            layoutParams.addRule(RelativeLayout.ALIGN_TOP, R.id.message_balloon) // Добавляем правило ALIGN_TOP с нужным id
            tail.layoutParams = layoutParams
        }
       statusIcon.isVisible = false
      //  bottomStatusIcon.isVisible = false
//        val avatar = itemView.findViewById<ImageView>(R.id.avatar)
//        avatar.isVisible = false

        val shadowDrawable = ContextCompat.getDrawable(
            context,
            R.drawable.bubble_1px
        )

        shadowDrawable?.setColorFilter(
            itemView.resources.getColor(
                R.color.blue_100,
                itemView.context.theme
            ), PorterDuff.Mode.MULTIPLY
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
                if (message.isUnread)
                    listen?.onBind(message)
            }

            override fun onViewDetachedFromWindow(v: View) {
                unsubscribeAll()
            }
        })
//        if (messageTextTv.getText().toString().trim().isEmpty()) {
//            messageTextTv.setVisibility(View.GONE)
//        }


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
//        messageTextTv.setOnClickListener {
//            if (Check.getSelectedMode()) {
//                listener?.checkItem(!messageRealmObject.isChecked, messageRealmObject.primary)
//            } else {
//                val popup = PopupMenu(it.context, it, Gravity.CENTER)
//                popup.setForceShowIcon(true)
//                popup.inflate(R.menu.popup_menu_message_incoming)
//
//
//                popup.setOnMenuItemClickListener { menuItem ->
//                    when (menuItem.itemId) {
//                        R.id.copy -> {
//                            val text = messageTextTv.text.toString()
//                            listener?.copyText(text)
//                        }
//                        R.id.pin -> {
//                            listener?.pinMessage(messageRealmObject)
//                        }
//                        R.id.forward -> {
//                            listener?.forwardMessage(messageRealmObject)
//                        }
//                        R.id.reply -> {
//                            listener?.replyMessage(messageRealmObject)
//                        }
//                        R.id.delete_message -> {
//                            listener?.deleteMessage(messageRealmObject.primary)
//                        }
//                    }
//                    true
//                }
//                popup.show()
//            }
//        }

//        itemView.setOnClickListener {
//            if (Check.getSelectedMode()) {
//                listener?.checkItem(!messageRealmObject.isChecked, messageRealmObject.primary)
//            } else {
//                val popup = PopupMenu(messageTextTv.context, messageTextTv, Gravity.CENTER)
//                popup.setForceShowIcon(true)
//                popup.inflate(R.menu.popup_menu_message_incoming)
//
//
//                popup.setOnMenuItemClickListener { menuItem ->
//                    when (menuItem.itemId) {
//                        R.id.copy -> {
//                            val text = messageTextTv.text.toString()
//                            listener?.copyText(text)
//                        }
//                        R.id.pin -> {
//                            listener?.pinMessage(messageRealmObject)
//                        }
//                        R.id.forward -> {
//                            listener?.forwardMessage(messageRealmObject)
//                        }
//                        R.id.reply -> {
//                            listener?.replyMessage(messageRealmObject)
//                        }
//                        R.id.delete_message -> {
//                            listener?.deleteMessage(messageRealmObject.primary)
//                        }
//                    }
//                    true
//                }
//                popup.show()
//            }
//        }

        itemView.setOnLongClickListener {
            if (!Check.getSelectedMode()) listener?.onLongClick(message.primary)
            else {
                listener?.checkItem(!message.isChecked, message.primary)
            }
            true
        }
    }


}
