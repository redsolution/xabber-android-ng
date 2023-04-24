package com.xabber.presentation.application.dialogs
import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.SimpleItemAnimator
import by.kirich1409.viewbindingdelegate.viewBinding
import com.aghajari.emojiview.AXEmojiManager
import com.aghajari.emojiview.googleprovider.AXGoogleEmojiProvider
import com.aghajari.emojiview.view.AXSingleEmojiView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.BottomSheetAttachBinding
import com.xabber.models.dto.MediaDto
import com.xabber.models.dto.MessageDto
import com.xabber.models.dto.MessageReferenceDto
import com.xabber.models.xmpp.messages.MessageDisplayType
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.presentation.AppConstants
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.application.activity.SoftInputAssist
import com.xabber.presentation.application.fragments.chat.*
import com.xabber.presentation.application.fragments.chat.FileManager.getFileUri
import com.xabber.presentation.application.fragments.chat.attach.AttachBottomSheet
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
import kotlin.collections.HashSet


class AttachBottomSheet2 : BottomSheetDialogFragment(R.layout.bottom_sheet_attach), GalleryAdapter.Listener {
    private val binding by viewBinding(BottomSheetAttachBinding::bind)
    private var assist: SoftInputAssist? = null
    private var behavior: BottomSheetBehavior<*>? = null
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private val viewModel: MediaViewModel by viewModels()
    private val chatVM: ChatViewModel by viewModels()
    private var galleryAdapter: GalleryAdapter? = null
    private var currentPhotoUri: Uri? = null
    private var buttonLayoutParams: FrameLayout.LayoutParams? = null
    private var collapsedMargin = 0
    private var buttonHeight = 0
    private var expandedHeight = 0
    private var mediaList = java.util.ArrayList<MediaDto>()


    companion object {
        fun newInstance(chatId: String): AttachBottomSheet {
            val arguments = Bundle().apply {
                putString(AppConstants.CHAT_ID, chatId)
            }
            val fragment = AttachBottomSheet()
            fragment.arguments = arguments
            return fragment
        }
    }
    override fun onResume() {
        super.onResume()
        assist?.onResume()
    }

    override fun onPause() {
        super.onPause()
        assist?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        assist?.onDestroy()
    }

    private fun getChatId(): String =
        requireArguments().getString(AppConstants.CHAT_ID)!!

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

    private val cameraResultLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) sendMessageWithAttachment(currentPhotoUri!!) else dismiss()
    }

    private val requestFilePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotFilePermissionResult
    )

    private val fileResultLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) sendMessageWithAttachment(uri) else dismiss()
    }

    override fun getTheme(): Int = R.style.Theme_NoWiredStrapInNavigationBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val dialog = super.onCreateDialog(savedInstanceState)

        dialog.setOnShowListener { dialogInterface: DialogInterface -> setupRatio(dialogInterface as BottomSheetDialog, savedInstanceState) }

        (dialog as BottomSheetDialog).behavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
