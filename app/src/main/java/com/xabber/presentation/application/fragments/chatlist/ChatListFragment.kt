package com.xabber.presentation.application.fragments.chatlist

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.data.dto.ChatListDto
import com.xabber.data.dto.ContactDto
import com.xabber.data.xmpp.account.Account
import com.xabber.databinding.FragmentChatListBinding
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.activity.DisplayManager
import com.xabber.presentation.application.activity.MaskedDrawableBitmapShader
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.chat.ChatParams

class ChatListFragment : BaseFragment(R.layout.fragment_chat_list), ChatListAdapter.ChatListener {
    private val binding by viewBinding(FragmentChatListBinding::bind)
    private val viewModel = ChatListViewModel()
    private var chatAdapter: ChatListAdapter? = null

    companion object {
        fun newInstance(_jid: String) = ChatListFragment().apply {
            arguments = Bundle().apply {
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return super.onCreateView(inflater, container, savedInstanceState)


    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)
        var actionBarHeight = 0
        val tv = TypedValue()
        if (requireActivity().theme.resolveAttribute(
                android.R.attr.actionBarSize,
                tv,
                true
            )
        ) actionBarHeight =
            TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)

        Log.d("ooo", "hei ${binding.chatToolbar.height}")
//        val params = AppBarLayout.LayoutParams(
//            ActionBar.LayoutParams.MATCH_PARENT, actionBarHeight
//        )
//
//        params.setMargins(0, DisplayManager.getHeightStatusBar(), 0, 0)

// binding.chatToolbar.updateLayoutParams<AppBarLayout.LayoutParams> {
//            setMargins(0,DisplayManager.getHeightStatusBar(),0,0)
//        }

        binding.root.forceLayout()
Handler().post( { binding.appbar.setPadding(0, DisplayManager.getHeightStatusBar(), 0, 0) })
        initToolbarActions()
        fillChat()
        initButton()
        subscribeOnViewModelData()
        binding.appbar.setPadding(0, DisplayManager.getHeightStatusBar(),0, 0)
        //  binding.chatToolbar.layoutParams = params
        binding.root.requestLayout()
    }

    private fun initToolbarActions() {
        loadAvatarWithMask()
        binding.imAvatar.setOnClickListener {
            navigator().showAccount(
                Account(
                    "Natalia Barabanshikova",
                    "Natalia Barabanshikova",
                    "natalia.barabanshikova@redsolution.com",
                    R.color.blue_100,
                    R.drawable.img, 1
                )
            )
        }
//        binding.imPlus.setOnClickListener {
//            navigator().showNewChat()
//        }
        val popup = PopupMenu(context, binding.tvChatTitle, Gravity.RIGHT)
        popup.inflate(R.menu.popup_menu_title_toolbar_chatlist)
        popup.setOnMenuItemClickListener {
            val list = viewModel.chatList.value
            val sortedList = ArrayList<ChatListDto>()
            Log.d("sort", "$sortedList")
            when (it.itemId) {
                R.id.recent_chats -> {
                    for (i in 0 until list!!.size) {
                        if (!list[i].isArchived) sortedList.add(list[i])
                    }
                    sortedList.sort()
                    Log.d("sort", "$sortedList")
                    chatAdapter?.submitList(sortedList)
                }
                R.id.unread -> {
                    if (list != null) {
                        for (i in 0 until list.size) {
                            if (list[i].unreadString != null) sortedList.add(list[i])
                        }
                        chatAdapter?.submitList(sortedList)
                    }
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

    private fun loadAvatarWithMask() {
        val mPictureBitmap = BitmapFactory.decodeResource(resources, R.drawable.img)
        val mMaskBitmap =
            BitmapFactory.decodeResource(resources, UiChanger.getMask().size32).extractAlpha()
        val maskedDrawable = MaskedDrawableBitmapShader()
        maskedDrawable.setPictureBitmap(mPictureBitmap)
        maskedDrawable.setMaskBitmap(mMaskBitmap)
        binding.imAvatar.setImageDrawable(maskedDrawable)
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
        chatAdapter = ChatListAdapter(this)
        binding.chatList.adapter = chatAdapter
        viewModel.chatList.observe(viewLifecycleOwner) {
            binding.groupChatEmpty.isVisible = it.isEmpty() || (it == null)
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
                val icon = context.resources.getDrawable(R.drawable.ic_arcived_white)
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

    override fun onClickAvatar(contactDto: ContactDto) {
        navigator().showContactAccount(contactDto)
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

//    private fun showSnackbar(deletedItem: AbstractChat, previousState: ChatListState) {
//        snackbar?.dismiss()
//
//        val abstractChat =
//            ChatManager.getInstance().getChat(deletedItem.account, deletedItem.contactJid)
//                ?: return
//        val archived = abstractChat.isArchived
//
//        snackbar = view?.let {
//            Snackbar.make(
//                it,
//                if (!archived) R.string.chat_was_unarchived else R.string.chat_was_archived,
//                Snackbar.LENGTH_LONG
//            )
//        }
//
//        snackbar?.setAction(
//            R.string.undo
//        ) {
//            abstractChat.isArchived = !archived
//            onStateSelected(previousState)
//            updateRequest.onNext(null)
//        }
//
//        snackbar?.setActionTextColor(Color.YELLOW)
//        snackbar?.show()
//    }



}
