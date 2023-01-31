package com.xabber.presentation.application.fragments.contacts

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.xabber.R
import com.xabber.data_base.defaultRealmConfig
import com.xabber.databinding.FragmentContactBinding
import com.xabber.models.dto.ContactDto
import com.xabber.models.xmpp.account.AccountStorageItem
import com.xabber.presentation.application.activity.UiChanger
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.dialogs.BlockContactDialog
import com.xabber.presentation.application.dialogs.DeletingContactDialog
import com.xabber.presentation.application.fragments.BaseFragment
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.fragments.chatlist.PullRecyclerViewEffectFactory
import com.xabber.presentation.application.fragments.contacts.vcard.ContactAccountParams
import io.realm.kotlin.Realm

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
        viewModel.initDataListener()
        viewModel.getChatList()
        val owner = viewModel.getOwner()

        if (owner != null) binding.tvContactTitle.text = owner

        binding.imAvatar.setOnClickListener {
            var jid = ""
            val realm = Realm.open(defaultRealmConfig())
            realm.writeBlocking {
                jid = realm.query(AccountStorageItem::class).first().find()!!.jid
            }
            navigator().showAccount(jid)
        }
    }

    private fun loadAvatarWithMask() {
        val avatar = UiChanger.getAvatar()
        val multiTransformation = MultiTransformation(CircleCrop())
        Glide.with(requireContext()).load(avatar).error(R.drawable.ic_avatar_placeholder)
            .apply(RequestOptions.bitmapTransform(multiTransformation))
            .into(binding.imAvatar)
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
                contactDto.avatar,
                contactDto.color
            )
        )
    }

    override fun onContactClick(owner: String, opponentJid: String, avatar: Int) {
        val chatId = viewModel.getChatId(owner, opponentJid)
        if (chatId != null) navigator().showChat(ChatParams(chatId, owner, opponentJid, avatar))
    }

    override fun editContact(contactDto: ContactDto, avatar: Int, color: Int) {
        navigator().showEditContactFromContacts(
            ContactAccountParams(
                contactDto.primary,
                avatar,
                color
            )
        )
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
