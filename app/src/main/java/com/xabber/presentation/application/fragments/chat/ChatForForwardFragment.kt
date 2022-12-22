package com.xabber.presentation.application.fragments.chat

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentChatForForwardBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chatlist.ChatListViewModel
import com.xabber.utils.parcelable
import com.xabber.utils.showToast

class ChatForForwardFragment : DetailBaseFragment(R.layout.fragment_chat_for_forward),
    ChatListForForwardAdapter.Listener {
    private val binding by viewBinding(FragmentChatForForwardBinding::bind)
    private val chatListViewModel: ChatListViewModel by activityViewModels()
    private var chatListAdapter: ChatListForForwardAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private var forwardMessage = ""


    companion object {
        fun newInstance(textMessage: String) = ChatForForwardFragment().apply {
            arguments = Bundle().apply {
                putString(AppConstants.CLEAR_HISTORY_NAME_KEY, textMessage)
                forwardMessage = textMessage
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val searchEditText =
            binding.forwardSearch.findViewById(androidx.appcompat.R.id.search_src_text) as EditText
        searchEditText.setTextColor(resources.getColor(R.color.white))
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbar.setNavigationOnClickListener {
            navigator().closeDetail()
        }
        chatListAdapter = ChatListForForwardAdapter(this)
        binding.chatList.adapter = chatListAdapter
        layoutManager = binding.chatList.layoutManager as LinearLayoutManager
        chatListViewModel.getChatList()
        chatListViewModel.chatList.observe(viewLifecycleOwner) {
            chatListAdapter!!.submitList(it)
        }
    }

    override fun onClickItem(id: String) {
        chatListViewModel.forwardMessage(id, forwardMessage)
        showToast("Messages have been forwarded")
        navigator().goBack()
    }
}