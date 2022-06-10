package com.xabber.presentation.application.fragments.message

import android.os.Build
import androidx.annotation.RequiresApi
import com.xabber.data.dto.MessageDto
import com.xabber.databinding.ItemMessageSystemBinding

class InformationMessageVH(
    private val binding: ItemMessageSystemBinding
) : BasicViewHolder(
    binding.root,
   null
) {
    @RequiresApi(Build.VERSION_CODES.N)
    override fun bind(
        messageDto: MessageDto,
        isNeedTail: Boolean,
        needDay: Boolean,
        showCheckbox: Boolean,
        isNeedTitle: Boolean
    ) {
        super.bind(messageDto, isNeedTail, needDay, showCheckbox, isNeedTitle)
        binding.messageText.text = messageDto.messageBody
    }
}