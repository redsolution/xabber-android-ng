package com.xabber.presentation.application.fragments.message

import android.os.Build
import androidx.annotation.RequiresApi
import com.xabber.data.dto.MessageDto
import com.xabber.databinding.ItemMessageSystemBinding

class InformationMessageVH(
    private val binding: ItemMessageSystemBinding
) : BasicViewHolder(
    binding.root
) {
    @RequiresApi(Build.VERSION_CODES.N)
    override fun bind(message: MessageDto, isNeedTail: Boolean, needDay: Boolean) {
        super.bind(message, isNeedTail, needDay)
        binding.messageText.text = message.messageBody
    }
}