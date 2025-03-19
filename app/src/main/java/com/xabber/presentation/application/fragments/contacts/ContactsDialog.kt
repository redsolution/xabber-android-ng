package com.xabber.presentation.application.fragments.contacts

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
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
import com.xabber.presentation.application.fragments.chat.ChatParams
import com.xabber.presentation.application.manage.DisplayManager

class ContactsDialog : BaseFragment(R.layout.fragment_contact), ContactAdapter.Listener, SharedPreferences.OnSharedPreferenceChangeListener {
    private val binding by viewBinding(FragmentContactBinding::bind)
    private val viewModel = ContactsViewModel()
    private var contactAdapter: ContactAdapter? = null
    private var selectedChatId = ""
    private lateinit var sh: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedChatId = savedInstanceState?.getString(AppConstants.SELECTED_CHAT_ID) ?: ""
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val widthDp = DisplayManager.getWidthDp()
            val orientation = resources.configuration.orientation
            if (widthDp > 600 && orientation == Configuration.ORIENTATION_PORTRAIT) {
                val width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 90% of screen width
                val height = (resources.displayMetrics.heightPixels * 0.95).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER) // Center the dialog
            }
            if ((widthDp > 800 && orientation == Configuration.ORIENTATION_LANDSCAPE)) {
                val width = (resources.displayMetrics.widthPixels * 0.48).toInt() // 90% of screen width
                val height = (resources.displayMetrics.heightPixels * 0.97).toInt()
                dialog.window?.setLayout(width, height)
                dialog.window?.setGravity(Gravity.CENTER) // Center the dialog
            }

        }
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

        binding.toolbarContacts.setNavigationIcon(R.drawable.ic_arrow_left_white)
        binding.toolbarContacts.setNavigationOnClickListener{dismiss()}
    }

    private fun initToolbarActions() {
        val toolbar = binding.toolbarContacts
        val overflowIcon = ContextCompat.getDrawable(requireContext(), R.drawable.more_vert_24px)
        overflowIcon?.let {
            val iconHeight = it.intrinsicHeight
            val toolbarHeight = toolbar.height
            val padding = iconHeight // Use icon height as padding value
            val vertical = padding
            val side = (padding - toolbarHeight) / 3
            val insetDrawable = InsetDrawable(it, side, vertical, side, vertical)
            binding.toolbarContacts.overflowIcon = insetDrawable
        }
        binding.toolbarContacts.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.reset_status -> navigator().showStatusFragment()
                R.id.add_contact -> navigator().showNewContact()
                R.id.display_contacts_offline -> {}
            }; true
        }
        binding.toolbarContacts.setNavigationIcon(drawable.ic_arrow_left_white)
        binding.toolbarContacts.setNavigationOnClickListener{navigator().goBack()}
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


        val params = ContactAccountParams(
                contactDto.primary,
                contactDto.avatar)

        if (DisplayManager.getWidthDp() > 600 && resources.configuration.orientation
            == Configuration.ORIENTATION_PORTRAIT || DisplayManager.getWidthDp() > 800
            && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {

            val accDialog = ContactAccountFragment.newInstance(params)
            accDialog.show(childFragmentManager, AppConstants.CHAT_ACCOUNT_INFO)

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
        val chatId = viewModel.getChatId(owner, opponentJid)
        if (chatId != null) {
            if (selectedChatId != chatId || !DisplayManager.isDualScreenMode()) {
                selectedChatId = chatId
                navigator().showChat(ChatParams(chatId, avatar))
                dismiss()
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
        contactAdapter?.notifyDataSetChanged()
    }
}
