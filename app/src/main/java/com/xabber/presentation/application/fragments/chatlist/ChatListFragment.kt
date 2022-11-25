package com.xabber.presentation.application.fragments.chatlist

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.databinding.FragmentChatListBinding
import com.xabber.model.dto.ChatListDto
import com.xabber.presentation.AppConstants.CHAT_LIST_UNREAD_KEY
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_BUNDLE_KEY
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_DIALOG_TAG
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_KEY
import com.xabber.presentation.AppConstants.DELETING_CHAT_BUNDLE_KEY
import com.xabber.presentation.AppConstants.DELETING_CHAT_DIALOG_TAG
import com.xabber.presentation.AppConstants.DELETING_CHAT_KEY
import com.xabber.presentation.AppConstants.TURN_OFF_NOTIFICATIONS_BUNDLE_KEY
import com.xabber.presentation.AppConstants.TURN_OFF_NOTIFICATIONS_KEY
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.AccountManager
import com.xabber.presentation.application.bottomsheet.NotificationBottomSheet
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.utils.partSmoothScrollToPosition
import com.xabber.utils.setFragmentResultListener


class ChatListFragment : BaseFragment(R.layout.fragment_chat_list), ChatListAdapter.ChatListener {
    private val binding by viewBinding(FragmentChatListBinding::bind)
    private val chatListViewModel: ChatListViewModel by activityViewModels()
    private var chatListAdapter: ChatListAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private var showUnreadOnly = false
    private val enableNotificationsCode = 0L
    private var toPin = false
    private var snackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getBoolean(
            CHAT_LIST_UNREAD_KEY
        )?.let {
            showUnreadOnly = it
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            chatListViewModel.initDataListener()
          //  chatListViewModel.getChat()
        }
        changeUiWithData()
        setTitle()
        initToolbarActions()
        initRecyclerView()
        subscribeOnViewModelData()
        initEmptyButton()
        initMarkUnreadsButton()
        initButtonArchive()
    }

    private fun setTitle() {
        val title = if (showUnreadOnly) R.string.unread_chats else R.string.application_title
        binding.tvChatTitle.setText(title)
    }

    private fun changeUiWithData() {
        loadAvatarWithMask()
    }

    private fun loadAvatarWithMask() {
        val multiTransformation = MultiTransformation(CircleCrop())

        Glide.with(requireContext()).load(AccountManager.avatar)
            .apply(RequestOptions.bitmapTransform(multiTransformation))
            .into(binding.imAvatar)
    }

    private fun initToolbarActions() {
        binding.imAvatar.setOnClickListener {
            navigator().showAccount()
        }

        binding.chatToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.add -> {
                    chatListViewModel.addChat()
                   // navigator().showNewChat()
                }
                else -> {}
            }; true
        }

        binding.chatToolbar.setOnClickListener { binding.chatList.partSmoothScrollToPosition(0) }
    }

    private fun scrollUp() {
        binding.chatList.scrollToPosition(0)
    }

    private fun initRecyclerView() {
        chatListAdapter = ChatListAdapter(this)
        binding.chatList.adapter = chatListAdapter
        layoutManager = binding.chatList.layoutManager as LinearLayoutManager
        addEdgeEffectFactory()
        addSwipeOption()
        addScrollListener()

    }

    private fun addEdgeEffectFactory() {
        binding.chatList.apply {
            edgeEffectFactory = BounceEdgeEffectFactory(binding.root)
        }
    }

    private fun addSwipeOption() {
        if (chatListAdapter != null) {
            val swiper = SwipeToArchiveCallback(chatListAdapter!!)
            val itemTouch = ItemTouchHelper(swiper)
            itemTouch.attachToRecyclerView(binding.chatList)
        }
    }

    private fun addScrollListener() {
        binding.chatList.setOnScrollChangeListener { _, _, _, _, _ ->
            if (layoutManager?.findFirstVisibleItemPosition() == 1) {
                binding.chatList.scrollBarSize = 0
            } else {
                binding.chatList.scrollBarSize = 10
            }
        }
    }

    private fun showEmptyListMode(isEmpty: Boolean) {
        binding.linEmpty.isVisible = isEmpty
        binding.emptyButton.visibility =
            if (isEmpty && showUnreadOnly) View.INVISIBLE else View.VISIBLE
        if (isEmpty) {
            val textResId =
                if (showUnreadOnly) R.string.unread_list_is_empty_text else R.string.chat_list_is_empty_text
            binding.emptyText.setText(textResId)
        }
    }

    private fun subscribeOnViewModelData() {
        chatListViewModel.showUnreadOnly.observe(viewLifecycleOwner) {
            showUnreadOnly = it
            setTitle()
           // chatListViewModel.initDataListener()
            chatListViewModel.getChat()
        }

        chatListViewModel.chatList.observe(viewLifecycleOwner) {
            Log.d("uuu", "list = $it")
            val a = ArrayList<ChatListDto>()
            a.addAll(it)
            if (a != null) a.sort()

            chatListAdapter?.submitList(a) {
                if (showUnreadOnly && !it.isNullOrEmpty()) {
                    if (!binding.btnMarkAllMessagesUnread.isVisible) {
                        binding.btnMarkAllMessagesUnread.isVisible = true
                        val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.appearance)
                        binding.btnMarkAllMessagesUnread.startAnimation(anim)
                    }
                } else binding.btnMarkAllMessagesUnread.isVisible = false
                showEmptyListMode(it.isEmpty() || it == null)
                if (toPin) {
                    scrollUp()
                    toPin = false
                }
            }
          //  chatListAdapter?.notifyDataSetChanged()
        }

        chatListViewModel.unreadMessage.observe(viewLifecycleOwner) {
            navigator().showUnreadMessage(it)
        }
    }

    private fun initEmptyButton() {
        binding.emptyButton.setOnClickListener { navigator().showContacts() }
    }

    private fun initMarkUnreadsButton() {
        chatListViewModel.markAllChatsAsUnread()
    }

    private fun initButtonArchive() {
        binding.btnArchive.setOnClickListener { navigator().showArchive() }
    }

    override fun onClickItem(chatListDto: ChatListDto) {
        navigator().showChat(
            ChatParams(
                chatListDto.id,
                AccountManager.owner,
                chatListDto.opponentName,
                chatListDto.opponentJid,
                chatListDto.drawableId
            )
        )
    }

    override fun pinChat(id: String) {
        chatListViewModel.pinChat(id)
        toPin = true
    }

    override fun unPinChat(id: String) {
        chatListViewModel.unPinChat(id)
    }

    override fun swipeItem(id: String) {
        chatListViewModel.movieChatToArchive(id, true)
        showSnackbar(id)
    }

    override fun deleteChat(name: String, id: String) {
        val dialog = DeletingChatDialog.newInstance(name)
        navigator().showDialogFragment(dialog, DELETING_CHAT_DIALOG_TAG)
        setFragmentResultListener(DELETING_CHAT_KEY) { _, bundle ->
            val result = bundle.getBoolean(DELETING_CHAT_BUNDLE_KEY)
            if (result) chatListViewModel.deleteChat(id)
        }
    }

    override fun clearHistory(id: String, name: String, opponent: String) {
        val dialog = ChatHistoryClearDialog.newInstance(name)
        navigator().showDialogFragment(dialog, CLEAR_HISTORY_DIALOG_TAG)
        setFragmentResultListener(CLEAR_HISTORY_KEY) { _, bundle ->
            val result = bundle.getBoolean(CLEAR_HISTORY_BUNDLE_KEY)
            if (result) chatListViewModel.clearHistoryChat(id, opponent)
        }
    }

    override fun turnOfNotifications(id: String) {
        val dialog = NotificationBottomSheet()
        navigator().showBottomSheetDialog(dialog)
        setFragmentResultListener(TURN_OFF_NOTIFICATIONS_KEY) { _, bundle ->
            val resultMuteExpired =
                bundle.getLong(TURN_OFF_NOTIFICATIONS_BUNDLE_KEY) + System.currentTimeMillis()
            chatListViewModel.setMute(id, resultMuteExpired)
        }
    }

    override fun enableNotifications(id: String) {
        chatListViewModel.setMute(id, enableNotificationsCode)
    }

    override fun openSpecialNotificationsFragment() {
        navigator().showSpecialNotificationSettings()
    }

    private fun showSnackbar(id: String) {
        snackbar?.dismiss()
        snackbar = Snackbar.make(
            binding.root,
            R.string.snackbar_title_to_archive,
            Snackbar.LENGTH_LONG
        )

        snackbar?.anchorView = binding.anchor
        snackbar?.setAction(
            R.string.snackbar_button_cancel
        ) {
            chatListViewModel.movieChatToArchive(id, false)
        }
        snackbar?.setActionTextColor(Color.YELLOW)
        snackbar?.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(
            CHAT_LIST_UNREAD_KEY,
            showUnreadOnly
        )
    }

    override fun onStop() {
        super.onStop()
        snackbar?.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        chatListAdapter = null
    }

}
