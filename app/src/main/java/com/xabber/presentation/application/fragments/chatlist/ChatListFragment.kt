package com.xabber.presentation.application.fragments.chatlist

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
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
import com.xabber.models.dto.ChatListDto
import com.xabber.presentation.AppConstants.CHAT_LIST_UNREAD_KEY
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_BUNDLE_KEY
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_DIALOG_TAG
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_KEY
import com.xabber.presentation.AppConstants.DELETING_CHAT_BUNDLE_KEY
import com.xabber.presentation.AppConstants.DELETING_CHAT_DIALOG_TAG
import com.xabber.presentation.AppConstants.DELETING_CHAT_KEY
import com.xabber.presentation.AppConstants.TURN_OFF_NOTIFICATIONS_BUNDLE_KEY
import com.xabber.presentation.AppConstants.TURN_OFF_NOTIFICATIONS_KEY
import com.xabber.presentation.application.AccountManager
import com.xabber.presentation.application.activity.ColorManager
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.dialogs.NotificationBottomSheet
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.custom.DividerItemDecoration
import com.xabber.presentation.custom.PullRefreshLayout
import com.xabber.utils.partSmoothScrollToPosition
import com.xabber.utils.setFragmentResultListener

/**
 * This fragment displays the chat list of enabled accounts and allows you to perform actions with chats.
 */
class ChatListFragment : BaseFragment(R.layout.fragment_chat_list), ChatListAdapter.ChatListener {
    private val binding by viewBinding(FragmentChatListBinding::bind)
    private val chatListViewModel: ChatListViewModel by activityViewModels()
    private var chatListAdapter: ChatListAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private var showUnreadOnly = false
    private val enableNotificationsCode = 0L
    private var toPin = false
    private var snackbar: Snackbar? = null
    private var currentId = ""
    private var isOverTriggerCrossed = false

    companion object {
        const val CURRENT_ID_KEY = "current id key"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getBoolean(
            CHAT_LIST_UNREAD_KEY
        )?.let {
            showUnreadOnly = it
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        chatListAdapter?.notifyDataSetChanged()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            chatListViewModel.initDataListener()
            chatListViewModel.initAccountDataListener()
        } else {
            currentId = savedInstanceState.getString(CURRENT_ID_KEY, "")
        }
        changeUiWithData()
        setTitle()
        initToolbarActions()
        initRecyclerView()
        subscribeToViewModelData()
        initEmptyButton()
        initMarkAllMessagesUnreadButton()
        setDialogListeners()
        initPullRefreshLayout()
    }

    private fun changeUiWithData() {
        loadAvatarWithMask()
    }

    private fun loadAvatarWithMask() {
        //    val avatarUri = AccountManager.getAvatar()
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(binding.imAvatar.context).load(R.drawable.backround_blue).error(R.color.blue_100)
            .apply(RequestOptions.bitmapTransform(multiTransformation))
            .into(binding.imAvatar)
    }

    private fun setTitle() {
        val title = if (showUnreadOnly) R.string.unread_chats else R.string.application_title
        binding.tvChatTitle.setText(title)
    }

