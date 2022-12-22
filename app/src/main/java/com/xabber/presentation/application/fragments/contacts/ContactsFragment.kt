package com.xabber.presentation.application.fragments.contacts

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentContactBinding
import com.xabber.model.dto.ContactDto
import com.xabber.presentation.application.BaseFragment
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.BlockContactDialog
import com.xabber.presentation.application.dialogs.DeletingContactDialog
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.utils.mask.MaskPrepare

class ContactsFragment : BaseFragment(R.layout.fragment_contact), ContactAdapter.Listener {
    private val binding by viewBinding(FragmentContactBinding::bind)
    private val viewModel = ContactsViewModel()
    private var contactAdapter: ContactAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAvatarWithMask()
        initToolbarActions()
        initContactList()
        subscribeViewModel()
    }

    private fun loadAvatarWithMask() {
        val maskedDrawable =
            MaskPrepare.getDrawableMask(resources, R.drawable.img, UiChanger.getMask().size32)
        binding.imAvatar.setImageDrawable(maskedDrawable)
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
    }

    override fun onAvatarClick(contactDto: ContactDto) {

       // navigator().showContactAccount()
    }

    override fun onContactClick(chatParams: ChatParams) {
        navigator().showChat(chatParams)
    }

    override fun editContact(contactDto: ContactDto) {
        navigator().showEditContactFromContacts(contactDto)
    }

    override fun deleteContact(userName: String) {
        navigator().showDialogFragment(DeletingContactDialog.newInstance(userName), "")
    }

    override fun blockContact(userName: String) {
        navigator().showDialogFragment(BlockContactDialog.newInstance(userName), "")
    }

    override fun onDestroy() {
        super.onDestroy()
        contactAdapter = null
    }
}
