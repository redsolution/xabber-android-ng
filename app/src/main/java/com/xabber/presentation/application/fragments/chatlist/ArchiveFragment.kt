package com.xabber.presentation.application.fragments.chatlist

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.databinding.FragmentChatListBinding
import com.xabber.models.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.AppConstants.TURN_OFF_NOTIFICATIONS_BUNDLE_KEY
import com.xabber.presentation.AppConstants.TURN_OFF_NOTIFICATIONS_KEY
import com.xabber.presentation.application.activity.ColorManager
import com.xabber.presentation.application.bottomsheet.NotificationBottomSheet
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chatlist.archive.ArchiveViewModel
import com.xabber.utils.partSmoothScrollToPosition
import com.xabber.utils.setFragmentResultListener

class ArchiveFragment : BaseFragment(R.layout.fragment_chat_list),
    ChatListAdapter.ChatListener {
    private val binding by viewBinding(FragmentChatListBinding::bind)
    private var adapter: ChatListAdapter? = null
    private val viewModel: ArchiveViewModel by viewModels()
    private val enableNotificationsCode = 0L
    private var snackbar: Snackbar? = null
    private var currentId = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) currentId =
            savedInstanceState.getString(AppConstants.CURRENT_ID_KEY, "")
    else viewModel.initListener()
        setupArchiveUi()
        setToolbarColor()
        initToolbarActions()
        initRecyclerView()
        subscribeOnViewModelData()
   //     fillArchive()
        setDialogListeners()
        binding.refreshLayout.isRefreshEnable = false
        binding.refreshLayout.isLoadMoreEnable = false
    }

    private fun setupArchiveUi() {
        binding.imAvatar.isVisible = false
        binding.avatarStatus.isVisible = false
        binding.chatToolbar.menu.clear()
        binding.emptyButton.isInvisible = true
        binding.tvChatTitle.text = resources.getString(R.string.archived_chat)
        binding.emptyText.text = resources.getString(R.string.archived_list_is_empty_text)
    }

    private fun setToolbarColor() {
        val colorId = viewModel.getColor()
      var fon =  if (colorId != null)ColorManager.convertColorNameToId(colorId) else R.color.blue_300

        val color = ColorUtils.blendARGB(
            ContextCompat.getColor(requireContext(), fon),
            Color.GRAY,
            0.4f
        )
        binding.appbar.setBackgroundColor(color)
    }

    private fun initToolbarActions() {
        binding.chatToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.chatToolbar.setNavigationOnClickListener {
            navigator().closeDetail()
            navigator().goBack()
        }
        binding.chatToolbar.setOnClickListener { binding.chatList.partSmoothScrollToPosition(0) }
    }

    private fun initRecyclerView() {
        val colorName = viewModel.getColor()
        val color = ColorManager.convertColorNameToId(colorName!!)
        val c = if (color != null) color else R.color.blue_500
        adapter = ChatListAdapter(this, c)
        binding.chatList.adapter = adapter
        addItemDecoration()
        addSwipeOption()
    }

    private fun addItemDecoration() {
        binding.chatList.addItemDecoration(
            com.xabber.presentation.application.fragments.chat.DividerItemDecoration(
                binding.root.context,
                LinearLayoutManager.VERTICAL
            ).apply {
                setChatListOffsetMode(ChatListFragment.ChatListAvatarState.SHOW_AVATARS)
            })
    }

    private fun addSwipeOption() {
        if (adapter != null) {
            val swiper = SwipeToArchiveCallback(adapter!!)
            val itemTouch = ItemTouchHelper(swiper)
            itemTouch.attachToRecyclerView(binding.chatList)
        }
    }

    private fun fillArchive() {
        viewModel.getChat()
    }

    private fun setDialogListeners() {
        setFragmentResultListener(TURN_OFF_NOTIFICATIONS_KEY) { _, bundle ->
            val mute =
                bundle.getLong(TURN_OFF_NOTIFICATIONS_BUNDLE_KEY) + System.currentTimeMillis()
            viewModel.setMute(currentId, mute)
        }

        setFragmentResultListener(AppConstants.DELETING_CHAT_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.DELETING_CHAT_BUNDLE_KEY)
            if (result) viewModel.deleteChat(currentId)
        }

        setFragmentResultListener(AppConstants.CLEAR_HISTORY_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.CLEAR_HISTORY_BUNDLE_KEY)
            if (result) viewModel.clearHistoryChat(currentId)
        }
    }

    private fun subscribeOnViewModelData() {
        viewModel.chatList.observe(viewLifecycleOwner) {
            Log.d("aaa", "observe ${it.size}")
            binding.linEmpty.isVisible = it.isEmpty() || it == null
            adapter?.submitList(it)
        }
    }

    override fun onClickItem(chatListDto: ChatListDto) {
        navigator().showChat(
            ChatParams(
                chatListDto.id,
                chatListDto.owner,
                chatListDto.opponentJid,
                chatListDto.drawableId
            )
        )
    }

    override fun pinChat(id: String) {
    }

    override fun unPinChat(id: String) {
    }

    override fun swipeItem(id: String) {
        viewModel.movieChatToArchive(id, false)
        showSnackbar(id)
    }

    private fun showSnackbar(id: String) {
        snackbar = Snackbar.make(
            binding.root,
            R.string.snackbar_title_from_archive,
            Snackbar.LENGTH_LONG
        )
        snackbar?.anchorView = binding.anchor
        snackbar?.setAction(
            R.string.snackbar_button_cancel
        ) {
            viewModel.movieChatToArchive(id, true)
        }
        snackbar?.setActionTextColor(Color.YELLOW)
        snackbar?.show()
    }

    override fun deleteChat(name: String, id: String) {
        currentId = id
        val dialog = DeletingChatDialog.newInstance(name)
        navigator().showDialogFragment(dialog, AppConstants.DELETING_CHAT_DIALOG_TAG)
    }

    override fun clearHistory(chatListDto: ChatListDto) {
        currentId = chatListDto.id
        val name = chatListDto.getChatName()
        val dialog = ChatHistoryClearDialog.newInstance(name)
        navigator().showDialogFragment(dialog, AppConstants.CLEAR_HISTORY_DIALOG_TAG)
    }

    override fun turnOfNotifications(id: String) {
        currentId = id
        val dialog = NotificationBottomSheet()
        navigator().showBottomSheetDialog(dialog)
    }

    override fun enableNotifications(id: String) {
        viewModel.setMute(id, enableNotificationsCode)
    }

    override fun openSpecialNotificationsFragment() {
        navigator().showSpecialNotificationSettings()
    }

    override fun onStop() {
        super.onStop()
        snackbar?.dismiss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(AppConstants.CURRENT_ID_KEY, currentId)
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter = null
    }

}

