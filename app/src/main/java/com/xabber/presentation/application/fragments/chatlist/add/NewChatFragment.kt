package com.xabber.presentation.application.fragments.chatlist.add

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentNewChatBinding
import com.xabber.presentation.AppConstants
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment

class NewChatFragment : DetailBaseFragment(R.layout.fragment_new_chat) {
    private val binding by viewBinding(FragmentNewChatBinding::bind)
    private var title: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            title = savedInstanceState.getString(AppConstants.NEW_CHAT_KEY)
        }
        setTitle()
        initButtons()
    }

    private fun setTitle() {
        if (title == null) {
            val quotes = getString(R.string.motivating_oneliner).split("\n")
            title = quotes.random().trim()
        }
        binding.tvToolbarTitle.text = title
    }

    private fun initButtons() {
        with(binding) {
            rlAddContact.setOnClickListener { navigator().showNewContact() }
            rlCreateGroup.setOnClickListener { navigator().showNewGroup(false) }
            rvCreateGroupIncognito.setOnClickListener { navigator().showNewGroup(true) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(AppConstants.NEW_CHAT_KEY, binding.tvToolbarTitle.text.toString())
    }

}
