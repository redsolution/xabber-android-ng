package com.xabber.presentation.application.fragments.contacts

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentContactBinding
import com.xabber.model.dto.ContactDto
import com.xabber.presentation.BaseFragment
import com.xabber.presentation.application.dialogs.DeletingContactDialog
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.BlockContactDialog
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.utils.mask.MaskPrepare

class ContactsFragment : BaseFragment(R.layout.fragment_contact), ContactAdapter.Listener {
    private val binding by viewBinding(FragmentContactBinding::bind)
    private val viewModel = ContactsViewModel()
    private var contactAdapter: ContactAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAvatarWithMask()
        initContactList()
        subscribeViewModel()
    }

    private fun loadAvatarWithMask() {
        val maskedDrawable =
            MaskPrepare.getDrawableMask(resources, R.drawable.img, UiChanger.getMask().size32)
        binding.imAvatar.setImageDrawable(maskedDrawable)
    }

    private fun initContactList() {
        contactAdapter = ContactAdapter(this)
        binding.recyclerView.adapter = contactAdapter
        contactAdapter!!.submitList(viewModel.contacts.value)
    }

    private fun subscribeViewModel() {
        viewModel.contacts.observe(viewLifecycleOwner) {
            contactAdapter?.submitList(it)
        }
    }

    override fun onAvatarClick(contactDto: ContactDto) {

        navigator().showContactAccount(contactDto)
    }

    override fun onContactClick(chatParams: ChatParams) {
            navigator().showChat(chatParams)
    }

    override fun editContact(contactDto: ContactDto) {
     navigator().showEditContactFromContacts(contactDto)
    }

    override fun deleteContact(userName: String) {
          navigator().showDialogFragment(DeletingContactDialog.newInstance(userName))
    }

    override fun blockContact(userName: String) {
        navigator().showDialogFragment(BlockContactDialog.newInstance(userName))
    }

    override fun onDestroy() {
        super.onDestroy()
        contactAdapter = null
    }
}
