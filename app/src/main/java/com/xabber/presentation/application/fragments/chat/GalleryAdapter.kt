package com.xabber.presentation.application.fragments.chat

import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.ItemImageFromGalleryBinding
import com.xabber.databinding.ItemPreviewCameraBinding

class GalleryAdapter(private val listener: Listener) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val selectedImagePaths = HashSet<String>()
    val ITEM_CAMERA = 0
    val ITEM_GALLERY_IMAGE = 1


    companion object {

        private val imagePaths = ArrayList<String>()
        val projectionPhotos = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )


    }

    fun getSelectedImagePaths(): HashSet<String> = selectedImagePaths

    fun updateAdapter(newImagePaths: ArrayList<String>) {
        imagePaths.clear()
        imagePaths.addAll(newImagePaths)
        selectedImagePaths.clear()
    }

    interface Listener {
        fun onRecentImagesSelected()
        fun tooManyFilesSelected()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_CAMERA -> {  CameraVH(ItemPreviewCameraBinding.inflate(LayoutInflater.from(parent.context),
                    parent,
                    false)) }
            else -> { GalleryVH(
                ItemImageFromGalleryBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )  }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      if (position > 0) {
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
                          listener.tooManyFilesSelected()
                      }
                  } else {
                      selectedImagePaths.remove(path)
                  }

                  listener.onRecentImagesSelected()
              })
      }
    }

    override fun getItemCount(): Int = imagePaths.size

    fun getSelectedImagePath(): HashSet<String> = selectedImagePaths


    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> {
                ITEM_CAMERA
            }
            else -> {
                ITEM_GALLERY_IMAGE
            }
        }

    }


}

