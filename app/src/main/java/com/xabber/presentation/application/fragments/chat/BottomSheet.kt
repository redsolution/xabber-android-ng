package com.xabber.presentation.application.fragments.chat

import android.app.Dialog
import android.content.ContentUris
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import androidx.annotation.NonNull
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.BottomSheetAttachBinding
import com.xabber.presentation.application.activity.ApplicationViewModel
import com.xabber.presentation.application.fragments.chat.GalleryAdapter.Companion.projectionPhotos
import com.xabber.presentation.application.util.dp
import java.util.*

class BottomSheet() : BottomSheetDialogFragment() {
    private var _binding: BottomSheetAttachBinding? = null
    private val binding get() = _binding!!
    private var imagePaths = ArrayList<Uri>()
    private var behavior: BottomSheetBehavior<*>? = null
    private val viewModel: ApplicationViewModel by activityViewModels()
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null

    companion object {
        const val TAG = "BottomSheet"
        fun newInstance() = BottomSheet()
    }


    interface BottomSheetAttachListener {
        fun sendMessage(textMessage: String, imagePaths: HashSet<Uri>?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) childFragmentManager.beginTransaction()
            .replace(R.id.frame, GalleryFragment())
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
        Log.d("fff", "топ ${bottomSheet.top}, хейт ${behavior?.peekHeight}")
        bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(@NonNull view: View, i: Int) {
                if (BottomSheetBehavior.STATE_HIDDEN == i) {
                    dismiss()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                Log.d("fff", "чччч ${bottomSheet.top}")
                if (bottomSheet.top in 1..896) {
                    Log.d(
                        "fff",
                        "behavior?.peekHeight = ${behavior?.peekHeight}, bottomSheet.top = ${bottomSheet.top}"
                    )
                    if (bottomSheet.top in 1..100) {
                        if (bottomSheet.top <= 100) {
//                            binding.appBar.layoutParams.height = 0
//                            binding.appBar.requestLayout()
//                            binding.appBar.isVisible = true
                        }
//                        binding.appBar.layoutParams.height = 200 - bottomSheet.top * 2
//                        binding.appBar.requestLayout()
                    } else {
                        //  binding.appBar.isVisible = false
                    }
                    if (!binding.attachScrollBar.isVisible) {
                        val anim = AnimationUtils.loadAnimation(context, R.anim.to_top)
                        binding.attachScrollBar.isVisible = true
                        binding.attachScrollBar.startAnimation(anim)

                    }
                    binding.attachScrollBar.y =
                        (((bottomSheet.parent as View).height - bottomSheet.top - binding.attachScrollBar.height / 2 - 16).toFloat())
                }
                val anim = AnimationUtils.loadAnimation(context, R.anim.to_bottom)
                if (bottomSheet.top == 0 && binding.attachScrollBar.isVisible) {
                    binding.attachScrollBar.startAnimation(anim)
                    binding.attachScrollBar.isVisible = false
                }
                //    else ((bottomSheet.parent as View).height - bottomSheet.top).toFloat()
                Log.d(
                    "fff",
                    "binding.attachScrollBar.y = ${binding.attachScrollBar.y}; bottomSheet.top = ${bottomSheet.top}"
                )
            }
        }.apply {
            binding.root.post { onSlide(binding.root.parent as View, 0f) }
        }
        behavior?.addBottomSheetCallback(bottomSheetCallback as BottomSheetBehavior.BottomSheetCallback)

        binding.root.minimumHeight = 1200

        val width =
            (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()
        val spancount = if (width > 600) 4 else 3
        //     galleryAdapter = GalleryAdapter(this)
        //      binding.images.layoutManager = GridLayoutManager(context, spancount)
        context?.let { loadGalleryPhotosAlbums() }
        //   binding.images.adapter = galleryAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnSend.setOnClickListener {
//            listener.sendMessage(
//                binding.chatInput.text.toString(),
//              //  galleryAdapter?.getSelectedImagePath()
//            )
            dismiss()
        }
        super.onViewCreated(view, savedInstanceState)
        binding.inputLayout.setOnClickListener {
            binding.chatInput.isFocusable = true
            binding.chatInput.isFocusableInTouchMode = true
            binding.chatInput.requestFocus()
            val inputMethodManager: InputMethodManager =
                context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(binding.chatInput, InputMethodManager.SHOW_IMPLICIT)
        }
        val animBottom = AnimationUtils.loadAnimation(context, R.anim.to_bottom)
        val animTop = AnimationUtils.loadAnimation(context, R.anim.to_top)
        viewModel.selectedImagesCount.observe(viewLifecycleOwner) {
            Log.d("fff", "$it")
            if (it > 0) {
                if (binding.attachScrollBar.isVisible) {
                    binding.attachScrollBar.startAnimation(animBottom)
                    binding.attachScrollBar.isVisible = false

                    binding.inputLayout.isVisible = true
                    binding.inputLayout.startAnimation(animTop)
                }
                binding.tvCountFiles.text = String.format(
                    Locale.getDefault(),
                    "%d",
                    it
                )
                Log.d("fff", " cy${binding.attachScrollBar.isVisible}")
            } else {
                binding.inputLayout.startAnimation(animBottom)
                binding.inputLayout.isVisible = false
                binding.attachScrollBar.isVisible = true
                Log.d("fff", "${binding.attachScrollBar.isVisible}")
                binding.attachScrollBar.startAnimation(animTop)
                Log.d("fff", "${binding.attachScrollBar.isVisible}")
            }
        }
        if (savedInstanceState == null) childFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet_container, GalleryFragment()).commit()
        //   binding.cancelBtn.setOnClickListener { dismiss() }
        binding.attachRadioGroup.setOnCheckedChangeListener { _, i ->
            when (i) {
                R.id.attach_gallery_button -> {
                    if (binding.attachRadioGroup.checkedRadioButtonId == R.id.attach_gallery_button) {
                        // behavior?.state = BottomSheetBehavior.STATE_EXPANDED
                        //   } else {
                        childFragmentManager.beginTransaction()
                            .replace(R.id.bottom_sheet_container, GalleryFragment()).commit()
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
        bottomSheetCallback?.let { behavior?.removeBottomSheetCallback(it) }
        _binding = null
    }

}

