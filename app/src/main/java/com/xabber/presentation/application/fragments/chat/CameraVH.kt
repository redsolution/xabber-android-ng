package com.xabber.presentation.application.fragments.chat

import android.widget.ImageView
import android.widget.TextView
import androidx.camera.view.PreviewView
import com.xabber.databinding.ItemPreviewCameraBinding

class CameraVH(private val binding: ItemPreviewCameraBinding) : BaseImageVH(binding.root) {

    fun getCameraPreview(): PreviewView = binding.previewCamera
    fun getTextViewPreview(): TextView = binding.tvCameraPreview
    fun getImageViewPreview(): ImageView = binding.imageViewPreviewCamera


}