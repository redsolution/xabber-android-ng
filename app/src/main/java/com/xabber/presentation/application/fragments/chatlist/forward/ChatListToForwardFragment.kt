package com.xabber.presentation.application.fragments.chatlist.forward

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SearchView
import androidx.annotation.RequiresApi
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.last_chats.LastChatsStorageItem
import com.xabber.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chatlist.ChatListViewModel
import com.xabber.utils.showToast
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatListToForwardFragment : DetailBaseFragment(R.layout.fragment_chat_for_forward),
    ChatListForForwardAdapter.Listener {

    private var forwardMessage = ""
    private var ownerJid = ""
    private val viewModel: ChatListViewModel by viewModels()
    private var adapter: ChatListForForwardAdapter? = null
    private lateinit var searchView: SearchView
    private lateinit var chatList: RecyclerView
    private val realm: Realm by lazy { Realm.open(defaultRealmConfig()) }

    companion object {
        private const val ARG_MESSAGE = "forward_message"
        private const val ARG_OWNER_JID = "owner_jid"

        fun newInstance(textMessage: String, ownerJid: String) = ChatListToForwardFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_MESSAGE, textMessage)
                putString(ARG_OWNER_JID, ownerJid)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Получаем аргументы
        arguments?.let {
            forwardMessage = it.getString(ARG_MESSAGE, "")
            ownerJid = it.getString(ARG_OWNER_JID, "") ?: ""
        }

        searchView = view.findViewById(R.id.forward_search)
        chatList = view.findViewById(R.id.chat_list)
        searchView.maxWidth = Int.MAX_VALUE

        adapter = ChatListForForwardAdapter(this)
        chatList.adapter = adapter
        chatList.layoutManager = LinearLayoutManager(requireContext())

        // Правильно: подписываемся на LiveData<List<ChatListDto>>
        viewModel.chats.observe(viewLifecycleOwner) { chatListDtoList ->
            val list = ArrayList(chatListDtoList)  // ← теперь можно мутировать

            // Добавляем "Saved Messages", если его нет
            val savedMessagesId = LastChatsStorageItem.genPrimary(
                ownerJid, ownerJid, com.xabber.data_base.models.sync.ConversationType.Regular
            )

            if (list.none { it.id == savedMessagesId }) {
                list.add(0, ChatListDto(
                    id = savedMessagesId,
                    owner = ownerJid,
                    opponentJid = ownerJid,
                    opponentNickname = "Saved Messages",
                    customNickname = "",
                    lastMessageBody = "",
                    lastMessageDate = System.currentTimeMillis(),
                    lastMessageState = com.xabber.data_base.models.messages.MessageSendingState.Read,
                    isArchived = false,
                    isSynced = true,
                    draftMessage = null,
                    hasAttachment = false,
                    isSystemMessage = false,
                    isMentioned = false,
                    muteExpired = 0L,
                    pinnedDate = 0L,
                    status = com.xabber.data_base.models.presences.ResourceStatus.ONLINE,
                    entity = com.xabber.data_base.models.presences.RosterItemEntity.CONTACT,
                    unread = "",
                    lastPosition = "",
                    drawableId = R.drawable.saved_messages_avatar,
                    isHide = false,
                    colorKey = ""
                ))
            }

            adapter?.submitList(list)
        }
    }

    override fun onClickItem(id: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Создаём Saved Messages, если его нет
                val savedMessagesId = LastChatsStorageItem.genPrimary(ownerJid, ownerJid, com.xabber.data_base.models.sync.ConversationType.Regular)
                if (id == savedMessagesId) {
                    val exists = realm.query<LastChatsStorageItem>("primary = '$id'").first().find() != null
                    if (!exists) {
                        realm.write {
                            copyToRealm(LastChatsStorageItem().apply {
                                primary = savedMessagesId
                                owner = ownerJid
                                jid = ownerJid
                                conversationType_ = com.xabber.data_base.models.sync.ConversationType.Regular.rawValue
                                messageDate = System.currentTimeMillis()
                                muteExpired = -1
                                isArchived = false
                                unread = 0
                                rosterItem = copyToRealm(com.xabber.data_base.models.roster.RosterStorageItem().apply {
                                    primary = com.xabber.data_base.models.roster.RosterStorageItem.genPrimary(ownerJid, ownerJid)
                                    this.owner = ownerJid
                                    this.jid = ownerJid
                                    customNickname = "Saved Messages"
                                })
                            })
                        }
                    }
                }

                // Форвардим сообщение (логика в отдельном сервисе или ViewModel)
                // Пока просто показываем тост
                withContext(Dispatchers.Main) {
                    showToast("Message forwarded")
                    navigator().goBack()
                }
            } catch (e: Exception) {
                Log.e("Forward", "Failed to forward message", e)
                withContext(Dispatchers.Main) {
                    showToast("Failed to forward")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        realm.close()
        adapter = null
    }
}