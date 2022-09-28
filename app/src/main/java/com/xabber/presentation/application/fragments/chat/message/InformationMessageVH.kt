package com.xabber.presentation.application.fragments.chat.message

import android.os.Build
import androidx.annotation.RequiresApi
import com.xabber.model.dto.MessageDto
import com.xabber.databinding.ItemMessageSystemBinding
import com.xabber.presentation.application.fragments.chat.message.BasicMessageVH

class InformationMessageVH(
    private val binding: ItemMessageSystemBinding
) : BasicMessageVH(
    binding.root,
) {
//    @RequiresApi(Build.VERSION_CODES.N)
//    override fun bind(
//        messageDto: MessageDto,
//        isNeedTail: Boolean,
//        needDay: Boolean,
//        showCheckbox: Boolean,
//        isNeedTitle: Boolean
//    ) {
//        super.bind(messageDto, isNeedTail, needDay, showCheckbox, isNeedTitle)
//        binding.messageText.text = messageDto.messageBody
//    }
}