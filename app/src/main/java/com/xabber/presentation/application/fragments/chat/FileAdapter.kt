package com.xabber.presentation.application.fragments.chat

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.ItemRecentFileBinding
import com.xabber.presentation.application.fragments.chat.message.FileCategory

class FileAdapter(private val listener: FilesListener) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val selectedFilesPaths = HashSet<Uri>()

    companion object {
        private val filePaths = ArrayList<Uri>()
        @RequiresApi(Build.VERSION_CODES.Q)
        val projectionFiles = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.BUCKET_DISPLAY_NAME,
            MediaStore.Downloads.DATE_TAKEN,
        )
    }

    fun getSelectedImagePaths(): HashSet<Uri> = selectedFilesPaths

    fun updateAdapter(newFilePaths: ArrayList<Uri>) {
        filePaths.clear()
        filePaths.addAll(newFilePaths)
        selectedFilesPaths.clear()
    }

    interface FilesListener {
        fun onRecentImagesSelected()
        fun tooManyFilesSelected()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return RecentFileVH(
            ItemRecentFileBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
 val recentImageViewHolder = holder as RecentFileVH
            val path = filePaths[position]
            val image = recentImageViewHolder.getImage()
            Glide.with(image.context).load(path).centerCrop().placeholder(R.drawable.ic_image_grey)
                .into(image)

            recentImageViewHolder.getImage().setOnClickListener {
                recentImageViewHolder.getCheckBox().isInvisible =
                    !recentImageViewHolder.getCheckBox().isInvisible
           //     recentImageViewHolder.getCheckBox().isChecked  =  !recentImageViewHolder.getCheckBox().isChecked
            }
        recentImageViewHolder.getCheckBox().setOnClickListener {


        }
            recentImageViewHolder.getCheckBox()
                .setOnCheckedChangeListener { buttonView, isChecked ->
                    if (isChecked) {
                        selectedFilesPaths.add(path)
                    } else {
                        selectedFilesPaths.remove(path)
                    }

                    listener.onRecentImagesSelected()
                }

    }


    override fun getItemCount(): Int = filePaths.size

    fun getSelectedImagePath(): HashSet<Uri> = selectedFilesPaths


    private fun getFileIconByCategory(category: FileCategory): Int {
        return when (category) {
            FileCategory.IMAGE -> R.drawable.ic_image_dark
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

