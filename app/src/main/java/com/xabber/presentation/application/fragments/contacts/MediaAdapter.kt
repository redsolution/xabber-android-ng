package com.xabber.presentation.application.fragments.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemMediaBinding

class MediaAdapter : RecyclerView.Adapter<MediaViewHolder>() {
    private var medias = ArrayList<String>()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        return MediaViewHolder(
            ItemMediaBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind()
    }

    override fun getItemCount(): Int = medias.size

    fun updateAdapter(newMedias: ArrayList<String>) {
        medias = newMedias
    }
}