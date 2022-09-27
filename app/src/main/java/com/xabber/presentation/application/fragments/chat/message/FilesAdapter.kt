package com.xabber.presentation.application.fragments.chat.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R


class FilesAdapter(private val fileListListener: FileListListener, private val timeStamp: Long) :
    RecyclerView.Adapter<FileViewHolder>() {

    interface FileListListener {
        fun onFileClick(position: Int)

        fun onVoiceClick(position: Int, attachmentId: String, saved: Boolean, timeStamp: Long)

        fun onVoiceProgressClick(
            position: Int,
            attachmentId: String,
            timestamp: Long,
            current: Int,
            max: Int
        )

        fun onFileLongClick(caller: View)

        fun onDownLoadCancel()

        fun onDownLoadError(error: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view: View = LayoutInflater.from(parent.context).inflate(
            R.layout.item_file_message, parent, false
        )
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {

    }

    override fun getItemCount(): Int = 0

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
            else -> R.drawable.ic_file
        }
    }
}