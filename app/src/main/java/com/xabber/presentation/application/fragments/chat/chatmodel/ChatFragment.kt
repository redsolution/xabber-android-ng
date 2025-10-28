package com.xabber.presentation.application.fragments.chat.chatmodel

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
import com.xabber.account.AccountManager
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.databinding.FragmentChatBinding
import com.xabber.dto.ChatListDto
import com.xabber.dto.MessageDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.*
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chat.Check
import com.xabber.presentation.application.fragments.chat.FileManager
import com.xabber.presentation.application.fragments.chat.HttpFileUploadManager
import com.xabber.presentation.application.fragments.chat.MediaDetailsActivity
import com.xabber.presentation.application.fragments.chat.ReplySwipeCallback
import com.xabber.presentation.application.fragments.chat.StatusMaker
import com.xabber.presentation.application.fragments.chat.audio.AudioRecorder
import com.xabber.presentation.application.fragments.chat.audio.PublishAudioProgress
import com.xabber.presentation.application.fragments.chat.audio.VoiceMessagePresenterManager
import com.xabber.presentation.application.fragments.chat.chatmodel.ChatFragmentModel
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
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

@RequiresApi(Build.VERSION_CODES.O)
class ChatFragment : DetailBaseFragment(R.layout.fragment_chat),
    ReplySwipeCallback.SwipeAction,
    ChatFragmentModel.MenuItemListener,
    ChatFragmentModel.OnViewClickListener {

    private val binding by viewBinding(FragmentChatBinding::bind)
    private val viewModel: ChatFragmentModel by viewModel { parametersOf(getParams().id) }
    private var lockIsClosed = false
    private var isVibrate = false

    private var layoutManager: LinearLayoutManager? = null
    private var replySwipeCallback: ReplySwipeCallback? = null
    private var messageSender: MessageCommonSender? = null

    private val audioRecorder = AudioRecorder()
    private var currentVoiceRecordingState = VoiceRecordState.NotRecording
    private var recordSaveAllowed = false
    private var recordingPath: String? = null
    private var stopTypingTimer: Timer? = null
    private var saveAudioMessage = true
    private var audioProgressSubscription: Disposable? = null
    private var isPlaying = false
    private var isSelectedMode = false
    private var replyingMessage: MessageDto? = null
    private var editMessageId: String? = null

    private val handler = Handler(Looper.getMainLooper())

    // === Рантаймы ===
    private val timer = Runnable { prepareUiForRecording(); beginTimer(true); currentVoiceRecordingState = VoiceRecordState.TouchRecording }
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
        val bot = TranslateAnimation(0f, 0f, 0f, 40f).apply { duration = 200L }
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

    // === Разрешения ===
    private val requestAudioPermissionResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) askUserForOpeningAppSettings()
    }

    private val requestGalleryPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) showAttachBottomSheet() else askUserForOpeningAppSettings()
    }

    companion object {
        fun newInstance(params: ChatParams) = ChatFragment().apply {
            arguments = Bundle().apply { putParcelable(AppConstants.CHAT_PARAMS, params) }
        }
    }

    private fun getParams(): ChatParams = requireArguments().parcelable(AppConstants.CHAT_PARAMS)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val chat = viewModel.chat.value ?: return navigator().closeDetail()
        messageSender = MessageCommonSender(chat.owner)

        prepareUi(chat)
        initializeToolbarActions(chat)
        initializeRecyclerView()
        initializeInputLayout()
        initializeSelectionToolbar()
        initializeSelectedPanel()
        observeViewModel()
        setupBackPress()

        if (savedInstanceState != null) restoreState(savedInstanceState) else restoreDraft()

        viewModel.loadInitialData()
        viewModel.startSyncIfNeeded()

        binding.messageList.post { scrollDown() }
    }

    private fun prepareUi(chat: ChatListDto) {
        onOrientationChange()
        loadContactAvatar()
        setTitle(chat.getChatName())
        setStatus(chat.status, chat.entity)
        setupMuteIcon(chat.muteExpired)
    }

    private fun onOrientationChange() = updateToolbarNavigation()
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

    private fun setTitle(name: String) {
        binding.tvChatTitle.text = name
    }

    private fun setStatus(status: ResourceStatus, entity: RosterItemEntity) {
        val icon = StatusMaker.statusIcon(entity)
        binding.avatarStatus.isVisible = icon != null
    }

    private fun setupMuteIcon(muteExpired: Long) {
        val res = when {
            muteExpired <= System.currentTimeMillis() -> null
            muteExpired - System.currentTimeMillis() > TimeMute.DAY1.time -> R.drawable.ic_bell_off_light_grey_mini
            else -> R.drawable.ic_bell_sleep_light_grey_mini
        }
        val drawable: Drawable? = res?.let { ContextCompat.getDrawable(requireContext(), it) }
        binding.tvChatTitle.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
    }

    private fun initializeToolbarActions(chat: ChatListDto) {
        binding.avatar.setOnClickListener {
            val contactId = viewModel.getContactId(chat.id) ?: return@setOnClickListener
            val params = ContactAccountParams(contactId, getParams().avatar)
            if (DisplayManager.getWidthDp() > 600 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                ContactAccountFragment.newInstance(params).show(childFragmentManager, AppConstants.CHAT_LIST_TO_FORWARD_DIALOG_TAG)
            } else if (DisplayManager.getWidthDp() > 800 && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                navigator().launchDetail(ContactAccountFragment.newInstance(params))
            } else {
                navigator().showContactAccount(params)
            }
        }

        binding.menu.setOnClickListener {
            val popup = PopupMenu(it.context, it)
            popup.menuInflater.inflate(R.menu.menu_toolbar_chat, popup.menu)
            val muteExpired = chat.muteExpired - System.currentTimeMillis()
            popup.menu.findItem(R.id.enable_notifications).isVisible = muteExpired > 0
            popup.menu.findItem(R.id.disable_notifications).isVisible = muteExpired <= 0
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.call_out -> sendIncomingMessages(chat.owner, chat.opponentJid)
                    R.id.disable_notifications -> disableNotifications()
                    R.id.enable_notifications -> viewModel.setMute(0L)
                    R.id.clear_message_history -> clearHistory(chat)
                    R.id.delete_chat -> deleteChat(chat)
                }
                true
            }
            popup.show()
        }
    }

    private fun initializeRecyclerView() {
        layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
            reverseLayout = false
        }
        binding.messageList.layoutManager = layoutManager
        binding.messageList.itemAnimator = null
        addSwipeCallback()
        addScrollListener()
    }

    private fun addSwipeCallback() {
        replySwipeCallback = ReplySwipeCallback(requireContext()) { position ->
            val message = viewModel.getMessageAt(position) ?: return@ReplySwipeCallback
            replyingMessage = message
            handler.postDelayed({ replyMessage(message) }, 200)
        }
        ItemTouchHelper(replySwipeCallback!!).attachToRecyclerView(binding.messageList)
        binding.messageList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                replySwipeCallback?.onDraw(c)
            }
        })
    }

    private fun addScrollListener() {
        binding.messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val firstVisible = layoutManager!!.findFirstVisibleItemPosition()
                if (firstVisible <= 2 && !viewModel.isLoadingHistory.value!!) {
                    viewModel.loadOlderMessages()
                }

                val lastVisible = layoutManager!!.findLastVisibleItemPosition()
                val itemCount = recyclerView.adapter?.itemCount ?: 0
                binding.downScroller.isVisible = lastVisible < itemCount - 1 && viewModel.unreadCount.value!! > 0
            }
        })

        binding.btnDownward.setOnClickListener {
            if (viewModel.unreadCount.value == 0) {
                scrollDown()
            } else {
                scrollToFirstUnread()
            }
        }
    }

    private fun scrollToFirstUnread() {
        val position = viewModel.getFirstUnreadPosition() ?: return
        layoutManager?.scrollToPositionWithOffset(position, 200)
        binding.tvNewReceivedCount.text = viewModel.unreadCount.value.toString()
        binding.tvNewReceivedCount.isVisible = true
    }

    private fun scrollDown() {
        binding.messageList.post {
            layoutManager?.scrollToPosition(viewModel.getMessageCount() - 1)
            binding.tvNewReceivedCount.isVisible = false
        }
    }

    private fun initializeInputLayout() {
        setupTextInput()
        setupEmojiButton()
        setupAttachButton()
        setupSendButton()
        setupRecordButton()
    }

    private fun setupTextInput() {
        binding.chatInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateInputButtons()
                binding.buttonSendMessage.isEnabled = s.toString().trim().isNotEmpty() || replyingMessage != null
            }
        })
    }

    private fun updateInputButtons() {
        val hasText = binding.chatInput.text.toString().trim().isNotEmpty()
        binding.btnRecord.isVisible = !hasText
        binding.buttonAttach.isVisible = !hasText
        binding.buttonSendMessage.isVisible = hasText || replyingMessage != null
    }

    private fun setupEmojiButton() {
        AXEmojiManager.install(requireContext(), AXGoogleEmojiProvider(requireContext()))
        val emojiView = AXSingleEmojiView(requireContext()).apply { editText = binding.chatInput }
        binding.emojiPopupLayout.initPopupView(emojiView)
    }

    private fun setupAttachButton() {
        binding.buttonAttach.setOnClickListener {
            requestGalleryPermissionLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun setupSendButton() {
        binding.buttonSendMessage.setOnClickListener {
            val text = binding.chatInput.text.toString().trim()
            if (text.isEmpty() && replyingMessage == null) return@setOnClickListener

            if (editMessageId != null) {
                viewModel.editMessage(editMessageId!!, text)
                clearInput()
                editMessageId = null
            } else {
                val chat = viewModel.chat.value!!
                val forwarded = replyingMessage?.primary?.let { listOf(it) } ?: emptyList()
                lifecycleScope.launch {
                    messageSender?.sendSimpleMessage(
                        body = text,
                        recipientJid = chat.opponentJid,
                        forwarded = forwarded,
                        conversationType = if (chat.isGroup) ConversationType.Group else ConversationType.Regular
                    )
                }
                clearInput()
                replyingMessage = null
                binding.answer.isVisible = false
                scrollDown()
            }
        }
    }

    private fun clearInput() {
        binding.chatInput.text?.clear()
        updateInputButtons()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecordButton() {
        binding.btnRecord.setOnTouchListener { _, event ->
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    if (isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
                        startAudioRecord()
                    } else {
                        requestAudioPermissionResult.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                MotionEvent.ACTION_UP -> handleRecordUp(event)
                MotionEvent.ACTION_MOVE -> handleRecordMove(event)
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

        binding.record.tvCancelRecording.setOnClickListener { clearVoiceMessage() }
        binding.audioPresenter.btnDeleteAudioMessage.setOnClickListener { clearVoiceMessage() }
        binding.audioPresenter.btnSendAudioMessage.setOnClickListener {
            sendVoiceMessage(audioRecorder.getRecordedFilePath()!!)
            clearVoiceMessage()
        }
        binding.btnRecordExpanded.setOnClickListener {
            if (isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
                audioRecorder.stopRecord()
                sendVoiceMessage(audioRecorder.getRecordedFilePath()!!)
                clearVoiceMessage()
            }
        }
    }

    private fun startAudioRecord() {
        handler.postDelayed(timer, 500)
        handler.postDelayed(record, 500)
        saveAudioMessage = true
        enableStandardPanelButtons(false)
    }

    private fun handleRecordUp(event: MotionEvent) {
        when (currentVoiceRecordingState) {
            VoiceRecordState.InitiatedRecording, VoiceRecordState.NotRecording -> {
                handler.removeCallbacks(record); handler.removeCallbacks(timer)
                hideRecordPanel(); beginTimer(false); navigator().lockScreen(false)
                currentVoiceRecordingState = VoiceRecordState.NotRecording
            }
            VoiceRecordState.TouchRecording -> {
                val elapsed = SystemClock.elapsedRealtime() - binding.record.chrRecordingTimer.base
                if (elapsed / 1000 >= 1) {
                    audioRecorder.stopRecord()
                    sendVoiceMessage(audioRecorder.getRecordedFilePath()!!)
                }
                hideRecordPanel(); navigator().lockScreen(false)
            }
            VoiceRecordState.NoTouchRecording -> handler.post(stop)
            else -> {
                binding.record.chrRecordingTimer.stop()
                hideRecordPanel()
                if (saveAudioMessage) sendVoiceMessage(audioRecorder.getRecordedFilePath()!!)
                currentVoiceRecordingState = VoiceRecordState.NotRecording
            }
        }
    }

    private fun handleRecordMove(event: MotionEvent) {
        when {
            event.y < -55 -> {
                if (!lockIsClosed) { shortVibrate(); lockIsClosed = true }
                currentVoiceRecordingState = VoiceRecordState.NoTouchRecording
                handler.post(shake)
            }
            event.y < 0 -> {
                binding.spaceLock.animate().y(event.y).start()
                val params = binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams
                params.bottomMargin = (-event.y / 4).toInt().coerceIn(2, 11)
                binding.imLockBar.layoutParams = params
                currentVoiceRecordingState = VoiceRecordState.TouchRecording
            }
        }
        val alpha = 1f + event.x / 400f
        binding.record.slideLayout.animate().x(event.x.coerceAtMost(0f)).start()
        binding.record.slideLayout.alpha = alpha
        if (alpha <= 0) {
            saveAudioMessage = false
            hideRecordPanel()
            currentVoiceRecordingState = VoiceRecordState.NotRecording
        }
    }

    private fun hideRecordPanel() {
        binding.record.recordLayout.isVisible = false
        binding.linRecordLock.isVisible = false
        binding.btnRecordExpanded.hide()
        enabledInputPanelButtons(true)
        binding.record.cancelRecordLayout.isVisible = false
    }

    private fun prepareUiForRecording() {
        binding.downScroller.isVisible = false
        enabledInputPanelButtons(false)
        binding.record.recordLayout.isVisible = true
        binding.record.linChronometr.isVisible = true
        binding.record.slideLayout.isVisible = true
        binding.record.slideLayout.alpha = 1f
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

    private fun clearVoiceMessage() {
        isPlaying = false
        binding.audioPresenter.btnPlay.setImageResource(R.drawable.ic_play)
        binding.audioPresenter.recordingPresenterLayout.isVisible = false
        binding.frameStop.isVisible = false
        binding.btnRecordExpanded.hide()
        currentVoiceRecordingState = VoiceRecordState.NotRecording
        lockIsClosed = false
        enabledInputPanelButtons(true)
    }

    private fun sendVoiceMessage(path: String) {
        enableStandardPanelButtons(true)
        beginTimer(false)
        hideRecordPanel()
        scrollDown()
    }

    private fun enableStandardPanelButtons(enable: Boolean) {
        binding.buttonEmoticon.isEnabled = enable
        binding.buttonAttach.isEnabled = enable
        binding.btnRecord.isEnabled = enable
        binding.buttonSendMessage.isEnabled = binding.chatInput.text.toString().trim().isNotEmpty() || replyingMessage != null
    }

    private fun enabledInputPanelButtons(enabled: Boolean) {
        binding.buttonEmoticon.isEnabled = enabled
        binding.btnDownward.isEnabled = enabled
    }

    private fun initializeSelectionToolbar() {
        binding.selectMessagesToolbar.imCloseSelectedMode.setOnClickListener { viewModel.clearSelection() }
        binding.selectMessagesToolbar.toolbarSelectedMessages.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.edit_message -> { editSelected(); viewModel.clearSelection() }
                R.id.copy_message -> { copySelectedText(); viewModel.clearSelection() }
                R.id.delete_message -> deleteSelected()
            }
            true
        }
    }

    private fun initializeSelectedPanel() {
        binding.interaction.linReply.setOnClickListener {
            val msg = viewModel.getSelectedMessage() ?: return@setOnClickListener
            viewModel.clearSelection()
            replyMessage(msg)
        }
        binding.interaction.linForward.setOnClickListener {
            val text = viewModel.getForwardText()
            viewModel.clearSelection()
            lifecycleScope.launch {
                delay(300)
                navigator().showForwardFragment(text, viewModel.getAccountJid())
            }
        }
    }

    private fun observeViewModel() {
        viewModel.chat.observe(viewLifecycleOwner) { chat ->
            if (chat == null) navigator().closeDetail()
            else setupOpponentName(chat.getChatName())
        }

        viewModel.opponentName.observe(viewLifecycleOwner) { binding.tvChatTitle.text = it }

        viewModel.muteExpired.observe(viewLifecycleOwner) { setupMuteIcon(it) }

        viewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            if (count > 0) {
                handler.removeCallbacks(unreadShower)
                handler.postDelayed(unreadShower, 150)
            } else {
                handler.removeCallbacks(unreadShower)
                unreadShower.run()
            }
        }

        viewModel.selectedCount.observe(viewLifecycleOwner) { count ->
            if (count > 0) {
                binding.selectMessagesToolbar.tvMessagesCount.text = count.toString()
                binding.selectMessagesToolbar.toolbarSelectedMessages.menu.findItem(R.id.edit_message).isVisible = count == 1
                binding.interaction.linReply.isVisible = count == 1
            } else {
                enableSelectionMode(false)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { binding.progressBar.isVisible = it }
    }

    private val unreadShower = Runnable {
        val count = viewModel.unreadCount.value ?: 0
        binding.tvNewReceivedCount.isVisible = count > 0
        binding.tvNewReceivedCount.text = count.toString()
    }

    private fun setupOpponentName(name: String?) {
        binding.tvChatTitle.text = name ?: "Saved messages"
    }

    private fun enableSelectionMode(enable: Boolean) {
        isSelectedMode = enable
        Check.setSelectedMode(enable)
        binding.appbar.setBackgroundResource(if (enable) R.color.white else ColorManager.convertColorNameToId(viewModel.getAccountColor()))
        binding.toolbar.isVisible = !enable
        binding.selectMessagesToolbar.toolbarSelectedMessages.isVisible = enable
        binding.interaction.interactionView.isVisible = enable
        replySwipeCallback?.setSwipeEnabled(!enable)
        binding.chatInput.isEnabled = !enable
        binding.buttonEmoticon.isEnabled = !enable
        binding.buttonAttach.isEnabled = !enable
        binding.btnRecord.isEnabled = !enable
    }

    private fun restoreDraft() {
        val draft = viewModel.getDraft() ?: return
        binding.chatInput.setText(draft)
        updateInputButtons()
    }

    private fun restoreState(bundle: Bundle) {
        binding.chatInput.setText(bundle.getString(AppConstants.CHAT_MESSAGE_TEXT_KEY))
        isSelectedMode = bundle.getBoolean(AppConstants.CHAT_SELECTION_MODE_KEY)
        enableSelectionMode(isSelectedMode)
        val path = bundle.getString("VOICE_MESSAGE")
        if (path != null) {
            recordingPath = path
            currentVoiceRecordingState = VoiceRecordState.StoppedRecording
            binding.record.recordLayout.isVisible = false
            binding.audioPresenter.recordingPresenterLayout.isVisible = true
            setUpVoiceMessagePresenter(path)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(AppConstants.CHAT_SELECTION_MODE_KEY, isSelectedMode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.saveDraft(binding.chatInput.text.toString().trimEnd().ifEmpty { null })
        viewModel.saveLastPosition(getLastVisibleMessageId())
        AccountManager.unregisterChatViewModel(getParams().id)
        messageSender?.unsubscribeSender()
        onBackPressedCallback.remove()
    }

    private fun getLastVisibleMessageId(): String {
        val pos = layoutManager?.findLastVisibleItemPosition() ?: return ""
        return viewModel.getMessageAt(pos)?.primary.orEmpty()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                binding.emojiPopupLayout.isShowing -> binding.emojiPopupLayout.isVisible = false
                isSelectedMode -> viewModel.clearSelection()
                else -> navigator().closeDetail()
            }
        }
    }

    private fun setupBackPress() {
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    // === MenuItemListener ===
    override fun copyText(text: String) = copyToClipboard(text)
    override fun pinMessage(message: MessageDto) { /* TODO */ }
    override fun forwardMessage(message: MessageDto) {
        navigator().showForwardFragment("${message.owner}\n${message.messageBody}", viewModel.getAccountJid())
    }
    override fun replyMessage(message: MessageDto) {
        replyingMessage = message
        binding.answer.isVisible = true
        binding.replyMessageTitle.text = if (message.isOutgoing) message.owner else binding.tvChatTitle.text
        binding.replyMessageContent.text = message.messageBody
        binding.close.setOnClickListener {
            replyingMessage = null
            binding.answer.isVisible = false
        }
    }
    override fun editMessage(primary: String, text: String) {
        binding.chatInput.setText(text)
        binding.chatInput.setSelection(text.length)
        editMessageId = primary
    }
    override fun deleteMessage(primary: String) = deleteMessageWithDialog(primary)

    // === OnViewClickListener ===
    override suspend fun onLongClick(primary: String) {
        enableSelectionMode(true)
        viewModel.selectMessage(primary, true)
    }

    override suspend fun checkItem(isChecked: Boolean, primary: String) {
        viewModel.selectMessage(primary, isChecked)
    }

    override fun onImageOrVideoClick(startPosition: Int, messageId: String) {
        startActivity(Intent(requireContext(), MediaDetailsActivity::class.java).apply {
            putExtra(AppConstants.START_POSITION, startPosition)
            putExtra(AppConstants.MESSAGE_UID, messageId)
        })
    }

    override fun onLocationClick(latitude: Double, longitude: Double) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude")))
    }

    override fun onFullSwipe(position: Int) {
        handler.postDelayed({ replyMessage(viewModel.getMessageAt(position)!!) }, 1500)
    }

    // === UI Actions ===
    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("", text))
        showToast(R.string.snack_bar_title_copy_text)
    }

    private fun editSelected() {
        val text = viewModel.getSelectedText()
        val id = viewModel.getSelectedMessageId()
        binding.chatInput.setText(text)
        binding.chatInput.setSelection(text.length)
        editMessageId = id
    }

    private fun copySelectedText() = copyToClipboard(viewModel.getSelectedText())

    private fun deleteSelected() = deleteMessageWithDialog()

    private fun deleteMessageWithDialog(id: String? = null) {
        val dialog = DeletingMessageDialog.newInstance(binding.tvChatTitle.text.toString(), id)
        navigator().showDialogFragment(dialog, AppConstants.DELETING_MESSAGE_DIALOG_TAG)
        setFragmentResultListener(AppConstants.DELETING_MESSAGE_DIALOG_KEY) { _, bundle ->
            val delete = bundle.getBoolean(AppConstants.DELETING_MESSAGE_BUNDLE_KEY)
            val forAll = bundle.getBoolean(AppConstants.DELETING_MESSAGE_FOR_ALL_BUNDLE_KEY)
            if (delete) {
                if (id != null) viewModel.deleteMessage(id, forAll) else viewModel.deleteSelectedMessages(forAll)
                enableSelectionMode(false)
            }
        }
    }

    private fun disableNotifications() {
        NotificationBottomSheet.newInstance(getParams().id)
            .show(childFragmentManager, AppConstants.NOTIFICATION_BOTTOM_SHEET_TAG)
    }

    private fun clearHistory(chat: ChatListDto) {
        ChatHistoryClearDialog.newInstance(chat.getChatName(), chat.id)
            .show(childFragmentManager, AppConstants.DELETING_CHAT_DIALOG_TAG)
    }

    private fun deleteChat(chat: ChatListDto) {
        DeletingChatDialog.newInstance(chat.getChatName(), chat.id)
            .show(childFragmentManager, AppConstants.DELETING_CHAT_DIALOG_TAG)
    }

    private fun showAttachBottomSheet() {
        AttachmentBottomSheet.newInstance(getParams().id)
            .show(childFragmentManager, AppConstants.ATTACH_BOTTOM_SHEET_TAG)
    }

    private fun sendIncomingMessages(owner: String, opponentJid: String) {
        // Тестовые сообщения
    }

    private fun setUpVoiceMessagePresenter(path: String) {
        val time = HttpFileUploadManager.getVoiceLength(path)
        binding.audioPresenter.tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d", TimeUnit.SECONDS.toMinutes(time), time % 60)
        VoiceMessagePresenterManager.getInstance().sendWaveDataIfSaved(path, binding.audioPresenter.playerVisualizer)
        binding.audioPresenter.playerVisualizer.updatePlayerPercent(0f, false)

        val mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
        }

        binding.audioPresenter.btnPlay.setOnClickListener {
            if (isPlaying) {
                mediaPlayer.pause()
                binding.audioPresenter.btnPlay.setImageResource(R.drawable.ic_play)
                isPlaying = false
            } else {
                mediaPlayer.start()
                binding.audioPresenter.btnPlay.setImageResource(R.drawable.ic_pause)
                isPlaying = true
            }
        }
    }

    private enum class VoiceRecordState {
        NotRecording, InitiatedRecording, TouchRecording, NoTouchRecording, StoppedRecording
    }
}