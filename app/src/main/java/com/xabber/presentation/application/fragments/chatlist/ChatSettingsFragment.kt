package com.xabber.presentation.application.fragments.chatlist

import android.os.Bundle
import android.view.View
import by.kirich1409.viewbindingdelegate.viewBinding
import com.xabber.R
import com.xabber.databinding.FragmentChatSettingsBinding
import com.xabber.presentation.application.fragments.DetailBaseFragment

class ChatSettingsFragment : DetailBaseFragment(R.layout.fragment_chat_settings) {
    private val binding by viewBinding(FragmentChatSettingsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

}
