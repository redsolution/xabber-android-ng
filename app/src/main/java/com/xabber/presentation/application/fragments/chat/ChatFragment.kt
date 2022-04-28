package com.xabber.presentation.application.fragments.chat

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.data.dto.ChatDto
import com.xabber.databinding.FragmentChatBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.util.DateFormatter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class ChatFragment : Fragment(), ChatAdapter.ChatListener {
    private var binding: FragmentChatBinding? = null
    lateinit var jid: String
    private val viewModel = ChatViewModel()
    private var chatAdapter = ChatAdapter(this)

    companion object {
        fun newInstance(_jid: String) = ChatFragment().apply {
            arguments = Bundle().apply {

            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        when (resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> navigator().hideFragment(false)
                Configuration.ORIENTATION_LANDSCAPE -> navigator().hideFragment(true) }
        binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbarActions()
        fillChat()
        initButton()
    }

    private fun initToolbarActions() {
        binding?.avatarContainer?.setOnClickListener {
          navigator().showAccount()

        }
        binding?.imPlus?.setOnClickListener {
            navigator().showNewChat()
        }

        val popup = PopupMenu(context, binding?.tvChatTitle, Gravity.RIGHT)
        popup.inflate(R.menu.context_menu_title_chat)
        popup.setOnMenuItemClickListener {
            val list = viewModel.chat.value
            val sortedList = ArrayList<ChatDto>()
            when (it.itemId) {
                R.id.recent_chats -> {
                    for (i in 0 until list!!.size) {
                        if (!list[i].isArchived) sortedList.add(list[i])
                    }
                    sortedList.sort()
                    chatAdapter.submitList(sortedList)
                }
                R.id.unread -> {
                    for (i in 0 until list!!.size) {
                        if (list[i].unread > 0) sortedList.add(list[i])
                    }
                    chatAdapter.submitList(sortedList)
                }
                R.id.archive -> {
                    for (i in 0 until list!!.size) {
                        if (list[i].isArchived) sortedList.add(list[i])
                    }
                    chatAdapter.submitList(sortedList)
                }
            }
            true
        }
        binding?.tvChatTitle?.setOnClickListener { popup.show() }
    }


    private fun fillChat() {
        binding?.chatList?.adapter = chatAdapter
        viewModel.chat.observe(viewLifecycleOwner) {
            val sortedList = ArrayList<ChatDto>()
            for (i in 0 until it!!.size) {
                if (!it[i].isArchived) sortedList.add(it[i])
            }
            sortedList.sort()
            chatAdapter.submitList(sortedList)
            binding?.groupChatEmpty?.isVisible = it.isEmpty()
        }

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

                movieChatToArchive(position)
            }
        }

        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(binding?.chatList)
    }

    private fun initButton() {
        binding?.emptyButton?.setOnClickListener { navigator().showContacts() }
    }

    private fun movieChatToArchive(position: Int) {
        viewModel.movieChatToArchive(position)
        chatAdapter.notifyItemRemoved(position)
    }


    override fun onClickItem(name: String) {
        navigator().showMessage(name)
    }

    override fun pinChat(id: Int) {
        viewModel.pinChat(id)
        chatAdapter.notifyDataSetChanged()
    }

    override fun unPinChat(id: Int) {
        viewModel.unPinChat(id)
        chatAdapter.notifyDataSetChanged()
    }

    override fun deleteChat(id: Int) {
        viewModel.deleteChat(id)
    }

    override fun turnOfNotifications(id: Int) {
        NotificationBottomSheet().show(parentFragmentManager, null)
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

    override fun onDestroyView() {
        navigator().hideFragment(false)
        super.onDestroyView()
    }
}
