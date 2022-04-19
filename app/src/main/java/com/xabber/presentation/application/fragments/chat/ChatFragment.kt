package com.xabber.presentation.application.fragments.chat

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.xabber.databinding.FragmentChatBinding
import com.xabber.presentation.application.contract.navigator


class ChatFragment() : Fragment(), ChatAdapter.ShowMessage {
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
        fillAdapter()
    }

    private fun initToolbarActions() {
        binding?.avatarContainer?.setOnClickListener {
            navigator().goToAccount()
        }
        binding?.imPlus?.setOnClickListener {
            navigator().goToNewMessage()
        }
    }

    private fun fillAdapter() {
        val adapter = ChatAdapter(this)
        binding?.chatList?.adapter = adapter
        adapter.submitList(viewModel.chat.sortedBy { !it.isPinned })
        val simpleCallback = object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        movieChatToArchive()
                    }
                    ItemTouchHelper.RIGHT -> {
                        movieChatToArchive()

                    }
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(binding?.chatList)
    }

    private fun movieChatToArchive() {

    }


    //   chatAdapter!!.submitList(viewModel.chat.sortedBy { !it.isPinned })


    override fun onClick() {
        navigator().goToMessage()
    }

    override fun onClickMenu() {
        NotificationBottomSheet().show(parentFragmentManager, null)
    }
}
