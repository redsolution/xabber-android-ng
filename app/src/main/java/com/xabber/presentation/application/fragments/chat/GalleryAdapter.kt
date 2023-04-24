package com.xabber.presentation.application.fragments.chat


import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemImageFromGalleryBinding
import com.xabber.models.dto.MediaDto


class GalleryAdapter(private val listener: Listener) :
    RecyclerView.Adapter<GalleryItemVH>() {
    private val selectedMediaIdes = HashSet<Long>()
    private val mediaList = ArrayList<MediaDto>()
    private val uriList = HashSet<Uri>()

    interface Listener {
        fun onRecentImagesSelected()
        fun tooManyFilesSelected()
        fun showMediaViewer(position: Int)
    }

    fun getSelectedMedia(): HashSet<Long> = selectedMediaIdes

    fun getUriesSelected(): HashSet<Uri> = uriList

    fun setMediaSelected(set: HashSet<Long>) {
        selectedMediaIdes.clear()
        selectedMediaIdes.addAll(set)
        uriList.clear()
        for (i in 0 until mediaList.size) {
            set.forEach { if (it == mediaList[i].id)  uriList.add(mediaList[i].uri)}
        }
    }


    fun updateAdapter(newMediaList: ArrayList<MediaDto>) {
        mediaList.clear()
        mediaList.addAll(newMediaList)
        selectedMediaIdes.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryItemVH =
        GalleryItemVH(
            ItemImageFromGalleryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: GalleryItemVH, position: Int) {
        val mediaDto = mediaList[position]
        holder.bind(mediaDto, selectedMediaIdes.contains(mediaDto.id), listener)
        val checkBox = holder.getCheckBox()

        holder.itemView.setOnClickListener {
            listener.showMediaViewer(position)
        }

        checkBox.setOnCheckedChangeListener(null)
        if (checkBox.isChecked != selectedMediaIdes.contains(mediaDto.id)) {
            checkBox.isChecked = selectedMediaIdes.contains(mediaDto.id)
        }
        checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                if (selectedMediaIdes.size < 10) {
                    selectedMediaIdes.add(mediaDto.id)
                    uriList.add(mediaDto.uri)
                } else {
                    buttonView.isChecked = false
                    listener.tooManyFilesSelected()
                }
            } else {
                selectedMediaIdes.remove(mediaDto.id)
                uriList.remove(mediaDto.uri)
            }
            notifyItemChanged(position)
            listener.onRecentImagesSelected()
        }
    }

    override fun getItemCount(): Int = mediaList.size

}