    private fun initToolbarActions() {
        binding.imAvatar.setOnClickListener {
            navigator().showAccount(chatListViewModel.getPrimaryAccount()!!)
        }

        binding.chatToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.add -> {
                    if (chatListViewModel.chatIsEmpty()) chatListViewModel.addSomeChats()
                    else navigator().showNewChat()
                }
                else -> {}
            }; true
        }

        binding.chatToolbar.setOnClickListener { scrollUp() }
    }

    private fun scrollUp() {
        binding.chatList.partSmoothScrollToPosition(0)
    }

    private fun initRecyclerView() {
        chatListAdapter = ChatListAdapter(this)
        binding.chatList.adapter = chatListAdapter
        layoutManager = binding.chatList.layoutManager as LinearLayoutManager
        addItemDecoration()
        addSwipeOption()
        addScrollListener()
    }

    private fun addItemDecoration() {
        binding.chatList.addItemDecoration(
            DividerItemDecoration(
                binding.root.context,
                LinearLayoutManager.VERTICAL
            ).apply {
                setChatListOffsetMode(ChatListAvatarState.SHOW_AVATARS)
                skipDividerOnLastItem(true)
            })
    }

    private fun addSwipeOption() {
        if (chatListAdapter != null) {
            val swiper = SwipeToArchiveCallback(chatListAdapter!!)
            val itemTouch = ItemTouchHelper(swiper)
            itemTouch.attachToRecyclerView(binding.chatList)
        }
    }

    private fun addScrollListener() {
        if (layoutManager != null) {
            binding.chatList.setOnScrollChangeListener { _, _, _, _, _ ->
                if (layoutManager!!.findFirstVisibleItemPosition() <= 2) {
                    binding.chatList.scrollBarSize = 0
                } else {
                    binding.chatList.scrollBarSize = 10
                }
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

    private fun initEmptyButton() {
        binding.emptyButton.setOnClickListener { navigator().showContacts() }
    }

    private fun subscribeToViewModelData() {
        chatListViewModel.showUnreadOnly.observe(viewLifecycleOwner) {
            showUnreadOnly = it
            setTitle()
            binding.refreshLayout.isRefreshEnable = !showUnreadOnly
        }

        chatListViewModel.chats.observe(viewLifecycleOwner) {
            chatListAdapter?.submitList(it) {
                binding.btnMarkAllMessagesUnread.isVisible = showUnreadOnly && !it.isNullOrEmpty()
                showEmptyListMode(it.isEmpty() || it == null)
                if (toPin) {
                    scrollUp()
                    toPin = false
                }
            }
        }
    }

    private fun initMarkAllMessagesUnreadButton() {
        binding.btnMarkAllMessagesUnread.setOnClickListener {
            chatListViewModel.markAllChatsAsUnread()
            binding.btnMarkAllMessagesUnread.isVisible = false
        }
    }

    override fun onClickItem(chatListDto: ChatListDto) {
        navigator().showChat(
            ChatParams(
                chatListDto.id,
                chatListDto.owner,
                chatListDto.opponentJid,
                chatListDto.drawableId
            )
        )
    }

    private fun setDialogListeners() {
        setFragmentResultListener(TURN_OFF_NOTIFICATIONS_KEY) { _, bundle ->
            val mute =
                bundle.getLong(TURN_OFF_NOTIFICATIONS_BUNDLE_KEY) + System.currentTimeMillis()
            chatListViewModel.setMute(currentId, mute)
        }

        setFragmentResultListener(DELETING_CHAT_KEY) { _, bundle ->
            val result = bundle.getBoolean(DELETING_CHAT_BUNDLE_KEY)
            if (result) chatListViewModel.deleteChat(currentId)
        }

        setFragmentResultListener(CLEAR_HISTORY_KEY) { _, bundle ->
            val result = bundle.getBoolean(CLEAR_HISTORY_BUNDLE_KEY)
            if (result) chatListViewModel.clearHistoryChat(currentId)
        }
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
        currentId = id
        val dialog = DeletingChatDialog.newInstance(name)
        navigator().showDialogFragment(dialog, DELETING_CHAT_DIALOG_TAG)
    }

    override fun clearHistory(chatListDto: ChatListDto) {
        currentId = chatListDto.id
        val name = chatListDto.getChatName()
        val dialog = ChatHistoryClearDialog.newInstance(name)
        navigator().showDialogFragment(dialog, CLEAR_HISTORY_DIALOG_TAG)
    }

    override fun turnOfNotifications(id: String) {
        currentId = id
        val dialog = NotificationBottomSheet()
        navigator().showBottomSheetDialog(dialog)
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

    @SuppressLint("InflateParams")
    private fun initPullRefreshLayout() {
        val colorKey = AccountManager.getColorKey()
        val superLightColor = ColorManager.convertColorSuperLightNameToId(colorKey)
        val lightColor = ColorManager.convertColorLightNameToId(colorKey)
        val standardColor = ColorManager.convertColorMediumNameToId(colorKey)

        binding.refreshLayout.setOnRefreshListener(object :
            PullRefreshLayout.OnRefreshListener {
            override fun onRefresh() {
                binding.refreshLayout.postDelayed({
                    binding.refreshLayout.finishRefresh()
                    navigator().showArchive()
                }, 0)
            }

            override fun onRefreshPulStateChange(percent: Float, state: Int) {
                when (state) {
                    PullRefreshLayout.NOT_OVER_TRIGGER_POINT -> {
                        binding.refreshLayout.setRefreshViewText(
                            R.string.pull_to_show_archive
                        )
                        if (isOverTriggerCrossed) {
                            shortVibrate()
                            isOverTriggerCrossed = false
                        }
                        binding.refreshLayout.setHeaderBackground(R.color.grey_100)
                        binding.refreshLayout.setElementsColors(
                            R.color.grey_400,
                            R.color.grey_300,
                            false
                        )
                    }
                    PullRefreshLayout.OVER_TRIGGER_POINT -> {
                        if (!isOverTriggerCrossed) {
                            shortVibrate()
                        }
                        isOverTriggerCrossed = true
                        binding.refreshLayout.setRefreshViewText(
                            R.string.release_to_show_archive
                        )
                        binding.refreshLayout.setHeaderBackground(superLightColor)
                        binding.refreshLayout.setElementsColors(standardColor, lightColor, true)
                    }
                    PullRefreshLayout.START -> {
                        binding.refreshLayout.setRefreshViewText(R.string.open_archive)
                        isOverTriggerCrossed = false
                        binding.refreshLayout.finishRefresh()
                    }
                }
            }
        })
    }

    private fun shortVibrate() {
        view?.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    enum class ChatListAvatarState {
        NOT_SPECIFIED, SHOW_AVATARS, DO_NOT_SHOW_AVATARS
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(CURRENT_ID_KEY, currentId)
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
        layoutManager = null
        chatListAdapter = null
    }

}
