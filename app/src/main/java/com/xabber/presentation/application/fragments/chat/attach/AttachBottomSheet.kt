package com.xabber.presentation.application.fragments.chat.attach

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import com.aghajari.emojiview.AXEmojiManager
import com.aghajari.emojiview.googleprovider.AXGoogleEmojiProvider
import com.aghajari.emojiview.view.AXSingleEmojiView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.BottomSheetAttachBinding
import com.xabber.models.dto.MessageDto
import com.xabber.models.xmpp.messages.MessageDisplayType
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.chat.*
import com.xabber.presentation.application.fragments.chat.FileManager.getFileUri
import com.xabber.presentation.application.fragments.chat.GalleryAdapter.Companion.projectionPhotos
import com.xabber.presentation.application.fragments.chat.geo.PickGeolocationActivity
import com.xabber.presentation.application.fragments.chat.geo.PickGeolocationActivity.Companion.LAT_RESULT
import com.xabber.presentation.application.fragments.chat.geo.PickGeolocationActivity.Companion.LON_RESULT
import com.xabber.utils.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class AttachBottomSheet : BottomSheetDialogFragment(), GalleryAdapter.Listener {
    private var _binding: BottomSheetAttachBinding? = null
    private val binding get() = _binding!!
    private var behavior: BottomSheetBehavior<*>? = null
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private val viewModel: ChatViewModel by viewModels()
    private var galleryAdapter: GalleryAdapter? = null
    private var imagePaths = ArrayList<Uri>()
    private var currentPhotoUri: Uri? = null
    private var buttonLayoutParams: ConstraintLayout.LayoutParams? = null
    private var collapsedMargin = 0
    private var buttonHeight = 0
    private var expandedHeight = 0

    companion object {
        fun newInstance(params: ChatParams): AttachBottomSheet {
            val arguments = Bundle().apply {
                putParcelable(AppConstants.CHAT_PARAMS, params)
            }
            val fragment = AttachBottomSheet()
            fragment.arguments = arguments
            return fragment
        }

        const val TAG = "BottomSheet"
        const val PICK_LOCATION_REQUEST_CODE = 10
    }

    private fun getParams(): ChatParams =
        requireArguments().parcelable(AppConstants.CHAT_PARAMS)!!

    private val windowHeight: Int
        get() {
            val displayMetrics = DisplayMetrics()
            (requireContext() as Activity).windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.heightPixels
        }

    private val bottomSheetDialogDefaultHeight: Int
        get() = windowHeight * 90 / 100

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(), ::onGotCameraPermissionResult
    )

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotGalleryPermissionResult
    )

    private val cameraResultLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) sendMessageWithAttachment(currentPhotoUri!!) else dismiss()
    }

    private val galleryResultLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) sendMessageWithAttachment(uri) else dismiss()
    }

    private val fileResultLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) sendMessageWithAttachment(uri) else dismiss()
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
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface: DialogInterface -> setupRatio(dialogInterface as BottomSheetDialog) }
        (dialog as BottomSheetDialog).behavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset > 0)                         //Sliding happens from 0 (Collapsed) to 1 (Expanded) - if so, calculate margins
                    buttonLayoutParams!!.topMargin =
                        ((expandedHeight - buttonHeight - collapsedMargin) * slideOffset + collapsedMargin).toInt() else  //If not sliding above expanded, set initial margin
                    buttonLayoutParams!!.topMargin = collapsedMargin
                binding.frameLayoutActionContainer.layoutParams =
                    buttonLayoutParams                      //Set layout params to button (margin from top)
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

        bottomSheet.updateLayoutParams {
            this.width = resources.getDimension(R.dimen.width).toInt()

        }
        buttonLayoutParams =
            binding.frameLayoutActionContainer.layoutParams as ConstraintLayout.LayoutParams

        //Retrieve bottom sheet parameters
        BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_COLLAPSED
        val bottomSheetLayoutParams = bottomSheet.layoutParams
        bottomSheetLayoutParams.height = bottomSheetDialogDefaultHeight
        expandedHeight = bottomSheetLayoutParams.height
        val peekHeight =
            (expandedHeight / 1.6).toInt()

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
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initGalleryRecyclerView()
        initBottomNavigationBar()
        initInputLayout()
        initEmojiButton()
        initButtonSend()
    }

    private fun initGalleryRecyclerView() {
        galleryAdapter = GalleryAdapter(this)
        binding.recentImages.adapter = galleryAdapter
        loadGalleryPhotosAlbums()
    }

    private fun loadGalleryPhotosAlbums() {
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID
        )
        val videoQueryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val videoCursor = activity?.contentResolver?.query(
            videoQueryUri,
            videoProjection,
            null,
            null,
            MediaStore.Video.Media.DATE_TAKEN + " DESC"
        )
        videoCursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri =
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                imagePaths.add(contentUri)
            }
        }


        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID
        )
        val imageQueryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imageCursor = activity?.contentResolver?.query(
            imageQueryUri,
            imageProjection,
            null,
            null,
            MediaStore.Images.Media.DATE_TAKEN + " DESC"
        )
        imageCursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
               imagePaths.add(contentUri)
            }
        }
galleryAdapter?.updateAdapter(imagePaths)





