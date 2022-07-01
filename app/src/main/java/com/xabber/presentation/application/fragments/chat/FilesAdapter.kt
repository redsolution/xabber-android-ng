package com.xabber.presentation.application.fragments.chat

import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.ItemRecentFilesBinding

class FilesAdapter(private val listener: FilesListener) :
RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val selectedImagePaths = HashSet<Uri>()

    companion object {

        private val imagePaths = ArrayList<Uri>()
        val projectionPhotos = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
        )

    }

    fun getSelectedImagePaths(): HashSet<Uri> = selectedImagePaths

    fun updateAdapter(newImagePaths: ArrayList<Uri>) {
        imagePaths.clear()
        imagePaths.addAll(newImagePaths)
        selectedImagePaths.clear()
    }

    interface FilesListener {
        fun onRecentImagesSelected()
        fun tooManyFilesSelected()

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return FileVH(
            ItemRecentFilesBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        val recentImageViewHolder = holder as GalleryVH
        val path = imagePaths[position]
        val image = recentImageViewHolder.getImage()
        Glide.with(image.context).load(path).centerCrop().placeholder(R.drawable.ic_image)
            .into(image)
        recentImageViewHolder.getImage().setOnClickListener {
            recentImageViewHolder.getCheckBox().isChecked =
                !recentImageViewHolder.getCheckBox().isChecked
        }
        recentImageViewHolder.getCheckBox().setOnCheckedChangeListener(null)
        recentImageViewHolder.getCheckBox().isChecked = selectedImagePaths.contains(path)

        recentImageViewHolder.getCheckBox()
            .setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    if (selectedImagePaths.size < 10)
                        selectedImagePaths.add(path)
                    else {
                        buttonView.isChecked = false
                  //      listener.tooManyFilesSelected()
                    }
                } else {
                    selectedImagePaths.remove(path)
                }

             //   listener.onRecentImagesSelected()
            })
    }


    override fun getItemCount(): Int = imagePaths.size

    fun getSelectedImagePath(): HashSet<Uri> = selectedImagePaths


    private fun getFileIconByCategory(category: FileCategory): Int {
        return when (category) {
            FileCategory.IMAGE -> R.drawable.ic_image
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

