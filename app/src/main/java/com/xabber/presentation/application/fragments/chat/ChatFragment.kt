package com.xabber.presentation.application.fragments.chat

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data.dto.ChatDto
import com.xabber.databinding.FragmentChatBinding
import com.xabber.presentation.application.contract.navigator


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
        binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbarActions()
        fillChat()
    }

    private fun initToolbarActions() {
        binding?.avatarContainer?.setOnClickListener {
            navigator().showAccount()
        }
        binding?.imPlus?.setOnClickListener {
            navigator().showNewChat()
        }
    }

    private fun fillChat() {
        binding?.chatList?.adapter = chatAdapter
        viewModel.chat.observe(viewLifecycleOwner) {
            it.sort()
            chatAdapter.submitList(it)
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
                val icon = context.resources.getDrawable(R.drawable.ic_archive_put)
                val itemView = viewHolder.itemView
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.action_with_chat_background, typedValue, true)
                val background = ColorDrawable(typedValue.data)

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
                val position = viewHolder.bindingAdapterPosition
                        movieChatToArchive(position)

                }
            }

        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(binding?.chatList)
    }

    private fun movieChatToArchive(position: Int) {
        viewModel.movieChatToArchive(position)
        chatAdapter.notifyItemRemoved(position)
    }


    //   chatAdapter!!.submitList(viewModel.chat.sortedBy { !it.isPinned })


    override fun onClickItem(chat: ChatDto) {
        navigator().showMessage(chat)
    }

    override fun pinChat(id: Int, position: Int) {
        viewModel.pinChat(id)
        chatAdapter.notifyDataSetChanged()
    }

    override fun unPinChat(id: Int, position: Int) {
        viewModel.unPinChat(id)
        chatAdapter.notifyDataSetChanged()
    }

    override fun deleteChat(id: Int) {
        viewModel.deleteChat(id)
    }

    override fun turnOfNotifications(id: Int) {
        NotificationBottomSheet().show(parentFragmentManager, null)
        viewModel.turnOfNotifications(id)
    }

    override fun openSpecialNotificationsFragment() {
        navigator().showSpecialNotificationSettings()
    }


}
