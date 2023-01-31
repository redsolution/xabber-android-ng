package com.xabber.presentation.application.fragments.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.TranslateAnimation
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.aghajari.emojiview.AXEmojiManager
import com.aghajari.emojiview.googleprovider.AXGoogleEmojiProvider
import com.aghajari.emojiview.view.AXSingleEmojiView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.databinding.FragmentChatBinding
import com.xabber.models.dto.ChatListDto
import com.xabber.models.dto.MessageDto
import com.xabber.models.dto.MessageKind
import com.xabber.models.xmpp.messages.MessageDisplayType
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.presentation.AppConstants
import com.xabber.presentation.AppConstants.CHAT_MESSAGE_TEXT_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_BUNDLE_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_DIALOG_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_FOR_ALL_BUNDLE_KEY
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.bottomsheet.NotificationBottomSheet
import com.xabber.presentation.application.bottomsheet.TimeMute
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.dialogs.DeletingMessageDialog
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.attach.AttachBottomSheet
import com.xabber.presentation.application.fragments.chat.audio.AudioRecorder
import com.xabber.presentation.application.fragments.chat.audio.VoiceManager
import com.xabber.presentation.application.fragments.chat.geo.Location
import com.xabber.presentation.application.fragments.chat.message.*
import com.xabber.presentation.application.fragments.contacts.vcard.ContactAccountParams
import com.xabber.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class ChatFragment : DetailBaseFragment(R.layout.fragment_chat), ChatAdapter.Listener,
    ReplySwipeCallback.SwipeAction {
    private val binding by viewBinding(FragmentChatBinding::bind)
    private val handler = Handler(Looper.getMainLooper())
    private var chatAdapter: ChatAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private val viewModel: ChatViewModel by viewModels()
    private var isNeedScrollDown = false
    private var editMessageId: String? = null
    private val enableNotificationsCode = 0L
    private var replySwipeCallback: ReplySwipeCallback? = null
    private var isSelectedMode = false
    private var replyingMessage: MessageDto? = null
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

    private val requestImagesAndVideoPermissionResult = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        ::onGotImagesAndVideoPermissionResult
    )

    private val cancelSelected = Runnable {
        viewModel.clearAllSelected()
        viewModel.getMessageList(getParams().opponentJid)
    }

    private val reply = Runnable {
        if (replyingMessage != null)
            replyMessage(
                replyingMessage!!
            )
    }

    private val timer = Runnable {
        prepareUiForRecording()
        beginTimer(true)
        currentVoiceRecordingState = VoiceRecordState.TouchRecording
        Log.d("ooo", "in timer $currentVoiceRecordingState")
    }

    private val record = Runnable {
        VoiceManager.getInstance().startRecording()
    }

    private val shake = Runnable {
        val shaker = AnimationUtils.loadAnimation(context, R.anim.shake)
        if (binding.imLock.animation == null) binding.imLock.startAnimation(shaker)
        if (binding.imLockBar.animation == null) binding.imLockBar.startAnimation(shaker)
    }

    private val stop = Runnable {
        Log.d("ooo", "stop")
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
        requireArguments().parcelable(AppConstants.CHAT_PARAMS)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) restoreState(savedInstanceState)
        else {
            restoreDraft()
            scrollToLastPosition()
        }
        prepareUi()
        initializeToolbarActions()
        initializeRecyclerView()
        initializeStandardInputLayoutActions()
        subscribeToChatData()
        initializeSelectMessageToolbarActions()
        initializeSelectedMessagePanel()
        viewModel.initChatDataListener(getParams().id)
        viewModel.initMessagesListener(getParams().opponentJid)
        viewModel.getChat(getParams().id)
        activity?.onBackPressedDispatcher?.addCallback(onBackPressedCallback)
        setFragmentResultListener(AppConstants.TURN_OFF_NOTIFICATIONS_KEY) { _, bundle ->
            val resultMuteExpired =
                bundle.getLong(AppConstants.TURN_OFF_NOTIFICATIONS_BUNDLE_KEY) + System.currentTimeMillis()
            viewModel.setMute(getParams().id, resultMuteExpired)
            binding.messageToolbar.menu.findItem(R.id.enable_notifications).isVisible = true
            binding.messageToolbar.menu.findItem(R.id.disable_notifications).isVisible = false
        }

        setFragmentResultListener(AppConstants.DELETING_CHAT_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.DELETING_CHAT_BUNDLE_KEY)
            if (result) {
                viewModel.deleteChat(getParams().id)
                navigator().closeDetail()
            }
        }

        setFragmentResultListener(AppConstants.CLEAR_HISTORY_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.CLEAR_HISTORY_BUNDLE_KEY)
            if (result) viewModel.clearHistory(
                getParams().id,
                getParams().owner,
                getParams().opponentJid
            )
        }
    }

    private fun restoreState(savedInstanceState: Bundle) {
        val messageText = savedInstanceState.getString(CHAT_MESSAGE_TEXT_KEY)
        binding.chatInput.setText(messageText)
        isSelectedMode = savedInstanceState.getBoolean(AppConstants.CHAT_SELECTION_MODE_KEY)
        enableSelectionMode(isSelectedMode)
    }

    private fun restoreDraft() {
        val draft = viewModel.getChat(getParams().id)?.draftMessage
        Log.d("iii", "draft = $draft")
        if (draft != null) binding.chatInput.setText(draft)
        setupInputButtons()
    }

    private fun scrollToLastPosition() {
        val lastPosition = viewModel.getChat(getParams().id)?.lastPosition
        if (!lastPosition.isNullOrEmpty()) {
            val messagePosition =
                viewModel.getPositionMessage(lastPosition)
            binding.messageList.scrollToPosition(messagePosition)
            viewModel.saveLastPosition(getParams().id, "")
        }
    }

    private fun setupInputButtons() {
        binding.btnRecord.isVisible = binding.chatInput.text.toString().isEmpty()
        binding.buttonAttach.isVisible = binding.chatInput.text.toString().isEmpty()
        binding.buttonSendMessage.isVisible = binding.chatInput.text.toString().isNotEmpty()
    }

    private fun prepareUi() {
        loadAvatarWithMask()
        val chat = viewModel.getChat(getParams().id)
        if (chat != null) {
            setTitle(chat)
            setStatus(chat.status)
            setupMuteIcon(chat.muteExpired)
        }
    }

    private fun loadAvatarWithMask() {
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(requireContext()).load(getParams().avatar)
            .error(R.drawable.ic_avatar_placeholder)
            .apply(RequestOptions.bitmapTransform(multiTransformation))
            .into(binding.avatarGroup.imAvatar)
    }

    private fun setTitle(chat: ChatListDto) {
        val chatName =
            if (chat.customNickname.isNotEmpty()) chat.customNickname else if (chat.opponentNickname.isNotEmpty()) chat.opponentNickname else chat.opponentJid
        binding.tvChatTitle.text = chatName
    }

    private fun setupMuteIcon(muteExpired: Long) {
        val imageResource =
            if (muteExpired - System.currentTimeMillis() <= 0) null else if (
                (muteExpired - System.currentTimeMillis()) > TimeMute.DAY1.time)
                R.drawable.ic_bell_off_light_grey_mini else R.drawable.ic_bell_sleep_light_grey_mini
        var drawable: Drawable? = null
        if (imageResource != null) drawable =
            ContextCompat.getDrawable(requireContext(), imageResource)
        binding.tvChatTitle.setCompoundDrawablesWithIntrinsicBounds(
            null, null, drawable, null
        )
    }

    private fun setStatus(status: ResourceStatus) {
    }

    private fun initializeToolbarActions() {
        binding.imBack.isVisible = !DisplayManager.isDualScreenMode()
        binding.imBack.setOnClickListener {
            navigator().closeDetail()
        }

        binding.avatarGroup.imAvatar.setOnClickListener {
            val contactId = viewModel.getContactPrimary(getParams().id)
            if (contactId != null) navigator().showContactAccount(
                ContactAccountParams(
                    contactId,
                    getParams().avatar, viewModel.getColor(getParams().id)
                )
            )
        }
        setupToolbarMenu()
    }

    private fun setupToolbarMenu() {
        val chat = viewModel.getChat(getParams().id)
        val muteExpired =
            if (
                chat != null
            ) viewModel.getChat(getParams().id)!!.muteExpired - System.currentTimeMillis() else 0
        binding.messageToolbar.menu.findItem(R.id.enable_notifications).isVisible =
            muteExpired > 0
        binding.messageToolbar.menu.findItem(R.id.disable_notifications).isVisible =
            muteExpired <= 0
        binding.messageToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.call_out -> sendIncomingMessages()
                R.id.disable_notifications -> disableNotifications()
                R.id.enable_notifications -> enableNotifications()
                R.id.clear_message_history -> clearHistory()
                R.id.delete_chat -> deleteChat()
            }; true
        }
    }

    private fun disableNotifications() {
        val dialog = NotificationBottomSheet()
        navigator().showBottomSheetDialog(dialog)

    }

    private fun enableNotifications() {
        viewModel.setMute(getParams().id, enableNotificationsCode)
        binding.messageToolbar.menu.findItem(R.id.enable_notifications).isVisible = false
        binding.messageToolbar.menu.findItem(R.id.disable_notifications).isVisible = true
    }

    private fun clearHistory() {
        val dialog = ChatHistoryClearDialog()
        navigator().showDialogFragment(dialog, AppConstants.CLEAR_HISTORY_DIALOG_TAG)

    }

    private fun deleteChat() {
        val dialog = DeletingChatDialog.newInstance(binding.tvChatTitle.text.toString())
        navigator().showDialogFragment(dialog, AppConstants.DELETING_CHAT_DIALOG_TAG)

    }

    private fun initializeRecyclerView() {
        chatAdapter = ChatAdapter(this)
        binding.messageList.adapter = chatAdapter
        layoutManager = LinearLayoutManager(context)
        layoutManager?.stackFromEnd = true
        binding.messageList.layoutManager = layoutManager
        addSwipeCallback()
        // addMessageHeaderViewDecoration()
        addScrollListener()
        fillChat()
        binding.messageList.itemAnimator = null
//binding.messageList.addItemDecoration(MessageHeaderViewDecoration(requireContext()))
    }

    private fun addSwipeCallback() {
        replySwipeCallback = ReplySwipeCallback(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.reply_circle
            )!!
        ) { position: Int ->
            val id = chatAdapter?.getPositionId(position)
            if (id != null) {
                replyingMessage = viewModel.getMessage(id)
                handler.postDelayed(reply, 0)
            }
        }

        ItemTouchHelper(replySwipeCallback!!).attachToRecyclerView(binding.messageList)

        binding.messageList.addItemDecoration(
            object : RecyclerView.ItemDecoration() {
                override fun onDraw(
                    c: Canvas,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    replySwipeCallback?.onDraw(c)
                }
            })
    }

    private fun addMessageHeaderViewDecoration() {
        // binding.messageList.addItemDecoration(MessageHeaderViewDecoration(requireContext()))
    }

    // @ExperimentalBadgeUtils
    private fun addScrollListener() {

//        GlobalScope.launch(Dispatchers.IO) {
//            delay(5000)
//        val last = chatAdapter!!.getPositionId(layoutManager!!.findLastVisibleItemPosition())
//        val realm = Realm.open(defaultRealmConfig())
//        realm.writeBlocking {
//            val m = this.query(MessageStorageItem::class, "primaty = '$last").first().find()
//            m?.isRead = true
//        }

        binding.messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                //  binding.-tvTopDate.isVisible = true
                if (layoutManager != null) {
                    if (layoutManager!!.findLastVisibleItemPosition() >= chatAdapter!!.itemCount - 1) {
                        binding.downScroller.isVisible = false
                    } else {
                        binding.downScroller.isVisible = true
                    }
                }
            }
        })
        binding.btnDownward.setOnClickListener {
            if (viewModel.unreadCount.value == 0 || viewModel.unreadCount.value == null) {
                scrollDown()
                binding.tvNewReceivedCount.text = ""
                binding.tvNewReceivedCount.isVisible = false
            } else scrollToFirstUnread()
        }
    }

    private fun scrollToFirstUnread() {
        layoutManager?.scrollToPositionWithOffset(
            chatAdapter!!.itemCount - viewModel.unreadCount.value!!,
            200
        )
    }

    private fun fillChat() {
        viewModel.getMessageList(getParams().opponentJid)
    }

    private fun initializeStandardInputLayoutActions() {
        chatInputAddListener()
        initializeButtonEmoji()
        initializeButtonAttach()
        initializeButtonSend()
        initializeButtonRecord()
    }

    private fun chatInputAddListener() {
        binding.chatInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun afterTextChanged(p0: Editable?) {
                setupInputButtons()
            }
        })
    }

    private fun initializeButtonEmoji() {
        AXEmojiManager.install(requireContext(), AXGoogleEmojiProvider(requireContext()))
        val emojiView = AXSingleEmojiView(requireContext())

        emojiView.editText = binding.chatInput
        binding.emojiPopupLayout.initPopupView(emojiView)
        binding.buttonEmoticon.setOnClickListener {
            if (binding.emojiPopupLayout.isShowing) {
                binding.buttonEmoticon.setImageResource(R.drawable.ic_emoticon_outline)
                binding.emojiPopupLayout.hideAndOpenKeyboard()
            } else {
                binding.buttonEmoticon.setImageResource(R.drawable.ic_keyboard)
                binding.emojiPopupLayout.toggle()
                binding.chatInput.showSoftInputOnFocus = false
            }
        }
    }

    private fun initializeButtonAttach() {
        binding.buttonAttach.setOnClickListener {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                if (ContextCompat.checkSelfPermission(
//                        requireContext(),
//                        Manifest.permission.READ_MEDIA_IMAGES
//                    ) ==
//                    PackageManager.PERMISSION_GRANTED
//                    && ContextCompat.checkSelfPermission(
//                        requireContext(),
//                        Manifest.permission.READ_MEDIA_VIDEO
//                    ) ==
//                    PackageManager.PERMISSION_GRANTED
//                ) {
//                } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
//                    askUserForOpeningAppSettings()
//                } else {
//                    requestImagesAndVideoPermissionResult.launch(
//                        arrayOf(
//                            Manifest.permission.READ_MEDIA_IMAGES,
//                            Manifest.permission.READ_MEDIA_VIDEO
//                        )
//                    )
//                }
//            } else {
            requestGalleryPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
            //    }
        }
    }

    private fun initializeButtonSend() {
        binding.buttonSendMessage.setOnClickListener {
            if (editMessageId != null) {
                viewModel.editMessage(editMessageId!!, binding.chatInput.text.toString())
                binding.chatInput.text?.clear()
                editMessageId = null
            } else {
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
                    getParams().id,
                    MessageDto(
                        "$timeStamp",
                        true,
                        "Иван Иванов",
                        getParams().opponentJid,
                        text,
                        MessageSendingState.Deliver,
                        timeStamp,
                        0,
                        MessageDisplayType.Text,
                        false,
                        false,
                        null,
                        false, messageKindDto, false, null, null, Location(2.8604, 14.540)
                    ),
                    true
                )
                binding.answer.isVisible = false
                isNeedScrollDown = true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeButtonRecord() {

        binding.btnRecord.setOnTouchListener { _: View?, motionEvent: MotionEvent ->
            Log.d("ooo", "$motionEvent")
            when (motionEvent.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    if (isPermissionGranted(
                            Manifest.permission.RECORD_AUDIO
                        )
                    ) {
                        if (currentVoiceRecordingState == VoiceRecordState.NotRecording)
                            startAudioRecord()
                        recordSaveAllowed = false
                        currentVoiceRecordingState = VoiceRecordState.InitiatedRecording
                        navigator().lockScreen(true)
                    } else {
                        requestAudioPermissionResult.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (currentVoiceRecordingState == VoiceRecordState.InitiatedRecording) {
                        handler.removeCallbacks(record)
                        handler.removeCallbacks(timer)
                        hideRecordPanel()
                        enabledInputPanelButtons(true)
                        currentVoiceRecordingState = VoiceRecordState.NotRecording
                        navigator().lockScreen(false)
                    } else if (currentVoiceRecordingState == VoiceRecordState.TouchRecording) {
                        sendVoiceMessage()
                        navigator().lockScreen(false)
                    }
//                    if (binding.imLock.animation != null) currentVoiceRecordingState =
//                        VoiceRecordState.StoppedRecording
                    if (currentVoiceRecordingState == VoiceRecordState.NoTouchRecording) {
                        handler.post(stop)
                    } else {
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
                        if (saveAudioMessage && isPermissionGranted(Manifest.permission.RECORD_AUDIO)) sendMessage(
                            "Audio message",
                            null
                        )
                        currentVoiceRecordingState = VoiceRecordState.NotRecording
                        //   binding.buttonAttach.isVisible = true
                        Log.d("yyy", "record")
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    Log.d("ooo", "y = ${motionEvent.y}")
                    when {
                        motionEvent.y < -55 -> {
                            val params =
                                binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams
                            params.bottomMargin = 0
                            binding.imLockBar.layoutParams = params
                            if (!isVibrate) shortVibrate()
                            lockIsClosed = true
                            isVibrate = true
                            currentVoiceRecordingState = VoiceRecordState.NoTouchRecording
                            handler.post(shake)
                        }
                        motionEvent.y < 0 -> {

                            isVibrate = false
                            binding.imLock.clearAnimation()
                            binding.imLockBar.clearAnimation()
                            binding.spaceLock.animate().y(motionEvent.y).start()
                            val params =
                                binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams
                            params.bottomMargin = -motionEvent.y.toInt() / 4
                            if (params.bottomMargin in 2..11) binding.imLockBar.layoutParams =
                                params
                            currentVoiceRecordingState = VoiceRecordState.TouchRecording
                        }
                    }
                    val alpha = 1f + motionEvent.x / 400f
                    // Если идет запись
                    if (motionEvent.x < 0) {
                        binding.record.slideLayout.animate().x(motionEvent.x).start()
                    } else binding.record.slideLayout.animate().x(0f).start()

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
            //  binding.buttonAttach.isVisible = false
            binding.record.recordingPresenterLayout.isVisible = true
            binding.record.voicePresenterTime.text =
                binding.record.chrRecordingTimer.text.toString()
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
            if (isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
                sendMessage("Audio message", null)
                clearVoiceMessage()
            }
        }

    }

    private fun subscribeToChatData() {
        viewModel.opponentName.observe(viewLifecycleOwner) {
            setupOpponentName(it)
        }

        viewModel.muteExpired.observe(viewLifecycleOwner) {
            setupMuteIcon(it)
        }

        viewModel.messages.observe(viewLifecycleOwner) {
            chatAdapter?.submitList(it) {
                chatAdapter?.notifyDataSetChanged()
                if (layoutManager != null && chatAdapter != null) {
                    if (layoutManager!!.findLastVisibleItemPosition() >= chatAdapter!!.itemCount - 2 && !isSelectedMode) scrollDown()
                    if (isNeedScrollDown) {
                        scrollDown()
                        isNeedScrollDown = false
                    }
                }
            }
        }

        viewModel.unreadCount.observe(viewLifecycleOwner) {
            if (it > 0) {
                binding.tvNewReceivedCount.isVisible = true
                binding.tvNewReceivedCount.text = it.toString()
            } else {
                binding.tvNewReceivedCount.isVisible = false
                binding.tvNewReceivedCount.text = ""
            }
        }

        viewModel.selectedCount.observe(viewLifecycleOwner) {
            if (it > 0) {
                binding.selectMessagesToolbar.tvMessagesCount.text = it.toString()
                binding.selectMessagesToolbar.toolbarSelectedMessages.menu.findItem(R.id.edit_message).isVisible =
                    it == 1 && viewModel.isOutgoing()
                binding.interaction.linReply.isVisible = it == 1
            } else {
                enableSelectionMode(false)
            }
        }
    }

    private fun setupOpponentName(opponentName: String) {
        binding.tvChatTitle.text = opponentName
    }

    private fun initializeSelectMessageToolbarActions() {
        binding.selectMessagesToolbar.imCloseSelectedMode.setOnClickListener {
            enableSelectionMode(false)
        }
        binding.selectMessagesToolbar.toolbarSelectedMessages.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.edit_message -> {
                    edit()
                    enableSelectionMode(false)
                }
                R.id.copy_message -> {
                    copyTextMessage()
                    enableSelectionMode(false)
                }
                R.id.delete_message -> {
                    delete()
                }
            }; true
        }
    }

    private fun initializeSelectedMessagePanel() {
        binding.interaction.linReply.setOnClickListener {
            val message = viewModel.getMessage()
            enableSelectionMode(false)
            if (message != null) replyMessage(message)
        }
        binding.interaction.linForward.setOnClickListener {
            val text = viewModel.getForwardMessagesText()
            enableSelectionMode(false)
            navigator().showForwardFragment(text)
        }
    }

    private fun sendIncomingMessages() {
        var a = 0
        var textRandom = arrayListOf<String>(
            "Привет",
            "Компания «Ростелеком» открыла новый сезон строительства оптических линий связи на Южном Урале. Первым объектом для подключения стал жилой дом Челябинска в ЖК «Ньютон» на Комсомольском проспекте, 141. После его сдачи жители 132 квартир смогут пользоваться интернетом на скорости до 1 Гбит/с.",
            "Да",
            "В торжественной презентации старта нового сезона стройки приняли участие хоккеисты"
        )
        lifecycleScope.launch {
            var c = false
            for (i in 0..1000) {
                delay(1000)
                c = false
                a++

                viewModel.insertMessage(
                    getParams().id,
                    MessageDto(
                        "$a ${getParams().opponentJid} ${System.currentTimeMillis()}",
                        c,
                        "Иван Иванов",
                        getParams().opponentJid,
                        "$a " + textRandom.random(),
                        MessageSendingState.Deliver,
                        System.currentTimeMillis(),
                        0,
                        MessageDisplayType.Text,
                        false,
                        false,
                        null,
                        false, null, false, null, null, Location(2.8604, 14.540)
                    ),
                    layoutManager!!.findLastVisibleItemPosition() >= (chatAdapter!!.itemCount - 3)
                )
                Log.d(
                    "yyy",
                    "lastPosition = ${layoutManager!!.findFirstVisibleItemPosition()}, first = ${layoutManager!!.findLastVisibleItemPosition()}"
                )
            }
        }
        isNeedScrollDown =
            layoutManager!!.findFirstVisibleItemPosition() + 2 >= (chatAdapter!!.itemCount - viewModel.unreadCount.value!!)
    }

    private fun onGotGalleryPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value })
            showAttachBottomSheet()
        else
            askUserForOpeningAppSettings()
    }

    private fun showAttachBottomSheet() {
        if (childFragmentManager.findFragmentByTag(AttachBottomSheet.TAG) == null) {
            AttachBottomSheet().show(childFragmentManager, AttachBottomSheet.TAG)
        }
    }

    private fun onGotAudioPermissionResult(granted: Boolean) {
        if (!granted) askUserForOpeningAppSettings()
    }

    private fun onGotImagesAndVideoPermissionResult(grantResults: Map<String, Boolean>) {
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

    private fun edit(id: String, textMessage: String) {
        binding.chatInput.setText(textMessage)
        binding.chatInput.setSelection(binding.chatInput.length())
        editMessageId = id
    }

    private fun edit() {
        binding.chatInput.setText(viewModel.getSelectedMessageText())
        binding.chatInput.setSelection(binding.chatInput.length())
        editMessageId = viewModel.getMessageId()
    }

    private fun copyTextMessage(text: String? = null) {
        val clipBoard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textMessage = text ?: viewModel.getSelectedText()
        val clipData = ClipData.newPlainText("", textMessage)
        clipBoard.setPrimaryClip(clipData)
        showToast(R.string.snack_bar_title_copy_text)
    }

    private fun delete(id: String? = null) {
        val dialog = DeletingMessageDialog.newInstance(binding.tvChatTitle.text.toString())
        navigator().showDialogFragment(dialog, AppConstants.DELETING_MESSAGE_DIALOG_TAG)
        setFragmentResultListener(DELETING_MESSAGE_DIALOG_KEY) { _, bundle ->
            val result = bundle.getBoolean(DELETING_MESSAGE_BUNDLE_KEY)
            val forAll = bundle.getBoolean(DELETING_MESSAGE_FOR_ALL_BUNDLE_KEY)
            if (result) {
                if (id != null) viewModel.deleteMessage(id, forAll) else viewModel.deleteMessages(
                    forAll
                )
                enableSelectionMode(false)
            }
        }
    }

    private fun showUnreadBadge(count: Int) {
        if (count > 0) {
            binding.tvNewReceivedCount.text = if (count < 100) count.toString() else "99+"
            binding.tvNewReceivedCount.isVisible = true
        } else binding.tvNewReceivedCount.isVisible = false
//        val badgeDrawable = BadgeDrawable.create(requireContext())
//        badgeDrawable.backgroundColor =
//            ResourcesCompat.getColor(binding.tvNewReceivedCount.resources, R.color.green_500, null)
//        badgeDrawable.horizontalOffset = 10.dp

//        badgeDrawable.verticalOffset = 6.dp
//        badgeDrawable.badgeGravity = BadgeDrawable.BOTTOM_END
////        binding.tvNewReceivedCount.viewTreeObserver.addOnGlobalLayoutListener(object :
////            ViewTreeObserver.OnGlobalLayoutListener {
////            override fun onGlobalLayout() {
//                if (count > 0) {
//                    BadgeUtils.attachBadgeDrawable(badgeDrawable, binding.btnDownward)
//                    badgeDrawable.number = count
//                } else BadgeUtils.detachBadgeDrawable(badgeDrawable, binding.tvNewReceivedCount)
////              binding.tvNewReceivedCount.viewTreeObserver.removeOnGlobalLayoutListener(this)
////            }
//      //  })
    }


    private fun scrollDown() {
        if (chatAdapter != null) binding.messageList.scrollToPosition(chatAdapter?.itemCount!! - 1)
        binding.tvNewReceivedCount.isVisible = false
        binding.tvNewReceivedCount.text = ""
        viewModel.markAllMessageUnread(getParams().id)
    }


    private fun startAudioRecord() {
        handler.postDelayed(timer, 500)
        handler.postDelayed(record, 500)
        saveAudioMessage = true
    }

    private fun prepareUiForRecording() {
        enabledInputPanelButtons(false)
        binding.record.recordLayout.isVisible = true
        // binding.record.linChronometr.isVisible = true
        // binding.record.slideLayout.isVisible = true
        binding.record.slideLayout.alpha = 1.0f
        binding.linRecordLock.isVisible = true
        shortVibrate()
        binding.btnRecordExpanded.show()
        //    binding.record.slideLayout.x = 0f
        //    binding.record.cancelRecordLayout.isVisible = false
    }

    private fun manageScreenSleep(keepScreenOn: Boolean) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun sendVoiceMessage() {
        beginTimer(false)
        VoiceManager.getInstance().stopRecording(false)
        sendMessage("Audio Message", null)
        manageVoiceMessage(recordSaveAllowed)
        hideRecordPanel()
        scrollDown()
        //     clearVoiceMessage()
        // setFirstUnreadMessageId(null)
    }

    private fun hideRecordPanel() {
        binding.record.recordLayout.isVisible = false
        binding.linRecordLock.isVisible = false
        binding.btnRecordExpanded.hide()
        binding.btnRecordExpanded.isVisible = false
        enabledInputPanelButtons(true)
    }

    private fun enabledInputPanelButtons(enabled: Boolean) {
        binding.buttonEmoticon.isEnabled = enabled
        binding.btnDownward.isEnabled = enabled
    }

    private fun beginTimer(start: Boolean) {
        if (start) {
            binding.record.chrRecordingTimer.base = SystemClock.elapsedRealtime()
            binding.record.chrRecordingTimer.start()
            currentVoiceRecordingState = VoiceRecordState.TouchRecording
        } else {
            binding.record.chrRecordingTimer.stop()
        }
    }

    private fun shortVibrate() {
        binding.root.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
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
        //   binding.buttonAttach.isVisible = true

        binding.btnRecordExpanded.hide()
        binding.btnRecordExpanded.isVisible = false
        binding.spaceLock.clearAnimation()
        binding.spaceLock.y = 0f
        Log.d("iii", "${binding.imLockBar.y}, ${binding.imLockBar.marginBottom}")
        val old = binding.imLockBar.y
        binding.imLockBar.y = old - 26f
        binding.linRecordLock.isVisible = false
        manageVoiceMessage(false)
        val params = binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams
        params.bottomToBottom = binding.imLock.id
        // params.bottomMargin = 2
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
//        outState.putBoolean(AppConstants.CHAT_SELECTION_MODE_KEY, isSelectedMode)
//        val messageText = binding.chatInput.text.toString().trim()
//        outState.putString(CHAT_MESSAGE_TEXT_KEY, messageText)
    }


    override fun copyText(text: String) {
        copyTextMessage(text)
    }

    override fun pinMessage(messageDto: MessageDto) {
        binding.pinPanel.isVisible = true
        binding.tvPinOwner.text =
            if (messageDto.isOutgoing) messageDto.owner else binding.tvChatTitle.text.toString()
        binding.tvPinContent.text = messageDto.messageBody


        binding.pinPanel.setOnClickListener {

            val position =
                viewModel.getPositionMessage(viewModel.lastPositionPrimary(messageDto.primary))
            binding.messageList.scrollToPosition(position)
            viewModel.selectMessage(messageDto.primary, true)
            viewModel.getMessageList(getParams().opponentJid)
            handler.postDelayed(cancelSelected, 1000)
//           viewModel.selectMessage(messageDto.primary, false)
//            viewModel.getMessageList(getParams().opponent)
        }

        binding.imPinClose.setOnClickListener {
            binding.pinPanel.isVisible = false
        }


    }


    override fun forwardMessage(messageDto: MessageDto) {
        val text = "${messageDto.owner} \n ${messageDto.messageBody}"
        Log.d("yyy", "1 message text = $text")
        navigator().showForwardFragment(text)
    }

    override fun replyMessage(messageDto: MessageDto) {
        binding.answer.isVisible = true
        binding.replyMessageTitle.text =
            if (messageDto.isOutgoing) messageDto.owner else binding.tvChatTitle.text.toString()
        binding.replyMessageContent.text = messageDto.messageBody
        binding.close.setOnClickListener {
            binding.replyMessageTitle.text = ""
            binding.replyMessageContent.text = ""
            binding.answer.isVisible = false
        }
    }

    override fun editMessage(primary: String, text: String) {
        edit(primary, text)
    }

    override fun deleteMessage(primary: String) {
        delete(primary)
    }

    override fun onLongClick(primary: String) {
        enableSelectionMode(true)
        Check.setSelectedMode(true)
        viewModel.selectMessage(primary, true)
        viewModel.getMessageList(getParams().opponentJid)
    }

    override fun checkItem(isChecked: Boolean, primary: String) {
        viewModel.selectMessage(primary, isChecked)
        viewModel.getMessageList(getParams().opponentJid)
//        chatAdapter?.notifyDataSetChanged()
    }

    override fun onFullSwipe(position: Int) {
        handler.postDelayed(reply, 1500)
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (binding.emojiPopupLayout.isShowing) {
                binding.buttonEmoticon.setImageResource(R.drawable.ic_emoticon_outline)
                binding.chatInput.showSoftInputOnFocus = true
            } else if (binding.selectMessagesToolbar.toolbarSelectedMessages.isVisible) {
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
            //  binding.chatPanelGroup.isVisible = false
            binding.interaction.interactionView.isVisible = true
            //   chatAdapter?.setSelectedMode(true)
            replySwipeCallback?.setSwipeEnabled(false)
            isSelectedMode = true
            Check.setSelectedMode(true)
            viewModel.getMessageList(getParams().opponentJid)
            binding.buttonEmoticon.isEnabled = false
            binding.buttonAttach.isEnabled = false
            binding.btnRecord.isEnabled = false
            binding.chatInput.isEnabled = false
        } else {
            binding.appbar.setBackgroundResource(R.color.blue_500)
            //   chatAdapter?.setSelectedMode(false)
            //  binding.chatPanelGroup.isVisible = true
            binding.selectMessagesToolbar.toolbarSelectedMessages.isVisible = false
            binding.interaction.interactionView.isVisible = false
            binding.messageToolbar.isVisible = true
            val textMessage = binding.chatInput.text.toString().trim()
            if (textMessage.isNotEmpty()) {
                //   binding.buttonSendMessage.isVisible = true
                //    binding.buttonAttach.isVisible = false
                binding.btnRecord.isVisible = false
            }
            Check.setSelectedMode(false)
            viewModel.clearAllSelected()
            viewModel.getMessageList(getParams().opponentJid)
            replySwipeCallback?.setSwipeEnabled(true)
            isSelectedMode = false
            binding.buttonEmoticon.isEnabled = true
            binding.buttonAttach.isEnabled = true
            binding.btnRecord.isEnabled = true
            binding.chatInput.isEnabled = true
        }
    }

    private fun saveDraft() {
        val text =
            if (binding.chatInput.text!!.isEmpty()) null else binding.chatInput.text.toString()
                .trimEnd()
        viewModel.saveDraft(getParams().id, text)
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
        var c = System.currentTimeMillis()
        viewModel.insertMessage(
            getParams().id,
            MessageDto(
                "$c",
                true,
                "Иван Иванов",
                getParams().opponentJid,
                textMessage,
                MessageSendingState.Deliver,
                timeStamp,
                0,
                MessageDisplayType.Text,
                false,
                false,
                null,
                false, messageKindDto, false, null, imageList
            ), true
        )
        binding.answer.isVisible = false
        isNeedScrollDown = true
    }

//     private fun scrollToFirstUnread(unreadCount: Int) {
//        layoutManager.scrollToPositionWithOffset(
//            chatMessageAdapter.itemCount - unreadCount,
//            200
//        )
//    }
//
//      private fun saveState() {
//        layoutManager.findLastCompletelyVisibleItemPosition()
//            .takeIf { it != -1 }
//            ?.let {
//                chat.saveLastPosition(if (it == chatMessageAdapter.itemCount - 1) 0 else it)
//            }
//    }


    private fun showBadgeWithUnreadMessages() {


    }

    override fun onDestroyView() {
        super.onDestroyView()
        val lastPosition = layoutManager?.findLastVisibleItemPosition()

        if (chatAdapter?.itemCount!! > 0 && lastPosition != null) {

            val savedPosition = chatAdapter?.getPositionId(lastPosition)
            if (savedPosition != null) viewModel.saveLastPosition(getParams().id, savedPosition)
        }

        saveDraft()
        onBackPressedCallback.remove()

    }

    override fun onDestroy() {
        super.onDestroy()
        chatAdapter?.submitList(null)
        chatAdapter = null
    }

    private enum class VoiceRecordState {
        NotRecording, InitiatedRecording, TouchRecording, NoTouchRecording, StoppedRecording
    }

}





