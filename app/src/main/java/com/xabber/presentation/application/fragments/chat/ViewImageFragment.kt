package com.xabber.presentation.application.fragments.chat

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.databinding.FragmentImageViewerBinding
import com.xabber.presentation.AppConstants
import com.xabber.utils.parcelable


class ViewImageFragment : Fragment(R.layout.fragment_image_viewer) {
    private val binding by viewBinding(FragmentImageViewerBinding::bind)
    private var mediaController: MediaController? = null

    companion object {
        fun newInstance(mediaUri: Uri) = ViewImageFragment().apply {
            arguments = Bundle().apply {
                putParcelable(AppConstants.MEDIA_URI, mediaUri)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mediaController = MediaController(binding.ivVideo.context)
        binding.ivVideo.setMediaController(mediaController)
        mediaController?.setAnchorView(binding.ivVideo)
        val mediaUri = arguments?.parcelable<Uri>(AppConstants.MEDIA_URI)
        if (mediaUri != null) {
            if (isImage(mediaUri)) {
                Glide.with(binding.ivPhoto).load(mediaUri).into(binding.ivPhoto)
                binding.imPlay.isVisible = false
                binding.preview.isVisible = false
                binding.ivVideo.isVisible = false
            } else {
                binding.preview.isVisible = true
                Glide.with(binding.preview).load(mediaUri).into(binding.preview)
                binding.ivPhoto.isVisible = false
                binding.ivVideo.isVisible = true
                binding.imPlay.isVisible = true
                binding.ivVideo.setVideoURI(mediaUri)

                binding.preview.setOnClickListener {
                    binding.preview.isVisible = false
                    binding.imPlay.isVisible = false
                    binding.ivVideo.start()
                    mediaController?.show()
                }
                binding.ivVideo.setOnClickListener {
                    if (binding.imPlay.isVisible) {
                        binding.imPlay.isVisible = false
                        binding.ivVideo.start()
                        mediaController?.show()
                    } else {
                        binding.ivVideo.pause()
                        binding.imPlay.isVisible = true
                    }
                }
            }
//            }
//        if (savedInstanceState != null) {
//            progress = savedInstanceState.getInt("progress")
//            binding.ivVideo.seekTo(progress)
//        } else { binding.ivVideo.seekTo(1) }
        }
    }

    private fun isImage(uri: Uri): Boolean {
        val mimeType = getMimeType(uri)
        return mimeType.startsWith("image/")
    }

    private fun getMimeType(uri: Uri): String = context?.contentResolver?.getType(uri)!!

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // outState.putInt("progress", binding.ivVideo.currentPosition)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaController = null
    }

}
