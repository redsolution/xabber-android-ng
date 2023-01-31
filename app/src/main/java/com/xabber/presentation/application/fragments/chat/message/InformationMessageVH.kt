package com.xabber.presentation.application.fragments.chat.message

import com.xabber.databinding.ItemMessageSystemBinding

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