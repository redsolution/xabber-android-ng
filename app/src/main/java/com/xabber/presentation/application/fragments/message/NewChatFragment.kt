package com.xabber.presentation.application.fragments.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.xabber.R
import com.xabber.databinding.FragmentNewChatBinding
import com.xabber.presentation.application.contract.applicationToolbarChanger
import com.xabber.presentation.application.contract.navigator
import kotlin.random.Random

class NewChatFragment : Fragment() {

    private var binding: FragmentNewChatBinding? = null

    private val quotes = listOf(
        "Leave the gun. Take the cannoli!",
        "Flex that smile",
        "Never give up!",
        "You talkin'to me?",
        "Infinite possibilities!",
        "Our Princess is in another castle!"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        binding = FragmentNewChatBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        changeTitle()
        initToolbarAction()
        initButton()
    }

    private fun changeTitle() {
        val random = Random.nextInt(quotes.size)
        val randomQuote = quotes[random]
        binding?.newChatToolbar?.title = randomQuote
    }

    private fun initToolbarAction() {
       binding?.newChatToolbar?.setNavigationIcon(R.drawable.ic_arrow_left)
        binding?.newChatToolbar?.setNavigationOnClickListener { navigator().goBack() }

    }

    private fun initButton() {
        with(binding!!) {
            rlAddContact.setOnClickListener { navigator().showNewContact() }
            rlCreateGroup.setOnClickListener {  navigator().showNewGroup(false) }
            rvCreateGroupIncognito.setOnClickListener { navigator().showNewGroup(true) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}