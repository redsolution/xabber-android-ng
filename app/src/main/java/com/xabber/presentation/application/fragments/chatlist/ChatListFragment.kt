package com.xabber.presentation.application.fragments.chatlist

import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat.ThemeCompat
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.databinding.FragmentChatListBinding
import com.xabber.model.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.ApplicationActivity
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.bottomsheet.NotificationBottomSheet
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.utils.mask.MaskPrepare
import com.xabber.utils.setFragmentResultListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class ChatListFragment : BaseFragment(R.layout.fragment_chat_list), ChatListAdapter.ChatListener {
    private val binding by viewBinding(FragmentChatListBinding::bind)
    private val viewModel = ChatListViewModel()
    private var chatListAdapter: ChatListAdapter? = null
    private var chatListType = ChatListType.RECENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelable(
                    AppConstants.CHAT_LIST_TYPE_KEY,
                    ChatListType::class.java
                )
                    ?.let { chatListType = it

                   }
            } else {
                savedInstanceState.getParcelable<ChatListType>(AppConstants.CHAT_LIST_TYPE_KEY)
                    ?.let { chatListType = it
                     Log.d("sss", "savedInstanceState = $savedInstanceState ${chatListType}")

                    }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle()
        changeUiWithData()
        initToolbarActions()
        initRecyclerView()
        fillChat()
        subscribeOnViewModelData()
        initButton()
        addNotificationBottomSheetListener()
    }

    private fun setTitle() {
        val title =
            when (chatListType) {
                ChatListType.RECENT -> R.string.application_title
                ChatListType.UNREAD -> R.string.unread_chats
                ChatListType.ARCHIVE -> R.string.archived_chat
            }
        binding.tvChatTitle.setText(title)
    }

    private fun changeUiWithData() {
        loadAvatarWithMask()
    }

    private fun loadAvatarWithMask() {
        val maskedDrawable =
            MaskPrepare.getDrawableMask(resources, R.drawable.img, UiChanger.getMask().size32)
        binding.imAvatar.setImageDrawable(maskedDrawable)
    }

    private fun initToolbarActions() {
        binding.imAvatar.setOnClickListener {
            viewModel.addInitial()
            //  navigator().showAccount()
        }
        binding.chatToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.add -> {
                    lifecycleScope.launch {
                        for (i in 0..1000) {
                            delay(2000)
                            viewModel.addChat()
//                            val layoutManager =
//                                binding.chatList.layoutManager as LinearLayoutManager
//                            val position = layoutManager.findFirstVisibleItemPosition()
                         //   if (position == 0) {
                                binding.chatList.scrollToPosition(0)
                           // } else {
                                binding.chatList.computeVerticalScrollOffset()
//                            }
                            }
                        }

                    }
                    //  R.id.add -> navigator().showNewChat()

                else -> {}
            }; true
        }

        val popup = createToolbarPopupMenu()
        binding.tvChatTitle.setOnClickListener { popup.show() }
    }

    private fun createToolbarPopupMenu(): PopupMenu {
        val popup = PopupMenu(context, binding.tvChatTitle, Gravity.CENTER)
        popup.inflate(R.menu.popup_menu_title_toolbar_chatlist)
        popup.setOnMenuItemClickListener {
            chatListType =
                when (it.itemId) {
                    R.id.recent_chats -> ChatListType.RECENT
                    R.id.unread -> ChatListType.UNREAD
                    R.id.archive -> ChatListType.ARCHIVE
                    else -> ChatListType.RECENT
                }
            setTitle()
           viewModel.getChatList(chatListType)
            true
        }
        return popup
    }

    private fun showEmptyListMode(isEmpty: Boolean) {
        binding.emptyLogo.isVisible = isEmpty
        binding.emptyText.isVisible = isEmpty
        binding.emptyButton.isVisible = chatListType == ChatListType.RECENT && isEmpty
        if (isEmpty) {
            val textResId =
                when (chatListType) {
                    ChatListType.RECENT -> R.string.chat_list_is_empty_text
                    ChatListType.UNREAD -> R.string.unread_list_is_empty_text
                    ChatListType.ARCHIVE -> R.string.archived_list_is_empty_text
                }
            binding.emptyText.setText(textResId)
        }
    }

    private fun subscribeOnViewModelData() {
        viewModel.chatList.observe(viewLifecycleOwner) {
            Log.d("sss", "observer $it")
            val a = ArrayList<ChatListDto>()
            a.addAll(it)
            a.sort()
            chatListAdapter?.submitList(a)
            showEmptyListMode(it.isEmpty() || it == null)
        }
//        applicationViewModel.showUnread.observe(viewLifecycleOwner) {
//            binding.cvMarkAllMessagesUnread.isVisible = it
//            if (it) {
//                binding.tvChatTitle.text = "Unread"
//                var count = 0
//                val a = viewModel.chat.value
//                for (i in 0 until a!!.size) {
//                    if (a[i].unreadString!!.isNotEmpty()) count += a[i].unreadString!!.toInt()
//                }
//                applicationViewModel.setUnreadCount(count)
//                val unreadList =
//                    viewModel.chat.value!!.filter { s -> s.unreadString!!.isNotEmpty() }
//                chatAdapter.submitList(unreadList)
//            } else {
//                chatAdapter.submitList(viewModel.chat.value)
//                binding.tvChatTitle.text = "Xabber"
//            }
//        }
    }

    private fun initRecyclerView() {
        chatListAdapter = ChatListAdapter(this)
        binding.chatList.adapter = chatListAdapter
        addSwipeOption()
    }

    private fun addSwipeOption() {
        if (chatListAdapter != null) {
            val swiper = SwipeToArchiveCallback(chatListAdapter!!)
            val itemTouch = ItemTouchHelper(swiper)
            itemTouch.attachToRecyclerView(binding.chatList)
        }
    }

    private fun fillChat() {
        Log.d("sss", "fillChat")
      viewModel.getChatList(chatListType)
    }

    private fun initButton() {
        binding.emptyButton.setOnClickListener { navigator().showContacts() }
        binding.cvMarkAllMessagesUnread.setOnClickListener {
            //  applicationViewModel.setUnreadCount(0)
            Toast.makeText(context, "You have no unread messages", Toast.LENGTH_SHORT).show()
        }
    }

    private fun movieChatToArchive(id: String) {
        viewModel.movieChatToArchive(id)
    }

    override fun onClickItem(chatListDto: ChatListDto) {
        navigator().showChat(ChatParams(chatListDto))
    }

    override fun pinChat(id: String) {
        viewModel.pinChat(id)
        binding.chatList.smoothScrollToPosition(0)

    }

    override fun swipeItem(id: Int) {
        //   chatListAdapter?.getIte
    }

    override fun unPinChat(id: String) {
        viewModel.unPinChat(id)
    }

    override fun deleteChat(id: String) {
        viewModel.deleteChat(id)
    }

    override fun turnOfNotifications(id: String) {
        val dialog = NotificationBottomSheet()
        dialog.show(childFragmentManager, "T")
        navigator().showBottomSheetDialog(dialog)
        setFragmentResultListener("requestKey") { _, bundle ->
            val resultMuteExpired = bundle.getLong("bundleKey")
            viewModel.setMute(id, resultMuteExpired)
        }
    }

    override fun enableNotifications(id: String) {
        viewModel.setMute(id, 0)
    }

    override fun openSpecialNotificationsFragment() {
        navigator().showSpecialNotificationSettings()
    }

    private fun showSnackbar(view: View) {
        var snackbar: Snackbar? = null
        snackbar?.dismiss()
        val archived = false
        snackbar = view.let {
            Snackbar.make(
                it,
                if (!archived) R.string.snackbar_title_to_archive else R.string.snackbar_title_pulled_from_archive,
                Snackbar.LENGTH_LONG
            )
        }
        snackbar.setAction(
            R.string.snackbar_button_cancel
        ) {
        }
        snackbar.setActionTextColor(Color.YELLOW)
        snackbar.show()
    }

    private fun addNotificationBottomSheetListener() {
    }

    override fun onDestroy() {
        super.onDestroy()
        chatListAdapter = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(
            AppConstants.CHAT_LIST_TYPE_KEY,
            chatListType
        )
    }

}
