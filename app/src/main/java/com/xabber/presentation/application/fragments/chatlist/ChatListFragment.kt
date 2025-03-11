package com.xabber.presentation.application.fragments.chatlist

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.*
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.data_base.models.account.AccountStorageItem
import com.xabber.databinding.FragmentChatListBinding
import com.xabber.dto.AccountDto
import com.xabber.dto.AvatarDto
import com.xabber.dto.ChatListDto
import com.xabber.presentation.AppConstants.CHAT_LIST_UNREAD_KEY
import com.xabber.presentation.AppConstants.CLEAR_HISTORY_DIALOG_TAG
import com.xabber.presentation.AppConstants.DELETING_CHAT_DIALOG_TAG
import com.xabber.presentation.AppConstants.NOTIFICATION_BOTTOM_SHEET_TAG
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.AccountDialog
import com.xabber.presentation.application.dialogs.ChatHistoryClearDialog
import com.xabber.presentation.application.dialogs.DeletingChatDialog
import com.xabber.presentation.application.dialogs.NotificationBottomSheet
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.contacts.ContactsFragment
import com.xabber.presentation.application.manage.ColorManager
import com.xabber.presentation.application.manage.DisplayManager
import com.xabber.utils.custom.DividerItemDecoration
import com.xabber.utils.custom.PullRefreshLayout
import com.xabber.utils.custom.SwipeToArchiveCallback
import com.xabber.utils.partSmoothScrollToPosition
import com.xabber.utils.toAccountDto
import com.xabber.utils.toAvatarDto
import io.realm.kotlin.Realm

/**
 * This fragment displays the chat list of enabled accounts and allows you to perform actions with chats.
 */
