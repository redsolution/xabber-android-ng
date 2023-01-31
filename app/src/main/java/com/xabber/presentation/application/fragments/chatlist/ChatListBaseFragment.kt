package com.xabber.presentation.application.fragments.chatlist

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.databinding.FragmentChatListBinding
import com.xabber.models.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.bottomsheet.NotificationBottomSheet
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
//import com.xabber.presentation.application.fragments.chatlist.spec_notifications.ChatList2ViewModel
import com.xabber.utils.partSmoothScrollToPosition
import com.xabber.utils.setFragmentResultListener

//abstract open class ChatListBaseFragment : BaseFragment(R.layout.fragment_chat_list),
//    ChatListAdapter.ChatListener {
//    val binding by viewBinding(FragmentChatListBinding::bind)
//    var chatListAdapter: ChatListAdapter? = null
//    val viewModel: ChatList2ViewModel by viewModels()
//    private var snackbar: Snackbar? = null
//    private val enableNotificationsCode = 0L
//    private var currentId = ""
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        if (savedInstanceState != null) currentId =
//            savedInstanceState.getString(AppConstants.CURRENT_ID_KEY, "")
//        binding.chatToolbar.setOnClickListener { scrollUp()  }
//        setDialogListeners()
//        initRecyclerView()
//    }
//
//     fun scrollUp() {
//        binding.chatList.partSmoothScrollToPosition(0)
//    }
//
//    private fun setDialogListeners() {
//        setFragmentResultListener(AppConstants.TURN_OFF_NOTIFICATIONS_KEY) { _, bundle ->
//            val mute =
//                bundle.getLong(AppConstants.TURN_OFF_NOTIFICATIONS_BUNDLE_KEY) + System.currentTimeMillis()
//            viewModel.setMute(currentId, mute)
//        }
//
//        setFragmentResultListener(AppConstants.DELETING_CHAT_KEY) { _, bundle ->
//            val result = bundle.getBoolean(AppConstants.DELETING_CHAT_BUNDLE_KEY)
//            if (result) viewModel.deleteChat(currentId)
//        }
//
//        setFragmentResultListener(AppConstants.CLEAR_HISTORY_KEY) { _, bundle ->
//            val result = bundle.getBoolean(AppConstants.CLEAR_HISTORY_BUNDLE_KEY)
//            if (result) viewModel.clearHistoryChat(currentId)
//        }
//    }
//
//    open fun initRecyclerView() {
//        chatListAdapter = ChatListAdapter(this)
//        binding.chatList.adapter = chatListAdapter
//        //addItemDecoration()
//        addSwipeOption()
//    }
//
////    private fun addItemDecoration() {
////        binding.chatList.addItemDecoration(
////            com.xabber.presentation.application.fragments.chat.DividerItemDecoration(
////                binding.root.context,
////                LinearLayoutManager.VERTICAL
////            ).apply {
////                setChatListOffsetMode(ChatListAvatarState.SHOW_AVATARS)
////            })
////    }
//
//    private fun addSwipeOption() {
//        if (chatListAdapter != null) {
//            val swiper = SwipeToArchiveCallback(chatListAdapter!!)
//            val itemTouch = ItemTouchHelper(swiper)
//            itemTouch.attachToRecyclerView(binding.chatList)
//        }
//    }
//
//    override fun onClickItem(chatListDto: ChatListDto) {
//        navigator().showChat(
//            ChatParams(
//                chatListDto.id,
//                chatListDto.owner,
//                chatListDto.opponentJid,
//                chatListDto.drawableId
//            )
//        )
//    }
//
//    override fun pinChat(id: String) {
//    }
//
//    override fun unPinChat(id: String) {
//    }
//
//    override fun swipeItem(id: String) {
//
//    }
//
//
//    fun showSnackbar(id: String) {
//        snackbar = Snackbar.make(
//            binding.root,
//            R.string.snackbar_title_from_archive,
//            Snackbar.LENGTH_LONG
//        )
//
//        snackbar?.anchorView = binding.anchor
//        snackbar?.setAction(
//            R.string.snackbar_button_cancel
//        ) {
//            viewModel.movieChatToArchive(id, true)
//        }
//        snackbar?.setActionTextColor(Color.YELLOW)
//        snackbar?.show()
//    }
//
//    override fun deleteChat(name: String, id: String) {
//        currentId = id
//        val dialog = DeletingChatDialog.newInstance(name)
//        navigator().showDialogFragment(dialog, AppConstants.DELETING_CHAT_DIALOG_TAG)
//    }
//
//    override fun clearHistory(chatListDto: ChatListDto) {
//        currentId = chatListDto.id
//        val name = chatListDto.getChatName()
//        val dialog = ChatHistoryClearDialog.newInstance(name)
//        navigator().showDialogFragment(dialog, AppConstants.CLEAR_HISTORY_DIALOG_TAG)
//    }
//
//    override fun turnOfNotifications(id: String) {
//        currentId = id
//        val dialog = NotificationBottomSheet()
//        navigator().showBottomSheetDialog(dialog)
//    }
//
//    override fun enableNotifications(id: String) {
//        viewModel.setMute(id, enableNotificationsCode)
//    }
//
//    override fun openSpecialNotificationsFragment() {
//        navigator().showSpecialNotificationSettings()
//    }
//
//    override fun onStop() {
//        super.onStop()
//        snackbar?.dismiss()
//    }
//
//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        outState.putString(AppConstants.CURRENT_ID_KEY, currentId)
//    }
//
//    enum class ChatListAvatarState {
//        NOT_SPECIFIED, SHOW_AVATARS, DO_NOT_SHOW_AVATARS
//    }
////
////    enum class ChatListType {
////        RECENT, UNREAD, ARCHIVED
////    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        chatListAdapter = null
//    }
//
//}
