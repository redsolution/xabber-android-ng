package com.xabber.presentation.application.fragments.chat

import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout


import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.ItemImageFromGalleryBinding


class GalleryAdapter(private val listener: Listener) :
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

    fun updateAdapter(newImagePaths: java.util.ArrayList<Uri>) {
        imagePaths.clear()
        imagePaths.addAll(newImagePaths)
        selectedImagePaths.clear()
    }

    interface Listener {
        fun onRecentImagesSelected()
        fun tooManyFilesSelected()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryVH {
        return GalleryVH(
                    ItemImageFromGalleryBinding.inflate(
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
val cl = recentImageViewHolder.getCl()
if (position == imagePaths.size -1) {
    val params = cl.layoutParams as RecyclerView.LayoutParams
    params.bottomMargin = 400
    Log.d("iii", "bottom")
    image.layoutParams = params
} else {
    val params = cl.layoutParams as RecyclerView.LayoutParams
    params.bottomMargin = 0
    image.layoutParams = params
}
            Glide.with(image.context).load(path).centerCrop().placeholder(R.drawable.ic_image_grey)
                .into(image)
            recentImageViewHolder.getImage().setOnClickListener {
                recentImageViewHolder.getCheckBox().isChecked =
                    !recentImageViewHolder.getCheckBox().isChecked
            }
            recentImageViewHolder.getCheckBox().setOnCheckedChangeListener(null)
            recentImageViewHolder.getCheckBox().isChecked = selectedImagePaths.contains(path)

            recentImageViewHolder.getCheckBox()
                .setOnCheckedChangeListener { buttonView, isChecked ->
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
                }
    }

    override fun getItemCount(): Int = imagePaths.size

}
