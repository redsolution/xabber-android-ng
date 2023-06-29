package com.xabber.presentation.application.fragments.chat.message

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.databinding.ItemChatListBinding
import com.xabber.databinding.ItemFileBinding
import com.xabber.databinding.ItemFileMessageBinding
import com.xabber.dto.MessageReferenceDto
import java.lang.String

class FileViewHolder(private val binding: ItemFileMessageBinding) :
    RecyclerView.ViewHolder(binding.root) {
    val view: View
        get() = itemView

    fun bind(reference: MessageReferenceDto) {
        Log.d("iii", "${reference.fileName}")
        binding.tvFileName.text = reference.fileName
        binding.tvFileSize.text = formatFileSize(reference.size)
    }

    @SuppressLint("DefaultLocale")
    fun formatFileSize(fileSize: Long): kotlin.String {
        if (fileSize <= 0) {
            return "0 B".toString()
        }

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(fileSize.toDouble()) / Math.log10(1024.0)).toInt()

        return String.format(
            "%.1f %s",
            fileSize / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }
}