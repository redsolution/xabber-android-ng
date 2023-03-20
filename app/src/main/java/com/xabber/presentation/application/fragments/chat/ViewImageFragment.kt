package com.xabber.presentation.application.fragments.chat

import android.R.attr
import android.graphics.drawable.BitmapDrawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.MediaController
import androidx.fragment.app.Fragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.FragmentImageViewerBinding


class ViewImageFragment : Fragment(R.layout.fragment_image_viewer) {
    private val binding by viewBinding(FragmentImageViewerBinding::bind)
    private var mediaController: MediaController? = null
    private var mediaUri: Uri? = null
    private var progress = 0

    companion object {
        fun newInstance(mediaUri: Uri) = ViewImageFragment().apply {
            this.mediaUri = mediaUri
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (mediaUri != null) {
            if (isImage()) {
                Glide.with(binding.ivPhoto).load(mediaUri).into(binding.ivPhoto)
            } else {
                Glide.with(binding.ivPhoto).load(mediaUri).into(binding.ivPhoto)
            }
//                    binding.ivPhoto.isVisible = false
//                    binding.ivVideo.isVisible = true
//                    mediaController = MediaController(binding.ivVideo.context)
//                    mediaController!!.setAnchorView(binding.ivVideo)
//                    mediaController!!.setPadding(0, 0, 0, 120)
//                    binding.ivVideo.setMediaController(mediaController)
//                    binding.ivVideo.setVideoURI(mediaUri)
//
//                }
//            }
//        if (savedInstanceState != null) {
//            progress = savedInstanceState.getInt("progress")
//            binding.ivVideo.seekTo(progress)
//        } else { binding.ivVideo.seekTo(1) }
        }
    }

    private fun getMimeType(): String = context?.contentResolver?.getType(mediaUri!!)!!


    private fun isImage(): Boolean {
        val mimeType = getMimeType()
        return mimeType.startsWith("image/")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("progress", binding.ivVideo.currentPosition)
    }
    override fun onDestroyView() {
        super.onDestroyView()
        mediaController = null
    }
}