//                if (slideOffset > 0)
//                    buttonLayoutParams?.topMargin =
//                        ((expandedHeight - buttonHeight - collapsedMargin) * slideOffset + collapsedMargin).toInt() else
//                    buttonLayoutParams?.topMargin = collapsedMargin
//                binding.frameLayoutActionContainer.layoutParams =
//                    buttonLayoutParams
            }
        })
        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    private fun setupRatio(bottomSheetDialog: BottomSheetDialog, savedInstanceState: Bundle?) {
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        behavior = BottomSheetBehavior.from(
            bottomSheet
        )
        buttonLayoutParams =
            binding.frameLayoutActionContainer.layoutParams as FrameLayout.LayoutParams

        //Retrieve bottom sheet parameters

        val bottomSheetLayoutParams = bottomSheet.layoutParams
        bottomSheetLayoutParams.height = bottomSheetDialogDefaultHeight
        expandedHeight = bottomSheetLayoutParams.height
        val peekHeight =
            (expandedHeight / 2.5).toInt()

        //Setup bottom sheet
        bottomSheet.layoutParams = bottomSheetLayoutParams
        BottomSheetBehavior.from(bottomSheet).skipCollapsed = false
        BottomSheetBehavior.from(bottomSheet).peekHeight = peekHeight
        BottomSheetBehavior.from(bottomSheet).isHideable = true

        //Calculate button margin from top
        behavior?.state = savedInstanceState?.getInt(AppConstants.BOTTOM_SHEET_STATE)
            ?: BottomSheetBehavior.STATE_COLLAPSED
        buttonHeight =
            binding.frameLayoutActionContainer.height  //How tall is the button + experimental distance from bottom (Change based on your view)

        collapsedMargin = peekHeight - buttonHeight //Button margin in bottom sheet collapsed state
        binding.frameLayoutActionContainer.layoutParams = buttonLayoutParams
//        buttonLayoutParams?.topMargin = if (behavior?.state == BottomSheetBehavior.STATE_EXPANDED)
//            ((expandedHeight - buttonHeight - collapsedMargin) * 1 + collapsedMargin) else collapsedMargin

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        super.onViewCreated(view, savedInstanceState)

        assist =
            SoftInputAssist(dialog!!.window!!)
        var set = HashSet<Long>()
        if (savedInstanceState != null) {
            val selected = savedInstanceState.getLongArray(AppConstants.SELECTED_SET)
            set = selected?.toList()?.let { HashSet(it) }!! }
        initGalleryRecyclerView(set)
        initBottomNavigationBar()
        initInputLayout()
        initEmojiButton()
        initButtonSend()
        if (savedInstanceState != null)
            if (galleryAdapter!!.getSelectedMedia().size > 0) {
                binding.attachFileButton.isEnabled = false
                binding.attachCameraButton.isEnabled = false
                binding.attachLocationButton.isEnabled = false
                binding.tvCountFiles.text = galleryAdapter!!.getSelectedMedia().size.toString()
            } else {
                binding.attachScrollBar.isVisible = true
                binding.attachFileButton.isEnabled = true
                binding.attachCameraButton.isEnabled = true
                binding.attachLocationButton.isEnabled = true
                binding.inputPanel.isVisible = false
            }

    }

    private fun initInputLayout() {


    }

    private fun initGalleryRecyclerView(set: HashSet<Long>) {
        galleryAdapter = GalleryAdapter(this)
        binding.recentMedia.adapter = galleryAdapter
        loadGalleryPhotosAlbums()
        galleryAdapter?.setMediaSelected(set)
    }

    private fun loadGalleryPhotosAlbums() {
       mediaList = viewModel.getMediaList()
        galleryAdapter?.updateAdapter(mediaList)
        (binding.recentMedia.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

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
            attachFileButton.setOnClickListener {
              requestFilePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
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

        binding.buttonEmoticon.setOnClickListener {
            showToast("This feature is not implemented")
        }
    }

    private fun initButtonSend() {
        binding.btnSend.setOnClickListener {
            val uries = galleryAdapter?.getUriesSelected()!!.toList()

            val refer = java.util.ArrayList<MessageReferenceDto>()
            for (i in 0 until uries.size) {
                val r = MessageReferenceDto(
                    id = uries[i].toString(),
                    uri = uries[i].toString(),
                    mimeType = getMimeType(uries[i])!!
                )
                refer.add(r)

               chatVM.insertMessage(
            getChatId(),
            MessageDto(
                "m${System.currentTimeMillis()}",
                true,
                owner = chatVM.getChat(getChatId())!!.owner,
                opponentJid = chatVM.getChat(getChatId())!!.opponentJid,
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
                references = refer,
                isUnread = true,
                hasReferences = true
            )
        )
                dismiss()
            }
        }
    }

    fun getMimeType(uri: Uri): String? {
        val resolver = XabberApplication.applicationContext().contentResolver
        return resolver.getType(uri)
    }

    private fun sendMessageWithAttachment(uri: Uri) {
        val list = ArrayList<String>()
        dismiss()
    }

    private fun onGotCameraPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value }) {
            takePhotoFromCamera()
        } else askUserForOpeningAppSettings()
    }

    private fun openLocation() {
        val intent = Intent(requireContext(), PickGeolocationActivity::class.java)
        startActivityForResult(
            intent,
            AppConstants.PICK_LOCATION_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            AppConstants.PICK_LOCATION_REQUEST_CODE -> {
                val lon = data?.getDoubleExtra(LON_RESULT, 0.0)
                val lat = data?.getDoubleExtra(LAT_RESULT, 0.0)
                sendGeolocation(lon, lat)
                dismiss()
            }
            222 -> {
                val result = data?.getLongArrayExtra(AppConstants.VIEW_IMAGE_ACTIVITY_RESULT)
                if (result != null) {
                    val set = HashSet(result.toList())
                    galleryAdapter?.setMediaSelected(set)

                    galleryAdapter?.notifyDataSetChanged()
                    if (galleryAdapter?.getSelectedMedia()!!.size > 0) {
                        binding.attachScrollBar.isVisible = false
                        binding.attachFileButton.isEnabled = false
                        binding.attachCameraButton.isEnabled = false
                        binding.attachLocationButton.isEnabled = false
                        binding.inputPanel.isVisible = true
                        binding.tvCountFiles.text = galleryAdapter!!.getSelectedMedia().size.toString()
                    } else {
                        binding.attachScrollBar.isVisible = true
                        binding.attachFileButton.isEnabled = true
                        binding.attachCameraButton.isEnabled = true
                        binding.attachLocationButton.isEnabled = true
                        binding.inputPanel.isVisible = false
                    }
                }
            }
        }

    }


    fun sendGeolocation(lon: Double?, lat: Double?) {
        sendMessage(lon, lat)
    }


    fun sendMessage(lon: Double? = null, lat: Double? = null) {
    }

    override fun onRecentImagesSelected() {
        //   val j = resources.getDimension(com.google.android.material.R.dimen.action_bar_size)
        val selectedImagesCount = if (galleryAdapter?.getSelectedMedia() != null) {
            galleryAdapter?.getSelectedMedia()!!.size
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
         //   binding.tvCountFile.text = galleryAdapter?.getSelectedMedia()!!.size.toString()
            if (binding.attachScrollBar.isVisible) {
                binding.frameLayoutActionContainer.startAnimation(minBottom)
                binding.attachScrollBar.startAnimation(dis)
                binding.attachScrollBar.isVisible = false

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
////
//
//                binding.send.isVisible = true
//                //   binding.btnSend.isVisible = true
//
//                binding.send.startAnimation(animLeft)
//                //   binding.inputLayout.startAnimation(animTop)
           }
//            binding.tvCountFiles.text = selectedImagesCount.toString()
////            binding.tvCountFiles.text = String.format(
////                Locale.getDefault(),
////                "%d",
////                selectedImagesCount
////            )
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
                binding.attachFileButton.isEnabled = true
                binding.attachLocationButton.isEnabled = true


                binding.chatInput.isVisible = false
                // binding.chatInput.elevation = 0f
            }
        }
    }

    override fun tooManyFilesSelected() {
        showToast(resources.getString(R.string.attach_files_warning))
    }

    override fun showMediaViewer(position: Int) {
        val intent = Intent(requireContext(), ViewImageActivity::class.java)
        val longArray = galleryAdapter?.getSelectedMedia()?.toLongArray()
        intent.putParcelableArrayListExtra(AppConstants.MEDIA_LIST, mediaList)
        intent.putExtra(AppConstants.SELECTED_IDES, longArray)
        intent.putExtra(AppConstants.IMAGE_POSITION_KEY, position)
        startActivityForResult(intent, 222)
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
        if (behavior != null) outState.putInt(AppConstants.BOTTOM_SHEET_STATE, behavior!!.state)
        val state = if (binding.send.isVisible) binding.tvCountFiles.text.toString() else ""
        outState.putString("state", state)
        val selectedArray = galleryAdapter?.getSelectedMedia()?.toLongArray()
        outState.putLongArray(AppConstants.SELECTED_SET, selectedArray)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        galleryAdapter = null
        bottomSheetCallback?.let { behavior?.removeBottomSheetCallback(it) }

    }
    private fun onGotFilePermissionResult(granted: Boolean) {
        if (granted) fileResultLauncher.launch(arrayOf("*/*"))
    }


}
