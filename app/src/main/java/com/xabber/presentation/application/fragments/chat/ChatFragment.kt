package com.xabber.presentation.application.fragments.chat


import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.aghajari.emojiview.AXEmojiManager
import com.aghajari.emojiview.googleprovider.AXGoogleEmojiProvider
import com.aghajari.emojiview.view.AXSingleEmojiView
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.databinding.FragmentChatBinding
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageKind
import com.xabber.dto.MessageReferenceDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.AppConstants.CHAT_MESSAGE_TEXT_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_BUNDLE_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_DIALOG_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_FOR_ALL_BUNDLE_KEY
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.*
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.audio.AudioRecorder
import com.xabber.presentation.application.fragments.chat.audio.PublishAudioProgress
import com.xabber.presentation.application.fragments.chat.audio.VoiceMessagePresenterManager
import com.xabber.presentation.application.fragments.chat.message.*
import com.xabber.presentation.application.fragments.contacts.AttachmentBottomSheet
import com.xabber.presentation.application.fragments.contacts.ContactAccountParams
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.utils.*
import com.xabber.utils.custom.PlayerVisualizerView
import io.reactivex.rxjava3.disposables.Disposable
import io.realm.kotlin.Realm
import kotlinx.coroutines.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.Runnable
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.experimental.and


