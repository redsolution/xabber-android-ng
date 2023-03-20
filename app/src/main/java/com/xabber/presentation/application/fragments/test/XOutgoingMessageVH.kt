package com.xabber.presentation.application.fragments.test

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.StyleRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.models.dto.MessageDto
import com.xabber.models.dto.MessageVhExtraData
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.message.MessageDeliveryStatusHelper
import com.xabber.presentation.application.fragments.chat.message.XMessageVH
import com.xabber.utils.StringUtils
import com.xabber.utils.dipToPx
import java.util.*
import kotlin.collections.ArrayList

class XOutgoingMessageVH internal constructor(private val listener: MessageAdapter.Listener,
    itemView: View?, messageListener: MessageClickListener?,
    longClickListener: MessageLongClickListener?,
    fileListener: FileListener?, @StyleRes appearance: Int
) : XMessageVH(itemView!!, messageListener!!, longClickListener!!, fileListener, appearance) {
    @SuppressLint("UseCompatLoadingForDrawables")
  override  fun bind(message: MessageDto, extraData: MessageVhExtraData) {
        super.bind(message, extraData)
        val context: Context = itemView.context
        val needTail: Boolean = extraData.isNeedTail
        if (message.isChecked) itemView.setBackgroundResource(R.color.selected) else itemView.setBackgroundResource(R.color.transparent)
        // text & appearance
        messageTextTv.text = message.messageBody

       // time
        val date = Date(message.sentTimestamp)
        val time = StringUtils.getTimeText(context, date)
        messageTime.text =   if(message.editTimestamp > 0)  "edit $time" else  time

        // setup BACKGROUND
        val shadowDrawable = ContextCompat.getDrawable(context,
            if (needTail) R.drawable.msg_out_shadow else R.drawable.msg_shadow)
        shadowDrawable?.setColorFilter(
            ContextCompat.getColor(context, R.color.black),
            PorterDuff.Mode.MULTIPLY
        )
        messageBalloon.background = ContextCompat.getDrawable(context,
            if (needTail) R.drawable.msg_out else R.drawable.msg)

        messageShadow.background = shadowDrawable

        // setup BALLOON margins
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(
            dipToPx(0f, context),
            dipToPx(3f, context),
            dipToPx(if (needTail) 3f else 11f, context),
            dipToPx(3f, context)
        )
        messageShadow.layoutParams = layoutParams

        // setup MESSAGE padding
        messageBalloon.setPadding(
            dipToPx(12f, context),
            dipToPx(8f, context),  //Utils.dipToPx(needTail ? 20f : 12f, context),
            dipToPx(if (needTail) 14.5f else 6.5f, context),
            dipToPx(8f, context)
        )

        setStatusIcon(message)

        // subscribe for FILE UPLOAD PROGRESS
        itemView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
               // subscribeForUploadProgress()
            }

            override fun onViewDetachedFromWindow(v: View) {
                unsubscribeAll()
            }
        })
        if (messageTextTv.getText().toString().trim().isEmpty()) {
            messageTextTv.setVisibility(View.GONE)
        }
messageTextTv.setOnClickListener {
    if (Check.getSelectedMode()) {
        Log.d("sel", "Check.getSelectedMode ${Check.getSelectedMode()}, mess isChecked = ${message.isChecked}")
        listener.checkItem(!message.isChecked, message.primary)
    } else {
        val popup = PopupMenu(it.context, it, Gravity.CENTER)
        popup.setForceShowIcon(true)
        if (message.isOutgoing) popup.inflate(R.menu.popup_menu_message_outgoing)
        else popup.inflate(R.menu.popup_menu_message_incoming)


        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.copy -> {
                    val text = message.messageBody
                    listener.copyText(text)
                }
                R.id.pin -> {
                    listener.pinMessage(message)
                }
                R.id.forward -> {
                    listener.forwardMessage(message)
                }
                R.id.reply -> {
                    listener.replyMessage(message)
                }
                R.id.delete_message -> {
                    listener.deleteMessage(message.primary)
                }
                R.id.edit -> {
                    listener.editMessage(message.primary, message.messageBody)
                }
            }
            true
        }
        popup.show()
    }
}
     itemView.setOnClickListener {
         Log.d("sel", "ITEM Check.getSelectedMode ${Check.getSelectedMode()}, mess isChecked = ${message.isChecked}")
            if (Check.getSelectedMode()) {
                listener.checkItem(!message.isChecked, message.primary)
            } else {
                val popup = PopupMenu(messageTextTv.context, messageTextTv, Gravity.CENTER)
                popup.setForceShowIcon(true)
                if (message.isOutgoing) popup.inflate(R.menu.popup_menu_message_outgoing)
                else popup.inflate(R.menu.popup_menu_message_incoming)


                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.copy -> {
                            val text = message.messageBody
                            listener.copyText(text)
                        }
                        R.id.pin -> {
                            listener.pinMessage(message)
                        }
                        R.id.forward -> {
                            listener.forwardMessage(message)
                        }
                        R.id.reply -> {
                            listener.replyMessage(message)
                        }
                        R.id.delete_message -> {
                            listener.deleteMessage(message.primary)
                        }
                        R.id.edit -> {
                            listener.editMessage(message.primary, message.messageBody)
                        }
                    }
                    true
                }
                popup.show()
            }
        }

        itemView.setOnLongClickListener {
            if (!Check.getSelectedMode()) listener.onLongClick(message.primary)
            else {
                listener.checkItem(!message.isChecked, message.primary)
            }
            true
        }
        imageGridContainer.removeAllViews()
        imageGridContainer.isVisible = false
        if (message.references != null && message.references.isNotEmpty()) {

imageGridContainer.isVisible = true
           val builder = ImageGridBuilder()

                val imageGridView: View =
                  builder.inflateView(imageGridContainer, message.references.size)
                builder.bindView(imageGridView, message.references, this)
                imageGridContainer.addView(imageGridView)
                imageGridContainer.visibility = View.VISIBLE

        }
    }


    private fun setStatusIcon(messageRealmObject: MessageDto) {
        statusIcon.isVisible = true
       bottomStatusIcon.isVisible = true
        progressBar.isVisible = false
        if (messageRealmObject.messageSendingState === MessageSendingState.Uploading) {
            messageTextTv.text = ""
           statusIcon.isVisible = false
           bottomStatusIcon.isVisible = false
        } else {
            MessageDeliveryStatusHelper.setupStatusImageView(
                messageRealmObject, statusIcon
            )
            MessageDeliveryStatusHelper.setupStatusImageView(
                messageRealmObject, statusIcon
            )
        }
    }

}
