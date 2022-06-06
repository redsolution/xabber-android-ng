package com.xabber.presentation.application.fragments.message

import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.CustomPopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.data.dto.MessageDto
import com.xabber.databinding.ItemMessageIncomingBinding
import com.xabber.presentation.application.util.StringUtils
import java.util.*

class IncomingMessageVH(
    private val binding: ItemMessageIncomingBinding,
    private val listener: MessageAdapter.Listener
) : BasicViewHolder(binding.root, listener) {

    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("RestrictedApi")
    override fun bind(messageDto: MessageDto, isNeedTail: Boolean, needDay: Boolean) {
        // text & appearance
        binding.tvContent.isVisible = messageDto.messageBody != null
        if (messageDto.messageBody != null) binding.tvContent.text = messageDto.messageBody
        //  tvContent.setTextAppearance(SettingsManager.chatsAppearanceStyle()) - берем из класса настроек

        // date
        binding.messageDate.tvDate.isVisible = needDay
        binding.messageDate.tvDate.text = StringUtils.getDateStringForMessage(messageDto.sentTimestamp)

        // time
        val date = Date(messageDto.sentTimestamp)
        val time = StringUtils.getTimeText(binding.tvSendingTime.context, date)
        binding.tvSendingTime.text = time


        //  val nextMessage = getMessage(position + 1)
        //   if (nextMessage != null)


        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(if (isNeedTail) 2 else 24, 0, 0, 0)
        binding.balloon.layoutParams = params
        binding.balloon.setPadding(if (isNeedTail) 54 else 26, 26, 26, 26)

        val typedValue = TypedValue()
        binding.root.context.theme.resolveAttribute(R.attr.message_background, typedValue, true)
        val shadowDrawable: Drawable =
            ContextCompat.getDrawable(binding.root.context, R.drawable.fwd_out_shadow)!!
        shadowDrawable.setColorFilter(
            ContextCompat.getColor(binding.root.context, R.color.black),
            PorterDuff.Mode.MULTIPLY
        )


        // tvContent.marginStart = if (needTail) 20 else 11
        binding.balloon.setBackgroundDrawable(
            ContextCompat.getDrawable(
                binding.root.context,
                if (isNeedTail) R.drawable.msg_in else R.drawable.msg
            )
        )

        binding.root.setOnClickListener {
            val popup = CustomPopupMenu(it.context, it, Gravity.CENTER)
            if (messageDto.isOutgoing) popup.inflate(R.menu.context_menu_message_outgoing)
            else popup.inflate(R.menu.context_menu_message_incoming)

            val menuHealper = MenuPopupHelper(it.context, popup.menu as MenuBuilder, binding.root)
            menuHealper.setForceShowIcon(true)
            menuHealper.show()

            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.copy -> {}
                    R.id.forward -> {}
                    R.id.reply -> {}
                    R.id.delete_message -> {}
                }
                true
            }
            popup.show()
            true
        }
        setBackground(messageDto, isNeedTail)
    }


    private fun setBackground(
        messageDto: MessageDto,
        isMessageNeedTail: Boolean
    ) {
        val balloonDrawable = ResourcesCompat.getDrawable(
            itemView.resources,
            if (isMessageNeedTail)
                    R.drawable.msg_in
            else
                R.drawable.msg,
            itemView.context.theme
        )!!
            balloonDrawable.setColorFilter(
                itemView.resources.getColor(
                    R.color.blue_100,
                    itemView.context.theme
                ), PorterDuff.Mode.MULTIPLY
            )
       binding.balloon.background = balloonDrawable

        val shadowDrawable = ResourcesCompat.getDrawable(
            itemView.resources,
            if (isMessageNeedTail)
                    R.drawable.msg_in_shadow
            else
                R.drawable.msg_shadow,
            itemView.context.theme
        )
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                shadowDrawable.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
//                    itemView.resources.getColor(R.color.grey_300, itemView.context.theme),
//                    BlendModeCompat.MULTIPLY
//                )
//            }
//            else {
        shadowDrawable?.setColorFilter(
            itemView.resources.getColor(
                R.color.black,
                itemView.context.theme
            ), PorterDuff.Mode.MULTIPLY
        )
//            }
        //     messageShadow.background = shadowDrawable


    }

}