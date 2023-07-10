package com.xabber.presentation.application.fragments.chatlist

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.dialogs.NotificationBottomSheet
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.utils.custom.DividerItemDecoration
import com.xabber.utils.custom.SwipeToArchiveCallback
import com.xabber.utils.partSmoothScrollToPosition
import com.xabber.utils.setFragmentResultListener

abstract class ChatListBaseFragment(@LayoutRes contentLayoutId: Int) :
    BaseFragment(contentLayoutId),
    ChatListAdapter.ChatListener {
    protected val chatListViewModel: ChatListViewModel by viewModels()
    protected var chatListAdapter: ChatListAdapter? = null
    protected var layoutManager: LinearLayoutManager? = null
    private var toolbar: MaterialToolbar? = null
    private var chatList: RecyclerView? = null
    protected var toPin = false
    protected var currentId = ""
    private val enableNotificationsCode = 0L
    private var snackbar: Snackbar? = null

    companion object {
        const val CURRENT_ID_KEY = "current id key"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) currentId = savedInstanceState.getString(CURRENT_ID_KEY, "")
        initViews()
        setDialogListeners()
        toolbar?.setOnClickListener {
            scrollUp()
        }
        initRecyclerView()
    }

    private fun initViews() {
        toolbar = view?.findViewById(R.id.chat_toolbar)
        chatList = view?.findViewById(R.id.chat_list)
    }

    protected fun scrollUp() {
        chatList?.partSmoothScrollToPosition(0)
    }

    private fun setDialogListeners() {
        setFragmentResultListener(AppConstants.TURN_OFF_NOTIFICATIONS_KEY) { _, bundle ->
            val mute =
                bundle.getLong(AppConstants.TURN_OFF_NOTIFICATIONS_BUNDLE_KEY) + System.currentTimeMillis()
            chatListViewModel.setMute(currentId, mute)
        }

        setFragmentResultListener(AppConstants.DELETING_CHAT_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.DELETING_CHAT_BUNDLE_KEY)
            if (result) chatListViewModel.deleteChat(currentId)
        }

        setFragmentResultListener(AppConstants.CLEAR_HISTORY_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.CLEAR_HISTORY_BUNDLE_KEY)
         //   if (result) chatListViewModel.clearHistoryChat(currentId)
        }
    }

    private fun initRecyclerView() {
        chatListAdapter = ChatListAdapter(this)
        chatList?.adapter = chatListAdapter
        layoutManager = chatList?.layoutManager as LinearLayoutManager
        addItemDecoration()
        addSwipeOption()
        addScrollListener()
    }

    private fun addSwipeOption() {
        if (chatListAdapter != null) {
            val swiper = SwipeToArchiveCallback(chatListAdapter!!)
            val itemTouch = ItemTouchHelper(swiper)
            itemTouch.attachToRecyclerView(chatList)
        }
    }

    private fun addScrollListener() {
        if (layoutManager != null) {
            chatList?.setOnScrollChangeListener { _, _, _, _, _ ->
                if (layoutManager!!.findFirstVisibleItemPosition() <= 2) {
                    chatList?.scrollBarSize = 0
                } else {
                    chatList?.scrollBarSize = 10
                }
            }
        }
    }

    private fun addItemDecoration() {
        chatList?.addItemDecoration(
            DividerItemDecoration(
                requireContext(),
                LinearLayoutManager.VERTICAL
            ).apply {
                setChatListOffsetMode(ChatListAvatarState.SHOW_AVATARS)
                skipDividerOnLastItem(true)
            })
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        chatListAdapter?.notifyDataSetChanged()
    }

    override fun pinChat(chatId: String) {
        chatListViewModel.pinChat(chatId)
        toPin = true
    }

    override fun unPinChat(chatId: String, position: Int) {
        chatListViewModel.unPinChat(chatId)
    }

    override fun swipeItem(chatId: String) {
        chatListViewModel.setArchived(chatId)
        showSnackbar(chatId)
    }

    override fun deleteChat(chatName: String, chatId: String) {
        currentId = chatId
        val dialog = DeletingChatDialog.newInstance(chatName, chatId)
        navigator().showDialogFragment(dialog, AppConstants.DELETING_CHAT_DIALOG_TAG)
    }

    override fun clearHistory(chatName: String, chatId: String) {
        val dialog = ChatHistoryClearDialog.newInstance(chatName, chatId)
        navigator().showDialogFragment(dialog, AppConstants.CLEAR_HISTORY_DIALOG_TAG)
    }

    override fun turnOfNotifications(chatId: String) {
        currentId = chatId
        val dialog = NotificationBottomSheet()
        navigator().showBottomSheetDialog(dialog)
    }

    override fun enableNotifications(chatId: String) {
        chatListViewModel.setMute(chatId, enableNotificationsCode)
    }

    override fun onClickItem(chatListDto: ChatListDto) {
        navigator().showChat(
            ChatParams(
                chatListDto.id,
                chatListDto.drawableId
            )
        )
    }

    private fun showSnackbar(id: String) {
//          snackbar?.dismiss()
//          snackbar = Snackbar.make(
//               binding.root,
//               R.string.snackbar_title_to_archive,
//               Snackbar.LENGTH_LONG
//          )
//
//          snackbar?.anchorView = binding.anchor
//          snackbar?.setAction(
//               R.string.snackbar_button_cancel
//          ) {
//               chatListViewModel.setArchived(id)
//          }
//          snackbar?.setActionTextColor(Color.YELLOW)
//          snackbar?.show()
    }


    override fun onStop() {
        super.onStop()
        snackbar?.dismiss()
    }


    enum class ChatListAvatarState {
        NOT_SPECIFIED, SHOW_AVATARS, DO_NOT_SHOW_AVATARS
    }

}