package com.xabber.presentation.application.fragments.chat.view

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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.aghajari.emojiview.AXEmojiManager
import com.aghajari.emojiview.googleprovider.AXGoogleEmojiProvider
import com.aghajari.emojiview.view.AXSingleEmojiView
import com.xabber.R
import com.xabber.account.AccountManager
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
import com.xabber.presentation.AppConstants.CHAT_MESSAGE_TEXT_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_BUNDLE_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_DIALOG_KEY
import com.xabber.presentation.AppConstants.DELETING_MESSAGE_FOR_ALL_BUNDLE_KEY
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.*
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.FileManager
import com.xabber.presentation.application.fragments.chat.HttpFileUploadManager
import com.xabber.presentation.application.fragments.chat.MediaDetailsActivity
import com.xabber.presentation.application.fragments.chat.MessageAdapter
import com.xabber.presentation.application.fragments.chat.ReplySwipeCallback
import com.xabber.presentation.application.fragments.chat.StatusMaker
import com.xabber.presentation.application.fragments.chat.audio.AudioRecorder
import com.xabber.presentation.application.fragments.chat.audio.PublishAudioProgress
import com.xabber.presentation.application.fragments.chat.audio.VoiceMessagePresenterManager
import com.xabber.presentation.application.fragments.chat.message.*
import com.xabber.presentation.application.fragments.chat.viewmodel.ChatViewModel
import com.xabber.presentation.application.fragments.contacts.AttachmentBottomSheet
import com.xabber.presentation.application.fragments.contacts.ContactAccountFragment
import com.xabber.presentation.application.fragments.contacts.ContactAccountParams
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.*
import com.xabber.utils.custom.PlayerVisualizerView
import com.xabber.xmpp.jid.XMPPJID
import com.xabber.xmpp.messages.messages_manager.MessageCommonSender
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.experimental.and

