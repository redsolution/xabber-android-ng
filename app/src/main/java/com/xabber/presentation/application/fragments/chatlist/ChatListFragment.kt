package com.xabber.presentation.application.fragments.chatlist

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.data.dto.ChatListDto
import com.xabber.databinding.FragmentChatBinding
import com.xabber.presentation.application.contract.navigator

import com.xabber.presentation.application.fragments.BaseFragment

class ChatListFragment : BaseFragment(R.layout.fragment_chat), ChatListAdapter.ChatListener {
    private val binding by viewBinding(FragmentChatBinding::bind)
    private val viewModel = ChatListViewModel()
    private var chatAdapter: ChatListAdapter? = null

    companion object {
        fun newInstance(_jid: String) = ChatListFragment().apply {
            arguments = Bundle().apply {

            }
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatAdapter = ChatListAdapter(this)
        initToolbarActions()
        fillChat()
        initButton()
        subscribeOnViewModelData()
    }


    private fun initToolbarActions() {
        binding.avatarContainer.setOnClickListener {
            navigator().showAccount()
        }

        binding.imPlus.setOnClickListener {
            navigator().showNewChat()
        }

        val popup = PopupMenu(context, binding.tvChatTitle, Gravity.RIGHT)
        popup.inflate(R.menu.context_menu_title_chat)
        popup.setOnMenuItemClickListener {
            val list = viewModel.chatList.value
            val sortedList = ArrayList<ChatListDto>()
            when (it.itemId) {
                R.id.recent_chats -> {
                    for (i in 0 until list!!.size) {
                        if (!list[i].isArchived) sortedList.add(list[i])
                    }
                    sortedList.sort()
                    chatAdapter?.submitList(sortedList)
                }
                R.id.unread -> {
                    for (i in 0 until list!!.size) {
                        if (list[i].unreadString!!.isNotEmpty()) sortedList.add(list[i])
                    }
                    chatAdapter?.submitList(sortedList)
                }
                R.id.archive -> {
                    for (i in 0 until list!!.size) {
                        if (list[i].isArchived) sortedList.add(list[i])
                    }
                    chatAdapter?.submitList(sortedList)
                }
            }
            true
        }
        binding.tvChatTitle.setOnClickListener { popup.show() }
    }

    private fun subscribeOnViewModelData() {
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

    private fun fillChat() {
        binding.chatList.adapter = chatAdapter

        Log.d("chatListSize", "ttt")
        viewModel.chatList.observe(viewLifecycleOwner) {
            binding.groupChatEmpty.isVisible = it.size == 0 || it == null
            Log.d("chatListSize", "${it.size}")
            val sortedList = ArrayList<ChatListDto>()
            for (i in 0 until it!!.size) {
                if (!it[i].isArchived) sortedList.add(it[i])
            }
            sortedList.sort()
            chatAdapter?.submitList(sortedList)
            binding.groupChatEmpty.isVisible = it.isEmpty()
        }
        viewModel.getChatList()
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
                val icon = context.resources.getDrawable(R.drawable.ic_arcived)
                val itemView = viewHolder.itemView
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.action_with_chat_background, typedValue, true)
                val background = ColorDrawable(resources.getColor(R.color.grey_400))

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

                val position = viewHolder.bindingAdapterPosition

                //   movieChatToArchive(position)
            }
        }

        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(binding.chatList)
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


    override fun onClickItem(name: String) {
        navigator().showMessage(name)
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

    override fun onClickAvatar(name: String) {
        navigator().showEditContact(name)
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
        chatAdapter = null
    }


}
