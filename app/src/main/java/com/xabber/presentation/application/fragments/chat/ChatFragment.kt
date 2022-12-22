package com.xabber.presentation.application.fragments.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
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
import com.xabber.model.dto.ChatListDto
import com.xabber.model.dto.MessageDto
import com.xabber.model.dto.MessageKind
import com.xabber.model.xmpp.messages.MessageDisplayType
import com.xabber.model.xmpp.messages.MessageSendingState
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
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.attach.AttachBottomSheet
import com.xabber.presentation.application.fragments.chat.audio.AudioRecorder
import com.xabber.presentation.application.fragments.chat.audio.VoiceManager
import com.xabber.presentation.application.fragments.chat.message.*
import com.xabber.presentation.application.fragments.contacts.ContactAccountParams
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
    private var replySwipeCallback: ReplySwipeCallback1? = null
    private var isSelectedMode = false

    private var currentVoiceRecordingState = VoiceRecordState.NotRecording
    private var recordSaveAllowed = false
    private var recordingPath: String? = null
    private var stopTypingTimer: Timer? = Timer()
    private var saveAudioMessage = true
    private var lockIsClosed = false
    var isVibrate = false

    var a = 0

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
    private val timer = Runnable {
        binding.record.recordLayout.isVisible = true
        binding.record.linChronometr.isVisible = true
        binding.record.slideLayout.isVisible = true
        binding.record.slideLayout.alpha = 1.0f
        binding.linRecordLock.isVisible = true
        beginTimer(true)
    }

    private val shake = Runnable {
        val shaker = AnimationUtils.loadAnimation(context, R.anim.shake)
        if (binding.imLock.animation == null) binding.imLock.startAnimation(shaker)
        if (binding.imLockBar.animation == null) binding.imLockBar.startAnimation(shaker)
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
        requireArguments().parcelable(AppConstants.CHAT_PARAMS)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            val messageText = savedInstanceState.getString(CHAT_MESSAGE_TEXT_KEY)
            binding.chatInput.setText(messageText)
            isSelectedMode = savedInstanceState.getBoolean("i")
            enableSelectionMode(isSelectedMode)
        } else {
            val drafted = viewModel.getDrafted(getParams().id)
            if (drafted != null) binding.chatInput.setText(drafted)
            setupInputButtons()
        }
        changeUiWithData()
        initToolbarActions()
        initRecyclerView()
        subscribeOnViewModelData()
        initEmojiPanel()
        initSelectMessageToolbarActions()
        initSelectedMessagePanel()
        viewModel.initChatDataListener(getParams().id)
        viewModel.initMessagesListener(getParams().opponentJid)
        viewModel.getChat(getParams().id)
        activity?.onBackPressedDispatcher?.addCallback(onBackPressedCallback)
        initStandardInputLayoutActions()
    }

    private fun changeUiWithData() {
        loadAvatarWithMask()
        val chat = viewModel.getChat(getParams().id)
        if (chat != null) {
            setTitle(chat)
            setupMuteIcon(chat.muteExpired)
        }
    }

    private fun loadAvatarWithMask() {
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(requireContext()).load(getParams().avatar)
            .apply(RequestOptions.bitmapTransform(multiTransformation))
            .into(binding.imAvatar)
    }

    private fun setTitle(chat: ChatListDto) {
        val opponentName =
            if (chat.customName.isNotEmpty()) chat.customName else if (chat.displayName.isNotEmpty()) chat.displayName else chat.opponentJid
        binding.tvChatTitle.text = opponentName
    }

    private fun setupMuteIcon(muteExpired: Long) {
        binding.imNotificationsIsDisable.isVisible = muteExpired - System.currentTimeMillis() > 0
        if ((muteExpired - System.currentTimeMillis()) > TimeMute.DAY1.time) {
            binding.imNotificationsIsDisable.setImageResource(R.drawable.ic_bell_off_light_grey)
        } else {
            binding.imNotificationsIsDisable.setImageResource(R.drawable.ic_bell_sleep_light_grey)
        }
    }

    private fun initToolbarActions() {
        if (!DisplayManager.isDualScreenMode()) {
            binding.messageToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
            binding.messageToolbar.setNavigationOnClickListener {
                navigator().closeDetail()
            }
        } else binding.messageToolbar.navigationIcon = null

        binding.imAvatar.setOnClickListener {
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
        binding.messageToolbar.menu.findItem(R.id.enable_notifications).isVisible =
            binding.imNotificationsIsDisable.isVisible
        binding.messageToolbar.menu.findItem(R.id.disable_notifications).isVisible =
            !binding.imNotificationsIsDisable.isVisible
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

    private fun sendIncomingMessages() {
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
                        "$a ${getParams().opponentJid}",
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
                    layoutManager!!.findLastVisibleItemPosition() + 2 >= (chatAdapter!!.itemCount - viewModel.unreadCount.value!!)
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

    private fun disableNotifications() {
        val dialog = NotificationBottomSheet()
        navigator().showBottomSheetDialog(dialog)
        setFragmentResultListener(AppConstants.TURN_OFF_NOTIFICATIONS_KEY) { _, bundle ->
            val resultMuteExpired =
                bundle.getLong(AppConstants.TURN_OFF_NOTIFICATIONS_BUNDLE_KEY) + System.currentTimeMillis()
            viewModel.setMute(getParams().id, resultMuteExpired)
            binding.messageToolbar.menu.findItem(R.id.enable_notifications).isVisible = true
            binding.messageToolbar.menu.findItem(R.id.disable_notifications).isVisible = false
        }
    }

    private fun enableNotifications() {
        viewModel.setMute(getParams().id, enableNotificationsCode)
        binding.messageToolbar.menu.findItem(R.id.enable_notifications).isVisible = false
        binding.messageToolbar.menu.findItem(R.id.disable_notifications).isVisible = true
    }

    private fun clearHistory() {
        val dialog = ChatHistoryClearDialog()
        navigator().showDialogFragment(dialog, AppConstants.CLEAR_HISTORY_DIALOG_TAG)
        setFragmentResultListener(AppConstants.CLEAR_HISTORY_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.CLEAR_HISTORY_BUNDLE_KEY)
            if (result) viewModel.clearHistory(
                getParams().id,
                getParams().owner,
                getParams().opponentJid
            )
        }
    }

    private fun deleteChat() {
        val dialog = DeletingChatDialog.newInstance(binding.tvChatTitle.text.toString())
        navigator().showDialogFragment(dialog, AppConstants.DELETING_CHAT_DIALOG_TAG)
        setFragmentResultListener(AppConstants.DELETING_CHAT_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.DELETING_CHAT_BUNDLE_KEY)
            if (result) {
                viewModel.deleteChat(getParams().id)
                navigator().closeDetail()
            }
        }
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

    private fun subscribeOnViewModelData() {
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

        viewModel.muteExpired.observe(viewLifecycleOwner) {
            setupMuteIcon(it)
        }
        viewModel.opponentName.observe(viewLifecycleOwner) {
            setupOpponentName(it)
        }
        viewModel.messages.observe(viewLifecycleOwner) {
            chatAdapter?.submitList(it) {

                val man = binding.messageList.layoutManager as LinearLayoutManager
                if (man.findLastVisibleItemPosition() >= chatAdapter!!.itemCount - 2 && !isSelectedMode) scrollDown()
                if (isNeedScrollDown) {
                    scrollDown()
                    isNeedScrollDown = false
                }
            }
        }
    }

    private fun setupOpponentName(opponentName: String) {
        binding.tvChatTitle.text = opponentName
    }

    private fun initEmojiPanel() {
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

    private fun initSelectMessageToolbarActions() {
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

    private fun initSelectedMessagePanel() {
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

    private fun copyTextMessage(text: String) {
        val clipBoard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("", text)
        clipBoard.setPrimaryClip(clipData)
        showToast(R.string.snack_bar_title_copy_text)
    }

    private fun copyTextMessage() {
        val clipBoard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = viewModel.getSelectedText()
        val clipData = ClipData.newPlainText("", text)
        clipBoard.setPrimaryClip(clipData)
        showToast(R.string.snack_bar_title_copy_text)
    }

    private fun delete(id: String) {
        val dialog = DeletingMessageDialog.newInstance(binding.tvChatTitle.text.toString())
        navigator().showDialogFragment(dialog, AppConstants.DELETING_MESSAGE_DIALOG_TAG)
        setFragmentResultListener(DELETING_MESSAGE_DIALOG_KEY) { _, bundle ->
            val result = bundle.getBoolean(DELETING_MESSAGE_BUNDLE_KEY)
            val forAll = bundle.getBoolean(DELETING_MESSAGE_FOR_ALL_BUNDLE_KEY)
            if (result) {
                viewModel.deleteMessage(id, forAll)
                enableSelectionMode(false)
            }
        }
    }

    private fun delete() {
        val dialog = DeletingMessageDialog.newInstance(binding.tvChatTitle.text.toString())
        navigator().showDialogFragment(dialog, AppConstants.DELETING_MESSAGE_DIALOG_TAG)
        setFragmentResultListener(DELETING_MESSAGE_DIALOG_KEY) { _, bundle ->
            val result = bundle.getBoolean(DELETING_MESSAGE_BUNDLE_KEY)
            val forAll = bundle.getBoolean(DELETING_MESSAGE_FOR_ALL_BUNDLE_KEY)
            if (result) {
                viewModel.deleteMessages(forAll)
                enableSelectionMode(false)
            }
        }
    }

    private fun initRecyclerView() {
        chatAdapter = ChatAdapter(this)
        binding.messageList.adapter = chatAdapter
        layoutManager = LinearLayoutManager(context)
        layoutManager?.stackFromEnd = true
        binding.messageList.layoutManager = layoutManager
        binding.messageList.addItemDecoration(MessageHeaderViewDecoration())
        addSwipeCallback()
        addScrollListener()
        fillChat()
        if (viewModel.lastPositionPrimary(getParams().id) != "") {
            Log.d(
                "iii",
                "viewModel.lastPositionPrimary(getParams().id) = ${
                    viewModel.lastPositionPrimary(getParams().id)
                }"
            )
            val position =
                viewModel.getPositionMessage(viewModel.lastPositionPrimary(getParams().id))
            Log.d("iii", "Position = $position")
            binding.messageList.scrollToPosition(position)
            viewModel.saveLastPosition(getParams().id, "")
        }
    }

    private fun addSwipeCallback() {
        replySwipeCallback = ReplySwipeCallback1(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.reply_circle
            )!!, context
        ) { position: Int ->
            val id = chatAdapter?.getPositionId(position)
            if (id != null) {
                replyMessage(viewModel.getMessage(id)!!)
            }
        }

        //    replySwipeCallback?.setSwipeEnabled(true)
        //  replySwipeCallback?.replySwipeCallback()
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

    // @ExperimentalBadgeUtils
    private fun addScrollListener() {
        val layoutManager = binding.messageList.layoutManager as LinearLayoutManager

        binding.messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {

                //  binding.tvTopDate.isVisible = true
                if (layoutManager.findLastVisibleItemPosition() >= chatAdapter!!.itemCount - 1) {
                    binding.fm.isVisible = false
                } else {
                    if (!binding.fm.isVisible)
                        binding.fm.isVisible = true
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

    private fun scrollDown() {
        if (chatAdapter != null) binding.messageList.scrollToPosition(chatAdapter?.itemCount!! - 1)
        binding.tvNewReceivedCount.isVisible = false
        binding.tvNewReceivedCount.text = ""
        viewModel.markAllMessageUnread(getParams().id)
    }

    private fun initStandardInputLayoutActions() {
        chatInputAddListener()
        initButtonAttach()
        initButtonSend()
        initButtonRecord()
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

    private fun setupInputButtons() {
        binding.btnRecord.isVisible = binding.chatInput.text.toString().isEmpty()
        binding.buttonAttach.isVisible = binding.chatInput.text.toString().isEmpty()
        binding.buttonSendMessage.isVisible = binding.chatInput.text.toString().isNotEmpty()
    }

    private fun initButtonAttach() {
        binding.buttonAttach.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.READ_MEDIA_IMAGES
                    ) ==
                    PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.READ_MEDIA_VIDEO
                    ) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    askUserForOpeningAppSettings()
                } else {
                    requestImagesAndVideoPermissionResult.launch(
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                        )
                    )
                }
            } else {
                requestGalleryPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }

    private fun initButtonSend() {
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
                var b = System.currentTimeMillis()
                viewModel.insertMessage(
                    getParams().id,
                    MessageDto(
                        "$b",
                        true,
                        "Иван Иванов",
                        "${getParams().opponentJid}",
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
                a++

                binding.answer.isVisible = false
                isNeedScrollDown = true
                scrollDown()
            }
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
                        //   binding.buttonAttach.isVisible = false
                        handler.post(stop)
                    } else {
                        if (currentVoiceRecordingState == VoiceRecordState.TouchRecording) {
                            sendVoiceMessage()
                            // binding.buttonAttach.isVisible = false
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
                        //   binding.buttonAttach.isVisible = true
                        Log.d("yyy", "record")
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
                            binding.spaceLock.animate().y(motionEvent.y).setDuration(0).start()
                            val params =
                                binding.imLockBar.layoutParams as ConstraintLayout.LayoutParams
                            params.bottomMargin = -motionEvent.y.toInt() / 4
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
            //  binding.buttonAttach.isVisible = false
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
            //   binding.buttonAttach.isVisible = false
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
        //   binding.buttonAttach.isVisible = true

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
        outState.putBoolean("i", isSelectedMode)
        val messageText = binding.chatInput.text.toString().trim()
        outState.putString(CHAT_MESSAGE_TEXT_KEY, messageText)
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
        replyMessage(
            MessageDto(
                "22222",
                true,
                "Ann",
                getParams().opponentJid,
                "Алексей присоединился к чату",
                MessageSendingState.Read,
                1654234345585,
                0,
                MessageDisplayType.System,
                false,
                false,
                null, false
            )
        )
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
        Log.d("yyy", "enable selection $enable")
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

    override fun onDestroy() {
        super.onDestroy()
        val man = binding.messageList.layoutManager as LinearLayoutManager
        val pos = man.findLastVisibleItemPosition()
        val savedPosition =
            if (chatAdapter?.itemCount!! > 0) chatAdapter?.getPositionId(pos) else null
        if (savedPosition != null) viewModel.saveLastPosition(getParams().id, savedPosition)

        saveDraft()
        chatAdapter?.submitList(null)
        chatAdapter = null
        onBackPressedCallback.remove()
        AudioRecorder.releaseRecorder()
    }
}




