package com.xabber.presentation.application.fragments.chat

import android.Manifest.permission
import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.ContentUris
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.BottomSheetAttachBinding
import com.xabber.presentation.application.fragments.chat.GalleryAdapter.Companion.projectionPhotos
import com.xabber.presentation.application.util.dp
import com.xabber.presentation.application.util.isPermissionGranted
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BottomSheet(private val listener: BottomSheetAttachListener) : BottomSheetDialogFragment()
    {
    private var _binding: BottomSheetAttachBinding? = null
    private val binding get() = _binding!!
//    private var galleryAdapter: GalleryAdapter? = null
    private var imagePaths = ArrayList<Uri>()
    private var behavior: BottomSheetBehavior<View>? = null
    lateinit var currentPhotoPath: String

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotCameraPermissionResult
    )

    private val requestExternalStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotExternalStoragePermissionResult
    )

    interface BottomSheetAttachListener {
        fun sendMessage(textMessage: String, imagePaths: HashSet<Uri>?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.beginTransaction().replace(R.id.frame, GalleryFragment())
        setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAttachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { setupBottomSheet(it) }
        return dialog
    }

    private fun onGotCameraPermissionResult(granted: Boolean) {

    }

    private fun onGotExternalStoragePermissionResult(granted: Boolean) {
    }

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            var bitmap: Bitmap? = null
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null) {
                    bitmap = data?.extras?.get("data") as Bitmap
                }
                addMediaToGallery(currentPhotoPath, bitmap)
            }
        }


    private fun addMediaToGallery(fromPath: String, bitmap: Bitmap? = null) {
        if (fromPath == null) {
            return
        }
        val f = File(fromPath)
        //  miniatures.add(FileDto(f, bitmap))
        val contentUri = Uri.fromFile(f)
        addMediaToGallery(contentUri)
    }

    private fun addMediaToGallery(uri: Uri) {
        if (uri == null) {
            return
        }
        try {
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.setData(uri)
            activity?.sendBroadcast(mediaScanIntent);
        } catch (e: Exception) {
            Log.d("data", "error")
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
            //   currentPhotoPath = image.absolutePath
        }
        resultLauncher.launch(takePictureIntent)
    }

    private fun generatePicturePath(): File? {
        try {
            // val storageDir = activity?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
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

    private fun setupBottomSheet(dialogInterface: DialogInterface) {
        requestCameraPermissionLauncher.launch(permission.CAMERA)
        requestExternalStoragePermissionLauncher.launch(permission.WRITE_EXTERNAL_STORAGE)

        binding.btnSend.setOnClickListener {
//            listener.sendMessage(
//                binding.chatInput.text.toString(),
//              //  galleryAdapter?.getSelectedImagePath()
//            )
//            dismiss()
        }
        val heightDp =
            (Resources.getSystem().displayMetrics.heightPixels / Resources.getSystem().displayMetrics.density).toInt()

        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
            ?: return
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(
            bottomSheet
        )
        behavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
        behavior.peekHeight = heightDp / 100 * 60.dp
        behavior.setBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(@NonNull view: View, i: Int) {
                if (BottomSheetBehavior.STATE_EXPANDED == i) {
                    binding.appBarLayout.isVisible = true
                    binding.swipe.isVisible = false
                }
                if (BottomSheetBehavior.STATE_COLLAPSED == i) {

                    //  binding.attachScrollBar.isVisible = true
                    binding.appBarLayout.isVisible = false
                    binding.swipe.isVisible = true
                }
                if (BottomSheetBehavior.STATE_HIDDEN == i) {
                    dismiss()
                }
            }

            override fun onSlide(@NonNull view: View, v: Float) {}
        })
        binding.root.minimumHeight = 1200

        val width =
            (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()
        val spancount = if (width > 600) 4 else 3
   //     galleryAdapter = GalleryAdapter(this)
        binding.images.layoutManager = GridLayoutManager(context, spancount)
        context?.let { loadGalleryPhotosAlbums() }
     //   binding.images.adapter = galleryAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chatInput.setOnClickListener { }
        binding.cancelBtn.setOnClickListener { dismiss() }
        binding.attachRadioGroup.setOnCheckedChangeListener { _, i ->
            when (i) {
                R.id.attach_gallery_button -> {
                    Log.d("fff", "${binding.attachRadioGroup.checkedRadioButtonId == R.id.attach_gallery_button}")
                    if (binding.attachRadioGroup.checkedRadioButtonId == R.id.attach_gallery_button) {
                       // behavior?.state = BottomSheetBehavior.STATE_EXPANDED
                 //   } else {
                        childFragmentManager.beginTransaction()
                        .replace(R.id.bottom_sheet_container, GalleryFragment()).commit()
                        Log.d("fff", "load")
                    }
                }
                R.id.attach_file_button -> {
                    childFragmentManager.beginTransaction()
                        .replace(R.id.bottom_sheet_container, FileFragment()).commit()
                }
                R.id.attach_location_button -> {
                }
            }
        }
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
                  //  galleryAdapter?.updateAdapter(imagePaths)
                  //  galleryAdapter?.notifyDataSetChanged()
                }
            }

        }
    }


