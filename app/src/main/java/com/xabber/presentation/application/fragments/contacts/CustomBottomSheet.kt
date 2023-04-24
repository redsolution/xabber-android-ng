package com.xabber.presentation.application.fragments.contacts

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.SimpleItemAnimator
import by.kirich1409.viewbindingdelegate.viewBinding
import com.aghajari.emojiview.view.AXEmojiEditText
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.databinding.LayoutBottomSheetCustomBinding
import com.xabber.models.dto.MediaDto
import com.xabber.models.dto.MessageDto
import com.xabber.models.dto.MessageReferenceDto
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.presentation.AppConstants
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.application.fragments.chat.*
import com.xabber.presentation.application.fragments.chat.attach.AttachBottomSheet
import com.xabber.presentation.application.fragments.chat.geo.PickGeolocationActivity
import com.xabber.presentation.onboarding.fragments.signin.feature.State
import com.xabber.utils.askUserForOpeningAppSettings
import com.xabber.utils.showToast
import java.io.File

class CustomBottomSheet : BottomSheetDialogFragment(R.layout.layout_bottom_sheet_custom),
    GalleryAdapter.Listener {
    private val binding by viewBinding(LayoutBottomSheetCustomBinding::bind)
    private var bottomSheetWidth = 0
    private var behavior: BottomSheetBehavior<*>? = null
    private val viewModel: MediaViewModel by viewModels()
    private val chatVM: ChatViewModel by viewModels()
    private var galleryAdapter: GalleryAdapter? = null
    private var currentPhotoUri: Uri? = null
    private var mediaList = java.util.ArrayList<MediaDto>()
    lateinit var frameLayoutActionContainer: FrameLayout
    lateinit var attachScrollBar: HorizontalScrollView
    lateinit var radioGroup: LinearLayout
    lateinit var cameraButton: LinearLayout
    lateinit var fileButton: LinearLayout
    lateinit var galleryButton: LinearLayout
    lateinit var locationButton: LinearLayout
    lateinit var sendGroup: ConstraintLayout
    lateinit var btnSend: ImageView
    lateinit var tvCount: TextView
    lateinit var badge: ImageView
    lateinit var chatInpyt: AXEmojiEditText
    lateinit var buttons: View
    lateinit var input: View

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(), ::onGotCameraPermissionResult
    )

    private val cameraResultLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) sendMessageWithAttachment(currentPhotoUri!!, "") else dismiss()
    }

    private val requestFilePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotFilePermissionResult
    )

    private fun onGotFilePermissionResult(granted: Boolean) {
        if (granted) fileResultLauncher.launch(arrayOf("*/*"))
    }

    private val fileResultLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) sendMessageWithAttachment(uri, "") else dismiss()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        view?.post {
            bottomSheetWidth = view?.width ?: 0
        }
    }

    override fun getTheme(): Int = R.style.Theme_NoWiredStrapInNavigationBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        bottomSheetDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        bottomSheetDialog.setOnShowListener {
            val coordinator = (it as BottomSheetDialog)
                .findViewById<CoordinatorLayout>(com.google.android.material.R.id.coordinator)
            val containerLayout =
                it.findViewById<FrameLayout>(com.google.android.material.R.id.container)
            input = bottomSheetDialog.layoutInflater.inflate(R.layout.input_panel, null)
            buttons = bottomSheetDialog.layoutInflater.inflate(R.layout.view_buttons, null)
            sendGroup = input.findViewById(R.id.send)
            chatInpyt = input.findViewById(R.id.chat_input)
            setR(bottomSheetDialog, savedInstanceState, buttons, input, sendGroup, chatInpyt)
            frameLayoutActionContainer = buttons.findViewById(R.id.frame_layout_action_container)
            attachScrollBar = buttons.findViewById(R.id.attach_scroll_bar)
            radioGroup = buttons.findViewById(R.id.attach_radio_group)
            cameraButton = buttons.findViewById(R.id.attach_camera_button)

            btnSend = input.findViewById(R.id.btn_send)
            tvCount = input.findViewById(R.id.tv_count_files)
            fileButton = buttons.findViewById(R.id.attach_file_button)
            locationButton = buttons.findViewById(R.id.attach_location_button)

            buttons.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

            }
            containerLayout!!.addView(buttons)
           input.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

            }
