package com.xabber.presentation.application.fragments.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemFileBinding
import com.xabber.databinding.ItemMediaBinding

class FAdapter : RecyclerView.Adapter<FileVH>() {
    private var medias = ArrayList<String>()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileVH {
        return FileVH(
            ItemFileBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: FileVH, position: Int) {
        holder.bind()
    }

    override fun getItemCount(): Int = medias.size

    fun updateAdapter(newMedias: ArrayList<String>) {
        medias = newMedias
    }

}