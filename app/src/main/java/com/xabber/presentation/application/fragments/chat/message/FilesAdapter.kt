package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.R
import com.xabber.databinding.ItemFileMessageBinding
import com.xabber.dto.MessageReferenceDto


class FilesAdapter(private val files: ArrayList<MessageReferenceDto>, private val timeStamp: Long) :
    ListAdapter<MessageReferenceDto, FileViewHolder>(object :
        DiffUtil.ItemCallback<MessageReferenceDto>() {
        override fun areItemsTheSame(oldItem: MessageReferenceDto, newItem: MessageReferenceDto) =
            oldItem == newItem

        override fun areContentsTheSame(
            oldItem: MessageReferenceDto,
            newItem: MessageReferenceDto
        ) =
            oldItem == newItem
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemFileMessageBinding.inflate(inflater, parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val icon = holder.view.findViewById<ImageView>(R.id.ivFileIcon)
        icon.setImageResource(getFileIconByCategory(FileCategory.determineFileCategory(files[position].mimeType)))
    }

    override fun getItemCount() = files.size

    override fun getItem(position: Int) = files[position]

    private fun getFileIconByCategory(category: FileCategory): Int {
        return when (category) {
            FileCategory.IMAGE -> R.drawable.ic_image_grey
            FileCategory.AUDIO -> R.drawable.ic_audio
            FileCategory.VIDEO -> R.drawable.ic_video
            FileCategory.DOCUMENT -> R.drawable.ic_document
            FileCategory.PDF -> R.drawable.ic_pdf
            FileCategory.TABLE -> R.drawable.ic_table
            FileCategory.PRESENTATION -> R.drawable.ic_presentation
            FileCategory.ARCHIVE -> R.drawable.ic_archive
            else -> R.drawable.ic_file_grey
        }
    }

}
