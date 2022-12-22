package com.xabber.presentation.application.fragments.chat.attach

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
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.BottomSheetAttachBinding
import com.xabber.presentation.application.fragments.chat.FileManager
import com.xabber.presentation.application.fragments.chat.FileManager.Companion.getFileUri
import com.xabber.presentation.application.fragments.chat.GalleryAdapter
import com.xabber.presentation.application.fragments.chat.GalleryAdapter.Companion.projectionPhotos
import com.xabber.presentation.application.fragments.chat.geo.PickGeolocationActivity
import com.xabber.presentation.application.fragments.chat.geo.PickGeolocationActivity.Companion.LAT_RESULT
import com.xabber.presentation.application.fragments.chat.geo.PickGeolocationActivity.Companion.LON_RESULT
import com.xabber.utils.askUserForOpeningAppSettings
import com.xabber.utils.isPermissionGranted
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AttachBottomSheet : BottomSheetDialogFragment(), GalleryAdapter.Listener {
    private var _binding: BottomSheetAttachBinding? = null
    private val binding get() = _binding!!
    private var behavior: BottomSheetBehavior<*>? = null
    private var galleryAdapter: GalleryAdapter? = null
    private var imagePaths = ArrayList<Uri>()
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private var currentPhotoUri: Uri? = null
    private var buttonLayoutParams: ConstraintLayout.LayoutParams? = null
    var collapsedMargin = 0
    var buttonHeight = 0
    var expandedHeight = 0

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(), ::onGotCameraPermissionResult
    )

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotGalleryPermissionResult
    )

    private val cameraResultLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture(), ::onSaveImage
    )

    private val galleryResultLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
        ::onTakePictureFromGallery
    )

    private val fileResultLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
        ::onTakePictureFromFile
    )

    private fun onTakePictureFromGallery(result: Uri?) {
        if (result != null) {
            val list = HashSet<String>()
            list.add(result.toString())
            //   onSend("", list)
            dismiss()
        }
    }

    private fun onTakePictureFromFile(result: Uri?) {
        if (result != null) {
            val list = HashSet<String>()
            list.add(result.toString())
            //   onSend("", list)
            dismiss()
        }
    }

    private fun onSaveImage(result: Boolean) {
        if (result) {
            if (currentPhotoUri != null) {
                val list = HashSet<String>()
                list.add(currentPhotoUri.toString())
            }
            dismiss()
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        //  setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogStyle)
        _binding = BottomSheetAttachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface: DialogInterface -> setupRatio(dialogInterface as BottomSheetDialog) }
        (dialog as BottomSheetDialog).behavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset > 0) //Sliding happens from 0 (Collapsed) to 1 (Expanded) - if so, calculate margins
                    buttonLayoutParams!!.topMargin =
                        ((expandedHeight - buttonHeight - collapsedMargin) * slideOffset + collapsedMargin).toInt() else  //If not sliding above expanded, set initial margin
                    buttonLayoutParams!!.topMargin = collapsedMargin
                binding.frameLayoutActionContainer.layoutParams =
                    buttonLayoutParams //Set layout params to button (margin from top)
            }
        })
        return dialog
    }


    private fun setupRatio(bottomSheetDialog: BottomSheetDialog) {
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        behavior = BottomSheetBehavior.from(
            bottomSheet
        )

        buttonLayoutParams =
            binding.frameLayoutActionContainer.layoutParams as ConstraintLayout.LayoutParams

        //Retrieve bottom sheet parameters
        BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_COLLAPSED
        val bottomSheetLayoutParams = bottomSheet.layoutParams
        bottomSheetLayoutParams.height = bottomSheetDialogDefaultHeight
        expandedHeight = bottomSheetLayoutParams.height
        val peekHeight =
            (expandedHeight / 1.6).toInt() //Peek height to 70% of expanded height (Change based on your view)

        //Setup bottom sheet
        bottomSheet.layoutParams = bottomSheetLayoutParams
        BottomSheetBehavior.from(bottomSheet).skipCollapsed = false
        BottomSheetBehavior.from(bottomSheet).peekHeight = peekHeight
        BottomSheetBehavior.from(bottomSheet).isHideable = true


        //Calculate button margin from top
        buttonHeight =
            binding.frameLayoutActionContainer.height  //How tall is the button + experimental distance from bottom (Change based on your view)
        collapsedMargin = peekHeight - buttonHeight //Button margin in bottom sheet collapsed state
        buttonLayoutParams!!.topMargin = collapsedMargin
        binding.frameLayoutActionContainer.layoutParams = buttonLayoutParams

        //OPTIONAL - Setting up margins
//        val recyclerLayoutParams =
//            binding.recentImages.layoutParams as ConstraintLayout.LayoutParams
//        val k =
//            (buttonHeight - 60) / buttonHeight.toFloat() //60 is amount that you want to be hidden behind button
//        recyclerLayoutParams.bottomMargin =
//            (k * buttonHeight).toInt() //Recyclerview bottom margin (from button)
//        binding.recentImages.layoutParams = recyclerLayoutParams
    }


    private val bottomSheetDialogDefaultHeight: Int
        get() = windowHeight * 90 / 100

    //Calculates window height for fullscreen use
    private val windowHeight: Int
        get() {
            val displayMetrics = DisplayMetrics()
            (requireContext() as Activity).windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.heightPixels
        }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initGalleryRecyclerView()
        initBottomNavigationBar()
        initInputLayout()
