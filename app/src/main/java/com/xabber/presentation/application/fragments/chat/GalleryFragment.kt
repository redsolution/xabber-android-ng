package com.xabber.presentation.application.fragments.chat

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentGalleryBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.fragments.chat.GalleryAdapter.Companion.projectionPhotos
import com.xabber.presentation.application.util.isPermissionGranted
import java.io.File

class GalleryFragment: BaseFragment(R.layout.fragment_gallery), GalleryAdapter.Listener {
       private val binding by viewBinding(FragmentGalleryBinding::bind)
 private var galleryAdapter: GalleryAdapter? = null
    private var imagePaths = ArrayList<Uri>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
 val spancount =  3
        galleryAdapter = GalleryAdapter(this)
        Log.d("fff", "$galleryAdapter")
        binding.imagess.layoutManager = GridLayoutManager(context, spancount)
        context?.let { loadGalleryPhotosAlbums() }
        binding.imagess.adapter = galleryAdapter
    }


 private fun loadGalleryPhotosAlbums() {
        context?.contentResolver?.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projectionPhotos,
            null,
            null,
            MediaStore.Images.Media.DATE_TAKEN + " DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    imagePaths.add(contentUri)
                    galleryAdapter?.updateAdapter(imagePaths)
                    galleryAdapter?.notifyDataSetChanged()
                }
            }

        }
    }


     override fun openCamera() {


        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    //    val image: File? = generatePicturePath()
     //   if (image != null) {
     //       takePictureIntent.putExtra(
      //          MediaStore.EXTRA_OUTPUT,
      //          FileManager.getFileUri(image, requireContext())
     //       )
           // currentPhotoPath = image.absolutePath

    //    }
     //   resultLauncher.launch(takePictureIntent)
    }


    override fun onRecentImagesSelected() {
        val animBottom = AnimationUtils.loadAnimation(context, R.anim.to_bottom)
        val animTop = AnimationUtils.loadAnimation(context, R.anim.to_top)
        val size = galleryAdapter?.getSelectedImagePath()!!.size
        if (size > 0) {
//            if (binding.attachScrollBar.isVisible) {
//                binding.attachScrollBar.startAnimation(animBottom)
//                binding.attachScrollBar.isVisible = false
//                binding.inputLayout.isVisible = true
//                binding.inputLayout.startAnimation(animTop)
            }
//            binding.tvCountFiles.text = String.format(
//                Locale.getDefault(),
//                "%d",
//                size
//            )

//        } else {
//
//            binding.inputLayout.startAnimation(animBottom)
//            binding.inputLayout.isVisible = false
//            binding.attachScrollBar.isVisible = true
//            binding.attachScrollBar.startAnimation(animTop)
//        }

    }

    override fun tooManyFilesSelected() {
        Toast.makeText(context, "Too_many_files_at_once", Toast.LENGTH_SHORT).show()

    }

    override fun cameraView(previewCamera: PreviewView, textview: TextView, imageVH: ImageView) {
        if (this.isPermissionGranted(Manifest.permission.CAMERA)) {
            textview.isVisible = false
            imageVH.isVisible = true
            val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
            cameraProviderFuture.addListener(Runnable {
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetRotation(previewCamera.display.rotation).build()
                    val cameraselector =
                        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build()
                    preview.setSurfaceProvider(previewCamera.surfaceProvider)
                    val useCaseGroup = UseCaseGroup.Builder().addUseCase(preview).build()
                    cameraProvider.bindToLifecycle(this, cameraselector, preview)
                } catch (e: Exception) {

                }
            }, ContextCompat.getMainExecutor(requireContext()))

        } else {
            textview.isVisible = true
            imageVH.isVisible = false
        }
    }


}