containerLayout.addView(input)

            buttons.post {
                (coordinator!!.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    buttons.layoutParams.width = bottomSheetWidth
                    containerLayout.requestLayout()
                }
            }

            input.post {
                (coordinator!!.layoutParams as ViewGroup.MarginLayoutParams).apply {
                   input.layoutParams.width = bottomSheetWidth
                    containerLayout.requestLayout()
                }
            }
        }
        return bottomSheetDialog
    }

    private fun setR(
        bottomSheetDialog: BottomSheetDialog,
        savedInstanceState: Bundle?,
        buttons: View, input: View, sendGroup: ConstraintLayout, chatInput: AXEmojiEditText
    ) {
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        behavior = BottomSheetBehavior.from(
            bottomSheet
        )
        behavior?.state = savedInstanceState?.getInt(AppConstants.BOTTOM_SHEET_STATE)
            ?: BottomSheetBehavior.STATE_COLLAPSED
        if (behavior?.state == BottomSheetBehavior.STATE_EXPANDED) binding.bottomSheetLayout.progress =
            1f
        val minTop = AnimationUtils.loadAnimation(context, R.anim.slide_top)
        val animBottom = AnimationUtils.loadAnimation(context, R.anim.to_bottom)
        val minBottom = AnimationUtils.loadAnimation(context, R.anim.slide_bottom)
        val animTop = AnimationUtils.loadAnimation(context, R.anim.to_top)
        val animRight = AnimationUtils.loadAnimation(context, R.anim.to_right)
        val app = AnimationUtils.loadAnimation(context, R.anim.appearance)
        val dis = AnimationUtils.loadAnimation(context, R.anim.disappearance)
        val animLeft = AnimationUtils.loadAnimation(context, R.anim.to_left)
        val bottom = AnimationUtils.loadAnimation(context, R.anim.bottom)
        input.isVisible = false

if (savedInstanceState != null) {
    val selected = savedInstanceState.getLongArray(AppConstants.SELECTED_SET)
   val set = selected?.toList()?.let { HashSet(it) }!!
    if (set.size > 0) {
        input.isVisible = true
        buttons.isVisible = false
    }
}

        (behavior as BottomSheetBehavior<View>).addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
//                when (newState) {
//                    BottomSheetBehavior.STATE_EXPANDED -> {
//                      if (buttons.isVisible) {
//                         buttons.startAnimation(bottom)
//                            buttons.isVisible = false
//                       }
//                        binding.bottomSheetLayout.transitionToEnd()
//                    }
//                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset > 0) {
                    if (slideOffset > 0.9) {
                       buttons.isVisible = false
                        val variable2Range = 0.3..1.0
                        val variable1Range = 0.9..1.0
                        binding.bottomSheetLayout.progress =
                            ((slideOffset - 0.9) * (variable2Range.endInclusive - variable2Range.start) / (variable1Range.endInclusive - 0.9) + variable2Range.start).toFloat()

                    } else if (slideOffset > 0.7) {
                        if (galleryAdapter?.getSelectedMedia()?.size == 0 && !buttons.isVisible) {
                            buttons.isVisible = true
                        }
                        val variable2Range = 0.1..0.3
                        val variable1Range = 0.7..1.0
                        binding.bottomSheetLayout.progress =
                            ((slideOffset - 0.7) * (variable2Range.endInclusive - variable2Range.start) / (variable1Range.endInclusive - 0.7) + variable2Range.start).toFloat()
                    }   else { binding.bottomSheetLayout.progress = 0f
                        if (galleryAdapter?.getSelectedMedia()?.size == 0 && !buttons.isVisible) {
                            buttons.isVisible = true
                    }
                }
                }else {
                    val layoutParams =
                        buttons.layoutParams as FrameLayout.LayoutParams
                    val lp = input.layoutParams as FrameLayout.LayoutParams
                    layoutParams.bottomMargin = (slideOffset * 1200).toInt()
                    buttons.layoutParams = layoutParams
                    lp.bottomMargin = (slideOffset * 1200).toInt()
                    input.layoutParams = lp
                }
            }
        })

