package com.xabber.presentation.application.fragments.chat.message

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemFileMessageBinding
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import kotlin.math.log10
import kotlin.math.pow

class FileViewHolder(private val binding: ItemFileMessageBinding) :
    RecyclerView.ViewHolder(binding.root) {
    val view: View
        get() = itemView

    fun bind(reference: MessageReferenceStorageItem) {
        binding.tvFileName.text = reference.fileName ?: ""
        binding.tvFileSize.text = formatFileSize(reference.fileSize ?: 0L)
    }

    @SuppressLint("DefaultLocale")
    fun formatFileSize(fileSize: Long): String {
        if (fileSize <= 0) {
            return "0 B"
        }

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(fileSize.toDouble()) / log10(1024.0)).toInt()

        return String.format(
            "%.1f %s",
            fileSize / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
    }
}