package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.xabber.R
import com.xabber.databinding.ItemFileMessageBinding
import com.xabber.data_base.models.messages.MessageReferenceStorageItem

class FilesAdapter(
    private val files: ArrayList<MessageReferenceStorageItem>,
    private val timeStamp: Long,
    private val listener: OnFileClickListener
) : ListAdapter<MessageReferenceStorageItem, FileViewHolder>(
    object : DiffUtil.ItemCallback<MessageReferenceStorageItem>() {
        override fun areItemsTheSame(
            oldItem: MessageReferenceStorageItem,
            newItem: MessageReferenceStorageItem
        ) = oldItem.primary == newItem.primary

        override fun areContentsTheSame(
            oldItem: MessageReferenceStorageItem,
            newItem: MessageReferenceStorageItem
        ) = oldItem == newItem
    }
) {

    interface OnFileClickListener {
        fun onFileClick(path: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemFileMessageBinding.inflate(inflater, parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val currentItem = getItem(position)
        val icon = holder.view.findViewById<ImageView>(R.id.ivFileIcon)
        icon.setImageResource(
            getFileIconByCategory(
                FileCategory.determineFileCategory(currentItem.mimeType ?: "")
            )
        )
        holder.view.setOnClickListener {
            if (currentItem.uri != null) listener.onFileClick(currentItem.uri!!)
        }
        holder.bind(currentItem)
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
            FileCategory.APK -> R.drawable.ic_get_app
            else -> R.drawable.ic_file
        }
    }
}