//        binding.btnSend.setOnClickListener {
//            val messageText = binding.chatInput.text.toString()
//            val result = Bundle().apply { putString("message_text", messageText) }
//            setFragmentResult("y", result)
//            val images = HashSet<String>()
//            val paths = galleryAdapter?.getSelectedImagePaths()
//            for (path in paths!!) {
//                images.add(path.toString())
//            }
//            //   onSend(binding.chatInput.text.toString().trim(), images)
//            dismiss()
//        }
        super.onViewCreated(view, savedInstanceState)
//        binding.chatInput.setOnClickListener {
//            binding.chatInput.isFocusable = true
//            binding.chatInput.isFocusableInTouchMode = true
//            binding.chatInput.requestFocus()
//            val inputMethodManager: InputMethodManager =
//                context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//            inputMethodManager.showSoftInput(binding.chatInput, InputMethodManager.SHOW_IMPLICIT)
//        }
    }

    private fun onGotCameraPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value }) {
            takePhotoFromCamera()
        } else askUserForOpeningAppSettings()
    }

    private fun openCamera() {
        val image: File? = FileManager.generatePicturePath()
        if (image != null) {
            currentPhotoUri = getFileUri(image, requireContext())
            cameraResultLauncher.launch(currentPhotoUri)
        }
    }

    private fun onGotGalleryPermissionResult(granted: Boolean) {
        if (granted) openGallery()
        else askUserForOpeningAppSettings()
    }

    private fun initGalleryRecyclerView() {
        val width =
            (Resources.getSystem().displayMetrics.widthPixels / Resources.getSystem().displayMetrics.density).toInt()
        val spanCount = if (width > 600) 4 else 3
        galleryAdapter = GalleryAdapter(this)
        binding.recentImages.setHasFixedSize(true)
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
            attachCameraButton.setOnClickListener {
                if (isPermissionGranted(Manifest.permission.CAMERA) && isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    dismiss()
                    openCamera()
                } else {
                    requestCameraPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }
            attachGalleryButton.setOnClickListener {
                if (isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    dismiss()
                    openGallery()
                } else requestGalleryPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            attachFileButton.setOnClickListener {
                if (isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    dismiss()
                    openFiles()
                } else requestGalleryPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            attachLocationButton.setOnClickListener {
                dismiss()
                openLocation()
            }
        }

    }

    private fun openGallery() {
        galleryResultLauncher.launch("image/*")
    }

    private fun openFiles() {
        fileResultLauncher.launch(arrayOf("image/*"))
    }

    private fun openLocation() {
        val intent = Intent(requireContext(), PickGeolocationActivity::class.java)
        startActivityForResult(
            intent,
            PICK_LOCATION_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PICK_LOCATION_REQUEST_CODE -> {
                val lon = data?.getDoubleExtra(LON_RESULT, 0.0)
                val lat = data?.getDoubleExtra(LAT_RESULT, 0.0)
                sendGeolocation(lon, lat)
            }
        }
        dismiss()
    }


    fun sendGeolocation(lon: Double?, lat: Double?) {
        sendMessage(lon, lat)
    }


    fun sendMessage(lon: Double? = null, lat: Double? = null) {

//         if (lon != null && lat != null) {
//             MessageManager.getInstance().sendGeolocationMessage(
//                 accountJid, contactJid, text, markupText, lon, lat
//             )
//         } else {
//             MessageManager.getInstance().sendMessage(accountJid, contactJid, text, markupText)
//         }
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
        //   val j = resources.getDimension(com.google.android.material.R.dimen.action_bar_size)
        val selectedImagesCount = if (galleryAdapter?.getSelectedImagePaths() != null) {
            galleryAdapter?.getSelectedImagePaths()!!.size
        } else 0
        val minTop = AnimationUtils.loadAnimation(context, R.anim.slide_top)
        val animBottom = AnimationUtils.loadAnimation(context, R.anim.to_bottom)
        val minBottom = AnimationUtils.loadAnimation(context, R.anim.slide_bottom)
        val animTop = AnimationUtils.loadAnimation(context, R.anim.to_top)
        val animRight = AnimationUtils.loadAnimation(context, R.anim.to_right)
        val app = AnimationUtils.loadAnimation(context, R.anim.appearance)
        val dis = AnimationUtils.loadAnimation(context, R.anim.disappearance)
        val animLeft = AnimationUtils.loadAnimation(context, R.anim.to_left)
        if (selectedImagesCount > 0) {
            if (binding.attachScrollBar.isVisible) {
                binding.frameLayoutActionContainer.startAnimation(minBottom)
                binding.attachScrollBar.startAnimation(dis)
                binding.attachScrollBar.isVisible = false
                binding.attachGalleryButton.isEnabled = false
                binding.attachFileButton.isEnabled = false
                binding.attachLocationButton.isEnabled = false
                binding.inputPanel.isVisible = true
                binding.chatInput.isEnabled = true
                binding.chatInput.isClickable = true
                binding.chatInput.elevation = 10f
              binding.chatInput.isVisible = true
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
//
             //   binding.groupSend.isVisible = true
                binding.send.isVisible = true
           //   binding.btnSend.isVisible = true
             //    binding.groupSend.startAnimation(app)
                binding.send.startAnimation(animLeft)
              //   binding.inputLayout.startAnimation(animTop)
            }
       //         binding.badge.setText(String.format(Locale.getDefault(),"Send (%d)", selectedImagesCount));
//            binding.tvCountFiles.text = String.format(
//                Locale.getDefault(),
//                "%d",
//                selectedImagesCount
//            )
        } else {
            if (binding.inputPanel.isVisible) {
                binding.inputPanel.startAnimation(dis)
                binding.send.startAnimation(animRight)
                binding.inputPanel.isVisible = false
                binding.send.isVisible = false
              //  binding.btnSend.isVisible = false
              binding.frameLayoutActionContainer.startAnimation(minTop)
//                val params = CoordinatorLayout.LayoutParams(
//                    CoordinatorLayout.LayoutParams.MATCH_PARENT, 150.dp
//                )
//                binding.frameLayoutActionContainer.layoutParams = params
                binding.attachScrollBar.isVisible = true
                binding.attachScrollBar.startAnimation(app)
                binding.attachGalleryButton.isEnabled = true
                binding.attachFileButton.isEnabled = true
                binding.attachLocationButton.isEnabled = true
                binding.chatInput.isEnabled = false
                binding.chatInput.isVisible = false
                // binding.chatInput.elevation = 0f
            }
        }
    }


    override fun tooManyFilesSelected() {
        Toast.makeText(context, "Too_many_files_at_once", Toast.LENGTH_SHORT).show()
    }


    private fun takePhotoFromCamera() {
        val image: File? = FileManager.generatePicturePath()
        if (image != null) {
            currentPhotoUri = getFileUri(image, requireContext())
            cameraResultLauncher.launch(currentPhotoUri)
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
            activity?.sendBroadcast(mediaScanIntent)
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

    companion object {
        const val TAG = "BottomSheet"
        const val PICK_LOCATION_REQUEST_CODE = 10
    }

}
