package com.xabber.presentation.application.fragments.chatlist

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentNewChatBinding
import com.xabber.presentation.application.contract.navigator
import com.xabber.presentation.application.fragments.DetailBaseFragment
import kotlin.random.Random

class NewChatFragment : DetailBaseFragment(R.layout.fragment_new_chat) {
    private val binding by viewBinding(FragmentNewChatBinding::bind)

    private val quotes = listOf(
        "Leave the gun. Take the cannoli!",
        "Flex that smile",
        "Never give up!",
        "You talkin'to me?",
        "Infinite possibilities!",
        "Our Princess is in another castle!"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changeTitle()
        initToolbarAction()
        initButton()
    }

    private fun changeTitle() {
        val random = Random.nextInt(quotes.size)
        val randomQuote = quotes[random]
        binding.newChatToolbar.title = randomQuote
    }

    private fun initToolbarAction() {
        binding.newChatToolbar.setNavigationIcon(R.drawable.ic_close)
        binding.newChatToolbar.setNavigationOnClickListener { navigator().closeDetail() }

    }

    private fun initButton() {
        with(binding) {
            rlAddContact.setOnClickListener { navigator().showNewContact() }
            rlCreateGroup.setOnClickListener { navigator().showNewGroup(false) }
            rvCreateGroupIncognito.setOnClickListener { navigator().showNewGroup(true) }
        }
    }

}