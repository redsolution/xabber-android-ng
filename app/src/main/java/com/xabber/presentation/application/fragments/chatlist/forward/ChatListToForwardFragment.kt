package com.xabber.presentation.application.fragments.chatlist.forward

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.chatlist.ChatListViewModel
import com.xabber.utils.showToast

class ChatListToForwardFragment: DialogFragment(), ChatListForForwardAdapter.Listener {
    private var forwardMessage = ""
    private val chatListViewModel: ChatListViewModel by activityViewModels()
    private var chatListAdapter: ChatListForForwardAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private lateinit var searchView: SearchView
    lateinit var chatList: RecyclerView

    companion object {
        fun newInstance(textMessage: String) = ChatListToForwardFragment().apply {
            arguments = Bundle().apply {
                putString(AppConstants.CLEAR_HISTORY_NAME_KEY, textMessage)
                forwardMessage = textMessage
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = layoutInflater.inflate(R.layout.fragment_chat_for_forward, null)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AlertDialog.Builder(context)
        val view = layoutInflater.inflate(R.layout.fragment_chat_for_forward, null)

        dialog.setView(view)
        return dialog.create()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchView = view.findViewById(R.id.forward_search)
        chatList = view.findViewById(R.id.chat_list)
        searchView.maxWidth = Int.MAX_VALUE
    //    val searchEditText = fowardSearch.findViewById(androidx.appcompat.R.id.search_src_text) as EditText
   //     searchEditText.setTextColor(resources.getColor(R.color.white))
//        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
//        binding.toolbar.setNavigationOnClickListener {
//            navigator().closeDetail()
//        }
        chatListAdapter = ChatListForForwardAdapter(this)
        chatList.adapter = chatListAdapter
        layoutManager = chatList.layoutManager as LinearLayoutManager
        chatListViewModel.getChatList()
        chatListViewModel.chats.observe(viewLifecycleOwner) {
            chatListAdapter!!.submitList(it)
        }
    }

    override fun onClickItem(id: String) {
        chatListViewModel.forwardMessage(id, forwardMessage)
        showToast("Messages have been forwarded")
        navigator().goBack()
    }

}
