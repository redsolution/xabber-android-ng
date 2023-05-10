package com.xabber.presentation.application.fragments.contacts

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentContactBinding
import com.xabber.dto.ContactDto
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.BlockContactDialog
import com.xabber.presentation.application.dialogs.DeletingContactDialog
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.manage.DisplayManager

class ContactsFragment : BaseFragment(R.layout.fragment_contact), ContactAdapter.Listener {
    private val binding by viewBinding(FragmentContactBinding::bind)
    private val viewModel = ContactsViewModel()
    private var contactAdapter: ContactAdapter? = null
    private var selectedChatId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedChatId = savedInstanceState?.getString(AppConstants.SELECTED_CHAT_ID) ?: ""
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbarActions()
        initContactList()
        subscribeViewModel()
        viewModel.initDataListener()
        viewModel.getChatList()
//        val account = baseViewModel.getPrimaryAccount()
//        if (account != null) binding.tvContactTitle.text = account.nickname else binding.tvContactTitle.text = resources.getString(R.string.contacts_toolbar_title)


    }

    private fun initToolbarActions() {
        binding.toolbarContacts.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.reset_status -> navigator().showStatusFragment()
                R.id.add_contact -> navigator().showNewContact()
                R.id.display_contacts_offline -> {}
            }; true
        }
    }

    private fun initContactList() {
        contactAdapter = ContactAdapter(this)
        binding.recyclerView.adapter = contactAdapter
    }

    private fun subscribeViewModel() {
        viewModel.contactList.observe(viewLifecycleOwner) {
            contactAdapter?.submitList(it)
        }
    }

    override fun onAvatarClick(contactDto: ContactDto) {

        navigator().showContactAccount(
            ContactAccountParams(
                contactDto.primary,
                contactDto.avatar
            )
        )
    }

    override fun onContactClick(owner: String, opponentJid: String, avatar: Int) {
        val chatId = viewModel.getChatId(owner, opponentJid)
        if (chatId != null) {
            if (selectedChatId != chatId || !DisplayManager.isDualScreenMode()) {
                selectedChatId = chatId
                navigator().showChat(ChatParams(chatId, avatar))
            }
        }
    }

    override fun editContact(contactDto: ContactDto, avatar: Int, color: String) {
        navigator().showEditContactFromContacts(
            ContactAccountParams(
                contactDto.primary,
                avatar
            )
        )
    }

    override fun deleteContact(contactDto: String) {
      //  navigator().showDialogFragment(DeletingContactDialog.newInstance(contactDto.nickName?: contactDto., viewMod), "")
    }

    override fun blockContact(userName: String) {
     //   navigator().showDialogFragment(BlockContactDialog.newInstance(userName), "")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(AppConstants.SELECTED_CHAT_ID, selectedChatId)
    }

    override fun onDestroy() {
        super.onDestroy()
        contactAdapter = null
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        contactAdapter?.notifyDataSetChanged()
    }
}
