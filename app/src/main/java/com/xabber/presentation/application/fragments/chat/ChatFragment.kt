package com.xabber.presentation.application.fragments.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
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
import android.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.aghajari.emojiview.AXEmojiManager
import com.aghajari.emojiview.googleprovider.AXGoogleEmojiProvider
import com.aghajari.emojiview.view.AXSingleEmojiView
import com.xabber.R
import com.xabber.common.AccountManager
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageDisplayType
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.messages.MessageStorageItem
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.databinding.FragmentChatBinding
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.dto.MessageReferenceDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.*
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.audio.AudioRecorder
import com.xabber.presentation.application.fragments.chat.audio.PublishAudioProgress
import com.xabber.presentation.application.fragments.chat.audio.VoiceMessagePresenterManager
import com.xabber.presentation.application.fragments.chat.message.*
import com.xabber.presentation.application.fragments.contacts.AttachmentBottomSheet
import com.xabber.presentation.application.fragments.contacts.ContactAccountFragment
import com.xabber.presentation.application.fragments.contacts.ContactAccountParams
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.*
import com.xabber.utils.custom.PlayerVisualizerView
import com.xabber.xmpp.messages.message_archive.MessageArchiveManager
import com.xabber.xmpp.messages.messages_manager.MessageCommonSender
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import kotlinx.coroutines.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.experimental.and

@RequiresApi(Build.VERSION_CODES.O)
class ChatFragment : DetailBaseFragment(R.layout.fragment_chat), MessageAdapter.MenuItemListener, MessageAdapter.OnViewClickListener, ReplySwipeCallback.SwipeAction {
    private val binding by viewBinding(FragmentChatBinding::bind)
    private val handler = Handler(Looper.getMainLooper())
    private var messageAdapter: MessageAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private val audioRecorder = AudioRecorder()
    private var replySwipeCallback: ReplySwipeCallback? = null
    private var isNeedScrollDown = false
    private var editMessageId: String? = null
    private var isSelectedMode = false
    private var replyingMessage: MessageDto? = null
    private var currentVoiceRecordingState = VoiceRecordState.NotRecording
    private var recordSaveAllowed = false
    private var recordingPath: String? = null
    private var stopTypingTimer: Timer? = Timer()
    private var saveAudioMessage = true
    private var audioProgressSubscription: io.reactivex.rxjava3.disposables.Disposable? = null
    private var lockIsClosed = false
    private var isVibrate = false
    private var isPlaying = false
    private var ignoreReceiver = true // Restored ignoreReceiver
    private var messageSender: MessageCommonSender? = null
    private var messageArchiveManager: MessageArchiveManager? = null
    private val realm = Realm.open(defaultRealmConfig())
    private val viewModel: ChatViewModel by viewModel { parametersOf(getParams().id) }

