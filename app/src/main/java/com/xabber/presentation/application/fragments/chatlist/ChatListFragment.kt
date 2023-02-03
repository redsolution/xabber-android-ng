package com.xabber.presentation.application.fragments.chatlist

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
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
import com.xabber.presentation.application.activity.ColorManager
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.bottomsheet.NotificationBottomSheet
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
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
    private var a = false
    private var b = false

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
            currentId = savedInstanceState.getString("currentId", "")
        }
        changeUiWithData()
        setTitle()
        initToolbarActions()
        initRecyclerView()
        subscribeOnViewModelData()
        initEmptyButton()
        initMarkAllMessagesUnreadButton()
        setDialogListeners()
        initSwipeRefreshLayout(binding.refreshLayout)
    }

    private fun changeUiWithData() {
        loadAvatarWithMask()
    }

    private fun loadAvatarWithMask() {
        val name = UiChanger.getAvatar()
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(binding.imAvatar.context).load(name).error(R.drawable.ic_avatar_placeholder)
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
        val color = chatListViewModel.getColor()
        val c = if (color != null) color else R.color.blue_500
        chatListAdapter = ChatListAdapter(this, c)
        binding.chatList.adapter = chatListAdapter
        layoutManager = binding.chatList.layoutManager as LinearLayoutManager
        addItemDecoration()
        addSwipeOption()
        addScrollListener()
    }

    private fun addItemDecoration() {
        binding.chatList.addItemDecoration(
            com.xabber.presentation.application.fragments.chat.DividerItemDecoration(
                binding.root.context,
                LinearLayoutManager.VERTICAL
            ).apply {
                setChatListOffsetMode(ChatListAvatarState.SHOW_AVATARS)
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

    private fun subscribeOnViewModelData() {
        chatListViewModel.showUnreadOnly.observe(viewLifecycleOwner) {
            showUnreadOnly = it
            setTitle()
            binding.refreshLayout.isRefreshEnable = !showUnreadOnly
        }

        chatListViewModel.chats.observe(viewLifecycleOwner) {
            chatListAdapter?.submitList(it) {
                Log.d("chatList", "${it.size}")
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
        val name =
            if (chatListDto.customNickname.isNotEmpty()) chatListDto.customNickname else if (chatListDto.opponentNickname.isNotEmpty()) chatListDto.opponentNickname else chatListDto.opponentJid
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("currentId", currentId)
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

    enum class ChatListAvatarState {
        NOT_SPECIFIED, SHOW_AVATARS, DO_NOT_SHOW_AVATARS
    }


    private fun initSwipeRefreshLayout(swipeRefreshLayout: PullRefreshLayout?) {
        val inflater = LayoutInflater.from(context)
        swipeRefreshLayout?.isLoadMoreEnable = false
        val view: View = inflater.inflate(R.layout.refresh_view, null)
        val textView = view.findViewById<View>(R.id.refresh_title) as TextView
        swipeRefreshLayout?.setFooterView(view)

        val colorName = chatListViewModel.getColorName()
        val lightColor = ColorManager.convertColorLightNameToId(colorName!!)
        val color = ColorManager.convertColorMediumNameToId(colorName)
        swipeRefreshLayout?.setOnRefreshListener(object :
            PullRefreshLayout.SHSOnRefreshListener {
            override fun onRefresh() {
                swipeRefreshLayout.postDelayed(Runnable {
                    swipeRefreshLayout.finishRefresh()
                    navigator().showArchive()
                }, 0)
            }

            override fun onLoading() {
                swipeRefreshLayout.postDelayed(Runnable {
                    swipeRefreshLayout.finishLoadmore()
                    Toast.makeText(context, "ON LOADING", Toast.LENGTH_SHORT).show()
                }, 1600)
            }

            override fun onRefreshPulStateChange(percent: Float, state: Int) {
                when (state) {
                    PullRefreshLayout.NOT_OVER_TRIGGER_POINT -> {
                        swipeRefreshLayout.setRefreshViewText(
                            "Тяните для показа архива"
                        )
                        if (b) {
                            shortVibrate()

                            b = false
                        }
                        a = false
                        swipeRefreshLayout.setHeaderBackground(lightColor)


                    }
                    PullRefreshLayout.OVER_TRIGGER_POINT -> {
                        if (!a) {

                            shortVibrate()
                            a = true
                        }
                        b = true
                        swipeRefreshLayout.setRefreshViewText(
                            "Отпустите для показа архива"
                        )
                        swipeRefreshLayout.setHeaderBackground(color)
                    }
                    PullRefreshLayout.START -> {
                        swipeRefreshLayout.setRefreshViewText("Открываем архив")
                        a = false
                        b = false
                        swipeRefreshLayout.finishRefresh()
                    }
                }
            }

            private fun shortVibrate() {
                view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }

            override fun onLoadMorePullStateChange(percent: Float, state: Int) {
                when (state) {
                    PullRefreshLayout.NOT_OVER_TRIGGER_POINT -> textView.text =
                        "NOT_OVER_TRIGGER_POINT"
                    PullRefreshLayout.OVER_TRIGGER_POINT -> {
                        textView.text = "OVER_TRIGGER_POINT"

                        val vibe: Vibrator =
                            activity?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        vibe.vibrate(500)
                    }
                    PullRefreshLayout.START -> textView.text = "START"
                }
            }
        })
    }
}



