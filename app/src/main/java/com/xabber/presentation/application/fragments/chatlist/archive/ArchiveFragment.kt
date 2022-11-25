package com.xabber.presentation.application.fragments.chatlist.archive

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.databinding.FragmentArchiveBinding
import com.xabber.model.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.AppConstants.TURN_OFF_NOTIFICATIONS_BUNDLE_KEY
import com.xabber.presentation.AppConstants.TURN_OFF_NOTIFICATIONS_KEY
import com.xabber.presentation.application.activity.AccountManager
import com.xabber.presentation.application.bottomsheet.NotificationBottomSheet
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.fragments.DetailBaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chatlist.ChatListAdapter
import com.xabber.presentation.application.fragments.chatlist.SwipeToArchiveCallback
import com.xabber.utils.partSmoothScrollToPosition
import com.xabber.utils.setFragmentResultListener

class ArchiveFragment : DetailBaseFragment(R.layout.fragment_archive),
    ChatListAdapter.ChatListener {
    private val binding by viewBinding(FragmentArchiveBinding::bind)
    private var adapter: ChatListAdapter? = null
    private val viewModel = ArchiveViewModel()
    private val enableNotificationsCode = 0L
    private var snackbar: Snackbar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setToolbarColor()
        initToolbarActions()
        initRecyclerView()
        subscribeOnViewModelData()
        fillArchive()
        viewModel.initListener()
    }

    private fun setToolbarColor() {
        val color = ColorUtils.blendARGB(
            ContextCompat.getColor(requireContext(), R.color.blue_300),
            Color.GRAY,
            0.4f
        )
        binding.appbar.setBackgroundColor(color)
    }

    private fun initToolbarActions() {
        binding.chatToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.chatToolbar.setNavigationOnClickListener { navigator().goBack() }
        binding.chatToolbar.setOnClickListener { binding.archivedList.partSmoothScrollToPosition(0) }
    }

    private fun initRecyclerView() {
        adapter = ChatListAdapter(this)
        binding.archivedList.adapter = adapter
        addSwipeOption()
    }

    private fun addSwipeOption() {
        if (adapter != null) {
            val swiper = SwipeToArchiveCallback(adapter!!)
            val itemTouch = ItemTouchHelper(swiper)
            itemTouch.attachToRecyclerView(binding.archivedList)
        }
    }

    private fun fillArchive() {
        viewModel.getChat()
    }

    private fun subscribeOnViewModelData() {
        viewModel.chatList.observe(viewLifecycleOwner) {
            val a = ArrayList<ChatListDto>()
            a.addAll(it)
            a.sort()
            binding.linEmpty.isVisible = it.isEmpty() || it == null
            adapter?.submitList(a)
        }
    }

    override fun onClickItem(chatListDto: ChatListDto) {
        navigator().showChat(
            ChatParams(
                chatListDto.id,
                AccountManager.owner,
                chatListDto.opponentName,
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
            R.string.snackbar_title_to_archive,
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
        val dialog = DeletingChatDialog.newInstance(name)
        navigator().showDialogFragment(dialog, AppConstants.DELETING_CHAT_DIALOG_TAG)
        setFragmentResultListener(AppConstants.DELETING_CHAT_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.DELETING_CHAT_BUNDLE_KEY)
            if (result) viewModel.deleteChat(id)
        }
    }

    override fun clearHistory(id: String, name: String, opponent: String) {
        val dialog = ChatHistoryClearDialog.newInstance(name)
        navigator().showDialogFragment(dialog, AppConstants.CLEAR_HISTORY_DIALOG_TAG)
        setFragmentResultListener(AppConstants.CLEAR_HISTORY_KEY) { _, bundle ->
            val result = bundle.getBoolean(AppConstants.CLEAR_HISTORY_BUNDLE_KEY)
            if (result) viewModel.clearHistoryChat(id)
        }
    }

    override fun turnOfNotifications(id: String) {
        val dialog = NotificationBottomSheet()
        navigator().showBottomSheetDialog(dialog)
        setFragmentResultListener(TURN_OFF_NOTIFICATIONS_KEY) { _, bundle ->
            val resultMuteExpired =
                bundle.getLong(TURN_OFF_NOTIFICATIONS_BUNDLE_KEY) + System.currentTimeMillis()
            viewModel.setMute(id, resultMuteExpired)
        }
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

    override fun onDestroy() {
        super.onDestroy()
        adapter = null
    }
}
