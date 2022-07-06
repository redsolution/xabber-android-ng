package com.xabber.presentation.application.fragments.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ContentUris
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
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
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.coordinatorlayout.widget.CoordinatorLayout
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
import com.xabber.presentation.application.util.*
import com.xabber.presentation.application.util.AppConstants.IMAGE_PICK_REQUEST_CODE
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AttachBottomSheet(private val onSend: (text: String, ArrayList<File>) -> Unit) :
    BottomSheetDialogFragment(), GalleryAdapter.Listener {
    private var _binding: BottomSheetAttachBinding? = null
    private val binding get() = _binding!!
    private var behavior: BottomSheetBehavior<*>? = null
    private var galleryAdapter: GalleryAdapter? = null
    private var imagePaths = ArrayList<Uri>()
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private var currentPhotoPath: String? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotCameraPermissionResult
    )

    private val requestExternalStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotExternalStoragePermissionResult
    )

    private val cameraResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                currentPhotoPath?.let {
                    addMediaToGallery(it)
                    val f = File(currentPhotoPath.toString())
                    Log.d("ooo", "$f, $currentPhotoPath")
                }
            }
        }

    companion object {
        const val TAG = "BottomSheet"
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogStyle)
        _binding = BottomSheetAttachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { setupBottomSheet(it) }
        return dialog
    }

    private fun setupBottomSheet(dialogInterface: DialogInterface) {
        val heightDp =
            (Resources.getSystem().displayMetrics.heightPixels / Resources.getSystem().displayMetrics.density).toInt()

        val bottomSheetDialog = dialogInterface as BottomSheetDialog
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
            ?: return
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        behavior = BottomSheetBehavior.from(
            bottomSheet
        )
        behavior?.peekHeight = heightDp / 100 * 60.dp
        bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(@NonNull view: View, i: Int) {
                if (BottomSheetBehavior.STATE_HIDDEN == i) {
                    dismiss()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {

//                    if (bottomSheet.top in 1..100) {
//                        if (bottomSheet.top <= 100) {
////                            binding.appBar.layoutParams.height = 0
////                            binding.appBar.requestLayout()
////                            binding.appBar.isVisible = true
//                        }
////                        binding.appBar.layoutParams.height = 200 - bottomSheet.top * 2
////                        binding.appBar.requestLayout()
//                    } else {
//                        //  binding.appBar.isVisible = false
//                    }
//                    if (!binding.attachScrollBar.isVisible && !binding.groupSend.isVisible) {
////                        val anim = AnimationUtils.loadAnimation(context, R.anim.to_top)
////                        binding.attachScrollBar.isVisible = true
////                        binding.attachScrollBar.startAnimation(anim)
//
//                    }
                if (bottomSheet.top <= 896) {
                    binding.frameLayoutActionContainer.y =
                        (((bottomSheet.parent as View).height - bottomSheet.top - binding.frameLayoutActionContainer.height / 2 - 16).toFloat())
                }
            }
//                val anim = AnimationUtils.loadAnimation(context, R.anim.to_bottom)
//                if (bottomSheet.top == 0 && binding.attachScrollBar.isVisible) {
//                    binding.attachScrollBar.startAnimation(anim)
//                    binding.attachScrollBar.isVisible = false
//                }
        }.apply {
            binding.root.post { onSlide(binding.root.parent as View, 0f) }
        }
        behavior?.addBottomSheetCallback(bottomSheetCallback as BottomSheetBehavior.BottomSheetCallback)

        binding.root.minimumHeight = 1200
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        requestExternalStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        initGalleryRecyclerView()
        initBottomNavigationBar()
        initInputLayout()
        binding.chatInput.setOnFocusChangeListener { _, hasFocused ->
            when {
                hasFocused ->
                    behavior?.removeBottomSheetCallback(bottomSheetCallback as BottomSheetBehavior.BottomSheetCallback)
                else ->
                    behavior?.addBottomSheetCallback(bottomSheetCallback as BottomSheetBehavior.BottomSheetCallback)
            }
        }
        binding.btnSend.setOnClickListener {
            val messageText = binding.chatInput.text.toString()
            val result = Bundle().apply { putString("message_text", messageText) }
            setFragmentResult("y", result)
            val images = ArrayList<File>()
            val paths = galleryAdapter?.getSelectedImagePaths()
            for (path in paths!!) {
                val file = File(path.path)
                images.add(file)
            }
            onSend(binding.chatInput.text.toString().trim(), images)
            dismiss()
        }
        super.onViewCreated(view, savedInstanceState)
        binding.chatInput.setOnClickListener {
            binding.chatInput.isFocusable = true
            binding.chatInput.isFocusableInTouchMode = true
            binding.chatInput.requestFocus()
            val inputMethodManager: InputMethodManager =
                context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(binding.chatInput, InputMethodManager.SHOW_IMPLICIT)
        }
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

    private fun initGalleryRecyclerView() {
        val width =
            (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()
        val spanCount = if (width > 600) 4 else 3
        galleryAdapter = GalleryAdapter(this)
        binding.recentImages.layoutManager = GridLayoutManager(context, spanCount)
        binding.recentImages.adapter = galleryAdapter
        loadGalleryPhotosAlbums()
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
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                imagePaths.add(contentUri)
            }
            galleryAdapter?.updateAdapter(imagePaths)
            galleryAdapter?.notifyDataSetChanged()
        }
    }

    private fun initBottomNavigationBar() {
        with(binding) {
            attachGalleryButton.setOnClickListener {
                if (isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) openGallery()
                else requestExternalStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            attachFileButton.setOnClickListener {
                if (isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) openFiles()
                else requestExternalStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            attachLocationButton.setOnClickListener { openLocation() }
        }

    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_REQUEST_CODE)
    }

    private fun openFiles() {
        val intent =
            Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, AppConstants.SELECT_FILE_REQUEST_CODE)
    }

    private fun openLocation() {
        Toast.makeText(context, "This feature has not been implemented yet", Toast.LENGTH_SHORT)
            .show()

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
        }
        return storageDir
    }

    private fun initInputLayout() {

    }

    override fun onRecentImagesSelected() {
        val j = resources.getDimension(com.google.android.material.R.dimen.action_bar_size)
        val selectedImagesCount = if (galleryAdapter?.getSelectedImagePaths() != null) {
            galleryAdapter?.getSelectedImagePaths()!!.size
        } else 0
        val minTop = AnimationUtils.loadAnimation(context, R.anim.min_top)
        val animBottom = AnimationUtils.loadAnimation(context, R.anim.to_bottom)
        val minBottom = AnimationUtils.loadAnimation(context, R.anim.min_bottom)
        val animTop = AnimationUtils.loadAnimation(context, R.anim.to_top)
        val animRight = AnimationUtils.loadAnimation(context, R.anim.to_right)
        val app = AnimationUtils.loadAnimation(context, R.anim.appearance)
        val dis = AnimationUtils.loadAnimation(context, R.anim.disappearance)
        val animLeft = AnimationUtils.loadAnimation(context, R.anim.to_left)
        if (selectedImagesCount > 0) {
            if (binding.attachScrollBar.isVisible) {
                binding.frameLayoutActionContainer.startAnimation(minBottom)
                val params = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT, 150.dp
                )
                binding.frameLayoutActionContainer.layoutParams = params
                binding.frameLayoutActionContainer.requestLayout()
                binding.attachScrollBar.startAnimation(dis)
                binding.attachScrollBar.isVisible = false
                binding.attachGalleryButton.isEnabled = false
                binding.attachFileButton.isEnabled = false
                binding.attachLocationButton.isEnabled = false
                binding.chatInput.isEnabled = true
                binding.chatInput.isClickable = true
                binding.chatInput.elevation = 10f
                binding.frameLayoutActionContainer.setOnClickListener {
                    binding.chatInput.isFocusable = true
                    binding.chatInput.isFocusableInTouchMode = true
                    binding.chatInput.requestFocus()
                    val inputMethodManager: InputMethodManager =
                        context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.showSoftInput(
                        binding.chatInput,
                        InputMethodManager.SHOW_IMPLICIT
                    )
                }

                binding.groupSend.isVisible = true
                binding.send.isVisible = true
                binding.btnSend.isVisible = true
                binding.groupSend.startAnimation(app)
                binding.send.startAnimation(animLeft)
                //   binding.inputLayout.startAnimation(animTop)
            }
            binding.tvCountFiles.text = String.format(
                Locale.getDefault(),
                "%d",
                selectedImagesCount
            )
        } else {
            if (binding.groupSend.isVisible) {
                binding.groupSend.startAnimation(dis)
                binding.send.startAnimation(animRight)
                binding.groupSend.isVisible = false
                binding.send.isVisible = false
                binding.btnSend.isVisible = false
                binding.frameLayoutActionContainer.startAnimation(minTop)
                val params = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT, 150.dp
                )
                binding.frameLayoutActionContainer.layoutParams = params
                binding.attachScrollBar.isVisible = true
                binding.attachScrollBar.startAnimation(app)
                binding.attachGalleryButton.isEnabled = true
                binding.attachFileButton.isEnabled = true
                binding.attachLocationButton.isEnabled = true
                binding.chatInput.isEnabled = false
                // binding.chatInput.elevation = 0f
            }
        }
    }

    override fun tooManyFilesSelected() {
        Toast.makeText(context, "Too_many_files_at_once", Toast.LENGTH_SHORT).show()
    }

    override fun cameraView(previewCamera: PreviewView, textView: TextView, imageView: ImageView) {
        if (this.isPermissionGranted(Manifest.permission.CAMERA)) {
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

                }
            }, ContextCompat.getMainExecutor(requireContext()))

        } else {
            textView.isVisible = true
            imageView.isVisible = false
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
            cameraResultLauncher.launch(takePictureIntent)
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            if (!isPermissionGranted(Manifest.permission.CAMERA)) askUserForOpeningAppSettings()
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


    override fun onDestroy() {
        super.onDestroy()
        galleryAdapter = null
        bottomSheetCallback?.let { behavior?.removeBottomSheetCallback(it) }
        _binding = null
    }

}

