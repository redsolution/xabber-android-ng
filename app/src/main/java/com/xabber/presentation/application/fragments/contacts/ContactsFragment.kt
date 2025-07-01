package com.xabber.presentation.application.fragments.contacts

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentContactBinding
import com.xabber.dto.ContactDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.BlockContactDialog
import com.xabber.presentation.application.dialogs.DeletingContactDialog
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.chat.ChatFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.manage.DisplayManager

class ContactsFragment : BaseFragment(R.layout.fragment_contact), ContactAdapter.Listener {
    private val binding by viewBinding(FragmentContactBinding::bind)
    private val viewModel: ContactsViewModel by activityViewModels()
    private var contactAdapter: ContactAdapter? = null
    private var selectedChatId = ""
    private var selectedGroup: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedChatId = savedInstanceState?.getString(AppConstants.SELECTED_CHAT_ID) ?: ""
        selectedGroup = savedInstanceState?.getString(AppConstants.SELECTED_GROUP)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbarActions()
        initContactList()
        subscribeViewModel()
        setupFragmentResultListener()
        viewModel.initDataListener()
        viewModel.getChatList()
        updateToolbarTitle()
    }

    private fun initToolbarActions() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            binding.toolbarContacts.setNavigationIcon(R.drawable.ic_arrow_left_white)
            binding.toolbarContacts.setNavigationOnClickListener { navigator().goBack() }
        }

        binding.menu.setOnClickListener {
            val popup = PopupMenu(binding.menu.context, binding.menu)
            popup.menuInflater.inflate(R.menu.menu_toolbar_contact_list, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.reset_status -> {
                        navigator().showStatusFragment()
                        true
                    }
                    R.id.add_contact -> {
                        navigator().showNewContact()
                        true
                    }
                    R.id.display_contacts_offline -> {
                        // Handle offline contacts action
                        true
                    }
                    R.id.show_all_contacts -> {
                        selectedGroup = null
                        viewModel.showAllContacts()
                        updateToolbarTitle()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun initContactList() {
        contactAdapter = ContactAdapter(this)
        binding.recyclerView.adapter = contactAdapter
    }

    private fun subscribeViewModel() {
        viewModel.contactList.observe(viewLifecycleOwner) {
            contactAdapter?.submitList(it)
            updateToolbarTitle()
        }
    }

    private fun setupFragmentResultListener() {
        parentFragmentManager.setFragmentResultListener("group_filter", viewLifecycleOwner) { _, bundle ->
            selectedGroup = bundle.getString("selected_group")
            updateToolbarTitle()
        }
    }

    private fun updateToolbarTitle() {
        binding.tvContactTitle.text = selectedGroup?.let { "$it Contacts" } ?: getString(R.string.contacts_toolbar_title)
    }

    override fun onAvatarClick(contactDto: ContactDto) {
        if (activeFragment is ChatFragment) {
            navigator().closeDetail()
        }
        val params = ContactAccountParams(contactDto.primary, contactDto.avatar)
        if (DisplayManager.getWidthDp() > 600 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            val accDialog = ContactAccountFragment.newInstance(params)
            accDialog.show(childFragmentManager, AppConstants.CHAT_LIST_TO_FORWARD_DIALOG_TAG)
        } else if (DisplayManager.getWidthDp() > 800 && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            navigator().launchDetail(ContactAccountFragment.newInstance(params))
        } else {
            navigator().showContactAccount(params)
        }
    }

    override fun onContactClick(owner: String, opponentJid: String, avatar: Int) {
        if (activeFragment is ContactAccountFragment) {
            navigator().closeDetail()
        }
        val chatId = viewModel.getChatId(owner, opponentJid)
        if (chatId != null) {
            if (selectedChatId != chatId || !DisplayManager.isDualScreenMode()) {
                selectedChatId = chatId
                navigator().showChat(ChatParams(chatId, avatar))
            }
        }
    }

    override fun editContact(contactDto: ContactDto, avatar: Int, color: String) {
        navigator().showEditContactFromContacts(ContactAccountParams(contactDto.primary, avatar))
    }

    override fun deleteContact(userName: String) {
        // navigator().showDialogFragment(DeletingContactDialog.newInstance(userName, viewModel), "")
    }

    override fun blockContact(userName: String) {
        // navigator().showDialogFragment(BlockContactDialog.newInstance(userName), "")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(AppConstants.SELECTED_CHAT_ID, selectedChatId)
        outState.putString(AppConstants.SELECTED_GROUP, selectedGroup)
    }

    override fun onDestroy() {
        super.onDestroy()
        contactAdapter = null
    }

    private val activeFragment: Fragment?
        get() = childFragmentManager.findFragmentById(R.id.application_container)

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        contactAdapter?.notifyDataSetChanged()
    }
}