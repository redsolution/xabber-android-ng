package com.xabber.presentation.application.fragments.chatlist

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.*
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.databinding.FragmentChatListBinding
import com.xabber.dto.ChatListDto
import com.xabber.presentation.AppConstants.CHAT_LIST_UNREAD_KEY
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_DIALOG_TAG
import com.xabber.presentation.AppConstants.DELETING_CHAT_DIALOG_TAG
import com.xabber.presentation.AppConstants.NOTIFICATION_BOTTOM_SHEET_TAG
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.dialogs.NotificationBottomSheet
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.account.color.AccountColorDialog
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.custom.DividerItemDecoration
import com.xabber.utils.custom.PullRefreshLayout
import com.xabber.utils.custom.SwipeToArchiveCallback
import com.xabber.utils.partSmoothScrollToPosition

/**
 * This fragment displays the chat list of enabled accounts and allows you to perform actions with chats.
 */
@RequiresApi(Build.VERSION_CODES.O)
class ChatListFragment : BaseFragment(R.layout.fragment_chat_list), ChatListAdapter.ChatListener {
    private val viewModel: ChatListViewModel by viewModels()
    private val binding by viewBinding(FragmentChatListBinding::bind)
    private var adapter: ChatListAdapter? = null
    private val chatListViewModel: ChatListViewModel by activityViewModels()
    private var chatListAdapter: ChatListAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private var isPin = false
    private var isUnpin = false
    private var unpinnedChatPosition = -1
    private var snackbar: Snackbar? = null
    private val enableNotificationsCode = 0L
    private var showUnreadOnly = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = ChatListAdapter(this).apply {
            recyclerView = binding.chatList
        }
        binding.chatList.adapter = adapter
        // остальной setup без изменений
    }

    private fun observeViewModel() {
        viewModel.chats.observe(viewLifecycleOwner) { list ->
            adapter?.submitList(list)
            binding.linEmpty.isVisible = list.isEmpty()
        }

        viewModel.selectedChatId.observe(viewLifecycleOwner) { id ->
            adapter?.setSelectedChatId(id)
        }
    }

    override fun onClickItem(chatListDto: ChatListDto) {
        viewModel.selectChat(chatListDto.id)
        navigator().showChat(ChatParams(chatListDto.id, chatListDto.drawableId))
    }

    override fun pinChat(chatId: String) {
        chatListViewModel.pinChat(chatId)
        isPin = true
    }

    override fun unPinChat(chatId: String, position: Int) {
        chatListViewModel.unpinChat(chatId)
        isUnpin = true
        unpinnedChatPosition = position
    }

    override fun swipeItem(chatId: String) {
        chatListViewModel.archiveChat(chatId)
        showSnackbar(chatId)
    }

    override fun deleteChat(chatName: String, chatId: String) {
        val dialog = DeletingChatDialog.newInstance(chatName, chatId)
        dialog.show(childFragmentManager, DELETING_CHAT_DIALOG_TAG)
    }

    override fun clearHistory(chatName: String, chatId: String) {
        val dialog = ChatHistoryClearDialog.newInstance(chatName, chatId)
        dialog.show(childFragmentManager, CLEAR_HISTORY_DIALOG_TAG)
    }

    override fun turnOfNotifications(chatId: String) {
        NotificationBottomSheet.newInstance(chatId)
            .show(childFragmentManager, NOTIFICATION_BOTTOM_SHEET_TAG)
    }

    override fun enableNotifications(chatId: String) {
        chatListViewModel.muteChat(chatId, enableNotificationsCode)
    }

    private fun showSnackbar(id: String) {
        snackbar?.dismiss()
        snackbar = Snackbar.make(
            binding.root,
            R.string.snackbar_title_to_archive,
            Snackbar.LENGTH_SHORT
        )

        snackbar?.anchorView = binding.anchor
        snackbar?.setAction(
            R.string.snackbar_button_cancel
        ) {
            chatListViewModel.archiveChat(id)
        }
        snackbar?.setActionTextColor(Color.YELLOW)
        snackbar?.show()
    }

    // Remove or comment out initPullRefreshLayout since PullRefreshLayout is disabled
    /*
    private fun initPullRefreshLayout() {
        val colorKey = R.color.amber_200.toString()
        val superLightColor = ColorManager.convertColorSuperLightNameToId(colorKey)
        val lightColor = ColorManager.convertColorLightNameToId(colorKey)
        val standardColor = ColorManager.convertColorMediumNameToId(colorKey)

        binding.refreshLayout.setOnRefreshListener(object :
            PullRefreshLayout.OnRefreshListener {
            override fun onRefresh() {
                binding.refreshLayout.postDelayed({
                    binding.refreshLayout.finishRefresh()
                    navigator().showArchive()
                }, 0)
            }

            override fun onRefreshPulStateChange(percent: Float, state: Int) {
                when (state) {
                    PullRefreshLayout.NOT_OVER_TRIGGER_POINT -> {
                        binding.refreshLayout.setRefreshViewText(
                            R.string.pull_to_show_archive
                        )
                        if (isOverTriggerCrossed) {
                            shortVibrate()
                            isOverTriggerCrossed = false
                        }
                        binding.refreshLayout.setHeaderBackground(R.color.grey_50)
                        binding.refreshLayout.setElementsColors(
                            R.color.grey_400,
                            R.color.grey_300,
                            false
                        )
                    }
                    PullRefreshLayout.OVER_TRIGGER_POINT -> {
                        if (!isOverTriggerCrossed) {
                            shortVibrate()
                        }
                        isOverTriggerCrossed = true
                        binding.refreshLayout.setRefreshViewText(
                            R.string.release_to_show_archive
                        )
                        binding.refreshLayout.setHeaderBackground(superLightColor)
                        binding.refreshLayout.setElementsColors(standardColor, lightColor, true)
                    }
                    PullRefreshLayout.START -> {
                        isOverTriggerCrossed = false
                        binding.refreshLayout.finishRefresh()
                    }
                }
            }
        })
    }
    */

    private fun shortVibrate() {
        view?.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(
            CHAT_LIST_UNREAD_KEY,
            showUnreadOnly
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        chatListAdapter?.notifyDataSetChanged()
    }

    override fun onStop() {
        super.onStop()
        snackbar?.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        layoutManager = null
        chatListAdapter = null
    }
}