package com.xabber.presentation.application.fragments.chat


import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
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

    interface Listener {
        fun onRecentImagesSelected()
        fun tooManyFilesSelected()
        fun showImageViewer(position: Int)
    }

    fun getSelectedImagePaths(): HashSet<Uri> = selectedImagePaths

    fun updateAdapter(newImagePaths: java.util.ArrayList<Uri>) {
        imagePaths.clear()
        imagePaths.addAll(newImagePaths)
        selectedImagePaths.clear()
    }

    fun isImage() {

    }

    fun isVideo(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri
        )
        return mimeType.startsWith("video/")
    }

    private fun getMimeType(context: Context, uri: Uri): String = context.contentResolver?.getType(uri)!!

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
        val tv = recentImageViewHolder.getTv()
        tv.isVisible = isVideo(tv.context, path)
        if (position == imagePaths.size - 1) {
            val params = cl.layoutParams as GridLayoutManager.LayoutParams
            params.bottomMargin = 400
            cl.layoutParams = params
        } else {
            val params = cl.layoutParams as GridLayoutManager.LayoutParams
            params.bottomMargin = 0
            cl.layoutParams = params
        }

        Glide.with(image.context).load(path).centerCrop().placeholder(R.drawable.ic_image_grey)
            .into(image)
        recentImageViewHolder.getImage().setOnClickListener {
            listener.showImageViewer(position)
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