@RequiresApi(Build.VERSION_CODES.O)
class ChatView : DetailBaseFragment(R.layout.fragment_chat),
    MessageAdapter.MenuItemListener,
    MessageAdapter.OnViewClickListener, ReplySwipeCallback.SwipeAction {

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
    private var isPlaying = false
    private var messageSender: MessageCommonSender? = null
    private var lastLoadOlderMessagesTime = 0L // For debouncing
    private val debounceInterval = 500L // 500ms debounce
    private var isLoadingHistory = false

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
    }

    private val reply = Runnable {
        if (replyingMessage != null) replyMessage(replyingMessage!!)
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
        val outputDir = context?.getExternalFilesDir(null)
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
        binding.imLockBar.animate().y(0f).translationY(25f).setDuration(200).start()
        binding.imLockBar.isVisible = false
        binding.imLock.setImageResource(R.drawable.grey_square)
        binding.linRecordLock.startAnimation(bot)
    }

    companion object {
        fun newInstance(params: ChatParams) = ChatView().apply {
            arguments = Bundle().apply {
                putParcelable(AppConstants.CHAT_PARAMS, params)
            }
        }
    }

    private fun getParams(): ChatParams = requireArguments().parcelable(AppConstants.CHAT_PARAMS)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val chat = viewModel.loadChat(getParams().id)
        if (chat == null) {
            navigator().closeDetail()
            return
        }

        // Convert to bare JIDs
        val bareOwner = try {
            XMPPJID(fullJID = chat.owner).bare()
        } catch (e: IllegalArgumentException) {
            Log.e("ChatView", "Invalid owner JID: ${chat.owner}, ${e.message}")
            navigator().closeDetail()
            return
        }
        val bareOpponent = try {
            XMPPJID(fullJID = chat.opponentJid).bare()
        } catch (e: IllegalArgumentException) {
            Log.e("ChatView", "Invalid opponent JID: ${chat.opponentJid}, ${e.message}")
            navigator().closeDetail()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            AccountManager.find(bareOwner)?.action { account, stream ->
                account.messageArchiveManager.syncChat(stream, bareOpponent, viewModel.conversationType)
            } ?: Log.e("ChatView", "Account not found for owner=$bareOwner")
        }

        messageSender = MessageCommonSender(bareOwner)
        prepareUi(chat)
        initializeToolbarActions(chat)
        initializeRecyclerView()
        initializeStandardInputLayoutActions()
        initializeSelectMessageToolbarActions()
        initializeSelectedMessagePanel()
        subscribeToChatData(chat)
        AccountManager.registerChatViewModel(getParams().id, viewModel)
        activity?.onBackPressedDispatcher?.addCallback(onBackPressedCallback)
        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        } else {
            restoreDraft()
        }
        viewModel.setLocked(true)
        lifecycleScope.launch {
            val account = AccountManager.find(bareOwner)
            if (account != null) {
                account.action { acc, stream ->
                    Log.d("ChatView", "Starting MAM sync for chat: owner=$bareOwner, opponent=$bareOpponent, type=${viewModel.conversationType}")
                    acc.messageArchiveManager.syncChat(
                        stream = stream,
                        jid = bareOpponent,
                        conversationType = viewModel.conversationType
                    )
                }
            } else {
                Log.e("ChatView", "Account not found for owner=$bareOwner")
            }
        }
        viewModel.setLocked(false)

        binding.messageList.post {
            scrollDown()
        }
    }

    private fun prepareUi(chat: ChatListDto) {
        onOrientationChange()
        loadContactAvatar()
        setTitle(chat.getChatName())
        setStatus(chat.status, chat.entity)
        setupMuteIcon(chat.muteExpired)
    }

    private fun onOrientationChange() {
        updateToolbarNavigation()
    }

    private fun updateToolbarNavigation() {
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
        updateToolbarNavigation()
    }

    private fun loadContactAvatar() {
        binding.avatar.setImageResource(getParams().avatar!!)
    }

    private fun setTitle(opponentName: String) {
        binding.tvChatTitle.text = opponentName
    }

    private fun setStatus(resourceStatus: ResourceStatus, rosterItemEntity: RosterItemEntity) {
        val statusIcon = StatusMaker.statusIcon(RosterItemEntity.BOT)
        val statusTint = StatusMaker.statusTint(ResourceStatus.DND)
        if (statusIcon != null) {
            binding.avatarStatus.isVisible = true
        } else {
            binding.avatarStatus.isVisible = false
        }
    }

    private fun setupMuteIcon(muteExpired: Long) {
        val imageResource = if (muteExpired - System.currentTimeMillis() <= 0) null
        else if ((muteExpired - System.currentTimeMillis()) > TimeMute.DAY1.time) R.drawable.ic_bell_off_light_grey_mini
        else R.drawable.ic_bell_sleep_light_grey_mini
        var drawable: Drawable? = null
        if (imageResource != null) drawable = ContextCompat.getDrawable(requireContext(), imageResource)
        binding.tvChatTitle.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
    }

    private fun initializeToolbarActions(chat: ChatListDto) {
        binding.avatar.setOnClickListener { anchor ->
            val contactId = viewModel.getContactId(getParams().id)
            if (contactId != null) {
                val params = ContactAccountParams(contactId, getParams().avatar)
                if (DisplayManager.getWidthDp() > 600 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    val accDialog = ContactAccountFragment.newInstance(params)
                    accDialog.show(childFragmentManager, AppConstants.CHAT_LIST_TO_FORWARD_DIALOG_TAG)
                } else if (DisplayManager.getWidthDp() > 800 && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    navigator().launchDetail(ContactAccountFragment.newInstance(params))
                } else {
                    navigator().showContactAccount(ContactAccountParams(contactId, getParams().avatar))
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

    private fun setupToolbarMenu(mute: Long) {
        val muteExpired = mute - System.currentTimeMillis()
        binding.toolbar.menu.findItem(R.id.enable_notifications).isVisible = muteExpired > 0
        binding.toolbar.menu.findItem(R.id.disable_notifications).isVisible = muteExpired <= 0
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
        val lastPosition = viewModel.lastPositionPrimary(getParams().id)
        val position = viewModel.getPositionMessage(lastPosition)
        binding.messageList.post {
            if (position > 0 && position < (messageAdapter?.itemCount ?: 0)) {
                layoutManager?.scrollToPosition(position)
            } else {
                scrollDown()
            }
        }
    }

    private fun setupInputButtons() {
        val isInputNotEmpty = binding.chatInput.text.toString().trim().isNotEmpty()
        binding.btnRecord.isVisible = !isInputNotEmpty
        binding.buttonAttach.isVisible = !isInputNotEmpty
        binding.buttonSendMessage.isVisible = isInputNotEmpty || replyingMessage != null
        binding.buttonEmoticon.isEnabled = true
        binding.buttonAttach.isEnabled = true
        binding.btnRecord.isEnabled = true
        binding.buttonSendMessage.isEnabled = isInputNotEmpty || replyingMessage != null
    }

    private fun disableNotifications() {
        val dialog = NotificationBottomSheet.newInstance(getParams().id)
        dialog.show(childFragmentManager, AppConstants.NOTIFICATION_BOTTOM_SHEET_TAG)
    }

    private fun enableNotifications() {
        viewModel.setMute(getParams().id, enableNotificationsCode)
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
        messageAdapter = MessageAdapter(
            layoutInflater,
            this,
            onViewClickListener = this,
            isGroup = isGroup
        )
        binding.messageList.adapter = messageAdapter
        layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
            reverseLayout = false
        }
        binding.messageList.layoutManager = layoutManager
        addSwipeCallback()
        addMessageHeaderViewDecoration()
        addScrollListener()
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
                if (layoutManager != null) {
                    val firstVisiblePosition = layoutManager!!.findFirstVisibleItemPosition()

                    if (firstVisiblePosition <= 2 && !isLoadingHistory) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastLoadOlderMessagesTime >= debounceInterval) {
                            lastLoadOlderMessagesTime = currentTime
                            loadOlderMessages()
                        }
                    }

                    val lastVisible = layoutManager!!.findLastVisibleItemPosition()
                    if (lastVisible >= messageAdapter!!.itemCount - 1) {
                        binding.downScroller.isVisible = false
                    } else {
                        if (currentVoiceRecordingState !in listOf(
                                VoiceRecordState.TouchRecording,
                                VoiceRecordState.InitiatedRecording,
                                VoiceRecordState.NoTouchRecording
                            )) {
                            binding.downScroller.isVisible = viewModel.unreadCount.value ?: 0 > 0
                        }
                    }
                }
            }
        })

        binding.btnDownward.setOnClickListener {
            val lastVisiblePosition = layoutManager!!.findLastVisibleItemPosition()
            if (viewModel.unreadCount.value == 0 ||
                lastVisiblePosition + 2 >= messageAdapter!!.itemCount - viewModel.unreadCount.value!!) {
                scrollDown()
                binding.tvNewReceivedCount.text = ""
                binding.tvNewReceivedCount.isVisible = false
            } else {
                scrollToFirstUnread()
            }
        }
    }

    private fun loadOlderMessages() {
        if (isLoadingHistory) return

        isLoadingHistory = true
        binding.progressBar.isVisible = true
        binding.overlay.isVisible = true
        viewModel.setLocked(true)

        // Save current scroll position
        val firstVisiblePosition = layoutManager!!.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager!!.findViewByPosition(firstVisiblePosition)
        val offset = firstVisibleView?.top ?: 0
        val firstVisibleItem = messageAdapter?.getMessageItem(firstVisiblePosition)
        val firstArchivedId = firstVisibleItem?.archivedId
        val currentItemCount = messageAdapter!!.itemCount

        lifecycleScope.launch {
            try {
                val bareOwner = try {
                    XMPPJID(fullJID = viewModel.owner).bare()
                } catch (e: IllegalArgumentException) {
                    Log.e("ChatView", "Invalid owner JID: ${viewModel.owner}, ${e.message}")
                    isLoadingHistory = false
                    binding.progressBar.isVisible = false
                    binding.overlay.isVisible = false
                    viewModel.setLocked(false)
                    return@launch
                }
                val bareOpponent = try {
                    XMPPJID(fullJID = viewModel.opponent).bare()
                } catch (e: IllegalArgumentException) {
                    Log.e("ChatView", "Invalid opponent JID: ${viewModel.opponent}, ${e.message}")
                    isLoadingHistory = false
                    binding.progressBar.isVisible = false
                    binding.overlay.isVisible = false
                    viewModel.setLocked(false)
                    return@launch
                }

                val account = AccountManager.find(bareOwner)
                account?.action { acc, stream ->
                    acc.messageArchiveManager.getPrevHistory(
                        stream = stream,
                        jid = bareOpponent,
                        conversationType = viewModel.conversationType,
                        messageId = firstArchivedId ?: "",
                        callback = {
                            lifecycleScope.launch(Dispatchers.Main) {
                                isLoadingHistory = false
                                binding.progressBar.isVisible = false
                                binding.overlay.isVisible = false
                                viewModel.setLocked(false)

                                val newItemCount = messageAdapter!!.itemCount
                                val insertedCount = newItemCount - currentItemCount
                                if (insertedCount > 0 && firstVisiblePosition != RecyclerView.NO_POSITION) {
                                    layoutManager!!.scrollToPositionWithOffset(
                                        firstVisiblePosition + insertedCount,
                                        offset
                                    )
                                }
                            }
                        }
                    )
                } ?: Log.e("ChatView", "Account not found for owner=$bareOwner")
            } catch (e: Exception) {
                Log.e("ChatView", "Error loading older messages", e)
                isLoadingHistory = false
                binding.progressBar.isVisible = false
                binding.overlay.isVisible = false
                viewModel.setLocked(false)
            }
        }
    }

    private fun scrollToFirstUnread() {
        val unreadCount = viewModel.unreadCount.value ?: 0
        if (unreadCount > 0 && messageAdapter != null && messageAdapter!!.itemCount > 0) {
            // Since ViewModel has messageList updated, use it
            val messages = viewModel.messages.value ?: emptyList()
            val position = messages.indexOfFirst { it.isUnread }
            if (position >= 0) {
                layoutManager?.scrollToPositionWithOffset(position, 200)
                binding.tvNewReceivedCount.text = unreadCount.toString()
                binding.tvNewReceivedCount.isVisible = true
                messageAdapter?.setFirstUnreadMessageId(messages[position].primary)
            }
        }
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
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                setupInputButtons()
                binding.buttonSendMessage.isEnabled = p0.toString().trim().isNotEmpty() || replyingMessage != null
            }
        })
    }

    private fun initializeButtonEmoji() {
        AXEmojiManager.install(requireContext(), AXGoogleEmojiProvider(requireContext()))
        val emojiView = AXSingleEmojiView(requireContext())
        emojiView.editText = binding.chatInput
        binding.emojiPopupLayout.initPopupView(emojiView)
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
            val text = binding.chatInput.text.toString().trim()
            if (text.isEmpty() && replyingMessage == null) {
                return@setOnClickListener
            }
            if (editMessageId != null) {
                viewModel.editMessage(editMessageId!!, text)
                binding.chatInput.text?.clear()
                editMessageId = null
            } else {
                val chat = viewModel.loadChat(getParams().id)!!
                val conversationType = if (chat.isGroup) ConversationType.Group else ConversationType.Regular
                val forwarded = if (replyingMessage != null) listOf(replyingMessage!!.primary) else emptyList()
                lifecycleScope.launch {
                    messageSender?.sendSimpleMessage(
                        body = text,
                        recipientJid = chat.opponentJid,
                        forwarded = forwarded,
                        conversationType = conversationType
                    )


                }
                binding.chatInput.text?.clear()
                binding.answer.isVisible = false
                replyingMessage = null
                isNeedScrollDown = true
                scrollDown()
            }
        }
        // Ensure button is enabled based on input text or replying message
        binding.buttonSendMessage.isEnabled = binding.chatInput.text.toString().trim().isNotEmpty() || replyingMessage != null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeButtonRecord() {
        binding.btnRecord.setOnTouchListener { _, motionEvent ->
            when (motionEvent.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    if (isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
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
                            val animRight = AnimationUtils.loadAnimation(context, R.anim.slide_to_right)
                            binding.record.recordLayout.startAnimation(animRight)
                            binding.record.recordLayout.isVisible = false
                            binding.linRecordLock.isVisible = false
                            binding.btnRecordExpanded.isVisible = false
                            handler.removeCallbacks(record)
                            handler.removeCallbacks(timer)
                            stopTypingTimer?.cancel()
                            navigator().lockScreen(false)
                            if (saveAudioMessage && isPermissionGranted(Manifest.permission.RECORD_AUDIO))
                                sendVoiceMessage(audioRecorder.getRecordedFilePath()!!)
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
                            params.bottomMargin = -motionEvent.y.toInt() / 4
                            if (params.bottomMargin in 2..11) binding.imLockBar.layoutParams = params
                            currentVoiceRecordingState = VoiceRecordState.TouchRecording
                        }
                    }
                    val alpha = 1f + motionEvent.x / 400f
                    if (motionEvent.x < 0) {
                        binding.record.slideLayout.animate().x(motionEvent.x).start()
                    } else {
                        binding.record.slideLayout.animate().x(0f).start()
                    }
                    binding.record.slideLayout.alpha = alpha
                    if (alpha <= 0) {
                        saveAudioMessage = false
                        val animRight = AnimationUtils.loadAnimation(context, R.anim.slide_to_right)
                        binding.record.recordLayout.startAnimation(animRight)
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
            if (it == null) {
                Log.w("ChatView", "Chat is null, closing fragment")
                navigator().closeDetail()
            } else {
                setupOpponentName(it.getChatName())
                setupMuteIcon(it.muteExpired)
            }
        }

        viewModel.opponentName.observe(viewLifecycleOwner) {
            setupOpponentName(it ?: "Saved messages")
        }

        viewModel.muteExpired.observe(viewLifecycleOwner) {
            if (it != null) setupMuteIcon(it)
        }

        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            // Update adapter
            messageAdapter?.submitList(messages)
        }

        viewModel.unreadCount.observe(viewLifecycleOwner) { unread ->
            if (!isAdded || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                return@observe
            }
            lifecycleScope.launch(Dispatchers.Main) {
                showUnreadBadge(unread)
                binding.downScroller.isVisible = unread > 0 && layoutManager != null && messageAdapter != null
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
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
            }
            true
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
        val textRandom = arrayListOf(
            "Привет",
            "Компания «Ростелеком» открыла новый сезон строительства оптических линий связи на Южном Урале. Первым объектом для подключения стал жилой дом Челябинска в ЖК «Ньютон» на Комсомольском проспекте, 141. После его сдачи жители 132 квартир смогут пользоваться интернетом на скорости до 1 Гбит/с.",
            "Да",
            "В торжественной презентации старта нового сезона стройки приняли участие хоккеисты"
        )
        val references = ArrayList<MessageReferenceDto>()
        references.add(MessageReferenceDto("$a 1 ${System.currentTimeMillis()}", isGeo = true, latitude = 56.98, longitude = 67.09, size = 0L))
        lifecycleScope.launch {
            for (i in 0 until 10) {
                delay(1000)
                a++
                val chat = viewModel.loadChat(getParams().id)!!
                val m = MessageDto(
                    "$a $opponentJid ${System.currentTimeMillis()}",
                    false,
                    owner,
                    opponentJid,
                    "$a ${textRandom.random()}",
                    MessageSendingState.Sent,
                    System.currentTimeMillis(),
                    0,
                    MessageDisplayType.Text,
                    false,
                    false,
                    null,
                    isUnread = true,
                    isGroup = chat.isGroup, // Set isGroup based on chat
                    kind = null,
                    isSelected = false,
                    references = references,
                    isChecked = false
                )
                viewModel.insertMessage(getParams().id, m)
            }
        }
    }

    private fun onGotGalleryPermissionResult(grantResults: Map<String, Boolean>) {
        if (grantResults.entries.all { it.value }) showAttachBottomSheet()
        else askUserForOpeningAppSettings()
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
                if (id != null) viewModel.deleteMessage(id, forAll) else viewModel.deleteMessages(forAll)
                enableSelectionMode(false)
            }
        }
    }

    private fun showUnreadBadge(count: Int) {
        if (count > 0) {
            handler.removeCallbacks(unreadShower)
            handler.postDelayed(unreadShower, 150)
        } else {
            handler.removeCallbacks(unreadShower)
            unreadShower.invoke()
        }
    }

    private fun scrollDown() {
        if (messageAdapter != null && messageAdapter!!.itemCount > 0) {
            binding.messageList.post {
                layoutManager?.scrollToPosition(messageAdapter!!.itemCount - 1)

            }
            binding.tvNewReceivedCount.isVisible = false
            binding.tvNewReceivedCount.text = ""
            messageAdapter?.setFirstUnreadMessageId(null)
            viewModel.markAllAsRead()
        }
    }

    private fun startAudioRecord() {
        handler.postDelayed(timer, 500)
        handler.postDelayed(record, 500)
        saveAudioMessage = true
        enableStandardPanelButtons(false)
    }

    private fun enableStandardPanelButtons(enable: Boolean) {
        binding.buttonEmoticon.isEnabled = true
        binding.buttonAttach.isEnabled = true
        binding.btnRecord.isEnabled = true
        binding.buttonSendMessage.isEnabled = binding.chatInput.text.toString().trim().isNotEmpty() || replyingMessage != null
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
            "a ${System.currentTimeMillis()}",
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
                true,
                true,
                null,
                isSelected = false,
                isUnread = false,
                isGroup = viewModel.loadChat(getParams().id)!!.isGroup,
                kind = null,
                references = list,
                isChecked = false
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

    private fun stopRecordingAndSend(save: Boolean) {
        if (save) {
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
        binding.tvPinOwner.text = if (messageDto.isOutgoing) messageDto.owner else binding.tvChatTitle.text.toString()
        binding.tvPinContent.text = messageDto.messageBody
        binding.pinPanel.setOnClickListener {
            val position = viewModel.getPositionMessage(viewModel.lastPositionPrimary(messageDto.primary))
            binding.messageList.scrollToPosition(position)
            lifecycleScope.launch {
                viewModel.selectMessage(messageDto.primary, true)
            }
            handler.postDelayed(cancelSelected, 1000)
        }
        binding.imPinClose.setOnClickListener {
            binding.pinPanel.isVisible = false
        }
    }
    override fun forwardMessage(messageDto: MessageDto) {
        val text = "${messageDto.owner}\n${messageDto.messageBody}"
        val chat = viewModel.loadChat(getParams().id)
        navigator().showForwardFragment(text, viewModel.getAccount(chat!!.owner)?.jid ?: "")
    }

    override fun replyMessage(messageDto: MessageDto) {
        binding.answer.isVisible = true
        binding.replyMessageTitle.text = if (messageDto.isOutgoing) messageDto.owner else binding.tvChatTitle.text.toString()
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
        lifecycleScope.launch {
            viewModel.selectMessage(primary, true)
        }
        val position = viewModel.getMessagePosition(primary)
        if (position != -1) {
            messageAdapter?.notifyItemChanged(position)
        }
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
            binding.interaction.interactionView.isVisible = true
            replySwipeCallback?.setSwipeEnabled(false)
            isSelectedMode = true
            Check.setSelectedMode(true)
            binding.buttonEmoticon.isEnabled = false
            binding.buttonAttach.isEnabled = false
            binding.btnRecord.isEnabled = false
            binding.chatInput.isEnabled = false
        } else {
            val color = baseViewModel.getPrimaryAccount()?.colorKey
            val c = ColorManager.convertColorNameToId(color ?: resources.getString(R.string.blue))
            binding.appbar.setBackgroundResource(c)
            binding.selectMessagesToolbar.toolbarSelectedMessages.isVisible = false
            binding.interaction.interactionView.isVisible = false
            binding.toolbar.isVisible = true
            val textMessage = binding.chatInput.text.toString().trim()
            if (textMessage.isNotEmpty()) {
                binding.btnRecord.isVisible = false
            }
            Check.setSelectedMode(false)
            viewModel.clearAllSelected()
            replySwipeCallback?.setSwipeEnabled(true)
            isSelectedMode = false
            binding.buttonEmoticon.isEnabled = true
            binding.buttonAttach.isEnabled = true
            binding.btnRecord.isEnabled = true
            binding.chatInput.isEnabled = true
        }
    }

    override fun checkItem(isChecked: Boolean, primary: String) {
        lifecycleScope.launch {

            viewModel.selectMessage(primary, isChecked)
            val position = viewModel.getMessagePosition(primary)
            if (position != -1) {
                messageAdapter?.notifyItemChanged(position)
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroyView() {
        super.onDestroyView()
        saveLastPosition()
        saveDraft()
        AccountManager.unregisterChatViewModel(getParams().id)
        messageSender?.unsubscribeSender()
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

    fun onBind(message: MessageDto?) {
        if (message != null && message.isUnread && !message.isOutgoing) {
            viewModel.setUnread(message.primary)
        }
    }

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
        return extensionIsImage(file.path)
    }

    fun extensionIsImage(path: String?): Boolean {
        if (path == null) return false
        else if (path.isEmpty()) return false
        else return VALID_IMAGE_EXTENSIONS.contains(path.substringAfterLast("."))
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
        if (!file.exists()) {
            return
        }
        try {
            val inputStream: InputStream = FileInputStream(file)
            val audioData = ByteArray(file.length().toInt())
            inputStream.read(audioData)
            inputStream.close()
            val waveform = createWaveformFromAudioData(audioData)
            view.updateVisualizer(waveform)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun setUpVoiceMessagePresenter(path: String) {

        val time = HttpFileUploadManager.getVoiceLength(path)
        binding.audioPresenter.tvDuration.text = String.format(
            Locale.getDefault(), "%02d:%02d",
            TimeUnit.SECONDS.toMinutes(time),
            time % 60
        )
        subscribeForRecordedAudioProgress()
        VoiceMessagePresenterManager.getInstance()
            .sendWaveDataIfSaved(path, binding.audioPresenter.playerVisualizer)
        binding.audioPresenter.playerVisualizer.updatePlayerPercent(0f, false)

        binding.audioPresenter.playerVisualizer.setOnTouchListener(object : PlayerVisualizerView.onProgressTouch() {
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

    private fun sendStoppedVoiceMessage(filePath: String?) {
        sendStoppedVoiceMessage(filePath, null)
    }

    private fun sendStoppedVoiceMessage(filePath: String?, forwardIDs: List<String?>?) {
        if (filePath != null) sendVoiceMessage(filePath)
    }

    private fun uploadVoiceFile(path: String) {
        // TODO: Implement server upload logic
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

    private enum class VoiceRecordState {
        NotRecording, InitiatedRecording, TouchRecording, NoTouchRecording, StoppedRecording
    }
}