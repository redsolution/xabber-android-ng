package com.xabber.presentation.application.fragments.contacts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.SimpleItemAnimator
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xabber.R
import com.xabber.data_base.models.messages.MessageReferenceStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.databinding.LayoutBottomSheetCustomBinding
import com.xabber.dto.MediaDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.XabberApplication
import com.xabber.presentation.application.fragments.chat.*
import com.xabber.presentation.application.fragments.chat.viewmodel.ChatViewModel
import com.xabber.utils.askUserForOpeningAppSettings
import com.xabber.utils.showToast
import io.realm.kotlin.ext.realmListOf
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import kotlin.random.Random

class AttachmentBottomSheet : BottomSheetDialogFragment(R.layout.layout_bottom_sheet_custom),
    GalleryAdapter.Listener {
    private val binding by viewBinding(LayoutBottomSheetCustomBinding::bind)
    private var bottomSheetWidth = 0
    private var behavior: BottomSheetBehavior<*>? = null
    private val viewModel: MediaViewModel by viewModels()
    private val chatVM: ChatViewModel by viewModel { parametersOf(getChatId()) }
    private var galleryAdapter: GalleryAdapter? = null
    private var currentPhotoUri: Uri? = null
    private var mediaList = ArrayList<MediaDto>()
    private var set = HashSet<Long>()

    private lateinit var actionPanel: View
    private lateinit var inputPanel: View
    private lateinit var camera: AppCompatTextView
    private lateinit var file: AppCompatTextView
    private lateinit var contact: AppCompatTextView
    private lateinit var music: AppCompatTextView
    private lateinit var addMessages: AppCompatTextView
    private lateinit var chatInput: EditText
    private lateinit var sendGroup: ConstraintLayout
    private lateinit var tvCount: TextView

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(), ::onGotCameraPermissionResult
    )

    private val cameraResultLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            sendMessageWithAttachment(listOf(currentPhotoUri!!), chatInput.text.trimEnd().toString())
        } else {
            dismiss()
        }
    }

    private val requestFilePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotFilePermissionResult
    )

    private val fileResultLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
            if (uris != null && uris.isNotEmpty()) {
                sendMessageWithAttachment(uris, chatInput.text.trimEnd().toString())
            } else {
                dismiss()
            }
        }

    @SuppressLint("NotifyDataSetChanged")
    private val viewImageActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val resultValue = data?.getLongArrayExtra(AppConstants.VIEW_IMAGE_ACTIVITY_RESULT)
                if (resultValue != null) {
                    val set = HashSet(resultValue.toList())
                    galleryAdapter?.setMediaSelected(set)
                    val animLeft = AnimationUtils.loadAnimation(context, R.anim.to_left)
                    galleryAdapter?.notifyDataSetChanged()
                    if (galleryAdapter?.getSelectedMedia()!!.size > 0 || behavior?.state == BottomSheetBehavior.STATE_EXPANDED) {
                        actionPanel.isVisible = false
                        inputPanel.isVisible = galleryAdapter?.getSelectedMedia()!!.size > 0
                        if (galleryAdapter?.getSelectedMedia()!!.size > 0) {
                            sendGroup.startAnimation(animLeft)
                            tvCount.text = galleryAdapter!!.getSelectedMedia().size.toString()
                        }
                    } else {
                        actionPanel.isVisible = true
                        inputPanel.isVisible = false
                    }
                }
            }
        }

    private val windowHeight: Int
        get() {
            val displayMetrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requireContext().display?.getRealMetrics(displayMetrics)
            } else activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
            return displayMetrics.heightPixels
        }

    private val bottomSheetDialogDefaultHeight: Int
        get() = windowHeight

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post {
            bottomSheetWidth = requireView().width
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
            inputPanel = bottomSheetDialog.layoutInflater.inflate(
                R.layout.input_panel,
                containerLayout,
                false
            )
            actionPanel = bottomSheetDialog.layoutInflater.inflate(
                R.layout.attachment_action_panel,
                containerLayout,
                false
            )

            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }

            actionPanel.layoutParams = layoutParams
            inputPanel.layoutParams = layoutParams

            containerLayout?.addView(actionPanel)
            containerLayout?.addView(inputPanel)
            actionPanel.post {
                (coordinator?.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    actionPanel.layoutParams.width = bottomSheetWidth
                    containerLayout?.requestLayout()
                }
            }

            inputPanel.post {
                (coordinator?.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    inputPanel.layoutParams.width = bottomSheetWidth
                    containerLayout?.requestLayout()
                }
            }
            initViews()
            setupRatio(bottomSheetDialog, savedInstanceState)
            initActions()
            initInputPanel()
            initGalleryRecyclerView(set)
        }
        return bottomSheetDialog
    }

    private fun initViews() {
        camera = actionPanel.findViewById(R.id.camera)
        file = actionPanel.findViewById(R.id.file)
        contact = actionPanel.findViewById(R.id.contact)
        music = actionPanel.findViewById(R.id.music)
        addMessages = actionPanel.findViewById(R.id.add_messages)
        chatInput = inputPanel.findViewById(R.id.chat_input)
        sendGroup = inputPanel.findViewById(R.id.send)
        tvCount = inputPanel.findViewById(R.id.tv_count_files)
    }

    private fun setupRatio(
        bottomSheetDialog: BottomSheetDialog,
        savedInstanceState: Bundle?
    ) {
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        behavior = BottomSheetBehavior.from(bottomSheet)
        val bottomSheetLayoutParams = bottomSheet.layoutParams
        bottomSheetLayoutParams.height = bottomSheetDialogDefaultHeight
        val expandedHeight = bottomSheetLayoutParams.height
        val peekHeight = (expandedHeight / 1.6).toInt()
        bottomSheet.layoutParams = bottomSheetLayoutParams

        BottomSheetBehavior.from(bottomSheet).skipCollapsed = false
        BottomSheetBehavior.from(bottomSheet).peekHeight = peekHeight
        BottomSheetBehavior.from(bottomSheet).isHideable = true
        inputPanel.isVisible = false
        restoreState(savedInstanceState)
        addBottomSheetCallback()
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        behavior?.state = savedInstanceState?.getInt(AppConstants.BOTTOM_SHEET_STATE)
            ?: BottomSheetBehavior.STATE_COLLAPSED
        if (behavior?.state == BottomSheetBehavior.STATE_EXPANDED) binding.bottomSheetLayout.progress = 1f
        if (savedInstanceState != null) {
            val selected = savedInstanceState.getLongArray(AppConstants.SELECTED_SET)
            set = selected?.toList()?.let { HashSet(it) }!!
            if (set.size > 0) {
                inputPanel.isVisible = true
                actionPanel.isVisible = false
            } else {
                if (behavior?.state == BottomSheetBehavior.STATE_EXPANDED) actionPanel.isVisible = false
            }
            val savedText = savedInstanceState.getString(AppConstants.SAVED_INPUT_TEXT)
            if (savedText != null) chatInput.setText(savedText)
        }
    }

    private fun addBottomSheetCallback() {
        behavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset > 0) {
                    if (slideOffset > 0.9) {
                        if (actionPanel.isVisible) hideActionPanel()
                        val variable2Range = 0.3..1.0
                        val variable1Range = 0.9..1.0
                        binding.bottomSheetLayout.progress =
                            ((slideOffset - 0.9) * (variable2Range.endInclusive - variable2Range.start) / (variable1Range.endInclusive - 0.9) + variable2Range.start).toFloat()
                    } else if (slideOffset > 0.7) {
                        if (galleryAdapter?.getSelectedMedia()?.size == 0 && !actionPanel.isVisible) {
                            actionPanel.isVisible = true
                        }
                        val variable2Range = 0.0..0.3
                        val variable1Range = 0.7..0.9
                        binding.bottomSheetLayout.progress =
                            ((slideOffset - 0.7) * (variable2Range.endInclusive - variable2Range.start) / (variable1Range.endInclusive - 0.7) + variable2Range.start).toFloat()
                    } else {
                        binding.bottomSheetLayout.progress = 0f
                        if (galleryAdapter?.getSelectedMedia()?.size == 0 && !actionPanel.isVisible) {
                            actionPanel.isVisible = true
                        }
                    }
                } else {
                    val layoutParams = actionPanel.layoutParams as FrameLayout.LayoutParams
                    val lp = inputPanel.layoutParams as FrameLayout.LayoutParams
                    layoutParams.bottomMargin = (slideOffset * 1200).toInt()
                    lp.bottomMargin = (slideOffset * 1200).toInt()
                    actionPanel.layoutParams = layoutParams
                    inputPanel.layoutParams = lp
                }
            }
        })
    }

    private fun hideActionPanel() {
        val bottom = AnimationUtils.loadAnimation(context, R.anim.fast_slide_bottom)
        actionPanel.startAnimation(bottom)
        actionPanel.isVisible = false
    }

    private fun initActions() {
        camera.setOnClickListener {
            requestCameraPermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            )
        }
        file.setOnClickListener { requestFilePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }
        contact.setOnClickListener { showToast(resources.getString(R.string.feature_not_implemented)) }
