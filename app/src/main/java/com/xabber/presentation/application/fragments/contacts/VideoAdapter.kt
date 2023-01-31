package com.xabber.presentation.application.fragments.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemMediaBinding
import com.xabber.databinding.ItemVideoBinding

class VideoAdapter: RecyclerView.Adapter<VideoVH>() {
    private var medias = ArrayList<String>()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoVH {
        return VideoVH(
            ItemVideoBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: VideoVH, position: Int) {
        holder.bind()
    }

    override fun getItemCount(): Int = medias.size

    fun updateAdapter(newMedias: ArrayList<String>) {
        medias = newMedias
    }
}