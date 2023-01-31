package com.xabber.presentation.application.fragments.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.ItemFileBinding
import com.xabber.databinding.ItemVoiceBinding

class VoiceAdapter: RecyclerView.Adapter<VoiceVH>() {
    private var medias = ArrayList<String>()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceVH {
        return VoiceVH(
            ItemVoiceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: VoiceVH, position: Int) {
        holder.bind()
    }

    override fun getItemCount(): Int = medias.size

    fun updateAdapter(newMedias: ArrayList<String>) {
        medias = newMedias
    }

}