    private val requestAudioPermissionResult = registerForActivityResult(ActivityResultContracts.RequestPermission(), ::onGotAudioPermissionResult)
    private val requestGalleryPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), ::onGotGalleryPermissionResult)
    private val cancelSelected = Runnable { viewModel.clearAllSelected(); viewModel.loadInitialMessages { messageAdapter?.updateAdapter(it) } }
    private val reply = Runnable { if (replyingMessage != null) replyMessage(replyingMessage!!) }
    private val unreadShower = {
        val unread = viewModel.unreadCount.value
        binding.tvNewReceivedCount.isVisible = unread != null && unread > 0
        binding.tvNewReceivedCount.text = unread?.toString() ?: ""
    }
    private val timer = Runnable {
        prepareUiForRecording()
        beginTimer(true)
        currentVoiceRecordingState = VoiceRecordState.TouchRecording
    }
    private val record = Runnable {
        val outputDir = context?.getExternalFilesDir(null)
        val fileName = "${System.currentTimeMillis()}_audio_file.mp4"
        recordingPath = File(outputDir, fileName).absolutePath
        audioRecorder.startRecord(recordingPath!!)
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
        val bot = TranslateAnimation(0f, 0f, 0f, 40f).apply {
            duration = 200L
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(p0: Animation?) {}
                override fun onAnimationEnd(p0: Animation?) {
                    binding.linRecordLock.isVisible = false
                    binding.frameStop.isVisible = true
                    val pulse = AnimationUtils.loadAnimation(context, R.anim.enlarge)
                    binding.imStop.startAnimation(pulse)
                    binding.record.slideLayout.isVisible = false
                    binding.record.cancelRecordLayout.isVisible = true
                    currentVoiceRecordingState = VoiceRecordState.StoppedRecording
                }
                override fun onAnimationRepeat(p0: Animation?) {}
            })
        }
        binding.imLockBar.animate().y(0f).translationY(25f).setDuration(200).start()
        binding.imLockBar.isVisible = false
        binding.imLock.setImageResource(R.drawable.grey_square)
        binding.linRecordLock.startAnimation(bot)
    }

    companion object {
        fun newInstance(params: ChatParams) = ChatFragment().apply {
            arguments = Bundle().apply { putParcelable(AppConstants.CHAT_PARAMS, params) }
        }
    }

    private fun getParams(): ChatParams = requireArguments().parcelable(AppConstants.CHAT_PARAMS)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val chat = viewModel.loadChat(getParams().id)
        if (chat == null) {
            navigator().closeDetail()
        } else {
            messageSender = MessageCommonSender(chat.owner)
            messageArchiveManager = MessageArchiveManager(chat.owner)
            prepareUi(chat)
            initializeToolbarActions(chat)
            initializeRecyclerView()
            initializeStandardInputLayoutActions()
            subscribeToChatData(chat)
            initializeSelectMessageToolbarActions()
            initializeSelectedMessagePanel()
            viewModel.loadInitialMessages { messageAdapter?.updateAdapter(it) }
            activity?.onBackPressedDispatcher?.addCallback(onBackPressedCallback)
            if (savedInstanceState != null) restoreState(savedInstanceState) else {
                restoreDraft()
                scrollToLastPosition()
            }
            syncChatHistory(chat)
        }
    }

    private fun syncChatHistory(chat: ChatListDto) {
        lifecycleScope.launch(Dispatchers.IO) {
            val account = AccountManager.find(chat.owner)
            if (account == null) {
                Log.e("ChatFragment", "No account found for owner ${chat.owner}")
                return@launch
            }
            val stream = account.stream
            if (stream == null) {
                Log.e("ChatFragment", "No stream available for account ${chat.owner}")
                return@launch
            }
            val conversationType = if (chat.isGroup) ConversationType.Group else ConversationType.Regular
            try {
                messageArchiveManager?.syncChat(
                    stream = stream,
                    jid = chat.opponentJid,
                    conversationType = conversationType,
                    callback = {
                        Log.d("ChatFragment", "Chat history sync completed for jid=${chat.opponentJid}")
                        viewModel.getMessageList(getParams().id)
                    }
                )
                Log.d("ChatFragment", "Initiated chat history sync for jid=${chat.opponentJid}")
            } catch (e: Exception) {
                Log.e("ChatFragment", "Failed to sync chat history for jid=${chat.opponentJid}: ${e.message}", e)
            }
        }
    }

    private fun prepareUi(chat: ChatListDto) {
        onOrientationChange()
        loadContactAvatar()
        setTitle(chat.getChatName())
        setupOpponentName(chat.getChatName())
        setStatus(chat.status, chat.entity)
        setupMuteIcon(chat.muteExpired)
    }

    private fun onOrientationChange() {
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
                binding.toolbar.setNavigationOnClickListener { navigator().closeDetail() }
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                binding.toolbar.setNavigationIcon(null)
                binding.toolbar.setNavigationOnClickListener(null)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onOrientationChange()
    }

    private fun loadContactAvatar() {
        binding.avatar.setImageResource(getParams().avatar ?: R.drawable.contact_circle)
    }

    private fun setTitle(opponentName: String) {
        binding.tvChatTitle.text = opponentName
    }

    private fun setupOpponentName(opponentName: String?) {
        binding.tvChatTitle.text = opponentName ?: "Saved messages"
    }

    private fun setStatus(resourceStatus: ResourceStatus, rosterItemEntity: RosterItemEntity) {
        val statusIcon = StatusMaker.statusIcon(rosterItemEntity)
        binding.avatarStatus.isVisible = statusIcon != null
        if (statusIcon != null) binding.avatarStatus.setImageResource(statusIcon)
    }

    private fun setupMuteIcon(muteExpired: Long) {
        val imageResource = if (muteExpired <= System.currentTimeMillis()) null
        else if (muteExpired - System.currentTimeMillis() > TimeMute.DAY1.time) R.drawable.ic_bell_off_light_grey_mini
        else R.drawable.ic_bell_sleep_light_grey_mini
        binding.tvChatTitle.setCompoundDrawablesWithIntrinsicBounds(null, null, imageResource?.let { ContextCompat.getDrawable(requireContext(), it) }, null)
    }

    private fun initializeToolbarActions(chat: ChatListDto) {
        binding.avatar.setOnClickListener {
            val contactId = viewModel.getContactId(getParams().id)
            if (contactId != null) {
                val params = ContactAccountParams(contactId, getParams().avatar)
                if (DisplayManager.getWidthDp() > 600 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    ContactAccountFragment.newInstance(params).show(childFragmentManager, AppConstants.CHAT_LIST_TO_FORWARD_DIALOG_TAG)
                } else if (DisplayManager.getWidthDp() > 800 && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    navigator().launchDetail(ContactAccountFragment.newInstance(params))
                } else {
                    navigator().showContactAccount(params)
                }
            }
        }
        initToolbarMenu(chat)
    }

    private fun initToolbarMenu(chat: ChatListDto) {
        binding.menu.setOnClickListener {
            val popup = PopupMenu(binding.menu.context, binding.menu)
            popup.menuInflater.inflate(R.menu.menu_toolbar_chat, popup.menu)
            val muteExpired = chat.muteExpired - System.currentTimeMillis()
            popup.menu.findItem(R.id.enable_notifications).isVisible = muteExpired > 0
            popup.menu.findItem(R.id.disable_notifications).isVisible = muteExpired <= 0
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.call_out -> sendIncomingMessages(chat.owner, chat.opponentJid)
                    R.id.disable_notifications -> disableNotifications()
                    R.id.enable_notifications -> enableNotifications()
                    R.id.clear_message_history -> clearHistory(chat)
                    R.id.delete_chat -> deleteChat(chat)
                }
                true
            }
            popup.show()
        }
    }
    private fun onBind(message: MessageDto?) {
        if (message != null && message.isUnread && !message.isOutgoing) {
            Log.d("ChatFragment", "Marking message as read: primary=${message.primary}")
            lifecycleScope.launch(Dispatchers.IO) {
                realm.writeBlocking {
                    val msg = query<MessageStorageItem>("primary = $0", message.primary).first().find()
                    if (msg != null && !msg.isRead) {
                        findLatest(msg)?.isRead = true
                        val chat = query<LastChatsStorageItem>("primary = $0", getParams().id).first().find()
                        if (chat != null) {
                            findLatest(chat)?.unread = maxOf(0, (chat.unread ?: 0) - 1)
                        }
                    }
                }
            }
        }
    }


    private fun sendIncomingMessages(owner: String, opponentJid: String) {
        val textRandom = arrayListOf(
            "Привет",
            "Компания «Ростелеком» открыла новый сезон строительства оптических линий связи на Южном Урале. Первым объектом для подключения стал жилой дом Челябинска в ЖК «Ньютон» на Комсомольском проспекте, 141. После его сдачи жители 132 квартир смогут пользоваться интернетом на скорости до 1 Гбит/с.",
            "Да",
            "В торжественной презентации старта нового сезона стройки приняли участие хоккеисты"
        )
        val references = ArrayList<MessageReferenceDto>()
        references.add(MessageReferenceDto("${System.currentTimeMillis()}", isGeo = true, latitude = 56.98, longitude = 67.09, size = 0L))
        lifecycleScope.launch {
            for (i in 0 until 10) {
                delay(1000)
                val m = MessageDto(
                    "${System.currentTimeMillis()}_$opponentJid",
                    false,
                    owner,
                    opponentJid,
                    "${i + 1} ${textRandom.random()}",
                    MessageSendingState.Sent,
                    System.currentTimeMillis(),
                    0,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null,
                    isGroup = false,
                    kind = null,
                    isSelected = false,
                    references = references,
                    isUnread = true,
                    isChecked = false
                )
                viewModel.insertMessage(getParams().id, m)
            }
        }
        isNeedScrollDown = layoutManager!!.findFirstVisibleItemPosition() + 2 >= (messageAdapter!!.itemCount - viewModel.unreadCount.value!!)
    }

    private fun disableNotifications() {
        NotificationBottomSheet.newInstance(getParams().id).show(childFragmentManager, AppConstants.NOTIFICATION_BOTTOM_SHEET_TAG)
    }

    private fun enableNotifications() {
        viewModel.setMute(getParams().id, 0L)
    }

    private fun clearHistory(chat: ChatListDto) {
        ChatHistoryClearDialog.newInstance(chat.getChatName(), chat.id).show(childFragmentManager, AppConstants.DELETING_CHAT_DIALOG_TAG)
    }

    private fun deleteChat(chat: ChatListDto) {
        DeletingChatDialog.newInstance(chat.getChatName(), chat.id).show(childFragmentManager, AppConstants.DELETING_CHAT_DIALOG_TAG)
    }

    private fun initializeRecyclerView() {
        val isGroup = viewModel.loadChat(getParams().id)?.isGroup ?: false
        messageAdapter = MessageAdapter(layoutInflater, this, this, ArrayList(), isGroup) { message -> onBind(message) }
        binding.messageList.adapter = messageAdapter
        layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
        binding.messageList.layoutManager = layoutManager
        binding.messageList.addItemDecoration(MessageHeaderViewDecoration(requireContext()))
        binding.messageList.itemAnimator = null
        addSwipeCallback()
        addMessageHeaderViewDecoration()
        addScrollListener()
        fillChat()
    }

    private fun addSwipeCallback() {
        replySwipeCallback = ReplySwipeCallback(requireContext()) { position ->
            messageAdapter?.getMessageItem(position)?.let { replyMessage(it) }
        }
        ItemTouchHelper(replySwipeCallback as ReplySwipeCallback).attachToRecyclerView(binding.messageList)
        binding.messageList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
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
                val firstVisible = layoutManager!!.findFirstVisibleItemPosition()
                val lastVisible = layoutManager!!.findLastVisibleItemPosition()
                if (lastVisible >= messageAdapter!!.itemCount - 2) {
                    binding.downScroller.isVisible = viewModel.unreadCount.value?.let { it > 0 && firstVisible < messageAdapter!!.itemCount - it } ?: false
                    if (lastVisible >= messageAdapter!!.itemCount - 1) loadNextMessages()
                } else if (firstVisible <= 2) {
                    loadPreviousMessages()
                }
            }
        })
        binding.btnDownward.setOnClickListener {
            val lastVisiblePosition = layoutManager!!.findLastVisibleItemPosition()
            val unreadCount = viewModel.unreadCount.value ?: 0
            if (unreadCount == 0 || lastVisiblePosition + 2 >= messageAdapter!!.itemCount - unreadCount) {
                scrollDown()
                binding.tvNewReceivedCount.isVisible = false
            } else {
                scrollToFirstUnread()
            }
        }
    }

    private fun loadPreviousMessages() {
        viewModel.loadPreviousMessages { messages ->
            if (messages.isNotEmpty()) {
                messageAdapter?.updateAdapter(messages)
                binding.messageList.post { layoutManager?.scrollToPosition(0) }
            }
        }
    }

    private fun loadNextMessages() {
        viewModel.loadNextMessages { messages ->
            if (messages.isNotEmpty()) {
                messageAdapter?.updateAdapter(messages)
                binding.messageList.post { layoutManager?.scrollToPosition(messageAdapter!!.itemCount - 1) }
            }
        }
    }

    private fun scrollDown() {
        binding.messageList.post { layoutManager?.scrollToPosition(messageAdapter?.itemCount?.minus(1) ?: 0) }
        binding.tvNewReceivedCount.isVisible = false
        messageAdapter?.setFirstUnreadMessageId(null)
        viewModel.markAllMessagesRead(getParams().id)
    }

    private fun scrollToLastPosition() {
        val lastPosition = viewModel.loadChat(getParams().id)?.lastPosition
        if (!lastPosition.isNullOrEmpty()) {
            val messagePosition = viewModel.getPositionMessage(lastPosition)
            binding.messageList.post { layoutManager?.scrollToPosition(messagePosition) }
            viewModel.saveLastPosition(getParams().id, "")
        }
    }

    private fun scrollToFirstUnread() {
        val unreadCount = viewModel.unreadCount.value ?: 0
        if (unreadCount > 0 && messageAdapter != null) {
            val position = maxOf(0, messageAdapter!!.itemCount - unreadCount)
            binding.messageList.post { layoutManager?.scrollToPositionWithOffset(position, 200) }
            binding.tvNewReceivedCount.text = unreadCount.toString()
            binding.tvNewReceivedCount.isVisible = true
        }
    }

    private fun fillChat() {
        viewModel.getMessageList(getParams().id)
    }

    private fun initializeStandardInputLayoutActions() {
        binding.chatInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                setupInputButtons()
            }
        })
        binding.buttonEmoticon.setOnClickListener {
            val emojiView = AXSingleEmojiView(requireContext())
            emojiView.editText = binding.chatInput
            binding.emojiPopupLayout.initPopupView(emojiView)
        }
        binding.buttonAttach.setOnClickListener {
            requestGalleryPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
        binding.buttonSendMessage.setOnClickListener {
            if (editMessageId != null) {
                viewModel.editMessage(editMessageId!!, binding.chatInput.text.toString())
                binding.chatInput.text?.clear()
                editMessageId = null
            } else {
                val text = binding.chatInput.text.toString().trim()
                if (text.isEmpty() && replyingMessage == null) return@setOnClickListener
                binding.chatInput.text?.clear()
                val chat = viewModel.loadChat(getParams().id)!!
                val forwarded = if (replyingMessage != null) listOf(replyingMessage!!.primary) else emptyList()
                lifecycleScope.launch {
                    messageSender?.sendSimpleMessage(
                        body = text,
                        recipientJid = chat.opponentJid,
                        forwarded = forwarded,
                        conversationType = if (chat.isGroup) ConversationType.Group else ConversationType.Regular
                    )
                }
                binding.answer.isVisible = false
                replyingMessage = null
                scrollDown()
            }
        }
        initializeButtonRecord()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeButtonRecord() {
        binding.btnRecord.setOnTouchListener { _, motionEvent ->
            when (motionEvent.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    if (isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
                        if (currentVoiceRecordingState == VoiceRecordState.NotRecording) startAudioRecord()
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
                            val elapsedTime = SystemClock.elapsedRealtime() - binding.record.chrRecordingTimer.base
                            if (elapsedTime / 1000 >= 1) {
                                audioRecorder.stopRecord()
                                sendVoiceMessage(recordingPath!!)
                                hideRecordPanel()
                                navigator().lockScreen(false)
                            } else {
                                audioRecorder.stopRecord()
                                hideRecordPanel()
                                currentVoiceRecordingState = VoiceRecordState.NotRecording
                                navigator().lockScreen(false)
                            }
                        }
                        VoiceRecordState.NoTouchRecording -> handler.post(stop)
                        else -> {
                            binding.record.chrRecordingTimer.stop()
                            binding.record.recordLayout.startAnimation(AnimationUtils.loadAnimation(context, R.anim.slide_to_right))
                            binding.record.recordLayout.isVisible = false
                            binding.linRecordLock.isVisible = false
                            binding.btnRecordExpanded.isVisible = false
                            handler.removeCallbacks(record)
                            handler.removeCallbacks(timer)
                            stopTypingTimer?.cancel()
                            navigator().lockScreen(false)
                            if (saveAudioMessage && isPermissionGranted(Manifest.permission.RECORD_AUDIO)) sendVoiceMessage(recordingPath!!)
                            hideRecordPanel()
                            currentVoiceRecordingState = VoiceRecordState.NotRecording
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    when {
                        motionEvent.y < -55 -> {
                            val params = binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams
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
                            val params = binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams
                            params.bottomMargin = (-motionEvent.y / 4).toInt()
                            if (params.bottomMargin in 2..11) binding.imLockBar.layoutParams = params
                            currentVoiceRecordingState = VoiceRecordState.TouchRecording
                        }
                    }
                    val alpha = 1f + motionEvent.x / 400f
                    if (motionEvent.x < 0) binding.record.slideLayout.animate().x(motionEvent.x).start() else binding.record.slideLayout.animate().x(0f).start()
                    binding.record.slideLayout.alpha = alpha
                    if (alpha <= 0) {
                        saveAudioMessage = false
                        binding.record.recordLayout.startAnimation(AnimationUtils.loadAnimation(context, R.anim.slide_to_right))
                        binding.record.recordLayout.isVisible = false
                        hideRecordPanel()
                        binding.record.slideLayout.x = 0f
                        currentVoiceRecordingState = VoiceRecordState.NotRecording
                    }
                }
            }
            true
        }
        binding.frameStop.setOnClickListener {
            binding.frameStop.isVisible = false
            binding.btnRecordExpanded.hide()
            binding.record.recordLayout.isVisible = false
            binding.audioPresenter.recordingPresenterLayout.isVisible = true
            audioRecorder.stopRecord()
            setUpVoiceMessagePresenter(recordingPath!!)
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
            sendVoiceMessage(recordingPath!!)
            clearVoiceMessage()
            isPlaying = false
            enableStandardPanelButtons(true)
        }
        binding.btnRecordExpanded.setOnClickListener {
            if (isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
                audioRecorder.stopRecord()
                sendVoiceMessage(recordingPath!!)
                clearVoiceMessage()
            }
        }
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
    }

    private fun beginTimer(start: Boolean) {
        if (start) {
            binding.record.chrRecordingTimer.base = SystemClock.elapsedRealtime()
            binding.record.chrRecordingTimer.start()
        } else {
            binding.record.chrRecordingTimer.stop()
        }
    }

    private fun shortVibrate() {
        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }

    private fun hideRecordPanel() {
        binding.record.recordLayout.isVisible = false
        binding.linRecordLock.isVisible = false
        binding.btnRecordExpanded.hide()
        binding.btnRecordExpanded.isVisible = false
        enabledInputPanelButtons(true)
        binding.record.cancelRecordLayout.isVisible = false
    }

    private fun enableStandardPanelButtons(enable: Boolean) {
        binding.buttonEmoticon.isEnabled = enable
        binding.buttonAttach.isEnabled = enable
        binding.buttonSendMessage.isEnabled = enable
    }

    private fun enabledInputPanelButtons(enabled: Boolean) {
        binding.buttonEmoticon.isEnabled = enabled
        binding.btnDownward.isEnabled = enabled
    }

    private fun sendVoiceMessage(path: String) {
        isPlaying = false
        binding.linRecordLock.invalidate()
        enableStandardPanelButtons(true)
        beginTimer(false)
        val reference = MessageReferenceDto("${System.currentTimeMillis()}", uri = path, size = 0L, isVoiceMessage = true)
        viewModel.insertMessage(
            getParams().id,
            MessageDto(
                primary = "${System.currentTimeMillis()}",
                isOutgoing = true,
                owner = viewModel.loadChat(getParams().id)!!.owner,
                opponentJid = viewModel.loadChat(getParams().id)!!.opponentJid,
                messageBody = "",
                messageSendingState = MessageSendingState.Deliver,
                sentTimestamp = System.currentTimeMillis(),
                editTimestamp = 0,
                displayType = MessageDisplayType.Text,
                canEditMessage = true,
                canDeleteMessage = true,
                urlAvatar = null,
                isGroup = viewModel.loadChat(getParams().id)!!.isGroup,
                kind = null,
                isSelected = false,
                references = arrayListOf(reference),
                isUnread = false,
                isChecked = false
            )
        )
        scrollDown()
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
        binding.imLockBar.setImageResource(R.drawable.ic_lock_bar)
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
        (binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams).bottomToBottom = binding.imLock.id
        currentVoiceRecordingState = VoiceRecordState.NotRecording
        lockIsClosed = false
        recordingPath = null
    }

    private fun subscribeToChatData(chat: ChatListDto) {
        viewModel.chat.observe(viewLifecycleOwner) {
            if (it == null) navigator().closeDetail()
        }
        viewModel.opponentName.observe(viewLifecycleOwner) { setupOpponentName(it) }
        viewModel.muteExpired.observe(viewLifecycleOwner) { if (it != null) setupMuteIcon(it) }
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            Log.d("ChatFragment", "Messages LiveData updated: ${messages.size} messages")
            messageAdapter?.updateAdapter(messages)
            if (layoutManager != null && messages.isNotEmpty()) {
                if (isNeedScrollDown || layoutManager!!.findLastVisibleItemPosition() >= messageAdapter!!.itemCount - 2) {
                    scrollDown()
                    isNeedScrollDown = false
                }
            }
        }
        viewModel.loading.observe(viewLifecycleOwner) { binding.progressBar.isVisible = it }
        viewModel.unreadCount.observe(viewLifecycleOwner) { unread ->
            if (!isAdded || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@observe
            lifecycleScope.launch(Dispatchers.Main) {
                realm.writeBlocking {
                    val chat = query<LastChatsStorageItem>("primary = $0", getParams().id).first().find()
                    if (chat != null) findLatest(chat)?.unread = unread
                }
                showUnreadBadge(unread)
                binding.downScroller.isVisible = unread > 0
            }
        }
        viewModel.selectedCount.observe(viewLifecycleOwner) {
            if (it > 0) {
                binding.selectMessagesToolbar.tvMessagesCount.text = it.toString()
                binding.selectMessagesToolbar.toolbarSelectedMessages.menu.findItem(R.id.edit_message).isVisible = it == 1 && viewModel.isOutgoing()
                binding.interaction.linReply.isVisible = it == 1
            } else {
                enableSelectionMode(false)
            }
        }
    }

    private fun showUnreadBadge(count: Int) {
        handler.removeCallbacks(unreadShower)
        if (count > 0) handler.postDelayed(unreadShower, 150) else unreadShower.invoke()
    }

    private fun initializeSelectMessageToolbarActions() {
        binding.selectMessagesToolbar.imCloseSelectedMode.setOnClickListener { enableSelectionMode(false) }
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
                R.id.delete_message -> delete()
            }
            true
        }
    }

    private fun initializeSelectedMessagePanel() {
        binding.interaction.linReply.setOnClickListener {
            viewModel.getMessage()?.let { replyMessage(it) }
            enableSelectionMode(false)
        }
        binding.interaction.linForward.setOnClickListener {
            val text = viewModel.getForwardMessagesText()
            enableSelectionMode(false)
            GlobalScope.launch {
                delay(300)
                navigator().showForwardFragment(text, viewModel.loadChat(getParams().id)?.owner ?: "")
            }
        }
    }

    private fun edit() {
        val text = viewModel.getSelectedMessageText()
        val id = viewModel.getMessageId()
        binding.chatInput.setText(text)
        binding.chatInput.setSelection(binding.chatInput.length())
        editMessageId = id
    }

    private fun copyTextMessage(text: String? = null) {
        val clipBoard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("", text ?: viewModel.getSelectedText())
        clipBoard.setPrimaryClip(clipData)
        showToast(R.string.snack_bar_title_copy_text)
    }

    private fun delete(id: String? = null) {
        DeletingMessageDialog.newInstance(binding.tvChatTitle.text.toString(), id).show(childFragmentManager, AppConstants.DELETING_MESSAGE_DIALOG_TAG)
        setFragmentResultListener(AppConstants.DELETING_MESSAGE_DIALOG_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.DELETING_MESSAGE_BUNDLE_KEY)
            val forAll = bundle.getBoolean(AppConstants.DELETING_MESSAGE_FOR_ALL_BUNDLE_KEY)
            if (result) {
                if (id != null) viewModel.deleteMessage(id, forAll) else viewModel.deleteMessages(forAll)
                enableSelectionMode(false)
            }
        }
    }

    override fun copyText(text: String) = copyTextMessage(text)
    override fun pinMessage(messageDto: MessageDto) {
        binding.pinPanel.isVisible = true
        binding.tvPinOwner.text = if (messageDto.isOutgoing) messageDto.owner else binding.tvChatTitle.text.toString()
        binding.tvPinContent.text = messageDto.messageBody
        binding.pinPanel.setOnClickListener {
            val position = viewModel.getMessagePosition(messageDto.primary)
            binding.messageList.scrollToPosition(position)
            viewModel.selectMessage(messageDto.primary, true)
            handler.postDelayed(cancelSelected, 1000)
        }
        binding.imPinClose.setOnClickListener { binding.pinPanel.isVisible = false }
    }
    override fun forwardMessage(messageDto: MessageDto) {
        val text = "${messageDto.owner}\n${messageDto.messageBody}"
        navigator().showForwardFragment(text, viewModel.loadChat(getParams().id)?.owner ?: "")
    }
    override fun replyMessage(messageDto: MessageDto) {
        binding.answer.isVisible = true
        binding.replyMessageTitle.text = if (messageDto.isOutgoing) messageDto.owner else binding.tvChatTitle.text.toString()
        binding.replyMessageContent.text = messageDto.messageBody
        binding.close.setOnClickListener {
            binding.replyMessageTitle.text = ""
            binding.replyMessageContent.text = ""
            binding.answer.isVisible = false
            replyingMessage = null
        }
        replyingMessage = messageDto
    }
    override fun editMessage(primary: String, text: String) = edit()
    override fun deleteMessage(primary: String) = delete(primary)
    override fun onLongClick(primary: String) {
        enableSelectionMode(true)
        Check.setSelectedMode(true)
        viewModel.selectMessage(primary, true)
        messageAdapter?.notifyItemChanged(viewModel.getMessagePosition(primary))
    }
    override fun checkItem(isChecked: Boolean, primary: String) {
        Log.d("ChatFragment", "checkItem: primary=$primary, isChecked=$isChecked")
        viewModel.selectMessage(primary, isChecked)
        messageAdapter?.notifyItemChanged(viewModel.getMessagePosition(primary))
    }
    override fun onFullSwipe(position: Int) {
        handler.postDelayed(reply, 1500)
    }
    override fun onImageOrVideoClick(startPosition: Int, messageId: String) {
        startActivity(Intent(requireContext(), MediaDetailsActivity::class.java).apply {
            putExtra(AppConstants.START_POSITION, startPosition)
            putExtra(AppConstants.MESSAGE_UID, messageId)
        })
    }
    override fun onLocationClick(latitude: Double, longitude: Double) {
        startActivity(Intent().apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
        })
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
        isSelectedMode = enable
        Check.setSelectedMode(enable)
        binding.appbar.setBackgroundResource(if (enable) R.color.white else ColorManager.convertColorNameToId(viewModel.loadChat(getParams().id)?.colorKey ?: "blue"))
        binding.toolbar.isVisible = !enable
        binding.selectMessagesToolbar.toolbarSelectedMessages.isVisible = enable
        binding.interaction.interactionView.isVisible = enable
        replySwipeCallback?.setSwipeEnabled(!enable)
        binding.buttonEmoticon.isEnabled = !enable
        binding.buttonAttach.isEnabled = !enable
        binding.btnRecord.isEnabled = !enable
        binding.chatInput.isEnabled = !enable
        if (!enable) viewModel.clearAllSelected()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(AppConstants.CHAT_SELECTION_MODE_KEY, isSelectedMode)
        if (recordingPath != null) outState.putString("VOICE_MESSAGE", recordingPath)
        outState.putBoolean("VOICE_MESSAGE_RECEIVER_IGNORE", ignoreReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        saveLastPosition()
        saveDraft()
        AccountManager.unregisterChatViewModel(getParams().id)
        messageSender?.unsubscribeSender()
        messageArchiveManager = null
        onBackPressedCallback.remove()
        messageAdapter = null
    }

    override fun onDestroy() {
        super.onDestroy()
        messageAdapter = null
    }

    private fun saveLastPosition() {
        val lastPosition = layoutManager?.findLastVisibleItemPosition()
        if (lastPosition != null && messageAdapter?.itemCount ?: 0 > 0) {
            messageAdapter?.getMessageItem(lastPosition)?.primary?.let { viewModel.saveLastPosition(getParams().id, it) }
        }
    }

    private fun saveDraft() {
        val inputText = binding.chatInput.text.toString().trimEnd()
        viewModel.saveDraft(getParams().id, if (inputText.isEmpty()) null else inputText)
    }

    private fun restoreState(savedInstanceState: Bundle) {
        binding.chatInput.setText(savedInstanceState.getString(AppConstants.CHAT_MESSAGE_TEXT_KEY))
        isSelectedMode = savedInstanceState.getBoolean(AppConstants.CHAT_SELECTION_MODE_KEY)
        enableSelectionMode(isSelectedMode)
        savedInstanceState.getString("VOICE_MESSAGE")?.let { path ->
            recordingPath = path
            currentVoiceRecordingState = VoiceRecordState.StoppedRecording
            binding.record.recordLayout.isVisible = false
            binding.audioPresenter.recordingPresenterLayout.isVisible = true
            setUpVoiceMessagePresenter(path)
        }
        ignoreReceiver = savedInstanceState.getBoolean("VOICE_MESSAGE_RECEIVER_IGNORE")
    }

    private fun restoreDraft() {
        viewModel.loadChat(getParams().id)?.draftMessage?.let { binding.chatInput.setText(it) }
        setupInputButtons()
    }

    private fun setupInputButtons() {
        binding.btnRecord.isVisible = binding.chatInput.text.toString().trimEnd().isEmpty()
        binding.buttonAttach.isVisible = binding.chatInput.text.toString().trimEnd().isEmpty()
        binding.buttonSendMessage.isVisible = binding.chatInput.text.toString().trimEnd().isNotEmpty()
    }

    private fun onGotAudioPermissionResult(granted: Boolean) {
        if (!granted) askUserForOpeningAppSettings()
    }

    private fun onGotGalleryPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value }) showAttachBottomSheet()
        else askUserForOpeningAppSettings()
    }

    private fun showAttachBottomSheet() {
        if (childFragmentManager.findFragmentByTag(AppConstants.ATTACH_BOTTOM_SHEET_TAG) == null)
            AttachmentBottomSheet.newInstance(getParams().id).show(childFragmentManager, AppConstants.ATTACH_BOTTOM_SHEET_TAG)
    }

    private fun startAudioRecord() {
        handler.postDelayed(timer, 500)
        handler.postDelayed(record, 500)
        saveAudioMessage = true
        enableStandardPanelButtons(false)
    }


    private fun setUpVoiceMessagePresenter(path: String) {
        val time = HttpFileUploadManager.getVoiceLength(path)
        binding.audioPresenter.tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d", TimeUnit.SECONDS.toMinutes(time), time % 60)
        VoiceMessagePresenterManager.getInstance().sendWaveDataIfSaved(path, binding.audioPresenter.playerVisualizer)
        binding.audioPresenter.playerVisualizer.updatePlayerPercent(0f, false)
        binding.audioPresenter.playerVisualizer.setOnTouchListener(object : PlayerVisualizerView.onProgressTouch() {
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> (view as PlayerVisualizerView).updatePlayerPercent(0f, true)
                    MotionEvent.ACTION_UP -> view.performClick()
                }
                return super.onTouch(view, motionEvent)
            }
        })
        binding.audioPresenter.btnPlay.setOnClickListener {
            val mediaPlayer = MediaPlayer().apply { setDataSource(path); prepare() }
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
            sendVoiceMessage(path)
            scrollDown()
            finishVoiceRecordLayout()
            recordingPath = null
            audioProgressSubscription?.dispose()
            binding.audioPresenter.recordingPresenterLayout.isVisible = false
            enableStandardPanelButtons(true)
            clearVoiceMessage()
        }
    }

    private fun finishVoiceRecordLayout() {
        binding.record.recordLayout.isVisible = false
        binding.audioPresenter.recordingPresenterLayout.isVisible = false
        binding.audioPresenter.playerVisualizer.updateVisualizer(null)
        currentVoiceRecordingState = VoiceRecordState.NotRecording
    }

    private fun sendStoppedVoiceMessage(filePath: String?) {
        if (filePath != null) sendVoiceMessage(filePath)
    }

    private fun sendStoppedVoiceMessage(filePath: String?, forwardIDs: List<String?>?) {
        if (filePath != null) sendVoiceMessage(filePath)
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
        audioProgressSubscription?.dispose()
        audioProgressSubscription = PublishAudioProgress.subscribeForProgress()?.subscribe {
            // Handle progress updates if needed
        }
        val mediaPlayer = MediaPlayer()
        recordingPath?.let { path ->
            mediaPlayer.setDataSource(path)
            mediaPlayer.prepare()
        }
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

    private fun fileIsImage(file: File): Boolean {
        return extensionIsImage(file.path)
    }

    private fun extensionIsImage(path: String?): Boolean {
        if (path == null || path.isEmpty()) return false
        return arrayOf("webp", "jpeg", "jpg", "png", "jpe", "gif").contains(path.substringAfterLast("."))
    }

    private fun createWaveformFromAudioData(audioData: ByteArray): ArrayList<Int> {
        val waveform: ArrayList<Int> = ArrayList()
        for (i in audioData.indices) {
            val value: Byte = audioData[i] and 0xFF.toByte()
            waveform.add(value.toInt())
        }
        return waveform
    }

    private fun createWaveform(filePath: String, view: PlayerVisualizerView) {
        val file = File(filePath)
        if (!file.exists()) return
        try {
            val inputStream: FileInputStream = FileInputStream(file)
            val audioData = ByteArray(file.length().toInt())
            inputStream.read(audioData)
            inputStream.close()
            val waveform = createWaveformFromAudioData(audioData)
            view.updateVisualizer(waveform)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private enum class VoiceRecordState {
        NotRecording, InitiatedRecording, TouchRecording, NoTouchRecording, StoppedRecording
    }
}