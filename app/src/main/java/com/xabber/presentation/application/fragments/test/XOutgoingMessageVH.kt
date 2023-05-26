package com.xabber.presentation.application.fragments.test

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.widget.FrameLayout
import androidx.annotation.StyleRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.dto.MessageDto
import com.xabber.models.dto.MessageVhExtraData
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.MessageChanger
import com.xabber.presentation.application.fragments.chat.message.MessageDeliveryStatusHelper
import com.xabber.presentation.application.fragments.chat.message.XMessageVH
import com.xabber.utils.StringUtils
import com.xabber.utils.dp
import java.util.*

class XOutgoingMessageVH internal constructor(
    private val listener: MessageAdapter.Listener?,
    itemView: View?, messageListener: MessageClickListener?,
    longClickListener: MessageLongClickListener?,
    fileListener: FileListener?, @StyleRes appearance: Int
) : XMessageVH(itemView!!, messageListener!!, longClickListener!!, fileListener, appearance) {
    @SuppressLint("UseCompatLoadingForDrawables")
    override fun bind(message: MessageDto, extraData: MessageVhExtraData) {
        super.bind(message, extraData)
        val context: Context = itemView.context
        var needTail: Boolean = extraData.isNeedTail
        if (message.references.size > 0) needTail = false
        if (message.isChecked) itemView.setBackgroundResource(R.color.selected) else itemView.setBackgroundResource(
            R.color.transparent
        )


        //  val fram = itemView.findViewById<FrameLayout>(R.id.fram)
        //      val params = messageBalloon.layoutParams as ConstraintLayout.LayoutParams
//        messageBalloon.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
//        val width = fram.measuredWidth
//      //   Math.round(fram.measuredWidth.toFloat() / 2) * 2 + 50
//        val height = fram.measuredHeight
//        //  Math.round(fram.measuredHeight.toFloat() / 2) * 2 + 40
        //  params.width = dpToPx(MessageChanger.w, itemView)
        //     params.height = dpToPx(MessageChanger.h, itemView)

        //  messageBalloon.layoutParams = params

        // text & appearance
        messageTextTv.text = message.messageBody
//            "${MessageChanger.w.toString()} x ${MessageChanger.h}"
//        MessageChanger.w = MessageChanger.w + 10
//        MessageChanger.h = MessageChanger.h + 1

        // time
        val date = Date(message.sentTimestamp)
        val time = StringUtils.getTimeText(context, date)
        messageTime.text = if (message.editTimestamp > 0) "edit $time" else time

//        // setup BACKGROUND
        val shadowDrawable = ContextCompat.getDrawable(
            context,
             if (needTail) MessageChanger.tail else
           MessageChanger.simple

        )
//        shadowDrawable?.setColorFilter(
//            ContextCompat.getColor(context, R.color.white),
//            PorterDuff.Mode.MULTIPLY
//        )
        val mesBack = itemView.findViewById<FrameLayout>(R.id.frame_background)
        mesBack.background = ContextCompat.getDrawable(
            context, MessageChanger.hvost
        )

        mesBack.isInvisible = !needTail
        messageBalloon.background = shadowDrawable
//        messageBalloon.layoutParams = layoutParams
//        val cons = ConstraintSet()
//        cons.clone(constraint)
//        cons.connect(R.id.message_balloon, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)


        messageBalloon.setPadding(
            0,
            2.dp,
            8.dp,
            2.dp
        )

//      if (message.references.size > 0) {
//        //  if (message.hasImage) {
//              messageBalloon.setPadding(
//                  dipToPx(border, context),
//                  dipToPx(border - 0.05f, context),
//                  dipToPx(border, context),
//                  dipToPx( border, context)
//              )
//       //   }
//      }
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
                Log.d(
                    "sel",
                    "Check.getSelectedMode ${Check.getSelectedMode()}, mess isChecked = ${message.isChecked}"
                )
                listener?.checkItem(!message.isChecked, message.primary)
            } else {
                val popup = PopupMenu(it.context, it, Gravity.CENTER)
                popup.setForceShowIcon(true)
                if (message.isOutgoing) popup.inflate(R.menu.popup_menu_message_outgoing)
                else popup.inflate(R.menu.popup_menu_message_incoming)


                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.copy -> {
                            val text = message.messageBody
                            listener?.copyText(text)
                        }
                        R.id.pin -> {
                            listener?.pinMessage(message)
                        }
                        R.id.forward -> {
                            listener?.forwardMessage(message)
                        }
                        R.id.reply -> {
                            listener?.replyMessage(message)
                        }
                        R.id.delete_message -> {
                            listener?.deleteMessage(message.primary)
                        }
                        R.id.edit -> {
                            listener?.editMessage(message.primary, message.messageBody)
                        }
                    }
                    true
                }
                popup.show()
            }
        }
        itemView.setOnClickListener {
            Log.d(
                "sel",
                "ITEM Check.getSelectedMode ${Check.getSelectedMode()}, mess isChecked = ${message.isChecked}"
            )
            if (Check.getSelectedMode()) {
                listener?.checkItem(!message.isChecked, message.primary)
            } else {
                val popup = PopupMenu(messageTextTv.context, messageTextTv, Gravity.CENTER)
                popup.setForceShowIcon(true)
                if (message.isOutgoing) popup.inflate(R.menu.popup_menu_message_outgoing)
                else popup.inflate(R.menu.popup_menu_message_incoming)


                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.copy -> {
                            val text = message.messageBody
                            listener?.copyText(text)
                        }
                        R.id.pin -> {
                            listener?.pinMessage(message)
                        }
                        R.id.forward -> {
                            listener?.forwardMessage(message)
                        }
                        R.id.reply -> {
                            listener?.replyMessage(message)
                        }
                        R.id.delete_message -> {
                            listener?.deleteMessage(message.primary)
                        }
                        R.id.edit -> {
                            listener?.editMessage(message.primary, message.messageBody)
                        }
                    }
                    true
                }
                popup.show()
            }
        }

        itemView.setOnLongClickListener {
            if (!Check.getSelectedMode()) listener?.onLongClick(message.primary)
            else {
                listener?.checkItem(!message.isChecked, message.primary)
            }
            true
        }

        //  imageGridContainer.removeAllViews()
        //    imageGridContainer.isVisible = false
        Log.d("ppp", "size ${message.references.size}")
        if (message.references.isNotEmpty()) {
            Log.d("ppp", "grid is not empty")
            //    imageGridContainer.isVisible = true
            val builder = ImageGridBuilder()
            // val imageGridView: View =
            //       builder.inflateView(imageGridContainer, message.references.size)
            //     builder.bindView(imageGridView, message.references, this)
            //     imageGridContainer.addView(imageGridView)
            //     imageGridContainer.visibility = View.VISIBLE
        }

        //   if (imageGridContainer.isVisible) {
        //      imageGridContainer.setOnClickListener {

        //     }
        //  }


    }


    private fun setStatusIcon(messageRealmObject: MessageDto) {
        statusIcon.isVisible = true
        //  bottomStatusIcon.isVisible = true
        //   progressBar.isVisible = false
        if (messageRealmObject.messageSendingState === MessageSendingState.Uploading) {
            messageTextTv.text = ""
            statusIcon.isVisible = false
            //    bottomStatusIcon.isVisible = false
        } else {
            MessageDeliveryStatusHelper.setupStatusImageView(
                messageRealmObject, statusIcon
            )
            MessageDeliveryStatusHelper.setupStatusImageView(
                messageRealmObject, statusIcon
            )
        }
    }

    private fun dpToPx(dp: Int, itemView: View?): Int {
        val displayMetrics = itemView?.context?.resources?.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), displayMetrics)
            .toInt()
    }

}
