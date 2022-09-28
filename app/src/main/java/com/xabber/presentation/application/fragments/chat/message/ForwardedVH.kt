package com.xabber.presentation.application.fragments.chat.message

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.xabber.R
import com.xabber.model.dto.MessageDto
import com.xabber.model.dto.MessageVhExtraData
import com.xabber.utils.dipToPx

class ForwardedVH(
    itemView: View,
    messageListener: MessageClickListener,
    longClickListener: MessageLongClickListener,
    listener: FileListener?,
    appearance: Int,
) : MessageVH(itemView, messageListener, longClickListener, listener) {

    private val tvForwardedCount: TextView = itemView.findViewById(R.id.forwarded_count_tv)

    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("UseCompatLoadingForDrawables")
    override fun bind(messageDto: MessageDto, vhExtraData: MessageVhExtraData) {
        super.bind(messageDto, vhExtraData)

        // hide STATUS ICONS
        statusIcon.visibility = View.GONE
        bottomStatusIcon.visibility = View.GONE

        val author = "Ivan"


        if (author != null && author.isNotEmpty()) {
            messageHeader.apply {
                text = author
                visibility = View.VISIBLE
            }
        } else {
            messageHeader.visibility = View.GONE
        }

        // setup FORWARDED
        val haveForwarded = messageDto.hasForwardedMessages
        if (haveForwarded) {
            forwardedMessagesRV.visibility = View.VISIBLE
            tvForwardedCount.apply {
                if (messageDto.references != null) {
                    val forwardedCount = messageDto.references.size
                 //   text = itemView.context.resources.getQuantityString(
                 //       R.plurals.forwarded_messages_count, forwardedCount, forwardedCount
                 //   )
                    paintFlags = tvForwardedCount.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    alpha =
                     //   if (SettingsManager.interfaceTheme() == SettingsManager.InterfaceTheme.light) {
                            1f
                     //   } else {
                    //        0.6f
                    //    }
                    visibility = View.VISIBLE
                }
            }
        } else {
            forwardedMessagesRV.visibility = View.GONE
        }

      //  LogManager.d(this, messageRealmObject.forwardedIds.joinToString { it.forwardMessageId })

        // setup BACKGROUND
        val balloonDrawable = itemView.context.resources.getDrawable(
            if (haveForwarded) R.drawable.fwd else R.drawable.msg
        )

        val shadowDrawable = itemView.context.resources.getDrawable(
            if (haveForwarded) R.drawable.fwd_shadow else R.drawable.msg_shadow
        )

        shadowDrawable.setColorFilter(itemView.context.resources.getColor(R.color.black), PorterDuff.Mode.MULTIPLY)
        messageBalloon.background = balloonDrawable
        messageShadow.background = shadowDrawable
        messageBalloon.setPadding(
            dipToPx(BALLOON_BORDER, itemView.context),
            dipToPx(BALLOON_BORDER, itemView.context),
            dipToPx(BALLOON_BORDER, itemView.context),
            dipToPx(BALLOON_BORDER, itemView.context)
        )

        // setup BACKGROUND COLOR
        val backgroundColor =
       //     if (isAuthorMe) {
                vhExtraData.colors.incomingForwardedBalloonColors
          //  } else {
         //       vhExtraData.colors.outgoingForwardedBalloonColors
      //      }
    //    setUpMessageBalloonBackground(messageBalloon, backgroundColor)

//        if (messageTextTv.text.toString().trim { it <= ' ' }.isEmpty()) {
//            messageTextTv.visibility = View.GONE
//        }

    }

    companion object {
        private const val BALLOON_BORDER = 6f
    }

}