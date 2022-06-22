package com.xabber.presentation.application.fragments.chat

import androidx.camera.view.PreviewView
import com.xabber.databinding.ItemPreviewCameraBinding

class CameraVH(private val binding: ItemPreviewCameraBinding) : BaseImageVH(binding.root) {

    fun getCameraPreview(): PreviewView = binding.previewCamera
    fun bind() {

    }


}