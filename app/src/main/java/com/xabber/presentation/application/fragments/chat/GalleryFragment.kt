package com.xabber.presentation.application.fragments.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ContentUris
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentGalleryBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.ApplicationViewModel
import com.xabber.presentation.application.fragments.chat.GalleryAdapter.Companion.projectionPhotos
import com.xabber.presentation.application.util.askUserForOpeningAppSettings
import com.xabber.presentation.application.util.isPermissionGranted
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GalleryFragment : BaseFragment(R.layout.fragment_gallery), GalleryAdapter.Listener {
    private val binding by viewBinding(FragmentGalleryBinding::bind)
    private var galleryAdapter: GalleryAdapter? = null
    private var imagePaths = ArrayList<Uri>()
    private val viewModel: ApplicationViewModel by activityViewModels()
    private var currentPhotoPath: String? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotCameraPermissionResult
    )

    private val requestExternalStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotExternalStoragePermissionResult
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        requestExternalStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val widthDp =
            (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()
        val spanCount = if (widthDp >= 600f) 4 else 3
        galleryAdapter = GalleryAdapter(this)
        binding.imagess.layoutManager = GridLayoutManager(context, spanCount)

        binding.imagess.adapter = galleryAdapter
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun onGotCameraPermissionResult(granted: Boolean) {
        if (granted) galleryAdapter?.notifyDataSetChanged()
    }

    private fun onGotExternalStoragePermissionResult(granted: Boolean) {
        if (granted) {
            context?.let {
                loadGalleryPhotosAlbums()
            }
        } else {
            galleryAdapter?.showPlug()
        }
    }

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                currentPhotoPath?.let { addMediaToGallery(it) }
            }
        }

    private fun addMediaToGallery(fromPath: String) {
        val newFile = File(fromPath)
        val contentUri = Uri.fromFile(newFile)
        addMediaToGallery(contentUri)
    }

    private fun addMediaToGallery(uri: Uri) {
        try {
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = uri
            activity?.sendBroadcast(mediaScanIntent);
        } catch (e: Exception) {
            Log.d("error", "${e.printStackTrace()}")
        }
    }


    private fun takePhotoFromCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val image: File? = generatePicturePath()
        if (image != null) {
            takePictureIntent.putExtra(
                MediaStore.EXTRA_OUTPUT,
                FileManager.getFileUri(image, requireContext())
            )
            currentPhotoPath = image.absolutePath
        }
        resultLauncher.launch(takePictureIntent)
    }

    private fun generatePicturePath(): File? {
        try {
            val storageDir = getAlbumDir()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            return File(storageDir, "IMG_" + timeStamp + ".jpg")
        } catch (e: java.lang.Exception) {

        }
        return null
    }

    private fun getAlbumDir(): File? {
        var storageDir: File? = null
        if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            storageDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Xabber"
            )
            if (!storageDir.mkdirs()) {
                if (!storageDir.exists()) {
                    return null
                }
            }
        } else {

        }
        return storageDir
    }


    @SuppressLint("NotifyDataSetChanged")
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
                }
            }
            galleryAdapter?.updateAdapter(imagePaths)
            galleryAdapter?.notifyDataSetChanged()
        }
    }


    override fun clickCameraPreview() {
        if (isPermissionGranted(Manifest.permission.CAMERA)) {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val image: File? = generatePicturePath()
            if (image != null) {
                takePictureIntent.putExtra(
                    MediaStore.EXTRA_OUTPUT,
                    FileManager.getFileUri(image, requireContext())
                )
                currentPhotoPath = image.absolutePath
            }
            resultLauncher.launch(takePictureIntent)
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            if (!isPermissionGranted(Manifest.permission.CAMERA)) askUserForOpeningAppSettings()
        }
    }


    override fun onRecentImagesSelected() {
        val animBottom = AnimationUtils.loadAnimation(context, R.anim.to_bottom)
        val animTop = AnimationUtils.loadAnimation(context, R.anim.to_top)
        val size = galleryAdapter?.getSelectedImagePath()!!.size
        viewModel.setSelectedImagesCount(size)
    }

    override fun tooManyFilesSelected() {
        Toast.makeText(context, "Too_many_files_at_once", Toast.LENGTH_SHORT).show()

    }

    override fun cameraView(previewCamera: PreviewView, textView: TextView, imageView: ImageView) {
        if (this.isPermissionGranted(Manifest.permission.CAMERA)) {
            previewCamera.isVisible = true
            textView.isVisible = false
            imageView.isVisible = true
            val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
            cameraProviderFuture.addListener(Runnable {
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetRotation(previewCamera.display.rotation).build()
                    val cameraSelector =
                        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build()
                    preview.setSurfaceProvider(previewCamera.surfaceProvider)
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview)
                } catch (e: Exception) {
                    Log.d("error", "${e.printStackTrace()}")
                }
            }, ContextCompat.getMainExecutor(requireContext()))

        } else {
            previewCamera.isVisible = false
            textView.isVisible = true
            imageView.isVisible = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.setSelectedImagesCount(0)
    }


}