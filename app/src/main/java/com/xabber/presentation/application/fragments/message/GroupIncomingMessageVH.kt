package com.xabber.presentation.application.fragments.message

import android.graphics.PorterDuff
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.CustomPopupMenu
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.xabber.R
import com.xabber.data.dto.MessageDto
import com.xabber.databinding.ItemGroupIncomingBinding

class GroupIncomingMessageVH(
    private val binding: ItemGroupIncomingBinding
) : BasicViewHolder(
    binding.root
) {
    private var needTail = false
    @RequiresApi(Build.VERSION_CODES.N)
    override fun bind(message: MessageDto, isNeedTail: Boolean, needDay: Boolean) {
        super.bind(message, isNeedTail, needDay)
        binding.tvContactName.text = message.owner
        binding.tvContent.text = message.messageBody
        binding.tvSendingTime.text =
            if (message.editTimestamp != null) message.editTimestamp.toString() else message.sentTimestamp.toString()

        setBackground(message)
        setMargin(message, isNeedTail)
        val popupMenu = getPopupMenu()
        binding.root.setOnClickListener {
            popupMenu.show()
        }
    }

    private fun getPopupMenu(): PopupMenu {
        val popup = CustomPopupMenu(binding.root.context, binding.root, Gravity.CENTER)
        popup.inflate(R.menu.context_menu_message_incoming)
        popup.setForceShowIcon(true)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.copy -> {}
                R.id.forward -> {}
                R.id.reply -> {}
                R.id.delete_message -> {}
            }
            true
        }
        return popup
    }

    private fun setMargin(itemModel: MessageDto, isNeedTail: Boolean) {
        binding.avatarContact.isVisible = needTail
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
            params.setMargins(if (needTail) 2 else 24, 0, 0, 0)
            binding.balloon.layoutParams = params
            binding.balloon.setPadding(if (needTail) 54 else 26, 26, 26, 26)
    }

    private fun setBackground(
        messageDto: MessageDto
    ) {
           val balloonDrawable = ResourcesCompat.getDrawable(
            itemView.resources,
            if (needTail)
                    R.drawable.msg_in
            else
                R.drawable.msg,
            itemView.context.theme
        )
        binding.balloon.background = balloonDrawable
            balloonDrawable?.setColorFilter(
                itemView.resources.getColor(
                    R.color.blue_100,
                    itemView.context.theme
                ), PorterDuff.Mode.MULTIPLY
            )

        val shadowDrawable = ResourcesCompat.getDrawable(
            itemView.resources,
            if (needTail)
                    R.drawable.msg_in_shadow
            else
                R.drawable.msg_shadow,
            itemView.context.theme
        )
    }

}