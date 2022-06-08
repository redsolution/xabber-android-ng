package com.xabber.presentation.application.fragments.message

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import com.xabber.data.dto.MessageDto
import com.xabber.databinding.ItemMessageSystemBinding
import com.xabber.presentation.application.util.StringUtils

class SystemMessageViewHolder(
    private val binding: ItemMessageSystemBinding
) : BasicViewHolder(
    binding.root,
    null
) {
    @RequiresApi(Build.VERSION_CODES.N)
    override fun bind(messageDto: MessageDto, isNeedTail: Boolean, needDay: Boolean, showCheckbox: Boolean) {
        super.bind(messageDto, isNeedTail, needDay, showCheckbox)
        binding.messageText.isVisible = messageDto.messageBody != null
        if (messageDto.messageBody != null) binding.messageText.text = messageDto.messageBody
        binding.messageDate.tvDate.isVisible = needDay
        binding.messageDate.tvDate.text = StringUtils.getDateStringForMessage(messageDto.sentTimestamp)
    }
}