//            context?.contentResolver?.query(
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//            projectionPhotos,
//            null,
//            null,
//            MediaStore.Images.Media.DATE_TAKEN + " DESC"
//        )?.use { cursor ->
//            while (cursor.moveToNext()) {
//                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
//                val id = cursor.getLong(idColumn)
//                val path = cursor.getString(idColumn)
//                val contentUri = ContentUris.withAppendedId(
//                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
//                )
//                imagePaths.add(contentUri)
//            }
//            galleryAdapter?.updateAdapter(imagePaths)
//        }
    }

    private fun initBottomNavigationBar() {
        with(binding) {
            attachCameraButton.setOnClickListener {
                requestCameraPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
            attachGalleryButton.setOnClickListener {
                requestGalleryPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            attachFileButton.setOnClickListener {
                requestGalleryPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            attachLocationButton.setOnClickListener {
                dismiss()
                openLocation()
            }
        }
    }

    private fun initEmojiButton() {
        AXEmojiManager.install(requireContext(), AXGoogleEmojiProvider(requireContext()))
        val emojiView = AXSingleEmojiView(requireContext())
        emojiView.editText = binding.chatInput
        binding.buttonEmoticon.setOnClickListener {
            showToast("This feature is not implemented")
        }
    }

    private fun initButtonSend() {
        binding.btnSend.setOnClickListener {
            val paths = galleryAdapter?.getSelectedImagePaths()
          // отправить картинки
            dismiss()
        }
    }

    private fun sendMessageWithAttachment(uri: Uri) {
                val list = ArrayList<String>()

                viewModel.insertMessage(
                    getParams().id,
                    MessageDto(
                        "m${System.currentTimeMillis()}",
                        true,
                        "Иван Иванов",
                        getParams().opponentJid,
                        "",
                        MessageSendingState.Deliver,
                        System.currentTimeMillis(),
                        0,
                        MessageDisplayType.Text,
                        false,
                        false,
                        null,
                        false,
                        null,
                        false,
                        uries = list,
                        references = null,
                        isUnread = true,
                        hasReferences = true
                    )
                )
//            if (currentPhotoUri != null) {
//                j
//                val list = HashSet<String>()
//                list.add(currentPhotoUri.toString())
//
//            }
        dismiss()
            }



    private fun onGotCameraPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value }) {
            takePhotoFromCamera()
        } else askUserForOpeningAppSettings()
    }


    private fun onGotGalleryPermissionResult(granted: Boolean) {
        if (granted) galleryResultLauncher.launch("image/*")
        else askUserForOpeningAppSettings()
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
                binding.inputPanel.setOnClickListener { }
                binding.frameLayoutActionContainer.setOnClickListener {
//                    binding.chatInput.isFocusable = true
//                    binding.chatInput.isFocusableInTouchMode = true
//                    binding.chatInput.requestFocus()
//                    val inputMethodManager: InputMethodManager =
//                        context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//                    inputMethodManager.showSoftInput(
//                        binding.chatInput,
//                        InputMethodManager.SHOW_IMPLICIT
//                    )
                }
//

                binding.send.isVisible = true
                //   binding.btnSend.isVisible = true

                binding.send.startAnimation(animLeft)
                //   binding.inputLayout.startAnimation(animTop)
            }
            binding.tvCountFiles.text = selectedImagesCount.toString()
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
        showToast(resources.getString(R.string.attach_files_warning))
    }

    override fun showImageViewer(position: Int) {
       navigator().showImageViewer(imagePaths, position)
    }

    private fun takePhotoFromCamera() {
        val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
        val image = File(imagesDir, "IMG_" + System.currentTimeMillis() + ".png")
        if (image != null) {
            currentPhotoUri = getFileUri(image)
            cameraResultLauncher.launch(currentPhotoUri)
        }
    }


    @Throws(IOException::class)
    fun saveImageInAndroidApi29AndAbove(@NonNull bitmap: Bitmap): Uri? {
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + System.currentTimeMillis())
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }
        val resolver = requireContext().contentResolver
        var uri: Uri? = null
        return try {
            val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            uri = resolver.insert(contentUri, values)
            if (uri == null) {
                //isSuccess = false;
                throw IOException("Failed to create new MediaStore record.")
            }
            resolver.openOutputStream(uri).use { stream ->
                if (stream == null) {
                    //isSuccess = false;
                    throw IOException("Failed to open output stream.")
                }
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)) {
                    //isSuccess = false;
                    throw IOException("Failed to save bitmap.")
                }
            }
            //isSuccess = true;
            uri
        } catch (e: IOException) {
            if (uri != null) {
                resolver.delete(uri, null, null)
            }
            throw e
        }
    }

    private fun saveImageInAndroidApi28AndBelow(bitmap: Bitmap): Boolean {
        val fos: OutputStream
        val imagesDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
        val image = File(imagesDir, "IMG_" + System.currentTimeMillis() + ".png")
        try {
            fos = FileOutputStream(image)
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
            Objects.requireNonNull(fos).close()
        } catch (e: IOException) {
            e.printStackTrace()
            //isSuccess = false;
            return false
        }
        //isSuccess = true;
        return true
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = if (binding.send.isVisible) binding.tvCountFiles.text.toString() else ""
        outState.putString("state", state)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        galleryAdapter = null
        bottomSheetCallback?.let { behavior?.removeBottomSheetCallback(it) }
        _binding = null
    }

}
