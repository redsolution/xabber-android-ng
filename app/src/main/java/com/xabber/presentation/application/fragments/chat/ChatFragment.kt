package com.xabber.presentation.application.fragments.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.TranslateAnimation
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentChatBinding
import com.xabber.model.dto.MessageDto
import com.xabber.model.dto.MessageKind
import com.xabber.model.xmpp.messages.MessageDisplayType
import com.xabber.model.xmpp.messages.MessageSendingState
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.attach.AttachBottomSheet
import com.xabber.presentation.application.fragments.chat.audio.AudioRecorder
import com.xabber.presentation.application.fragments.chat.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.fragments.chat.dialogs.DeletingChatDialog
import com.xabber.presentation.application.fragments.chatlist.NotificationBottomSheet
import com.xabber.presentation.application.fragments.chatlist.SwitchNotifications
import com.xabber.presentation.application.util.askUserForOpeningAppSettings
import com.xabber.presentation.application.util.isPermissionGranted
import com.xabber.utils.mask.MaskPrepare
import java.util.*

class ChatFragment : DetailBaseFragment(R.layout.fragment_chat), MessageAdapter.Listener,
    ReplySwipeCallback.SwipeAction,
    SwitchNotifications {
    private val binding by viewBinding(FragmentChatBinding::bind)
    private var messageAdapter: MessageAdapter? = null
    private var miniatureAdapter: MiniatureAdapter? = null
    private val viewModel = ChatViewModel()
    var name: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var currentVoiceRecordingState = VoiceRecordState.NotRecording
    private var recordSaveAllowed = false
    private var recordingPath: String? = null
    private var stopTypingTimer: Timer? = Timer()
    private var saveAudioMessage = true
    private var lockIsClosed = false
    var isVibrate = false

    private val requestAudioPermissionResult = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotAudioPermissionResult
    )

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        ::onGotGalleryPermissionResult
    )

    private val timer = Runnable {
        binding.record.recordLayout.isVisible = true
        binding.record.linChronometr.isVisible = true
        binding.record.slideLayout.isVisible = true
        binding.record.slideLayout.alpha = 1.0f
        binding.linRecordLock.isVisible = true
        beginTimer(true)
    }

    private val shake = Runnable {
        val sh = AnimationUtils.loadAnimation(context, R.anim.shake)
        if (binding.imLock.animation == null) binding.imLock.startAnimation(sh)
        if (binding.imLockBar.animation == null) binding.imLockBar.startAnimation(sh)
    }

    private val record = Runnable {
        shortVibrate()
        binding.btnRecordExpanded.show()
        context?.let { VoiceManager.startRecording(it) }
    }

    private val stop = Runnable {
        binding.imLockBar.clearAnimation()
        binding.imLock.clearAnimation()
        binding.linRecordLock.clearAnimation()


        val bot = TranslateAnimation(0f, 0f, 0f, 40f)
        bot.duration = 200L
        bot.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {

            }

            override fun onAnimationEnd(p0: Animation?) {

                binding.linRecordLock.isVisible = false
                binding.frameStop.isVisible = true
                val pulse = AnimationUtils.loadAnimation(context, R.anim.enlarge)
                binding.imStop.startAnimation(pulse)
                val animTop = AnimationUtils.loadAnimation(context, R.anim.to_top)
                binding.record.slideLayout.isVisible = false
                binding.record.cancelRecordLayout.isVisible = true
                currentVoiceRecordingState = VoiceRecordState.StoppedRecording
            }

            override fun onAnimationRepeat(p0: Animation?) {
            }

        })

        binding.imLockBar.animate().y(0f).translationY(25f).setDuration(200).start()
        binding.imLockBar.isVisible = false
        binding.imLock.setImageResource(R.drawable.grey_square)
        binding.linRecordLock.startAnimation(bot)
    }

    companion object {
        fun newInstance(params: ChatParams): ChatFragment {
            val args = Bundle().apply {
                putParcelable(AppConstants.CHAT_PARAMS, params)
            }
            val fragment = ChatFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private fun getParams(): ChatParams =
        requireArguments().getParcelable(AppConstants.CHAT_PARAMS)!!


    private fun onGotAudioPermissionResult(granted: Boolean) {
        if (!granted) askUserForOpeningAppSettings()
    }

    private fun onGotGalleryPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value }) {
            context?.let {
                if (childFragmentManager.findFragmentByTag(AttachBottomSheet.TAG) == null) {
                    AttachBottomSheet().show(childFragmentManager, AttachBottomSheet.TAG)
                }
            }
        } else {
            askUserForOpeningAppSettings()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            name = savedInstanceState.getString("name", "")
            val messageText = savedInstanceState.getString("message_text", "")
            binding.chatInput.setText(messageText)
            if (messageText.length > 0) {
                Log.d("ooo", "$messageText")
                binding.btnRecord.isVisible = false
                binding.buttonAttach.isVisible = false
                binding.buttonSendMessage.isVisible = true
                Log.d("ooo", "${binding.btnRecord.isVisible}")
            }
        }

        populateUiWithData()
        subscribeViewModelData()
        initToolbarActions()
        initSelectMessageToolbarActions()
        initRecyclerView()
        initStandardInputLayoutActions()
    }

    private fun populateUiWithData() {
        loadAvatarWithMask()
        binding.messageUserName.text = getParams().chatListDto.displayName
    }

    private fun loadAvatarWithMask() {
        val maskedDrawable = MaskPrepare.getDrawableMask(
            resources,
            getParams().chatListDto.drawableId,
            UiChanger.getMask().size48
        )
        binding.imAvatar.setImageDrawable(maskedDrawable)
    }

    private fun subscribeViewModelData() {
        viewModel.initList()
        viewModel.messages.observe(viewLifecycleOwner) {
            it.sort()
            messageAdapter?.submitList(it)
        }
    }

    private fun initToolbarActions() {
        if (!DisplayManager.isDualScreenMode()) {
            binding.messageToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
            binding.messageToolbar.setNavigationOnClickListener {
                navigator().closeDetail()
            }
        } else binding.messageToolbar.navigationIcon = null
        binding.messageToolbar.setOnClickListener {
            //"Перейти на страницу контакта"
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

    private fun initSelectMessageToolbarActions() {
        binding.selectMessagesToolbar.imCloseSelectedMode.setOnClickListener {
            enableSelectionMode(false)
        }
    }

    private fun initRecyclerView() {
        messageAdapter = MessageAdapter(this)
        binding.messageList.adapter = messageAdapter
        val linearLayoutManager = LinearLayoutManager(context)
        linearLayoutManager.reverseLayout = true
        binding.messageList.layoutManager = linearLayoutManager
        binding.messageList.addItemDecoration(MessageHeaderViewDecoration())
        addSwipeCallback()
        addScrollListener()
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

    private fun addScrollListener() {
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

    private fun initStandardInputLayoutActions() {
        chatInputAddListener()
        initButtonEmoticon()
        initButtonAttach()
        initButtonSend()
        initButtonRecord()
    }

    private fun chatInputAddListener() {
        with(binding) {
            chatInput.addTextChangedListener(object : TextWatcher {

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                      Log.d("ooo", "po before ${binding.btnRecord.isVisible}")
                }
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun afterTextChanged(p0: Editable?) {
                    Log.d("ooo", "p0 after${p0.toString()}")
                    if (p0.toString().trim().isNotEmpty()) {
                        btnRecord.isVisible = false
                        buttonAttach.isVisible = false
                        buttonSendMessage.isVisible = true
                    } else {
                        btnRecord.isVisible = true
                        buttonAttach.isVisible = true
                        buttonSendMessage.isVisible = false
                    }
                }
            })
        }
    }

    private fun initButtonEmoticon() {
        binding.buttonEmoticon.setOnClickListener { }
    }

    private fun initButtonAttach() {
        binding.buttonAttach.setOnClickListener {
            requestGalleryPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun initButtonSend() {
        binding.buttonSendMessage.setOnClickListener {
            var messageKindDto: MessageKind? = null
            if (binding.answer.isVisible) {
                messageKindDto = MessageKind(
                    "id",
                    binding.replyMessageTitle.text.toString(),
                    binding.replyMessageContent.text.toString()
                )
            }

            val text = binding.chatInput.text.toString().trim()
            binding.chatInput.text?.clear()
            val timeStamp = System.currentTimeMillis()

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
                    false, messageKindDto, false, null
                )
            )

            binding.buttonSendMessage.isVisible = false
            binding.buttonAttach.isVisible = true
            binding.btnRecord.isVisible = true
            binding.answer.isVisible = false
            messageAdapter?.notifyDataSetChanged()
            scrollDown()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initButtonRecord() {

        binding.btnRecord.setOnTouchListener { _: View?, motionEvent: MotionEvent ->
            when (motionEvent.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    if (isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
                        startAudioRecord()
                    } else {
                        requestAudioPermissionResult.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (binding.imLock.animation != null) currentVoiceRecordingState =
                        VoiceRecordState.StoppedRecording
                    if (currentVoiceRecordingState == VoiceRecordState.StoppedRecording) {
                        binding.buttonAttach.isVisible = false
                        handler.post(stop)
                    } else {
                        if (currentVoiceRecordingState == VoiceRecordState.TouchRecording) {
                            sendVoiceMessage()
                            binding.buttonAttach.isVisible = false
                            navigator().lockScreen(false)
                        }


                        binding.record.chrRecordingTimer.stop()

                        val animRight =
                            AnimationUtils.loadAnimation(context, R.animator.slide_to_right)
                        binding.record.recordLayout.startAnimation(animRight)
                        binding.record.recordLayout.isVisible = false
                        binding.linRecordLock.isVisible = false
                        binding.btnRecordExpanded.isVisible = false
                        handler.removeCallbacks(record)
                        handler.removeCallbacks(timer)
                        stopTypingTimer?.cancel()
                        navigator().lockScreen(false)
                        if (saveAudioMessage) sendMessage("Audio message", null)
                        currentVoiceRecordingState = VoiceRecordState.NotRecording
                        binding.buttonAttach.isVisible = true
                    }
                }
                MotionEvent.ACTION_MOVE -> {

                    when {
                        motionEvent.y < -55 -> {

                            val params =
                                binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams
                            params.bottomMargin = 0
                            binding.imLockBar.layoutParams = params
                            lockIsClosed = true
                            if (!isVibrate) shortVibrate()
                            isVibrate = true
                            handler.post(shake)
//
//                            if (currentVoiceRecordingState != VoiceRecordState.StoppedRecording) {
//                                currentVoiceRecordingState = VoiceRecordState.StoppedRecording
//                                handler.postDelayed(
//                                    stop,
//                                    500
//                                )

                            //     }
                        }

                        motionEvent.y < 0 -> {
                            isVibrate = false
                            binding.imLock.clearAnimation()
                            binding.imLockBar.clearAnimation()
                            Log.d("ooz", "anim ${binding.imLock.animation}")
                            binding.spaceLock.animate().y(motionEvent.y).setDuration(0).start()
                            val params =
                                binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams
                            params.bottomMargin = -motionEvent.y.toInt() / 4
                            Log.d("ooz", "${params.bottomMargin}, ${motionEvent.y}")
                            if (params.bottomMargin >= 2 && params.bottomMargin < 12) binding.imLockBar.layoutParams =
                                params


                        }
                    }
                    val alpha = 1f + motionEvent.x / 400f
                    // Если идет запись
                    if (motionEvent.x < 0) {
                        binding.record.slideLayout.animate().x(motionEvent.x).setDuration(0).start()
                    } else binding.record.slideLayout.animate().x(0f).setDuration(0).start()

                    binding.record.slideLayout.alpha = alpha

                    //since alpha and slide are tied together, we can cancel recording by checking transparency value
                    if (alpha <= 0) {
                        clearVoiceMessage()
                        saveAudioMessage = false
                        val animRight =
                            AnimationUtils.loadAnimation(context, R.animator.slide_to_right)
                        binding.record.recordLayout.startAnimation(animRight)
                        binding.record.recordLayout.isVisible = false
                    }
                }
            }; true
        }


        binding.frameStop.setOnClickListener {
            binding.frameStop.isVisible = false
            binding.linRecordLock.isVisible = false
            binding.record.slideLayout.isVisible = false
            binding.record.linChronometr.isVisible = false
            binding.btnRecordExpanded.hide()
            binding.buttonAttach.isVisible = false
            binding.record.recordingPresenterLayout.isVisible = true
        }

        binding.record.tvCancelRecording.setOnClickListener {
            clearVoiceMessage()
        }

        binding.record.voicePresenterDelete.setOnClickListener { clearVoiceMessage() }

        binding.record.btnSendStop.setOnClickListener {
            sendMessage("Audio message", null)
            clearVoiceMessage()
        }

        binding.btnRecordExpanded.setOnClickListener {
            sendMessage("Audio message", null)
            clearVoiceMessage()
        }

    }

    private fun startAudioRecord() {
        if (currentVoiceRecordingState == VoiceRecordState.NotRecording) {
            binding.record.slideLayout.x = 0f
            binding.buttonAttach.isVisible = false
            binding.record.cancelRecordLayout.isVisible = false
            recordSaveAllowed = false
            handler.postDelayed(record, 0)
            handler.postDelayed(timer, 0)
            currentVoiceRecordingState = VoiceRecordState.InitiatedRecording
            navigator().lockScreen(true)
            saveAudioMessage = true
        }
    }

    private fun manageScreenSleep(keepScreenOn: Boolean) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun sendVoiceMessage() {
        manageVoiceMessage(recordSaveAllowed)
        scrollDown()
        clearVoiceMessage()
        // setFirstUnreadMessageId(null)
    }

    private fun beginTimer(start: Boolean) {
        if (start) {
            stopTypingTimer?.cancel()
            binding.record.chrRecordingTimer.base = SystemClock.elapsedRealtime()
            binding.record.chrRecordingTimer.start()
            currentVoiceRecordingState = VoiceRecordState.TouchRecording
        } else {
            binding.record.chrRecordingTimer.stop()
        }
    }


    private fun changeStateOfInputViewButtonsTo(state: Boolean) {
//        binding.buttonEmoticon.isEnabled = state
//        binding.buttonAttach.isEnabled = state
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

    private fun clearVoiceMessage() {
        isVibrate = false
        binding.record.recordLayout.clearAnimation()
        binding.record.recordLayout.x = 0f
        binding.record.slideLayout.x = 0f
        binding.record.slideLayout.clearAnimation()
        binding.imLock.setImageResource(R.drawable.ic_lock_base)
        binding.imLockBar.isVisible = true
        binding.record.recordingPresenterLayout.isVisible = false
        binding.frameStop.clearAnimation()
        binding.frameStop.isVisible = false

        binding.record.recordLayout.isVisible = false
        binding.buttonAttach.isVisible = true

        binding.btnRecordExpanded.hide()
        binding.btnRecordExpanded.isVisible = false
        binding.spaceLock.clearAnimation()
        binding.spaceLock.y = 0f
        binding.imLockBar.y = 22f
        binding.linRecordLock.isVisible = false
        manageVoiceMessage(false)
        val params = binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams
        params.bottomToBottom = binding.imLock.id
        params.bottomMargin = 2
        binding.imLockBar.layoutParams = params
        currentVoiceRecordingState = VoiceRecordState.NotRecording
        lockIsClosed = false
    }

    private fun manageVoiceMessage(saveMessage: Boolean) {
        handler.removeCallbacks(record)
        handler.removeCallbacks(timer)
        stopRecordingAndSend(saveMessage)
    }

    private fun stopRecordingAndSend(send: Boolean) {
        if (send) {
            sendMessage("Audio Message", null)
            currentVoiceRecordingState = VoiceRecordState.NotRecording
        } else {
            currentVoiceRecordingState = VoiceRecordState.NotRecording
        }
    }


    private fun updateTopDateIfNeed() {
        val layoutManager = binding.messageList.layoutManager as LinearLayoutManager
        val position = layoutManager.findFirstVisibleItemPosition()
// val message : MessageDto = messageAdapter!!.getItem(position)
// if (message != null)
// binding.tvTopDate.setText(StringUtils.getDateStringForMessage(message.t)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("name", name)
        val messageText = binding.chatInput.text.toString().trim()
        outState.putString("message_text", messageText)
    }

    override fun onDestroy() {
        super.onDestroy()
        messageAdapter = null
        miniatureAdapter = null
        onBackPressedCallback.remove()
        AudioRecorder.releaseRecorder()
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

    override fun disableNotifications() {
        binding.imNotificationsIsDisable.isVisible = true
        binding.imNotificationsIsDisable.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_bell_sleep_grey,
                context?.theme
            )
        )
    }

    override fun disableNotificationsForever() {
        binding.imNotificationsIsDisable.isVisible = true
        binding.imNotificationsIsDisable.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources,
                R.drawable.ic_bell_off_forever_grey,
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
            binding.appbar.setBackgroundResource(R.color.white)
            binding.messageToolbar.isVisible = false
            binding.selectMessagesToolbar.toolbarSelectedMessages.isVisible = true
            saveDraft()
            binding.chatPanelGroup.isVisible = false
            binding.interaction.interactionView.isVisible = true
            messageAdapter?.showCheckbox(true)
            messageAdapter?.notifyDataSetChanged()
        } else {
            binding.appbar.setBackgroundResource(R.color.blue_500)
            messageAdapter?.showCheckbox(false)
            binding.chatPanelGroup.isVisible = true
            binding.selectMessagesToolbar.toolbarSelectedMessages.isVisible = false
            binding.interaction.interactionView.isVisible = false
            binding.messageToolbar.isVisible = true
            val textMessage = binding.chatInput.text.toString().trim()
            if (textMessage.isNotEmpty()) {
                binding.buttonSendMessage.isVisible = true
                binding.buttonAttach.isVisible = false
                binding.btnRecord.isVisible = false
            }
        }
    }

    private fun saveDraft() {

    }

    fun sendMessage(textMessage: String, imagePaths: HashSet<String>?) {
        var messageKindDto: MessageKind? = null
        if (binding.answer.isVisible) {
            messageKindDto = MessageKind(
                "id",
                binding.replyMessageTitle.text.toString(),
                binding.replyMessageContent.text.toString()
            )
        }
        val imageList = ArrayList<String>()
        if (imagePaths != null) {
            imagePaths!!.forEach {

                imageList.add(it)

            }
        }
        val timeStamp = System.currentTimeMillis()

        viewModel.insertMessage(
            MessageDto(
                "151515",
                true,
                "Алексей Иванов",
                "Геннадий Белов",
                textMessage,
                MessageSendingState.Deliver,
                timeStamp,
                null,
                MessageDisplayType.Text,
                false,
                false,
                null,
                false, messageKindDto, false, null, imageList
            )
        )
        messageAdapter?.notifyDataSetChanged()
        binding.answer.isVisible = false
    }


}