class ChatFragment : DetailBaseFragment(R.layout.fragment_chat), MessageAdapter.MenuItemListener,
    MessageAdapter.OnViewClickListener,
    ReplySwipeCallback.SwipeAction {
    private val binding by viewBinding(FragmentChatBinding::bind)
    private val handler = Handler(Looper.getMainLooper())
    private var messageAdapter: MessageAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private val viewModel: ChatViewModel by viewModel { parametersOf(getParams().id) }
    private val audioRecorder = AudioRecorder()
    private var replySwipeCallback: ReplySwipeCallback? = null
    private var isNeedScrollDown = false
    private var editMessageId: String? = null
    private val enableNotificationsCode = 0L
    private var isSelectedMode = false
    private var replyingMessage: MessageDto? = null
    private var currentVoiceRecordingState = VoiceRecordState.NotRecording
    private var recordSaveAllowed = false
    private var recordingPath: String? = null
    private var stopTypingTimer: Timer? = Timer()
    private var saveAudioMessage = true
    private var audioProgressSubscription: Disposable? = null
    private var lockIsClosed = false
    private var isVibrate = false
    private var ignoreReceiver = true

    var isPlaying = false

    val realm = Realm.open(defaultRealmConfig())


    private val requestAudioPermissionResult = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ::onGotAudioPermissionResult
    )

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        ::onGotGalleryPermissionResult
    )

    private val cancelSelected = Runnable {
        viewModel.clearAllSelected()
        viewModel.getMessageList(getParams().id)
    }

    private val reply = Runnable {
        if (replyingMessage != null)
            replyMessage(
                replyingMessage!!
            )
    }

    private val unreadShower = {
        val unread = viewModel.unreadCount.value
        if (unread != null) {
            if (unread > 0) {
                binding.tvNewReceivedCount.isVisible = true
                binding.tvNewReceivedCount.text = unread.toString()
            } else {
                binding.tvNewReceivedCount.isVisible = false
                binding.tvNewReceivedCount.text = ""
            }
        } else {
            binding.tvNewReceivedCount.isVisible = false
            binding.tvNewReceivedCount.text = ""
        }
    }

    private val timer = Runnable {
        prepareUiForRecording()
        beginTimer(true)
        currentVoiceRecordingState = VoiceRecordState.TouchRecording
    }

    private val record = Runnable {
        val outputDir =
            context?.getExternalFilesDir(null) // Получаем директорию, где будет сохраняться файл
        val fileName = "${System.currentTimeMillis()} audio_file.mp4"
        val outputPath = File(outputDir, fileName).absolutePath
        audioRecorder.startRecord(outputPath)
    }

    private val shake = Runnable {
        val shaker = AnimationUtils.loadAnimation(context, R.anim.shake)
        if (binding.imLock.animation == null) binding.imLock.startAnimation(shaker)
        if (binding.imLockBar.animation == null) binding.imLockBar.startAnimation(shaker)
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
        fun newInstance(params: ChatParams) = ChatFragment().apply {
            arguments = Bundle().apply {
                putParcelable(AppConstants.CHAT_PARAMS, params)
            }
        }
    }

    private fun getParams(): ChatParams =
        requireArguments().parcelable(AppConstants.CHAT_PARAMS)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val chat = viewModel.loadChat(getParams().id)
        if (chat == null) navigator().closeDetail()
        else {
            prepareUi(chat)
            initializeToolbarActions(chat)
            initializeRecyclerView()
            initializeStandardInputLayoutActions()
            subscribeToChatData(chat)
            initializeSelectMessageToolbarActions()
            initializeSelectedMessagePanel()
            viewModel.initChatDataListener(getParams().id)
            viewModel.initMessagesListener(chat.owner, chat.opponentJid)
            viewModel.loadChat(getParams().id)
            activity?.onBackPressedDispatcher?.addCallback(onBackPressedCallback)
        }
        if (savedInstanceState != null) restoreState(savedInstanceState)
        else {
            restoreDraft()
            scrollToLastPosition()
        }
    }

    private fun prepareUi(chat: ChatListDto) {
        loadContactAvatar()
        setTitle(chat.getChatName())
        setStatus(chat.status, chat.entity)
        setupMuteIcon(chat.muteExpired)
    }

    private fun loadContactAvatar() {
        binding.avatar.setImageResource(getParams().avatar!!)
    }

    private fun setTitle(opponentName: String) {
        binding.tvChatTitle.text = opponentName
    }

    private fun setStatus(resourceStatus: ResourceStatus, rosterItemEntity: RosterItemEntity) {
        val statusIcon = StatusMaker.statusIcon(RosterItemEntity.Bot)
        val statusTint = StatusMaker.statusTint(ResourceStatus.Dnd)
        if (statusIcon != null) {
            binding.avatarStatus.isVisible = true
//            binding.avatarStatus.setImageResource(statusIcon)
//            binding.avatarStatus.setColorFilter(
//                ContextCompat.getColor(requireContext(), statusTint),
//                PorterDuff.Mode.SRC_IN
//            )
        } else binding.avatarStatus.isVisible = false
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

    private fun initializeToolbarActions(chat: ChatListDto) {
        binding.avatar.setOnClickListener {
            val contactId = viewModel.getContactId(getParams().id)
            if (contactId != null) navigator().showContactAccount(
                ContactAccountParams(
                    contactId,
                    getParams().avatar
                )
            )
        }
        initToolbarMenu(chat)
        setupToolbarMenu(chat.muteExpired)
    }

    private fun initToolbarMenu(chat: ChatListDto) {
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.call_out -> sendIncomingMessages(chat.owner, chat.opponentJid)
                R.id.disable_notifications -> disableNotifications()
                R.id.enable_notifications -> enableNotifications()
                R.id.clear_message_history -> clearHistory(chat)
                R.id.delete_chat -> deleteChat(chat)
            }; true
        }
    }

    private fun setupToolbarMenu(mute: Long) {
        val muteExpired = mute - System.currentTimeMillis()
        binding.toolbar.menu.findItem(R.id.enable_notifications).isVisible =
            muteExpired > 0
        binding.toolbar.menu.findItem(R.id.disable_notifications).isVisible =
            muteExpired <= 0
    }

    private fun restoreState(savedInstanceState: Bundle) {
        val messageText = savedInstanceState.getString(CHAT_MESSAGE_TEXT_KEY)
        binding.chatInput.setText(messageText)
        isSelectedMode = savedInstanceState.getBoolean(AppConstants.CHAT_SELECTION_MODE_KEY)
        enableSelectionMode(isSelectedMode)

        if (savedInstanceState != null) {
            val voiceRecordPath = savedInstanceState.getString("VOICE_MESSAGE")
            ignoreReceiver = savedInstanceState.getBoolean("VOICE_MESSAGE_RECEIVER_IGNORE")
            if (voiceRecordPath != null) {
                recordingPath = voiceRecordPath
                currentVoiceRecordingState = VoiceRecordState.StoppedRecording
                binding.record.recordLayout.isVisible = false
                binding.audioPresenter.recordingPresenterLayout.isVisible = true
                if (recordingPath != null) setUpVoiceMessagePresenter(recordingPath!!)
            }
        }
    }

    private fun restoreDraft() {
        val draft = viewModel.loadChat(getParams().id)?.draftMessage
        if (draft != null) binding.chatInput.setText(draft)
        setupInputButtons()
    }

    private fun scrollToLastPosition() {
        val lastPosition = viewModel.loadChat(getParams().id)?.lastPosition

        if (!lastPosition.isNullOrEmpty()) {
            val messagePosition =
                viewModel.getPositionMessage(lastPosition)
            binding.messageList.post { layoutManager?.scrollToPosition(messagePosition) }
            viewModel.saveLastPosition(getParams().id, "")
        }
    }

    private fun setupInputButtons() {
        binding.btnRecord.isVisible = binding.chatInput.text.toString().trimEnd().isEmpty()
        binding.buttonAttach.isVisible = binding.chatInput.text.toString().trimEnd().isEmpty()
        binding.buttonSendMessage.isVisible =
            binding.chatInput.text.toString().trimEnd().isNotEmpty()
    }

    private fun disableNotifications() {
        val dialog = NotificationBottomSheet.newInstance(getParams().id)
        dialog.show(childFragmentManager, AppConstants.NOTIFICATION_BOTTOM_SHEET_TAG)
    }

    private fun enableNotifications() {
        viewModel.setMute(getParams().id, enableNotificationsCode)
        binding.toolbar.menu.findItem(R.id.enable_notifications).isVisible = false
        binding.toolbar.menu.findItem(R.id.disable_notifications).isVisible = true
    }

    private fun clearHistory(chat: ChatListDto) {
        val dialog = ChatHistoryClearDialog.newInstance(chat.getChatName(), chat.id)
        dialog.show(childFragmentManager, AppConstants.DELETING_CHAT_DIALOG_TAG)
    }

    private fun deleteChat(chat: ChatListDto) {
        val dialog = DeletingChatDialog.newInstance(chat.getChatName(), chat.id)
        dialog.show(childFragmentManager, AppConstants.DELETING_CHAT_DIALOG_TAG)
    }

    private fun initializeRecyclerView() {
        val isGroup = viewModel.loadChat(getParams().id)!!.isGroup
        messageAdapter = MessageAdapter(layoutInflater,
            this,
            onViewClickListener = this,
            messages = ArrayList<MessageDto>(), isGroup = isGroup
        )
        binding.messageList.adapter = messageAdapter
        layoutManager = LinearLayoutManager(context)
        layoutManager?.stackFromEnd = true
        binding.messageList.layoutManager = layoutManager
        addSwipeCallback()
        addMessageHeaderViewDecoration()
        addScrollListener()
        fillChat()
        binding.messageList.itemAnimator = null
    }

    private fun addSwipeCallback() {
        replySwipeCallback = ReplySwipeCallback(requireContext()) { position: Int ->
            val message = messageAdapter?.getMessageItem(position)
            if (message != null) {
                replyingMessage = message
                handler.postDelayed(reply, 200)
            }
        }

        ItemTouchHelper(replySwipeCallback as ReplySwipeCallback).attachToRecyclerView(binding.messageList)

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
        binding.messageList.addItemDecoration(MessageHeaderViewDecoration(requireContext()))
    }

    private fun addScrollListener() {
        binding.messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (layoutManager != null) {
                    if (layoutManager!!.findLastVisibleItemPosition() >= messageAdapter!!.itemCount - 1) {
                        binding.downScroller.isVisible =
                            binding.tvNewReceivedCount.text.isNotEmpty()
                    } else {
                        if (currentVoiceRecordingState != VoiceRecordState.TouchRecording && currentVoiceRecordingState != VoiceRecordState.InitiatedRecording && currentVoiceRecordingState != VoiceRecordState.NoTouchRecording)
                            binding.downScroller.isVisible = true
                    }
                }
            }
        })

        binding.btnDownward.setOnClickListener {
            val lastVisiblePosition = layoutManager!!.findLastVisibleItemPosition()
            if (viewModel.unreadCount.value == 0 || viewModel.unreadCount.value == null || lastVisiblePosition + 2 >= messageAdapter!!.itemCount - viewModel.unreadCount.value!!) {
                scrollDown()
                binding.tvNewReceivedCount.text = ""
                binding.tvNewReceivedCount.isVisible = false
            } else scrollToFirstUnread()
        }
    }

    private fun scrollToFirstUnread() {
        layoutManager?.scrollToPositionWithOffset(
            messageAdapter!!.itemCount - viewModel.unreadCount.value!!, 200
        )
    }

    private fun fillChat() {
        viewModel.getMessageList(getParams().id)
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
//        binding.buttonEmoticon.setOnClickListener {
//            if (binding.emojiPopupLayout.isShowing) {
//                binding.buttonEmoticon.setImageResource(R.drawable.ic_emoticon_outline)
//                binding.emojiPopupLayout.hideAndOpenKeyboard()
//                binding.emojiPopupLayout.hidePopupView()
//            } else {
//                binding.buttonEmoticon.setImageResource(R.drawable.ic_keyboard)
//
//                binding.emojiPopupLayout.toggle()
//                binding.chatInput.showSoftInputOnFocus = false
//            }
//        }
    }

    private fun initializeButtonAttach() {
        binding.buttonAttach.setOnClickListener {
            requestGalleryPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
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
                val chat = viewModel.loadChat(getParams().id)
                val timeStamp = System.currentTimeMillis()
                viewModel.insertMessage(
                    getParams().id,
                    MessageDto(
                        "$timeStamp",
                        true,
                        viewModel.loadChat(getParams().id)!!.owner,
                        viewModel.loadChat(getParams().id)!!.opponentJid,
                        text,
                        MessageSendingState.Deliver,
                        timeStamp,
                        0,
                        MessageDisplayType.Text,
                        false,
                        false,
                        null,
                        isSelected = false,
                        isUnread = true,
                        isGroup = false
                    )
                )
                binding.answer.isVisible = false
                isNeedScrollDown = true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeButtonRecord() {
        binding.btnRecord.setOnTouchListener { _: View?, motionEvent: MotionEvent ->
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
                    when (currentVoiceRecordingState) {
                        VoiceRecordState.InitiatedRecording, VoiceRecordState.NotRecording -> {
                            handler.removeCallbacks(record)
                            handler.removeCallbacks(timer)
                            hideRecordPanel()
                            beginTimer(false)
                            enabledInputPanelButtons(true)
                            navigator().lockScreen(false)
                            currentVoiceRecordingState = VoiceRecordState.NotRecording
                        }
                        VoiceRecordState.TouchRecording -> {
                            val baseTime: Long = binding.record.chrRecordingTimer.getBase()
                            val elapsedTime = SystemClock.elapsedRealtime() - baseTime
                            val seconds = (elapsedTime / 1000).toInt()
                            if (seconds >= 1) {
                                audioRecorder.stopRecord()
                                sendVoiceMessage(audioRecorder.getRecordedFilePath()!!)
                                hideRecordPanel()
                                navigator().lockScreen(false)
                            } else {
                                audioRecorder.stopRecord()
                                hideRecordPanel()
                                currentVoiceRecordingState = VoiceRecordState.NotRecording
                                navigator().lockScreen(false)
                            }
                        }
                        VoiceRecordState.NoTouchRecording -> {
                            handler.post(stop)
                        }
                        else -> {
                            binding.record.chrRecordingTimer.stop()

                            val animRight =
                                AnimationUtils.loadAnimation(context, R.anim.slide_to_right)
                            binding.record.recordLayout.startAnimation(animRight)
                            binding.record.recordLayout.isVisible = false
                            binding.linRecordLock.isVisible = false
                            binding.btnRecordExpanded.isVisible = false
                            handler.removeCallbacks(record)
                            handler.removeCallbacks(timer)
                            stopTypingTimer?.cancel()
                            navigator().lockScreen(false)
                            if (saveAudioMessage && isPermissionGranted(Manifest.permission.RECORD_AUDIO)) sendVoiceMessage(
                                audioRecorder.getRecordedFilePath()!!
                            )
                            hideRecordPanel()
                            currentVoiceRecordingState = VoiceRecordState.NotRecording
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
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

                    if (alpha <= 0) {

                        saveAudioMessage = false
                        val animRight =
                            AnimationUtils.loadAnimation(context, R.anim.slide_to_right)
                        binding.record.recordLayout.startAnimation(animRight)
                        binding.record.recordLayout.isVisible = false
                        hideRecordPanel()
                        binding.record.slideLayout.x = 0f
                        currentVoiceRecordingState = VoiceRecordState.NotRecording
                    }
                }
            }; true
        }


        binding.frameStop.setOnClickListener {
            binding.frameStop.isVisible = false
            binding.btnRecordExpanded.hide()
            binding.record.recordLayout.isVisible = false
            binding.audioPresenter.recordingPresenterLayout.isVisible = true
            audioRecorder.stopRecord()
            setUpVoiceMessagePresenter(audioRecorder.getRecordedFilePath()!!)
        }

        binding.record.tvCancelRecording.setOnClickListener {
            currentVoiceRecordingState = VoiceRecordState.NotRecording
            hideRecordPanel()
            clearVoiceMessage()
        }

        binding.audioPresenter.btnDeleteAudioMessage.setOnClickListener {
            currentVoiceRecordingState = VoiceRecordState.NotRecording
            hideRecordPanel()
            clearVoiceMessage()
        }

        binding.audioPresenter.btnSendAudioMessage.setOnClickListener {
            sendVoiceMessage(audioRecorder.getRecordedFilePath()!!)
            clearVoiceMessage()
            isPlaying = false
            enableStandardPanelButtons(true)
        }

        binding.btnRecordExpanded.setOnClickListener {
            if (isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
                audioRecorder.stopRecord()
                sendVoiceMessage(audioRecorder.getRecordedFilePath()!!)
                clearVoiceMessage()
            }
        }

    }

    private fun subscribeToChatData(chat: ChatListDto) {

        viewModel.chat.observe(viewLifecycleOwner) {
            if (it == null) navigator().closeDetail()
        }

        if (chat.owner != chat.opponentJid) {
            viewModel.opponentName.observe(viewLifecycleOwner) {
                setupOpponentName(it)
            }
        }

        viewModel.muteExpired.observe(viewLifecycleOwner) {
            if (it != null) setupMuteIcon(it)
            setupToolbarMenu(it)
        }

        viewModel.messages.observe(viewLifecycleOwner) {
            messageAdapter?.updateAdapter(it)
          messageAdapter?.notifyDataSetChanged()
            if (layoutManager != null && messageAdapter != null) {
                if (layoutManager!!.findLastVisibleItemPosition() >= messageAdapter!!.itemCount - 2 && !isSelectedMode) scrollDown()
                if (it.isNotEmpty()) isNeedScrollDown = it[it.size - 1].isOutgoing
                if (isNeedScrollDown) {
                    scrollDown()
                    isNeedScrollDown = false
                }
            }
        }


        viewModel.unreadCount.observe(viewLifecycleOwner) { unread ->
            val chatId = getParams().id
            realm.writeBlocking {
                val chat =
                    this.query(LastChatsStorageItem::class, "primary = '$chatId'").first().find()
                if (chat != null) {
                    findLatest(chat).also {
                        chat.unread = unread
                    }
                }
            }
            handler.postDelayed(unreadShower, 10)
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

    private fun setupOpponentName(opponentName: String?) {
        binding.tvChatTitle.text = opponentName ?: "Saved messages"
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
        val chat = viewModel.loadChat(getParams().id)
        binding.interaction.linReply.setOnClickListener {
            val message = viewModel.getMessage()
            enableSelectionMode(false)
            if (message != null) replyMessage(message)
        }
        binding.interaction.linForward.setOnClickListener {
            val text = viewModel.getForwardMessagesText()
            enableSelectionMode(false)
            GlobalScope.launch {
                delay(300)
                navigator().showForwardFragment(text, viewModel.getAccount(chat!!.owner)?.jid ?: "")
            }

        }
    }

    private fun sendIncomingMessages(owner: String, opponentJid: String) {
        var a = 0
        var textRandom = arrayListOf<String>(
            "Привет",
            "Компания «Ростелеком» открыла новый сезон строительства оптических линий связи на Южном Урале. Первым объектом для подключения стал жилой дом Челябинска в ЖК «Ньютон» на Комсомольском проспекте, 141. После его сдачи жители 132 квартир смогут пользоваться интернетом на скорости до 1 Гбит/с.",
            "Да",
            "В торжественной презентации старта нового сезона стройки приняли участие хоккеисты"
        )
        val references1 = ArrayList<MessageReferenceDto>()
        references1.add(MessageReferenceDto("$a 1 ${System.currentTimeMillis()}", isGeo = true, latitude = 56.98, longitude = 67.09, size = 0L))




        val mes = ArrayList<MessageDto>()
      lifecycleScope.launch() {
            for (i in 0 until 1000) {
                delay(1000)
                a++
               val m =
                    MessageDto(
                        "$a ${opponentJid} ${System.currentTimeMillis()}",
                        false,
                        owner,
                        opponentJid,
                        "$a " + textRandom.random(),
                        MessageSendingState.Deliver,
                        System.currentTimeMillis(),
                        0,
                        MessageDisplayType.System,
                        false,
                        false,
                        null,
                        isUnread = true,
                        isGroup = false
                    )
                viewModel.insertMessage(getParams().id, m)
//mes.add(m)
            }

      }
        isNeedScrollDown =
            layoutManager!!.findFirstVisibleItemPosition() + 2 >= (messageAdapter!!.itemCount - viewModel.unreadCount.value!!)
    }

    private fun onGotGalleryPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value })
            showAttachBottomSheet()
        else
            askUserForOpeningAppSettings()
    }

    private fun showAttachBottomSheet() {
        if (childFragmentManager.findFragmentByTag(AppConstants.ATTACH_BOTTOM_SHEET_TAG) == null)
            AttachmentBottomSheet.newInstance(getParams().id)
                .show(childFragmentManager, AppConstants.ATTACH_BOTTOM_SHEET_TAG)
    }

    private fun onGotAudioPermissionResult(granted: Boolean) {
        if (!granted) askUserForOpeningAppSettings()
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
        val dialog = DeletingMessageDialog.newInstance(binding.tvChatTitle.text.toString(), id)
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
////            override fun onGlobalLay
        // out() {
//                if (count > 0) {
//                    BadgeUtils.attachBadgeDrawable(badgeDrawable, binding.btnDownward)
//                    badgeDrawable.number = count
//                } else BadgeUtils.detachBadgeDrawable(badgeDrawable, binding.tvNewReceivedCount)
////              binding.tvNewReceivedCount.viewTreeObserver.removeOnGlobalLayoutListener(this)
////            }
//      //  })
    }


    private fun scrollDown() {
        if (messageAdapter != null) binding.messageList.scrollToPosition(messageAdapter?.itemCount!! - 1)
        binding.tvNewReceivedCount.isVisible = false
        binding.tvNewReceivedCount.text = ""
        messageAdapter?.setFirstUnreadMessageId(null)
        viewModel.markAllMessageUnread(getParams().id)
    }

    private fun startAudioRecord() {
        handler.postDelayed(timer, 500)
        handler.postDelayed(record, 500)
        saveAudioMessage = true
        enableStandardPanelButtons(false)
    }

    private fun enableStandardPanelButtons(enable: Boolean) {
        binding.buttonEmoticon.isEnabled = enable
        //   binding.btnRecord.isEnabled = enable
        binding.buttonAttach.isEnabled = enable
        binding.buttonSendMessage.isEnabled = enable
    }

    private fun prepareUiForRecording() {
        binding.downScroller.isVisible = false
        enabledInputPanelButtons(false)
        binding.record.recordLayout.isVisible = true
        binding.record.linChronometr.isVisible = true
        binding.record.slideLayout.isVisible = true
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

    private fun sendVoiceMessage(path: String) {
        isPlaying = false
        binding.linRecordLock.invalidate()
        enableStandardPanelButtons(true)
        beginTimer(false)

        val reference = MessageReferenceDto(
            "a ${System.currentTimeMillis()},",
            uri = path,
            size = 0L,
            isVoiceMessage = true
        )
        val list = ArrayList<MessageReferenceDto>()
        list.add(reference)
        viewModel.insertMessage(
            getParams().id, MessageDto(
                "${System.currentTimeMillis()}",
                true,
                viewModel.loadChat(getParams().id)!!.owner,
                viewModel.loadChat(getParams().id)!!.opponentJid,
                "",
                MessageSendingState.Deliver,
                System.currentTimeMillis(),
                0,
                MessageDisplayType.Text,
                false,
                false,
                null,
                false, null, false, isUnread = true, references = list
            )
        )

        manageVoiceMessage(recordSaveAllowed)
        hideRecordPanel()
        scrollDown()
    }

    private fun hideRecordPanel() {
        binding.record.recordLayout.isVisible = false
        binding.linRecordLock.isVisible = false
        binding.btnRecordExpanded.hide()
        binding.btnRecordExpanded.isVisible = false
        enabledInputPanelButtons(true)
        binding.record.cancelRecordLayout.isVisible = false
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
        binding.audioPresenter.btnPlay.setImageResource(R.drawable.ic_play)
        isPlaying = false
        isVibrate = false
        binding.record.recordLayout.clearAnimation()
        binding.record.recordLayout.x = 0f
        binding.record.slideLayout.x = 0f
        binding.record.slideLayout.clearAnimation()
        binding.imLock.setImageResource(R.drawable.ic_lock_base)
        binding.imLockBar.isVisible = true
        binding.audioPresenter.recordingPresenterLayout.isVisible = false
        binding.frameStop.clearAnimation()
        binding.frameStop.isVisible = false
        binding.record.recordLayout.isVisible = false
        binding.btnRecordExpanded.hide()
        binding.btnRecordExpanded.isVisible = false
        binding.spaceLock.clearAnimation()
        binding.spaceLock.y = 0f
        val old = binding.imLockBar.y
        binding.imLockBar.y = old - 26f
        binding.linRecordLock.isVisible = false
        manageVoiceMessage(false)
        val params = binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams
        params.bottomToBottom = binding.imLock.id
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
            sendVoiceMessage(audioRecorder.getRecordedFilePath()!!)
            currentVoiceRecordingState = VoiceRecordState.NotRecording
        } else {
            currentVoiceRecordingState = VoiceRecordState.NotRecording
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(AppConstants.CHAT_SELECTION_MODE_KEY, isSelectedMode)
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
            viewModel.getMessageList(getParams().id)
            handler.postDelayed(cancelSelected, 1000)
        }

        binding.imPinClose.setOnClickListener {
            binding.pinPanel.isVisible = false
        }

    }


    override fun forwardMessage(messageDto: MessageDto) {
        val text = "${messageDto.owner} \n ${messageDto.messageBody}"
        val chat = viewModel.loadChat(getParams().id)
        navigator().showForwardFragment(text, viewModel.getAccount(chat!!.owner)?.jid ?: "")
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
        viewModel.getMessageList(getParams().id)
    }

    override fun checkItem(isChecked: Boolean, primary: String) {
        viewModel.selectMessage(primary, isChecked)
        viewModel.getMessageList(getParams().id)
        messageAdapter?.notifyDataSetChanged()
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
            binding.toolbar.isVisible = false
            binding.selectMessagesToolbar.toolbarSelectedMessages.isVisible = true
            saveDraft()
            //  binding.chatPanelGroup.isVisible = false
            binding.interaction.interactionView.isVisible = true
            //  chatAdapter?.setSelectedMode(true)
            replySwipeCallback?.setSwipeEnabled(false)
            isSelectedMode = true
            Check.setSelectedMode(true)
            viewModel.getMessageList(getParams().id)

            binding.buttonEmoticon.isEnabled = false
            binding.buttonAttach.isEnabled = false
            binding.btnRecord.isEnabled = false
            binding.chatInput.isEnabled = false
        } else {
            val color = baseViewModel.getPrimaryAccount()?.colorKey
            val c = ColorManager.convertColorNameToId(color ?: resources.getString(R.string.blue))
            binding.appbar.setBackgroundResource(c)
            //   chatAdapter?.setSelectedMode(false)
            //  binding.chatPanelGroup.isVisible = true
            binding.selectMessagesToolbar.toolbarSelectedMessages.isVisible = false
            binding.interaction.interactionView.isVisible = false
            binding.toolbar.isVisible = true
            val textMessage = binding.chatInput.text.toString().trim()
            if (textMessage.isNotEmpty()) {
                //   binding.buttonSendMessage.isVisible = true
                //    binding.buttonAttach.isVisible = false
                binding.btnRecord.isVisible = false
            }
            Check.setSelectedMode(false)
            viewModel.clearAllSelected()
            viewModel.getMessageList(getParams().id)
            replySwipeCallback?.setSwipeEnabled(true)
            isSelectedMode = false
            binding.buttonEmoticon.isEnabled = true
            binding.buttonAttach.isEnabled = true
            binding.btnRecord.isEnabled = true
            binding.chatInput.isEnabled = true
        }
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
        val chat = viewModel.loadChat(getParams().id)
        viewModel.insertMessage(
            getParams().id,
            MessageDto(
                "$c",
                true,
                viewModel.loadChat(getParams().id)!!.owner,
                viewModel.loadChat(getParams().id)!!.opponentJid,
                textMessage,
                MessageSendingState.Deliver,
                timeStamp,
                0,
                MessageDisplayType.Text,
                false,
                false,
                null,
                false, messageKindDto, false, isUnread = true
            )
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


    override fun onDestroyView() {
        super.onDestroyView()
        saveLastPosition()
        saveDraft()
//        chatListViewModel.selectedChatId = ""
        realm.close()
        onBackPressedCallback.remove()
    }

    override fun onDestroy() {
        super.onDestroy()

        messageAdapter = null
    }

    private fun saveDraft() {
        val inputText = binding.chatInput.text.toString().trimEnd()
        val draft = inputText.ifEmpty { null }
        viewModel.saveDraft(getParams().id, draft)
    }

    private fun saveLastPosition() {
        val lastPosition = layoutManager?.findLastVisibleItemPosition()
        if (messageAdapter?.itemCount!! > 0 && lastPosition != null) {
            val savedPosition = messageAdapter?.getMessageItem(lastPosition)?.primary
            if (savedPosition != null) viewModel.saveLastPosition(getParams().id, savedPosition)
        }
    }

    private enum class VoiceRecordState {
        NotRecording, InitiatedRecording, TouchRecording, NoTouchRecording, StoppedRecording
    }

//    override fun onBind(message: MessageDto?) {
//        if (message != null) {
//            val id = message.primary
//            if (message.isUnread) {
//                realm.writeBlocking {
//                    val m = this.query(MessageStorageItem::class, "primary = '$id'").first().find()
//                    if (m != null) {
//                        findLatest(m).also {
//                            if (!it!!.isRead) {
//                                it.isRead = true
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }

    override fun onImageOrVideoClick(startPosition: Int, messageId: String) {
        val intent = Intent(requireContext(), MediaDetailsActivity::class.java)
        intent.putExtra(AppConstants.START_POSITION, startPosition)
        intent.putExtra(AppConstants.MESSAGE_UID, messageId)
        startActivity(intent)
    }

    override fun onLocationClick(latitude: Double, longitude: Double) {
        context?.startActivity(
            Intent().apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
            }
        )
    }

    private val VALID_IMAGE_EXTENSIONS = arrayOf("webp", "jpeg", "jpg", "png", "jpe", "gif")

    fun fileIsImage(file: File): Boolean {
        return extensionIsImage((file.path))
    }

    fun extensionIsImage(path: String?): Boolean {
        if (path == null) return false
        else if (path.isEmpty()) return false
        else return VALID_IMAGE_EXTENSIONS.contains(path)
    }


    private fun createWaveformFromAudioData(audioData: ByteArray): ArrayList<Int> {
        // Здесь следует реализовать логику создания волны из аудио данных
        // Приведенный здесь код является примером и может потребоваться более сложная логика для создания волны
        val waveform: ArrayList<Int> = ArrayList()

        // Цикл обработки аудио данных
        for (i in audioData.indices) {
            // Пример: преобразование байта в целое число и добавление в волну
            val value: Byte =
                audioData[i] and 0xFF.toByte() // Преобразование байта в беззнаковое целое число
            waveform.add(value.toInt())
        }
        return waveform
    }


    private fun createWaveform(filePath: String, view: PlayerVisualizerView) {
        // Получение файла из пути
        val file = File(filePath)

        // Проверка наличия файла
        if (!file.exists()) {
            // Обработка ошибки - файл не найден
            return
        }
        try {
            // Чтение аудио данных из файла
            val inputStream: InputStream = FileInputStream(file)
            val audioData = ByteArray(file.length().toInt())
            inputStream.read(audioData)
            inputStream.close()

            // Создание волны из аудио данных
            val waveform = createWaveformFromAudioData(audioData)
            view.updateVisualizer(waveform)
            // Обновление визуализатора с волной
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    fun setUpVoiceMessagePresenter(path: String) {
        Log.d("iii", "presenter $path")
        val time = HttpFileUploadManager.getVoiceLength(path)
        binding.audioPresenter.tvDuration.text = String.format(
            Locale.getDefault(), "%02d:%02d",
            TimeUnit.SECONDS.toMinutes(time),
            TimeUnit.SECONDS.toSeconds(time)
        )
        subscribeForRecordedAudioProgress()
        //    binding.record.recordingPresenterLayout.visibility = View.VISIBLE
        //  createWaveform(path,  binding.record.voicePresenterVisualizer)
        VoiceMessagePresenterManager.getInstance()
            .sendWaveDataIfSaved(path, binding.audioPresenter.playerVisualizer)
        //  recordingPresenter.updateVisualizerFromFile();
//        VoiceMessagePresenterManager.getInstance()
//            .sendWaveDataIfSaved(path, binding.record.voicePresenterVisualizer)
        // binding.record.voicePresenterVisualizer.refreshVisualizer()
        binding.audioPresenter.playerVisualizer.updatePlayerPercent(0f, false)

        binding.audioPresenter.playerVisualizer.setOnTouchListener(object :
            PlayerVisualizerView.onProgressTouch() {
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                        (view as PlayerVisualizerView).updatePlayerPercent(0f, true)
                    MotionEvent.ACTION_UP -> {
                        view.performClick()
                        return super.onTouch(view, motionEvent)
                    }
                }
                return super.onTouch(view, motionEvent)
            }
        })

        binding.audioPresenter.btnDeleteAudioMessage.setOnClickListener {
            isPlaying = false
            releaseRecordedVoicePlayback(path)
            finishVoiceRecordLayout()
            recordingPath = null
            audioProgressSubscription?.dispose()
            enableStandardPanelButtons(true)
            binding.record.cancelRecordLayout.isVisible = false
            binding.imLock.setImageResource(R.drawable.ic_lock_base)
            binding.imLockBar.setImageResource(R.drawable.ic_lock_bar)
            binding.linRecordLock.animate().y(911f).translationY(0f).start()
            binding.record.recordLayout.invalidate()
            clearVoiceMessage()

        }
        binding.audioPresenter.btnSendAudioMessage.setOnClickListener {
            sendStoppedVoiceMessage(path)
            scrollDown()
            //   setFirstUnreadMessageId(null)
            finishVoiceRecordLayout()
            recordingPath = null
            audioProgressSubscription?.dispose()

            binding.audioPresenter.recordingPresenterLayout.isVisible = false
            enableStandardPanelButtons(true)
            clearVoiceMessage()
        }
    }

    private fun sendStoppedVoiceMessage(filePath: String?) {
        sendStoppedVoiceMessage(filePath, null)
    }

    private fun sendStoppedVoiceMessage(filePath: String?, forwardIDs: List<String?>?) {
        sendVoiceMessage(filePath!!)
    }

    private fun uploadVoiceFile(path: String) {
        // отправляем на сервер
    }

    private fun releaseRecordedVoicePlayback(filePath: String?): Boolean {
        val file = File(filePath)
        if (file.exists()) {
            FileManager.deleteTempFile(file)
            return !file.exists()
        }
        return true
    }

    private fun subscribeForRecordedAudioProgress() {
        val audioProgress = PublishAudioProgress.subscribeForProgress()
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(audioRecorder.getRecordedFilePath())
        mediaPlayer.prepare()

        binding.audioPresenter.btnPlay.setOnClickListener {
            if (isPlaying) {
                mediaPlayer.pause()
                binding.audioPresenter.btnPlay.setImageResource(R.drawable.ic_play)
                isPlaying = false
            } else {
                binding.audioPresenter.btnPlay.setImageResource(R.drawable.ic_pause)
                mediaPlayer.start()
                isPlaying = true
            }
        }
    }

    private fun finishVoiceRecordLayout() {
        binding.record.recordLayout.isVisible = false
        binding.audioPresenter.recordingPresenterLayout.isVisible = false
        binding.audioPresenter.playerVisualizer.updateVisualizer(null)
        currentVoiceRecordingState = VoiceRecordState.NotRecording
    }


//    fun setVoicePresenterData(tempFilePath: String?) {
//        recordingPath = tempFilePath
//        if (recordingPath != null) {
//            setUpVoiceMessagePresenter()
//
//        }
//    }

}
