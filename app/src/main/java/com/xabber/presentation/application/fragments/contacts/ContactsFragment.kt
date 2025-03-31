package com.xabber.presentation.application.fragments.contacts

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.R.drawable
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
    private val viewModel = ContactsViewModel()
    private var contactAdapter: ContactAdapter? = null
    private var selectedChatId = ""
    private val activeFragment: Fragment?
        get() = childFragmentManager.findFragmentById(R.id.application_container)
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



    }

    private fun initToolbarActions() {
        // Set up navigation (this part remains unchanged)
        if ( resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            binding.toolbarContacts.setNavigationIcon(R.drawable.ic_arrow_left_white)
            binding.toolbarContacts.setNavigationOnClickListener { navigator().goBack() }
        }


        // Set up the ImageView menu button
        binding.menu.setOnClickListener {
            // Create a PopupMenu anchored to the ImageView
            val popup = PopupMenu(binding.menu.context, binding.menu)
            popup.menuInflater.inflate(R.menu.menu_toolbar_contact_list, popup.menu) // Use your existing menu XML

            // Handle menu item clicks
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
                    else -> false
                }
            }

            // Show the popup menu
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
        }
    }

    override fun onAvatarClick(contactDto: ContactDto) {
        if (activeFragment is ChatFragment) {navigator().closeDetail()}
        val params = ContactAccountParams(
                contactDto.primary,
                contactDto.avatar)

        if (DisplayManager.getWidthDp() > 600 && resources.configuration.orientation
            == Configuration.ORIENTATION_PORTRAIT) {
            val accDialog = ContactAccountFragment.newInstance(params)
            accDialog.show(childFragmentManager, AppConstants.CHAT_LIST_TO_FORWARD_DIALOG_TAG)
        } else if (DisplayManager.getWidthDp() > 800 && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {

            navigator().launchDetail(ContactAccountFragment.newInstance(params))

        } else {
            navigator().showContactAccount(
                ContactAccountParams(
                    contactDto.primary,
                    contactDto.avatar
                )
            )

        }

    }

    override fun onContactClick(owner: String, opponentJid: String, avatar: Int) {
        if (activeFragment is ContactAccountFragment) {navigator().closeDetail()}

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