//    override fun openCamera() {
//
//
//        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//        val image: File? = generatePicturePath()
//        if (image != null) {
//            takePictureIntent.putExtra(
//                MediaStore.EXTRA_OUTPUT,
//                FileManager.getFileUri(image, requireContext())
//            )
//            currentPhotoPath = image.absolutePath
//
//        }
//        resultLauncher.launch(takePictureIntent)
//    }
//
//
//    override fun onRecentImagesSelected() {
//        val animBottom = AnimationUtils.loadAnimation(context, R.anim.to_bottom)
//        val animTop = AnimationUtils.loadAnimation(context, R.anim.to_top)
//        val size = 0
//          //  galleryAdapter?.getSelectedImagePath()!!.size
//        if (size > 0) {
//            if (binding.attachScrollBar.isVisible) {
//                binding.attachScrollBar.startAnimation(animBottom)
//                binding.attachScrollBar.isVisible = false
//                binding.inputLayout.isVisible = true
//                binding.inputLayout.startAnimation(animTop)
//            }
//            binding.tvCountFiles.text = String.format(
//                Locale.getDefault(),
//                "%d",
//                size
//            )
//
//        } else {
//
//            binding.inputLayout.startAnimation(animBottom)
//            binding.inputLayout.isVisible = false
//            binding.attachScrollBar.isVisible = true
//            binding.attachScrollBar.startAnimation(animTop)
//        }
//
//    }
//
//    override fun tooManyFilesSelected() {
//        Toast.makeText(context, "Too_many_files_at_once", Toast.LENGTH_SHORT).show()
//
//    }
//
//    override fun cameraView(previewCamera: PreviewView, textview: TextView, imageVH: ImageView) {
//        if (this.isPermissionGranted(permission.CAMERA)) {
//            textview.isVisible = false
//            imageVH.isVisible = true
//            val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
//            cameraProviderFuture.addListener(Runnable {
//                try {
//                    val cameraProvider = cameraProviderFuture.get()
//                    val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
//                        .setTargetRotation(previewCamera.display.rotation).build()
//                    val cameraselector =
//                        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
//                            .build()
//                    preview.setSurfaceProvider(previewCamera.surfaceProvider)
//                    val useCaseGroup = UseCaseGroup.Builder().addUseCase(preview).build()
//                    cameraProvider.bindToLifecycle(this, cameraselector, preview)
//                } catch (e: Exception) {
//
//                }
//            }, ContextCompat.getMainExecutor(requireContext()))
//
//        } else {
//            textview.isVisible = true
//            imageVH.isVisible = false
//        }
//    }


    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        requestCameraPermissionLauncher.unregister()
        requestExternalStoragePermissionLauncher.unregister()
    }

}

