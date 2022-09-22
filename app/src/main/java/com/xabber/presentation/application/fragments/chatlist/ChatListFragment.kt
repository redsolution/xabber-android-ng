package com.xabber.presentation.application.fragments.chatlist

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.databinding.FragmentChatListBinding
import com.xabber.model.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.bottomsheet.NotificationBottomSheet
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.utils.showToast
import com.xabber.utils.mask.MaskPrepare

class ChatListFragment : BaseFragment(R.layout.fragment_chat_list), ChatListAdapter.ChatListener {
    private val binding by viewBinding(FragmentChatListBinding::bind)
    private val viewModel = ChatListViewModel()
    private var chatListAdapter: ChatListAdapter? = null
    private var chatListType = ChatListType.RECENT

    companion object {
        fun newInstance(_jid: String) = ChatListFragment().apply {
            arguments = Bundle().apply {
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            savedInstanceState.getParcelable<ChatListType>(AppConstants.CHAT_LIST_TYPE_KEY)
                ?.let { chatListType = it }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
          viewModel.getChatList()
        changeUiWithData()
        initRecyclerView()
        initToolbarActions()
        fillChat()
        initButton()
        subscribeOnViewModelData()
    }

    private fun changeUiWithData() {
        loadAvatarWithMask()
    }

    private fun initToolbarActions() {
        binding.chatToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.add -> showToast("This feature is not implemented")
            }; true
        }

        val popup = createToolbarPopupMenu()
        binding.tvChatTitle.setOnClickListener { popup.show() }
    }

    private fun loadAvatarWithMask() {
        val maskedDrawable =
            MaskPrepare.getDrawableMask(resources, R.drawable.img, UiChanger.getMask().size32)
        binding.imAvatar.setImageDrawable(maskedDrawable)
    }

    private fun createToolbarPopupMenu(): PopupMenu {
        val popup = PopupMenu(context, binding.tvChatTitle, Gravity.CENTER)
        popup.inflate(R.menu.popup_menu_title_toolbar_chatlist)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.recent_chats -> showRecentChatList()
                R.id.unread -> showUnreadChatList()
                R.id.archive -> showArchivedChatList()
            }
            true
        }
        return popup
    }

    private fun showRecentChatList() {
        val list = viewModel.chatList.value
        val sortedList = ArrayList<ChatListDto>()
        binding.tvChatTitle.setText(R.string.application_title)
        if (list != null) {
            for (i in list.indices) {
                if (!list[i].isArchived) sortedList.add(list[i])
            }
            if (sortedList.size > 0) {
                sortedList.sort()
                chatListAdapter?.submitList(sortedList)
            } else {
                showEmptyListMode(R.string.chat_list_is_empty_text)
            }
        }
        chatListType = ChatListType.RECENT
    }

    private fun showUnreadChatList() {
        val list = viewModel.chatList.value
        val sortedList = ArrayList<ChatListDto>()
        binding.tvChatTitle.setText(R.string.unread_chats)
        if (list != null) {
            for (i in list.indices) {
                if (list[i].unreadString != null) sortedList.add(list[i])
            }
            if (sortedList.size > 0) {
                sortedList.sort()
                chatListAdapter?.submitList(sortedList)
            } else {
                showEmptyListMode(R.string.unread_list_is_empty_text)
            }
        }
        chatListType = ChatListType.UNREAD
    }

    private fun showArchivedChatList() {
        val list = viewModel.chatList.value
        val sortedList = ArrayList<ChatListDto>()
        binding.tvChatTitle.setText(R.string.archived_chat)
        for (i in 0 until list!!.size) {
            if (list[i].isArchived) sortedList.add(list[i])
        }
        if (sortedList.size > 0) {
            sortedList.sort()
            chatListAdapter?.submitList(sortedList)
        } else {
            showEmptyListMode(R.string.archived_list_is_empty_text)
        }
      chatListType = ChatListType.ARCHIVE
    }

    private fun showEmptyListMode(textId: Int) {
        binding.emptyLogo.isVisible = true
        binding.emptyText.isVisible = true
        binding.emptyText.setText(textId)
    }

    private fun subscribeOnViewModelData() {
           viewModel.chatList.observe(viewLifecycleOwner) {
            binding.groupChatEmpty.isVisible = it.isEmpty() || (it == null)
            val sortedList = ArrayList<ChatListDto>()
            for (i in 0 until it!!.size) {
                if (!it[i].isArchived) sortedList.add(it[i])
            }
            sortedList.sort()
            chatListAdapter?.submitList(sortedList)
            binding.groupChatEmpty.isVisible = it.isEmpty()
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
        addRegisterAdapterDataObserver()
        addSwipeOption()

    }

    private fun addRegisterAdapterDataObserver() {
        if (chatListAdapter != null) chatListAdapter!!.registerAdapterDataObserver(object :
            RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.chatList.scrollToPosition(0)
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
            }
        })
    }

    private fun addSwipeOption() {
        val simpleCallback = object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )

                val context = recyclerView.context
                val icon = ContextCompat.getDrawable(context, R.drawable.ic_arcived_white)!!
                val itemView = viewHolder.itemView
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.action_with_chat_background, typedValue, true)
                val background = ContextCompat.getDrawable(context, R.color.grey_400)!!
                val backgroundOffset = 20
                val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
                val iconBottom = iconTop + icon.intrinsicHeight

                if (dX > 0) {
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = itemView.left + iconMargin + icon.intrinsicWidth
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    background.setBounds(
                        itemView.left,
                        itemView.top,
                        itemView.left + dX.toInt() + backgroundOffset,
                        itemView.bottom
                    )
                } else if (dX < 0) {
                    val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    background.setBounds(
                        itemView.right + dX.toInt() - backgroundOffset,
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                } else background.setBounds(0, 0, 0, 0)

                background.draw(c)
                icon.draw(c)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                //    chatAdapter.onSwipeChatItem(viewHolder as ChatAdapter.ChatViewHolder)
                //   movieChatToArchive(position)
            }
        }
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(binding.chatList)
    }

    private fun fillChat() {

        when (chatListType) {
            ChatListType.RECENT -> showRecentChatList()
            ChatListType.UNREAD -> showUnreadChatList()
            ChatListType.ARCHIVE -> showArchivedChatList()
        }
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
    }

    override fun unPinChat(id: String) {
        viewModel.unPinChat(id)
    }

    override fun deleteChat(id: String) {
        viewModel.deleteChat(id)
    }

    override fun turnOfNotifications(id: String) {
        val dialog = NotificationBottomSheet()
        navigator().showBottomSheetDialog(dialog)
        //  viewModel.turnOfNotifications(id)
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
