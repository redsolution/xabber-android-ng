package com.xabber.presentation.application.fragments.chat.message

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.databinding.ItemChatListBinding
import com.xabber.databinding.ItemFileBinding
import com.xabber.databinding.ItemFileMessageBinding
import com.xabber.dto.MessageReferenceDto

class FileViewHolder(private val binding: ItemFileMessageBinding) :
    RecyclerView.ViewHolder(binding.root) {
    val view: View
        get() = itemView

    fun bind(reference: MessageReferenceDto) {
        binding.tvFileName.text = reference.mimeType
        binding.tvFileSize.text = reference.size
    }
}