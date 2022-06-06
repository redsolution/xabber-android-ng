package com.xabber.presentation.application.fragments.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.databinding.AttachDialogBinding
import com.xabber.presentation.application.contract.navigator

class AttachDialog(private val listener: Listener) : BottomSheetDialogFragment() {
    private var _binding: AttachDialogBinding? = null
    private val binding get() = _binding!!

    interface Listener {
        fun onRecentPhotosSend(paths: List<String>)
        fun onGalleryClick()
        fun onFilesClick()
        fun onCameraClick()
        fun onLocationClick()
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AttachDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.attachRecentImagesRecyclerView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        initDialogActions()
        //setupImagesRecycler(true)
    }

    private fun initDialogActions() {
        with(binding) {
            attachCameraButton.setOnClickListener {
            listener.onCameraClick()
              dismiss()
            }
            attachGalleryButton.setOnClickListener {

            }
            attachFileButton.setOnClickListener {

            }
           attachCheckButton.setOnClickListener {
               dismiss()
           }
        }
    }


//    private fun setupImagesRecycler(visibility: Boolean) {
//        if (visibility) {
//            binding.attachRecentImagesRecyclerView.isVisible = true
//            recentImagesAdapter = RecentImagesAdapter(this)
//            context?.let { recentImagesAdapter!!.loadGalleryPhotosAlbums(it) }
//            binding.attachRecentImagesRecyclerView.adapter = recentImagesAdapter
//        } else {
//            binding.attachRecentImagesRecyclerView.isVisible = false
//        }
//    }
//
//
//    override fun onStart() {
//        super.onStart()
////        val bottomSheetBehavior = BottomSheetBehavior.from((binding!!.root))
////        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
//    }
//
//    override fun onRecentImagesSelected() {
//        val size = recentImagesAdapter?.getSelectedImagePath()!!.size
//        if (size > 0) {
//            binding.attachSendButtonTextView.isVisible = true
//            binding.attachSendButtonTextView.setText(
//                String.format(
//                    Locale.getDefault(),
//                    "Send (%d)",
//                    size
//                )
//            )
//            binding.attachSendButtonIcon.setImageResource(R.drawable.ic_send_circle)
//        } else {
//            binding.attachSendButtonTextView.isVisible = false
//            binding.attachSendButtonIcon.setImageResource(R.drawable.ic_down_circle)
//        }
//    }
//
//
//    override fun tooManyFilesSelected() {
//        Toast.makeText(context, "Too_many_files_at_once", Toast.LENGTH_SHORT).show()
//    }
//
//    override fun onClick(p0: View?) {
//        if (p0 != null) {
//            when (p0.id) {
//                R.id.attach_send_button -> {
//                    if (recentImagesAdapter != null) {
//                        val selectedImagePaths = recentImagesAdapter!!.getSelectedImagePaths()
//                        if (selectedImagePaths.isNotEmpty()) {
//                            listener?.onRecentPhotosSend(ArrayList(selectedImagePaths))
//                        }
//                    }
//                }
//                R.id.attach_camera_button -> listener?.onCameraClick()
//                R.id.attach_file_button -> listener?.onFilesClick()
//                R.id.attach_gallery_button -> listener?.onGalleryClick()
//                R.id.attach_location_button -> listener?.onLocationClick()
//            }
//        }
//        dismiss()
//    }


}