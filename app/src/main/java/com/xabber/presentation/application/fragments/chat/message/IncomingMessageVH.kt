package com.xabber.presentation.application.fragments.chat.message

import android.content.Context
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.dto.MessageDto
import com.xabber.presentation.application.fragments.chat.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.ChatSettingsManager
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.utils.StringUtils
import com.xabber.utils.custom.ShapeOfView
import com.xabber.utils.dp
import java.util.*

class IncomingMessageVH internal constructor(
    private val listener: MessageAdapter.Listener?,
    itemView: View, messageListener: MessageClickListener?,
    longClickListener: MessageLongClickListener?, fileListener: FileListener?,
    val listen: BindListener?, avatarClickListener: OnMessageAvatarClickListener,
) : MessageVH(itemView, messageListener!!, longClickListener!!, fileListener) {

    interface BindListener {
        fun onBind(message: MessageDto?)
    }

    interface OnMessageAvatarClickListener {
        fun onMessageAvatarClick(position: Int)
    }

    override fun bind(messageDto: MessageDto, vhExtraData: MessageVhExtraData) {
        super.bind(messageDto, vhExtraData)
       val tvMessageUserName = itemView.findViewById<TextView>(R.id.tv_message_username)
        val avatarShape = itemView.findViewById<ShapeOfView>(R.id.avatar_shape)
        val avatar = itemView.findViewById<ImageView>(R.id.im_message_avatar)


      avatarShape.isVisible = vhExtraData.isGroup && vhExtraData.isNeedTail



        val shapeParams = avatarShape.layoutParams as RelativeLayout.LayoutParams
        val containerParams = messageContainer?.layoutParams as RelativeLayout.LayoutParams
       if (!ChatSettingsManager.bottom) {

//           containerParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
//           containerParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
           shapeParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
           shapeParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
       } else {
//           containerParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
//           containerParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
           shapeParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
           shapeParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
       }
        avatarShape.layoutParams = shapeParams
//messageContainer?.layoutParams = containerParams

    tvMessageUserName.isVisible = vhExtraData.isNeedName

//if (extraData.isGroup && extraData.isNeedName) {
//    messageBalloon.removeAllViews()
//    val v = inflateText(messageBalloon)
//    messageBalloon.addView(v)
//}

//        if (message.messageBody.length < tvMessageUserName.text.length) {
//            linearLayoutTextBox.setMinimumWidth(usernameWidth)
//        }
//        else {
//            val par = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
//            )
//            linearLayoutTextBox.layoutParams = par
//        }


        val context: Context = itemView.context
        var needTail: Boolean = vhExtraData.isNeedTail

        val balloon = itemView.findViewById<FrameLayout>(R.id.balloon)
        val messageBalloon = itemView.findViewById<LinearLayout>(R.id.message_container)
        val tail = itemView.findViewById<FrameLayout>(R.id.tail)

        if (messageDto.hasReferences) needTail = false

        // checked background
        if (messageDto.isChecked) itemView.setBackgroundResource(R.color.selected) else itemView.setBackgroundResource(
            R.color.transparent
        )

        // text
      //  messageTextTv.text = messageDto.messageBody

        // time
        val date = Date(messageDto.sentTimestamp)
        val time = StringUtils.getTimeText(context, date)
        tvTime?.text = if (messageDto.editTimestamp > 0) "edit $time" else time


        // background
        val balloonBackground = ContextCompat.getDrawable(
            context,
            if (needTail) ChatSettingsManager.tail else
                ChatSettingsManager.simple
        )

        val tailBackground = ContextCompat.getDrawable(
            context, ChatSettingsManager.hvost
        )
        tailBackground?.setColorFilter(
            ContextCompat.getColor(context, R.color.blue_100),
            PorterDuff.Mode.MULTIPLY
        )
        balloonBackground?.setColorFilter(
            ContextCompat.getColor(context, R.color.blue_100),
            PorterDuff.Mode.MULTIPLY
        )
        balloon.background = balloonBackground

        tail.background = tailBackground

        if (!ChatSettingsManager.bottom) {
            balloon.scaleY = -1f
            tail.scaleY = -1f
        } else {
            balloon.scaleY = 1f
            tail.scaleY = 1f
        }

        // visible tail
        tail.isInvisible = !needTail || ChatSettingsManager.messageTypeValue?.rawValue == 2


        statusIcon?.isVisible = false
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
                if (messageDto.isUnread)
                    listen?.onBind(messageDto)
            }

            override fun onViewDetachedFromWindow(v: View) {

            }
        })

        val params = tail?.layoutParams as RelativeLayout.LayoutParams
        params.marginStart = if (vhExtraData.isGroup && !vhExtraData.isNeedTail) 56.dp else 4.dp
//        if (ChatSettingsManager.bottom) {
//            params.removeRule(RelativeLayout.ALIGN_TOP)
//            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
//        } else {
//            params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
//            params.addRule(RelativeLayout.ALIGN_TOP, R.id.balloon)
//        }
       tail?.layoutParams = params
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
            if (!Check.getSelectedMode()) listener?.onLongClick(messageDto.primary)
            else {
                listener?.checkItem(!messageDto.isChecked, messageDto.primary)
            }
            true
        }
    }

    fun inflateText(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context)
            .inflate(R.layout.user_name, parent, false)
    }
}
