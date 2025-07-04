package com.xabber.presentation.application.fragments.chatlist.forward

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.data_base.models.messages.MessageSendingState
import com.xabber.data_base.models.presences.ResourceStatus
import com.xabber.data_base.models.presences.RosterItemEntity
import com.xabber.data_base.models.roster.RosterStorageItem
import com.xabber.data_base.models.sync.ConversationType
import com.xabber.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chatlist.ChatListViewModel
import com.xabber.utils.showToast
import io.realm.kotlin.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatListToForwardFragment : DetailBaseFragment(R.layout.fragment_chat_for_forward),
    ChatListForForwardAdapter.Listener {
    private var forwardMessage = ""
    private val chatListViewModel: ChatListViewModel by viewModels()
    private var chatListAdapter: ChatListForForwardAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private lateinit var searchView: SearchView
    lateinit var chatList: RecyclerView
    private var ownerJid = ""
    private val realm: Realm by lazy { Realm.open(defaultRealmConfig()) }

    companion object {
        fun newInstance(textMessage: String, ownerJid: String) = ChatListToForwardFragment().apply {
            arguments = Bundle().apply {
                putString(AppConstants.CLEAR_HISTORY_NAME_KEY, textMessage)
                putString(AppConstants.OWNER_JID_KEY, ownerJid)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            forwardMessage = it.getString(AppConstants.CLEAR_HISTORY_NAME_KEY, "")
            ownerJid = it.getString(AppConstants.OWNER_JID_KEY, "")
        }
        searchView = view.findViewById(R.id.forward_search)
        chatList = view.findViewById(R.id.chat_list)
        searchView.maxWidth = Int.MAX_VALUE
        chatListAdapter = ChatListForForwardAdapter(this)
        chatList.adapter = chatListAdapter
        layoutManager = chatList.layoutManager as LinearLayoutManager

        chatListViewModel.chats.observe(viewLifecycleOwner) { chats ->
            val list = ArrayList(chats)
            Log.d("ChatListToForwardFragment", "Received ${list.size} chats from LiveData")
            list.forEach { Log.d("ChatListToForwardFragment", "Chat: $it") }

            if (!chatListViewModel.isSavedHas(ownerJid)) {
                list.add(
                    0, ChatListDto(
                        id = LastChatsStorageItem.genPrimary(ownerJid, ownerJid, ConversationType.Regular),
                        owner = ownerJid,
                        opponentJid = ownerJid,
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
                        status = ResourceStatus.ONLINE,
                        entity = RosterItemEntity.CONTACT,
                        unread = "",
                        lastPosition = "",
                        drawableId = R.drawable.saved_messages_avatar,
                        isHide = false
                    )
                )
                Log.d("ChatListToForwardFragment", "Added Saved Messages chat for ownerJid $ownerJid")
            }
            chatListAdapter?.submitList(list)
            Log.d("ChatListToForwardFragment", "Submitted ${list.size} chats to adapter")
        }
        chatListViewModel.getChatList()
    }

    override fun onClickItem(id: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (id == LastChatsStorageItem.genPrimary(ownerJid, ownerJid, ConversationType.Regular) && !chatListViewModel.isSavedHas(ownerJid)) {
                realm.write {
                    copyToRealm(LastChatsStorageItem().apply {
                        primary = LastChatsStorageItem.genPrimary(ownerJid, ownerJid, ConversationType.Regular)
                        muteExpired = -1
                        owner = ownerJid
                        jid = ownerJid
                        conversationType_ = ConversationType.Regular.rawValue
                        messageDate = System.currentTimeMillis()
                        isArchived = false
                        unread = 0
                        avatar = R.drawable.saved_messages_avatar
                        rosterItem = copyToRealm(RosterStorageItem().apply {
                            primary = RosterStorageItem.genPrimary(ownerJid, ownerJid)
                            owner = this@apply.owner
                            jid = this@apply.jid
                            customNickname = "Saved Messages"
                        })
                    })
                    Log.d("ChatListToForwardFragment", "Created Saved Messages chat for ownerJid $ownerJid")
                }
            }
            chatListViewModel.forwardMessage(id, forwardMessage)
            withContext(Dispatchers.Main) {
                showToast("Messages have been forwarded")
                navigator().goBack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        realm.close()
        chatListAdapter = null
        layoutManager = null
    }
}