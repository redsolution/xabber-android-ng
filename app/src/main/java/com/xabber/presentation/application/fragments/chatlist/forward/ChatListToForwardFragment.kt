package com.xabber.presentation.application.fragments.chatlist.forward

import android.os.Bundle
import android.view.View
import android.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.models.dto.ChatListDto
import com.xabber.models.xmpp.last_chats.LastChatsStorageItem
import com.xabber.models.xmpp.messages.MessageSendingState
import com.xabber.models.xmpp.presences.ResourceStatus
import com.xabber.models.xmpp.presences.RosterItemEntity
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chatlist.ChatListViewModel
import com.xabber.utils.showToast
import io.realm.kotlin.Realm

class ChatListToForwardFragment : DetailBaseFragment(R.layout.fragment_chat_for_forward),
    ChatListForForwardAdapter.Listener {
    private var forwardMessage = ""
    private val chatListViewModel: ChatListViewModel by viewModels()
    private var chatListAdapter: ChatListForForwardAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private lateinit var searchView: SearchView
    lateinit var chatList: RecyclerView
    private var jid = ""

    companion object {
        fun newInstance(textMessage: String, _jid: String) = ChatListToForwardFragment().apply {
            arguments = Bundle().apply {
                putString(AppConstants.CLEAR_HISTORY_NAME_KEY, textMessage)
                forwardMessage = textMessage
                jid = _jid
            }
        }
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
        chatListViewModel.chats.observe(viewLifecycleOwner) {
            val list = it
            if (!chatListViewModel.isSavedHas(jid)) {
                list.add(
                    0, ChatListDto(
                        id = jid,
                        owner = jid,
                        opponentJid = jid,
                        opponentNickname = "Saved Messages",
                        customNickname = "",
                        lastMessageBody = "",
                        lastMessageDate = 0L,
                        lastMessageState = MessageSendingState.Read,
                        isArchived = false,
                        isSynced = true,
                        draftMessage = null,
                        hasAttachment = false,
                        isSystemMessage = false,
                        isMentioned = false,
                        muteExpired = 0,
                        pinnedDate = 0,
                        status = ResourceStatus.Online,
                        entity = RosterItemEntity.Contact,
                        unread = "",
                        lastPosition = "",
                        drawableId = R.drawable.saved_messages_avatar,
                        isHide = false,
                        lastMessageIsOutgoing = true
                    )
                )
            }
            chatListAdapter?.submitList(it)
        }
        chatListViewModel.getChatList()

    }

    override fun onClickItem(id: String) {
        if (id == jid && !chatListViewModel.isSavedHas(jid)) {
            var realm = Realm.open(defaultRealmConfig())
            realm.writeBlocking {
                this.copyToRealm(LastChatsStorageItem().apply {
                    primary = jid
                    muteExpired = -1
                    owner = jid
                    jid = "Saved messages"
                    messageDate = System.currentTimeMillis()
                    isArchived = false
                    unread = 0
                    avatar = R.drawable.saved_messages_avatar
                })
            }
        }
        chatListViewModel.forwardMessage(id, forwardMessage)
        showToast("Messages have been forwarded")
        navigator().goBack()
    }

}
