package com.xabber.presentation.application.fragments.chat

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data.dto.ChatDto
import com.xabber.databinding.FragmentChatBinding
import com.xabber.presentation.application.contract.navigator


class ChatFragment : Fragment(), ChatAdapter.ShowMessage {
    private var binding: FragmentChatBinding? = null
    lateinit var userName: String
    private val viewModel = ChatViewModel()
    private var chatAdapter: ChatAdapter? = null

    companion object {
        fun newInstance(_userName: String) = ChatFragment().apply {
            userName = _userName
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
        binding?.avatarContainer?.setOnClickListener {
            navigator().startAccountFragment()
        }
    }

    private fun initToolbarActions() {
        binding?.avatarContainer?.setOnClickListener {
            navigator().goToAccount()
        }
        binding?.imPlus?.setOnClickListener {
            navigator().goToNewMessage()
        }
    }

    private fun fillChat() {
        chatAdapter = ChatAdapter(this)
        binding?.chatList?.adapter = chatAdapter
        viewModel.chat.observe(viewLifecycleOwner) {
            chatAdapter!!.submitList(it)
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

                val back = ColorDrawable(Color.GRAY)

                //     back.setBounds(0, viewHolder.itemView.top,
                //        (viewHolder.itemView.left + dX).toInt(), viewHolder.itemView.bottom)
                back.setBounds(
                    (viewHolder.itemView.right + dX).toInt(),
                    viewHolder.itemView.top,
                    viewHolder.itemView.right,
                    viewHolder.itemView.bottom
                )

                back.draw(c)
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        movieChatToArchive(position)
                    }
                    ItemTouchHelper.RIGHT -> {
                        movieChatToArchive(position)

                    }
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(binding?.chatList)
    }

    private fun movieChatToArchive(position: Int) {
        viewModel.movieChatToArchive(position)
        chatAdapter!!.notifyItemRemoved(position)
    }


    //   chatAdapter!!.submitList(viewModel.chat.sortedBy { !it.isPinned })


    override fun onClick(chat: ChatDto) {
        navigator().goToMessage(chat)
    }

    override fun openSpecialNotificationsFragment() {
        navigator().startSpecialNotificationsFragment()
    }

    override fun onClickMenu() {
        NotificationBottomSheet().show(parentFragmentManager, null)
    }
}
