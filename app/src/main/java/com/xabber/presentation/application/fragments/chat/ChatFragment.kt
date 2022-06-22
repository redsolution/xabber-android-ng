package com.xabber.presentation.application.fragments.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.SystemClock
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.xabber.R
import com.xabber.data.dto.FileDto
import com.xabber.data.dto.MessageDto
import com.xabber.data.dto.MessageKind
import com.xabber.data.xmpp.messages.MessageDisplayType
import com.xabber.data.xmpp.messages.MessageSendingState
import com.xabber.databinding.FragmentMessageBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chatlist.NotificationBottomSheet
import com.xabber.presentation.application.fragments.chatlist.SwitchNotifications
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatFragment : DetailBaseFragment(R.layout.fragment_message), MessageAdapter.Listener,
    AttachDialog.Listener, MiniatureAdapter.Listener, ReplySwipeCallback.SwipeAction,
    SwitchNotifications {
    private val binding by viewBinding(FragmentMessageBinding::bind)
    private var messageAdapter: MessageAdapter? = null
    private var miniatureAdapter: MiniatureAdapter? = null
    private val viewModel = ChatViewModel()
    var name: String = ""
    private val miniatures = ArrayList<FileDto>()
    lateinit var currentPhotoPath: String
    lateinit var photoUri: Uri

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotCameraPermissionResult
    )

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            var bitmap: Bitmap? = null
            Log.d("ooo", "${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null) {
                    bitmap = data?.extras?.get("data") as Bitmap
                    Log.d("ooo", "$bitmap")

                }
                addMediaToGallery(currentPhotoPath, bitmap)
                if (!binding.frameLayoutAttachedFiles.isVisible) binding.frameLayoutAttachedFiles.isVisible =
                    true
                miniatureAdapter?.submitList(miniatures)
                miniatureAdapter?.notifyDataSetChanged()
                if (miniatures.size > 0) {
                    binding.buttonSendMessage.isVisible = true
                    binding.buttonRecord.isVisible = false
                } else {
                    binding.buttonSendMessage.isVisible = false
                    binding.buttonRecord.isVisible = true
                }
            }
        }


    private val handler = Handler()
    private var currentVoiceRecordingState = VoiceRecordState.NotRecording
    private var recordSaveAllowed = false
    private var recordingPath: String? = null
    private var lockViewHeightSize = 0
    private var lockViewMarginBottom = 0
    private var fabMicViewHeightSize = 0
    private var fabMicViewMarginBottom = 0
    private var rootViewHeight = 0f
    private var stopTypingTimer: Timer? = Timer()

    private val timer = Runnable {
        changeStateOfInputViewButtonsTo(false)
        binding.linRecordLock.isVisible = true
        shortVibrate()
        binding.recordFloatButton.show()
        beginTimer(currentVoiceRecordingState == VoiceRecordState.InitiatedRecording)
    }

    private val record = Runnable { context?.let { VoiceManager.startRecording(it) } }

    companion object {
        fun newInstance(_name: String) = ChatFragment().apply {
            arguments = Bundle().apply {
                putString("_name", _name)
                name = _name
            }
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState != null) {
            name = savedInstanceState.getString("name", "")
            val messageText = savedInstanceState.getString("message_text", "")
            binding.chatInput.setText(messageText)
        }




        populateUiWithData()
        initToolbarActions()
        initRecyclerView()
        subscribeViewModelData()
        initInputLayoutActions()
        miniatureAdapter = MiniatureAdapter(this)
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)



        binding.attachedFiles.adapter = miniatureAdapter
        binding.attachedFiles.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        binding.inputLayout.setOnClickListener {
            //   binding.chatInput.isFocusable = true
            binding.chatInput.isFocusableInTouchMode = true
            binding.chatInput.requestFocus()
            val inputMethodManager: InputMethodManager =
                context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(binding.chatInput, InputMethodManager.SHOW_IMPLICIT)
        }
        if (binding.chatInput.text.toString().trim().isNotEmpty()) {
            binding.buttonSendMessage.isVisible = true
            binding.buttonAttach.isVisible = false
            binding.buttonRecord.isVisible = false
        } else {
            binding.buttonSendMessage.isVisible = false
            binding.buttonAttach.isVisible = true
            binding.buttonRecord.isVisible = true
        }
        binding.buttonRecord.setOnTouchListener { _: View?, motionEvent: MotionEvent ->
            when (motionEvent.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d("lll", "${navigator().requestPermissionToRecord()}")
                    if (navigator().requestPermissionToRecord()) {
                        if (currentVoiceRecordingState == VoiceRecordState.NotRecording) {
                            recordSaveAllowed = false
                            //   handler.postDelayed(record, 500)
                            handler.postDelayed(timer, 500)
                            AudioRecorder.startRecord()
                            currentVoiceRecordingState = VoiceRecordState.InitiatedRecording
                            navigator().lockScreenRotation(true)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    Log.d("lll", "$currentVoiceRecordingState")
                    if (currentVoiceRecordingState == VoiceRecordState.TouchRecording) {
                        sendVoiceMessage()
                        navigator().lockScreenRotation(false)
                    } else if (currentVoiceRecordingState == VoiceRecordState.InitiatedRecording) {
                        //                      handler.removeCallbacks(record)
                        handler.removeCallbacks(timer)
                        AudioRecorder.stopRecord { }
                        currentVoiceRecordingState = VoiceRecordState.NotRecording
                        navigator().lockScreenRotation(false)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    //FAB movement
//                    val lockParams =
//                        recordLockChevronImage?.layoutParams as LinearLayout.LayoutParams
//                    val yRecordDiff = rootViewHeight
//                    -(fabMicViewHeightSize + fabMicViewMarginBottom) + motionEvent.y
//                    val yLockDiff = rootViewHeight
//                    -(lockViewMarginBottom + lockViewHeightSize) + motionEvent.y
                    if (currentVoiceRecordingState == VoiceRecordState.TouchRecording) {
                        when {
                            motionEvent.y > 0 -> {
                                binding.recordFloatButton.animate()
                                    .y(rootViewHeight - (fabMicViewHeightSize + fabMicViewMarginBottom))
                                    .setDuration(0)
                                    .start()
                                binding.linRecordLock.animate()
                                    .y(rootViewHeight - (lockViewMarginBottom + lockViewHeightSize))
                                    .setDuration(0)
                                    .start()
//                                recordLockChevronImage?.alpha = 1f
                            }
                            motionEvent.y > -200 -> { //200 = height to the "locked" state
                                binding.recordFloatButton.animate()
                                    .y(10f)
                                    .setDuration(0)
                                    .start()
//                               binding.linRecordLock.animate()
//                                    .y(yLockDiff)
//                                    .setDuration(0)
//                                    .start()

//                                //lockParams.topMargin = (int) motionEvent.getY() / 3;
//                                lockParams.topMargin = (motionEvent.y.toInt()
//                                        * (recordLockChevronImage!!.height - recordLockImage!!.paddingTop)
//                                        / 200)
//                                recordLockChevronImage?.alpha = 1f + motionEvent.y / 200f
//                                recordLockChevronImage?.layoutParams = lockParams
                            }
                            else -> {
                                currentVoiceRecordingState = VoiceRecordState.NoTouchRecording

                                //workaround for the https://issuetracker.google.com/issues/111316656 issue of
                                //the button's image not updating after setting a background tint manually.
                                binding.recordFloatButton.hide()
                                binding.recordFloatButton.setImageResource(R.drawable.ic_send_black_24dp)
                                binding.recordFloatButton.show()
                                binding.recordFloatButton.animate()
                                    .y(rootViewHeight - (fabMicViewHeightSize + fabMicViewMarginBottom))
                                    .setDuration(100)
                                    .start()
//                                recordLockView.animate()
//                                    .y(rootViewHeight - (lockViewMarginBottom + lockViewHeightSize) + 50) // 50=temporary offset
//                                    .setDuration(100)
//                                    .start()
                                binding.includeRecord.cancelRecordLayout.visibility = View.VISIBLE
                                binding.imLock.setImageResource(R.drawable.ic_stop)
//                                recordLockImage?.setPadding(0, 0, 0, 0)
//                                recordLockImage?.setOnClickListener {
//                                    if (currentVoiceRecordingState == VoiceRecordState.NoTouchRecording) {
//                                        shortVibrate()
//                                        stopRecording()
//                                    }
//                                }
//                                lockParams.topMargin = -recordLockChevronImage!!.height
//                                recordLockChevronImage?.layoutParams = lockParams
//                                recordLockChevronImage?.alpha = 0f
                                shortVibrate()
                            }
                        }
                    }
                    //"Slide To Cancel" movement;
                    val alpha = 1f + motionEvent.x / 400f
                    if (currentVoiceRecordingState == VoiceRecordState.TouchRecording) {

                        if (motionEvent.x < 0) {
                            binding.includeRecord.cancelRecordLayout.animate().x(motionEvent.x)
                                .setDuration(0).start()
                        } else binding.includeRecord.cancelRecordLayout.animate().x(0f)
                            .setDuration(0).start()

                        binding.includeRecord.cancelRecordLayout.alpha = alpha

                        //since alpha and slide are tied together, we can cancel recording by checking transparency value
                        if (alpha <= 0) {
                            clearVoiceMessage()

                        }
                    }
                }
            }; true
        }


    }

    private fun populateUiWithData() {
        binding.messageUserName.text = name
        Glide.with(binding.imAvatar.context).load(R.drawable.images).into(binding.imAvatar)
        if (binding.chatInput.text.trim().isNotEmpty()) {
            binding.buttonSendMessage.isVisible = true
            binding.buttonAttach.isVisible = false
            binding.buttonRecord.isVisible = false
        } else {
            binding.buttonSendMessage.isVisible = false
            binding.buttonAttach.isVisible = true
            binding.buttonRecord.isVisible = true
        }
    }

    private fun initToolbarActions() {
        binding.messageToolbar.setOnClickListener { navigator().showEditContact(name) }
        binding.messageIconBack.setOnClickListener {
            // navigator().closeDetail()
            navigator().showBottomSheetDialog(BottomSheet())
        }
        binding.messageToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.call_out -> {}
                R.id.disable_notifications -> {
                    val dialog = NotificationBottomSheet()
                    navigator().showBottomSheetDialog(dialog)
                }
                R.id.clear_message_history -> {
                    val dialog = ChatHistoryClearDialog.newInstance(name)
                    navigator().showDialogFragment(dialog)
                }
                R.id.delete_chat -> {
                    val dialog = DeletingChatDialog.newInstance(name)
                    navigator().showDialogFragment(dialog)

                }
            }; true
        }
    }

    private fun sendVoiceMessage() {
        // отправить сообщение
        scrollDown()
    }


    private fun beginTimer(start: Boolean) {
        if (start) {
            binding.includeRecord.recordLayout.isVisible = true
            stopTypingTimer?.cancel()
            //      ignoreReceiver = false
            //     binding.includeRecord.slideLayout.animate().x(0f).setDuration(0).start()
//            binding.imChevron.alpha = 1f
//            binding.imLock.setPadding(0, 4.dp, 0, 0)
//            val layoutParams = binding.imChevron.layoutParams as LinearLayout.LayoutParams
//            layoutParams.topMargin = 0
//            layoutParams.let { binding.imChevron.layoutParams = it }

            binding.includeRecord.chrRecordingTimer.base = SystemClock.elapsedRealtime()
            binding.includeRecord.chrRecordingTimer.start()
            currentVoiceRecordingState = VoiceRecordState.TouchRecording

//            recordLockChevronImage?.alpha = 1f
//            recordLockImage?.setImageResource(R.drawable.ic_security_plain_24dp)
//            recordLockImage?.setPadding(0, dipToPx(4f, requireContext()), 0, 0)
//
//            val layoutParams = recordLockChevronImage?.layoutParams as? LinearLayout.LayoutParams
//            layoutParams?.topMargin = 0
//            layoutParams?.let { recordLockChevronImage?.layoutParams = it }
//
            //   recordTimer.base = SystemClock.elapsedRealtime()
//            recordTimer.start()
//            currentVoiceRecordingState = VoiceRecordState.TouchRecording
//            showScrollDownButtonIfNeed()
//            manageScreenSleep(true)
        } else {
            binding.includeRecord.chrRecordingTimer.stop()
//            ChatStateManager.getInstance().onPaused(accountJid, contactJid)
//            manageScreenSleep(false)
        }
    }


    private fun changeStateOfInputViewButtonsTo(state: Boolean) {
        binding.buttonEmoticon.isEnabled = state
        binding.buttonAttach.isEnabled = state
    }

    private fun shortVibrate() {
        binding.root.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }


    private enum class VoiceRecordState {
        NotRecording, InitiatedRecording, TouchRecording, NoTouchRecording, StoppedRecording
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val bitmap = data?.extras?.get("data") as Bitmap
            //    miniatures.add(0, ImageDto(bitmap))

            binding.frameLayoutAttachedFiles.isVisible = true
            //  miniatureAdapter?.submitList(miniatures)
            if (miniatures.size > 0) {
                binding.buttonSendMessage.isVisible = true
                binding.buttonRecord.isVisible = false
            }
            //     Log.d("files", "${files.size}, ${binding.attachedFiles.adapter}")
            //    fileAdapter?.updateAdapter(files)

        }
    }


    private fun clearVoiceMessage() {
        manageVoiceMessage(false)
    }

    private fun manageVoiceMessage(saveMessage: Boolean) {
        handler.removeCallbacks(record)
        handler.removeCallbacks(timer)
//        if (bottomMessagesPanel != null && bottomMessagesPanel?.messagesIds?.isNotEmpty() == true) {
//            stopRecordingAndSend(saveMessage, bottomMessagesPanel?.messagesIds)
//        } else {
//            stopRecordingAndSend(saveMessage)
//        }
//        cancelRecordingCompletely()
    }


    private fun initRecyclerView() {
        messageAdapter = MessageAdapter(this)
        binding.messageList.adapter = messageAdapter
        val linearLayoutManager = LinearLayoutManager(context)
        linearLayoutManager.reverseLayout = true
        binding.messageList.layoutManager = linearLayoutManager
        binding.messageList.addItemDecoration(MessageHeaderViewDecoration())
        addSwipeCallback()
    }

    private fun addSwipeCallback() {
        val replySwipeCallback = ReplySwipeCallback(binding.messageList.context)
        replySwipeCallback.setSwipeEnabled(true)
        replySwipeCallback.replySwipeCallback()
        ItemTouchHelper(replySwipeCallback).attachToRecyclerView(binding.messageList)

        binding.messageList.addItemDecoration(
            object : RecyclerView.ItemDecoration() {
                override fun onDraw(
                    c: Canvas,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    replySwipeCallback.onDraw(c)
                }
            })
    }

    private fun subscribeViewModelData() {
        viewModel.initList()
        viewModel.messages.observe(viewLifecycleOwner) {
            it.sort()
            messageAdapter?.submitList(it)
        }
        viewModel.miniatures.observe(viewLifecycleOwner) {
            //   fileAdapter?.updateAdapter(it)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initInputLayoutActions() {
        chatInputAddListener()
        binding.buttonEmoticon.setOnClickListener { }

        binding.buttonAttach.setOnClickListener {

            val dialog = AttachDialog(this)
            navigator().showBottomSheetDialog(dialog)
        }

        binding.buttonSendMessage.setOnClickListener {
            var messageKindDto: MessageKind? = null
            if (binding.answer.isVisible) {
                messageKindDto = MessageKind(
                    "id",
                    binding.replyMessageTitle.text.toString(),
                    binding.replyMessageContent.text.toString()
                )
            }
            val imageList = ArrayList<FileDto>()
            val text = binding.chatInput.text.toString().trim()
            binding.chatInput.text?.clear()
            val timeStamp = System.currentTimeMillis()
            if (binding.frameLayoutAttachedFiles.isVisible && miniatures.size > 0) {
                imageList.addAll(miniatures)
            }
            viewModel.insertMessage(
                MessageDto(
                    "151515",
                    true,
                    "Алексей Иванов",
                    "Геннадий Белов",
                    text,
                    MessageSendingState.Deliver,
                    timeStamp,
                    null,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null,
                    false, messageKindDto, false, imageList
                )
            )
            binding.frameLayoutAttachedFiles.isVisible = false
            miniatures.clear()
            binding.buttonSendMessage.isVisible = false
            binding.buttonAttach.isVisible = true
            binding.buttonRecord.isVisible = true
            binding.answer.isVisible = false
            messageAdapter?.notifyDataSetChanged()
            scrollDown()
        }
//        binding.buttonRecord.setOnTouchListener { _, motionEvent ->
//            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
//                binding.groupRecord.isVisible = true
//                binding.buttonEmoticon.isVisible = false
//                binding.buttonAttach.isVisible = false
//                binding.chatInput.isVisible = false
//                binding.recordChronometer.base = SystemClock.elapsedRealtime()
//                binding.recordChronometer.start()
//                AudioRecorder.startRecord()
//            } else if (motionEvent.action == MotionEvent.ACTION_UP) {
//                binding.groupRecord.isVisible = false
//                binding.buttonEmoticon.isVisible = true
//                binding.buttonAttach.isVisible = true
//                binding.chatInput.isVisible = true
//
//                binding.recordChronometer.stop()
//                binding.recordChronometer.base = SystemClock.elapsedRealtime()
//                AudioRecorder.stopRecord { file ->
//                    Toast.makeText(context, "${file == null}", Toast.LENGTH_SHORT).show()
//                }
//            }
//            true
//        }

        binding.messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy < 0) binding.btnDownward.animate()
                    .translationY(binding.btnDownward.height + binding.btnDownward.marginBottom.toFloat())
                else if (dy > 0) binding.btnDownward.animate()
                    .translationY(0f)
            }
        })
        binding.btnDownward.setOnClickListener {
            scrollDown()
        }

    }

    private fun scrollDown() {
        binding.messageList.scrollToPosition(0)
    }

    private fun updateTopDateIfNeed() {
        val layoutManager = binding.messageList.layoutManager as LinearLayoutManager
        val position = layoutManager.findFirstVisibleItemPosition()
// val message : MessageDto = messageAdapter!!.getItem(position)
// if (message != null)
// binding.tvTopDate.setText(StringUtils.getDateStringForMessage(message.t)
    }


    private fun chatInputAddListener() {
        with(binding) {
            chatInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

                }

                override fun afterTextChanged(p0: Editable?) {
                    if (p0.toString().trim().isNotEmpty()) {
                        buttonRecord.isVisible = false
                        buttonAttach.isVisible = false
                        buttonSendMessage.isVisible = true
                    } else {
                        buttonRecord.isVisible = true
                        buttonAttach.isVisible = true
                        buttonSendMessage.isVisible = false
                    }
                }
            })
        }

    }


    override fun onDestroy() {
        super.onDestroy()
        messageAdapter = null
        miniatureAdapter = null
        onBackPressedCallback.remove()
        AudioRecorder.releaseRecorder()
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("name", name)
        val messageText = binding.chatInput.text.toString().trim()
        outState.putString("message_text", messageText)
    }

    override fun copyText(text: String) {
        val clipBoard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("", text)
        clipBoard.setPrimaryClip(clipData)
    }

    override fun forwardMessage(messageDto: MessageDto) {
        //    navigator().showChatFragment()
    }

    override fun replyMessage(messageDto: MessageDto) {
        binding.answer.isVisible = true
        binding.replyMessageTitle.text = messageDto.owner
        binding.replyMessageContent.text = messageDto.messageBody
        binding.close.setOnClickListener {
            binding.replyMessageTitle.text = ""
            binding.replyMessageContent.text = ""
            binding.answer.isVisible = false
        }
    }

    override fun editMessage(primary: String) {
// binding.chatInput.text = primary
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLongClick(primary: String) {
        enableSelectionMode(true)

    }

    override fun deleteMessage(messageDto: MessageDto) {
        viewModel.deleteMessage(messageDto)
        messageAdapter?.notifyDataSetChanged()
    }

    override fun onRecentPhotosSend(paths: HashSet<String>?) {
        var messageKindDto: MessageKind? = null
        if (binding.answer.isVisible) {
            messageKindDto = MessageKind(
                "id",
                binding.replyMessageTitle.text.toString(),
                binding.replyMessageContent.text.toString()
            )
        }
        val imageList = ArrayList<FileDto>()
        val text = binding.chatInput.text.toString().trim()
        binding.chatInput.text?.clear()
        val timeStamp = System.currentTimeMillis()
        for (i in 0 until paths!!.size) {
            imageList.add(FileDto(File(paths.elementAt(i))))
        }


        viewModel.insertMessage(
            MessageDto(
                "151515",
                true,
                "Алексей Иванов",
                "Геннадий Белов",
                text,
                MessageSendingState.Deliver,
                timeStamp,
                null,
                MessageDisplayType.Text,
                false,
                false,
                null,
                false, messageKindDto, false, imageList
            )
        )


    }

    override fun onGalleryClick() {
        navigator().openGallery()
    }

    override fun onFilesClick() {
        navigator().openFiles()
    }

    override fun onCameraClick() {
        requestCameraPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun onGotCameraPermissionResult(granted: Boolean) {
        return if (granted) {
            takePhoto()
        } else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
            } else {
            }
        }
    }

    private fun takePhoto() {
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
        Log.d("data", "$storageDir")
        return storageDir
    }


    override fun onLocationClick() {

    }

    override fun onFullSwipe(position: Int) {

        replyMessage(
            MessageDto(
                "22222",
                true,
                "Ann",
                "Геннадий Белов",
                "Алексей присоединился к чату",
                MessageSendingState.Read,
                1654234345585,
                null,
                MessageDisplayType.System,
                false,
                false,
                null, false
            )
        )
    }

    override fun deleteFile(fileDto: FileDto) {
        miniatures.remove(fileDto)
        miniatureAdapter?.submitList(miniatures)
        miniatureAdapter?.notifyDataSetChanged()
        if (miniatures.size < 0) {
            binding.frameLayoutAttachedFiles.isVisible = false
            binding.buttonRecord.isVisible = true
            binding.buttonSendMessage.isVisible = false
        }
    }


    override fun disableNotifications() {
        binding.imNotificationsIsDisable.isVisible = true
        binding.imNotificationsIsDisable.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_bell_sleep,
                context?.theme
            )
        )
    }

    override fun disableNotificationsForever() {
        binding.imNotificationsIsDisable.isVisible = true
        binding.imNotificationsIsDisable.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_bell_off_forever,
                context?.theme
            )
        )


    }

    override fun enableNotifications() {
        binding.imNotificationsIsDisable.isVisible = false
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (messageAdapter?.getCheckBoxIsVisible() == true) {
                enableSelectionMode(false)
            } else {
                navigator().closeDetail()
            }
        }
    }

    private fun enableSelectionMode(enable: Boolean) {
        if (enable) {
            binding.messageToolbar.isVisible = false
            binding.linearForward.isVisible = true
            binding.linearReply.isVisible = true
            binding.toolbarSelectedMessages.isVisible = true
            binding.groupNormal.isVisible = false
            binding.buttonSendMessage.isVisible = false
            messageAdapter?.showCheckbox(true)
            messageAdapter?.notifyDataSetChanged()
        } else {
            messageAdapter?.showCheckbox(false)
            binding.groupNormal.isVisible = true
            binding.toolbarSelectedMessages.isVisible = false
            binding.linearReply.isVisible = false
            binding.linearForward.isVisible = false
            binding.messageToolbar.isVisible = true
            val textMessage = binding.chatInput.text.toString().trim()
            if (textMessage.isNotEmpty()) {
                binding.buttonSendMessage.isVisible = true
                binding.buttonAttach.isVisible = false
                binding.buttonRecord.isVisible = false
            }
        }
    }


    private fun addMediaToGallery(fromPath: String, bitmap: Bitmap? = null) {
        if (fromPath == null) {
            return
        }
        val f = File(fromPath)
        miniatures.add(FileDto(f, bitmap))
        val contentUri = Uri.fromFile(f)
        addMediaToGallery(contentUri)
    }

    private fun addMediaToGallery(uri: Uri) {
        if (uri == null) {
            return
        }
        try {
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(uri)
            activity?.sendBroadcast(mediaScanIntent);
        } catch (e: Exception) {
            Log.d("data", "error")
        }
    }


}
