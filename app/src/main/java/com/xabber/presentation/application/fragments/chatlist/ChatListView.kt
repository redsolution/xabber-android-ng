// File: ChatListView.kt
package com.xabber.presentation.application.fragments.chatlist

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentChatListBinding
import com.xabber.dto.ChatListDto
import com.xabber.presentation.application.fragments.chat.view.ChatPreloadCache
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.dialogs.NotificationBottomSheet
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.utils.custom.DividerItemDecoration
import com.xabber.utils.partSmoothScrollToPosition

@RequiresApi(Build.VERSION_CODES.O)
class ChatListView : BaseFragment(R.layout.fragment_chat_list), ChatListAdapter.ChatListener {

    private val binding: FragmentChatListBinding by viewBinding(FragmentChatListBinding::bind)
    private val viewModel: ChatListViewModel by activityViewModels()
    private var adapter: ChatListAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        binding.btnMarkAllMessagesUnread.setOnClickListener { viewModel.markAllAsRead() }
        scheduleStartupLoads()
    }

    private fun setupToolbar() {
        binding.chatToolbar.setOnClickListener { binding.chatList.partSmoothScrollToPosition(0) }
    }

    private fun setupRecyclerView() {
        adapter = ChatListAdapter(this)
        binding.chatList.adapter = adapter
        binding.chatList.layoutManager = LinearLayoutManager(requireContext())
        binding.chatList.setHasFixedSize(true)
        binding.chatList.itemAnimator = null
        binding.chatList.setItemViewCacheSize(8)
        binding.chatList.addItemDecoration(
            DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL).apply {
                setChatListOffsetMode(ChatListBaseFragment.ChatListAvatarState.SHOW_AVATARS)
                skipDividerOnLastItem(true)
            }
        )
    }

    private fun scheduleStartupLoads() {
        view?.post {
            viewModel.startInitialLoad()
            view?.post {
                viewModel.startEnrichment()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun observeViewModel() {
        viewModel.chats.observe(viewLifecycleOwner) { chats ->
            adapter?.submitList(chats)
            binding.linEmpty.isVisible = chats.isEmpty()
            binding.btnMarkAllMessagesUnread.isVisible = viewModel.showUnreadOnly.value == true && chats.isNotEmpty()
        }

        viewModel.showUnreadOnly.observe(viewLifecycleOwner) { unreadOnly ->
            binding.tvChatTitle.setText(if (unreadOnly) R.string.unread_chats else R.string.menu_item_chats)
        }

        viewModel.selectedChatId.observe(viewLifecycleOwner) { id ->
            adapter?.setSelectedChatId(id)
        }
    }

    override fun onClickItem(chatListDto: ChatListDto) {
        ChatPreloadCache.put(chatListDto.id, chatListDto)
        viewModel.selectChat(chatListDto.id)
        navigator().showChat(ChatParams(chatListDto.id, chatListDto.drawableId))
    }

    override fun pinChat(chatId: String) {
        viewModel.pinChat(chatId)
    }

    override fun unPinChat(chatId: String, position: Int) {
        viewModel.unpinChat(chatId)
    }

    override fun swipeItem(chatId: String) {
        viewModel.archiveChat(chatId)
    }

    override fun deleteChat(chatName: String, chatId: String) {
        DeletingChatDialog.newInstance(chatName, chatId)
            .show(childFragmentManager, "delete_chat")
    }

    override fun clearHistory(chatName: String, chatId: String) {
        ChatHistoryClearDialog.newInstance(chatName, chatId)
            .show(childFragmentManager, "clear_history")
    }

    override fun turnOfNotifications(chatId: String) {
        NotificationBottomSheet.newInstance(chatId)
            .show(childFragmentManager, "mute_notifications")
    }

    override fun enableNotifications(chatId: String) {
        viewModel.muteChat(chatId, 0L)
    }
}
