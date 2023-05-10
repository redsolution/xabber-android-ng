package com.xabber.presentation.application.fragments.chat

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.ItemImageFromGalleryBinding
import com.xabber.dto.MediaDto
import java.util.concurrent.TimeUnit

class GalleryItemVH(private val binding: ItemImageFromGalleryBinding) : RecyclerView.ViewHolder(binding.root) {
    private val retriever = MediaMetadataRetriever()

    fun getRoot() = binding.frame
    fun getCheckBox() = binding.recentImageItemCheckbox
    fun getImage() = binding.image

    fun bind(mediaDto: MediaDto, isChecked: Boolean, listener: GalleryAdapter.Listener) {
        loadImageOrPreview(mediaDto.uri)
        binding.tvDuration.isVisible = mediaDto.duration > 0
        if (mediaDto.duration > 0) binding.tvDuration.text = formatDuration(mediaDto.duration)

    }

    private fun loadImageOrPreview(uri: Uri) {
        Glide.with(binding.image).load(uri).centerCrop()
            .placeholder(R.drawable.ic_image_grey)
            .into(binding.image)
    }

    private fun setVideoDuration(uri: Uri) {
        retriever.setDataSource(binding.root.context, uri)
        val durationString =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val duration = durationString?.toLong()
        if (duration != null) binding.tvDuration.text = formatDuration(duration)
    }

    private fun formatDuration(duration: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) -
                TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }
}