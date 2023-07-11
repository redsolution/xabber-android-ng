package com.xabber.presentation.application.fragments.chatlist.archive

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.databinding.FragmentArchiveBinding
import com.xabber.dto.ChatListDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.dialogs.NotificationBottomSheet
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chatlist.ChatListAdapter
import com.xabber.presentation.application.fragments.chatlist.ChatListBaseFragment
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.custom.DividerItemDecoration
import com.xabber.utils.custom.SwipeToArchiveCallback
import com.xabber.utils.partSmoothScrollToPosition

class ArchiveFragment : BaseFragment(R.layout.fragment_archive),
    ChatListAdapter.ChatListener {
    private val binding by viewBinding(FragmentArchiveBinding::bind)
    private var adapter: ChatListAdapter? = null
    private val viewModel: ArchiveViewModel by activityViewModels()
    private var layoutManager: LinearLayoutManager? = null
    private val enableNotificationsCode = 0L
    private var snackbar: Snackbar? = null
    private var isPin = false
    private var isUnpin = false
    private var unpinnedChatPosition = -1
    private var selectedChatId = ""
    private var itemAnimator = DefaultItemAnimator().apply {
        this.removeDuration = 0
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupArchiveUi()
        initToolbarActions()
        initRecyclerView()
        subscribeOnViewModelData()
    }

    private fun setupArchiveUi() {
        binding.emptyText.text = resources.getString(R.string.archived_list_is_empty_text)
    }

    private fun initToolbarActions() {
        binding.chatToolbar.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.chatToolbar.setNavigationOnClickListener {
            navigator().closeDetail()
            navigator().goBack()
        }
        binding.chatToolbar.setOnClickListener { scrollUp() }
    }

    private fun scrollUp() {
        binding.chatList.partSmoothScrollToPosition(0)
    }

    private fun initRecyclerView() {
        adapter = ChatListAdapter(this)
        binding.chatList.adapter = adapter
        layoutManager = binding.chatList.layoutManager as LinearLayoutManager
        setRemoveDurationAnimation()
        addItemDecoration()
        addSwipeOption()
        addScrollListener()
    }

    private fun setRemoveDurationAnimation() {
        binding.chatList.animation = null
        val animator = DefaultItemAnimator()
        animator.removeDuration = 0
        binding.chatList.itemAnimator = animator
    }

    private fun addItemDecoration() {
        binding.chatList.addItemDecoration(
            DividerItemDecoration(
                binding.root.context,
                LinearLayoutManager.VERTICAL
            ).apply {
                setChatListOffsetMode(ChatListBaseFragment.ChatListAvatarState.SHOW_AVATARS)
            })
    }

    private fun addSwipeOption() {
        if (adapter != null) {
            val swiper = SwipeToArchiveCallback(adapter!!)
            val itemTouch = ItemTouchHelper(swiper)
            itemTouch.attachToRecyclerView(binding.chatList)
        }
    }

    private fun addScrollListener() {   // Если находимся вверху списка делаем scrollbar невидимым
        if (layoutManager != null) {
            binding.chatList.setOnScrollChangeListener { _, _, _, _, _ ->
                if (layoutManager!!.findFirstVisibleItemPosition() <= 2) {
                    binding.chatList.scrollBarSize = 0
                } else {
                    binding.chatList.scrollBarSize = 10
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun subscribeOnViewModelData() {
        viewModel.chatList.observe(viewLifecycleOwner) {
            val positionBeforeUpdate = layoutManager?.findFirstVisibleItemPosition()
            adapter?.isManyOwners = viewModel.getAccountsAmount() > 1
            val list = ArrayList<ChatListDto>()
            list.addAll(it)
            adapter?.submitList(list) {
                binding.linEmpty.isVisible = it.isEmpty() || it == null
                if (isPin) {                                           // Если это перемещение элемента вверх при видимом элементе 0 произойдет стандартная анимация, иначе выключаем анимацию
                    if (layoutManager != null) {
                        if (layoutManager!!.findFirstVisibleItemPosition() > 0)
                            binding.chatList.itemAnimator = null
                        else binding.chatList.itemAnimator = itemAnimator
                        binding.chatList.partSmoothScrollToPosition(0)
                        isPin = false
                    }
                } else if (isUnpin && unpinnedChatPosition == layoutManager?.findFirstVisibleItemPosition()) {
                    if (positionBeforeUpdate != null)             // Меняем стандартное поведение recyclerView (при перемещении первого видимого элемента происходит скроллирование списка до его новой позиции)
                        layoutManager?.scrollToPositionWithOffset(  // на нужное нам: остаемся на позиции beforeUpdate
                            positionBeforeUpdate,
                            0
                        )
                    isUnpin = false
                }
            }
            binding.chatList.itemAnimator = itemAnimator   // включаем анимацию
        }

        baseViewModel.colorKey.observe(viewLifecycleOwner) {
            adapter?.isManyOwners = viewModel.getAccountsAmount() > 1
          //  viewModel.initListener()
            viewModel.getChat()
        }
    }

    override fun onClickItem(chatListDto: ChatListDto) {
        if (DisplayManager.isDualScreenMode()) {
            if (selectedChatId != chatListDto.id) {   // В режиме двух экранов перед тем как открыть чат делаем проверку на то что он уже открыт, чтобы не открывать заново
                selectedChatId = chatListDto.id
                navigator().showChat(
                    ChatParams(
                        chatListDto.id,
                        chatListDto.drawableId  // пока не работает сервер, передаем id аватарки из ресурсов
                    )
                )
            }
        } else {
            navigator().showChat(
                ChatParams(
                    chatListDto.id,
                    chatListDto.drawableId
                )
            )
        }
    }

    override fun pinChat(chatId: String) {
        viewModel.pinChat(chatId)
        isPin = true
    }

    override fun unPinChat(chatId: String, position: Int) {
        viewModel.unPinChat(chatId)
        isUnpin = true
        unpinnedChatPosition = position
    }

    override fun swipeItem(chatId: String) {
        viewModel.setArchived(chatId)
        showSnackbar(chatId)
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
            viewModel.setArchived(id)
        }
        snackbar?.setActionTextColor(Color.YELLOW)
        snackbar?.show()
    }

    override fun deleteChat(chatName: String, chatId: String) {
        val dialog = DeletingChatDialog.newInstance(chatName, chatId)
        navigator().showDialogFragment(dialog, AppConstants.DELETING_CHAT_DIALOG_TAG)
    }

    override fun clearHistory(chatName: String, chatId: String) {
        val dialog = ChatHistoryClearDialog.newInstance(chatName, chatId)
        navigator().showDialogFragment(dialog, AppConstants.CLEAR_HISTORY_DIALOG_TAG)
    }

    override fun turnOfNotifications(chatId: String) {
        val dialog = NotificationBottomSheet.newInstance(chatId)
        navigator().showBottomSheetDialog(dialog)
    }

    override fun enableNotifications(chatId: String) {
        viewModel.setMute(chatId, enableNotificationsCode)
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