//        addMessages.setOnClickListener { send1000Messages() }
        music.setOnClickListener { showToast(resources.getString(R.string.feature_not_implemented)) }
    }

    private fun getFileNameAndSizeFromUri(uri: Uri): Pair<String?, Long> {
        var fileName: String? = null
        var fileSize: Long = -1
        val contentResolver = XabberApplication.applicationContext().contentResolver
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)

        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                fileName = it.getString(displayNameIndex)
                fileSize = it.getLong(sizeIndex)
            }
        }
        return Pair(fileName, fileSize)
    }

    fun getMimeType(uri: Uri): String? {
        val resolver = XabberApplication.applicationContext().contentResolver
        return resolver.getType(uri)
    }

    private fun initInputPanel() {
        sendGroup.setOnClickListener {
            val selectedUris = galleryAdapter?.getUriesSelected()?.toList()
            sendMessageWithAttachment(selectedUris, chatInput.text?.trimEnd().toString())
        }
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
        (binding.recyclerGallery.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    private fun onGotCameraPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value }) {
            takePhotoFromCamera()
        } else askUserForOpeningAppSettings()
    }

    private fun takePhotoFromCamera() {
        val imagesDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DCIM).toString()
        val image = File(imagesDir, "IMG_" + System.currentTimeMillis() + ".png")
        currentPhotoUri = FileManager.getFileUri(image)
        cameraResultLauncher.launch(currentPhotoUri)
    }

    private fun onGotFilePermissionResult(granted: Boolean) {
        if (granted) fileResultLauncher.launch("*/*")
    }

    override fun onRecentImagesSelected() {
        val selectedImagesCount = galleryAdapter?.getSelectedMedia()?.size ?: 0
        val minTop = AnimationUtils.loadAnimation(context, R.anim.slide_top)
        val minBottom = AnimationUtils.loadAnimation(context, R.anim.bottom)
        val animRight = AnimationUtils.loadAnimation(context, R.anim.to_right)
        val dis = AnimationUtils.loadAnimation(context, R.anim.disappearance)
        val animLeft = AnimationUtils.loadAnimation(context, R.anim.to_left)
        if (selectedImagesCount > 0) {
            if (!inputPanel.isVisible) {
                if (actionPanel.isVisible) {
                    setVisibleActions(false)
                    actionPanel.startAnimation(minBottom)
                }
                actionPanel.isVisible = false
                inputPanel.isVisible = true
                sendGroup.startAnimation(animLeft)
            }
            tvCount.text = selectedImagesCount.toString()
        } else {
            if (inputPanel.isVisible) {
                setVisibleActions(true)
                inputPanel.startAnimation(dis)
                sendGroup.startAnimation(animRight)
                inputPanel.isVisible = false
                if (behavior?.state != BottomSheetBehavior.STATE_EXPANDED) {
                    actionPanel.isVisible = true
                    actionPanel.startAnimation(minTop)
                } else {
                    actionPanel.isVisible = false
                    inputPanel.isVisible = false
                }
            }
        }
    }

    private fun setVisibleActions(show: Boolean) {
        camera.isVisible = show
        file.isVisible = show
        contact.isVisible = show
        music.isVisible = show
    }

    override fun tooManyFilesSelected() {
        showToast(resources.getString(R.string.attach_files_warning))
    }

    override fun showMediaViewer(id: Long) {
        val intent = Intent(requireContext(), MediaDetailsActivity::class.java)
        val longArray = galleryAdapter?.getSelectedMedia()?.toLongArray()
        intent.putExtra(AppConstants.SELECTED_IDES, longArray)
        intent.putExtra(AppConstants.IMAGE_POSITION_KEY, id)
        viewImageActivityLauncher.launch(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (behavior != null) outState.putInt(AppConstants.BOTTOM_SHEET_STATE, behavior!!.state)
        val savedText = chatInput.text.toString()
        outState.putString(AppConstants.SAVED_INPUT_TEXT, savedText)
        val selectedArray = galleryAdapter?.getSelectedMedia()?.toLongArray()
        outState.putLongArray(AppConstants.SELECTED_SET, selectedArray)
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dialog?.window?.setWindowAnimations(-1)
    }

    override fun onDestroy() {
        super.onDestroy()
        galleryAdapter = null
    }

    private fun sendMessageWithAttachment(uris: List<Uri>?, caption: String) {
        val effectiveUris = uris ?: emptyList()

        val references = realmListOf<MessageReferenceStorageItem>()
        effectiveUris.forEach { selectedUri ->
            val pair = getFileNameAndSizeFromUri(selectedUri)
            references.add(
                MessageReferenceStorageItem().apply {
                    uri = selectedUri.toString()
                    mimeType = getMimeType(selectedUri).toString()
                    fileName = pair.first ?: ""
                    fileSize = pair.second
                }
            )
        }

        val chat = chatVM.loadChat(getChatId()) ?: return

        val currentTime = System.currentTimeMillis()
        val messageId = currentTime.toString()
        val message = MessageStorageItem().apply {
            this.messageId = messageId
            primary = MessageStorageItem.genPrimary(messageId, chat.owner)
            owner = chat.owner
            opponent = chat.opponentJid
            outgoing = true
            body = caption
            this.references = references
            sentDate = currentTime
            date = currentTime
            state = MessageSendingState.NotSent
            isRead = true
        }

        chatVM.insertMessage(getChatId(), message)
        dismiss()
    }

    companion object {
        fun newInstance(chatId: String): AttachmentBottomSheet {
            val arguments = Bundle().apply {
                putString(AppConstants.CHAT_ID, chatId)
            }
            val fragment = AttachmentBottomSheet()
            fragment.arguments = arguments
            return fragment
        }
    }

    private fun getChatId(): String =
        requireArguments().getString(AppConstants.CHAT_ID)!!
}