class ChatListFragment : BaseFragment(R.layout.fragment_chat_list), ChatListAdapter.ChatListener, NavigationView.OnNavigationItemSelectedListener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var actionBarToggle: ActionBarDrawerToggle

    private val realm = Realm.open(defaultRealmConfig())
    private val binding by viewBinding(FragmentChatListBinding::bind)
    private val chatListViewModel: ChatListViewModel by activityViewModels()
    private var chatListAdapter: ChatListAdapter? = null
    private var layoutManager: LinearLayoutManager? = null
    private var showUnreadOnly = false
    private val enableNotificationsCode = 0L
    private var isPin = false
    private var isUnpin = false
    private var unpinnedChatPosition = -1
    private val activeFragment: Fragment?
        get() = requireActivity().supportFragmentManager.findFragmentById(R.id.application_container)
    private var snackbar: Snackbar? = null
    private var isOverTriggerCrossed = false
    private var selectedChatId = ""
    private var itemAnimator = DefaultItemAnimator().apply {
        this.removeDuration = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getBoolean(
            CHAT_LIST_UNREAD_KEY
        )?.let {
            showUnreadOnly = it
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            chatListViewModel.initDataListener()
            chatListViewModel.initAccountDataListener()
        }
     //   setTitle()
        initToolbarActions()
        initRecyclerView()
        subscribeToViewModelData()
        initEmptyButton()
        initMarkAllMessagesUnreadButton()
        initPullRefreshLayout()
        setupNavigationDrawer()
        if (baseViewModel.getPrimaryAccount() == null)
            binding.refreshLayout.isRefreshEnable = false


        drawerLayout = view.findViewById(R.id.drawer_layout)
        val navigationView = view.findViewById<NavigationView>(R.id.nav_view)

        drawerLayout.addDrawerListener(actionBarToggle)
        actionBarToggle.syncState()

        // Set NavigationView listener
        navigationView.setNavigationItemSelectedListener(this)

    }

    private fun setupNavigationDrawer() {
        drawerLayout = binding.drawerLayout

        // Initialize actionBarToggle
        actionBarToggle = ActionBarDrawerToggle(
            requireActivity(), // Use requireActivity() to get the parent activity
            drawerLayout,
            R.string.open,
            R.string.close
        ).apply {
            drawerLayout.addDrawerListener(this)
            syncState()
        }

        // Set the Toolbar as the ActionBar
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.chatToolbar)
        val navigationView = view?.findViewById<NavigationView>(R.id.nav_view)

        // Enable the home button (hamburger icon) in the Toolbar
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (requireActivity() as AppCompatActivity).supportActionBar?.setHomeButtonEnabled(true)
        val avatarImageView = requireActivity().findViewById<ImageView>(R.id.avatar_image_view)
        val titleTextView = requireActivity().findViewById<TextView>(R.id.title_text_view)
        val subtitleTextView = requireActivity().findViewById<TextView>(R.id.subtitle_text_view)
        val account = getPrimaryAccount()
        val avatar = account?.let { getAvatar(it.id) }
        account?.let {
            titleTextView.text = it.getAccountName()
            subtitleTextView.text = it.jid
        }
        avatar?.let {
            Glide.with(this).load(it.fileUri).into(avatarImageView)
            avatarImageView.requestLayout()
        }
        navigationView?.setNavigationItemSelectedListener(this)

        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    private fun getPrimaryAccount(): AccountDto? {
        var accountDto: AccountDto? = null
        val realmAccounts = realm.query(com.xabber.data_base.models.account.AccountStorageItem::class, "enabled = true").find()
        val primaryAccount = realmAccounts.minByOrNull { T -> T.order }
        if (primaryAccount != null) {
            accountDto = primaryAccount.toAccountDto()
        }
        return accountDto
    }

    private fun getAvatar(id: String): AvatarDto? {
        var avatarDto: AvatarDto? = null
        realm.writeBlocking {
            val realmAvatar =
                this.query(com.xabber.data_base.models.avatar.AvatarStorageItem::class, "primary = '$id'").first().find()
            if (realmAvatar != null)
                avatarDto = realmAvatar.toAvatarDto()
        }
        return avatarDto
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.chats -> handleChatsNavigation()
            R.id.calls -> handleCallsNavigation()
            R.id.contacts -> handleContactsNavigation()
            R.id.discover -> handleDiscoverNavigation()
            R.id.archive -> handleArchiveNavigation()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun handleProfileNavigation() {
        val widthDp = DisplayManager.getWidthDp()
        val orientation = resources.configuration.orientation
        val jid = getPrimaryAccount()?.jid ?: run {
            Toast.makeText(requireContext(), "No account found", Toast.LENGTH_SHORT).show()
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }
        if ((widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) ||
            (widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE)) {
            val accountDialog = AccountDialog.newInstance(jid)
            accountDialog.show(childFragmentManager, "Account")
        } else {
            navigator().showAccount(jid)
        }
        drawerLayout.closeDrawer(GravityCompat.START)
    }
    private fun replaceFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            // Uncomment if you want custom animations
            // .setCustomAnimations(R.anim.appearance_fragments, R.anim.disappearance_fragments)
            .replace(R.id.application_container, fragment)
            .commit()
    }
    private fun setupIconChat(unreadChats: Boolean) {
        // if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        val menuItem = binding.navView.menu.findItem(R.id.chats)
        if (unreadChats) {
            menuItem.setIcon(R.drawable.ic_chat_alert)
        } else {
            menuItem.setIcon(R.drawable.ic_chat)
        }
    }

    private fun handleChatsNavigation() {
        // Handle chats navigation
    }

    private fun handleCallsNavigation() {
        // Handle calls navigation
    }

    private fun handleContactsNavigation() {
        chatListViewModel.setShowUnreadOnly(false)
        navigator().closeDetail()
        if (activeFragment !is ContactsFragment) {
            replaceFragment(ContactsFragment())
        }
        setupIconChat(false)
    }

    private fun handleDiscoverNavigation() {
        // Handle discover navigation
    }

    private fun handleArchiveNavigation() {
        // Handle archive navigation
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun setTitle() {
        val title = if (showUnreadOnly) R.string.unread_chats else R.string.menu_item_chats
            //R.string.application_title
        binding.tvChatTitle.setText(title)
    }

    private fun initToolbarActions() {
        binding.chatToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.add -> {
                   if (chatListViewModel.chatIsEmpty()) chatListViewModel.addSomeChats()
                    else navigator().showNewChat()
                }

                else -> {}
            }; true

        }
        binding.chatToolbar.setOnClickListener {
            binding.chatList.partSmoothScrollToPosition(0) // Перемещение вверх с эффектом видимого скроллирования
        }
    }

    private fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            onBackPressed()
        }
    }
    private fun initRecyclerView() {
        chatListAdapter = ChatListAdapter(this)
        binding.chatList.adapter = chatListAdapter
        layoutManager = binding.chatList.layoutManager as LinearLayoutManager
        setRemoveDurationAnimation()    // длительность анимации удаления уменьшаем до 0, чтобы быстрее происходило перемещение элементов при смахивании в архив
        addItemDecoration()            // добавляем декоратор, разделяющий чаты
        addSwipeOption()               // свайп чата в архив
        addScrollListener()
    }

    private fun setRemoveDurationAnimation() {
        binding.chatList.itemAnimator = itemAnimator
    }

    private fun addItemDecoration() {
        val dividerItemDecoration = DividerItemDecoration(
            binding.root.context,
            LinearLayoutManager.VERTICAL
        )
        binding.chatList.addItemDecoration(
            dividerItemDecoration.apply {
                setChatListOffsetMode(ChatListBaseFragment.ChatListAvatarState.SHOW_AVATARS)
                skipDividerOnLastItem(true)
            })
    }

    private fun addSwipeOption() {
        if (chatListAdapter != null) {
            val swiper = SwipeToArchiveCallback(chatListAdapter!!)
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

    private fun showEmptyListMode(isEmpty: Boolean) {
        binding.linEmpty.isVisible = isEmpty
        binding.emptyButton.visibility =
            if (isEmpty && showUnreadOnly) View.INVISIBLE else View.VISIBLE
        if (isEmpty) {
            val textResId =
                if (showUnreadOnly) R.string.unread_list_is_empty_text else R.string.chat_list_is_empty_text
            binding.emptyText.setText(textResId)
        }
    }

    private fun initEmptyButton() {
        binding.emptyButton.setOnClickListener { navigator().showContacts() }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun subscribeToViewModelData() {
        chatListViewModel.showUnreadOnly.observe(viewLifecycleOwner) {
            showUnreadOnly = it
            setTitle()
            binding.refreshLayout.isRefreshEnable =
                !showUnreadOnly && baseViewModel.getPrimaryAccount() != null
        }

        chatListViewModel.chats.observe(viewLifecycleOwner) {
            val positionBeforeUpdate = layoutManager?.findFirstVisibleItemPosition()
            chatListAdapter?.isManyOwners = chatListViewModel.getAccountsAmount() > 1
            val list = ArrayList<ChatListDto>()
            list.addAll(it)
            chatListAdapter?.submitList(list) {
                binding.btnMarkAllMessagesUnread.isVisible = showUnreadOnly && !it.isNullOrEmpty()
                showEmptyListMode(it.isEmpty() || it == null)
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
            if (it == null || it == "offline") binding.refreshLayout.isRefreshEnable = false else {
                binding.refreshLayout.isRefreshEnable = true
                initPullRefreshLayout()
            }
            chatListAdapter?.isManyOwners = chatListViewModel.getAccountsAmount() > 1
            chatListViewModel.getChatList() // при изменении цвета меняем цвета pullRefreshLayout и цветного индикатора у чатов
        }

    }

    private fun initMarkAllMessagesUnreadButton() {
        binding.btnMarkAllMessagesUnread.setOnClickListener {
            chatListViewModel.markAllChatsAsUnread()
            binding.btnMarkAllMessagesUnread.isVisible = false
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
        chatListViewModel.pinChat(chatId)
        isPin = true
    }

    override fun unPinChat(chatId: String, position: Int) {
        chatListViewModel.unPinChat(chatId)
        isUnpin = true
        unpinnedChatPosition = position
    }

    override fun swipeItem(chatId: String) {
        chatListViewModel.setArchived(chatId)
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
        chatListViewModel.setMute(chatId, enableNotificationsCode)
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
            chatListViewModel.setArchived(id)
        }
        snackbar?.setActionTextColor(Color.YELLOW)
        snackbar?.show()
    }

    private fun initPullRefreshLayout() {
        val colorKey = chatListViewModel.getPrimaryAccountColorKey()
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
                    PullRefreshLayout.OVER_TRIGGER_POINT -> {   // точка, доходя до которой при отпускании произойдет переход в архив
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
        chatListAdapter?.notifyDataSetChanged()  // При изменении Маски перерисовываем список
    }

    override fun onStop() {
        super.onStop()
        snackbar?.dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        drawerLayout.removeDrawerListener(actionBarToggle)
    }

    companion object {
         fun getPrimaryAccount(chatListFragment: ChatListFragment): AccountDto? {
            var accountDto: AccountDto? = null
            val realmAccounts = chatListFragment.realm.query(AccountStorageItem::class, "enabled = true").find()
            val primaryAccount = realmAccounts.minByOrNull { T -> T.order }
            if (primaryAccount != null) {
                accountDto = primaryAccount.toAccountDto()
            }
            return accountDto
        }

    }

}