sendGroup.setOnClickListener {
sendMessageWithAttachment(null, chatInpyt.text?.trimEnd().toString())
}
        val bottomSheetLayoutParams = bottomSheet.layoutParams
        bottomSheetLayoutParams.height = bottomSheetDialogDefaultHeight
        val expandedHeight = bottomSheetLayoutParams.height
        val peekHeight =
            (expandedHeight / 1.6).toInt()
        bottomSheet.layoutParams = bottomSheetLayoutParams
        BottomSheetBehavior.from(bottomSheet).skipCollapsed = false
        BottomSheetBehavior.from(bottomSheet).peekHeight = peekHeight
        BottomSheetBehavior.from(bottomSheet).isHideable = true
    }

    private val windowHeight: Int
        get() {
            val displayMetrics = DisplayMetrics()
            (requireContext() as Activity).windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics.heightPixels
        }

    private val bottomSheetDialogDefaultHeight: Int
        get() = windowHeight

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var set = HashSet<Long>()

        if (savedInstanceState != null) {
            val selected = savedInstanceState.getLongArray(AppConstants.SELECTED_SET)
            set = selected?.toList()?.let { HashSet(it) }!!
        }
        initGalleryRecyclerView(set)

    }

    private fun initGalleryRecyclerView(set: HashSet<Long>) {
        galleryAdapter = GalleryAdapter(this)
        binding.recyclerGallery.adapter = galleryAdapter
        loadGalleryPhotosAlbums()
        galleryAdapter?.setMediaSelected(set)

    }

    private fun loadGalleryPhotosAlbums() {
        mediaList = viewModel.getMediaList()
        galleryAdapter?.updateAdapter(mediaList)
        (binding.recyclerGallery.itemAnimator as SimpleItemAnimator).supportsChangeAnimations =
            false

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

    private fun takePhotoFromCamera() {
        val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
        val image = File(imagesDir, "IMG_" + System.currentTimeMillis() + ".png")
        if (image != null) {
            currentPhotoUri = FileManager.getFileUri(image)
            cameraResultLauncher.launch(currentPhotoUri)
        }
    }

    override fun onRecentImagesSelected() {
        val selectedImagesCount = if (galleryAdapter?.getSelectedMedia() != null) {
            galleryAdapter?.getSelectedMedia()!!.size
        } else 0
        val minTop = AnimationUtils.loadAnimation(context, R.anim.slide_top)
        val minBottom = AnimationUtils.loadAnimation(context, R.anim.slide_bottom)
        val animRight = AnimationUtils.loadAnimation(context, R.anim.to_right)
        val app = AnimationUtils.loadAnimation(context, R.anim.appearance)
        val dis = AnimationUtils.loadAnimation(context, R.anim.disappearance)
        val animLeft = AnimationUtils.loadAnimation(context, R.anim.to_left)
        if (selectedImagesCount > 0) {
                if (!input.isVisible) {
                   buttons.startAnimation(minBottom)
               //     attachScrollBar.startAnimation(dis)
           // buttons.startAnimation(dis)
                    buttons.isVisible = false
            buttons.animation = null
                    input.isVisible = true
                    fileButton.isEnabled = false
                    cameraButton.isEnabled = false
                    sendGroup.startAnimation(animLeft) }
                    tvCount.text = galleryAdapter?.getSelectedMedia()!!.size.toString()

            //    }
            } else {
            if (input.isVisible) {
                input.startAnimation(dis)
                sendGroup.startAnimation(animRight)
                input.isVisible = false
                if (behavior?.state != BottomSheetBehavior.STATE_EXPANDED) {
                    buttons.isVisible = true
                buttons.startAnimation(minTop)
              //      attachScrollBar.startAnimation(app)
                    fileButton.isEnabled = true
                    locationButton.isEnabled = true
                    cameraButton.isEnabled = true
                } else {
                    buttons.isVisible = false
                    input.isVisible = false
                }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (behavior != null) outState.putInt(AppConstants.BOTTOM_SHEET_STATE, behavior!!.state)
        val state = if (sendGroup.isVisible) tvCount.text.toString() else ""
        outState.putString("state", state)
        val selectedArray = galleryAdapter?.getSelectedMedia()?.toLongArray()
        outState.putLongArray(AppConstants.SELECTED_SET, selectedArray)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        galleryAdapter = null
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            AppConstants.PICK_LOCATION_REQUEST_CODE -> {
                val lon = data?.getDoubleExtra(PickGeolocationActivity.LON_RESULT, 0.0)
                val lat = data?.getDoubleExtra(PickGeolocationActivity.LAT_RESULT, 0.0)
                sendGeolocation(lon, lat)
                dismiss()
            }
            222 -> {
                val result = data?.getLongArrayExtra(AppConstants.VIEW_IMAGE_ACTIVITY_RESULT)
                if (result != null) {
                    val set = HashSet(result.toList())
                    galleryAdapter?.setMediaSelected(set)
                    val animLeft = AnimationUtils.loadAnimation(context, R.anim.to_left)
                    galleryAdapter?.notifyDataSetChanged()
                    if (galleryAdapter?.getSelectedMedia()!!.size > 0 || behavior?.state == BottomSheetBehavior.STATE_EXPANDED) {
                        buttons.isVisible = false
                        fileButton.isEnabled = false
                        cameraButton.isEnabled = false
                        locationButton.isEnabled = false
                        input.isVisible = galleryAdapter?.getSelectedMedia()!!.size > 0
                       if (galleryAdapter?.getSelectedMedia()!!.size > 0 ){
                        sendGroup.startAnimation(animLeft)
                        tvCount.text = galleryAdapter!!.getSelectedMedia().size.toString()}
                    } else {
                        buttons.isVisible = true
                        fileButton.isEnabled = true
                        cameraButton.isEnabled = true
                        locationButton.isEnabled = true
                        input.isVisible = false
                    }
                }
            }
        }

    }

    private fun sendGeolocation(lon: Double?, lat: Double?) {

    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dialog?.window?.setWindowAnimations(-1)
    }


    private fun sendMessageWithAttachment(uri: Uri?, body: String) {
        val uries = galleryAdapter?.getUriesSelected()?.toList()

        val refer = java.util.ArrayList<MessageReferenceDto>()
        if (uries != null) {
            for (i in 0 until uries.size) {
                val r = MessageReferenceDto(
                    id = uries[i].toString(),
                    uri = uries[i].toString(),
                    mimeType = getMimeType(uries[i])!!
                )
                refer.add(r)
            }
        }
        if (uri != null) refer.add(MessageReferenceDto(id=uri.toString(), uri=uri.toString(), mimeType = getMimeType(uri)!!))
        val chat = chatVM.getChat(getChatId())
        chatVM.insertMessage( getChatId(), MessageDto(primary = getChatId() + System.currentTimeMillis(), references = refer, isOutgoing = true, owner= chat!!.owner, opponentJid = chat.opponentJid, canDeleteMessage = false, canEditMessage = false, messageBody = "  " + body, messageSendingState = MessageSendingState.Sending, sentTimestamp = System.currentTimeMillis(), isGroup = false))
        dismiss()
    }

    fun getMimeType(uri: Uri): String? {
        val resolver = XabberApplication.applicationContext().contentResolver
        return resolver.getType(uri)
    }


    companion object {
        fun newInstance(chatId: String): CustomBottomSheet {
            val arguments = Bundle().apply {
                putString(AppConstants.CHAT_ID, chatId)
            }
            val fragment = CustomBottomSheet()
            fragment.arguments = arguments
            return fragment
        }
    }

    private fun getChatId(): String =
        requireArguments().getString(AppConstants.CHAT_ID)!!

}