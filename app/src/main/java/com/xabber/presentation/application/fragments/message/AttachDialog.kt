package com.xabber.presentation.application.fragments.message

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.AttachDialogBinding
import com.xabber.presentation.application.fragments.message.RecentImagesAdapter.Companion.projectionPhotos
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class AttachDialog(private val listener: Listener) : BottomSheetDialogFragment(), RecentImagesAdapter.Listener {
    private var _binding: AttachDialogBinding? = null
    private val binding get() = _binding!!
    private var recentImagesAdapter: RecentImagesAdapter? = null

    interface Listener {
        fun onRecentPhotosSend(paths: HashSet<String>?)
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

        initDialogActions()
        setupImagesRecycler(true)
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
               val selectedImagesPath = recentImagesAdapter?.getSelectedImagePath()
             if (selectedImagesPath != null && selectedImagesPath.size > 0)  listener.onRecentPhotosSend(selectedImagesPath)
               else {
                   dismiss()
             }
           }
        }
    }


   private fun setupImagesRecycler(visibility: Boolean) {
        if (visibility) {
            binding.attachRecentImagesRecyclerView.isVisible = true
            recentImagesAdapter = RecentImagesAdapter(this)

             binding.attachRecentImagesRecyclerView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            context?.let { loadGalleryPhotosAlbums() }
            binding.attachRecentImagesRecyclerView.adapter = recentImagesAdapter
        } else {
            binding.attachRecentImagesRecyclerView.isVisible = false
        }
    }

    private fun loadGalleryPhotosAlbums() {
        val imagePaths = ArrayList<String>()
        context?.contentResolver?.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projectionPhotos,
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    imagePaths.add(contentUri.toString())
                    recentImagesAdapter?.updateAdapter(imagePaths)
                    recentImagesAdapter?.notifyDataSetChanged()
                     Log.d("test", "${contentUri.toString()}")

                }
            }

        }
    }
//
//
//    override fun onStart() {
//        super.onStart()
////        val bottomSheetBehavior = BottomSheetBehavior.from((binding!!.root))
////        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
//    }
//
    override fun onRecentImagesSelected() {
        val size = recentImagesAdapter?.getSelectedImagePath()!!.size
        if (size > 0) {
            binding.attachSendButtonTextView.isVisible = true
            binding.attachSendButtonTextView.setText(
                String.format(
                    Locale.getDefault(),
                    "Send (%d)",
                    size
                )
            )
            binding.attachCheckButtonIcon.setImageResource(R.drawable.ic_send_circle)
        } else {
            binding.attachSendButtonTextView.isVisible = false
           binding.attachCheckButtonIcon.setImageResource(R.drawable.ic_down_circle)
        }
    }


    override fun tooManyFilesSelected() {
        Toast.makeText(context, "Too_many_files_at_once", Toast.LENGTH_SHORT).show()
    }

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